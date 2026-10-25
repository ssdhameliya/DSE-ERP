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
        send(recipient, BrandingService.applicationName() + " verification code", "Your verification code is " + otp + ". It expires in 10 minutes. Do not share this code.");
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
        send(recipient, subject, body, attachment, null);
    }

    public static void send(String recipient, String subject, String body, Path attachment, Path signatureImage) {
        if (signatureImage == null) {
            String configuredSig = MessageTemplateService.getSignatureImagePath();
            if (configuredSig != null && !configuredSig.isBlank()) {
                try {
                    Path p = Path.of(configuredSig);
                    if (Files.isRegularFile(p) && Files.isReadable(p)) {
                        signatureImage = p;
                    }
                } catch (Exception ignored) {
                    java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored);
                }
            }
        }
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

            boolean hasSignatureImage = signatureImage != null && Files.isRegularFile(signatureImage) && Files.isReadable(signatureImage);
            boolean hasAttachment = attachment != null && Files.isRegularFile(attachment) && Files.isReadable(attachment);

            if (!hasSignatureImage && !hasAttachment) {
                message.setContent(body == null ? "" : body, "text/plain; charset=UTF-8");
            } else {
                var rootMultipart = new jakarta.mail.internet.MimeMultipart("mixed");

                if (hasSignatureImage) {
                    var relatedMultipart = new jakarta.mail.internet.MimeMultipart("related");

                    var htmlPart = new jakarta.mail.internet.MimeBodyPart();
                    String htmlContent = formatHtmlEmailBody(body, true);
                    htmlPart.setContent(htmlContent, "text/html; charset=UTF-8");
                    relatedMultipart.addBodyPart(htmlPart);

                    var imgPart = new jakarta.mail.internet.MimeBodyPart();
                    imgPart.attachFile(signatureImage.toFile());
                    imgPart.setContentID("<signatureImage>");
                    imgPart.setHeader("Content-ID", "<signatureImage>");
                    imgPart.setDisposition(jakarta.mail.Part.INLINE);
                    String mimeType = Files.probeContentType(signatureImage);
                    if (mimeType == null || mimeType.isBlank()) mimeType = "image/png";
                    imgPart.setHeader("Content-Type", mimeType + "; name=\"" + signatureImage.getFileName() + "\"");
                    relatedMultipart.addBodyPart(imgPart);

                    var bodyWrapper = new jakarta.mail.internet.MimeBodyPart();
                    bodyWrapper.setContent(relatedMultipart);
                    rootMultipart.addBodyPart(bodyWrapper);
                } else {
                    var textPart = new jakarta.mail.internet.MimeBodyPart();
                    textPart.setContent(body == null ? "" : body, "text/plain; charset=UTF-8");
                    rootMultipart.addBodyPart(textPart);
                }

                if (hasAttachment) {
                    var filePart = new jakarta.mail.internet.MimeBodyPart();
                    filePart.attachFile(attachment.toFile());
                    filePart.setFileName(attachment.getFileName().toString());
                    filePart.setDisposition(jakarta.mail.Part.ATTACHMENT);
                    rootMultipart.addBodyPart(filePart);
                }

                message.setContent(rootMultipart);
            }
            Transport.send(message);
        } catch (Exception e) {
            throw new RuntimeException(
                "Email could not be sent using " + host + ".\n\n" + EmailFailureMessages.forAdministrator(e), e);
        }
    }

    public static String formatHtmlEmailBody(String plainText, boolean includeInlineSignatureImage) {
        if (plainText == null) plainText = "";
        String escaped = plainText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\r\n", "\n")
                .replace("\n", "<br/>\n");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"margin:0; padding:12px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size: 14px; line-height: 1.6; color: #1e293b;\">");
        sb.append("<div>").append(escaped).append("</div>");
        if (includeInlineSignatureImage) {
            sb.append("<div style=\"margin-top: 16px; margin-bottom: 8px;\">");
            sb.append("<img src=\"cid:signatureImage\" alt=\"Signature / Stamp\" style=\"max-height: 90px; max-width: 260px; object-fit: contain; border: none; display: block;\" />");
            sb.append("</div>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String inferHost(String email) {
        String value = email.toLowerCase();
        if (value.endsWith("@gmail.com") || value.endsWith("@googlemail.com")) return "smtp.gmail.com";
        if (value.endsWith("@outlook.com") || value.endsWith("@hotmail.com") || value.endsWith("@live.com")) return "smtp.office365.com";
        if (value.endsWith("@yahoo.com") || value.endsWith("@yahoo.in")) return "smtp.mail.yahoo.com";
        throw new IllegalStateException("Email provider was not recognized. Enter SMTP Host and Port in Settings.");
    }

}
