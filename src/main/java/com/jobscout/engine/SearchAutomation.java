package com.jobscout.engine;

import com.jobscout.common.ErrorCode;
import com.jobscout.common.JobScoutException;
import com.jobscout.configuration.model.ConfigModel.CompanyConfig;
import com.jobscout.configuration.model.ConfigModel.SearchFieldConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import org.springframework.stereotype.Component;

/** Applies the configured search fields (fill / select / check / click) in workbook order. */
@Component
public class SearchAutomation {

    public void apply(Page page, CompanyConfig c, TaskContext ctx) {
        int requiredWaitMs = c.pageTimeoutSeconds() * 1000;
        for (SearchFieldConfig f : c.searchFields()) {
            if (ctx.cancelled()) return;
            String value = Templates.text(f.value(), c.profile());
            Locator loc = Locators.field(page, f);
            try {
                loc.waitFor(new Locator.WaitForOptions().setTimeout(f.required() ? requiredWaitMs : 3000));
            } catch (TimeoutError e) {
                if (f.required()) {
                    throw new JobScoutException(ErrorCode.SELECTOR_NOT_FOUND,
                            "Required search field '" + f.fieldKey() + "' not found: " + f.selector(), false);
                }
                ctx.event("WARN", "SEARCH", ErrorCode.SELECTOR_NOT_FOUND.name(),
                        "Optional search field '" + f.fieldKey() + "' not found; skipped");
                continue;
            }
            try {
                switch (f.action()) {
                    case FILL -> loc.fill(value);
                    case SELECT -> select(page, loc, value);
                    case CHECK -> loc.check();
                    case CLICK -> loc.click();
                }
            } catch (PlaywrightException e) {
                if (f.required()) {
                    throw new JobScoutException(ErrorCode.SEARCH_FAILED,
                            "Action " + f.action() + " failed on '" + f.fieldKey() + "': " + firstLine(e), true);
                }
                ctx.event("WARN", "SEARCH", ErrorCode.SEARCH_FAILED.name(),
                        "Optional field '" + f.fieldKey() + "' action failed: " + firstLine(e));
            }
            if (f.waitAfterMs() > 0) page.waitForTimeout(f.waitAfterMs());
        }
    }

    /** Native <select> first; otherwise treat as a custom dropdown and click the visible option text. */
    private void select(Page page, Locator loc, String value) {
        try {
            loc.selectOption(value);
        } catch (PlaywrightException e) {
            loc.click();
            page.getByText(value).first().click();
        }
    }

    static String firstLine(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m.lines().findFirst().orElse(m);
    }
}
