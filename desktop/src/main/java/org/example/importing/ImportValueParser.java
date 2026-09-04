package org.example.importing;

import org.example.util.BusinessClock;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure scalar parsing rules shared by import processors. */
public final class ImportValueParser {
    private static final Pattern DAYS = Pattern.compile("(\\d+)");
    private ImportValueParser() { }

    public static double number(String value) {
        if (value == null || value.isBlank()) return 0.0;
        String normalized = value.trim().replace(",", "");
        try {
            double parsed = Double.parseDouble(normalized);
            if (!Double.isFinite(parsed)) throw new NumberFormatException("not finite");
            return parsed;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid numeric value: '" + value + "'");
        }
    }

    public static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + field);
        return value;
    }

    public static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public static double positive(String value, String field) {
        double parsed = number(value);
        if (parsed <= 0) throw new IllegalArgumentException(field + " must be greater than zero");
        return parsed;
    }

    public static LocalDate requiredDate(String value) {
        LocalDate parsed = BusinessClock.parseDate(value);
        if (parsed == null) throw new IllegalArgumentException("Missing required date");
        return parsed;
    }

    public static int termDays(String term) {
        Matcher matcher = DAYS.matcher(defaultText(term, "0"));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}
