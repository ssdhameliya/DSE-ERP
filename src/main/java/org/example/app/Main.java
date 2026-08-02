package org.example.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.example.backup.BackupManager;
import org.example.database.DatabaseManager;
import org.example.config.ConfigManager;
import org.example.util.SceneManager;
import org.example.util.WindowUtilsFx;
import org.example.update.UpdateStartupChecker;
import org.example.update.UpdateLifecycle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main extends Application {

    private ScheduledExecutorService backupScheduler;

    @Override
    public void start(Stage stage) {
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
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR,
                    "Database initialization failed: " + e.getMessage()).showAndWait();
        }

        SceneManager.initialize(stage);
        WindowUtilsFx.apply(stage, 1200, 800);
        stage.show();
        UpdateLifecycle.afterDatabaseInitialization(stage);
        SceneManager.showSplash();

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

        startBackupScheduler();
        UpdateStartupChecker.checkLater(stage);
    }

    private void startBackupScheduler() {
        backupScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "erp-backup-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        backupScheduler.scheduleWithFixedDelay(
                BackupManager::createScheduledBackupIfDue,
                0,
                1,
                TimeUnit.HOURS
        );
    }

    @Override
    public void stop() {
        if (backupScheduler != null) {
            backupScheduler.shutdownNow();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
