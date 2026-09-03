package org.example.service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.example.config.ConfigManager;

import java.util.Properties;
import java.nio.file.Path;
import java.nio.file.Files;

/** SMTP delivery configured from the application's Settings screen. */
public final class EmailService {
    private EmailService() {
    }

    public static void sendOtp(String recipient, String otp) {
        send(recipient, "JavaApp ERP verification code", "Your verification code is " + otp + ". It expires in 10 minutes. Do not share this code.");
    }

    public static void send(String recipient, String subject, String body) {
        send(recipient, subject, body, null);
    }

    public static void resend(String recipient, String subject, String body, Path attachment) {
        org.example.service.PermissionService.require("COMMUNICATION.RESEND", "re-send a communication");
        if (ConfigManager.isSharedClient()) {
            new org.example.api.authority.BusinessEmailClient().resend(recipient, subject, body, attachment);
            return;
        }
        send(recipient, subject, body, attachment);
    }

    public static void send(String recipient, String subject, String body, Path attachment) {
        if (ConfigManager.isSharedClient()) {
            new org.example.api.authority.BusinessEmailClient().send(recipient, subject, body, attachment);
            return;
        }
        String sender = ConfigManager.get("smtp.email", "").trim();
        String password = ConfigManager.get("smtp.appPassword", "").trim();
        if (sender.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Email is not configured. Open Settings and enter the sending email address and app password.");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Recipient email address is missing. Update the customer or supplier master record and try again.");
        }
        try {
            sender = new InternetAddress(sender, true).getAddress();
            recipient = new InternetAddress(recipient.trim(), true).getAddress();
        } catch (Exception invalidAddress) {
            throw new IllegalArgumentException("The sender or recipient email address is not valid.", invalidAddress);
        }
        if (attachment != null && (!Files.isRegularFile(attachment) || !Files.isReadable(attachment))) {
            throw new IllegalStateException("The PDF attachment could not be read: " + attachment);
        }
        String host = ConfigManager.get("smtp.host", "").trim();
        if (host.isBlank()) host = inferHost(sender);
        password = password.replaceAll("\\s+", "");
        final String authSender = sender;
        final String authPassword = password;
        String port = ConfigManager.get("smtp.port", "587").trim();
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port.isBlank() ? "587" : port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "30000");
        props.put("mail.smtp.writetimeout", "30000");
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(authSender, authPassword);
            }
        });
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(sender));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, true));
            message.setSubject(subject);
            if (attachment == null) {
                message.setText(body);
            } else {
                var multipart = new jakarta.mail.internet.MimeMultipart();
                var text = new jakarta.mail.internet.MimeBodyPart();
                text.setText(body);
                multipart.addBodyPart(text);
                var file = new jakarta.mail.internet.MimeBodyPart();
                file.attachFile(attachment.toFile());
                multipart.addBodyPart(file);
                message.setContent(multipart);
            }
            Transport.send(message);
        } catch (Exception e) {
            throw new RuntimeException(
                "Email could not be sent using " + host + ".\n\n" + friendlyMessage(e), e);
        }
    }

    private static String inferHost(String email) {
        String value = email.toLowerCase();
        if (value.endsWith("@gmail.com") || value.endsWith("@googlemail.com")) return "smtp.gmail.com";
        if (value.endsWith("@outlook.com") || value.endsWith("@hotmail.com") || value.endsWith("@live.com")) return "smtp.office365.com";
        if (value.endsWith("@yahoo.com") || value.endsWith("@yahoo.in")) return "smtp.mail.yahoo.com";
        throw new IllegalStateException("Email provider was not recognized. Enter SMTP Host and Port in Settings.");
    }

    private static String friendlyMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
