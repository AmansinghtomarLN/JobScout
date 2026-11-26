package com.jobscout.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscout.config.JobScoutProperties;
import com.jobscout.configuration.model.ConfigModel.CompanyConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

/** Greenhouse public Job Board API. career_url example: https://boards.greenhouse.io/{board_token} */
@Component
public class GreenhouseSource implements JobSource {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;
    private final JobScoutProperties props;

    public GreenhouseSource(ObjectMapper mapper, JobScoutProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    @Override public String key() { return "GREENHOUSE"; }

    @Override
    public EngineResult fetch(CompanyConfig c, TaskContext ctx) {
        ctx.stage("API");
        String token = HttpJson.boardToken(c.careerUrl());
        JsonNode root = HttpJson.get(http, mapper,
                "https://boards-api.greenhouse.io/v1/boards/" + token + "/jobs?content=true",
                props.engine().userAgent(), c.pageTimeoutSeconds());
        List<RawJob> jobs = parse(root, c);
        ctx.progress(1, jobs.size());
        return new EngineResult(jobs, 1);
    }

    static List<RawJob> parse(JsonNode root, CompanyConfig c) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root.path("jobs")) {
            String title = j.path("title").asText(null);
            String desc = JobMatcher.stripHtml(j.path("content").asText(null));
            String loc = j.path("location").path("name").asText(null);
            if (!JobMatcher.matches(c.profile(), title, desc, loc)) continue;
            String url = j.path("absolute_url").asText(null);
            Map<String, String> v = new LinkedHashMap<>();
            put(v, "job_title", title);
            put(v, "job_id", j.path("id").asText(null));
            put(v, "location", loc);
            put(v, "job_url", url);
            put(v, "description", desc);
            put(v, "posted_date", j.hasNonNull("first_published") ? j.get("first_published").asText() : j.path("updated_at").asText(null));
            out.add(new RawJob(v, url));
            if (out.size() >= c.maxJobs()) break;
        }
        return out;
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }
}
