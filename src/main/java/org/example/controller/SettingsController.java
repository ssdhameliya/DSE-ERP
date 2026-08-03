package org.example.controller;

import org.example.util.OwnedAlert;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.service.EmailService;
import org.example.service.NotificationService;
import org.example.update.UpdateDialogs;
import org.example.update.UpdateService;
import org.example.update.BuildInfo;
import org.example.util.IconFactory;

import java.io.File;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Settings entered here are persisted locally.
 *
 * The controller preserves the existing five settings sections:
 * Company, Payment, Invoice, Notifications and Email.
 */
public class SettingsController {

    @FXML private Button btnCheckUpdates;

    /* =========================================================
       COMPANY FIELDS
       ========================================================= */

    @FXML
    private TextField txtCompanyName;

    @FXML
    private TextField txtPhone;

    @FXML
    private TextField txtEmail;

    @FXML
    private TextField txtGstin;

    @FXML
    private TextField txtCompanyPan;

    @FXML
    private ComboBox<String> cmbBusinessType;

    @FXML
    private ComboBox<String> cmbIndustry;

    @FXML
    private DatePicker dpFinancialYearStart;

    /* =========================================================
       PAYMENT FIELDS
       ========================================================= */

    @FXML
    private TextField txtUpiId;

    @FXML
    private TextField txtAccountHolder;

    @FXML
    private TextField txtBankName;

    @FXML
    private TextField txtAccountNumber;

    @FXML
    private TextField txtIfsc;

    @FXML
    private TextField txtBranch;

    /* =========================================================
       INVOICE FIELDS
       ========================================================= */

    @FXML
    private TextField txtCompanyState;

    @FXML
    private TextField txtCompanyWebsite;

    @FXML
    private TextField txtCompanyTagline;

    @FXML
    private TextArea txtCompanyAddress;

    @FXML
    private TextArea txtShipAddress;

    @FXML
    private TextArea txtInvoiceTerms;

    @FXML
    private ComboBox<String> cmbCurrency;

    @FXML
    private ComboBox<String> cmbTimeZone;

    @FXML
    private ComboBox<String> cmbDateFormat;

    /* =========================================================
       EMAIL FIELDS
       ========================================================= */

    @FXML
    private TextField txtSmtpEmail;

    @FXML
    private PasswordField txtSmtpPassword;

    @FXML
    private TextField txtSmtpHost;

    @FXML
    private TextField txtSmtpPort;

    /* =========================================================
       NOTIFICATIONS
       ========================================================= */

    @FXML
    private CheckBox chkNotifications;

    /* =========================================================
       IMAGE PREVIEWS
       ========================================================= */

    @FXML
    private ImageView imgCompanyLogo;

    @FXML
    private ImageView imgSignature;

    @FXML
    private ImageView imgPaymentQr;

    @FXML
    private VBox placeholderCompanyLogo;

    @FXML
    private VBox placeholderSignature;

    @FXML
    private VBox placeholderPaymentQr;

    @FXML
    private Label lblLogoFile;

    @FXML
    private Label lblSignatureFile;

    @FXML
    private Label lblQrFile;

    /* =========================================================
       NAVIGATION
       ========================================================= */

    @FXML
    private HBox navCompany;

    @FXML
    private HBox navPayment;

    @FXML
    private HBox navInvoice;

    @FXML
    private HBox navNotifications;

    @FXML
    private HBox navEmail;

    @FXML
    private VBox panelCompany;

    @FXML
    private VBox panelPayment;

    @FXML
    private VBox panelInvoice;

    @FXML
    private VBox panelNotifications;

    @FXML
    private VBox panelEmail;

    /* =========================================================
       APPLICATION UPDATES
       ========================================================= */

