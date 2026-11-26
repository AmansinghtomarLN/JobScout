package com.jobscout.orchestration;

import com.jobscout.config.JobScoutProperties;
import com.jobscout.configuration.ConfigService;
import com.jobscout.configuration.ConfigService.LoadedConfig;
import com.jobscout.configuration.model.ConfigModel.CompanyConfig;
import com.jobscout.engine.EngineHelper;
import com.jobscout.persistence.entity.CompanyTask;
import com.jobscout.persistence.entity.ExecutionRun;
import com.jobscout.persistence.entity.Statuses;
import com.jobscout.persistence.repository.CompanyTaskRepository;
import com.jobscout.persistence.repository.ExecutionRunRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Plans runs, fans out one task per company onto the bounded pool, enforces per-domain limits. */
@Service
public class RunService {

    public record RunRequest(List<String> companyIds, Long snapshotId, Boolean dryRun, Integer maxPages, String trigger) {}

    private final ConfigService config;
    private final TaskRunner runner;
    private final ExecutionRunRepository runs;
    private final CompanyTaskRepository tasks;
    private final ExecutorService executor;
    private final JobScoutProperties props;
    private final DigestNotifier notifier;
    private final Map<String, Semaphore> domainLocks = new ConcurrentHashMap<>();
    private final Set<Long> cancelledRuns = ConcurrentHashMap.newKeySet();

    public RunService(ConfigService config, TaskRunner runner, ExecutionRunRepository runs, CompanyTaskRepository tasks,
                      @Qualifier("jobExecutor") ExecutorService executor, JobScoutProperties props,
                      DigestNotifier notifier) {
        this.config = config;
        this.runner = runner;
        this.runs = runs;
        this.tasks = tasks;
        this.executor = executor;
        this.props = props;
        this.notifier = notifier;
    }

    /** Anything left RUNNING/QUEUED by a previous process was interrupted. */
    @PostConstruct
    void recoverInterrupted() {
        for (String s : List.of(Statuses.RUNNING, Statuses.QUEUED)) {
            for (CompanyTask t : tasks.findByStatus(s)) {
                t.status = Statuses.FAILED;
                t.errorCode = "UNEXPECTED";
                t.errorMessage = "Interrupted by application restart";
                t.completedAt = Instant.now();
                tasks.save(t);
            }
        }
        for (ExecutionRun r : runs.findByStatus(Statuses.RUNNING)) {
            r.status = Statuses.FAILED;
            r.completedAt = Instant.now();
            runs.save(r);
        }
    }

