package com.jobscout;

import static org.junit.jupiter.api.Assertions.*;

import com.jobscout.normalization.DateParser;
import com.jobscout.normalization.DateParser.Status;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DateParserTest {
    private final DateParser p = new DateParser();
    private final LocalDate today = LocalDate.of(2026, 11, 26);
    private final ZoneId utc = ZoneId.of("UTC");

    @Test void blankIsUnknown() {
        assertEquals(Status.UNKNOWN, p.parse("  ", null, utc, today).status());
        assertEquals(Status.UNKNOWN, p.parse(null, null, utc, today).status());
    }

    @Test void isoDate() {
        var r = p.parse("2026-11-20", null, utc, today);
        assertEquals(LocalDate.of(2026, 11, 20), r.date());
        assertEquals(Status.PARSED, r.status());
    }

    @Test void epochSeconds() {
        var r = p.parse("1795564800", null, utc, today);   // 2026-11-25T00:00:00Z
        assertEquals(LocalDate.of(2026, 11, 25), r.date());
    }

    @Test void configuredPattern() {
        var r = p.parse("20 Nov 2026", "dd MMM yyyy", utc, today);
        assertEquals(LocalDate.of(2026, 11, 20), r.date());
        assertEquals(Status.PARSED, r.status());
    }

    @Test void relativeTextIsEstimated() {
        var r = p.parse("Posted 3 days ago", null, utc, today);
        assertEquals(LocalDate.of(2026, 11, 23), r.date());
        assertEquals(Status.ESTIMATED, r.status());
        assertEquals(today.minusDays(1), p.parse("Yesterday", null, utc, today).date());
        assertEquals(today.minusWeeks(2), p.parse("2 weeks ago", null, utc, today).date());
    }

    @Test void garbageIsFailedNotRecent() {
        var r = p.parse("soonish", null, utc, today);
        assertNull(r.date());
        assertEquals(Status.FAILED, r.status());
    }
}
