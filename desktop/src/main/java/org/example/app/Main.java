package org.example.app;

import org.example.util.OwnedAlert;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
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
import org.example.util.DesktopLog;

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
        DesktopLog.initialize();
        DesktopLog.info("Main", "START", "DSE ERP desktop start requested");
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
            if (ConfigManager.isSharedClient()) {
                SceneManager.updateSplashStage(2, "Connecting to company server...");
            } else {
                SceneManager.updateSplashStage(2, "Preparing local PostgreSQL...");
                ManagedPostgresRuntime.ensureReady();
                SceneManager.updateSplashStage(2, "PostgreSQL is ready.");
            }
        } catch (Exception exception) {
            DesktopLog.error("Main", "POSTGRES_START_FAILED", "Managed PostgreSQL startup failed", exception);
            Platform.runLater(() -> showStartupFailureWithWorkspaceRecovery(
                    stage,
                    "Database runtime startup failed",
                    BrandingService.applicationName() + " could not prepare its local PostgreSQL database.\n\n" + exception.getMessage()));
            return;
        }
        BackupManager.RestoreResult restoreResult = ConfigManager.isSharedClient()
                ? BackupManager.RestoreResult.none()
                : BackupManager.applyPendingRestoreIfPresent();
        if (restoreResult.attempted() && !restoreResult.applied()) {
            if (restoreResult.failure() != null) DesktopLog.error("Main", "RESTORE_FAILED", restoreResult.message(), restoreResult.failure());
        }
        try {
            SceneManager.updateSplashStage(3, "Starting Spring Boot services...");
            RuntimeBootstrapper.ensureServerReady();
            SceneManager.updateSplashStage(4, "Verifying database, schema and migrations...");
            new org.example.api.runtime.RuntimeApiClient().status();
            if (new SetupApiClient().requiresSetup()) {
                if (ConfigManager.isSharedClient()) throw new IllegalStateException(
                        "The company server has not been initialized. Complete setup on the server computer first.");
                Platform.runLater(() -> SceneManager.showSetupWizard(() -> completeFirstRun(stage)));
                return;
            }
            SceneManager.updateSplashStage(5, "Finalizing " + BrandingService.applicationName() + "...");
            SceneManager.markSplashReady("Services ready. Opening " + BrandingService.applicationName() + "...");
        } catch (Exception exception) {
            DesktopLog.error("Main", "SERVER_START_FAILED", "Spring services could not start", exception);
            Platform.runLater(() -> showStartupFailureWithWorkspaceRecovery(
                    stage,
                    BrandingService.applicationName() + " startup failed",
                    BrandingService.applicationName() + " services could not start automatically.\n\n" + exception.getMessage()
                            + "\n\nServer log: " + RuntimeBootstrapper.serverLogPath()));
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
                org.example.util.ToastManager.success(stage, "Database restore completed",
                        "The staged database restore was applied successfully. " + safety);
            }
        });
    }

    /** SetupWizardController has created the workspace and bootstrapped company/admin data through the Spring API. */
    private void showStartupFailureWithWorkspaceRecovery(Stage stage, String header, String message) {
        ButtonType existing = new ButtonType("Select Existing Workspace", ButtonBar.ButtonData.OTHER);
        ButtonType exit = new ButtonType("Exit", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new OwnedAlert(Alert.AlertType.ERROR, message, existing, exit);
        alert.setHeaderText(header);
        ButtonType choice = alert.showAndWait().orElse(exit);
        if (choice == existing) SceneManager.showSetupWizard(() -> completeFirstRun(stage));
        else Platform.exit();
    }

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
                if (!ConfigManager.isSharedClient()) {
                    SceneManager.updateSplashStage(2, "Preparing local PostgreSQL...");
                    ManagedPostgresRuntime.ensureReady();
                } else SceneManager.updateSplashStage(2, "Connecting to company server...");
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
                DesktopLog.error("Main", "FIRST_RUN_START_FAILED", "Services could not start after setup", exception);
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
        if (ConfigManager.isSharedClient()) return;
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
                DesktopLog.error("Main", "UNCAUGHT_START_FAILURE", "Desktop startup failed", failure);
                application.stop();
                Platform.exit();
            }
        });
    }
}
