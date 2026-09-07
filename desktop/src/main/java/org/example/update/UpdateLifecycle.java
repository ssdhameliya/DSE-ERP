package org.example.update;

import org.example.util.OwnedAlert;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Window;
import org.example.config.ConfigManager;

public final class UpdateLifecycle {
    private UpdateLifecycle() {}
    public static void afterDatabaseInitialization(Window owner) {
        try {
            DatabaseMigrationManager.MigrationResult migration = DatabaseMigrationManager.migrate();
            String buildVersion = BuildInfo.version();
            String previous = ConfigManager.get("app.version", "");
            ConfigManager.set("app.version", buildVersion);
            if (!previous.isBlank() && SemanticVersion.parse(buildVersion).compareTo(SemanticVersion.parse(previous)) > 0) {
                boolean sharedClient = ConfigManager.isSharedClient();
                String detail = sharedClient
                        ? "Shared client upgraded from " + previous + "; company-server schema remains server-managed"
                        : "Upgraded from " + previous + "; database schema " + migration.fromVersion() + " → " + migration.toVersion();
                UpdateHistoryStore.append(buildVersion, ConfigManager.get("update.channel", "STABLE"), "SUCCESS", detail);
                // Guarantee one user-visible What's New dialog after a real application upgrade,
                // even if an older profile already carried a releaseNotesSeen value.
                ConfigManager.set("update.releaseNotesPending", buildVersion);
                Platform.runLater(() -> org.example.util.ToastManager.success(owner,
                    sharedClient ? "Client updated" : "Update completed",
                    sharedClient
                            ? "DSE ERP " + buildVersion + " client is ready for the company server."
                            : "DSE ERP " + buildVersion + " is installed. Database schema: " + migration.toVersion()));
            }
        } catch (Exception exception) {
            UpdateHistoryStore.append(BuildInfo.version(), ConfigManager.get("update.channel", "STABLE"), "MIGRATION_FAILED", exception.getMessage());
            throw new IllegalStateException("Application database migration failed", exception);
        }
    }
}
