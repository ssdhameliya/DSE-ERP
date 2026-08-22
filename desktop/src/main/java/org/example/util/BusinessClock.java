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
        String configured = ConfigManager.runtimeBusinessZone();
        if (configured == null || configured.isBlank()) configured = ConfigManager.get("company.timeZone", "");
        if (configured != null && !configured.isBlank()) {
            try { return ZoneId.of(configured.trim()); } catch (Exception ignored) { }
        }
        return ZoneId.systemDefault();
    }

    public static String datePattern() {
        String configured = ConfigManager.runtimeBusinessDateFormat();
        if (configured == null || configured.isBlank()) configured = ConfigManager.get("company.dateFormat", DEFAULT_DATE_FORMAT);
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

    /**
     * Formats ISO and legacy day/month date strings through the configured
     * business date pattern. This guarantees leading zeroes for dd/MM/yyyy.
     */
    public static String formatDate(String value) {
        if (value == null || value.isBlank()) return "";
        String text = value.trim();
        for (DateTimeFormatter parser : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/uuuu"),
                DateTimeFormatter.ofPattern("dd/MM/uuuu"))) {
            try {
                return formatDate(LocalDate.parse(text, parser));
            } catch (DateTimeParseException ignored) { }
        }
        return text;
    }

    public static String formatInstant(Instant value, String timePattern) {
        if (value == null) return "";
        String pattern = datePattern() + (timePattern == null || timePattern.isBlank() ? "" : " " + timePattern.trim());
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).withZone(zone()).format(value);
    }

    /** Formats canonical or legacy persisted timestamps in the Application Settings timezone. */
    public static String formatTimestamp(String value) {
        if (value == null || value.isBlank()) return "";
        try { return formatInstant(parseTimestamp(value), "hh:mm a"); }
        catch (RuntimeException ignored) { return value; }
    }

    /** Returns the configured business-local date represented by a canonical or legacy timestamp. */
    public static LocalDate localDateOfTimestamp(String value) {
        if (value == null || value.isBlank()) return null;
        try { return parseTimestamp(value).atZone(zone()).toLocalDate(); }
        catch (RuntimeException ignored) { return null; }
    }

    /** Parses canonical UTC/offset timestamps and legacy local TEXT timestamps using the configured business zone. */
    public static Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        try { return Instant.parse(text); } catch (DateTimeParseException ignored) { }
        try { return OffsetDateTime.parse(text).toInstant(); } catch (DateTimeParseException ignored) { }
        String offsetCompatible = text.replace(' ', 'T');
        if (offsetCompatible.matches(".*[+-]\\d{2}$")) offsetCompatible += ":00";
        try { return OffsetDateTime.parse(offsetCompatible).toInstant(); } catch (DateTimeParseException ignored) { }
        try { return LocalDateTime.parse(offsetCompatible, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone()).toInstant(); } catch (DateTimeParseException ignored) { }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))) {
            try { return LocalDateTime.parse(text, formatter).atZone(zone()).toInstant(); }
            catch (DateTimeParseException ignored) { }
        }
        throw new IllegalArgumentException("Unsupported timestamp format: " + value);
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
