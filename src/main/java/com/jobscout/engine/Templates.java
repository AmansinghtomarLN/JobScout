package com.jobscout.engine;

import com.jobscout.configuration.model.ConfigModel.SearchProfile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Placeholder substitution: ${keyword}, ${location}, ${posted_days}. */
final class Templates {
    private Templates() {}

    static String text(String t, SearchProfile p) {
        if (t == null || p == null) return t;
        return t.replace("${keyword}", nz(p.keyword())).replace("${location}", nz(p.location()))
                .replace("${posted_days}", String.valueOf(p.postedDays()));
    }

    static String url(String t, SearchProfile p) {
        if (t == null || p == null) return t;
        return t.replace("${keyword}", enc(p.keyword())).replace("${location}", enc(p.location()))
                .replace("${posted_days}", String.valueOf(p.postedDays()));
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String enc(String s) { return URLEncoder.encode(nz(s), StandardCharsets.UTF_8); }
}
