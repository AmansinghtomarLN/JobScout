package com.jobscout;

import static com.jobscout.configuration.model.ConfigModel.*;
import static org.junit.jupiter.api.Assertions.*;

import com.jobscout.configuration.ConfigValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigValidatorTest {
    private final ConfigValidator v = new ConfigValidator();

    private CompanyConfig company(String cardSel, List<ExtractionFieldConfig> ex, SearchProfile profile) {
        return new CompanyConfig("ACME", "Acme", "https://jobs.acme.test/?q=${keyword}", true, InteractionType.PLAYWRIGHT,
                "P1", 5, 100, 30, 0, null, "UTC", null, profile, List.of(), ex,
                new PaginationConfig(PaginationType.NEXT_BUTTON, "a.next", null, 5, WaitCondition.SELECTOR, cardSel));
    }

    private static ExtractionFieldConfig f(String key, String sel) {
        return new ExtractionFieldConfig("ACME", key, FieldScope.RESULT_CARD, SelectorType.CSS, sel, null, true, null);
    }

    @Test void validConfigHasNoIssues() {
        var c = company("div.card", List.of(f("job_title", "h2"), f("job_url", "a")), new SearchProfile("P1", "Java", "India", 7));
        assertTrue(v.validate(List.of(c)).isEmpty());
    }

    @Test void reportsMissingFieldsBadSelectorAndMissingProfile() {
        var c = company("div[class='x'", List.of(f("job_title", "h2")), null);
        var msgs = v.validate(List.of(c)).stream().map(ConfigIssue::message).toList();
        assertTrue(msgs.stream().anyMatch(m -> m.contains("job_url")));
        assertTrue(msgs.stream().anyMatch(m -> m.contains("Invalid selector")));
        assertTrue(msgs.stream().anyMatch(m -> m.contains("search_profile_id")));
    }

    @Test void xpathSyntaxChecked() {
        assertNull(ConfigValidator.selectorProblem(SelectorType.XPATH, ".//a[@href]"));
        assertNotNull(ConfigValidator.selectorProblem(SelectorType.XPATH, ".//a[@href"));
    }
}
