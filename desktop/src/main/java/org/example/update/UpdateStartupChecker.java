package org.example.update;

import javafx.application.Platform;
import javafx.stage.Window;
import org.example.config.ConfigManager;

import java.time.Duration;
import java.time.Instant;

public final class UpdateStartupChecker {
    private UpdateStartupChecker() {}
    public static void checkLater(Window owner) {
        if (UpdateDialogs.isCompatibleUpdateDeferredForSession()) return;
        if (!org.example.service.PermissionService.allowed("APPLICATION_UPDATES.CHECK")) return;
        if (!Boolean.parseBoolean(ConfigManager.get("update.checkAtStartup", "true"))) return;
        String raw = ConfigManager.get("update.lastChecked", "");
        try { if (!raw.isBlank() && Duration.between(Instant.parse(raw), Instant.now()).toHours() < 12) return; } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        Platform.runLater(() -> UpdateDialogs.checkForUpdates(owner, true));
    }
}