    @FXML private HBox navUpdates;
    @FXML private VBox panelUpdates;
    @FXML private TextField txtGitHubOwner;
    @FXML private TextField txtGitHubRepository;
    @FXML private ComboBox<String> cmbUpdateChannel;
    @FXML private CheckBox chkUpdateAtStartup;
    @FXML private CheckBox chkDownloadInBackground;
    @FXML private Label lblCurrentVersion;
    @FXML private Label lblLatestVersion;
    @FXML private Label lblLastChecked;

    /* =========================================================
       WORKSPACE & STORAGE
       ========================================================= */
    @FXML private HBox navWorkspace;
    @FXML private VBox panelWorkspace;
    @FXML private Label lblWorkspacePath;
    @FXML private Label lblWorkspaceStatus;

    /* =========================================================
       CONFIGURATION KEYS
       ========================================================= */

    private static final String LOGO_PATH_KEY =
        "company.logoPath";

    private static final String SIGNATURE_PATH_KEY =
        "company.signaturePath";

    private static final String QR_PATH_KEY =
        "payment.qrImagePath";

    /* =========================================================
       INITIALIZATION
       ========================================================= */

    @FXML
    public void initialize() {
        if (btnCheckUpdates != null) { btnCheckUpdates.setGraphic(IconFactory.icon("update", 16)); btnCheckUpdates.getProperties().put("erp-icon-preserve", true); }

        configureChoiceFields();
        loadSettings();
        refreshAllAssetPreviews();
        refreshWorkspacePanel();

        showCompany();
    }

    private void configureChoiceFields() {

        cmbBusinessType.setItems(
            FXCollections.observableArrayList(
                "Proprietorship",
                "Partnership",
                "Private Limited Company",
                "Public Limited Company",
                "Limited Liability Partnership",
                "Trust",
                "Society",
                "Other"
            )
        );

        cmbIndustry.setItems(
            FXCollections.observableArrayList(
                "Manufacturing",
                "Trading",
                "Retail",
                "Wholesale",
                "Construction",
                "Engineering",
                "Textile",
                "Automotive",
                "Information Technology",
                "Professional Services",
                "Logistics",
                "Healthcare",
                "Food & Beverage",
                "Other"
            )
        );

        cmbCurrency.setItems(
            FXCollections.observableArrayList(
                "INR - Indian Rupee",
                "USD - US Dollar",
                "EUR - Euro",
                "GBP - British Pound",
                "AED - UAE Dirham"
            )
        );

        cmbTimeZone.setItems(
            FXCollections.observableArrayList(
                "Asia/Kolkata",
                "Asia/Dubai",
                "Europe/London",
                "America/New_York",
                "America/Los_Angeles",
                "UTC"
            )
        );

        cmbDateFormat.setItems(
            FXCollections.observableArrayList(
                "dd/MM/yyyy",
                "dd-MM-yyyy",
                "yyyy-MM-dd",
                "MM/dd/yyyy",
                "dd MMM yyyy"
            )
        );


        cmbUpdateChannel.setItems(
            FXCollections.observableArrayList("STABLE", "BETA")
        );
    }

