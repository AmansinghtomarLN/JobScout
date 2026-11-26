package com.jobscout.orchestration;

import com.jobscout.configuration.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-syncs the workbook and starts a run for all enabled companies on a cron schedule.
 * Enable with jobscout.schedule.enabled=true. Run a single instance (no distributed lock included).
 */
@Component
@ConditionalOnProperty(name = "jobscout.schedule.enabled", havingValue = "true")
public class ScheduledRuns {
    private static final Logger log = LoggerFactory.getLogger(ScheduledRuns.class);
    private final ConfigService config;
    private final RunService runs;

    public ScheduledRuns(ConfigService config, RunService runs) {
        this.config = config;
        this.runs = runs;
    }

    @Scheduled(cron = "${jobscout.schedule.cron:0 0 7 * * *}", zone = "${jobscout.schedule.zone:Asia/Kolkata}")
    public void daily() {
        try {
            config.sync();
            var run = runs.start(new RunService.RunRequest(null, null, false, null, "SCHEDULED"));
            log.info("Scheduled run {} started", run.id);
        } catch (RuntimeException e) {
            log.error("Scheduled run failed to start: {}", e.getMessage());
        }
    }
}
