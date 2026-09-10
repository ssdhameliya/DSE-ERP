package org.example.app;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;
import org.example.util.DialogPresentation;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.collections.FXCollections;
import javafx.scene.layout.VBox;
import org.example.backup.BackupManager;
import org.example.backup.LocalRecoveryManager;
import org.example.api.runtime.RuntimeBootstrapper;
import org.example.api.runtime.RuntimeApiClient;
import org.example.api.runtime.RuntimeHealthMonitor;
import org.example.api.runtime.ManagedPostgresRuntime;
import org.example.api.runtime.DeploymentConnectionService;
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

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

        if (requiresWorkspaceChooser(WorkspaceManager.isConfigured())) {
            SceneManager.showSetupWizard(() -> completeFirstRun(stage));
            return;
        }
        // A saved, valid workspace must always be attempted automatically. Older workspaces may
        // pre-date the local setup.completed marker; the authoritative setup state is verified
        // through the server API after PostgreSQL/Spring services are ready.
        initializeConfiguredApplication(stage);
    }


    static boolean requiresWorkspaceChooser(boolean workspaceConfigured) {
        return !workspaceConfigured;
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
        LocalRecoveryManager.FileApplyResult recoveryFiles = ConfigManager.isSharedClient()
                ? LocalRecoveryManager.FileApplyResult.none()
                : LocalRecoveryManager.applyPendingFilesIfReady();
        if (recoveryFiles.attempted() && !recoveryFiles.applied()) {
            if (recoveryFiles.failure() != null) DesktopLog.error("Main", "LOCAL_RECOVERY_FILES_FAILED", recoveryFiles.message(), recoveryFiles.failure());
            Platform.runLater(() -> showStartupFailureWithWorkspaceRecovery(stage,
                    "LOCAL disaster recovery incomplete",
                    recoveryFiles.message() + "\n\nDSE ERP will not open the recovered LOCAL company until its business files are consistent."));
            return;
        }
        RuntimeApiClient.RuntimeStatus startupRuntime;
        try {
            SceneManager.updateSplashStage(3, "Starting Spring Boot services...");
            startupRuntime = RuntimeBootstrapper.ensureServerReady();
            SceneManager.updateSplashStage(4, "Verifying database, schema and migrations...");
            new org.example.api.runtime.RuntimeApiClient().status();
            if (new SetupApiClient().requiresSetup()) {
                if (ConfigManager.isSharedClient()) throw new IllegalStateException(
                        "The company server has not been initialized. Complete setup on the server computer first.");
                Platform.runLater(() -> SceneManager.showSetupWizard(() -> completeFirstRun(stage)));
                return;
            }
            // The database/server is already initialized, so repair only the local legacy marker.
            // This is deliberately non-destructive and prevents upgrades from asking users to
            // select an already-configured workspace again.
            if (!WorkspaceManager.isSetupComplete()) WorkspaceManager.markSetupComplete();
            SceneManager.updateSplashStage(5, "Finalizing " + BrandingService.applicationName() + "...");
            SceneManager.markSplashReady("Services ready. Opening " + BrandingService.applicationName() + "...");
        } catch (Exception exception) {
            if (exception instanceof org.example.api.runtime.DeploymentConnectionService.ClientUpdateRequiredException updateRequired) {
                DesktopLog.info("Main", "CLIENT_UPDATE_REQUIRED",
                        "Company server requires desktop " + updateRequired.requiredVersion());
                Platform.runLater(() -> org.example.update.UpdateDialogs.offerRequiredClientUpdateAtStartup(
                        stage, updateRequired.requiredVersion()));
                return;
            }
            DesktopLog.error("Main", "SERVER_START_FAILED", "Spring services could not start", exception);
            String startupMessage = ConfigManager.isSharedClient()
                    ? "Could not connect to the company server.\n\n" + exception.getMessage()
                            + "\n\nThe Shared Client does not start or replace the remote company server."
                    : BrandingService.applicationName() + " services could not start automatically.\n\n" + exception.getMessage()
                            + "\n\nServer log: " + RuntimeBootstrapper.serverLogPath();
            Platform.runLater(() -> showStartupFailureWithWorkspaceRecovery(
                    stage,
                    BrandingService.applicationName() + " startup failed",
                    startupMessage));
            return;
        }
        RuntimeApiClient.RuntimeStatus verifiedRuntime = startupRuntime;
        Platform.runLater(() -> {
            Runnable continueStartup = () -> completeStartupTransition(stage, restoreResult, recoveryFiles);
            if (ConfigManager.isSharedClient()
                    && DeploymentConnectionService.isCompatibleClientUpdateAvailable(verifiedRuntime)) {
                org.example.update.UpdateDialogs.offerCompatibleClientUpdate(
                        stage,
                        verifiedRuntime.version(),
                        DeploymentConnectionService.effectiveMinimumSupportedDesktopVersion(verifiedRuntime),
                        continueStartup);
            } else {
                continueStartup.run();
            }
        });
    }

    private void completeStartupTransition(Stage stage, BackupManager.RestoreResult restoreResult,
                                           LocalRecoveryManager.FileApplyResult recoveryFiles) {
        // Splash is non-interactive. Still guard the transition so a late startup callback
        // can never replace an already authenticated application shell.
        if (SessionService.current() == null) SceneManager.showLogin();
        finishStartup(stage);
        if (restoreResult.attempted() && !restoreResult.applied()) {
            new OwnedAlert(Alert.AlertType.ERROR,
                    restoreResult.message() + "\n\nThe ERP will continue using the preserved database.").show();
        } else if (restoreResult.applied()) {
            String safety = restoreResult.safetyBackup() == null
                    ? "No previous database existed."
                    : "Safety backup: " + restoreResult.safetyBackup();
            if (recoveryFiles.applied()) {
                org.example.util.ToastManager.success(stage, "LOCAL recovery completed",
                        "The company-server database and business files were restored to this LOCAL workspace. " + safety);
            } else {
                org.example.util.ToastManager.success(stage, "Database restore completed",
                        "The staged database restore was applied successfully. " + safety);
            }
        } else if (recoveryFiles.applied()) {
            org.example.util.ToastManager.success(stage, "LOCAL recovery files completed",
                    "The staged company-server business files were applied successfully.");
        }
    }

    /** SetupWizardController has created the workspace and bootstrapped company/admin data through the Spring API. */
    private void showStartupFailureWithWorkspaceRecovery(Stage stage, String header, String message) {
        ButtonType exit = new ButtonType("Exit", ButtonBar.ButtonData.CANCEL_CLOSE);
        if (ConfigManager.isSharedClient()) {
            ButtonType retry = new ButtonType("Retry", ButtonBar.ButtonData.OK_DONE);
            ButtonType configure = new ButtonType("Configure Server", ButtonBar.ButtonData.OTHER);
            ButtonType recover = new ButtonType("Recover from Package", ButtonBar.ButtonData.OTHER);
            Alert alert = new OwnedAlert(Alert.AlertType.ERROR,
                    message + "\n\nThis PC remains in Shared Client mode. DSE ERP will not silently switch to an older LOCAL database.",
                    retry, configure, recover, exit);
            alert.setHeaderText(header);
            ButtonType choice = alert.showAndWait().orElse(exit);
            if (choice == retry) initializeConfiguredApplication(stage);
            else if (choice == configure) {
                if (!configureSharedClientConnectionAtStartup(stage))
                    showStartupFailureWithWorkspaceRecovery(stage, header, message);
            } else if (choice == recover) {
                if (!prepareOfflineLocalRecovery(stage)) showStartupFailureWithWorkspaceRecovery(stage, header, message);
            } else Platform.exit();
            return;
        }

        ButtonType existing = new ButtonType("Select Existing Workspace", ButtonBar.ButtonData.OTHER);
        Alert alert = new OwnedAlert(Alert.AlertType.ERROR, message, existing, exit);
        alert.setHeaderText(header);
        ButtonType choice = alert.showAndWait().orElse(exit);
        if (choice == existing) SceneManager.showSetupWizard(() -> completeFirstRun(stage));
        else Platform.exit();
    }


    /** Repairs only the managed Shared Client endpoint when startup cannot reach its company server.
     * The previous LOCAL workspace and all server data remain untouched. */
    private boolean configureSharedClientConnectionAtStartup(Stage stage) {
        OwnedDialog<ButtonType> dialog = new OwnedDialog<>();
        dialog.setTitle("Company Server Connection");
        DialogPresentation.configureWorkspace(dialog, "notification");

        ComboBox<String> environment = new ComboBox<>(FXCollections.observableArrayList("UAT", "PROD"));
        String currentEnvironment = ConfigManager.getConfiguredDeploymentEnvironment();
        environment.setValue("PROD".equalsIgnoreCase(currentEnvironment) ? "PROD" : "UAT");
        environment.setMaxWidth(Double.MAX_VALUE);

        TextField serverUrl = new TextField(ConfigManager.getConfiguredServerUrl());
        serverUrl.setPromptText("https://api-uat.company.example");
        serverUrl.setMaxWidth(Double.MAX_VALUE);

        Label status = new Label("Enter the company server address, then verify it before saving.");
        status.setWrapText(true);
        status.getStyleClass().add("settings-help-text");

        Button test = new Button("Test Connection");
        test.getStyleClass().addAll("settings-action-button", "settings-secondary-button");

        VBox content = new VBox(8,
                new Label("Environment"), environment,
                new Label("Company server URL"), serverUrl,
                test, status);
        content.setFillWidth(true);
        content.setMinWidth(560);

        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType save = new ButtonType("Save & Restart", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(cancel, save);
        dialog.getDialogPane().setContent(content);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.setDisable(true);
        AtomicReference<String> validatedUrl = new AtomicReference<>();
        AtomicReference<String> validatedEnvironment = new AtomicReference<>();

        Runnable invalidate = () -> {
            validatedUrl.set(null);
            validatedEnvironment.set(null);
            saveButton.setDisable(true);
        };
        serverUrl.textProperty().addListener((o, a, b) -> invalidate.run());
        environment.valueProperty().addListener((o, a, b) -> invalidate.run());

        test.setOnAction(event -> {
            String candidate = serverUrl.getText();
            String expectedEnvironment = environment.getValue();
            test.setDisable(true);
            saveButton.setDisable(true);
            status.setText("Testing company server...");
            Thread worker = new Thread(() -> {
                try {
                    var runtime = DeploymentConnectionService.test(candidate, expectedEnvironment);
                    String normalized = DeploymentConnectionService.normalize(candidate);
                    Platform.runLater(() -> {
                        validatedUrl.set(normalized);
                        validatedEnvironment.set(expectedEnvironment);
                        status.setText("Connected: " + runtime.service() + " " + runtime.version()
                                + " • " + runtime.environment() + " • Database " + runtime.databaseName());
                        test.setDisable(false);
                        saveButton.setDisable(false);
                    });
                } catch (Exception failure) {
                    Platform.runLater(() -> {
                        invalidate.run();
                        status.setText("Connection failed: " + failure.getMessage());
                        test.setDisable(false);
                    });
                }
            }, "dse-startup-shared-server-test");
            worker.setDaemon(true);
            worker.start();
        });

        ButtonType result = dialog.showAndWait().orElse(cancel);
        if (result != save) return false;
        String normalized = validatedUrl.get();
        String selectedEnvironment = validatedEnvironment.get();
        if (normalized == null || selectedEnvironment == null) return false;

        try {
            WorkspaceManager.updateManagedSharedClientConnection(normalized, selectedEnvironment);
            ConfigManager.load();
            OwnedAlert saved = new OwnedAlert(Alert.AlertType.INFORMATION,
                    "The verified company-server connection was saved.\n\n"
                            + "This PC remains in Shared Client mode and the previous LOCAL workspace was not modified. "
                            + "DSE ERP will close now; reopen it to connect using the repaired profile.",
                    ButtonType.OK);
            saved.setHeaderText("Company server connection repaired");
            saved.showAndWait();
            Platform.exit();
            return true;
        } catch (Exception failure) {
            DesktopLog.error("Main", "SHARED_PROFILE_REPAIR_FAILED",
                    "Managed Shared Client connection could not be repaired", failure);
            OwnedAlert error = new OwnedAlert(Alert.AlertType.ERROR,
                    "The Shared Client profile could not be repaired.\n\n" + failure.getMessage(),
                    ButtonType.OK);
            error.setHeaderText("Company server connection not saved");
            error.showAndWait();
            return false;
        }
    }

    private boolean prepareOfflineLocalRecovery(Stage stage) {
        FileChooser packageChooser = new FileChooser();
        packageChooser.setTitle("Choose DSE ERP Recovery Package");
        packageChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DSE ERP recovery package (*.zip)", "*.zip"));
        File packageFile = packageChooser.showOpenDialog(stage);
        if (packageFile == null) return false;

        DirectoryChooser targetChooser = new DirectoryChooser();
        targetChooser.setTitle("Choose LOCAL Recovery Workspace");
        File targetFolder = targetChooser.showDialog(stage);
        if (targetFolder == null) return false;
        Path target = targetFolder.toPath().toAbsolutePath().normalize();
        var inspection = WorkspaceManager.inspectLocalRecoveryTarget(target);
        if (!inspection.valid()) {
            OwnedAlert warning = new OwnedAlert(Alert.AlertType.WARNING, inspection.message());
            warning.setHeaderText("LOCAL recovery target is not safe");
            warning.showAndWait();
            return false;
        }

        ButtonType recover = new ButtonType("Prepare LOCAL Recovery", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        OwnedAlert confirmation = new OwnedAlert(Alert.AlertType.WARNING,
                "Recovery package: " + packageFile.getAbsolutePath() + "\n\n"
                        + "LOCAL workspace: " + target + "\n\n"
                        + "The package checksum and contents will be validated before this PC is switched. The company server will not be modified. After preparation DSE ERP will close; on the next start the database is restored before login and business files are applied only after the database restore succeeds.",
                cancel, recover);
        confirmation.setHeaderText("Explicit offline LOCAL disaster recovery");
        if (confirmation.showAndWait().orElse(cancel) != recover) return false;

        try {
            LocalRecoveryManager.StageResult staged = LocalRecoveryManager.stageForLocal(
                    packageFile.toPath(), target, ConfigManager.getConfiguredServerUrl());
            OwnedAlert ready = new OwnedAlert(Alert.AlertType.INFORMATION,
                    "The recovery package was verified and staged successfully.\n\n"
                            + "Source: " + staged.sourceEnvironment() + " • " + staged.sourceVersion() + " • " + staged.databaseName() + "\n"
                            + "LOCAL workspace: " + staged.workspace() + "\n\n"
                            + "DSE ERP will now close. Start it again to complete the LOCAL database and business-file recovery before login.");
            ready.setHeaderText("LOCAL recovery prepared");
            ready.showAndWait();
            Platform.exit();
            return true;
        } catch (Exception failure) {
            DesktopLog.error("Main", "OFFLINE_LOCAL_RECOVERY_FAILED", "Offline LOCAL recovery package could not be staged", failure);
            OwnedAlert error = new OwnedAlert(Alert.AlertType.ERROR,
                    "The recovery package was not applied.\n\n" + failure.getMessage());
            error.setHeaderText("LOCAL recovery preparation failed");
            error.showAndWait();
            return false;
        }
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
                RuntimeApiClient.RuntimeStatus startupRuntime = RuntimeBootstrapper.ensureServerReady();
                SceneManager.updateSplashStage(4, "Verifying database, schema and migrations...");
                new org.example.api.runtime.RuntimeApiClient().status();
                SceneManager.updateSplashStage(5, "Finalizing " + BrandingService.applicationName() + "...");
                SceneManager.markSplashReady("Services ready. Opening " + BrandingService.applicationName() + "...");
                RuntimeApiClient.RuntimeStatus verifiedRuntime = startupRuntime;
                Platform.runLater(() -> {
                    Runnable continueStartup = () -> {
                        if (SessionService.current() == null) SceneManager.showLogin();
                        finishStartup(stage);
                    };
                    if (ConfigManager.isSharedClient()
                            && DeploymentConnectionService.isCompatibleClientUpdateAvailable(verifiedRuntime)) {
                        org.example.update.UpdateDialogs.offerCompatibleClientUpdate(
                                stage,
                                verifiedRuntime.version(),
                                DeploymentConnectionService.effectiveMinimumSupportedDesktopVersion(verifiedRuntime),
                                continueStartup);
                    } else {
                        continueStartup.run();
                    }
                });
            } catch (Exception exception) {
                if (exception instanceof org.example.api.runtime.DeploymentConnectionService.ClientUpdateRequiredException updateRequired) {
                    DesktopLog.info("Main", "FIRST_RUN_CLIENT_UPDATE_REQUIRED",
                            "Company server requires desktop " + updateRequired.requiredVersion());
                    Platform.runLater(() -> org.example.update.UpdateDialogs.offerRequiredClientUpdateAtStartup(
                            stage, updateRequired.requiredVersion()));
                    return;
                }
                DesktopLog.error("Main", "FIRST_RUN_START_FAILED", "Services could not start after setup", exception);
                String startupMessage = ConfigManager.isSharedClient()
                        ? "Could not connect to the company server after setup.\n\n" + exception.getMessage()
                                + "\n\nThe Shared Client does not start or replace the remote company server."
                        : BrandingService.applicationName() + " services could not start after setup.\n\n" + exception.getMessage()
                                + "\n\nServer log: " + RuntimeBootstrapper.serverLogPath();
                Platform.runLater(() -> {
                    Alert alert = new OwnedAlert(Alert.AlertType.ERROR, startupMessage);
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
