package com.jobscout.persistence;

import com.jobscout.normalization.JobDraft;
import com.jobscout.persistence.entity.JobObservation;
import com.jobscout.persistence.entity.JobPosting;
import com.jobscout.persistence.entity.Statuses;
import com.jobscout.persistence.repository.JobObservationRepository;
import com.jobscout.persistence.repository.JobPostingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent persistence: re-running a search updates existing postings and adds one observation per run. */
@Service
public class JobStore {
    private final JobPostingRepository jobs;
    private final JobObservationRepository observations;

    public JobStore(JobPostingRepository jobs, JobObservationRepository observations) {
        this.jobs = jobs;
        this.observations = observations;
    }

    /** @return true when the posting was seen for the first time. */
    @Transactional
    public boolean upsert(JobDraft d, long runId) {
        Instant now = Instant.now();
        String key = dedupeKey(d);
        JobPosting jp = jobs.findByDedupeKey(key).orElse(null);
        boolean isNew = jp == null;
        if (isNew) {
            jp = new JobPosting();
            jp.dedupeKey = key;
            jp.companyId = d.companyId();
            jp.externalJobId = d.externalJobId();
            jp.firstSeenAt = now;
            jp.firstRunId = runId;
        }
        jp.companyName = d.companyName();
        jp.canonicalUrl = d.canonicalUrl();
        jp.title = trunc(d.title(), 500);
        jp.location = trunc(d.location(), 500);
        if (d.description() != null) jp.description = d.description();
        if (d.employmentType() != null) jp.employmentType = trunc(d.employmentType(), 100);
        jp.postedDate = d.postedDate();
        jp.postedDateRaw = trunc(d.postedDateRaw(), 200);
        jp.postedDateStatus = d.postedDateStatus().name();
        jp.currentStatus = Statuses.ACTIVE;
        jp.lastSeenAt = now;
        jp.lastVerifiedAt = now;
        jp.lastRunId = runId;
        jp = jobs.save(jp);

        if (!observations.existsByJobIdAndRunId(jp.id, runId)) {
            JobObservation o = new JobObservation();
            o.jobId = jp.id;
            o.runId = runId;
            o.observedAt = now;
            o.sourcePostedText = trunc(d.postedDateRaw(), 200);
            o.rawValues = d.rawValues().toString();
            o.searchKeyword = d.searchKeyword();
            o.searchLocation = d.searchLocation();
            o.postedDays = d.postedDays();
            observations.save(o);
        }
        return isNew;
    }

    /** Priority: external id, then canonical URL, then normalised title+location (last resort). */
    public static String dedupeKey(JobDraft d) {
        String basis;
        if (d.externalJobId() != null && !d.externalJobId().isBlank()) basis = "id:" + d.externalJobId().trim();
        else if (d.canonicalUrl() != null && !d.canonicalUrl().isBlank()) basis = "url:" + d.canonicalUrl();
        else basis = "tl:" + norm(d.title()) + "|" + norm(d.location());
        return sha256(d.companyId() + "|" + basis);
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String trunc(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
