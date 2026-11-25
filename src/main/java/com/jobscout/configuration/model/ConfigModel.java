package com.jobscout.configuration.model;

import java.util.List;

/** Typed, immutable configuration parsed from the Excel workbook. */
public final class ConfigModel {
    private ConfigModel() {}

    public enum SelectorType { CSS, XPATH, LABEL, ROLE }
    public enum InteractionType { PLAYWRIGHT, API }
    public enum InputAction { FILL, SELECT, CHECK, CLICK }
    public enum PaginationType { NEXT_BUTTON, PAGE_NUMBER, INFINITE_SCROLL, API_CURSOR, NONE }
    public enum WaitCondition { NETWORK_IDLE, SELECTOR, TIMEOUT }
    public enum FieldScope { RESULT_CARD, DETAIL_PAGE }
    public enum Severity { ERROR, WARN }

    public record SearchProfile(String profileId, String keyword, String location, int postedDays) {}

    public record SearchFieldConfig(String companyId, String profileId, String fieldKey, SelectorType selectorType,
                                    String selector, InputAction action, String value, boolean required,
                                    int waitAfterMs) {}

    public record ExtractionFieldConfig(String companyId, String fieldKey, FieldScope scope, SelectorType selectorType,
                                        String selector, String attribute, boolean required, String transform) {}

    public record PaginationConfig(PaginationType type, String nextSelector, String disabledSelector, int maxPages,
                                   WaitCondition waitCondition, String resultCardSelector) {}

    public record CompanyConfig(String companyId, String companyName, String careerUrl, boolean enabled,
                                InteractionType interactionType, String searchProfileId, int maxPages, int maxJobs,
                                int pageTimeoutSeconds, long rateLimitMs, String adapterKey, String timezone,
                                String dateFormat, SearchProfile profile, List<SearchFieldConfig> searchFields,
                                List<ExtractionFieldConfig> extractionFields, PaginationConfig pagination) {
        public CompanyConfig withMaxPages(int n) {
            return new CompanyConfig(companyId, companyName, careerUrl, enabled, interactionType, searchProfileId, n,
                    maxJobs, pageTimeoutSeconds, rateLimitMs, adapterKey, timezone, dateFormat, profile,
                    searchFields, extractionFields, pagination);
        }
    }

    public record ConfigIssue(Severity severity, String companyId, String sheet, String message) {}

    public record ParsedConfig(List<CompanyConfig> companies, List<ConfigIssue> issues) {}
}
