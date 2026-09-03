package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import org.example.backup.BackupManager;
import org.example.api.runtime.ManagedPostgresRuntime;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.api.runtime.RuntimeBootstrapper;
import org.example.api.runtime.DeploymentConnectionService;
import org.example.config.DeploymentMode;
import org.example.api.setup.SetupApiClient;
import org.example.util.IconFactory;
import org.example.util.SceneManager;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** First-run onboarding and workspace selection for Windows and macOS. */
public class SetupWizardController {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @FXML private StackPane stepWorkspace, stepCompany, stepEmail, stepAdmin, stepFinish;
    @FXML private Label lblStep, lblTitle, lblDescription, lblError, lblSummary;
    @FXML private TextField txtWorkspace, txtCompanyName, txtPhone, txtCompanyEmail, txtGstin, txtAddress;
    @FXML private TextField txtSmtpEmail, txtSmtpHost, txtSmtpPort;
    @FXML private PasswordField txtSmtpPassword;
    @FXML private TextField txtAdminName, txtAdminUsername, txtAdminEmail;
    @FXML private PasswordField txtAdminPassword, txtAdminConfirm;
    @FXML private Button btnBack, btnNext, btnBrowse, btnUseExisting;
    @FXML private Button btnTestServer;
    @FXML private CheckBox chkConfigureEmail;
    @FXML private RadioButton rbLocal, rbShared;
    @FXML private TextField txtServerUrl;
    @FXML private Label lblServerStatus;
    @FXML private javafx.scene.layout.VBox sharedServerBox, localWorkspacePreview;

    private final List<StackPane> steps = new java.util.ArrayList<>();
    private int index;
    private Runnable onCompleted;
    private String validatedServerUrl;

    @FXML public void initialize() {
        steps.addAll(List.of(stepWorkspace, stepCompany, stepEmail, stepAdmin, stepFinish));
        txtWorkspace.setText((WorkspaceManager.isConfigured()
                ? WorkspaceManager.getWorkspaceRoot() : WorkspaceManager.getSuggestedWorkspace()).toString());
        txtSmtpHost.setText("smtp.mail.yahoo.com");
        txtSmtpPort.setText("465");
        txtAdminUsername.setText("admin");
        ToggleGroup deployment = new ToggleGroup();
        rbLocal.setToggleGroup(deployment); rbShared.setToggleGroup(deployment);
        rbLocal.setSelected(true);
        rbShared.selectedProperty().addListener((o,a,shared)->updateDeploymentControls());
        txtServerUrl.textProperty().addListener((o,a,b)->validatedServerUrl=null);
        btnBrowse.setGraphic(IconFactory.icon("folder"));
        if (btnUseExisting != null) btnUseExisting.setGraphic(IconFactory.icon("restore"));
        btnBack.setGraphic(IconFactory.icon("return"));
        btnNext.setGraphic(IconFactory.icon("complete"));
        chkConfigureEmail.selectedProperty().addListener((o,a,b)->setEmailControlsEnabled(b));
        setEmailControlsEnabled(false);
        updateDeploymentControls();
        showStep(0);
    }

    public void setOnCompleted(Runnable onCompleted) { this.onCompleted = onCompleted; }

