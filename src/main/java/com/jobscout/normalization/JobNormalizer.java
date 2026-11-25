package com.jobscout.normalization;

import com.jobscout.configuration.model.ConfigModel.CompanyConfig;
import com.jobscout.engine.RawJob;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class JobNormalizer {
    private final DateParser dateParser;

    public JobNormalizer(DateParser dateParser) {
        this.dateParser = dateParser;
    }

    /** Returns null when the record has no usable title or URL. */
    public JobDraft normalize(CompanyConfig c, RawJob raw) {
        var v = raw.values();
        String title = v.get("job_title");
        String url = UrlCanonicalizer.canonicalize(raw.url());
        if ((title == null || title.isBlank()) && url == null) return null;

        ZoneId zone;
        try { zone = ZoneId.of(c.timezone()); } catch (DateTimeException e) { zone = ZoneId.of("UTC"); }
        String rawDate = v.get("posted_date");
        DateParser.Result d = dateParser.parse(rawDate, c.dateFormat(), zone, LocalDate.now(zone));

        return new JobDraft(c.companyId(), c.companyName(), blankToNull(v.get("job_id")), title,
                v.get("location"), v.get("description"), v.get("employment_type"), url, d.date(), rawDate, d.status(),
                c.profile().keyword(), c.profile().location(), c.profile().postedDays(), v);
    }

    /** Unknown/failed dates are kept (flagged) so the user decides; known dates outside the window are rejected. */
    public boolean withinWindow(JobDraft d) {
        if (d.postedDate() == null) return true;
        return !d.postedDate().isBefore(LocalDate.now().minusDays(d.postedDays() + 1L));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