    private void loadSettings() {

        txtCompanyName.setText(
            ConfigManager.get("company.name", "")
        );

        txtPhone.setText(
            ConfigManager.get("company.phone", "")
        );

        txtEmail.setText(
            ConfigManager.get("company.email", "")
        );

        txtGstin.setText(
            ConfigManager.get("company.gstin", "")
        );

        txtCompanyPan.setText(
            ConfigManager.get("company.pan", "")
        );

        txtCompanyAddress.setText(
            ConfigManager.get("company.address", "")
        );

        txtCompanyState.setText(
            ConfigManager.get("company.state", "")
        );

        txtCompanyWebsite.setText(
            ConfigManager.get("company.website", "")
        );

        txtCompanyTagline.setText(
            ConfigManager.get(
                "company.tagline",
                "Business Solution - Simplified"
            )
        );

        txtShipAddress.setText(
            ConfigManager.get("company.shipAddress", "")
        );

        txtInvoiceTerms.setText(
            ConfigManager.get("company.terms", "")
        );

        selectComboValue(
            cmbBusinessType,
            ConfigManager.get(
                "company.businessType",
                "Proprietorship"
            )
        );

        selectComboValue(
            cmbIndustry,
            ConfigManager.get(
                "company.industry",
                "Manufacturing"
            )
        );

        dpFinancialYearStart.setValue(
            parseDate(
                ConfigManager.get(
                    "company.financialYearStart",
                    ""
                )
            )
        );

        selectComboValue(
            cmbCurrency,
            ConfigManager.get(
                "company.currency",
                "INR - Indian Rupee"
            )
        );

        selectComboValue(
            cmbTimeZone,
            ConfigManager.get(
                "company.timeZone",
                ZoneId.systemDefault().getId()
            )
        );

        selectComboValue(
            cmbDateFormat,
            ConfigManager.get(
                "company.dateFormat",
                "dd/MM/yyyy"
            )
        );

        txtSmtpEmail.setText(
            ConfigManager.get("smtp.email", "")
        );

        txtSmtpPassword.setText(
            ConfigManager.get("smtp.appPassword", "")
        );

        txtSmtpHost.setText(
            ConfigManager.get("smtp.host", "")
        );

        txtSmtpPort.setText(
            ConfigManager.get("smtp.port", "587")
        );

        txtUpiId.setText(
            ConfigManager.get("payment.upiId", "")
        );

        txtAccountHolder.setText(
            ConfigManager.get(
                "payment.accountHolder",
                ""
            )
        );

        txtBankName.setText(
            ConfigManager.get("payment.bankName", "")
        );

        txtAccountNumber.setText(
            ConfigManager.get(
                "payment.accountNumber",
                ""
            )
        );

        txtIfsc.setText(
            ConfigManager.get("payment.ifsc", "")
        );

        txtBranch.setText(
            ConfigManager.get("payment.branch", "")
        );

        chkNotifications.setSelected(
            Boolean.parseBoolean(
                ConfigManager.get(
                    "notifications.enabled",
                    "true"
                )
            )
        );
        txtGitHubOwner.setText(ConfigManager.get("update.github.owner", UpdateService.DEFAULT_GITHUB_OWNER));
        txtGitHubRepository.setText(ConfigManager.get("update.github.repository", UpdateService.DEFAULT_GITHUB_REPOSITORY));
        selectComboValue(cmbUpdateChannel, ConfigManager.get("update.channel", "STABLE"));
        chkUpdateAtStartup.setSelected(Boolean.parseBoolean(ConfigManager.get("update.checkAtStartup", "true")));
        chkDownloadInBackground.setSelected(Boolean.parseBoolean(ConfigManager.get("update.downloadInBackground", "false")));
        lblCurrentVersion.setText(BuildInfo.version());
        lblLatestVersion.setText("Check GitHub Releases");
        lblLastChecked.setText(formatUpdateTimestamp(ConfigManager.get("update.lastChecked", "")));
    }

    private void selectComboValue(
        ComboBox<String> comboBox,
        String configuredValue
    ) {

        if (
            configuredValue == null
                || configuredValue.isBlank()
        ) {
            return;
        }

        if (
            !comboBox
                .getItems()
                .contains(configuredValue)
        ) {
            comboBox
                .getItems()
                .add(configuredValue);
        }

        comboBox.setValue(configuredValue);
    }

