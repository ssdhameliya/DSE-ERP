package org.example.server.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Properties;

/** Server-side business clock synchronized with the desktop workspace settings. */
public final class BusinessClock {
    private BusinessClock() { }

    public static ZoneId zone() {
        String configured = readConfigured("company.timeZone");
        if (configured == null || configured.isBlank()) configured = System.getenv("DSE_BUSINESS_TIME_ZONE");
        if (configured != null && !configured.isBlank()) {
            try { return ZoneId.of(configured.trim()); } catch (Exception ignored) { }
        }
        return ZoneId.systemDefault();
    }

    public static LocalDate today() { return LocalDate.now(zone()); }
    public static LocalDateTime now() { return LocalDateTime.now(zone()); }
    public static YearMonth currentMonth() { return YearMonth.from(today()); }
    public static Instant nowUtc() { return Instant.now(); }
    public static String nowUtcText() { return DateTimeFormatter.ISO_INSTANT.format(nowUtc()); }

    /**
     * Converts persisted legacy timestamp values to the canonical UTC API representation.
     * SQL TIMESTAMP values from older releases are interpreted in the configured business zone.
     */
    public static String toUtcText(Object value) {
        if (value == null) return "";
        try {
            Instant instant;
            if (value instanceof Instant v) instant = v;
            else if (value instanceof OffsetDateTime v) instant = v.toInstant();
            else if (value instanceof ZonedDateTime v) instant = v.toInstant();
            else if (value instanceof LocalDateTime v) instant = v.atZone(zone()).toInstant();
            else instant = parseTimestamp(String.valueOf(value));
            return DateTimeFormatter.ISO_INSTANT.format(instant);
        } catch (RuntimeException ignored) {
            return String.valueOf(value);
        }
    }

    public static String datePattern() {
        String configured = readConfigured("company.dateFormat");
        if (configured == null || configured.isBlank()) configured = System.getenv("DSE_BUSINESS_DATE_FORMAT");
        String candidate = configured == null || configured.isBlank() ? "dd/MM/yyyy" : configured.trim();
        try { DateTimeFormatter.ofPattern(candidate); return candidate; }
        catch (IllegalArgumentException ignored) { return "dd/MM/yyyy"; }
    }

    /**
     * Compatibility parser for historical TEXT timestamps. New event timestamps are always ISO UTC instants.
     */
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

    private static String readConfigured(String key) {
        String file = System.getenv("DSE_BUSINESS_CONFIG_FILE");
        if (file == null || file.isBlank()) return null;
        try {
            Path path = Path.of(file);
            if (!Files.isRegularFile(path)) return null;
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(path)) { properties.load(in); }
            return properties.getProperty(key);
        } catch (Exception ignored) {
            return null;
        }
    }
}
