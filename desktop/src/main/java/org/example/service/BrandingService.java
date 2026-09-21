package org.example.service;

import javafx.application.Platform;
import javafx.scene.image.Image;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;
import org.example.util.UiTaskExecutor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Workspace-aware branding with safe built-in fallbacks for first launch. */
public final class BrandingService {
    private static final String TECHNICAL_PRODUCT_NAME = "DSE ERP";
    private static volatile String sharedCompanyName;
    private static volatile String sharedCompanyTagline;
    private static final AtomicBoolean sharedIdentityRefreshPending = new AtomicBoolean();

    private BrandingService() { }

    /** Stable internal product identity used only where a technical product name is required. */
    public static String technicalProductName() { return TECHNICAL_PRODUCT_NAME; }

    /** Canonical customer-facing identity. Shared clients resolve company.* from the company server after login. */
    public static String companyName() {
        String fallback = value("application.displayName", "");
        if (fallback.isBlank()) fallback = TECHNICAL_PRODUCT_NAME;
        if (mustAvoidSharedServerReadOnFxThread()) {
            String cached = sharedCompanyName;
            if (cached == null) refreshSharedIdentityAsync();
            return cached == null || cached.isBlank() ? fallback : cached;
        }
        String company = value("company.name", "");
        if (!company.isBlank()) {
            sharedCompanyName = company;
            return company;
        }
        return fallback;
    }

    /**
     * Backward-compatible UI accessor. Customer-facing surfaces must follow company.name rather
     * than a workstation-local application.displayName that can drift from UAT/PROD company data.
     */
    public static String applicationName() { return companyName(); }

    public static String tagline() {
        String applicationTagline = value("application.tagline", "");
        if (!applicationTagline.isBlank()) return applicationTagline;
        if (mustAvoidSharedServerReadOnFxThread()) {
            String cached = sharedCompanyTagline;
            if (cached == null) refreshSharedIdentityAsync();
            return cached == null || cached.isBlank() ? "Business Management Suite" : cached;
        }
        String companyTagline = value("company.tagline", "Business Management Suite");
        sharedCompanyTagline = companyTagline;
        return companyTagline;
    }

    /**
     * Invalidates the lightweight shared-company identity cache after Company settings change.
     * The next UI read returns a safe local fallback immediately and refreshes in the background.
     */
    public static void invalidateSharedIdentity() {
        sharedCompanyName = null;
        sharedCompanyTagline = null;
        sharedIdentityRefreshPending.set(false);
    }

    /** Preloads shared-client company identity without ever blocking the JavaFX application thread. */
    public static void refreshSharedIdentityAsync() {
        if (!ConfigManager.isSharedClient() || !ApiSession.isEstablished()) return;
        if (!sharedIdentityRefreshPending.compareAndSet(false, true)) return;
        UiTaskExecutor.submitLatest(
                "branding-shared-identity",
                () -> {
                    String company = value("company.name", "");
                    String tagline = value("company.tagline", "Business Management Suite");
                    return new String[]{company, tagline};
                },
                values -> {
                    sharedCompanyName = values[0];
                    sharedCompanyTagline = values[1];
                    sharedIdentityRefreshPending.set(false);
                },
                failure -> sharedIdentityRefreshPending.set(false)
        );
    }

    private static boolean mustAvoidSharedServerReadOnFxThread() {
        return Platform.isFxApplicationThread() && ConfigManager.isSharedClient() && ApiSession.isEstablished();
    }

    public static String startingText() {
        String configured = value("application.startingText", "");
        if (configured.isBlank() || configured.equalsIgnoreCase("Starting DSE ERP...")
                || configured.equalsIgnoreCase("Starting DSE ERP…")) {
            return "Starting " + applicationName() + "...";
        }
        return configured.replace(TECHNICAL_PRODUCT_NAME, applicationName());
    }

    public static String loginDescription() { return "Role-aware secure access to " + applicationName(); }

    /** Application UI banner used by Splash/Login/Registration/Email screens. */
    public static Image applicationBrandImage() {
        return configuredImage("application.brandImagePath");
    }

    /** Square application mark used below the wide banner on auth/startup screens. */
    public static Image applicationMarkImage() {
        return configuredImage("application.markImagePath");
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