    private LocalDate parseDate(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    /* =========================================================
       IMAGE UPLOAD ACTIONS
       ========================================================= */

    @FXML
    private void uploadCompanyLogo() {

        selectAndStoreImage(
            LOGO_PATH_KEY,
            "company-logo",
            imgCompanyLogo,
            placeholderCompanyLogo,
            lblLogoFile
        );
    }

    @FXML
    private void removeCompanyLogo() {

        removeConfiguredAsset(
            LOGO_PATH_KEY,
            imgCompanyLogo,
            placeholderCompanyLogo,
            lblLogoFile
        );
    }

    @FXML
    private void uploadSignature() {

        selectAndStoreImage(
            SIGNATURE_PATH_KEY,
            "signature",
            imgSignature,
            placeholderSignature,
            lblSignatureFile
        );
    }

    @FXML
    private void removeSignature() {

        removeConfiguredAsset(
            SIGNATURE_PATH_KEY,
            imgSignature,
            placeholderSignature,
            lblSignatureFile
        );
    }

    @FXML
    private void uploadPaymentQr() {

        selectAndStoreImage(
            QR_PATH_KEY,
            "payment-qr",
            imgPaymentQr,
            placeholderPaymentQr,
            lblQrFile
        );
    }

    @FXML
    private void removePaymentQr() {

        removeConfiguredAsset(
            QR_PATH_KEY,
            imgPaymentQr,
            placeholderPaymentQr,
            lblQrFile
        );
    }

    private void selectAndStoreImage(
        String configKey,
        String baseName,
        ImageView imageView,
        VBox placeholder,
        Label fileLabel
    ) {

        FileChooser chooser = new FileChooser();

        chooser.setTitle("Choose Image");

        chooser
            .getExtensionFilters()
            .add(
                new FileChooser.ExtensionFilter(
                    "Image files",
                    "*.png",
                    "*.jpg",
                    "*.jpeg"
                )
            );

        File selected =
            chooser.showOpenDialog(
                txtCompanyName
                    .getScene()
                    .getWindow()
            );

        if (selected == null) {
            return;
        }

        try {

            String extension =
                getSafeExtension(
                    selected.getName()
                );

            Path assetsFolder =
                ConfigManager
                    .getConfigFolder()
                    .resolve("assets");

            Files.createDirectories(
                assetsFolder
            );

            removeOlderAssetVersions(
                assetsFolder,
                baseName
            );

            Path destination =
                assetsFolder.resolve(
                    baseName + extension
                );

            Files.copy(
                selected.toPath(),
                destination,
                StandardCopyOption.REPLACE_EXISTING
            );

            ConfigManager.set(
                configKey,
                destination
                    .toAbsolutePath()
                    .toString()
            );

            showImagePreview(
                destination,
                imageView,
                placeholder,
                fileLabel
            );

        } catch (Exception exception) {

            showError(
                "The image could not be saved: "
                    + exception.getMessage()
            );
        }
    }

    private String getSafeExtension(String fileName) {

        String lowerName =
            fileName == null
                ? ""
                : fileName.toLowerCase();

        if (lowerName.endsWith(".jpg")) {
            return ".jpg";
        }

        if (lowerName.endsWith(".jpeg")) {
            return ".jpeg";
        }

        return ".png";
    }

    private void removeOlderAssetVersions(
        Path assetsFolder,
        String baseName
    ) {

        List<String> extensions =
            List.of(
                ".png",
                ".jpg",
                ".jpeg"
            );

        for (String extension : extensions) {

            try {
                Files.deleteIfExists(
                    assetsFolder.resolve(
                        baseName + extension
                    )
                );
            } catch (Exception ignored) {
                // A failed cleanup must not stop upload.
            }
        }
    }

    private void removeConfiguredAsset(
        String configKey,
        ImageView imageView,
        VBox placeholder,
        Label fileLabel
    ) {

        String configuredPath =
            ConfigManager.get(
                configKey,
                ""
            );

        ConfigManager.set(
            configKey,
            ""
        );

        imageView.setImage(null);

        imageView.setVisible(false);
        imageView.setManaged(false);

        placeholder.setVisible(true);
        placeholder.setManaged(true);

        fileLabel.setText(
            "No image selected"
        );

        if (!configuredPath.isBlank()) {

            try {
                Files.deleteIfExists(
                    Path.of(configuredPath)
                );
            } catch (Exception ignored) {
                // Configuration removal still succeeds.
            }
        }
    }

    private void refreshAllAssetPreviews() {

        refreshAssetPreview(
            LOGO_PATH_KEY,
            imgCompanyLogo,
            placeholderCompanyLogo,
            lblLogoFile
        );

        refreshAssetPreview(
            SIGNATURE_PATH_KEY,
            imgSignature,
            placeholderSignature,
            lblSignatureFile
        );

        refreshAssetPreview(
            QR_PATH_KEY,
            imgPaymentQr,
            placeholderPaymentQr,
            lblQrFile
        );
    }

    private void refreshAssetPreview(
        String configKey,
        ImageView imageView,
        VBox placeholder,
        Label fileLabel
    ) {

        String configuredPath =
            ConfigManager.get(
                configKey,
                ""
            );

        if (configuredPath.isBlank()) {

            clearImagePreview(
                imageView,
                placeholder,
                fileLabel
            );

            return;
        }

        try {

            Path path =
                Path.of(configuredPath);

            if (!Files.isRegularFile(path)) {

                clearImagePreview(
                    imageView,
                    placeholder,
                    fileLabel
                );

                return;
            }

            showImagePreview(
                path,
                imageView,
                placeholder,
                fileLabel
            );

        } catch (Exception ignored) {

            clearImagePreview(
                imageView,
                placeholder,
                fileLabel
            );
        }
    }

    private void showImagePreview(
        Path path,
        ImageView imageView,
        VBox placeholder,
        Label fileLabel
    ) {

        Image image =
            new Image(
                path
                    .toUri()
                    .toString(),
                false
            );

        if (image.isError()) {

            clearImagePreview(
                imageView,
                placeholder,
                fileLabel
            );

            return;
        }

        imageView.setImage(image);

        imageView.setVisible(true);
        imageView.setManaged(true);

        placeholder.setVisible(false);
        placeholder.setManaged(false);

        fileLabel.setText(
            path
                .getFileName()
                .toString()
        );
    }

    private void clearImagePreview(
        ImageView imageView,
        VBox placeholder,
        Label fileLabel
    ) {

        imageView.setImage(null);

        imageView.setVisible(false);
        imageView.setManaged(false);

        placeholder.setVisible(true);
        placeholder.setManaged(true);

        fileLabel.setText(
            "No image selected"
        );
    }

    /* =========================================================
       TAB NAVIGATION
       ========================================================= */

    private void selectSection(
        HBox selectedNavigation,
        VBox selectedPanel
    ) {

        HBox[] navigationItems = {
            navCompany,
            navPayment,
            navInvoice,
            navNotifications,
            navEmail,
            navWorkspace,
            navUpdates
        };

        for (HBox item : navigationItems) {

            if (item == null) {
                continue;
            }

            item
                .getStyleClass()
                .remove(
                    "settings-navigation-item-selected"
                );
        }

        if (
            selectedNavigation != null
                && !selectedNavigation
                .getStyleClass()
                .contains(
                    "settings-navigation-item-selected"
                )
        ) {

            selectedNavigation
                .getStyleClass()
                .add(
                    "settings-navigation-item-selected"
                );
        }

        VBox[] panels = {
            panelCompany,
            panelPayment,
            panelInvoice,
            panelNotifications,
            panelEmail,
            panelWorkspace,
            panelUpdates
        };

        for (VBox panel : panels) {

            if (panel == null) {
                continue;
            }

            boolean active =
                panel == selectedPanel;

            panel.setVisible(active);
            panel.setManaged(active);
        }
    }

    @FXML
    private void showCompany() {

        selectSection(
            navCompany,
            panelCompany
        );
    }

    @FXML
    private void showPayment() {

        selectSection(
            navPayment,
            panelPayment
        );
    }

    @FXML
    private void showInvoice() {

        selectSection(
            navInvoice,
            panelInvoice
        );
    }

    @FXML
    private void showNotifications() {

        selectSection(
            navNotifications,
            panelNotifications
        );
    }

    @FXML
    private void showEmail() {

        selectSection(
            navEmail,
            panelEmail
        );
    }


    @FXML
    private void showWorkspace() {
        selectSection(navWorkspace, panelWorkspace);
        refreshWorkspacePanel();
    }

    @FXML
    private void openWorkspaceFolder() {
        try {
            Desktop.getDesktop().open(WorkspaceManager.getWorkspaceRoot().toFile());
        } catch (Exception exception) {
            showError("The workspace folder could not be opened: " + exception.getMessage());
        }
    }

    @FXML
    private void moveWorkspace() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose New DSE ERP Workspace");
        File selected = chooser.showDialog(panelWorkspace.getScene().getWindow());
        if (selected == null) return;
        try {
            WorkspaceManager.stageMove(selected.toPath());
            lblWorkspaceStatus.setText("Move scheduled. Close and reopen DSE ERP to copy and verify the workspace. The current workspace will be retained as a recovery copy.");
            lblWorkspaceStatus.getStyleClass().removeAll("workspace-status-ok", "workspace-status-warning");
            lblWorkspaceStatus.getStyleClass().add("workspace-status-warning");
            new OwnedAlert(Alert.AlertType.INFORMATION,
                    "The workspace move is scheduled for the next application start.\n\n" +
                    "DSE ERP will copy the data while SQLite is closed, verify the destination, and retain the current workspace as a safety copy.",
                    ButtonType.OK).showAndWait();
        } catch (Exception exception) {
            showError("The workspace move could not be scheduled: " + exception.getMessage());
        }
    }

