package com.jobscout.engine;

import com.jobscout.common.JobScoutException;
import com.jobscout.configuration.model.ConfigModel.*;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import java.util.List;
import org.springframework.stereotype.Component;

/** Optional second phase: opens each discovered job URL (rate limited) and extracts DETAIL_PAGE fields. */
@Component
public class DetailScraper {

    public void scrape(Page listPage, List<RawJob> jobs, CompanyConfig c, TaskContext ctx) {
        List<ExtractionFieldConfig> fields = c.extractionFields().stream()
                .filter(f -> f.scope() == FieldScope.DETAIL_PAGE).toList();
        if (fields.isEmpty()) return;
        for (RawJob job : jobs) {
            if (ctx.cancelled() || job.url() == null) continue;
            Page detail = listPage.context().newPage();
            try {
                detail.navigate(job.url());
                BlockDetector.check(detail);
                FieldExtractor.Extracted ex = FieldExtractor.extractAll(detail.locator("body"), fields, detail.url());
                job.values().putAll(ex.values());
                if (!ex.missingRequired().isEmpty()) {
                    ctx.event("WARN", "SCRAPE", "EXTRACTION_FAILED",
                            "Detail fields missing " + ex.missingRequired() + " for " + job.url());
                }
            } catch (JobScoutException e) {
                ctx.event("WARN", "SCRAPE", e.code().name(), "Detail scraping stopped: " + e.getMessage());
                return;                                   // keep list-level data already collected
            } catch (PlaywrightException e) {
                ctx.event("WARN", "SCRAPE", "EXTRACTION_FAILED", "Detail page failed: " + SearchAutomation.firstLine(e));
            } finally {
                detail.close();
            }
            ResultCrawler.sleep(c.rateLimitMs());
        }
    }
}
