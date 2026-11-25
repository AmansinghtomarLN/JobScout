package com.jobscout.api;

import com.jobscout.configuration.ConfigService;
import com.jobscout.configuration.ConfigService.LoadedConfig;
import com.jobscout.configuration.model.ConfigModel.CompanyConfig;
import com.jobscout.configuration.model.ConfigModel.ConfigIssue;
import com.jobscout.persistence.repository.CompanyTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ConfigController {
    private final ConfigService config;
    private final CompanyTaskRepository tasks;

    public ConfigController(ConfigService config, CompanyTaskRepository tasks) {
        this.config = config;
        this.tasks = tasks;
    }

    public record SyncResponse(long snapshotId, String checksum, String source, Instant importedAt, int companies,
                               long errors, List<ConfigIssue> issues) {}

    public record CompanyRow(String companyId, String companyName, String careerUrl, boolean enabled,
                             String interactionType, String searchProfileId, boolean configValid,
                             String lastRunStatus, Long lastRunId) {}

    @PostMapping("/configurations/sync")
    public SyncResponse sync() { return toResponse(config.sync()); }

    @PostMapping("/configurations/validate")
    public SyncResponse validate() { return toResponse(config.current()); }

    @GetMapping("/companies")
    public List<CompanyRow> companies() {
        LoadedConfig cfg = config.current();
        return cfg.companies().stream().map(c -> {
            var last = tasks.findTopByCompanyIdOrderByIdDesc(c.companyId());
            return new CompanyRow(c.companyId(), c.companyName(), c.careerUrl(), c.enabled(), c.interactionType().name(),
                    c.searchProfileId(), cfg.errorsFor(c.companyId()).isEmpty(),
                    last.map(t -> t.status).orElse(null), last.map(t -> t.runId).orElse(null));
        }).toList();
    }

    @GetMapping("/companies/{id}")
    public CompanyConfig company(@PathVariable String id) {
        return config.current().companies().stream().filter(c -> c.companyId().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Company " + id));
    }

    private SyncResponse toResponse(LoadedConfig l) {
        long errors = l.issues().stream().filter(i -> i.severity() == com.jobscout.configuration.model.ConfigModel.Severity.ERROR).count();
        return new SyncResponse(l.snapshotId(), l.checksum(), l.source(), l.importedAt(), l.companies().size(), errors, l.issues());
    }
}
