package com.jobscout.engine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Minimal robots.txt check (User-agent: * and our own agent token). Fails open if robots.txt is unreachable. */
@Component
public class RobotsChecker {
    private record Rules(List<String> allow, List<String> disallow) {}

    private final Map<String, Rules> cache = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public boolean isAllowed(String url, String userAgent) {
        URI u = URI.create(url);
        String origin = u.getScheme() + "://" + u.getAuthority();
        Rules rules = cache.computeIfAbsent(origin, o -> load(o, userAgent));
        String path = (u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath())
                + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
        int allow = longest(rules.allow(), path);
        int disallow = longest(rules.disallow(), path);
        return disallow < 0 || allow >= disallow;
    }

    private static int longest(List<String> prefixes, String path) {
        int best = -1;
        for (String p : prefixes) if (!p.isEmpty() && path.startsWith(p)) best = Math.max(best, p.length());
        return best;
    }

    private Rules load(String origin, String userAgent) {
        List<String> allow = new ArrayList<>(), disallow = new ArrayList<>();
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(origin + "/robots.txt"))
                    .timeout(Duration.ofSeconds(10)).header("User-Agent", userAgent).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return new Rules(allow, disallow);
            String token = userAgent.split("[/ ]")[0].toLowerCase(Locale.ROOT);
            boolean applies = false, inAgents = false;
            for (String line : r.body().split("\\R")) {
                String l = line.replaceAll("#.*", "").trim();
                int i = l.indexOf(':');
                if (i < 0) continue;
                String k = l.substring(0, i).trim().toLowerCase(Locale.ROOT), val = l.substring(i + 1).trim();
                if (k.equals("user-agent")) {
                    if (!inAgents) applies = false;
                    inAgents = true;
                    if (val.equals("*") || val.toLowerCase(Locale.ROOT).contains(token)) applies = true;
                } else {
                    inAgents = false;
                    if (applies && k.equals("allow")) allow.add(val);
                    if (applies && k.equals("disallow")) disallow.add(val);
                }
            }
        } catch (Exception e) {
            // unreachable robots.txt: fail open
        } 
        return new Rules(allow, disallow);
    }
}
