package com.jobscout.api;

import com.jobscout.persistence.entity.JobPosting;
import java.time.Instant;
import java.time.LocalDate;

public record JobDto(Long id, String companyId, String companyName, String externalJobId, String title, String location,
                     String employmentType, LocalDate postedDate, String postedDateRaw, String postedDateStatus,
                     String currentStatus, boolean isNew, boolean unknownDate, String jobUrl, Instant firstSeenAt,
                     Instant lastSeenAt, Instant lastVerifiedAt, Long lastRunId, String description) {

    public static JobDto from(JobPosting j, boolean withDescription) {
        boolean unknown = !"PARSED".equals(j.postedDateStatus) && !"ESTIMATED".equals(j.postedDateStatus);
        return new JobDto(j.id, j.companyId, j.companyName, j.externalJobId, j.title, j.location, j.employmentType,
                j.postedDate, j.postedDateRaw, j.postedDateStatus, j.currentStatus,
                j.firstRunId != null && j.firstRunId.equals(j.lastRunId), unknown, j.canonicalUrl, j.firstSeenAt,
                j.lastSeenAt, j.lastVerifiedAt, j.lastRunId, withDescription ? j.description : null);
    }
}
