package org.example.shared;

import java.time.LocalDate;
import java.util.regex.Pattern;

/** Shared reference-format validation used by normal ERP creation and import preflight. */
public final class ReferenceFormatRules {
    private ReferenceFormatRules() {}

    public static boolean matches(String format, String value, LocalDate documentDate) {
        if (format == null || format.isBlank() || value == null || value.isBlank()) return false;
        return Pattern.compile(toRegex(format.trim(), documentDate), Pattern.CASE_INSENSITIVE).matcher(value.trim()).matches();
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
                out.append("\\d{").append(j-i).append('}'); i=j; continue;
            }
            char c=format.charAt(i++);
            if ("\\.^$|?*+()[]{}".indexOf(c)>=0) out.append('\\');
            out.append(c);
        }
        return out.append('$').toString();
    }
}
