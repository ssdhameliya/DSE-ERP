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
            try { return ZoneId.of(configured.trim()); } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
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

    public static String normalizeDatePattern(String candidate) {
        if (candidate == null || candidate.isBlank()) return "dd/MM/yyyy";
        String s = candidate.trim();
        if ("DD-MM-YYYY".equalsIgnoreCase(s)) return "dd-MM-yyyy";
        if ("DD/MM/YYYY".equalsIgnoreCase(s)) return "dd/MM/yyyy";
        if ("YYYY-MM-DD".equalsIgnoreCase(s)) return "yyyy-MM-dd";
        if ("MM/DD/YYYY".equalsIgnoreCase(s)) return "MM/dd/yyyy";
        if ("DD MMM YYYY".equalsIgnoreCase(s)) return "dd MMM yyyy";
        String cleaned = s.replace("YYYY", "yyyy").replace("DD", "dd");
        try {
            DateTimeFormatter.ofPattern(cleaned, java.util.Locale.getDefault());
            return cleaned;
        } catch (IllegalArgumentException ignored) {
            return "dd/MM/yyyy";
        }
    }

    public static String datePattern() {
        String configured = org.example.config.ConfigManager.get("company.dateFormat", "");
        if (configured == null || configured.isBlank()) configured = org.example.config.ConfigManager.get("date.format", "");
        if (configured == null || configured.isBlank()) configured = readConfigured("company.dateFormat");
        if (configured == null || configured.isBlank()) configured = System.getenv("DSE_BUSINESS_DATE_FORMAT");
        return normalizeDatePattern(configured);
    }

    public static String formatDate(LocalDate value) {
        if (value == null) return "";
        try {
            return DateTimeFormatter.ofPattern(datePattern(), java.util.Locale.getDefault()).format(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    public static String formatDate(String value) {
        if (value == null || value.isBlank()) return "";
        String text = value.trim();
        String candidate = text.length() > 10 && (text.contains(" ") || text.contains("T")) ? text.substring(0, 10) : text;
        for (DateTimeFormatter parser : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/uuuu"),
                DateTimeFormatter.ofPattern("dd/MM/uuuu"),
                DateTimeFormatter.ofPattern("dd-MM-uuuu"),
                DateTimeFormatter.ofPattern("d-M-uuuu"),
                DateTimeFormatter.ofPattern("MM/dd/uuuu"),
                DateTimeFormatter.ofPattern("M/d/uuuu"),
                DateTimeFormatter.ofPattern("uuuu/MM/dd"),
                DateTimeFormatter.ofPattern("dd-MMM-uuuu", java.util.Locale.ENGLISH))) {
            try {
                return formatDate(LocalDate.parse(candidate, parser));
            } catch (DateTimeParseException ignored) { }
            try {
                return formatDate(LocalDate.parse(text, parser));
            } catch (DateTimeParseException ignored) { }
        }
        return text;
    }

    public static String timePattern() {
        String configured = org.example.config.ConfigManager.get("company.timeFormat", "");
        if (configured == null || configured.isBlank()) configured = readConfigured("company.timeFormat");
        if (configured == null || configured.isBlank()) configured = System.getenv("DSE_BUSINESS_TIME_FORMAT");
        String candidate = configured == null || configured.isBlank() ? "hh:mm a" : configured.trim();
        if ("12-hour".equalsIgnoreCase(candidate) || "12h".equalsIgnoreCase(candidate) || "12_HOUR".equalsIgnoreCase(candidate)) return "hh:mm a";
        if ("24-hour".equalsIgnoreCase(candidate) || "24h".equalsIgnoreCase(candidate) || "24_HOUR".equalsIgnoreCase(candidate)) return "HH:mm";
        try { DateTimeFormatter.ofPattern(candidate); return candidate; }
        catch (IllegalArgumentException ignored) { return "hh:mm a"; }
    }

    public static String formatTimestamp(Instant value) {
        if (value == null) return "";
        String pattern = datePattern() + " " + timePattern();
        try {
            return DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()).withZone(zone()).format(value);
        } catch (Exception e) {
            return DateTimeFormatter.ISO_INSTANT.format(value);
        }
    }

    /**
     * Compatibility parser for historical TEXT timestamps. New event timestamps are always ISO UTC instants.
     */
    public static Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        try { return Instant.parse(text); } catch (DateTimeParseException ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        try { return OffsetDateTime.parse(text).toInstant(); } catch (DateTimeParseException ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        String offsetCompatible = text.replace(' ', 'T');
        if (offsetCompatible.matches(".*[+-]\\d{2}$")) offsetCompatible += ":00";
        try { return OffsetDateTime.parse(offsetCompatible).toInstant(); } catch (DateTimeParseException ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        try { return LocalDateTime.parse(offsetCompatible, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone()).toInstant(); } catch (DateTimeParseException ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))) {
            try { return LocalDateTime.parse(text, formatter).atZone(zone()).toInstant(); }
            catch (DateTimeParseException ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
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