    @FXML private void browseWorkspace() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose DSE ERP Workspace");
        File current = new File(txtWorkspace.getText().trim());
        if (current.isDirectory()) chooser.setInitialDirectory(current);
        File selected = chooser.showDialog(btnBrowse.getScene().getWindow());
        if (selected != null) txtWorkspace.setText(selected.getAbsolutePath());
    }


    /**
     * Permanent recovery path for upgrades, moved workspaces and lost workspace pointers.
     * This path never creates a company/admin or initializes a new database.
     */
    @FXML private void useExistingWorkspace() {
        clearError();
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Existing DSE ERP Workspace");
        try {
            File current = new File(txtWorkspace.getText() == null ? "" : txtWorkspace.getText().trim());
            if (current.isDirectory()) chooser.setInitialDirectory(current);
        } catch (Exception ignored) { }
        File selected = chooser.showDialog(btnUseExisting.getScene().getWindow());
        if (selected == null) return;
        Path root = selected.toPath().toAbsolutePath().normalize();
        var inspection = WorkspaceManager.inspectExisting(root);
        if (!inspection.valid()) {
            fail(inspection.message(), txtWorkspace);
            return;
        }
        txtWorkspace.setText(root.toString());
        btnNext.setDisable(true); btnBack.setDisable(true); btnUseExisting.setDisable(true);
        lblError.setText("Verifying the existing workspace and database. No business data will be recreated...");
        lblError.getStyleClass().remove("setup-error"); lblError.getStyleClass().add("setup-progress");
        Thread worker = new Thread(() -> {
            try {
                WorkspaceManager.configureExisting(root);
                ConfigManager.load();
                if (ConfigManager.isSharedClient()) RuntimeBootstrapper.ensureServerReady();
                else {
                    ManagedPostgresRuntime.ensureReady();
                    RuntimeBootstrapper.ensureServerReady();
                }
                if (new SetupApiClient().requiresSetup()) {
                    throw new IllegalStateException("This workspace does not contain an initialized DSE ERP company/admin database. Select another existing workspace or create a new one.");
                }
                WorkspaceManager.markSetupComplete();
                ConfigManager.load();
                Platform.runLater(() -> {
                    lblError.setText("Existing workspace verified. Opening DSE ERP...");
                    if (onCompleted != null) onCompleted.run(); else SceneManager.showLogin();
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    btnNext.setDisable(false); btnBack.setDisable(false); btnUseExisting.setDisable(false);
                    lblError.getStyleClass().remove("setup-progress"); lblError.getStyleClass().add("setup-error");
                    lblError.setText("Existing workspace could not be opened: " + safeRecoveryMessage(exception)
                            + " No company, administrator or business records were recreated.");
                });
            }
        }, "dse-existing-workspace-recovery");
        worker.setDaemon(true); worker.start();
    }

    private String safeRecoveryMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        if (message == null || message.isBlank()) return "verification failed.";
        return message;
    }

    @FXML private void previous() {
        if (isShared() && index == steps.size() - 1) showStep(0);
        else if (index > 0) showStep(index - 1);
    }

    @FXML private void next() {
        clearError();
        if (!validateCurrentStep()) return;
        if (index == 0 && isShared()) {
            prepareSummary();
            showStep(steps.size() - 1);
            return;
        }
        if (index < steps.size() - 1) {
            if (index == steps.size() - 2) prepareSummary();
            showStep(index + 1);
        } else {
            completeSetup();
        }
    }

    private void showStep(int newIndex) {
        index = newIndex;
        for (int i=0;i<steps.size();i++) {
            boolean active = i == index;
            steps.get(i).setVisible(active);
            steps.get(i).setManaged(active);
        }
        String[] titles = {"Choose your workspace", "Company information", "Email delivery", "Administrator account", "Ready to start"};
        String[] descriptions = {
                "Keep all business data together on a drive or external volume you control.",
                "These details appear on invoices, reports and customer communication.",
                "Optional: configure Yahoo or another SMTP account now, or do it later in Settings.",
                "Create the primary administrator who will manage users, roles and permissions.",
                "Review your setup. DSE ERP will create the workspace and initialize the database."
        };
        lblStep.setText("Step " + (index + 1) + " of " + steps.size());
        lblTitle.setText(titles[index]);
        lblDescription.setText(descriptions[index]);
        btnBack.setDisable(index == 0);
        btnNext.setText(index == steps.size() - 1
                ? (isShared() ? "Save & Connect" : "Create Workspace & Start") : "Continue");
    }

    private boolean validateCurrentStep() {
        return switch (index) {
            case 0 -> validateWorkspace();
            case 1 -> require(txtCompanyName, "Company name is required.");
            case 2 -> validateEmail();
            case 3 -> validateAdmin();
            default -> true;
        };
    }

    private boolean validateWorkspace() {
        if (txtWorkspace.getText() == null || txtWorkspace.getText().isBlank()) return fail("Choose a workspace folder.", txtWorkspace);
        try {
            Path path = Path.of(txtWorkspace.getText().trim()).toAbsolutePath().normalize();
            if (path.getRoot() != null && path.equals(path.getRoot())) return fail("Choose a folder inside the drive, not the drive root itself.", txtWorkspace);
            if (isShared()) {
                String normalized;
                try { normalized = DeploymentConnectionService.normalize(txtServerUrl.getText()); }
                catch (Exception exception) { return fail(exception.getMessage(), txtServerUrl); }
                if (!normalized.equals(validatedServerUrl)) return fail("Test the company server connection before continuing.", txtServerUrl);
            }
            return true;
        } catch (Exception exception) {
            return fail("The workspace path is not valid.", txtWorkspace);
        }
    }

    private boolean validateEmail() {
        if (!chkConfigureEmail.isSelected()) return true;
        if (!validEmail(txtSmtpEmail.getText())) return fail("Enter a valid sending email address.", txtSmtpEmail);
        if (txtSmtpPassword.getText() == null || txtSmtpPassword.getText().isBlank()) return fail("Email app password is required.", txtSmtpPassword);
        if (txtSmtpHost.getText() == null || txtSmtpHost.getText().isBlank()) return fail("SMTP host is required.", txtSmtpHost);
        try {
            int port = Integer.parseInt(txtSmtpPort.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (Exception exception) {
            return fail("SMTP port must be between 1 and 65535.", txtSmtpPort);
        }
        return true;
    }

    private boolean validateAdmin() {
        if (!require(txtAdminName, "Administrator name is required.")) return false;
        if (!require(txtAdminUsername, "Administrator username is required.")) return false;
        if (!validEmail(txtAdminEmail.getText())) return fail("Enter a valid administrator email address.", txtAdminEmail);
        if (txtAdminPassword.getText() == null || txtAdminPassword.getText().length() < 6) return fail("Use an administrator password with at least 6 characters.", txtAdminPassword);
        if (!txtAdminPassword.getText().equals(txtAdminConfirm.getText())) return fail("Administrator passwords do not match.", txtAdminConfirm);
        return true;
    }

    private void prepareSummary() {
        if (isShared()) {
            lblSummary.setText("Mode\nConnect to company server"
                    + "\n\nServer\n" + validatedServerUrl
                    + "\n\nLocal workspace\n" + txtWorkspace.getText().trim()
                    + "\n\nBusiness data, users and documents remain on the company server. This PC will not start local PostgreSQL or Spring services.");
            return;
        }
        String email = chkConfigureEmail.isSelected() ? txtSmtpEmail.getText().trim() : "Configure later in Settings";
        lblSummary.setText("Workspace\n" + txtWorkspace.getText().trim()
                + "\n\nCompany\n" + txtCompanyName.getText().trim()
                + "\n\nEmail delivery\n" + email
                + "\n\nAdministrator\n" + txtAdminName.getText().trim() + " (" + txtAdminUsername.getText().trim() + ")"
                + "\n\nThe original workspace is never stored inside the installed application.");
    }

    private void completeSetup() {
        btnNext.setDisable(true);
        btnBack.setDisable(true);
        lblError.setText("Creating your workspace and database...");
        lblError.getStyleClass().remove("setup-error");
        lblError.getStyleClass().add("setup-progress");
        Thread worker = new Thread(() -> {
            try {
                WorkspaceManager.configure(Path.of(txtWorkspace.getText().trim()));
                ConfigManager.load();
                if (isShared()) {
                    String server = DeploymentConnectionService.normalize(validatedServerUrl);
                    ConfigManager.setWithoutSaving("deployment.mode", DeploymentMode.SHARED_CLIENT.name());
                    ConfigManager.setWithoutSaving("server.baseUrl", server);
                    ConfigManager.setWithoutSaving("setup.completed", "true");
                    ConfigManager.save();
                    RuntimeBootstrapper.ensureServerReady();
                    Platform.runLater(() -> { if (onCompleted != null) onCompleted.run(); else SceneManager.showLogin(); });
                    return;
                }
                ConfigManager.setWithoutSaving("deployment.mode", DeploymentMode.LOCAL.name());
                ConfigManager.setWithoutSaving("server.baseUrl", "");
                ConfigManager.setWithoutSaving("setup.completed", "false");
                ConfigManager.setWithoutSaving("company.name", txtCompanyName.getText().trim());
                ConfigManager.setWithoutSaving("company.phone", safe(txtPhone));
                ConfigManager.setWithoutSaving("company.email", safe(txtCompanyEmail));
                ConfigManager.setWithoutSaving("company.gstin", safe(txtGstin));
                ConfigManager.setWithoutSaving("company.address", safe(txtAddress));
                if (chkConfigureEmail.isSelected()) {
                    ConfigManager.setWithoutSaving("smtp.email", txtSmtpEmail.getText().trim());
                    ConfigManager.setWithoutSaving("smtp.appPassword", txtSmtpPassword.getText());
                    ConfigManager.setWithoutSaving("smtp.host", txtSmtpHost.getText().trim());
                    ConfigManager.setWithoutSaving("smtp.port", txtSmtpPort.getText().trim());
                }
                ConfigManager.save();
                ManagedPostgresRuntime.ensureReady();
                RuntimeBootstrapper.ensureServerReady();
                new SetupApiClient().bootstrap(
                        txtCompanyName.getText().trim(), safe(txtPhone), safe(txtCompanyEmail), safe(txtGstin), safe(txtAddress),
                        txtAdminName.getText().trim(), txtAdminUsername.getText().trim(), txtAdminEmail.getText().trim(), txtAdminPassword.getText());
                ConfigManager.setWithoutSaving("setup.completed", "true");
                ConfigManager.save();
                Platform.runLater(() -> {
                    if (onCompleted != null) onCompleted.run(); else SceneManager.showLogin();
                });
            } catch (Exception exception) {
                exception.printStackTrace();
                Platform.runLater(() -> {
                    btnNext.setDisable(false);
                    btnBack.setDisable(false);
                    lblError.getStyleClass().remove("setup-progress");
                    lblError.getStyleClass().add("setup-error");
                    lblError.setText("Setup could not be completed: " + exception.getMessage());
                });
            }
        }, "dse-erp-first-run-setup");
        worker.setDaemon(true);
        worker.start();
    }

    private void setEmailControlsEnabled(boolean enabled) {
        txtSmtpEmail.setDisable(!enabled); txtSmtpPassword.setDisable(!enabled);
        txtSmtpHost.setDisable(!enabled); txtSmtpPort.setDisable(!enabled);
    }

    private boolean isShared() { return rbShared != null && rbShared.isSelected(); }

    private void updateDeploymentControls() {
        boolean shared = isShared();
        sharedServerBox.setVisible(shared); sharedServerBox.setManaged(shared);
        localWorkspacePreview.setVisible(!shared); localWorkspacePreview.setManaged(!shared);
        validatedServerUrl = null;
    }

    @FXML private void testServerConnection() {
        clearError();
        btnTestServer.setDisable(true);
        lblServerStatus.setText("Testing company server...");
        String candidate = txtServerUrl.getText();
        Thread worker = new Thread(() -> {
            try {
                var status = DeploymentConnectionService.test(candidate);
                String normalized = DeploymentConnectionService.normalize(candidate);
                Platform.runLater(() -> {
                    validatedServerUrl = normalized;
                    lblServerStatus.setText("Connected: " + status.service() + " " + status.version() + " • Database " + status.database());
                    btnTestServer.setDisable(false);
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    validatedServerUrl = null;
                    lblServerStatus.setText("Connection failed: " + exception.getMessage());
                    btnTestServer.setDisable(false);
                });
            }
        }, "dse-shared-server-test");
        worker.setDaemon(true); worker.start();
    }
    private boolean require(TextInputControl field, String message) { return field.getText()!=null&&!field.getText().isBlank() || fail(message, field); }
    private boolean validEmail(String value) { return value != null && EMAIL.matcher(value.trim()).matches(); }
    private boolean fail(String message, Control control) { lblError.setText(message); lblError.getStyleClass().remove("setup-progress"); lblError.getStyleClass().add("setup-error"); control.requestFocus(); return false; }
    private void clearError() { lblError.setText(""); lblError.getStyleClass().removeAll("setup-error","setup-progress"); }
    private String safe(TextInputControl control) { return control.getText() == null ? "" : control.getText().trim(); }
}
