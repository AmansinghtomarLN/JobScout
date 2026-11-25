package com.jobscout.engine;

import com.jobscout.configuration.model.ConfigModel.SearchFieldConfig;
import com.jobscout.configuration.model.ConfigModel.SelectorType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.Locale;

final class Locators {
    private Locators() {}

    /** Selector string for scoped lookups (extraction/pagination). XPath may be given with or without type. */
    static String scoped(SelectorType type, String selector) {
        if (type == SelectorType.XPATH || selector.startsWith("//") || selector.startsWith("(//")) {
            return "xpath=" + selector;
        }
        return selector;
    }

    /** Resolves a search field to exactly one element using its configured selector strategy. */
    static Locator field(Page page, SearchFieldConfig f) {
        String s = f.selector();
        return switch (f.selectorType()) {
            case CSS, XPATH -> page.locator(scoped(f.selectorType(), s)).first();
            case LABEL -> page.getByLabel(s).first();
            case ROLE -> {
                String[] p = s.split(":", 2);
                Page.GetByRoleOptions o = new Page.GetByRoleOptions();
                if (p.length > 1) o.setName(p[1].trim());
                yield page.getByRole(AriaRole.valueOf(p[0].trim().toUpperCase(Locale.ROOT)), o).first();
            }
        };
    }
}
