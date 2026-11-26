package com.jobscout.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscout.common.ErrorCode;
import com.jobscout.common.JobScoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Small JSON GET helper that maps HTTP failures to JobScout error codes. */
final class HttpJson {
    private HttpJson() {}

    static JsonNode get(HttpClient http, ObjectMapper mapper, String url, String userAgent, int timeoutSeconds) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds)).header("Accept", "application/json")
                    .header("User-Agent", userAgent).GET().build(), HttpResponse.BodyHandlers.ofString());
            int s = r.statusCode();
            if (s == 429) throw new JobScoutException(ErrorCode.RATE_LIMITED, "HTTP 429 from " + url, true);
            if (s == 404) throw new JobScoutException(ErrorCode.CONFIG_INVALID, "Board not found (404): " + url, false);
            if (s >= 500) throw new JobScoutException(ErrorCode.NAVIGATION_TIMEOUT, "HTTP " + s + " from " + url, true);
            if (s != 200) throw new JobScoutException(ErrorCode.SEARCH_FAILED, "HTTP " + s + " from " + url, false);
            return mapper.readTree(r.body());
        } catch (JobScoutException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JobScoutException(ErrorCode.NAVIGATION_TIMEOUT, "Interrupted", false);
        } catch (Exception e) {
            throw new JobScoutException(ErrorCode.NAVIGATION_TIMEOUT, "Request failed: " + e.getMessage(), true);
        }
    }

    /** First path segment of a board URL, e.g. https://boards.greenhouse.io/acme/jobs -> acme. */
    static String boardToken(String careerUrl) {
        String path = URI.create(careerUrl).getPath();
        for (String seg : path.split("/")) if (!seg.isBlank()) return seg;
        throw new JobScoutException(ErrorCode.CONFIG_INVALID, "Cannot derive board token from " + careerUrl, false);
    }
}
