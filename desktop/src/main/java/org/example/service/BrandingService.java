package org.example.service;

import javafx.scene.image.Image;
import org.example.config.ConfigManager;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Workspace-aware branding with safe built-in fallbacks for first launch. */
public final class BrandingService {
    private BrandingService() { }

    public static String companyName() { return value("company.name", "DSE ERP"); }
    public static String applicationName() { return value("application.displayName", "DSE ERP"); }
    public static String tagline() { return value("application.tagline", "Business Management Suite"); }
    public static String startingText() { return value("application.startingText", "Starting " + applicationName() + "..."); }
    public static String loginDescription() { return "Role-aware secure access to " + applicationName(); }

    /** Application UI banner used by Splash/Login/Registration/Email screens. */
    public static Image applicationBrandImage() {
        return configuredImage("application.brandImagePath");
    }

    public static Image companyLogo() { return configuredImage("company.logoPath"); }
    public static Image authorizedSignature() { return configuredImage("company.signaturePath"); }
    public static Image paymentQrImage() { return configuredImage("payment.qrImagePath"); }

    /** Backward-compatible alias retained for older callers. */
    public static Image brandImage() { return applicationBrandImage(); }

    /** Backward-compatible alias retained for older callers. */
    public static Image logo() { return applicationBrandImage(); }

    private static Image configuredImage(String configKey) {
        try {
            String configured = ConfigManager.get(configKey, "").trim();
            if (configured.isBlank()) return null;
            Path path = Path.of(configured).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) return null;
            // Load from a fresh stream so replacing the same asset filename does
            // not leave a stale URL-cached image in a long-running application.
            try (InputStream input = Files.newInputStream(path)) {
                Image image = new Image(input);
                return image.isError() ? null : image;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String value(String key, String fallback) {
        try {
            String configured = ConfigManager.get(key, fallback);
            return configured == null || configured.isBlank() ? fallback : configured.trim();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
