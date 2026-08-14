package org.example.util;

import org.example.config.ConfigManager;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single source of truth for user-facing business dates and times.
 * Saved company settings win; system defaults are used only when no valid setting exists.
 */
public final class BusinessClock {
    private static final String DEFAULT_DATE_FORMAT = "dd/MM/yyyy";

    private BusinessClock() { }

    public static ZoneId zone() {
        String configured = ConfigManager.get("company.timeZone", "");
        if (configured != null && !configured.isBlank()) {
            try { return ZoneId.of(configured.trim()); } catch (Exception ignored) { }
        }
        return ZoneId.systemDefault();
    }

    public static String datePattern() {
        String configured = ConfigManager.get("company.dateFormat", DEFAULT_DATE_FORMAT);
        String candidate = configured == null || configured.isBlank() ? DEFAULT_DATE_FORMAT : configured.trim();
        try {
            DateTimeFormatter.ofPattern(candidate, Locale.getDefault());
            return candidate;
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_DATE_FORMAT;
        }
    }

    public static DateTimeFormatter dateFormatter() {
        return DateTimeFormatter.ofPattern(datePattern(), Locale.getDefault());
    }

    public static LocalDate today() { return LocalDate.now(zone()); }
    public static LocalDateTime now() { return LocalDateTime.now(zone()); }
    public static ZonedDateTime zonedNow() { return ZonedDateTime.now(zone()); }

    /** Short display label for the saved business timezone, e.g. IST, UTC or GST. */
    public static String zoneAbbreviation() {
        try {
            return DateTimeFormatter.ofPattern("z", Locale.ENGLISH).format(zonedNow());
        } catch (Exception ignored) {
            return zone().getId();
        }
    }
    public static Instant nowUtc() { return Instant.now(); }
    public static YearMonth currentMonth() { return YearMonth.from(today()); }

    public static String formatDate(LocalDate value) {
        return value == null ? "" : value.format(dateFormatter());
    }

    public static String formatInstant(Instant value, String timePattern) {
        if (value == null) return "";
        String pattern = datePattern() + (timePattern == null || timePattern.isBlank() ? "" : " " + timePattern.trim());
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).withZone(zone()).format(value);
    }

    /** Parses text dates using the saved date format first, then safe compatibility formats. */
    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        Set<String> patterns = new LinkedHashSet<>();
        patterns.add(datePattern());
        patterns.add("yyyy-MM-dd");
        patterns.add("dd/MM/yyyy");
        patterns.add("d/M/yyyy");
        patterns.add("MM/dd/yyyy");
        patterns.add("M/d/yyyy");
        patterns.add("dd-MM-yyyy");
        patterns.add("d-M-yyyy");
        patterns.add("dd MMM yyyy");
        patterns.add("d MMM yyyy");
        patterns.add("dd-MMM-yyyy");
        patterns.add("d-MMM-yyyy");
        List<String> attempted = new ArrayList<>();
        for (String pattern : patterns) {
            try {
                attempted.add(pattern);
                return LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
            } catch (DateTimeParseException | IllegalArgumentException ignored) { }
        }
        try { return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE); } catch (Exception ignored) { }
        throw new IllegalArgumentException("Invalid date: " + value + " (expected " + datePattern() + " or a supported Excel date format)");
    }
}
