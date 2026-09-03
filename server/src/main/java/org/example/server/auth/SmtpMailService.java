package org.example.server.auth;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.example.server.persistence.JpaNativeRepository;
import org.example.shared.SecretValueCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.List;

/**
 * One SMTP authority for login OTP and business email.
 * Local desktop-backed servers continue to read the workspace config file;
 * dedicated/company servers use application_setting, with environment values
 * retained as a safe deployment fallback.
 */
@Service
public class SmtpMailService {
    private static final Logger LOG = LoggerFactory.getLogger(SmtpMailService.class);
    private final String host;
    private final int port;
    private final String email;
    private final String password;
    private final String configFile;
    private final JpaNativeRepository db;

    SmtpMailService(@Value("${dse.smtp.host:}") String host,
                    @Value("${dse.smtp.port:587}") int port,
                    @Value("${dse.smtp.email:}") String email,
                    @Value("${dse.smtp.password:}") String password,
                    @Value("${dse.smtp.config-file:}") String configFile,
                    JpaNativeRepository db) {
        this.host = host == null ? "" : host.trim();
        this.port = port;
        this.email = email == null ? "" : email.trim();
        this.password = password == null ? "" : password;
        this.configFile = configFile == null ? "" : configFile.trim();
        this.db = db;
    }

    public void requireConfigured() {
        Settings settings = settings();
        if (settings.host().isBlank() || settings.email().isBlank() || settings.password().isBlank())
            throw new IllegalStateException("Email/OTP settings are not configured. Configure SMTP settings first.");
    }

    public Settings currentSettings() { return settings(); }

    @Transactional
    public Settings saveSettings(String email, String password, String host, Integer port) {
        String cleanedEmail = email == null ? "" : email.trim();
        String requestedPassword = password == null ? "" : password.replaceAll("\\s+", "");
        Settings existing = settings();
        String cleanedPassword = requestedPassword.isBlank() ? existing.password() : requestedPassword;
        String cleanedHost = host == null ? "" : host.trim();
        int cleanedPort = port == null ? 587 : port;
        if (cleanedEmail.isBlank()) throw new IllegalArgumentException("Sending email address is required.");
        if (cleanedPassword.isBlank()) throw new IllegalArgumentException("Email app password is required the first time email is configured.");
        if (cleanedPort < 1 || cleanedPort > 65535) throw new IllegalArgumentException("SMTP port must be between 1 and 65535.");
        put("smtp.email", cleanedEmail);
        put("smtp.appPassword", SecretValueCodec.encrypt(cleanedPassword));
        put("smtp.host", cleanedHost);
        put("smtp.port", Integer.toString(cleanedPort));
        return settingsFromDatabase(new Settings(cleanedHost, cleanedPort, cleanedEmail, cleanedPassword));
    }

