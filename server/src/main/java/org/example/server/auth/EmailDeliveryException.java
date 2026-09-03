package org.example.server.auth;

import jakarta.mail.AuthenticationFailedException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Locale;

/**
 * Sanitized SMTP failure. The public message is safe for registration/business users;
 * the administrator message is actionable but never contains provider stack traces or credentials.
 */
public final class EmailDeliveryException extends RuntimeException {
    private final String adminMessage;

    private EmailDeliveryException(String publicMessage, String adminMessage, Throwable cause) {
        super(publicMessage, cause);
        this.adminMessage = adminMessage;
    }

    public String adminMessage() { return adminMessage; }

    public static EmailDeliveryException verification(Throwable failure) {
        return from(failure,
                "Verification email is temporarily unavailable. Please contact your administrator or try again later.");
    }

    public static EmailDeliveryException business(Throwable failure) {
        return from(failure,
                "Email delivery is temporarily unavailable. Please contact your administrator or try again later.");
    }

    private static EmailDeliveryException from(Throwable failure, String publicMessage) {
        Throwable root = root(failure);
        String raw = root == null || root.getMessage() == null ? "" : root.getMessage();
        String lower = raw.toLowerCase(Locale.ROOT);
        String admin;
        if (root instanceof AuthenticationFailedException || lower.contains("535")
                || lower.contains("authentication failed") || lower.contains("username and password not accepted")
                || lower.contains("badcredentials")) {
            admin = "SMTP authentication failed. Verify the sending email address and configured app password. "
                    + "For Gmail, use a current Google App Password rather than the normal account password.";
        } else if (root instanceof SocketTimeoutException || lower.contains("timed out") || lower.contains("timeout")) {
            admin = "The SMTP server did not respond in time. Verify the SMTP host/port, internet connection, firewall, and provider availability.";
        } else if (root instanceof ConnectException || lower.contains("connection refused") || lower.contains("could not connect")
                || lower.contains("unknown host")) {
            admin = "DSE ERP could not connect to the SMTP server. Verify the SMTP host/port, internet connection, firewall, and provider availability.";
        } else {
            admin = "The SMTP provider rejected or could not complete the email request. Verify the sender, SMTP host/port and app password, then use Test Email again.";
        }
        return new EmailDeliveryException(publicMessage, admin, failure);
    }

    private static Throwable root(Throwable failure) {
        Throwable value = failure;
        while (value != null && value.getCause() != null && value.getCause() != value) value = value.getCause();
        return value;
    }
}
