package com.jobscout.engine;

import com.jobscout.common.ErrorCode;
import com.jobscout.common.JobScoutException;
import com.jobscout.configuration.model.ConfigModel.*;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.jobscout.normalization.UrlCanonicalizer;
import java.util.*;
import org.springframework.stereotype.Component;

/** Walks result pages (next button / page number / infinite scroll) and extracts result-card fields. */
@Component
public class ResultCrawler {

    public record CrawlResult(List<RawJob> jobs, int pages) {}

    public CrawlResult crawl(Page page, CompanyConfig c, TaskContext ctx) {
        PaginationConfig pg = c.pagination();
        List<ExtractionFieldConfig> fields = c.extractionFields().stream()
                .filter(f -> f.scope() == FieldScope.RESULT_CARD).toList();
        int timeoutMs = c.pageTimeoutSeconds() * 1000;
        Set<String> seen = new HashSet<>();
        List<RawJob> jobs = new ArrayList<>();
        int pages = 0, misses = 0;

        while (pages < c.maxPages() && jobs.size() < c.maxJobs() && !ctx.cancelled()) {
            waitForCards(page, pg, timeoutMs, pages == 0);
            Locator cards = page.locator(Locators.scoped(SelectorType.CSS, pg.resultCardSelector()));
            int n = cards.count();
            int before = seen.size();
            for (int i = 0; i < n && jobs.size() < c.maxJobs(); i++) {
                FieldExtractor.Extracted ex = FieldExtractor.extractAll(cards.nth(i), fields, page.url());
                if (!ex.missingRequired().isEmpty()) { misses++; continue; }
                String url = ex.values().get("job_url");
                String key = url != null ? UrlCanonicalizer.canonicalize(url) : ex.values().toString();
                if (seen.add(key)) jobs.add(new RawJob(ex.values(), url));
            }
            pages++;
            ctx.progress(pages, jobs.size());
            if (seen.size() == before) break;            // no new job URLs -> stop
            if (!advance(page, pg, pages, timeoutMs, ctx)) break;
            sleep(c.rateLimitMs());
        }
        if (jobs.isEmpty() && misses > 0) {
            throw new JobScoutException(ErrorCode.EXTRACTION_FAILED,
                    "Result cards found but required fields missing on all " + misses + " cards", false);
        }
        return new CrawlResult(jobs, pages);
    }

    private void waitForCards(Page page, PaginationConfig pg, int timeoutMs, boolean first) {
        Locator card = page.locator(Locators.scoped(SelectorType.CSS, pg.resultCardSelector())).first();
        try {
            if (pg.waitCondition() == WaitCondition.NETWORK_IDLE) {
                try { page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs)); }
                catch (PlaywrightException ignored) { }
            } else if (pg.waitCondition() == WaitCondition.TIMEOUT) {
                page.waitForTimeout(2000);
            }
            if (first || pg.waitCondition() == WaitCondition.SELECTOR) {
                card.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            }
        } catch (TimeoutError e) {
            if (first) {
                throw new JobScoutException(ErrorCode.SELECTOR_NOT_FOUND,
                        "No result cards matched '" + pg.resultCardSelector()
                                + "' (zero results or invalid result_card_selector)", false);
            }
        }
    }

    private boolean advance(Page page, PaginationConfig pg, int pagesVisited, int timeoutMs, TaskContext ctx) {
        try {
            switch (pg.type()) {
                case NEXT_BUTTON -> {
                    Locator next = page.locator(Locators.scoped(SelectorType.CSS, pg.nextSelector())).first();
                    if (next.count() == 0) return false;
                    if (pg.disabledSelector() != null
                            && page.locator(Locators.scoped(SelectorType.CSS, pg.disabledSelector())).count() > 0) return false;
                    next.click();
                }
                case PAGE_NUMBER -> {
                    String sel = pg.nextSelector().replace("{n}", String.valueOf(pagesVisited + 1));
                    Locator l = page.locator(Locators.scoped(SelectorType.CSS, sel)).first();
                    if (l.count() == 0) return false;
                    l.click();
                }
                case INFINITE_SCROLL -> page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                default -> { return false; }
            }
            if (pg.waitCondition() == WaitCondition.NETWORK_IDLE) {
                try { page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs)); }
                catch (PlaywrightException ignored) { }
            } else {
                page.waitForTimeout(1000);
            }
            BlockDetector.check(page);
            return true;
        } catch (JobScoutException e) {
            throw e;
        } catch (PlaywrightException e) {
            // Keep what we already collected; record why pagination stopped.
            ctx.event("WARN", "CRAWL", ErrorCode.PAGINATION_FAILED.name(), "Pagination stopped: " + SearchAutomation.firstLine(e));
            return false;
        }
    }

    static void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
