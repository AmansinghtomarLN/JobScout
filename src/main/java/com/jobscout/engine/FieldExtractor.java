package com.jobscout.engine;

import com.jobscout.configuration.model.ConfigModel.ExtractionFieldConfig;
import com.jobscout.normalization.UrlCanonicalizer;
import com.microsoft.playwright.Locator;
import java.util.*;

final class FieldExtractor {
    private FieldExtractor() {}

    record Extracted(Map<String, String> values, List<String> missingRequired) {}

    /** Extracts all configured fields relative to {@code scope}. Selector "self" targets the scope itself. */
    static Extracted extractAll(Locator scope, List<ExtractionFieldConfig> fields, String baseUrl) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (ExtractionFieldConfig f : fields) {
            String v = null;
            try { v = extractOne(scope, f, baseUrl); } catch (RuntimeException ignored) { /* treated as missing */ }
            if (v == null || v.isBlank()) {
                if (f.required()) missing.add(f.fieldKey());
            } else {
                values.put(f.fieldKey(), v);
            }
        }
        return new Extracted(values, missing);
    }

    private static String extractOne(Locator scope, ExtractionFieldConfig f, String baseUrl) {
        Locator target = "self".equalsIgnoreCase(f.selector())
                ? scope : scope.locator(Locators.scoped(f.selectorType(), f.selector())).first();
        if (target.count() == 0) return null;
        String attr = f.attribute();
        String v;
        if (attr == null || attr.isBlank() || attr.equalsIgnoreCase("textContent")) v = target.textContent();
        else if (attr.equalsIgnoreCase("innerText")) v = target.innerText();
        else v = target.getAttribute(attr);
        if (v == null) return null;
        v = v.strip();
        if (f.transform() != null) {
            for (String t : f.transform().split(",")) {
                switch (t.trim().toUpperCase(Locale.ROOT)) {
                    case "TRIM" -> v = v.replaceAll("\\s+", " ").strip();
                    case "ABSOLUTE_URL" -> v = UrlCanonicalizer.absolute(baseUrl, v);
                    default -> { /* DATE_PARSE handled by the normalizer */ }
                }
            }
        }
        return v;
    }
}