    private void refreshWorkspacePanel() {
        if (lblWorkspacePath == null || lblWorkspaceStatus == null) return;
        lblWorkspacePath.setText(WorkspaceManager.getWorkspaceRoot().toString());
        boolean pending = WorkspaceManager.hasPendingMove();
        lblWorkspaceStatus.setText(pending
                ? "A workspace move is pending and will run before the database opens on the next start."
                : "Workspace is available and writable. Application updates do not replace this folder.");
        lblWorkspaceStatus.getStyleClass().removeAll("workspace-status-ok", "workspace-status-warning");
        lblWorkspaceStatus.getStyleClass().add(pending ? "workspace-status-warning" : "workspace-status-ok");
    }

    @FXML
    private void showUpdates() {
        selectSection(navUpdates, panelUpdates);
        lblCurrentVersion.setText(BuildInfo.version());
        lblLastChecked.setText(formatUpdateTimestamp(ConfigManager.get("update.lastChecked", "")));
    }

    @FXML
    private void checkForUpdates() {
        saveUpdateSettings();
        UpdateDialogs.checkForUpdates(panelUpdates.getScene().getWindow(), false);
    }

    @FXML
    private void viewUpdateHistory() {
        UpdateDialogs.showHistory(panelUpdates.getScene().getWindow());
    }

