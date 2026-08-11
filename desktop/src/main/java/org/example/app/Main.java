package org.example.app;

import org.example.util.OwnedAlert;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.backup.BackupManager;
import org.example.api.runtime.RuntimeBootstrapper;
import org.example.api.runtime.RuntimeHealthMonitor;
import org.example.api.runtime.ManagedPostgresRuntime;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.service.SessionService;
import org.example.update.UpdateLifecycle;
import org.example.update.UpdateStartupChecker;
import org.example.util.SceneManager;
import org.example.util.WindowUtilsFx;
import org.example.util.PerformanceMonitor;
import org.example.util.PerformanceBudgets;
import org.example.util.FxResponsivenessMonitor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main {
    private ScheduledExecutorService backupScheduler;
    private boolean stopped;
    private final FxResponsivenessMonitor responsivenessMonitor = new FxResponsivenessMonitor();
    private final RuntimeHealthMonitor runtimeHealthMonitor = new RuntimeHealthMonitor();

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
        PerformanceMonitor.start("warm-startup");
        SceneManager.showSplash();
        Thread startup = new Thread(() -> initializeInBackground(stage), "dse-startup");
        startup.setDaemon(true);
        startup.start();
    }

    private void initializeInBackground(Stage stage) {
        ConfigManager.load();
        try {
            SceneManager.updateSplashStatus("Preparing local PostgreSQL...");
            ManagedPostgresRuntime.ensureReady();
            SceneManager.updateSplashStatus("Database runtime ready...");
        } catch (Exception exception) {
            exception.printStackTrace();
            Platform.runLater(() -> {
                Alert alert = new OwnedAlert(Alert.AlertType.ERROR,
                        "DSE ERP could not prepare its local PostgreSQL database.\n\n" + exception.getMessage());
                alert.setHeaderText("Database runtime startup failed");
                alert.showAndWait();
            });
            return;
        }
        BackupManager.RestoreResult restoreResult = BackupManager.applyPendingRestoreIfPresent();
        if (restoreResult.attempted() && !restoreResult.applied()) {
            if (restoreResult.failure() != null) restoreResult.failure().printStackTrace();
        }
        try {
            SceneManager.updateSplashStatus("Starting DSE ERP services...");
            RuntimeBootstrapper.ensureServerReady();
            SceneManager.updateSplashStatus("Spring is verifying the ERP schema...");
            new org.example.api.runtime.RuntimeApiClient().status();
            SceneManager.updateSplashStatus("Services ready. Opening DSE ERP...");
        } catch (Exception exception) {
            exception.printStackTrace();
            Platform.runLater(() -> {
                Alert alert = new OwnedAlert(Alert.AlertType.ERROR,
                        "DSE ERP services could not start automatically.\n\n" + exception.getMessage()
                                + "\n\nServer log: " + RuntimeBootstrapper.serverLogPath());
                alert.setHeaderText("DSE ERP startup failed");
                alert.showAndWait();
            });
            return;
        }
        Platform.runLater(() -> {
            // Splash is non-interactive. Still guard the transition so a late startup
            // callback can never replace an already authenticated application shell.
            if (SessionService.current() == null) SceneManager.showLogin();
            finishStartup(stage);
            if (restoreResult.attempted() && !restoreResult.applied()) {
                new OwnedAlert(Alert.AlertType.ERROR,
                        restoreResult.message() + "\n\nThe ERP will continue using the preserved database.").show();
            } else if (restoreResult.applied()) {
                String safety = restoreResult.safetyBackup() == null
                        ? "No previous database existed."
                        : "Safety backup: " + restoreResult.safetyBackup();
                Alert alert = new OwnedAlert(Alert.AlertType.INFORMATION,
                        "The staged database restore was applied successfully.\n\n" + safety);
                alert.setHeaderText("Database restore completed");
                alert.show();
            }
        });
    }

    /** SetupWizardController has created the workspace and bootstrapped company/admin data through the Spring API. */
    private void completeFirstRun(Stage stage) {
        SceneManager.showSplash();
        Thread firstRunStartup = new Thread(() -> {
            try {
                ConfigManager.load();
                SceneManager.updateSplashStatus("Preparing local PostgreSQL...");
                ManagedPostgresRuntime.ensureReady();
                SceneManager.updateSplashStatus("Starting DSE ERP services...");
                RuntimeBootstrapper.ensureServerReady();
                Platform.runLater(() -> {
                    finishStartup(stage);
                    if (SessionService.current() == null) SceneManager.showLogin();
                });
            } catch (Exception exception) {
                exception.printStackTrace();
                Platform.runLater(() -> {
                    Alert alert = new OwnedAlert(Alert.AlertType.ERROR,
                            "DSE ERP services could not start after setup.\n\n" + exception.getMessage()
                                    + "\n\nServer log: " + RuntimeBootstrapper.serverLogPath());
                    alert.setHeaderText("First-time startup failed");
                    alert.showAndWait();
                });
            }
        }, "dse-first-run-startup");
        firstRunStartup.setDaemon(true);
        firstRunStartup.start();
    }

    private void finishStartup(Stage stage) {
        stage.show();
        responsivenessMonitor.start();
        long startupMillis = PerformanceMonitor.finish("warm-startup");
        if (startupMillis >= 0) PerformanceBudgets.record("warm-startup", startupMillis,
                PerformanceBudgets.WARM_STARTUP_MS);
        PerformanceMonitor.event("runtime",
            "os=" + System.getProperty("os.name")
                + " | arch=" + System.getProperty("os.arch")
                + " | java=" + System.getProperty("java.version")
                + " | javafx=" + System.getProperty("javafx.version")
                + " | scale=" + stage.getOutputScaleX() + "x" + stage.getOutputScaleY());
        runtimeHealthMonitor.start();
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
        responsivenessMonitor.stop();
        runtimeHealthMonitor.close();
        RuntimeBootstrapper.shutdownManagedServer();
        ManagedPostgresRuntime.shutdownIfConfigured();
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
