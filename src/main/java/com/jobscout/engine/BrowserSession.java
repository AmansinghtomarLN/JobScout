package com.jobscout.engine;

import com.jobscout.config.JobScoutProperties;
import com.microsoft.playwright.*;

/**
 * One Playwright instance + browser + isolated context per task. Playwright for Java is not thread-safe,
 * so instances are never shared between worker threads; the bounded worker pool caps concurrent browsers.
 */
public final class BrowserSession implements AutoCloseable {
    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;

    public BrowserSession(JobScoutProperties.Engine cfg, int timeoutMs) {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(cfg.headless()));
        Browser.NewContextOptions o = new Browser.NewContextOptions().setLocale("en-US");
        if (cfg.userAgent() != null && !cfg.userAgent().isBlank()) o.setUserAgent(cfg.userAgent());
        context = browser.newContext(o);
        context.setDefaultTimeout(timeoutMs);
        context.setDefaultNavigationTimeout(timeoutMs);
    }

    public Page newPage() {
        return context.newPage();
    }

    @Override
    public void close() {
        try { context.close(); } catch (RuntimeException ignored) { }
        try { browser.close(); } catch (RuntimeException ignored) { }
        try { playwright.close(); } catch (RuntimeException ignored) { }
    }
}