    public ExecutionRun start(RunRequest req) {
        LoadedConfig cfg = req.snapshotId() == null ? config.current()
                : config.snapshot(req.snapshotId()).orElseThrow(() ->
                new IllegalArgumentException("Snapshot " + req.snapshotId() + " is no longer in memory; sync again"));
        Set<String> wanted = req.companyIds() == null || req.companyIds().isEmpty() ? null : new HashSet<>(req.companyIds());
        List<CompanyConfig> selected = cfg.companies().stream()
                .filter(CompanyConfig::enabled)
                .filter(c -> wanted == null || wanted.contains(c.companyId()))
                .map(c -> req.maxPages() != null ? c.withMaxPages(req.maxPages()) : c)
                .toList();
        if (selected.isEmpty()) throw new IllegalArgumentException("No enabled companies match the request");

        ExecutionRun run = new ExecutionRun();
        run.startedAt = Instant.now();
        run.triggerType = req.trigger() == null ? "MANUAL" : req.trigger();
        run.configSnapshotId = cfg.snapshotId();
        run.status = Statuses.RUNNING;
        run = runs.save(run);
        final long runId = run.id;

        Map<Long, CompanyConfig> byTask = new LinkedHashMap<>();
        for (CompanyConfig c : selected) {
            CompanyTask t = new CompanyTask();
            t.runId = runId;
            t.companyId = c.companyId();
            t.companyName = c.companyName();
            t.profileId = c.searchProfileId();
            t.status = Statuses.QUEUED;
            byTask.put(tasks.save(t).id, c);
        }

        if (Boolean.TRUE.equals(req.dryRun())) {                       // validation-only mode
            byTask.forEach((taskId, c) -> {
                var errs = cfg.errorsFor(c.companyId());
                if (errs.isEmpty()) {
                    CompanyTask t = tasks.findById(taskId).orElseThrow();
                    t.status = Statuses.VALIDATED;
                    t.completedAt = Instant.now();
                    tasks.save(t);
                } else {
                    runner.failConfig(taskId, joinErrors(errs));
                }
            });
            return finalizeRun(runId);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        byTask.forEach((taskId, c) -> futures.add(CompletableFuture.runAsync(() -> runOne(taskId, c, runId, cfg), executor)));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((v, e) -> finalizeRun(runId));
        return run;
    }

    private void runOne(long taskId, CompanyConfig c, long runId, LoadedConfig cfg) {
        var errs = cfg.errorsFor(c.companyId());
        if (!errs.isEmpty()) {
            runner.failConfig(taskId, joinErrors(errs));
            return;
        }
        Semaphore sem = domainLocks.computeIfAbsent(EngineHelper.host(c),
                h -> new Semaphore(Math.max(1, props.engine().maxTasksPerDomain()), true));
        boolean acquired = false;
        try {
            sem.acquire();
            acquired = true;
            runner.execute(taskId, c, runId, () -> cancelledRuns.contains(runId));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            runner.failConfig(taskId, "Unexpected: " + e.getMessage());
        } finally {
            if (acquired) sem.release();
        }
    }

    private ExecutionRun finalizeRun(long runId) {
        ExecutionRun run = runs.findById(runId).orElseThrow();
        List<CompanyTask> ts = tasks.findByRunId(runId);
        long failed = ts.stream().filter(t -> Statuses.FAILED.equals(t.status)).count();
        long partial = ts.stream().filter(t -> Statuses.PARTIAL.equals(t.status)).count();
        run.jobsFound = ts.stream().mapToInt(t -> t.jobsExtracted).sum();
        run.newJobs = ts.stream().mapToInt(t -> t.newJobs).sum();
        run.completedAt = Instant.now();
        if (cancelledRuns.remove(runId)) run.status = Statuses.CANCELLED;
        else if (failed == ts.size()) run.status = Statuses.FAILED;
        else if (failed > 0 || partial > 0) run.status = Statuses.PARTIAL;
        else run.status = Statuses.SUCCEEDED;
        run.summary = ts.size() + " companies, " + failed + " failed, " + partial + " partial";
        run = runs.save(run);
        notifier.notifyRun(run);
        return run;
    }

    public void cancel(long runId) {
        cancelledRuns.add(runId);
        for (CompanyTask t : tasks.findByRunId(runId)) {
            if (Statuses.QUEUED.equals(t.status)) {
                t.status = Statuses.CANCELLED;
                t.completedAt = Instant.now();
                tasks.save(t);
            }
        }
    }

    /** Re-runs only the failed/partial companies of a run, on the same snapshot or the latest workbook. */
    public ExecutionRun retryFailed(long runId, boolean useLatestConfig) {
        ExecutionRun old = runs.findById(runId).orElseThrow(() -> new NoSuchElementException("Run " + runId));
        List<String> ids = tasks.findByRunId(runId).stream()
                .filter(t -> Statuses.FAILED.equals(t.status) || Statuses.PARTIAL.equals(t.status))
                .map(t -> t.companyId).collect(Collectors.toList());
        if (ids.isEmpty()) throw new IllegalArgumentException("Run " + runId + " has no failed tasks");
        return start(new RunRequest(ids, useLatestConfig ? null : old.configSnapshotId, false, null, "RETRY"));
    }

    private static String joinErrors(List<com.jobscout.configuration.model.ConfigModel.ConfigIssue> errs) {
        return errs.stream().map(e -> e.sheet() + ": " + e.message()).collect(Collectors.joining("; "));
    }
}
