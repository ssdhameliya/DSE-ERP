package org.example.shared;

import java.time.LocalDate;
import java.util.regex.Pattern;

/** Shared reference-format validation used by normal ERP creation and import preflight. */
public final class ReferenceFormatRules {
    private ReferenceFormatRules() {}


    public static void requireValidFormat(String format) {
        if (format == null || format.isBlank()) throw new IllegalArgumentException("Reference format is required");
        java.util.regex.Matcher matcher = Pattern.compile("X{2,}").matcher(format.trim());
        if (!matcher.find()) throw new IllegalArgumentException("Reference format must contain exactly one sequence group of at least two X characters (for example XXXX). X defines the minimum zero-padding width and expands automatically when the sequence grows");
        if (matcher.find()) throw new IllegalArgumentException("Reference format must contain only one X sequence group");
    }

    public static java.util.regex.Matcher sequenceMatcher(String format) {
        requireValidFormat(format);
        java.util.regex.Matcher matcher = Pattern.compile("X{2,}").matcher(format.trim());
        matcher.find();
        return matcher;
    }

    public static boolean matches(String format, String value, LocalDate documentDate) {
        if (format == null || format.isBlank() || value == null || value.isBlank()) return false;
        return Pattern.compile(toRegex(format.trim(), documentDate), Pattern.CASE_INSENSITIVE).matcher(value.trim()).matches();
    }

    public static String format(String formatPattern, long sequenceNumber, LocalDate date) {
        if (formatPattern == null || formatPattern.isBlank()) return String.valueOf(sequenceNumber);
        String res = formatPattern.trim();
        LocalDate d = date == null ? LocalDate.now() : date;
        res = res.replace("YYYY", String.format(java.util.Locale.ROOT, "%04d", d.getYear()));
        res = res.replace("YY", String.format(java.util.Locale.ROOT, "%02d", d.getYear() % 100));
        res = res.replace("DD", String.format(java.util.Locale.ROOT, "%02d", d.getDayOfMonth()));
        res = res.replace("MM", String.format(java.util.Locale.ROOT, "%02d", d.getMonthValue()));
        java.util.regex.Matcher matcher = Pattern.compile("X{2,}").matcher(res);
        if (matcher.find()) {
            int width = matcher.end() - matcher.start();
            String formattedSeq = String.format(java.util.Locale.ROOT, "%0" + width + "d", sequenceNumber);
            res = res.substring(0, matcher.start()) + formattedSeq + res.substring(matcher.end());
        } else {
            res = res + sequenceNumber;
        }
        return res;
    }

    public static String validationMessage(String format, String value, LocalDate documentDate) {
        if (value == null || value.isBlank()) return "Reference is mandatory";
        if (format == null || format.isBlank()) return "Reference format is not configured";
        return matches(format, value, documentDate) ? "All validations passed" : "Does not match configured format " + format;
    }

    static String toRegex(String format, LocalDate date) {
        StringBuilder out = new StringBuilder("^");
        for (int i = 0; i < format.length();) {
            if (format.startsWith("YYYY", i)) { out.append(date == null ? "\\d{4}" : String.format("%04d", date.getYear())); i += 4; continue; }
            if (format.startsWith("YY", i)) { out.append(date == null ? "\\d{2}" : String.format("%02d", date.getYear() % 100)); i += 2; continue; }
            if (format.startsWith("DD", i)) { out.append(date == null ? "\\d{2}" : String.format("%02d", date.getDayOfMonth())); i += 2; continue; }
            if (format.startsWith("MM", i)) { out.append(date == null ? "\\d{2}" : String.format("%02d", date.getMonthValue())); i += 2; continue; }
            if (format.charAt(i) == 'X') {
                int j=i; while (j < format.length() && format.charAt(j)=='X') j++;
                out.append("\\d{").append(j-i).append(",}"); i=j; continue;
            }
            char c=format.charAt(i++);
            if ("\\.^$|?*+()[]{}".indexOf(c)>=0) out.append('\\');
            out.append(c);
        }
        return out.append('$').toString();
    }
}
