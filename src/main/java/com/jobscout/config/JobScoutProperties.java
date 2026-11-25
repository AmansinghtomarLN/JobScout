package com.jobscout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "jobscout")
public record JobScoutProperties(@DefaultValue Workbook workbook, @DefaultValue Engine engine) {

    public record Workbook(@DefaultValue("local") String source,
                           @DefaultValue("./config/jobscout-config.xlsx") String localPath,
                           String driveFileId,
                           String credentialsPath) {}

    public record Engine(@DefaultValue("3") int maxConcurrentTasks,
                         @DefaultValue("1") int maxTasksPerDomain,
                         @DefaultValue("true") boolean headless,
                         @DefaultValue("2") int defaultRetries,
                         @DefaultValue("true") boolean honorRobots,
                         @DefaultValue("JobScoutBot/0.1") String userAgent) {}
}
