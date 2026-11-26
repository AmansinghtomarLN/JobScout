package com.jobscout.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscout.config.JobScoutProperties;
import com.jobscout.persistence.entity.ExecutionRun;
import com.jobscout.persistence.entity.JobPosting;
import com.jobscout.persistence.repository.JobPostingRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Posts a "new jobs" digest to a Slack incoming webhook after a run. Never fails the run. */
@Component
public class DigestNotifier {
    private static final Logger log = LoggerFactory.getLogger(DigestNotifier.class);
    private final JobScoutProperties props;
    private final JobPostingRepository jobs;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public DigestNotifier(JobScoutProperties props, JobPostingRepository jobs, ObjectMapper mapper) {
        this.props = props;
        this.jobs = jobs;
        this.mapper = mapper;
    }

    public void notifyRun(ExecutionRun run) {
        String hook = props.notify().slackWebhookUrl();
        if (hook == null || hook.isBlank() || run.newJobs == 0) return;
        try {
            List<JobPosting> fresh = jobs.findTop20ByFirstRunIdOrderByPostedDateDesc(run.id);
            StringBuilder sb = new StringBuilder("*JobScout run #").append(run.id).append("*: ")
                    .append(run.newJobs).append(" new jobs (").append(run.status).append(")\n");
            for (JobPosting j : fresh) {
                sb.append("• <").append(j.canonicalUrl).append("|").append(j.title).append("> — ")
                        .append(j.companyName).append(j.location == null ? "" : ", " + j.location).append('\n');
            }
            if (run.newJobs > fresh.size()) sb.append("…and ").append(run.newJobs - fresh.size()).append(" more");
            http.send(HttpRequest.newBuilder(URI.create(hook)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("text", sb.toString()))))
                    .build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("Digest notification failed: {}", e.getMessage());
        }
    }
}
