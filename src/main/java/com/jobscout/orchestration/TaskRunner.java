package com.jobscout.orchestration;

import com.jobscout.common.ErrorCode;
import com.jobscout.common.JobScoutException;
import com.jobscout.config.JobScoutProperties;
import com.jobscout.configuration.model.ConfigModel.CompanyConfig;
import com.jobscout.engine.EngineResult;
import com.jobscout.engine.PlaywrightJobEngine;
import com.jobscout.engine.RawJob;
import com.jobscout.engine.TaskContext;
import com.jobscout.normalization.JobDraft;
import com.jobscout.normalization.JobNormalizer;
import com.jobscout.persistence.JobStore;
import com.jobscout.persistence.entity.CompanyTask;
import com.jobscout.persistence.entity.ExecutionEvent;
import com.jobscout.persistence.entity.Statuses;
import com.jobscout.persistence.repository.CompanyTaskRepository;
import com.jobscout.persistence.repository.ExecutionEventRepository;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Executes one company task: retries with backoff + jitter, normalises, filters by date, persists. */
@Component
public class TaskRunner {
    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);

    private final PlaywrightJobEngine engine;
    private final JobNormalizer normalizer;
    private final JobStore store;
    private final CompanyTaskRepository tasks;
    private final ExecutionEventRepository events;
    private final JobScoutProperties props;

    public TaskRunner(PlaywrightJobEngine engine, JobNormalizer normalizer, JobStore store,
                      CompanyTaskRepository tasks, ExecutionEventRepository events, JobScoutProperties props) {
        this.engine = engine;
        this.normalizer = normalizer;
        this.store = store;
        this.tasks = tasks;
        this.events = events;
        this.props = props;
    }

    public void execute(long taskId, CompanyConfig c, long runId, BooleanSupplier cancelled) {
        CompanyTask t = tasks.findById(taskId).orElseThrow();
        if (!Statuses.QUEUED.equals(t.status)) return;             // cancelled while queued
        MDC.put("runId", String.valueOf(runId));
        MDC.put("taskId", String.valueOf(taskId));
        MDC.put("companyId", c.companyId());
        try {
            run(t, c, runId, cancelled);
        } finally {
            MDC.clear();
        }
    }

    private void run(CompanyTask t, CompanyConfig c, long runId, BooleanSupplier cancelled) {
        t.status = Statuses.RUNNING;
        t.startedAt = Instant.now();
        tasks.save(t);

        TaskContext ctx = new TaskContext() {
            public boolean cancelled() { return cancelled.getAsBoolean(); }
            public void stage(String s) { t.stage = s; tasks.save(t); }
            public void progress(int pages, int urls) { t.pagesVisited = pages; t.urlsDiscovered = urls; tasks.save(t); }
            public void event(String level, String stage, String code, String msg) { logEvent(t.id, level, stage, code, msg); }
        };

        int maxAttempts = props.engine().defaultRetries() + 1;
        EngineResult result = null;
        JobScoutException failure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (cancelled.getAsBoolean()) { finish(t, Statuses.CANCELLED, null, "Cancelled"); return; }
            t.attempts = attempt;
            tasks.save(t);
            try {
                result = engine.run(c, ctx);
                failure = null;
                break;
            } catch (JobScoutException e) {
                failure = e;
                logEvent(t.id, "ERROR", t.stage, e.code().name(), "Attempt " + attempt + ": " + e.getMessage());
                if (!e.retryable() || attempt == maxAttempts) break;
                backoff(attempt);
            } catch (RuntimeException e) {
                failure = new JobScoutException(ErrorCode.UNEXPECTED, e.getClass().getSimpleName() + ": " + e.getMessage(), false);
                logEvent(t.id, "ERROR", t.stage, ErrorCode.UNEXPECTED.name(), failure.getMessage());
                break;
            }
        }
        if (result == null) {
            finish(t, Statuses.FAILED, failure.code(), failure.getMessage());
            return;
        }

        ctx.stage("PERSIST");
        int saved = 0, fresh = 0, rejected = 0, bad = 0, storageErrors = 0;
        for (RawJob raw : result.jobs()) {
            JobDraft d = normalizer.normalize(c, raw);
            if (d == null) { bad++; continue; }
            if (!normalizer.withinWindow(d)) { rejected++; continue; }
            try {
                if (store.upsert(d, runId)) fresh++;
                saved++;
            } catch (RuntimeException e) {
                storageErrors++;
                logEvent(t.id, "ERROR", "PERSIST", ErrorCode.STORAGE_FAILED.name(), String.valueOf(e.getMessage()));
            }
        }
        t.pagesVisited = result.pagesVisited();
        t.urlsDiscovered = result.jobs().size();
        t.jobsExtracted = saved;
        t.newJobs = fresh;
        t.rejectedByDate = rejected;
        if (storageErrors > 0) finish(t, Statuses.PARTIAL, ErrorCode.STORAGE_FAILED, storageErrors + " jobs failed to persist");
        else if (bad > 0) finish(t, Statuses.PARTIAL, ErrorCode.EXTRACTION_FAILED, bad + " records had no title/URL");
        else finish(t, Statuses.SUCCEEDED, null, null);
    }

    /** Marks a task failed before any browser work (configuration errors). */
    public void failConfig(long taskId, String message) {
        CompanyTask t = tasks.findById(taskId).orElseThrow();
        t.startedAt = Instant.now();
        finish(t, Statuses.FAILED, ErrorCode.CONFIG_INVALID, message);
    }

    private void finish(CompanyTask t, String status, ErrorCode code, String message) {
        t.status = status;
        t.stage = "DONE";
        t.completedAt = Instant.now();
        t.errorCode = code == null ? null : code.name();
        t.errorMessage = message == null ? null : message.substring(0, Math.min(message.length(), 900));
        tasks.save(t);
        log.info("task {} finished status={} jobs={} new={} code={}", t.id, status, t.jobsExtracted, t.newJobs, t.errorCode);
    }

    private void logEvent(Long taskId, String level, String stage, String code, String msg) {
        ExecutionEvent e = new ExecutionEvent();
        e.taskId = taskId;
        e.ts = Instant.now();
        e.level = level;
        e.stage = stage;
        e.eventCode = code;
        e.message = msg == null ? null : msg.substring(0, Math.min(msg.length(), 900));
        events.save(e);
    }

    private static void backoff(int attempt) {
        long ms = (long) (1000 * Math.pow(2, attempt - 1)) + ThreadLocalRandom.current().nextLong(500);
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
