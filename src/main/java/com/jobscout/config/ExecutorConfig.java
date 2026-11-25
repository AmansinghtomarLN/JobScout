package com.jobscout.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {
    /** Bounded worker pool: one Playwright browser per running task, so this caps browser processes. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService jobExecutor(JobScoutProperties props) {
        return Executors.newFixedThreadPool(Math.max(1, props.engine().maxConcurrentTasks()), r -> {
            Thread t = new Thread(r, "job-task");
            t.setDaemon(true);
            return t;
        });
    }
}
