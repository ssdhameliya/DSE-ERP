package org.example.service;

import jakarta.mail.AuthenticationFailedException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Locale;

/** Friendly, credential-safe diagnostics for Admin email configuration screens. */
public final class EmailFailureMessages {
    private EmailFailureMessages() {}

    public static String forAdministrator(Throwable failure) {
        Throwable root = root(failure);
        String raw = root == null || root.getMessage() == null ? "" : root.getMessage();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (root instanceof AuthenticationFailedException || lower.contains("535")
                || lower.contains("username and password not accepted") || lower.contains("badcredentials")
                || lower.contains("authentication failed")) {
            return "SMTP authentication failed. Verify the sending email address and app password. "
                    + "For Gmail, use a current Google App Password instead of the normal Gmail password.";
        }
        if (root instanceof SocketTimeoutException || lower.contains("timed out") || lower.contains("timeout")) {
            return "The SMTP server did not respond in time. Check the SMTP host/port, internet connection, firewall and provider availability.";
        }
        if (root instanceof ConnectException || lower.contains("connection refused") || lower.contains("could not connect")
                || lower.contains("unknown host")) {
            return "DSE ERP could not connect to the SMTP server. Check the SMTP host/port, internet connection and firewall.";
        }
        return "Email delivery failed. Check the sender address, SMTP host/port and app password, then try Test Email again.";
    }

    private static Throwable root(Throwable failure) {
        Throwable value=failure;
        while(value!=null && value.getCause()!=null && value.getCause()!=value)value=value.getCause();
        return value;
    }
}
