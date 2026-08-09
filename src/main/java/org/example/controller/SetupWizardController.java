package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import org.example.backup.BackupManager;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.database.DatabaseManager;
import org.example.util.IconFactory;
import org.example.util.SceneManager;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
    @FXML private Button btnBack, btnNext, btnBrowse;
    @FXML private CheckBox chkConfigureEmail;

    private final List<StackPane> steps = new java.util.ArrayList<>();
    private int index;
    private Runnable onCompleted;

    @FXML public void initialize() {
        steps.addAll(List.of(stepWorkspace, stepCompany, stepEmail, stepAdmin, stepFinish));
        txtWorkspace.setText(WorkspaceManager.getSuggestedWorkspace().toString());
        txtSmtpHost.setText("smtp.mail.yahoo.com");
        txtSmtpPort.setText("465");
        txtAdminUsername.setText("admin");
        btnBrowse.setGraphic(IconFactory.icon("folder"));
        btnBack.setGraphic(IconFactory.icon("return"));
        btnNext.setGraphic(IconFactory.icon("complete"));
        chkConfigureEmail.selectedProperty().addListener((o,a,b)->setEmailControlsEnabled(b));
        setEmailControlsEnabled(false);
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

    @FXML private void previous() { if (index > 0) showStep(index - 1); }

    @FXML private void next() {
        clearError();
        if (!validateCurrentStep()) return;
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
        btnNext.setText(index == steps.size() - 1 ? "Create Workspace & Start" : "Continue");
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
                ConfigManager.setWithoutSaving("setup.completed", "true");
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
                DatabaseManager.initialize();
                if (ConfigManager.isSqlite()) BackupManager.ensureApplicationMetadata();
                saveAdministrator();
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

    private void saveAdministrator() throws Exception {
        String sql = "UPDATE users SET username=?,password=?,full_name=?,email=?,role='ADMIN',active=1 WHERE username='admin' OR role='ADMIN'";
        try (Connection connection = DatabaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, txtAdminUsername.getText().trim());
            statement.setString(2, txtAdminPassword.getText());
            statement.setString(3, txtAdminName.getText().trim());
            statement.setString(4, txtAdminEmail.getText().trim());
            if (statement.executeUpdate() == 0) {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO users(username,password,full_name,email,role,active) VALUES(?,?,?,?, 'ADMIN',1)")) {
                    insert.setString(1, txtAdminUsername.getText().trim());
                    insert.setString(2, txtAdminPassword.getText());
                    insert.setString(3, txtAdminName.getText().trim());
                    insert.setString(4, txtAdminEmail.getText().trim());
                    insert.executeUpdate();
                }
            }
        }
    }

    private void setEmailControlsEnabled(boolean enabled) {
        txtSmtpEmail.setDisable(!enabled); txtSmtpPassword.setDisable(!enabled);
        txtSmtpHost.setDisable(!enabled); txtSmtpPort.setDisable(!enabled);
    }
    private boolean require(TextInputControl field, String message) { return field.getText()!=null&&!field.getText().isBlank() || fail(message, field); }
    private boolean validEmail(String value) { return value != null && EMAIL.matcher(value.trim()).matches(); }
    private boolean fail(String message, Control control) { lblError.setText(message); lblError.getStyleClass().remove("setup-progress"); lblError.getStyleClass().add("setup-error"); control.requestFocus(); return false; }
    private void clearError() { lblError.setText(""); lblError.getStyleClass().removeAll("setup-error","setup-progress"); }
    private String safe(TextInputControl control) { return control.getText() == null ? "" : control.getText().trim(); }
}
