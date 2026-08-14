package org.example.service;

import javafx.scene.image.Image;
import org.example.config.ConfigManager;
import java.nio.file.Files;
import java.nio.file.Path;

/** Workspace-aware branding with safe built-in fallbacks for first launch. */
public final class BrandingService {
    private BrandingService() {}
    public static String companyName() { return value("company.name", "DSE ERP"); }
    public static String applicationName() { return value("application.displayName", "DSE ERP"); }
    public static String tagline() { return value("application.tagline", "Business Management Suite"); }
    public static String startingText() { return value("application.startingText", "Starting " + applicationName() + "..."); }
    public static String loginDescription() { return "Role-aware secure access to " + applicationName(); }
    public static Image brandImage() {
        try {
            String configured = ConfigManager.get("application.brandImagePath", "").trim();
            if (!configured.isBlank()) {
                Path path = Path.of(configured).toAbsolutePath().normalize();
                if (Files.isRegularFile(path)) return new Image(path.toUri().toString(), true);
            }
        } catch (Exception ignored) { }
        return null;
    }
    /** Backward-compatible alias for application UI branding. */
    public static Image logo() { return brandImage(); }
    private static String value(String key, String fallback) {
        try {
            String configured = ConfigManager.get(key, fallback);
            return configured == null || configured.isBlank() ? fallback : configured.trim();
        } catch (Exception ignored) { return fallback; }
    }
}
