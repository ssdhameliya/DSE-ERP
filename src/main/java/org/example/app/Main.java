package org.example.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.example.backup.BackupManager;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.database.DatabaseManager;
import org.example.update.UpdateLifecycle;
import org.example.update.UpdateStartupChecker;
import org.example.util.SceneManager;
import org.example.util.WindowUtilsFx;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main extends Application {
    private ScheduledExecutorService backupScheduler;

    @Override public void start(Stage stage) {
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
            new Alert(Alert.AlertType.ERROR,
                    restoreResult.message() + "\n\nThe ERP will continue using the preserved database.")
                    .showAndWait();
        }
        try {
            DatabaseManager.initialize();
            BackupManager.ensureApplicationMetadata();
        } catch (Exception exception) {
            exception.printStackTrace();
            new Alert(Alert.AlertType.ERROR,
                    "Database initialization failed: " + exception.getMessage()).showAndWait();
        }
        finishStartup(stage);
        if (restoreResult.applied()) {
            Platform.runLater(() -> {
                String safety = restoreResult.safetyBackup() == null
                        ? "No previous database existed."
                        : "Safety backup: " + restoreResult.safetyBackup();
                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        "The staged database restore was applied successfully.\n\n" + safety);
                alert.setHeaderText("Database restore completed");
                alert.show();
            });
        }
    }

    /** SetupWizardController has already created the workspace and initialized SQLite. */
    private void completeFirstRun(Stage stage) {
        finishStartup(stage);
        SceneManager.showLogin();
    }

    private void finishStartup(Stage stage) {
        stage.show();
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

    @Override public void stop() {
        if (backupScheduler != null) backupScheduler.shutdownNow();
    }

    public static void main(String[] args) { launch(args); }
}
