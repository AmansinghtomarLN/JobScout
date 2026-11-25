package com.jobscout.normalization;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Parses posted-date text. Never guesses: unparseable values are FAILED, empty ones UNKNOWN. */
@Component
public class DateParser {
    public enum Status { PARSED, ESTIMATED, UNKNOWN, FAILED }

    public record Result(LocalDate date, Status status) {}

    private static final Pattern RELATIVE =
            Pattern.compile("(\\d+)\\+?\\s*(minute|min|hour|hr|day|week|month)s?\\s*ago");

    public Result parse(String raw, String pattern, ZoneId zone, LocalDate today) {
        if (raw == null || raw.isBlank()) return new Result(null, Status.UNKNOWN);
        String s = raw.trim();

        if (pattern != null && !pattern.isBlank()) {
            try {
                TemporalAccessor ta = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
                        .parseBest(s, ZonedDateTime::from, LocalDateTime::from, LocalDate::from);
                return new Result(toDate(ta, zone), Status.PARSED);
            } catch (Exception ignored) { /* fall through to generic parsing */ }
        }

        if (s.matches("\\d{13}")) return new Result(Instant.ofEpochMilli(Long.parseLong(s)).atZone(zone).toLocalDate(), Status.PARSED);
        if (s.matches("\\d{10}")) return new Result(Instant.ofEpochSecond(Long.parseLong(s)).atZone(zone).toLocalDate(), Status.PARSED);

        try { return new Result(OffsetDateTime.parse(s).atZoneSameInstant(zone).toLocalDate(), Status.PARSED); } catch (Exception ignored) { }
        try { return new Result(LocalDateTime.parse(s).toLocalDate(), Status.PARSED); } catch (Exception ignored) { }
        try { return new Result(LocalDate.parse(s), Status.PARSED); } catch (Exception ignored) { }

        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.contains("today") || lower.contains("just now") || lower.contains("just posted"))
            return new Result(today, Status.ESTIMATED);
        if (lower.contains("yesterday")) return new Result(today.minusDays(1), Status.ESTIMATED);
        Matcher m = RELATIVE.matcher(lower);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            LocalDate d = switch (m.group(2)) {
                case "minute", "min", "hour", "hr" -> today;
                case "day" -> today.minusDays(n);
                case "week" -> today.minusWeeks(n);
                default -> today.minusMonths(n);
            };
            return new Result(d, Status.ESTIMATED);
        }
        return new Result(null, Status.FAILED);
    }

    private static LocalDate toDate(TemporalAccessor ta, ZoneId zone) {
        if (ta instanceof ZonedDateTime z) return z.withZoneSameInstant(zone).toLocalDate();
        if (ta instanceof LocalDateTime l) return l.toLocalDate();
        return (LocalDate) ta;
    }
}
