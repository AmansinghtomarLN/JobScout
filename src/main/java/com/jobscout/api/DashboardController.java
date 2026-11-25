package com.jobscout.api;

import com.jobscout.configuration.ConfigService;
import com.jobscout.configuration.model.ConfigModel.CompanyConfig;
import com.jobscout.persistence.entity.CompanyTask;
import com.jobscout.persistence.entity.ExecutionRun;
import com.jobscout.persistence.entity.Statuses;
import com.jobscout.persistence.repository.CompanyTaskRepository;
import com.jobscout.persistence.repository.ExecutionRunRepository;
import com.jobscout.persistence.repository.JobPostingRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final ConfigService config;
    private final ExecutionRunRepository runs;
    private final CompanyTaskRepository tasks;
    private final JobPostingRepository jobs;

    public DashboardController(ConfigService config, ExecutionRunRepository runs, CompanyTaskRepository tasks,
                               JobPostingRepository jobs) {
        this.config = config;
        this.runs = runs;
        this.tasks = tasks;
        this.jobs = jobs;
    }

    public record Summary(long enabledCompanies, Long latestRunId, String latestRunStatus, int companiesProcessed,
                          int jobsFound, int newJobs, long failedCompanies, long totalJobs, Instant configImportedAt,
                          String configChecksum) {}

    @GetMapping("/dashboard/summary")
    public Summary summary() {
        var cfg = config.current();
        long enabled = cfg.companies().stream().filter(CompanyConfig::enabled).count();
        ExecutionRun latest = runs.findTopByOrderByIdDesc().orElse(null);
        List<CompanyTask> ts = latest == null ? List.of() : tasks.findByRunId(latest.id);
        return new Summary(enabled, latest == null ? null : latest.id, latest == null ? null : latest.status,
                ts.size(), ts.stream().mapToInt(t -> t.jobsExtracted).sum(), ts.stream().mapToInt(t -> t.newJobs).sum(),
                ts.stream().filter(t -> Statuses.FAILED.equals(t.status)).count(), jobs.count(),
                cfg.importedAt(), cfg.checksum());
    }

    @GetMapping("/errors")
    public Page<CompanyTask> errors(@RequestParam(required = false) String company,
                                    @RequestParam(required = false) Long runId,
                                    @RequestParam(required = false) String code,
                                    @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.DESC) Pageable p) {
        return tasks.errors(company, runId, code, p);
    }
}