    @FXML
    private void installOfflineUpdate() {
        saveUpdateSettings();
        UpdateDialogs.showOfflineUpdate(panelUpdates.getScene().getWindow());
    }

    @FXML
    private void showSystemHealth() {
        UpdateDialogs.showSystemHealth(panelUpdates.getScene().getWindow());
    }

    /* =========================================================
       SAVE
       ========================================================= */

    @FXML
    private void save() {

        if (!saveValues()) {
            return;
        }

        if (chkNotifications.isSelected()) {

            NotificationService.add(
                "Application settings were updated."
            );
        }

        new OwnedAlert(
            Alert.AlertType.INFORMATION,
            "Settings saved successfully.",
            ButtonType.OK
        ).showAndWait();
    }

    private boolean saveValues() {

        if (!validateSettings()) {
            return false;
        }

        saveCompanyDetails();
        savePaymentDetails();
        saveInvoiceIdentity();
        saveEmailSettings();
        saveNotificationSettings();
        saveUpdateSettings();

        return true;
    }

    private void saveCompanyDetails() {

        ConfigManager.set(
            "company.name",
            txtCompanyName
                .getText()
                .trim()
        );

        ConfigManager.set(
            "company.phone",
            txtPhone
                .getText()
                .trim()
        );

        ConfigManager.set(
            "company.email",
            txtEmail
                .getText()
                .trim()
        );

        ConfigManager.set(
            "company.gstin",
            txtGstin
                .getText()
                .trim()
                .toUpperCase()
        );

        ConfigManager.set(
            "company.pan",
            txtCompanyPan
                .getText()
                .trim()
                .toUpperCase()
        );

        ConfigManager.set(
            "company.businessType",
            valueOrEmpty(cmbBusinessType)
        );

        ConfigManager.set(
            "company.industry",
            valueOrEmpty(cmbIndustry)
        );

        ConfigManager.set(
            "company.financialYearStart",
            dpFinancialYearStart.getValue() == null
                ? ""
                : dpFinancialYearStart
                .getValue()
                .toString()
        );
    }

