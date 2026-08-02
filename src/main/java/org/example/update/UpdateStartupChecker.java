package org.example.update;

import javafx.application.Platform;
import javafx.stage.Window;
import org.example.config.ConfigManager;

import java.time.Duration;
import java.time.Instant;

public final class UpdateStartupChecker {
    private UpdateStartupChecker() {}
    public static void checkLater(Window owner) {
        if (!Boolean.parseBoolean(ConfigManager.get("update.checkAtStartup", "true"))) return;
        if (ConfigManager.get("update.github.owner", UpdateService.DEFAULT_GITHUB_OWNER).isBlank() || ConfigManager.get("update.github.repository", UpdateService.DEFAULT_GITHUB_REPOSITORY).isBlank()) return;
        String raw = ConfigManager.get("update.lastChecked", "");
        try { if (!raw.isBlank() && Duration.between(Instant.parse(raw), Instant.now()).toHours() < 12) return; } catch (Exception ignored) {}
        Platform.runLater(() -> UpdateDialogs.checkForUpdates(owner, true));
    }
}
