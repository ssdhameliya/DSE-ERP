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
import org.example.api.setup.SetupApiClient;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.service.SessionService;
import org.example.service.BrandingService;
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

        if (!WorkspaceManager.isConfigured() || !WorkspaceManager.isSetupComplete()) {
            SceneManager.showSetupWizard(() -> completeFirstRun(stage));
            return;
        }
        initializeConfiguredApplication(stage);
    }

    private void initializeConfiguredApplication(Stage stage) {
        PerformanceMonitor.start("warm-startup");
        // Load workspace configuration before Splash.fxml is created so application
        // branding (name, tagline, startup message and brand image) is available on
        // the very first rendered frame instead of falling back to hard-coded defaults.
        ConfigManager.load();
        SceneManager.showSplash();
        Thread startup = new Thread(() -> initializeInBackground(stage), "dse-startup");
        startup.setDaemon(true);
        startup.start();
    }

    private void initializeInBackground(Stage stage) {
        SceneManager.updateSplashStage(1, "Workspace and configuration loaded.");
        // Reload is intentionally safe here in case another startup component changed
        // configuration after the initial splash preload, then refresh visible branding.
        ConfigManager.load();
        SceneManager.refreshSplashBranding();
        try {
            SceneManager.updateSplashStage(2, "Preparing local PostgreSQL...");
            ManagedPostgresRuntime.ensureReady();
            SceneManager.updateSplashStage(2, "PostgreSQL is ready.");
        } catch (Exception exception) {
            exception.printStackTrace();
            Platform.runLater(() -> {
                Alert alert = new OwnedAlert(Alert.AlertType.ERROR,
                        BrandingService.applicationName() + " could not prepare its local PostgreSQL database.\n\n" + exception.getMessage());
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
            SceneManager.updateSplashStage(3, "Starting Spring Boot services...");
            RuntimeBootstrapper.ensureServerReady();
            SceneManager.updateSplashStage(4, "Verifying database, schema and migrations...");
            new org.example.api.runtime.RuntimeApiClient().status();
            if (new SetupApiClient().requiresSetup()) {
                Platform.runLater(() -> SceneManager.showSetupWizard(() -> completeFirstRun(stage)));
                return;
            }
            SceneManager.updateSplashStage(5, "Finalizing " + BrandingService.applicationName() + "...");
            SceneManager.markSplashReady("Services ready. Opening " + BrandingService.applicationName() + "...");
        } catch (Exception exception) {
            exception.printStackTrace();
            Platform.runLater(() -> {
                Alert alert = new OwnedAlert(Alert.AlertType.ERROR,
                        BrandingService.applicationName() + " services could not start automatically.\n\n" + exception.getMessage()
                                + "\n\nServer log: " + RuntimeBootstrapper.serverLogPath());
                alert.setHeaderText(BrandingService.applicationName() + " startup failed");
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
        // The setup wizard has now created the workspace, so load its configuration
        // before constructing the splash and immediately show the user's branding.
        ConfigManager.load();
        SceneManager.showSplash();
        Thread firstRunStartup = new Thread(() -> {
            try {
                SceneManager.updateSplashStage(1, "Workspace and configuration loaded.");
                ConfigManager.load();
                SceneManager.refreshSplashBranding();
                SceneManager.updateSplashStage(2, "Preparing local PostgreSQL...");
                ManagedPostgresRuntime.ensureReady();
                SceneManager.updateSplashStage(3, "Starting Spring Boot services...");
                RuntimeBootstrapper.ensureServerReady();
                SceneManager.updateSplashStage(4, "Verifying database, schema and migrations...");
                new org.example.api.runtime.RuntimeApiClient().status();
                SceneManager.updateSplashStage(5, "Finalizing " + BrandingService.applicationName() + "...");
                SceneManager.markSplashReady("Services ready. Opening " + BrandingService.applicationName() + "...");
                Platform.runLater(() -> {
                    finishStartup(stage);
                    if (SessionService.current() == null) SceneManager.showLogin();
                });
            } catch (Exception exception) {
                exception.printStackTrace();
                Platform.runLater(() -> {
                    Alert alert = new OwnedAlert(Alert.AlertType.ERROR,
                            BrandingService.applicationName() + " services could not start after setup.\n\n" + exception.getMessage()
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
                + " | scale=" + stage.getOutputScaleX() + "x" + stage.getOutputScaleY()
                + " | prism.order=" + System.getProperty("prism.order", "javafx-default")
                + " | prism.verbose=" + System.getProperty("prism.verbose", "false"));
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