    public void sendOtp(String recipient, String purpose, String code) {
        Settings settings = settings();
        if (settings.host().isBlank() || settings.email().isBlank() || settings.password().isBlank())
            throw new IllegalStateException("Email/OTP settings are not configured. Configure SMTP settings first.");
        try {
            InternetAddress destination = new InternetAddress(recipient, true);
            Session session = mailSession(settings, 10_000, 10_000);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(settings.email()));
            message.setRecipient(Message.RecipientType.TO, destination);
            message.setSubject("DSE ERP " + purpose + " code");
            message.setText("Your DSE ERP verification code is " + code
                    + ". It expires in 10 minutes. If you did not request this, ignore this email.");
            Transport.send(message);
        } catch (Exception exception) {
            EmailDeliveryException failure = EmailDeliveryException.verification(exception);
            LOG.warn("Verification email delivery failed via {}:{} for sender {}: {}", settings.host(), settings.port(), masked(settings.email()), failure.adminMessage());
            throw failure;
        }
    }

    public void sendBusiness(String recipient, String subject, String body, String attachmentName, byte[] attachment) {
        List<Attachment> attachments = attachment == null || attachment.length == 0
                ? List.of()
                : List.of(new Attachment(attachmentName == null ? "document.pdf" : attachmentName,
                "application/octet-stream", attachment));
        sendBusiness(recipient, subject, body, attachments);
    }

    public void sendBusiness(String recipient, String subject, String body, List<Attachment> attachments) {
        Settings settings = settings();
        if (settings.host().isBlank() || settings.email().isBlank() || settings.password().isBlank())
            throw new IllegalStateException("Server business email is not configured");
        try {
            Session session = mailSession(settings, 15_000, 30_000);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(settings.email()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient, true));
            message.setSubject(subject == null ? "DSE ERP document" : subject);
            List<Attachment> files = attachments == null ? List.of() : attachments.stream()
                    .filter(a -> a != null && a.data() != null && a.data().length > 0).toList();
            if (files.isEmpty()) message.setText(body == null ? "" : body);
            else {
                var multipart = new jakarta.mail.internet.MimeMultipart();
                var text = new jakarta.mail.internet.MimeBodyPart();
                text.setText(body == null ? "" : body);
                multipart.addBodyPart(text);
                for (Attachment attachment : files) {
                    var file = new jakarta.mail.internet.MimeBodyPart();
                    file.setFileName(attachment.name() == null || attachment.name().isBlank() ? "attachment" : attachment.name());
                    file.setContent(attachment.data(), attachment.contentType() == null || attachment.contentType().isBlank()
                            ? "application/octet-stream" : attachment.contentType());
                    multipart.addBodyPart(file);
                }
                message.setContent(multipart);
            }
            Transport.send(message);
        } catch (Exception e) {
            EmailDeliveryException failure = EmailDeliveryException.business(e);
            LOG.warn("Business email delivery failed via {}:{} for sender {}: {}", settings.host(), settings.port(), masked(settings.email()), failure.adminMessage());
            throw failure;
        }
    }

    private Session mailSession(Settings settings, int connectTimeout, int timeout) {
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.host", settings.host());
        properties.put("mail.smtp.port", Integer.toString(settings.port()));
        properties.put("mail.smtp.connectiontimeout", Integer.toString(connectTimeout));
        properties.put("mail.smtp.timeout", Integer.toString(timeout));
        return Session.getInstance(properties, new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(settings.email(), settings.password());
            }
        });
    }

    private Settings settings() {
        Settings fallback = new Settings(host, port, email, password);
        // LOCAL desktop servers explicitly provide the workspace config file.
        // Keeping this path authoritative preserves the current single-PC flow.
        if (!configFile.isBlank()) {
            Path path = Path.of(configFile).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) return settingsFromFile(path, fallback);
        }
        // Dedicated/company servers have no desktop config file: settings are
        // owned by the server database and can be changed by an Admin in-app.
        return settingsFromDatabase(fallback);
    }

    private Settings settingsFromFile(Path path, Settings fallback) {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
            String currentEmail = values.getProperty("smtp.email", fallback.email()).trim();
            String storedPassword = values.getProperty("smtp.appPassword", fallback.password());
            String currentPassword = SecretValueCodec.decrypt(storedPassword).replaceAll("\\s+", "");
            String currentHost = values.getProperty("smtp.host", fallback.host()).trim();
            String configuredPort = values.getProperty("smtp.port", Integer.toString(fallback.port())).trim();
            int currentPort = configuredPort.isBlank() ? 587 : Integer.parseInt(configuredPort);
            if (currentHost.isBlank()) currentHost = inferHost(currentEmail);
            return new Settings(currentHost, currentPort, currentEmail, currentPassword);
        } catch (Exception exception) {
            throw new IllegalStateException("Email/OTP settings could not be read", exception);
        }
    }

    private Settings settingsFromDatabase(Settings fallback) {
        String currentEmail = setting("smtp.email", fallback.email()).trim();
        String storedPassword = setting("smtp.appPassword", fallback.password());
        String currentPassword = SecretValueCodec.decrypt(storedPassword).replaceAll("\\s+", "");
        if(!storedPassword.isBlank()&&!SecretValueCodec.isEncrypted(storedPassword)) put("smtp.appPassword",SecretValueCodec.encrypt(currentPassword));
        String currentHost = setting("smtp.host", fallback.host()).trim();
        int currentPort;
        try { currentPort = Integer.parseInt(setting("smtp.port", Integer.toString(fallback.port())).trim()); }
        catch (Exception ignored) { currentPort = 587; }
        if (currentHost.isBlank()) currentHost = inferHost(currentEmail);
        return new Settings(currentHost, currentPort, currentEmail, currentPassword);
    }

    private String setting(String key, String fallback) {
        try {
            String value = db.queryForObject("SELECT setting_value FROM application_setting WHERE setting_key=?", String.class, key);
            return value == null ? fallback : value;
        } catch (Exception ignored) {
            return fallback == null ? "" : fallback;
        }
    }

    private void put(String key, String value) {
        db.update("INSERT INTO application_setting(setting_key,setting_value,updated_at) VALUES(?,?,CURRENT_TIMESTAMP) "
                + "ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value,updated_at=CURRENT_TIMESTAMP", key, value);
    }

    private String inferHost(String value) {
        String address = value == null ? "" : value.toLowerCase();
        if (address.endsWith("@gmail.com") || address.endsWith("@googlemail.com")) return "smtp.gmail.com";
        if (address.endsWith("@outlook.com") || address.endsWith("@hotmail.com") || address.endsWith("@live.com")) return "smtp.office365.com";
        if (address.endsWith("@yahoo.com") || address.endsWith("@yahoo.in")) return "smtp.mail.yahoo.com";
        return "";
    }

    private static String masked(String address) {
        if (address == null || address.isBlank()) return "(not configured)";
        int at = address.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? address.substring(at) : "");
        return address.substring(0, 1) + "***" + address.substring(at);
    }

    public record Attachment(String name, String contentType, byte[] data) {}
    public record Settings(String host, int port, String email, String password) {}
}
