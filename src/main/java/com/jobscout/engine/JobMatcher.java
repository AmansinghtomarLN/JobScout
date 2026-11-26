package com.jobscout.engine;

import com.jobscout.configuration.model.ConfigModel.SearchProfile;
import java.util.Locale;

/** Client-side keyword/location filter for API sources that return a company's whole board. */
final class JobMatcher {
    private JobMatcher() {}

    static boolean matches(SearchProfile p, String title, String description, String location) {
        if (p == null) return true;
        if (p.keyword() != null && !p.keyword().isBlank()) {
            String k = p.keyword().toLowerCase(Locale.ROOT);
            String hay = (nz(title) + " " + nz(description)).toLowerCase(Locale.ROOT);
            if (!hay.contains(k)) return false;
        }
        if (p.location() != null && !p.location().isBlank() && location != null && !location.isBlank()) {
            return location.toLowerCase(Locale.ROOT).contains(p.location().toLowerCase(Locale.ROOT));
        }
        return true;
    }

    static String stripHtml(String s) {
        if (s == null) return null;
        String t = s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("&nbsp;", " ");
        return t.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
