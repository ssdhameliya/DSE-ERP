package org.example.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application-wide conversion from technical failures to user-facing ERP messages.
 * Controllers must not expose raw exception bodies directly in blocking dialogs.
 */
public final class UserFacingErrorMapper {
    private static final Pattern HTTP = Pattern.compile("(?i)(?:^|[^A-Za-z0-9])HTTP\\s*[:#-]?\\s*([1-5][0-9]{2})(?=$|[^0-9])");

    private UserFacingErrorMapper() {}

    public static String title(String raw) {
        String value = clean(raw);
        if (value.isBlank()) return "Operation Incomplete";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("duplicate key") || lower.contains("23505") || lower.contains("unique constraint")) {
            return "Duplicate Identifier Conflict";
        }
        if (lower.contains("foreign key") || lower.contains("23503") || lower.contains("referenced by")) {
            return "Linked Record Dependency";
        }
        if (lower.contains("concurrentedit") || lower.contains("optimistic locking") || lower.contains("row_version")) {
            return "Simultaneous Edit Conflict";
        }
        if (lower.contains("lock timeout") || lower.contains("could not obtain lock") || lower.contains("deadlock")) {
            return "Database Lock Wait Timeout";
        }
        if (lower.contains("connection refused") || lower.contains("connectexception") || lower.contains("unknownhost")) {
            return "Server Connection Unavailable";
        }
        if (lower.contains("column") && lower.contains("does not exist")) {
            return "Database Schema Mismatch";
        }
        if (lower.contains("numberformatexception") || lower.contains("for input string")) {
            return "Invalid Numeric Format";
        }
        if (lower.contains("nullpointerexception") || lower.contains("illegalstateexception")) {
            return "Internal Application Error";
        }
        Matcher matcher = HTTP.matcher(value);
        if (matcher.find()) return "HTTP " + matcher.group(1) + " • " + statusSummary(matcher.group(1));
        return "Application Diagnostic Event";
    }

    public static String action(String raw) {
        String value = clean(raw);
        if (value.isBlank()) return "Please retry the operation.";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("duplicate key") || lower.contains("23505") || lower.contains("unique constraint")) {
            return "Review the reference sequence or enter an alternative unique code.";
        }
        if (lower.contains("foreign key") || lower.contains("23503") || lower.contains("referenced by")) {
            return "Remove the dependent line items or associated vouchers before proceeding.";
        }
        if (lower.contains("concurrentedit") || lower.contains("optimistic locking") || lower.contains("row_version")) {
            return "Click Refresh to load the latest values and re-apply your changes.";
        }
        if (lower.contains("lock timeout") || lower.contains("could not obtain lock") || lower.contains("deadlock")) {
            return "Wait a few seconds for the background lock to release, then retry.";
        }
        if (lower.contains("connection refused") || lower.contains("connectexception") || lower.contains("unknownhost")) {
            return "Check your network or server status. Your current unsaved form data is safe.";
        }
        if (lower.contains("column") && lower.contains("does not exist")) {
            return "Verify that all Flyway database migrations have been executed.";
        }
        return "Review the details above or retry the operation.";
    }

    public static String message(String raw) {
        String value = clean(raw);
        if (value.isBlank()) return "The operation could not be completed. Your current work has not been cleared. Please try again.";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("empty string") || lower.startsWith("for input string:") || lower.contains("numberformatexception")) {
            logTechnical(value);
            return "A required value is blank or invalid. Review the entered information and try again.";
        }
        if (lower.contains("nullpointerexception") || lower.contains("illegalstateexception")
                || lower.contains("stacktrace") || lower.startsWith("java.") || lower.startsWith("org.springframework.")) {
            logTechnical(value);
            return "The ERP could not complete this operation. Your current work has not been cleared. Please try again; if it continues, contact support.";
        }
        if ((value.startsWith("{") && value.endsWith("}"))
                || lower.startsWith("operations api error (500)")
                || lower.startsWith("master api error (500)")
                || lower.startsWith("bank statement api error (500)")) {
            logTechnical(value);
            return "The ERP server could not complete this request. Your current work has not been cleared. Please try again.";
        }
        if (lower.contains("duplicate key") || lower.contains("23505") || lower.contains("unique constraint")) {
            logTechnical(value);
            return "A record with this reference or number already exists in the system. Please use a unique identifier.";
        }
        if (lower.contains("foreign key") || lower.contains("23503") || lower.contains("referenced by")) {
            logTechnical(value);
            return "This item cannot be deleted or modified because it is currently linked to existing transactions.";
        }
        if (lower.contains("concurrentedit") || lower.contains("optimistic locking") || lower.contains("row_version")) {
            logTechnical(value);
            return "Another operator has updated this record simultaneously. Please refresh to load the latest values and retry.";
        }
        if (lower.contains("lock timeout") || lower.contains("could not obtain lock") || lower.contains("deadlock")) {
            logTechnical(value);
            return "The database is currently synchronizing another transaction on this record. Please wait a moment and try again.";
        }
        if (lower.contains("connection refused") || lower.contains("connectexception") || lower.contains("unknownhost")) {
            logTechnical(value);
            return "The ERP server could not be reached. Check the connection and try again. Your current work remains open.";
        }
        return value;
    }

    public static String reference(String raw) {
        String value = clean(raw);
        Matcher matcher = HTTP.matcher(value);
        if (matcher.find()) return "HTTP " + matcher.group(1) + " • " + statusSummary(matcher.group(1));
        return "Your current work remains available unless the message above states otherwise.";
    }

    private static String statusSummary(String code) {
        return switch (code) {
            case "400" -> "Bad request";
            case "401" -> "Authentication required";
            case "403" -> "Access denied";
            case "404" -> "Resource not found";
            case "408", "504" -> "Request timed out";
            case "409" -> "Request conflict";
            case "422" -> "Validation failed";
            case "429" -> "Too many requests";
            case "500" -> "Server request failed";
            case "502" -> "Gateway request failed";
            case "503" -> "Service unavailable";
            default -> "Request failed";
        };
    }

    private static void logTechnical(String value) {
        System.err.println("DSE ERP technical error detail: " + value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
