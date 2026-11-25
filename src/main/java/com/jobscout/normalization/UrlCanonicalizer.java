package com.jobscout.normalization;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class UrlCanonicalizer {
    private static final Set<String> TRACKING = Set.of("gclid", "fbclid", "mc_cid", "mc_eid", "ref", "src", "trk");

    private UrlCanonicalizer() {}

    public static String canonicalize(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI u = URI.create(url.trim());
            if (u.getHost() == null) return url.trim();
            String query = null;
            if (u.getRawQuery() != null) {
                query = Arrays.stream(u.getRawQuery().split("&"))
                        .filter(p -> {
                            String k = p.split("=", 2)[0].toLowerCase(Locale.ROOT);
                            return !k.startsWith("utm_") && !TRACKING.contains(k);
                        })
                        .collect(Collectors.joining("&"));
                if (query.isEmpty()) query = null;
            }
            String path = u.getRawPath() == null ? "" : u.getRawPath();
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            StringBuilder sb = new StringBuilder(u.getScheme().toLowerCase(Locale.ROOT)).append("://")
                    .append(u.getHost().toLowerCase(Locale.ROOT));
            if (u.getPort() > 0) sb.append(':').append(u.getPort());
            sb.append(path);
            if (query != null) sb.append('?').append(query);
            return sb.toString();
        } catch (IllegalArgumentException e) {
            return url.trim();
        }
    }

    public static String absolute(String base, String href) {
        if (href == null) return null;
        try { return URI.create(base).resolve(href.trim()).toString(); }
        catch (Exception e) { return href.trim(); }
    }
}
