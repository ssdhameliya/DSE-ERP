package org.example.server.auth;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
final class SmtpMailService {
    private final String host;
    private final int port;
    private final String email;
    private final String password;
    private final String configFile;

    SmtpMailService(@Value("${dse.smtp.host:}") String host,
                    @Value("${dse.smtp.port:587}") int port,
                    @Value("${dse.smtp.email:}") String email,
                    @Value("${dse.smtp.password:}") String password,
                    @Value("${dse.smtp.config-file:}") String configFile) {
        this.host = host == null ? "" : host.trim();
        this.port = port;
        this.email = email == null ? "" : email.trim();
        this.password = password == null ? "" : password;
        this.configFile = configFile == null ? "" : configFile.trim();
    }

    void requireConfigured() {
        Settings settings = settings();
        if (settings.host().isBlank() || settings.email().isBlank() || settings.password().isBlank()) {
            throw new IllegalStateException("Email/OTP settings are not configured. Configure SMTP settings first.");
        }
    }

    void sendOtp(String recipient, String purpose, String code) {
        Settings settings = settings();
        if (settings.host().isBlank() || settings.email().isBlank() || settings.password().isBlank())
            throw new IllegalStateException("Email/OTP settings are not configured. Configure SMTP settings first.");
        try {
            InternetAddress destination = new InternetAddress(recipient, true);
            Properties properties = new Properties();
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
            properties.put("mail.smtp.host", settings.host());
            properties.put("mail.smtp.port", Integer.toString(settings.port()));
            properties.put("mail.smtp.connectiontimeout", "10000");
            properties.put("mail.smtp.timeout", "10000");
            Session session = Session.getInstance(properties, new Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(settings.email(), settings.password());
                }
            });
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(settings.email()));
            message.setRecipient(Message.RecipientType.TO, destination);
            message.setSubject("DSE ERP " + purpose + " code");
            message.setText("Your DSE ERP verification code is " + code
                    + ". It expires in 10 minutes. If you did not request this, ignore this email.");
            Transport.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("The verification email could not be sent. Check SMTP settings.", exception);
        }
    }

    private Settings settings() {
        String currentHost = host;
        int currentPort = port;
        String currentEmail = email;
        String currentPassword = password;
        if (!configFile.isBlank()) {
            Path path = Path.of(configFile).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                Properties values = new Properties();
                try (InputStream input = Files.newInputStream(path)) {
                    values.load(input);
                    currentEmail = values.getProperty("smtp.email", currentEmail).trim();
                    currentPassword = values.getProperty("smtp.appPassword", currentPassword).replaceAll("\\s+", "");
                    currentHost = values.getProperty("smtp.host", currentHost).trim();
                    String configuredPort = values.getProperty("smtp.port", Integer.toString(currentPort)).trim();
                    currentPort = configuredPort.isBlank() ? 587 : Integer.parseInt(configuredPort);
                } catch (Exception exception) {
                    throw new IllegalStateException("Email/OTP settings could not be read", exception);
                }
            }
        }
        if (currentHost.isBlank()) currentHost = inferHost(currentEmail);
        return new Settings(currentHost, currentPort, currentEmail, currentPassword);
    }

    private String inferHost(String value) {
        String address = value == null ? "" : value.toLowerCase();
        if (address.endsWith("@gmail.com") || address.endsWith("@googlemail.com")) return "smtp.gmail.com";
        if (address.endsWith("@outlook.com") || address.endsWith("@hotmail.com") || address.endsWith("@live.com"))
            return "smtp.office365.com";
        if (address.endsWith("@yahoo.com") || address.endsWith("@yahoo.in")) return "smtp.mail.yahoo.com";
        return "";
    }

    private record Settings(String host, int port, String email, String password) {}
}
