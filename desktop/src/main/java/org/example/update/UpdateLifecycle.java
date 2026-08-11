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
                UpdateHistoryStore.append(buildVersion, ConfigManager.get("update.channel", "STABLE"), "SUCCESS", "Upgraded from " + previous + "; database schema " + migration.fromVersion() + " → " + migration.toVersion());
                Platform.runLater(() -> {
                    Alert alert = new OwnedAlert(Alert.AlertType.INFORMATION);
                    if (owner != null) alert.initOwner(owner);
                    alert.setHeaderText("Update completed successfully");
                    alert.setContentText("DSE ERP " + buildVersion + " is installed.\nDatabase schema: " + migration.toVersion());
                    alert.show();
                });
            }
        } catch (Exception exception) {
            UpdateHistoryStore.append(BuildInfo.version(), ConfigManager.get("update.channel", "STABLE"), "MIGRATION_FAILED", exception.getMessage());
            throw new IllegalStateException("Application database migration failed", exception);
        }
    }
}
