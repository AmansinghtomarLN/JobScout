package com.jobscout.configuration;

import static com.jobscout.configuration.model.ConfigModel.*;

import java.net.URI;
import java.util.*;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.springframework.stereotype.Component;

/** Validates configuration before any browser work starts. */
@Component
public class ConfigValidator {

    public List<ConfigIssue> validate(List<CompanyConfig> companies) {
        List<ConfigIssue> out = new ArrayList<>();
        for (CompanyConfig c : companies) {
            if (!c.enabled()) continue;
            String id = c.companyId();
            if (c.careerUrl() == null || !c.careerUrl().matches("(?i)^https?://.+")) {
                err(out, id, "Companies", "career_url must be an http(s) URL");
            } else {
                try { URI.create(c.careerUrl().replace("${", "x").replace("}", "")); }
                catch (IllegalArgumentException e) { err(out, id, "Companies", "career_url is not a valid URI"); }
            }
            if (c.interactionType() == InteractionType.API) {
                err(out, id, "Companies", "interaction_type API is not implemented in this build (PLAYWRIGHT only)");
            }
            if (c.adapterKey() != null && !c.adapterKey().equalsIgnoreCase("GENERIC")) {
                out.add(new ConfigIssue(Severity.WARN, id, "Companies", "adapter_key '" + c.adapterKey() + "' ignored (no adapters registered)"));
            }
            if (c.profile() == null) {
                err(out, id, "SearchProfiles", "search_profile_id '" + c.searchProfileId() + "' not found");
            }
            PaginationConfig pg = c.pagination();
            if (pg.resultCardSelector() == null) {
                err(out, id, "Pagination", "result_card_selector is required");
            } else {
                check(out, id, "Pagination", SelectorType.CSS, pg.resultCardSelector());
            }
            switch (pg.type()) {
                case NEXT_BUTTON, PAGE_NUMBER -> {
                    if (pg.nextSelector() == null) err(out, id, "Pagination", "next_selector required for " + pg.type());
                    else check(out, id, "Pagination", SelectorType.CSS, pg.nextSelector());
                }
                case API_CURSOR -> err(out, id, "Pagination", "API_CURSOR is not supported by the Playwright engine");
                default -> { }
            }
            Set<String> keys = new HashSet<>();
            for (ExtractionFieldConfig f : c.extractionFields()) {
                if (f.fieldKey() == null) { err(out, id, "ExtractionFields", "field_key missing"); continue; }
                keys.add(f.fieldKey());
                if (f.selector() == null) err(out, id, "ExtractionFields", f.fieldKey() + ": selector missing");
                else if (f.selectorType() != SelectorType.CSS && f.selectorType() != SelectorType.XPATH)
                    err(out, id, "ExtractionFields", f.fieldKey() + ": only CSS/XPATH supported for extraction");
                else check(out, id, "ExtractionFields", f.selectorType(), f.selector());
            }
            for (String required : List.of("job_title", "job_url")) {
                if (!keys.contains(required)) err(out, id, "ExtractionFields", "field '" + required + "' is required");
            }
            for (SearchFieldConfig f : c.searchFields()) {
                String label = "SearchFields/" + f.fieldKey();
                if (f.selector() == null) { err(out, id, "SearchFields", f.fieldKey() + ": selector missing"); continue; }
                check(out, id, "SearchFields", f.selectorType(), f.selector());
                if ((f.action() == InputAction.FILL || f.action() == InputAction.SELECT) && f.value() == null)
                    err(out, id, "SearchFields", label + ": input_value required for " + f.action());
            }
        }
        return out;
    }

    private void check(List<ConfigIssue> out, String id, String sheet, SelectorType type, String selector) {
        String problem = selectorProblem(type, selector);
        if (problem != null) err(out, id, sheet, "Invalid selector '" + selector + "': " + problem);
    }

    /** Lightweight syntax check; Playwright extensions (:has-text, role= ...) are tolerated. */
    public static String selectorProblem(SelectorType type, String s) {
        if (s == null || s.isBlank()) return "empty";
        switch (type) {
            case XPATH -> {
                try { XPathFactory.newInstance().newXPath().compile(s); }
                catch (XPathExpressionException e) { return "bad XPath"; }
            }
            case CSS -> {
                if (s.startsWith("//") || s.startsWith("(//")) return null; // treated as XPath by the engine
                Deque<Character> stack = new ArrayDeque<>();
                char quote = 0;
                for (char ch : s.toCharArray()) {
                    if (quote != 0) { if (ch == quote) quote = 0; continue; }
                    if (ch == '"' || ch == '\'') quote = ch;
                    else if (ch == '(' || ch == '[') stack.push(ch);
                    else if (ch == ')' || ch == ']') {
                        if (stack.isEmpty() || (ch == ')') != (stack.pop() == '(')) return "unbalanced brackets";
                    }
                }
                if (quote != 0 || !stack.isEmpty()) return "unbalanced quotes/brackets";
            }
            case ROLE -> {
                String role = s.split(":", 2)[0].trim();
                try { com.microsoft.playwright.options.AriaRole.valueOf(role.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { return "unknown ARIA role '" + role + "' (format role[:name])"; }
            }
            default -> { }
        }
        return null;
    }

    private static void err(List<ConfigIssue> out, String id, String sheet, String msg) {
        out.add(new ConfigIssue(Severity.ERROR, id, sheet, msg));
    }
}
