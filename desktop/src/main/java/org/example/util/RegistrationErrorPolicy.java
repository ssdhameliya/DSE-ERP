package org.example.util;

import java.util.Locale;

/** Public-registration error policy: never expose SMTP/provider diagnostics. */
public final class RegistrationErrorPolicy {
    private RegistrationErrorPolicy() {}

    public static boolean isCaptchaFailure(Throwable failure) {
        String value = message(failure).toLowerCase(Locale.ROOT);
        return value.contains("captcha");
    }

    public static String userMessage(Throwable failure) {
        String value = message(failure);
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("captcha")) return value.isBlank() ? "CAPTCHA verification failed. Refresh it and try again." : value;
        if (lower.contains("email") || lower.contains("smtp") || lower.contains("mail") || lower.contains("535")
                || lower.contains("authentication") || lower.contains("server") || lower.contains("connect")
                || lower.contains("timeout") || lower.contains("temporarily unavailable")) {
            return "Verification email is temporarily unavailable. Please contact your administrator or try again later.";
        }
        return value.isBlank() ? "Registration verification could not be started. Please try again." : value;
    }

    private static String message(Throwable failure) {
        if (failure == null || failure.getMessage() == null) return "";
        return failure.getMessage().trim();
    }
}
