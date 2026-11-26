package com.jobscout.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscout.config.JobScoutProperties;
import com.jobscout.configuration.model.ConfigModel.CompanyConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

/** Lever public Postings API. career_url example: https://jobs.lever.co/{company} */
@Component
public class LeverSource implements JobSource {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;
    private final JobScoutProperties props;

    public LeverSource(ObjectMapper mapper, JobScoutProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    @Override public String key() { return "LEVER"; }

    @Override
    public EngineResult fetch(CompanyConfig c, TaskContext ctx) {
        ctx.stage("API");
        String token = HttpJson.boardToken(c.careerUrl());
        JsonNode root = HttpJson.get(http, mapper, "https://api.lever.co/v0/postings/" + token + "?mode=json",
                props.engine().userAgent(), c.pageTimeoutSeconds());
        List<RawJob> jobs = parse(root, c);
        ctx.progress(1, jobs.size());
        return new EngineResult(jobs, 1);
    }

    static List<RawJob> parse(JsonNode root, CompanyConfig c) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root) {
            String title = j.path("text").asText(null);
            String desc = j.path("descriptionPlain").asText(null);
            String loc = j.path("categories").path("location").asText(null);
            if (!JobMatcher.matches(c.profile(), title, desc, loc)) continue;
            String url = j.path("hostedUrl").asText(null);
            Map<String, String> v = new LinkedHashMap<>();
            put(v, "job_title", title);
            put(v, "job_id", j.path("id").asText(null));
            put(v, "location", loc);
            put(v, "employment_type", j.path("categories").path("commitment").asText(null));
            put(v, "job_url", url);
            put(v, "description", desc);
            if (j.hasNonNull("createdAt")) v.put("posted_date", j.get("createdAt").asText());   // epoch millis
            out.add(new RawJob(v, url));
            if (out.size() >= c.maxJobs()) break;
        }
        return out;
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }
}
