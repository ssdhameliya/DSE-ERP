package org.example.config;

import org.example.util.BusinessClock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Pure conversion/formatting helpers used by Settings controllers. */
public final class SettingsFieldSupport {
    private SettingsFieldSupport() { }

    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value); } catch (Exception ignored) { return null; }
    }

    public static String text(String value) { return value == null ? "" : value.trim(); }
    public static String upper(String value) { return text(value).toUpperCase(Locale.ROOT); }

    public static String formatUpdateTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return "Never";
        try {
            return DateTimeFormatter.ofPattern(BusinessClock.datePattern() + ", hh:mm a")
                .withZone(BusinessClock.zone()).format(Instant.parse(raw));
        } catch (Exception ignored) {
            return raw;
        }
    }
}
