package com.jobscout.engine;

import com.jobscout.common.ErrorCode;
import com.jobscout.common.JobScoutException;
import com.microsoft.playwright.Page;
import java.util.List;
import java.util.Locale;

/** Detects CAPTCHA / access-denied pages. The engine stops; it never tries to bypass them. */
final class BlockDetector {
    private static final List<String> MARKERS = List.of("captcha", "verify you are human", "access denied",
            "unusual traffic", "are you a robot", "request blocked");

    private BlockDetector() {}

    static void check(Page page) {
        String text;
        try {
            String body = page.locator("body").innerText();
            text = (page.title() + " " + body.substring(0, Math.min(1500, body.length()))).toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return;
        }
        for (String m : MARKERS) {
            if (text.contains(m)) {
                throw new JobScoutException(ErrorCode.BOT_PROTECTION,
                        "Site appears to use bot protection ('" + m + "'); marking unsupported, not bypassing", false);
            }
        }
    }
}
