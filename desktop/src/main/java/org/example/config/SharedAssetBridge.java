package org.example.config;

import org.example.api.authority.ServerResourceClient;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public final class SharedAssetBridge {
    private static final String PREFIX = "server-resource:";
    public static final List<String> ASSET_KEYS = List.of(
            "company.logoPath",
            "company.signaturePath",
            "payment.qrImagePath",
            "application.brandImagePath",
            "application.markImagePath"
    );

    private SharedAssetBridge() {}

    public static boolean isAssetKey(String k) {
        return ASSET_KEYS.contains(k);
    }

    public static String publish(String key, String value) {
        try {
            var api = new ServerResourceClient();
            if (value == null || value.isBlank()) {
                try {
                    api.delete("BUSINESS_ASSET", key);
                } catch (Exception ignored) {
                    java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored);
                }
                return "";
            }
            Path p = Path.of(value);
            if (!Files.isRegularFile(p)) return "";
            api.put("BUSINESS_ASSET", key, p.getFileName().toString(), Files.readAllBytes(p));
            return PREFIX + key;
        } catch (IOException e) {
            throw new IllegalStateException("Business asset could not be uploaded to the company server", e);
        }
    }

    public static String resolve(String key, String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return stored;
        try {
            Path folder = ConfigManager.getConfigurationFolder().resolve("assets");
            Files.createDirectories(folder);
            Path target = folder.resolve(key.replaceAll("[^A-Za-z0-9._-]", "_") + ".png");
            byte[] bytes = new ServerResourceClient().get("BUSINESS_ASSET", key);
            if (bytes != null && bytes.length > 0) {
                Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return target.toAbsolutePath().toString();
            }
            return "";
        } catch (Exception e) {
            java.lang.System.getLogger("org.example.config").log(java.lang.System.Logger.Level.DEBUG, "Could not resolve server asset: " + key + ": " + e.getMessage());
            return "";
        }
    }

    public static void syncAllAsync() {
        if (!ConfigManager.isSharedClient() || !org.example.api.ApiSession.isEstablished()) return;
        org.example.util.UiTaskExecutor.submitAction(
                "sync-business-assets",
                () -> { syncAll(); return Boolean.TRUE; },
                done -> org.example.service.BrandingService.invalidateSharedIdentity(),
                err -> java.lang.System.getLogger("org.example.config").log(java.lang.System.Logger.Level.DEBUG, "Asset sync background warning: " + err.getMessage())
        );
    }

    public static void syncAll() {
        if (!ConfigManager.isSharedClient() || !org.example.api.ApiSession.isEstablished()) return;
        var support = new org.example.api.support.SupportApiClient();
        for (String key : ASSET_KEYS) {
            try {
                String remote = support.setting(key, "");
                if (remote != null && remote.startsWith(PREFIX)) {
                    String localPath = resolve(key, remote);
                    if (localPath != null && !localPath.isBlank()) {
                        ConfigManager.setWithoutSaving(key, localPath.replace('\\', '/'));
                    }
                } else {
                    String localConfigured = ConfigManager.get(key, "");
                    if (localConfigured != null && !localConfigured.isBlank()) {
                        Path p = Path.of(localConfigured);
                        if (Files.isRegularFile(p)) {
                            String published = publish(key, localConfigured);
                            if (!published.isBlank()) {
                                support.setSetting(key, published);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                java.lang.System.getLogger("org.example.config").log(java.lang.System.Logger.Level.DEBUG, "Sync asset error for " + key + ": " + ex.getMessage());
            }
        }
        ConfigManager.save();
    }
}

