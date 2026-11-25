package com.jobscout.engine;

import com.jobscout.common.ErrorCode;
import com.jobscout.common.JobScoutException;
import com.jobscout.config.JobScoutProperties;
import com.jobscout.configuration.model.ConfigModel.CompanyConfig;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import org.springframework.stereotype.Component;

/** Config-driven engine: navigate -> search -> crawl -> scrape. No company-specific code lives here. */
@Component
public class PlaywrightJobEngine {
    private final JobScoutProperties props;
    private final RobotsChecker robots;
    private final SearchAutomation search;
    private final ResultCrawler crawler;
    private final DetailScraper detailScraper;

    public PlaywrightJobEngine(JobScoutProperties props, RobotsChecker robots, SearchAutomation search,
                               ResultCrawler crawler, DetailScraper detailScraper) {
        this.props = props;
        this.robots = robots;
        this.search = search;
        this.crawler = crawler;
        this.detailScraper = detailScraper;
    }

    public EngineResult run(CompanyConfig c, TaskContext ctx) {
        String url = Templates.url(c.careerUrl(), c.profile());
        if (props.engine().honorRobots() && !robots.isAllowed(url, props.engine().userAgent())) {
            throw new JobScoutException(ErrorCode.ROBOTS_DISALLOWED, "robots.txt disallows " + url, false);
        }
        try (BrowserSession session = new BrowserSession(props.engine(), c.pageTimeoutSeconds() * 1000)) {
            Page page = session.newPage();

            ctx.stage("NAVIGATE");
            try {
                page.navigate(url);
            } catch (PlaywrightException e) {
                throw new JobScoutException(ErrorCode.NAVIGATION_TIMEOUT, "Navigation failed: " + SearchAutomation.firstLine(e),
                        e instanceof TimeoutError);
            }
            BlockDetector.check(page);

            ctx.stage("SEARCH");
            try {
                search.apply(page, c, ctx);
            } catch (TimeoutError e) {
                throw new JobScoutException(ErrorCode.SEARCH_FAILED, "Search timed out: " + SearchAutomation.firstLine(e), true);
            }
            BlockDetector.check(page);

            ctx.stage("CRAWL");
            ResultCrawler.CrawlResult crawl;
            try {
                crawl = crawler.crawl(page, c, ctx);
            } catch (PlaywrightException e) {
                throw new JobScoutException(ErrorCode.PAGINATION_FAILED, "Crawl failed: " + SearchAutomation.firstLine(e), true);
            }

            ctx.stage("SCRAPE");
            detailScraper.scrape(page, crawl.jobs(), c, ctx);
            return new EngineResult(crawl.jobs(), crawl.pages());
        }
    }
}
