package org.example.app;

import org.example.util.OwnedAlert;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.backup.BackupManager;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.database.DatabaseManager;
import org.example.migration.AutomaticPostgresMigration;
import org.example.persistence.SpringPersistence;
import org.example.update.UpdateLifecycle;
import org.example.update.UpdateStartupChecker;
import org.example.util.SceneManager;
import org.example.util.WindowUtilsFx;
import org.example.util.PerformanceMonitor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main {
    private ScheduledExecutorService backupScheduler;
    private boolean stopped;

    public void start(Stage stage) {
        stage.initStyle(StageStyle.DECORATED);
        stage.setResizable(true);
        WorkspaceManager.initialize();
        SceneManager.initialize(stage);
        WindowUtilsFx.apply(stage, 1200, 800);

        if (!WorkspaceManager.isConfigured()) {
            SceneManager.showSetupWizard(() -> completeFirstRun(stage));
            return;
        }
        initializeConfiguredApplication(stage);
    }

    private void initializeConfiguredApplication(Stage stage) {
        ConfigManager.load();
        BackupManager.RestoreResult restoreResult = BackupManager.applyPendingRestoreIfPresent();
        if (restoreResult.attempted() && !restoreResult.applied()) {
            if (restoreResult.failure() != null) restoreResult.failure().printStackTrace();
            new OwnedAlert(Alert.AlertType.ERROR,
                    restoreResult.message() + "\n\nThe ERP will continue using the preserved database.")
                    .showAndWait();
        }
        AutomaticPostgresMigration.Result databaseUpgrade = AutomaticPostgresMigration.attempt();
        if (databaseUpgrade.failure() != null) databaseUpgrade.failure().printStackTrace();
        try {
            DatabaseManager.initialize();
            if (ConfigManager.isPostgreSql()) SpringPersistence.initialize();
            else BackupManager.ensureApplicationMetadata();
        } catch (Exception exception) {
            exception.printStackTrace();
            new OwnedAlert(Alert.AlertType.ERROR,
                    "Database initialization failed: " + exception.getMessage()).showAndWait();
        }
        finishStartup(stage);
        showDatabaseUpgradeResult(databaseUpgrade);
        if (restoreResult.applied()) {
            Platform.runLater(() -> {
                String safety = restoreResult.safetyBackup() == null
                        ? "No previous database existed."
                        : "Safety backup: " + restoreResult.safetyBackup();
                Alert alert = new OwnedAlert(Alert.AlertType.INFORMATION,
                        "The staged database restore was applied successfully.\n\n" + safety);
                alert.setHeaderText("Database restore completed");
                alert.show();
            });
        }
    }

    private void showDatabaseUpgradeResult(AutomaticPostgresMigration.Result result) {
        if (result.status() == AutomaticPostgresMigration.Status.NOT_REQUIRED) return;
        Platform.runLater(() -> {
            if (result.status() == AutomaticPostgresMigration.Status.MIGRATED) {
                String detail = result.report().alreadyMigrated()
                        ? "The previously migrated PostgreSQL data was verified and reconnected."
                        : "Migrated " + result.report().tableCount() + " tables and "
                        + result.report().rowCount() + " rows.";
                Alert alert = new OwnedAlert(Alert.AlertType.INFORMATION,
                        detail + "\n\nPostgreSQL: " + result.targetUrl()
                                + "\nSQLite safety backup: " + result.safetyBackup());
                alert.setHeaderText("PostgreSQL upgrade completed");
                alert.show();
                return;
            }
            String backup = result.safetyBackup() == null ? "Not created" : result.safetyBackup().toString();
            Alert alert = new OwnedAlert(Alert.AlertType.WARNING,
                    "PostgreSQL is not ready, so DSE ERP is continuing with the original SQLite database."
                            + " No business data was removed.\n\nReason: "
                            + (result.failure() == null ? "Unknown" : result.failure().getMessage())
                            + "\nSafety snapshot: " + backup);
            alert.setHeaderText("PostgreSQL migration postponed");
            alert.show();
        });
    }

    /** SetupWizardController has already created the workspace and initialized its database. */
    private void completeFirstRun(Stage stage) {
        if (ConfigManager.isPostgreSql()) SpringPersistence.initialize();
        finishStartup(stage);
        SceneManager.showLogin();
    }

    private void finishStartup(Stage stage) {
        stage.show();
        PerformanceMonitor.event("runtime",
            "os=" + System.getProperty("os.name")
                + " | arch=" + System.getProperty("os.arch")
                + " | java=" + System.getProperty("java.version")
                + " | javafx=" + System.getProperty("javafx.version")
                + " | scale=" + stage.getOutputScaleX() + "x" + stage.getOutputScaleY());
        UpdateLifecycle.afterDatabaseInitialization(stage);
        if (stage.getScene() == null) SceneManager.showLogin();
        startBackupScheduler();
        UpdateStartupChecker.checkLater(stage);
    }

    private void startBackupScheduler() {
        if (backupScheduler != null) return;
        backupScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "erp-backup-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        backupScheduler.scheduleWithFixedDelay(
                BackupManager::createScheduledBackupIfDue, 0, 1, TimeUnit.HOURS);
    }

    public synchronized void stop() {
        if (stopped) return;
        stopped = true;
        if (backupScheduler != null) backupScheduler.shutdownNow();
        SpringPersistence.close();
    }

    public static void launch(String[] args) {
        Platform.startup(() -> {
            Main application = new Main();
            Stage stage = new Stage();
            stage.setOnHidden(event -> application.stop());
            try {
                application.start(stage);
            } catch (Throwable failure) {
                failure.printStackTrace();
                application.stop();
                Platform.exit();
            }
        });
    }
}