    private void savePaymentDetails() {

        ConfigManager.set(
            "payment.upiId",
            txtUpiId
                .getText()
                .trim()
        );

        ConfigManager.set(
            "payment.accountHolder",
            txtAccountHolder
                .getText()
                .trim()
        );

        ConfigManager.set(
            "payment.bankName",
            txtBankName
                .getText()
                .trim()
        );

        ConfigManager.set(
            "payment.accountNumber",
            txtAccountNumber
                .getText()
                .trim()
        );

        ConfigManager.set(
            "payment.ifsc",
            txtIfsc
                .getText()
                .trim()
                .toUpperCase()
        );

        ConfigManager.set(
            "payment.branch",
            txtBranch
                .getText()
                .trim()
        );
    }

    private void saveInvoiceIdentity() {

        ConfigManager.set(
            "company.address",
            txtCompanyAddress
                .getText()
                .trim()
        );

        ConfigManager.set(
            "company.state",
            txtCompanyState
                .getText()
                .trim()
        );

        ConfigManager.set(
            "company.website",
            txtCompanyWebsite
                .getText()
                .trim()
        );

        ConfigManager.set(
            "company.tagline",
            txtCompanyTagline
                .getText()
                .trim()
        );

        ConfigManager.set(
            "company.shipAddress",
            txtShipAddress
                .getText()
                .trim()
        );

        ConfigManager.set(
            "company.terms",
            txtInvoiceTerms
                .getText()
                .trim()
        );

        ConfigManager.set(
            "company.currency",
            valueOrEmpty(cmbCurrency)
        );

        ConfigManager.set(
            "company.timeZone",
            valueOrEmpty(cmbTimeZone)
        );

        ConfigManager.set(
            "company.dateFormat",
            valueOrEmpty(cmbDateFormat)
        );
    }

    private void saveEmailSettings() {

        ConfigManager.set(
            "smtp.email",
            txtSmtpEmail
                .getText()
                .trim()
        );

        ConfigManager.set(
            "smtp.appPassword",
            txtSmtpPassword.getText()
        );

        ConfigManager.set(
            "smtp.host",
            txtSmtpHost
                .getText()
                .trim()
        );

        String port =
            txtSmtpPort
                .getText()
                .trim();

        ConfigManager.set(
            "smtp.port",
            port.isBlank()
                ? "587"
                : port
        );
    }

    private void saveNotificationSettings() {

        ConfigManager.set(
            "notifications.enabled",
            Boolean.toString(
                chkNotifications.isSelected()
            )
        );
    }

    private String valueOrEmpty(
        ComboBox<String> comboBox
    ) {

        return comboBox.getValue() == null
            ? ""
            : comboBox
            .getValue()
            .trim();
    }

    /* =========================================================
       EMAIL TEST
       ========================================================= */

