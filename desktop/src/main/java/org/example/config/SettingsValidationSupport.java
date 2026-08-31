package org.example.config;

import java.util.Locale;

/** Pure validation rules shared by the Settings UI without JavaFX dependencies. */
public final class SettingsValidationSupport {
    private SettingsValidationSupport() { }

    public record PaymentResult(boolean valid, String message, String normalizedTolerance) {
        public static PaymentResult ok(String tolerance) { return new PaymentResult(true, "", tolerance); }
        public static PaymentResult error(String message) { return new PaymentResult(false, message, null); }
    }

    public static PaymentResult validatePayment(
            String upi,
            String accountNumber,
            String ifsc,
            String toleranceText
    ) {
        String safeUpi = trim(upi);
        String safeAccount = trim(accountNumber);
        String safeIfsc = trim(ifsc);

        if (!safeUpi.isBlank() && !safeUpi.matches("^[A-Za-z0-9._-]{2,}@[A-Za-z0-9.-]{2,}$")) {
            return PaymentResult.error("Enter a valid UPI ID, for example company@bank.");
        }
        if (!safeAccount.isBlank() && !safeAccount.matches("[0-9]{6,20}")) {
            return PaymentResult.error("Account number must contain 6 to 20 digits.");
        }
        if (!safeIfsc.isBlank() && !safeIfsc.matches("(?i)^[A-Z]{4}0[A-Z0-9]{6}$")) {
            return PaymentResult.error("Enter a valid 11-character IFSC code.");
        }

        try {
            double tolerance = Double.parseDouble(trim(toleranceText));
            if (!Double.isFinite(tolerance) || tolerance < 0 || tolerance > 5) {
                return PaymentResult.error("Bank reconciliation round-off tolerance must be between ₹0.00 and ₹5.00.");
            }
            return PaymentResult.ok(String.format(Locale.ROOT, "%.2f", tolerance));
        } catch (Exception exception) {
            return PaymentResult.error("Enter a valid bank reconciliation round-off tolerance, for example 1.00.");
        }
    }

    public static String emailPortError(String smtpPort) {
        String value = trim(smtpPort);
        if (!value.isBlank() && !value.matches("\\d{1,5}")) {
            return "SMTP port must be a valid number.";
        }
        if (!value.isBlank()) {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) return "SMTP port must be between 1 and 65535.";
        }
        return null;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