    @FXML
    private void testEmail() {

        if (!saveValues()) {
            return;
        }

        String recipient =
            txtSmtpEmail
                .getText()
                .trim();

        if (recipient.isBlank()) {

            warn(
                "Enter the sending email address first."
            );

            showEmail();
            return;
        }

        try {

            EmailService.send(
                recipient,
                "DSE ERP email test",
                "Your DSE ERP email configuration is working correctly."
            );

            new OwnedAlert(
                Alert.AlertType.INFORMATION,
                "Test email sent successfully to "
                    + recipient
                    + ".",
                ButtonType.OK
            ).showAndWait();

        } catch (RuntimeException exception) {

            showError(
                exception.getMessage()
            );
        }
    }

    /* =========================================================
       VALIDATION
       ========================================================= */

    private boolean validateSettings() {

        if (!validatePaymentDetails()) {
            showPayment();
            return false;
        }

        if (!validateEmailSettings()) {
            showEmail();
            return false;
        }

        return true;
    }

    private boolean validatePaymentDetails() {

        String upi =
            txtUpiId
                .getText()
                .trim();

        String accountNumber =
            txtAccountNumber
                .getText()
                .trim();

        String ifsc =
            txtIfsc
                .getText()
                .trim();

        if (
            !upi.isBlank()
                && !upi.matches(
                "^[A-Za-z0-9._-]{2,}@[A-Za-z0-9.-]{2,}$"
            )
        ) {

            warn(
                "Enter a valid UPI ID, for example company@bank."
            );

            return false;
        }

        if (
            !accountNumber.isBlank()
                && !accountNumber.matches(
                "[0-9]{6,20}"
            )
        ) {

            warn(
                "Account number must contain 6 to 20 digits."
            );

            return false;
        }

        if (
            !ifsc.isBlank()
                && !ifsc.matches(
                "(?i)^[A-Z]{4}0[A-Z0-9]{6}$"
            )
        ) {

            warn(
                "Enter a valid 11-character IFSC code."
            );

            return false;
        }

        return true;
    }

    private boolean validateEmailSettings() {

        String smtpPort =
            txtSmtpPort
                .getText()
                .trim();

        if (
            !smtpPort.isBlank()
                && !smtpPort.matches("\\d{1,5}")
        ) {

            warn(
                "SMTP port must be a valid number."
            );

            return false;
        }

        if (!smtpPort.isBlank()) {

            int port =
                Integer.parseInt(smtpPort);

            if (port < 1 || port > 65535) {

                warn(
                    "SMTP port must be between 1 and 65535."
                );

                return false;
            }
        }

        return true;
    }

    private void warn(String message) {

        new OwnedAlert(
            Alert.AlertType.WARNING,
            message,
            ButtonType.OK
        ).showAndWait();
    }

    private void showError(String message) {

        new OwnedAlert(
            Alert.AlertType.ERROR,
            message == null
                ? "An unexpected error occurred."
                : message,
            ButtonType.OK
        ).showAndWait();
    }

    private void saveUpdateSettings() {
        if (txtGitHubOwner == null) return;
        ConfigManager.set("update.github.owner", txtGitHubOwner.getText().trim());
        ConfigManager.set("update.github.repository", txtGitHubRepository.getText().trim());
        ConfigManager.set("update.channel", cmbUpdateChannel.getValue() == null ? "STABLE" : cmbUpdateChannel.getValue());
        ConfigManager.set("update.checkAtStartup", String.valueOf(chkUpdateAtStartup.isSelected()));
        ConfigManager.set("update.downloadInBackground", String.valueOf(chkDownloadInBackground.isSelected()));
    }

    private String formatUpdateTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return "Never";
        try {
            return java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
                    .withZone(ZoneId.systemDefault()).format(java.time.Instant.parse(raw));
        } catch (Exception ignored) {
            return raw;
        }
    }

}
