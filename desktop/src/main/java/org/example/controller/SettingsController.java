package org.example.controller;

import org.example.config.SettingsFieldSupport;
import org.example.service.SettingsAssetPreviewLoader;
import org.example.service.WorkspaceSettingsService;

import org.example.util.BusinessClock;
import org.example.navigation.ScreenLifecycle;

import org.example.util.OwnedAlert;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.config.DeploymentMode;
import org.example.config.SettingsValidationSupport;
import org.example.api.runtime.DeploymentConnectionService;
import org.example.service.EmailService;
import org.example.service.EmailFailureMessages;
import org.example.service.DiagnosticBundleService;
import org.example.service.BrandAssetPolicy;
import org.example.service.BrandImagePresenter;
import org.example.service.SettingsAssetService;
import org.example.service.NotificationService;
import org.example.service.SessionService;
import org.example.ui.SharedApplicationFooter;
import org.example.update.UpdateDialogs;
import org.example.update.UpdateService;
import org.example.update.BuildInfo;
import org.example.util.IconFactory;
import org.example.util.PerformanceMonitor;
import org.example.shortcut.ShortcutRegistry;
import org.example.shortcut.ShortcutRegistry.Action;
import org.example.shortcut.SettingsShortcutSupport;
import org.example.util.UiTaskExecutor;
import org.example.util.UiDiagnostics;
import javafx.application.Platform;

import java.io.File;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Settings entered here are persisted locally.
 *
 * The controller preserves the existing settings persistence logic while sidebar
 * navigation chooses the active panel, including user-defined keyboard shortcuts.
 */
public class SettingsController implements ScreenLifecycle {
    public enum Section {
        COMPANY, PAYMENT, INVOICE, NOTIFICATIONS, EMAIL, SECURITY, WORKSPACE, SHORTCUTS, UPDATES
    }

    private static volatile Section requestedSection = Section.COMPANY;
    private boolean batchingSettingsSave;
    private long assetPreviewRevision;
    private boolean rootInitialized;
    private boolean fragmentLoading;
    private final EnumMap<Section, VBox> loadedPanels = new EnumMap<>(Section.class);
    private final EnumMap<Action, String> shortcutDraftValues = new EnumMap<>(Action.class);
    private final EnumMap<Action, ShortcutRegistry.Scope> shortcutDraftScopes = new EnumMap<>(Action.class);
    private Action selectedShortcutAction;
    private boolean shortcutUiLoading;
    private String shortcutCategoryFilter = "Application Actions";

    public static void requestSection(Section section) {
        requestedSection = section == null ? Section.COMPANY : section;
    }


    @FXML private StackPane panelHost;
    @FXML private ScrollPane panelScroll;

    @FXML private Button btnCheckUpdates,btnTestEmail,btnSaveSettings;
    @FXML private VBox panelSecurity;
    @FXML private TextField txtSessionTimeoutMinutes, txtSessionWarningMinutes;
    @FXML private ComboBox<String> cmbMfaPolicy;
    @FXML private CheckBox chkUiDiagnostics;
    @FXML private StackPane securityHeaderIcon;

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

    @FXML private TextField txtApplicationName;
    @FXML private TextField txtApplicationTagline;
    @FXML private TextField txtApplicationStartingText;

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

    @FXML
    private TextField txtBankMatchRoundingTolerance;

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
    @FXML private CheckBox chkNotifySales;
    @FXML private CheckBox chkNotifyPurchases;
    @FXML private CheckBox chkNotifyQuotations;
    @FXML private CheckBox chkNotifyReturns;
    @FXML private CheckBox chkNotifyPayments;
    @FXML private CheckBox chkNotifyInventory;
    @FXML private CheckBox chkNotifyReminders;
    @FXML private CheckBox chkNotifyCommunication;
    @FXML private CheckBox chkNotifySystem;

    /* =========================================================
       IMAGE PREVIEWS
       ========================================================= */

    @FXML
    private ImageView imgCompanyLogo;

    @FXML private ImageView imgApplicationBrand;
    @FXML private ImageView imgApplicationMark;
    @FXML private StackPane applicationBrandPreview;
    @FXML private StackPane applicationMarkPreview;
    @FXML private StackPane companyLogoPreview;
    @FXML private StackPane signaturePreview;
    @FXML private StackPane paymentQrPreview;

    @FXML
    private ImageView imgSignature;

    @FXML
    private ImageView imgPaymentQr;

    @FXML
    private VBox placeholderCompanyLogo;

    @FXML private VBox placeholderApplicationBrand;
    @FXML private VBox placeholderApplicationMark;

    @FXML
    private VBox placeholderSignature;

    @FXML
    private VBox placeholderPaymentQr;

    @FXML
    private Label lblLogoFile;

    @FXML private Label lblApplicationBrandFile;
    @FXML private Label lblApplicationMarkFile;

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
    @FXML private VBox deploymentSection;
    @FXML private ComboBox<String> cmbDeploymentMode;
    @FXML private TextField txtCompanyServerUrl;
    @FXML private Label lblCompanyServerStatus;
    @FXML private Button btnTestCompanyServer;
    @FXML private VBox storageRetentionSection;
    @FXML private TextField txtLogRetentionDays;
    @FXML private TextField txtReportRetentionDays;
    @FXML private TextField txtExportRetentionDays;
    @FXML private TextField txtDiagnosticRetentionDays;
    @FXML private TextField txtImportResultRetentionDays;
    @FXML private TextField txtTempRetentionDays;
    @FXML private CheckBox chkCompressOldLogs;
    @FXML private Label lblStorageDocuments;
    @FXML private Label lblStorageAttachments;
    @FXML private Label lblStorageReportsExports;
    @FXML private Label lblStorageLogsTemp;
    @FXML private Label lblStorageTotal;
    @FXML private Label lblLastCleanup;
    private String validatedCompanyServerUrl;

    /* =========================================================
       KEYBOARD SHORTCUTS
       ========================================================= */
    @FXML private VBox panelShortcuts;
    @FXML private ListView<Action> lstShortcutActions;
    @FXML private TextField txtShortcutSearch;
    @FXML private TextField txtShortcutKeys;
    @FXML private ComboBox<String> cmbShortcutScope;
    @FXML private ComboBox<Action> cmbShortcutAction;
    @FXML private TextArea txtShortcutDescription;
    @FXML private ToggleButton chkShortcutActive;
    @FXML private CheckBox chkShortcutAllowTextInput;
    @FXML private CheckBox chkShortcutRequireSelection;
    @FXML private VBox shortcutDrawer;
    @FXML private VBox shortcutConflictBox;
    @FXML private Button btnShortcutApplication;
    @FXML private Button btnShortcutQuickCreate;
    @FXML private Button btnShortcutNavigation;
    @FXML private StackPane shortcutHeaderIcon;
    @FXML private StackPane shortcutDrawerIcon;
    @FXML private StackPane shortcutKpiTotalIcon;
    @FXML private StackPane shortcutKpiCustomIcon;
    @FXML private StackPane shortcutKpiConflictIcon;
    @FXML private StackPane shortcutKpiCategoryIcon;
    @FXML private Label lblShortcutValidation;
    @FXML private Label lblShortcutTotal;
    @FXML private Label lblShortcutCustom;
    @FXML private Label lblShortcutConflicts;
    @FXML private Label lblShortcutCategories;
    @FXML private Label lblShortcutDrawerTitle;
    @FXML private Label lblShortcutConflict;
    @FXML private Label lblShortcutScopeHint;
    @FXML private HBox settingsPageHeader;

    /* =========================================================
       CONFIGURATION KEYS
       ========================================================= */

    private static final String LOGO_PATH_KEY =
        "company.logoPath";

    private static final String APPLICATION_BRAND_PATH_KEY =
        "application.brandImagePath";

    private static final String APPLICATION_MARK_PATH_KEY =
        "application.markImagePath";

    private static final String SIGNATURE_PATH_KEY =
        "company.signaturePath";

    private static final String QR_PATH_KEY =
        "payment.qrImagePath";

    /* =========================================================
       INITIALIZATION
       ========================================================= */

    @FXML
    public void initialize() {
        // Every lazily loaded fragment injects into this same controller and
        // invokes initialize(). Only the root Settings.fxml performs startup.
        if (fragmentLoading || rootInitialized) return;
        rootInitialized = true;
        if(btnTestEmail!=null) org.example.util.UiActionIcons.apply(btnTestEmail,"email","Test Email");
        if(btnSaveSettings!=null) org.example.util.UiActionIcons.apply(btnSaveSettings,"save","Save Settings");
        showRequestedSection();
    }

    private VBox ensureSectionLoaded(Section section) {
        VBox cached = loadedPanels.get(section);
        if (cached != null) return cached;
        String file = switch (section) {
            case COMPANY -> "CompanySettingsPanel.fxml";
            case PAYMENT -> "PaymentSettingsPanel.fxml";
            case INVOICE -> "InvoiceSettingsPanel.fxml";
            case NOTIFICATIONS -> "NotificationsSettingsPanel.fxml";
            case EMAIL -> "EmailSettingsPanel.fxml";
            case SECURITY -> "SecuritySettingsPanel.fxml";
            case WORKSPACE -> "WorkspaceSettingsPanel.fxml";
            case SHORTCUTS -> "ShortcutsSettingsPanel.fxml";
            case UPDATES -> "UpdatesSettingsPanel.fxml";
        };
        try {
            FXMLLoader loader = new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/settings/" + file));
            loader.setController(this);
            fragmentLoading = true;
            VBox panel = loader.load();
            org.example.util.ProfessionalUiEnhancer.enhance(panel);
            loadedPanels.put(section, panel);
            initializeLoadedSection(section);
            return panel;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load Settings section " + section + ": " + exception.getMessage(), exception);
        } finally {
            fragmentLoading = false;
        }
    }

    private void initializeLoadedSection(Section section) {
        switch (section) {
            case COMPANY -> {
                cmbBusinessType.setItems(FXCollections.observableArrayList("Proprietorship","Partnership","Private Limited Company","Public Limited Company","Limited Liability Partnership","Trust","Society","Other"));
                cmbIndustry.setItems(FXCollections.observableArrayList("Manufacturing","Trading","Retail","Wholesale","Construction","Engineering","Textile","Automotive","Information Technology","Professional Services","Logistics","Healthcare","Food & Beverage","Other"));
                txtCompanyName.setText(ConfigManager.get("company.name", ""));
                txtPhone.setText(ConfigManager.get("company.phone", ""));
                txtEmail.setText(ConfigManager.get("company.email", ""));
                txtGstin.setText(ConfigManager.get("company.gstin", ""));
                txtCompanyPan.setText(ConfigManager.get("company.pan", ""));
                txtApplicationName.setText(ConfigManager.get("application.displayName", "DSE ERP"));
                txtApplicationTagline.setText(ConfigManager.get("application.tagline", "Business Management Suite"));
                txtApplicationStartingText.setText(ConfigManager.get("application.startingText", "Starting DSE ERP..."));
                selectComboValue(cmbBusinessType, ConfigManager.get("company.businessType", "Proprietorship"));
                selectComboValue(cmbIndustry, ConfigManager.get("company.industry", "Manufacturing"));
                dpFinancialYearStart.setValue(parseDate(ConfigManager.get("company.financialYearStart", "")));
                BrandImagePresenter.applicationBannerPreview(imgApplicationBrand, applicationBrandPreview);
                BrandImagePresenter.contain(imgApplicationMark, applicationMarkPreview);
                refreshAllAssetPreviewsAsync();
            }
            case PAYMENT -> {
                txtUpiId.setText(ConfigManager.get("payment.upiId", ""));
                txtAccountHolder.setText(ConfigManager.get("payment.accountHolder", ""));
                txtBankName.setText(ConfigManager.get("payment.bankName", ""));
                txtAccountNumber.setText(ConfigManager.get("payment.accountNumber", ""));
                txtIfsc.setText(ConfigManager.get("payment.ifsc", ""));
                txtBranch.setText(ConfigManager.get("payment.branch", ""));
                txtBankMatchRoundingTolerance.setText(ConfigManager.get("payment.bankMatchRoundingTolerance", "1.00"));
                BrandImagePresenter.contain(imgPaymentQr, paymentQrPreview);
                refreshAllAssetPreviewsAsync();
            }
            case INVOICE -> {
                cmbCurrency.setItems(FXCollections.observableArrayList("INR - Indian Rupee","USD - US Dollar","EUR - Euro","GBP - British Pound","AED - UAE Dirham"));
                cmbTimeZone.setItems(FXCollections.observableArrayList("Asia/Kolkata","Asia/Dubai","Europe/London","America/New_York","America/Los_Angeles","UTC"));
                cmbDateFormat.setItems(FXCollections.observableArrayList("dd/MM/yyyy","dd-MM-yyyy","yyyy-MM-dd","MM/dd/yyyy","dd MMM yyyy"));
                txtCompanyAddress.setText(ConfigManager.get("company.address", ""));
                txtCompanyState.setText(ConfigManager.get("company.state", ""));
                txtCompanyWebsite.setText(ConfigManager.get("company.website", ""));
                txtCompanyTagline.setText(ConfigManager.get("company.tagline", "Business Solution - Simplified"));
                txtShipAddress.setText(ConfigManager.get("company.shipAddress", ""));
                txtInvoiceTerms.setText(ConfigManager.get("company.terms", ""));
                selectComboValue(cmbCurrency, ConfigManager.get("company.currency", "INR - Indian Rupee"));
                selectComboValue(cmbTimeZone, ConfigManager.get("company.timeZone", BusinessClock.zone().getId()));
                selectComboValue(cmbDateFormat, ConfigManager.get("company.dateFormat", "dd/MM/yyyy"));
                // Logo and signature controls both belong to InvoiceSettingsPanel.fxml.
                // Configure them only after that fragment has injected its controls.
                BrandImagePresenter.contain(imgCompanyLogo, companyLogoPreview);
                BrandImagePresenter.contain(imgSignature, signaturePreview);
                refreshAllAssetPreviewsAsync();
            }
            case NOTIFICATIONS -> {
                chkNotifications.setSelected(Boolean.parseBoolean(ConfigManager.get("notifications.enabled", "true")));
                loadNotificationCategory(chkNotifySales, "sales"); loadNotificationCategory(chkNotifyPurchases, "purchases");
                loadNotificationCategory(chkNotifyQuotations, "quotations"); loadNotificationCategory(chkNotifyReturns, "returns");
                loadNotificationCategory(chkNotifyPayments, "payments"); loadNotificationCategory(chkNotifyInventory, "inventory");
                loadNotificationCategory(chkNotifyReminders, "reminders"); loadNotificationCategory(chkNotifyCommunication, "communication");
                loadNotificationCategory(chkNotifySystem, "system");
                chkNotifications.selectedProperty().addListener((obs, oldValue, enabled) -> setNotificationCategoriesDisabled(!enabled));
                setNotificationCategoriesDisabled(!chkNotifications.isSelected());
            }
            case EMAIL -> {
                if (ConfigManager.isSharedClient()) {
                    if (SessionService.isAdmin()) {
                        var smtp = new org.example.api.authority.BusinessEmailClient().settings();
                        txtSmtpEmail.setText(smtp.email());
                        txtSmtpPassword.clear();
                        txtSmtpPassword.setPromptText(smtp.passwordConfigured()?"Configured — leave blank to keep current password":"Enter email app password");
                        txtSmtpHost.setText(smtp.host());
                        txtSmtpPort.setText(smtp.port() == null ? "587" : Integer.toString(smtp.port()));
                    } else {
                        txtSmtpEmail.clear(); txtSmtpPassword.clear(); txtSmtpHost.clear(); txtSmtpPort.setText("587");
                        txtSmtpEmail.setDisable(true); txtSmtpPassword.setDisable(true); txtSmtpHost.setDisable(true); txtSmtpPort.setDisable(true);
                    }
                } else {
                    txtSmtpEmail.setText(ConfigManager.getSmtpEmail());
                    txtSmtpPassword.setText(ConfigManager.getSmtpPassword());
                    txtSmtpHost.setText(ConfigManager.getSmtpHost());
                    txtSmtpPort.setText(ConfigManager.getSmtpPort());
                }
            }
            case SECURITY -> {
                var support = new org.example.api.support.SupportApiClient();
                txtSessionTimeoutMinutes.setText(support.setting("security.session.timeout.minutes", "10"));
                txtSessionWarningMinutes.setText(support.setting("security.session.warning.minutes", "2"));
                if (cmbMfaPolicy != null) {
                    cmbMfaPolicy.getItems().setAll("Required", "Admin Controlled", "Disabled");
                    String policy=support.setting("security.auth.mfa.policy", "REQUIRED").trim().toUpperCase(Locale.ROOT);
                    cmbMfaPolicy.setValue("ADMIN_CONTROLLED".equals(policy)?"Admin Controlled":"DISABLED".equals(policy)?"Disabled":"Required");
                }
                if (chkUiDiagnostics != null) chkUiDiagnostics.setSelected(UiDiagnostics.isEnabled());
                if (securityHeaderIcon != null) securityHeaderIcon.getChildren().setAll(IconFactory.icon("security", 22));
                boolean editable = SessionService.isAdmin();
                txtSessionTimeoutMinutes.setDisable(!editable); txtSessionWarningMinutes.setDisable(!editable); if(cmbMfaPolicy!=null)cmbMfaPolicy.setDisable(!editable);
            }
            case WORKSPACE -> {
                refreshWorkspacePanel();
                boolean admin = SessionService.isAdmin();
                if (deploymentSection != null) { deploymentSection.setVisible(admin); deploymentSection.setManaged(admin); }
                if (storageRetentionSection != null) storageRetentionSection.setDisable(!admin);
                loadStorageRetentionSettings();
                refreshStorageUsage();
                if (admin) {
                    cmbDeploymentMode.setItems(FXCollections.observableArrayList("This PC only", "Connect to company server"));
                    cmbDeploymentMode.getSelectionModel().select(ConfigManager.isSharedClient() ? 1 : 0);
                    txtCompanyServerUrl.setText(ConfigManager.getConfiguredServerUrl());
                    txtCompanyServerUrl.textProperty().addListener((o,a,b)->validatedCompanyServerUrl=null);
                    cmbDeploymentMode.valueProperty().addListener((o,a,b)->updateDeploymentSettingsControls());
                    updateDeploymentSettingsControls();
                }
            }
            case SHORTCUTS -> initializeShortcutSettings();
            case UPDATES -> {
                cmbUpdateChannel.setItems(FXCollections.observableArrayList("STABLE", "BETA"));
                txtGitHubOwner.setText(ConfigManager.get("update.github.owner", UpdateService.DEFAULT_GITHUB_OWNER));
                txtGitHubRepository.setText(ConfigManager.get("update.github.repository", UpdateService.DEFAULT_GITHUB_REPOSITORY));
                selectComboValue(cmbUpdateChannel, ConfigManager.get("update.channel", "STABLE"));
                chkUpdateAtStartup.setSelected(Boolean.parseBoolean(ConfigManager.get("update.checkAtStartup", "true")));
                chkDownloadInBackground.setSelected(Boolean.parseBoolean(ConfigManager.get("update.downloadInBackground", "false")));
                lblCurrentVersion.setText(BuildInfo.version());
                lblLatestVersion.setText("Check GitHub Releases");
                lblLastChecked.setText(formatUpdateTimestamp(ConfigManager.get("update.lastChecked", "")));
                if (btnCheckUpdates != null) { btnCheckUpdates.setGraphic(IconFactory.icon("update", 16)); btnCheckUpdates.getProperties().put("erp-icon-preserve", true); }
            }
        }
        PerformanceMonitor.event("controller-phase", "settings-section-loaded | " + section);
    }

    @Override
    public void onScreenShown(boolean reusedFromCache) {
        showRequestedSection();
    }

    private void showRequestedSection() {
        switch (requestedSection) {
            case PAYMENT -> showPayment();
            case INVOICE -> showInvoice();
            case NOTIFICATIONS -> showNotifications();
            case EMAIL -> showEmail();
            case SECURITY -> showSecurity();
            case WORKSPACE -> showWorkspace();
            case SHORTCUTS -> showShortcuts();
            case UPDATES -> showUpdates();
            case COMPANY -> showCompany();
        }
    }
    private void selectComboValue(ComboBox<String> comboBox, String configuredValue) {
        if (configuredValue == null || configuredValue.isBlank()) return;
        if (!comboBox.getItems().contains(configuredValue)) comboBox.getItems().add(configuredValue);
        comboBox.setValue(configuredValue);
    }
    private LocalDate parseDate(String value) { return SettingsFieldSupport.parseDate(value); }



    /* =========================================================
       IMAGE UPLOAD ACTIONS
       ========================================================= */

    @FXML
    private void uploadApplicationBrand() {
        selectAndStoreImage(APPLICATION_BRAND_PATH_KEY, "application-brand", imgApplicationBrand, placeholderApplicationBrand, lblApplicationBrandFile, BrandAssetPolicy.Role.APPLICATION_BANNER);
    }

    @FXML
    private void previewApplicationBrand() { previewConfiguredAsset(APPLICATION_BRAND_PATH_KEY, "application branding image"); }

    @FXML
    private void removeApplicationBrand() {
        removeConfiguredAsset(APPLICATION_BRAND_PATH_KEY, imgApplicationBrand, placeholderApplicationBrand, lblApplicationBrandFile);
    }

    @FXML
    private void uploadApplicationMark() {
        selectAndStoreImage(APPLICATION_MARK_PATH_KEY, "application-mark", imgApplicationMark, placeholderApplicationMark, lblApplicationMarkFile, BrandAssetPolicy.Role.APPLICATION_MARK);
    }

    @FXML
    private void previewApplicationMark() { previewConfiguredAsset(APPLICATION_MARK_PATH_KEY, "application mark"); }

    @FXML
    private void removeApplicationMark() {
        removeConfiguredAsset(APPLICATION_MARK_PATH_KEY, imgApplicationMark, placeholderApplicationMark, lblApplicationMarkFile);
    }

    @FXML
    private void uploadCompanyLogo() {

        selectAndStoreImage(
            LOGO_PATH_KEY,
            "company-logo",
            imgCompanyLogo,
            placeholderCompanyLogo,
            lblLogoFile,
            BrandAssetPolicy.Role.COMPANY_LOGO
        );
    }

    @FXML
    private void previewCompanyLogo() { previewConfiguredAsset(LOGO_PATH_KEY, "company logo"); }

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
            lblSignatureFile,
            BrandAssetPolicy.Role.SIGNATURE
        );
    }

    @FXML
    private void previewSignature() { previewConfiguredAsset(SIGNATURE_PATH_KEY, "authorized signature"); }

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
            lblQrFile,
            BrandAssetPolicy.Role.PAYMENT_QR
        );
    }

    @FXML
    private void previewPaymentQr() { previewConfiguredAsset(QR_PATH_KEY, "payment QR image"); }

    @FXML
    private void removePaymentQr() {

        removeConfiguredAsset(
            QR_PATH_KEY,
            imgPaymentQr,
            placeholderPaymentQr,
            lblQrFile
        );
    }
    private void previewConfiguredAsset(String configKey, String label) {
        try {
            SettingsAssetService.openConfigured(configKey, label);
        } catch (Exception error) {
            showError(safeMessage(error));
        }
    }


    private void selectAndStoreImage(
        String configKey,
        String baseName,
        ImageView imageView,
        VBox placeholder,
        Label fileLabel,
        BrandAssetPolicy.Role role
    ) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image files", "*.png", "*.jpg", "*.jpeg"));

        Window owner = resolveAssetChooserOwner(imageView);
        File selected = chooser.showOpenDialog(owner);
        if (selected == null) return;

        Path selectedPath = selected.toPath().toAbsolutePath().normalize();
        UiTaskExecutor.cancel("settings-asset-store-" + configKey);
        String taskKey = "settings-asset-inspect-" + configKey;
        UiTaskExecutor.submitLatest(
            taskKey,
            () -> SettingsAssetService.inspect(selectedPath, role),
            selection -> {
                if (selection.inspection().hasWarnings()
                        && !confirmImageWarnings(role, selection.inspection())) {
                    return;
                }
                storeSelectedImageAsync(configKey, baseName, imageView, placeholder,
                        fileLabel, role, selection);
            },
            error -> showError("The image could not be inspected: " + safeMessage(error))
        );
    }

    private void storeSelectedImageAsync(
        String configKey,
        String baseName,
        ImageView imageView,
        VBox placeholder,
        Label fileLabel,
        BrandAssetPolicy.Role role,
        SettingsAssetService.Selection selection
    ) {
        String previousConfiguredPath = ConfigManager.get(configKey, "");
        String taskKey = "settings-asset-store-" + configKey;
        UiTaskExecutor.submitAction(
            taskKey,
            () -> SettingsAssetService.store(configKey, baseName, role, selection, previousConfiguredPath),
            result -> {
                ++assetPreviewRevision; // invalidate an older queued preview refresh
                applyImagePreview(result.path(), result.previewImage(), result.inspection(),
                        imageView, placeholder, fileLabel);
            },
            error -> showError("The image could not be saved: " + safeMessage(error))
        );
    }
    private boolean confirmImageWarnings(BrandAssetPolicy.Role role, BrandAssetPolicy.Inspection inspection) {
        StringBuilder message = new StringBuilder();
        message.append("Image: ").append(inspection.dimensions())
                .append(" • ").append(inspection.ratioLabel()).append("\n\n");
        for (String warning : inspection.warnings()) message.append("• ").append(warning).append("\n");
        message.append("\n").append(BrandAssetPolicy.recommendation(role))
                .append("\n\nUse this image anyway?");
        return new OwnedAlert(
                Alert.AlertType.CONFIRMATION,
                message.toString(),
                ButtonType.YES,
                ButtonType.NO
        ).showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }
    private void removeConfiguredAsset(
        String configKey,
        ImageView imageView,
        VBox placeholder,
        Label fileLabel
    ) {

        UiTaskExecutor.cancel("settings-asset-inspect-" + configKey);
        UiTaskExecutor.cancel("settings-asset-store-" + configKey);

        String configuredPath =
            ConfigManager.get(
                configKey,
                ""
            );

        putSetting(
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

        SettingsAssetService.deleteConfiguredFile(configuredPath);
    }

    private void refreshAllAssetPreviewsAsync() {
        long revision = ++assetPreviewRevision;
        List<AssetPreviewRequest> requests = new ArrayList<>();
        if (imgApplicationBrand != null && placeholderApplicationBrand != null && lblApplicationBrandFile != null)
            requests.add(new AssetPreviewRequest(APPLICATION_BRAND_PATH_KEY, imgApplicationBrand, placeholderApplicationBrand, lblApplicationBrandFile, BrandAssetPolicy.Role.APPLICATION_BANNER));
        if (imgApplicationMark != null && placeholderApplicationMark != null && lblApplicationMarkFile != null)
            requests.add(new AssetPreviewRequest(APPLICATION_MARK_PATH_KEY, imgApplicationMark, placeholderApplicationMark, lblApplicationMarkFile, BrandAssetPolicy.Role.APPLICATION_MARK));
        if (imgCompanyLogo != null && placeholderCompanyLogo != null && lblLogoFile != null)
            requests.add(new AssetPreviewRequest(LOGO_PATH_KEY, imgCompanyLogo, placeholderCompanyLogo, lblLogoFile, BrandAssetPolicy.Role.COMPANY_LOGO));
        if (imgSignature != null && placeholderSignature != null && lblSignatureFile != null)
            requests.add(new AssetPreviewRequest(SIGNATURE_PATH_KEY, imgSignature, placeholderSignature, lblSignatureFile, BrandAssetPolicy.Role.SIGNATURE));
        if (imgPaymentQr != null && placeholderPaymentQr != null && lblQrFile != null)
            requests.add(new AssetPreviewRequest(QR_PATH_KEY, imgPaymentQr, placeholderPaymentQr, lblQrFile, BrandAssetPolicy.Role.PAYMENT_QR));
        if (requests.isEmpty()) return;
        UiTaskExecutor.submitLatest(
            "settings-asset-previews",
            () -> loadAssetPreviews(requests),
            results -> {
                if (revision == assetPreviewRevision) applyAssetPreviews(results);
            },
            error -> PerformanceMonitor.event("background-work-failed", "settings-asset-previews | " + safeMessage(error))
        );
    }

    private List<AssetPreviewResult> loadAssetPreviews(List<AssetPreviewRequest> requests) {
        Map<String, AssetPreviewRequest> byKey = new LinkedHashMap<>();
        List<SettingsAssetPreviewLoader.Request> work = new ArrayList<>();
        for (AssetPreviewRequest request : requests) {
            byKey.put(request.configKey(), request);
            work.add(new SettingsAssetPreviewLoader.Request(request.configKey(), request.role()));
        }
        List<AssetPreviewResult> results = new ArrayList<>();
        for (SettingsAssetPreviewLoader.Result loaded : SettingsAssetPreviewLoader.load(work)) {
            AssetPreviewRequest request = byKey.get(loaded.request().configKey());
            SettingsAssetService.Preview preview = loaded.preview();
            results.add(new AssetPreviewResult(request, preview.image(), preview.path(), preview.inspection()));
        }
        return results;
    }


    private void applyAssetPreviews(List<AssetPreviewResult> results) {
        if (results == null) return;
        for (AssetPreviewResult result : results) {
            AssetPreviewRequest request = result.request();
            if (result.image() == null || result.path() == null || result.inspection() == null) {
                clearImagePreview(request.imageView(), request.placeholder(), request.fileLabel());
                continue;
            }
            request.imageView().setImage(result.image());
            request.imageView().setVisible(true);
            request.imageView().setManaged(true);
            request.placeholder().setVisible(false);
            request.placeholder().setManaged(false);
            request.fileLabel().setText(result.path().getFileName() + " • " + result.inspection().dimensions());
        }
    }
    private Window resolveAssetChooserOwner(ImageView imageView) {
        if (imageView != null && imageView.getScene() != null) {
            return imageView.getScene().getWindow();
        }
        if (panelHost != null && panelHost.getScene() != null) {
            return panelHost.getScene().getWindow();
        }
        return null;
    }
    private void applyImagePreview(
        Path path,
        Image image,
        BrandAssetPolicy.Inspection inspection,
        ImageView imageView,
        VBox placeholder,
        Label fileLabel
    ) {
        imageView.setImage(image);
        imageView.setVisible(true);
        imageView.setManaged(true);
        placeholder.setVisible(false);
        placeholder.setManaged(false);
        fileLabel.setText(path.getFileName() + " • " + inspection.dimensions());
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "unknown error";
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.toString() : message;
    }
private record AssetPreviewRequest(
        String configKey,
        ImageView imageView,
        VBox placeholder,
        Label fileLabel,
        BrandAssetPolicy.Role role
    ) { }

    private record AssetPreviewResult(
        AssetPreviewRequest request,
        Image image,
        Path path,
        BrandAssetPolicy.Inspection inspection
    ) { }
    private void clearImagePreview(ImageView imageView, VBox placeholder, Label fileLabel) {
        imageView.setImage(null);
        imageView.setVisible(false); imageView.setManaged(false);
        placeholder.setVisible(true); placeholder.setManaged(true);
        fileLabel.setText("No image selected");
    }


    /* =========================================================
       TAB NAVIGATION
       ========================================================= */
    private void selectSection(HBox selectedNavigation, VBox selectedPanel) {
        HBox[] navigationItems = {navCompany, navPayment, navInvoice, navNotifications, navEmail, navWorkspace, navUpdates};
        for (HBox item : navigationItems) if (item != null) item.getStyleClass().remove("settings-navigation-item-selected");
        if (selectedNavigation != null && !selectedNavigation.getStyleClass().contains("settings-navigation-item-selected")) {
            selectedNavigation.getStyleClass().add("settings-navigation-item-selected");
        }
        if (selectedPanel == null || panelHost == null) return;
        selectedPanel.setVisible(true); selectedPanel.setManaged(true);
        if (panelHost.getChildren().size() != 1 || panelHost.getChildren().getFirst() != selectedPanel) panelHost.getChildren().setAll(selectedPanel);
        boolean shortcutMode = selectedPanel == panelShortcuts;
        if (settingsPageHeader != null) { settingsPageHeader.setVisible(!shortcutMode); settingsPageHeader.setManaged(!shortcutMode); }
        if (panelScroll != null) {
            panelScroll.setVbarPolicy(shortcutMode ? ScrollPane.ScrollBarPolicy.NEVER : ScrollPane.ScrollBarPolicy.AS_NEEDED);
            Platform.runLater(() -> panelScroll.setVvalue(0.0));
        }
    }
    @FXML private void showCompany() { selectSection(navCompany, ensureSectionLoaded(Section.COMPANY)); }
    @FXML private void showPayment() { selectSection(navPayment, ensureSectionLoaded(Section.PAYMENT)); }
    @FXML private void showInvoice() { selectSection(navInvoice, ensureSectionLoaded(Section.INVOICE)); }
    @FXML private void showNotifications() { selectSection(navNotifications, ensureSectionLoaded(Section.NOTIFICATIONS)); }
    @FXML private void showEmail() { selectSection(navEmail, ensureSectionLoaded(Section.EMAIL)); }
    @FXML private void showSecurity() { selectSection(null, ensureSectionLoaded(Section.SECURITY)); }



    @FXML
    private void showWorkspace() {
        selectSection(navWorkspace, ensureSectionLoaded(Section.WORKSPACE));
        refreshWorkspacePanel();
    }

    @FXML
    private void openWorkspaceFolder() {
        try {
            WorkspaceSettingsService.openWorkspaceFolder();
        } catch (Exception exception) {
            showError("The workspace folder could not be opened: " + exception.getMessage());
        }
    }

    @FXML
    private void openDocumentsFolder() {
        try { WorkspaceSettingsService.openDocumentsFolder(); }
        catch (Exception exception) { showError("The Documents folder could not be opened: " + exception.getMessage()); }
    }

    @FXML
    private void openReportsFolder() {
        try { WorkspaceSettingsService.openReportsFolder(); }
        catch (Exception exception) { showError("The Reports folder could not be opened: " + exception.getMessage()); }
    }

    @FXML
    private void openLogsFolder() {
        try { WorkspaceSettingsService.openLogsFolder(); }
        catch (Exception exception) { showError("The Logs folder could not be opened: " + exception.getMessage()); }
    }

    @FXML
    private void refreshStorageUsage() {
        if (lblStorageTotal == null) return;
        lblStorageTotal.setText("Total managed storage: Loading…");
        UiTaskExecutor.submitLatest("settings-storage-status",
                WorkspaceSettingsService::storageStatus,
                this::applyStorageStatus,
                error -> {
                    lblStorageTotal.setText("Total managed storage: unavailable");
                    if (lblLastCleanup != null) lblLastCleanup.setText("Storage status unavailable: " + safeMessage(error));
                });
    }

    @FXML
    private void previewStorageCleanup() {
        if (!SessionService.isAdmin()) { warn("Storage cleanup can be previewed only by an administrator."); return; }
        UiTaskExecutor.submitAction("settings-storage-cleanup-preview",
                WorkspaceSettingsService::previewCleanup,
                result -> {
                    OwnedAlert alert = new OwnedAlert(Alert.AlertType.INFORMATION,
                            "Cleanup preview\n\nFiles eligible for deletion: " + result.filesDeleted()
                                    + "\nLog files eligible for compression: " + result.filesCompressed()
                                    + "\nPotential space recovery: " + formatBytes(result.bytesReclaimed())
                                    + "\n\nDocuments, attachments, backups and database files are excluded.",
                            ButtonType.OK);
                    alert.setHeaderText("Safe storage cleanup preview");
                    alert.showAndWait();
                },
                error -> showError("Cleanup preview failed: " + safeMessage(error)));
    }

    @FXML
    private void cleanStorageNow() {
        if (!SessionService.isAdmin()) { warn("Storage cleanup can be run only by an administrator."); return; }
        UiTaskExecutor.submitAction("settings-storage-cleanup-confirm-preview",
                WorkspaceSettingsService::previewCleanup,
                preview -> {
                    ButtonType clean = new ButtonType("Clean Now", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
                    OwnedAlert confirm = new OwnedAlert(Alert.AlertType.CONFIRMATION,
                            "The current retention policy makes " + preview.filesDeleted() + " files eligible for deletion"
                                    + (preview.filesCompressed() > 0 ? " and " + preview.filesCompressed() + " old logs eligible for compression" : "")
                                    + ".\n\nEstimated space recovery: " + formatBytes(preview.bytesReclaimed())
                                    + "\n\nInvoices, attachments, payment proofs, backups and database records will NOT be touched.",
                            ButtonType.CANCEL, clean);
                    confirm.setHeaderText("Run safe workspace cleanup?");
                    if (confirm.showAndWait().orElse(ButtonType.CANCEL) != clean) return;
                    UiTaskExecutor.submitAction("settings-storage-cleanup-run",
                            WorkspaceSettingsService::cleanNow,
                            result -> {
                                org.example.util.ToastManager.success(panelWorkspace, "Storage cleanup completed", result.summary());
                                refreshStorageUsage();
                            },
                            error -> showError("Storage cleanup failed: " + safeMessage(error)));
                },
                error -> showError("Cleanup preview failed: " + safeMessage(error)));
    }

    @FXML
    private void exportDiagnostics() {
        try {
            Path bundle = WorkspaceSettingsService.exportDiagnostics();
            new OwnedAlert(Alert.AlertType.INFORMATION,
                    "Diagnostic package created successfully.\n\n" + bundle +
                    "\n\nIt contains version/runtime information and recent logs only; database and business documents are not included.",
                    ButtonType.OK).showAndWait();
        } catch (Exception exception) {
            showError("The diagnostic package could not be created: " + exception.getMessage());
        }
    }

    @FXML
    private void selectExistingWorkspace() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Existing DSE ERP Workspace");
        try {
            File current = WorkspaceManager.getWorkspaceRoot().toFile();
            if (current.isDirectory()) chooser.setInitialDirectory(current);
        } catch (Exception ignored) { }
        File selected = chooser.showDialog(panelWorkspace.getScene().getWindow());
        if (selected == null) return;

        Path chosen = selected.toPath().toAbsolutePath().normalize();
        Path current = WorkspaceManager.getWorkspaceRoot().toAbsolutePath().normalize();
        if (chosen.equals(current)) {
            org.example.util.ToastManager.info(panelWorkspace, "Workspace unchanged",
                    "The selected folder is already the active DSE ERP workspace.");
            return;
        }

        WorkspaceManager.ExistingWorkspaceInspection inspection = WorkspaceSettingsService.inspectExisting(chosen);
        if (!inspection.valid()) {
            showError(inspection.message());
            return;
        }

        ButtonType switchWorkspace = new ButtonType("Switch Workspace", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        OwnedAlert confirmation = new OwnedAlert(Alert.AlertType.CONFIRMATION,
                "Current workspace:\n" + current +
                "\n\nSelected existing workspace:\n" + inspection.root() +
                "\n\nDSE ERP will validate and save this existing workspace without copying or overwriting its business data. " +
                "The application will then close so every database connection, service and screen can reopen cleanly against the selected workspace.",
                ButtonType.CANCEL, switchWorkspace);
        confirmation.setHeaderText("Switch to existing workspace?");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != switchWorkspace) return;

        try {
            WorkspaceSettingsService.configureExisting(inspection.root());
            lblWorkspacePath.setText(inspection.root().toString());
            lblWorkspaceStatus.setText("Existing workspace selected. Reopen DSE ERP to connect using this workspace.");
            lblWorkspaceStatus.getStyleClass().removeAll("workspace-status-ok", "workspace-status-warning");
            lblWorkspaceStatus.getStyleClass().add("workspace-status-warning");
            OwnedAlert done = new OwnedAlert(Alert.AlertType.INFORMATION,
                    "The existing workspace has been selected successfully.\n\nDSE ERP will close now. Reopen the application to start with:\n" + inspection.root(),
                    ButtonType.OK);
            done.setHeaderText("Workspace selected");
            done.showAndWait();
            Platform.exit();
        } catch (Exception exception) {
            showError("The existing workspace could not be selected: " + exception.getMessage());
        }
    }

    @FXML
    private void moveWorkspace() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose New DSE ERP Workspace");
        File selected = chooser.showDialog(panelWorkspace.getScene().getWindow());
        if (selected == null) return;
        try {
            WorkspaceSettingsService.stageMove(selected.toPath());
            lblWorkspaceStatus.setText("Move scheduled. Close and reopen DSE ERP to copy and verify the workspace. The current workspace will be retained as a recovery copy.");
            lblWorkspaceStatus.getStyleClass().removeAll("workspace-status-ok", "workspace-status-warning");
            lblWorkspaceStatus.getStyleClass().add("workspace-status-warning");
            new OwnedAlert(Alert.AlertType.INFORMATION,
                    "The workspace move is scheduled for the next application start.\n\n" +
                    "DSE ERP will copy the workspace while managed services are stopped, verify the destination, and retain the current workspace as a safety copy.",
                    ButtonType.OK).showAndWait();
        } catch (Exception exception) {
            showError("The workspace move could not be scheduled: " + exception.getMessage());
        }
    }

    private void refreshWorkspacePanel() {
        if (lblWorkspacePath == null || lblWorkspaceStatus == null) return;
        WorkspaceSettingsService.Status workspace = WorkspaceSettingsService.status();
        lblWorkspacePath.setText(workspace.root().toString());
        boolean pending = workspace.pendingMove();
        lblWorkspaceStatus.setText(pending
                ? "A workspace move is pending and will run before the database opens on the next start."
                : "Workspace is available and writable. Application updates do not replace this folder.");
        lblWorkspaceStatus.getStyleClass().removeAll("workspace-status-ok", "workspace-status-warning");
        lblWorkspaceStatus.getStyleClass().add(pending ? "workspace-status-warning" : "workspace-status-ok");
    }

    private void loadStorageRetentionSettings() {
        if (txtLogRetentionDays == null) return;
        try {
            var support = new org.example.api.support.SupportApiClient();
            txtLogRetentionDays.setText(support.setting("storage.logs.retentionDays", "30"));
            txtReportRetentionDays.setText(support.setting("storage.reports.retentionDays", "365"));
            txtExportRetentionDays.setText(support.setting("storage.exports.retentionDays", "90"));
            txtDiagnosticRetentionDays.setText(support.setting("storage.diagnostics.retentionDays", "30"));
            txtImportResultRetentionDays.setText(support.setting("storage.importResults.retentionDays", "90"));
            txtTempRetentionDays.setText(support.setting("storage.temp.retentionDays", "7"));
            chkCompressOldLogs.setSelected(Boolean.parseBoolean(support.setting("storage.logs.compress", "true")));
        } catch (Exception error) {
            if (lblLastCleanup != null) lblLastCleanup.setText("Retention policy could not be loaded: " + safeMessage(error));
        }
    }

    private void saveStorageRetentionSettings() {
        if (txtLogRetentionDays == null || !SessionService.isAdmin()) return;
        int logs = retentionDays(txtLogRetentionDays, "Logs", 1, 3650);
        int reports = retentionDays(txtReportRetentionDays, "Reports", 1, 3650);
        int exports = retentionDays(txtExportRetentionDays, "Exports", 1, 3650);
        int diagnostics = retentionDays(txtDiagnosticRetentionDays, "Diagnostic ZIPs", 1, 3650);
        int importResults = retentionDays(txtImportResultRetentionDays, "Import results", 1, 3650);
        int temp = retentionDays(txtTempRetentionDays, "Temporary files", 1, 365);
        var support = new org.example.api.support.SupportApiClient();
        support.setSetting("storage.logs.retentionDays", Integer.toString(logs));
        support.setSetting("storage.reports.retentionDays", Integer.toString(reports));
        support.setSetting("storage.exports.retentionDays", Integer.toString(exports));
        support.setSetting("storage.diagnostics.retentionDays", Integer.toString(diagnostics));
        support.setSetting("storage.importResults.retentionDays", Integer.toString(importResults));
        support.setSetting("storage.temp.retentionDays", Integer.toString(temp));
        support.setSetting("storage.logs.compress", Boolean.toString(chkCompressOldLogs != null && chkCompressOldLogs.isSelected()));
    }

    private static int retentionDays(TextField field, String label, int min, int max) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (Exception error) {
            throw new IllegalArgumentException(label + " retention must be between " + min + " and " + max + " days.");
        }
    }

    private void applyStorageStatus(org.example.api.storage.StorageApiClient.Status status) {
        if (status == null) return;
        if (lblStorageDocuments != null) lblStorageDocuments.setText(formatBytes(status.documentsBytes()));
        if (lblStorageAttachments != null) lblStorageAttachments.setText(formatBytes(status.attachmentsBytes()));
        if (lblStorageReportsExports != null) lblStorageReportsExports.setText(formatBytes(status.reportsBytes() + status.exportsBytes()));
        if (lblStorageLogsTemp != null) lblStorageLogsTemp.setText(formatBytes(status.logsBytes() + status.tempBytes()));
        if (lblStorageTotal != null) lblStorageTotal.setText("Total managed storage: " + formatBytes(status.totalManagedBytes()));
        if (lblLastCleanup != null) {
            String when = status.lastCleanupAt() == null || status.lastCleanupAt().isBlank() ? "Never run" : status.lastCleanupAt();
            lblLastCleanup.setText("Last cleanup: " + when + " • " + (status.lastCleanupSummary() == null ? "" : status.lastCleanupSummary()));
        }
    }

    private static String formatBytes(long bytes) {
        double value = Math.max(0, bytes);
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) { value /= 1024.0; unit++; }
        return unit == 0 ? String.format(Locale.ROOT, "%.0f %s", value, units[unit])
                : String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    @FXML
    private void showShortcuts() {
        selectSection(null, ensureSectionLoaded(Section.SHORTCUTS));
        refreshShortcutValidation();
    }

    @FXML
    private void resetAllShortcuts() {
        ensureSectionLoaded(Section.SHORTCUTS);
        for (Action action : SettingsShortcutSupport.managerActions()) {
            shortcutDraftValues.put(action, action.defaultBinding());
            shortcutDraftScopes.put(action, action.scope());
            ShortcutRegistry.saveOptions(action, action.scope(), false,
                    action == Action.EDIT_CURRENT || action == Action.OPEN_SELECTED || action == Action.DELETE_SELECTED);
        }
        ShortcutRegistry.saveActions(shortcutManagerDraft(), shortcutManagerScopeDraft(), SettingsShortcutSupport.managerActions());
        refreshShortcutWorkspace();
        if (selectedShortcutAction != null) openShortcutEditor(selectedShortcutAction);
        org.example.util.ToastManager.success(panelHost, "Shortcuts reset", "All keyboard shortcuts were restored to product defaults.");
    }

    @FXML
    private void showUpdates() {
        selectSection(navUpdates, ensureSectionLoaded(Section.UPDATES));
        lblCurrentVersion.setText(BuildInfo.version());
        lblLastChecked.setText(formatUpdateTimestamp(ConfigManager.get("update.lastChecked", "")));
    }

    @FXML
    private void checkForUpdates() {
        org.example.service.PermissionService.require("APPLICATION_UPDATES.CHECK", "check for application updates");
        saveUpdateSettings();
        UpdateDialogs.checkForUpdates(panelUpdates.getScene().getWindow(), false);
    }

    @FXML
    private void showWhatsNew() {
        UpdateDialogs.showWhatsNew(panelUpdates.getScene().getWindow());
    }

    @FXML
    private void viewUpdateHistory() {
        UpdateDialogs.showHistory(panelUpdates.getScene().getWindow());
    }

    @FXML
    private void installOfflineUpdate() {
        org.example.service.PermissionService.require("APPLICATION_UPDATES.INSTALL", "install an application update");
        saveUpdateSettings();
        UpdateDialogs.showOfflineUpdate(panelUpdates.getScene().getWindow());
    }

    @FXML
    private void showSystemHealth() {
        UpdateDialogs.showSystemHealth(panelUpdates.getScene().getWindow());
    }

    private void putSetting(String key, String value) {
        if (batchingSettingsSave) ConfigManager.setWithoutSaving(key, value);
        else ConfigManager.set(key, value);
    }

    private void initializeShortcutSettings() {
        if (panelShortcuts == null || txtShortcutSearch == null || lstShortcutActions == null || cmbShortcutAction == null) return;

        shortcutDraftValues.clear();
        shortcutDraftScopes.clear();
        for (Action action : ShortcutRegistry.actions()) {
            shortcutDraftValues.put(action, ShortcutRegistry.configuredBinding(action));
            shortcutDraftScopes.put(action, ShortcutRegistry.configuredScope(action));
        }

        installShortcutIcons();
        configureShortcutActionConverter();
        configureShortcutList();

        shortcutUiLoading = true;
        cmbShortcutAction.setItems(FXCollections.observableArrayList(SettingsShortcutSupport.managerActions()));
        cmbShortcutScope.setItems(FXCollections.observableArrayList(
                java.util.Arrays.stream(ShortcutRegistry.Scope.values()).map(ShortcutRegistry.Scope::label).toList()));
        shortcutUiLoading = false;

        if (!Boolean.TRUE.equals(panelShortcuts.getProperties().get("dse.shortcut-manager.listeners"))) {
            panelShortcuts.getProperties().put("dse.shortcut-manager.listeners", Boolean.TRUE);
            txtShortcutSearch.textProperty().addListener((obs, oldValue, value) -> refreshShortcutWorkspace());
            lstShortcutActions.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, value) -> {
                if (!shortcutUiLoading && value != null && value != selectedShortcutAction) openShortcutEditor(value);
            });
            cmbShortcutAction.valueProperty().addListener((obs, oldValue, value) -> {
                if (!shortcutUiLoading && value != null && value != selectedShortcutAction) {
                    shortcutCategoryFilter = SettingsShortcutSupport.category(value);
                    refreshShortcutCategoryButtons();
                    openShortcutEditor(value);
                    refreshShortcutWorkspace();
                }
            });
            cmbShortcutScope.valueProperty().addListener((obs, oldValue, value) -> {
                if (!shortcutUiLoading && selectedShortcutAction != null) {
                    ShortcutRegistry.Scope scope = SettingsShortcutSupport.scopeFromLabel(value, selectedShortcutAction.scope());
                    if (lblShortcutScopeHint != null) lblShortcutScopeHint.setText(SettingsShortcutSupport.scopeHint(scope));
                    refreshSelectedShortcutConflict();
                }
            });
            chkShortcutActive.selectedProperty().addListener((obs, oldValue, active) -> {
                chkShortcutActive.setAlignment(active ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
                if (!shortcutUiLoading) refreshSelectedShortcutConflict();
            });
            txtShortcutKeys.getProperties().put("dse.shortcut-capture", Boolean.TRUE);
            txtShortcutKeys.setOnMouseClicked(event -> focusShortcutCapture());
            txtShortcutKeys.setOnKeyPressed(this::captureShortcutDraft);
        }

        if (selectedShortcutAction == null || !SettingsShortcutSupport.managerActions().contains(selectedShortcutAction)) {
            selectedShortcutAction = SettingsShortcutSupport.managerActions().stream()
                    .filter(action -> "Application Actions".equals(SettingsShortcutSupport.category(action)))
                    .findFirst().orElse(SettingsShortcutSupport.managerActions().stream().findFirst().orElse(Action.SAVE_CURRENT));
        }
        shortcutCategoryFilter = SettingsShortcutSupport.category(selectedShortcutAction);
        refreshShortcutCategoryButtons();
        refreshShortcutWorkspace();
        openShortcutEditor(selectedShortcutAction);
    }

    private void installShortcutIcons() {
        setShortcutIcon(shortcutHeaderIcon, "adjust", 22);
        setShortcutIcon(shortcutDrawerIcon, "edit", 20);
        setShortcutIcon(shortcutKpiTotalIcon, "document", 20);
        setShortcutIcon(shortcutKpiCustomIcon, "edit", 20);
        setShortcutIcon(shortcutKpiConflictIcon, "warning", 20);
        setShortcutIcon(shortcutKpiCategoryIcon, "category", 20);
    }

    private void setShortcutIcon(StackPane target, String semantic, double size) {
        if (target == null) return;
        var icon = IconFactory.icon(semantic, size);
        icon.getProperties().put("erp-icon-preserve", true);
        target.getChildren().setAll(icon);
    }

    private void configureShortcutActionConverter() {
        if (cmbShortcutAction == null || cmbShortcutAction.getConverter() != null) return;
        cmbShortcutAction.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Action action) {
                return action == null ? "" : action.label();
            }
            @Override public Action fromString(String value) { return null; }
        });
    }

    private void configureShortcutList() {
        if (lstShortcutActions == null || lstShortcutActions.getCellFactory() != null) return;
        lstShortcutActions.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Action action, boolean empty) {
                super.updateItem(action, empty);
                if (empty || action == null) { setText(null); setGraphic(null); return; }

                StackPane iconBox = new StackPane();
                iconBox.getStyleClass().add("shortcut-list-icon");
                var icon = IconFactory.icon(SettingsShortcutSupport.categoryIcon(SettingsShortcutSupport.category(action)), 16);
                icon.getProperties().put("erp-icon-preserve", true);
                iconBox.getChildren().setAll(icon);

                Label name = new Label(action.label());
                name.getStyleClass().add("shortcut-list-name");
                Label category = new Label(SettingsShortcutSupport.category(action));
                category.getStyleClass().add("shortcut-list-category");
                VBox labels = new VBox(2, name, category);
                HBox.setHgrow(labels, javafx.scene.layout.Priority.ALWAYS);

                ShortcutRegistry.Scope configuredScope = shortcutDraftScopes.getOrDefault(action, ShortcutRegistry.configuredScope(action));
                Label scope = new Label(configuredScope.label());
                scope.getStyleClass().add("shortcut-scope-badge");
                String raw = shortcutDraftValues.getOrDefault(action, ShortcutRegistry.configuredBinding(action));
                Label key = new Label(SettingsShortcutSupport.display(raw));
                key.getStyleClass().add("shortcut-key-badge");
                Label status = new Label(raw == null || raw.isBlank() ? "UNASSIGNED" : "ACTIVE");
                status.getStyleClass().addAll("shortcut-status", raw == null || raw.isBlank() ? "status-unassigned" : "status-active");

                HBox row = new HBox(10, iconBox, labels, key, scope, status);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.getStyleClass().add("shortcut-list-row");
                setText(null);
                setGraphic(row);
            }
        });
    }

    private Map<Action,String> shortcutManagerDraft() {
        Map<Action,String> values = new LinkedHashMap<>();
        for (Action action : SettingsShortcutSupport.managerActions()) {
            values.put(action, shortcutDraftValues.getOrDefault(action, ShortcutRegistry.configuredBinding(action)));
        }
        return values;
    }

    private Map<Action,ShortcutRegistry.Scope> shortcutManagerScopeDraft() {
        Map<Action,ShortcutRegistry.Scope> scopes = new LinkedHashMap<>();
        for (Action action : SettingsShortcutSupport.managerActions()) {
            scopes.put(action, shortcutDraftScopes.getOrDefault(action, ShortcutRegistry.configuredScope(action)));
        }
        return scopes;
    }

    @FXML
    private void showShortcutApplicationActions() {
        setShortcutCategoryFilter("Application Actions");
    }

    @FXML
    private void showShortcutQuickCreate() {
        setShortcutCategoryFilter("Quick Create");
    }

    @FXML
    private void showShortcutNavigation() {
        setShortcutCategoryFilter("Navigation");
    }

    private void setShortcutCategoryFilter(String category) {
        shortcutCategoryFilter = category;
        refreshShortcutCategoryButtons();
        refreshShortcutWorkspace();
    }

    private void refreshShortcutCategoryButtons() {
        updateShortcutCategoryButton(btnShortcutApplication, "Application Actions".equals(shortcutCategoryFilter));
        updateShortcutCategoryButton(btnShortcutQuickCreate, "Quick Create".equals(shortcutCategoryFilter));
        updateShortcutCategoryButton(btnShortcutNavigation, "Navigation".equals(shortcutCategoryFilter));
    }

    private void updateShortcutCategoryButton(Button button, boolean selected) {
        if (button == null) return;
        button.getStyleClass().remove("shortcut-group-selected");
        if (selected) button.getStyleClass().add("shortcut-group-selected");
    }

    private void refreshShortcutWorkspace() {
        if (lstShortcutActions == null) return;
        String query = txtShortcutSearch == null || txtShortcutSearch.getText() == null
                ? "" : txtShortcutSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);

        List<Action> filtered = SettingsShortcutSupport.managerActions().stream()
                .filter(action -> shortcutCategoryFilter.equals(SettingsShortcutSupport.category(action)))
                .filter(action -> {
                    if (query.isBlank()) return true;
                    String raw = shortcutDraftValues.getOrDefault(action, ShortcutRegistry.configuredBinding(action));
                    ShortcutRegistry.Scope scope = shortcutDraftScopes.getOrDefault(action, ShortcutRegistry.configuredScope(action));
                    String haystack = (action.label() + " " + SettingsShortcutSupport.category(action) + " "
                            + SettingsShortcutSupport.display(raw) + " " + scope.label()).toLowerCase(java.util.Locale.ROOT);
                    return haystack.contains(query);
                }).toList();

        shortcutUiLoading = true;
        lstShortcutActions.setItems(FXCollections.observableArrayList(filtered));
        if (selectedShortcutAction != null && filtered.contains(selectedShortcutAction)) {
            lstShortcutActions.getSelectionModel().select(selectedShortcutAction);
        } else if (!filtered.isEmpty()) {
            selectedShortcutAction = filtered.getFirst();
            lstShortcutActions.getSelectionModel().selectFirst();
        }
        shortcutUiLoading = false;

        refreshShortcutKpis();
        refreshShortcutValidation();
        if (selectedShortcutAction != null && filtered.contains(selectedShortcutAction)) openShortcutEditor(selectedShortcutAction);
    }

    private void refreshShortcutKpis() {
        if (lblShortcutTotal == null) return;
        int total = SettingsShortcutSupport.managerActions().size();
        int custom = 0;
        for (Action action : SettingsShortcutSupport.managerActions()) {
            String current = SettingsShortcutSupport.normalize(shortcutDraftValues.get(action));
            String defaults = SettingsShortcutSupport.normalize(action.defaultBinding());
            if (!current.equalsIgnoreCase(defaults)) custom++;
        }
        List<String> conflicts = SettingsShortcutSupport.validate(shortcutManagerDraft(), shortcutManagerScopeDraft());
        lblShortcutTotal.setText(Integer.toString(total));
        if (lblShortcutCustom != null) lblShortcutCustom.setText(Integer.toString(custom));
        if (lblShortcutConflicts != null) lblShortcutConflicts.setText(Integer.toString(conflicts.size()));
        if (lblShortcutCategories != null) lblShortcutCategories.setText("3");
    }

    private void openShortcutEditor(Action action) {
        if (action == null || cmbShortcutAction == null) return;
        selectedShortcutAction = action;
        shortcutUiLoading = true;
        cmbShortcutAction.getSelectionModel().select(action);
        String raw = shortcutDraftValues.getOrDefault(action, ShortcutRegistry.configuredBinding(action));
        if (txtShortcutKeys != null) txtShortcutKeys.setText(SettingsShortcutSupport.display(raw));
        if (chkShortcutActive != null) chkShortcutActive.setSelected(raw != null && !raw.isBlank());
        ShortcutRegistry.Scope configuredScope = shortcutDraftScopes.getOrDefault(action, ShortcutRegistry.configuredScope(action));
        List<ShortcutRegistry.Scope> allowedScopes = SettingsShortcutSupport.scopesForAction(action);
        if (cmbShortcutScope != null) {
            cmbShortcutScope.setItems(FXCollections.observableArrayList(allowedScopes.stream().map(ShortcutRegistry.Scope::label).toList()));
            if (!allowedScopes.contains(configuredScope)) configuredScope = action.scope();
            cmbShortcutScope.getSelectionModel().select(configuredScope.label());
            cmbShortcutScope.setDisable(allowedScopes.size() == 1);
        }
        if (txtShortcutDescription != null) txtShortcutDescription.setText(SettingsShortcutSupport.description(action));
        if (chkShortcutAllowTextInput != null) chkShortcutAllowTextInput.setSelected(ShortcutRegistry.allowInTextInput(action));
        if (chkShortcutRequireSelection != null) chkShortcutRequireSelection.setSelected(ShortcutRegistry.requireSelection(action));
        if (lblShortcutDrawerTitle != null) lblShortcutDrawerTitle.setText(action.label());
        if (lblShortcutScopeHint != null) lblShortcutScopeHint.setText(SettingsShortcutSupport.scopeHint(configuredScope));
        shortcutUiLoading = false;
        refreshSelectedShortcutConflict();
    }

    @FXML
    private void focusShortcutCapture() {
        if (txtShortcutKeys == null) return;
        txtShortcutKeys.requestFocus();
        txtShortcutKeys.selectAll();
    }

    private void captureShortcutDraft(KeyEvent event) {
        if (selectedShortcutAction == null || txtShortcutKeys == null) return;
        if (event.getCode() == KeyCode.ESCAPE) {
            txtShortcutKeys.setText(SettingsShortcutSupport.display(shortcutDraftValues.get(selectedShortcutAction)));
            event.consume();
            return;
        }
        if ((event.getCode() == KeyCode.BACK_SPACE || event.getCode() == KeyCode.DELETE)
                && !event.isControlDown() && !event.isMetaDown() && !event.isAltDown() && !event.isShiftDown()) {
            txtShortcutKeys.clear();
            if (chkShortcutActive != null) chkShortcutActive.setSelected(false);
            refreshSelectedShortcutConflict();
            event.consume();
            return;
        }
        String captured = ShortcutRegistry.fromEvent(event);
        if (!captured.isBlank()) {
            txtShortcutKeys.setText(SettingsShortcutSupport.display(captured));
            if (chkShortcutActive != null) chkShortcutActive.setSelected(true);
            refreshSelectedShortcutConflict();
        }
        event.consume();
    }

    @FXML
    private void disableSelectedShortcut() {
        if (selectedShortcutAction == null) return;
        shortcutDraftValues.put(selectedShortcutAction, "");
        ShortcutRegistry.Scope scope = shortcutDraftScopes.getOrDefault(selectedShortcutAction, ShortcutRegistry.configuredScope(selectedShortcutAction));
        ShortcutRegistry.saveActions(Map.of(selectedShortcutAction, ""), Map.of(selectedShortcutAction, scope), List.of(selectedShortcutAction));
        refreshShortcutWorkspace();
        openShortcutEditor(selectedShortcutAction);
        org.example.util.ToastManager.success(panelHost, "Shortcut disabled", selectedShortcutAction.label() + " is disabled for this user.");
    }

    @FXML
    private void resetSelectedShortcut() {
        if (selectedShortcutAction == null) return;
        if (txtShortcutKeys != null) txtShortcutKeys.setText(SettingsShortcutSupport.display(selectedShortcutAction.defaultBinding()));
        if (chkShortcutActive != null) chkShortcutActive.setSelected(!selectedShortcutAction.defaultBinding().isBlank());
        ShortcutRegistry.Scope defaultScope = selectedShortcutAction.scope();
        if (cmbShortcutScope != null) cmbShortcutScope.getSelectionModel().select(defaultScope.label());
        if (chkShortcutAllowTextInput != null) chkShortcutAllowTextInput.setSelected(false);
        if (chkShortcutRequireSelection != null) chkShortcutRequireSelection.setSelected(
                selectedShortcutAction == Action.EDIT_CURRENT || selectedShortcutAction == Action.OPEN_SELECTED || selectedShortcutAction == Action.DELETE_SELECTED);
        refreshSelectedShortcutConflict();
    }

    @FXML
    private void saveSelectedShortcut() {
        if (selectedShortcutAction == null) return;
        String raw = chkShortcutActive != null && chkShortcutActive.isSelected()
                ? SettingsShortcutSupport.normalize(txtShortcutKeys == null ? "" : txtShortcutKeys.getText()) : "";
        ShortcutRegistry.Scope selectedScope = SettingsShortcutSupport.scopeFromLabel(
                cmbShortcutScope == null ? null : cmbShortcutScope.getValue(), selectedShortcutAction.scope());
        Map<Action,String> candidate = new LinkedHashMap<>(shortcutDraftValues);
        Map<Action,ShortcutRegistry.Scope> candidateScopes = new LinkedHashMap<>(shortcutDraftScopes);
        candidate.put(selectedShortcutAction, raw);
        candidateScopes.put(selectedShortcutAction, selectedScope);
        List<String> errors = SettingsShortcutSupport.validate(candidate, candidateScopes);
        if (!errors.isEmpty()) {
            refreshSelectedShortcutConflict();
            warn("Keyboard shortcut conflict:\n" + String.join("\n", errors));
            return;
        }
        shortcutDraftValues.put(selectedShortcutAction, raw);
        shortcutDraftScopes.put(selectedShortcutAction, selectedScope);
        ShortcutRegistry.saveActions(shortcutManagerDraft(), shortcutManagerScopeDraft(), SettingsShortcutSupport.managerActions());
        ShortcutRegistry.saveOptions(selectedShortcutAction, selectedScope,
                chkShortcutAllowTextInput != null && chkShortcutAllowTextInput.isSelected(),
                chkShortcutRequireSelection != null && chkShortcutRequireSelection.isSelected());
        ShortcutRegistry.refreshBoundLabels();
        refreshShortcutWorkspace();
        openShortcutEditor(selectedShortcutAction);
        org.example.util.ToastManager.success(panelHost, "Shortcut saved", selectedShortcutAction.label() + " shortcut updated.");
    }

    private void refreshSelectedShortcutConflict() {
        if (selectedShortcutAction == null || lblShortcutConflict == null) return;
        String raw = chkShortcutActive != null && chkShortcutActive.isSelected()
                ? SettingsShortcutSupport.normalize(txtShortcutKeys == null ? "" : txtShortcutKeys.getText()) : "";
        Map<Action,String> candidate = new LinkedHashMap<>(shortcutDraftValues);
        Map<Action,ShortcutRegistry.Scope> candidateScopes = new LinkedHashMap<>(shortcutDraftScopes);
        candidate.put(selectedShortcutAction, raw);
        candidateScopes.put(selectedShortcutAction, SettingsShortcutSupport.scopeFromLabel(
                cmbShortcutScope == null ? null : cmbShortcutScope.getValue(), selectedShortcutAction.scope()));
        List<String> errors = SettingsShortcutSupport.validate(candidate, candidateScopes);
        String relevant = errors.stream()
                .filter(error -> error.contains(selectedShortcutAction.label()) || (!raw.isBlank() && error.toLowerCase(java.util.Locale.ROOT)
                        .contains(SettingsShortcutSupport.display(raw).toLowerCase(java.util.Locale.ROOT))))
                .findFirst().orElse("");
        boolean conflict = !relevant.isBlank();
        lblShortcutConflict.setText(conflict ? relevant : "No conflicts for this shortcut.");
        if (shortcutConflictBox != null) {
            shortcutConflictBox.getStyleClass().removeAll("shortcut-conflict-ok", "shortcut-conflict-warning");
            shortcutConflictBox.getStyleClass().add(conflict ? "shortcut-conflict-warning" : "shortcut-conflict-ok");
        }
    }


    private boolean validateShortcutSettings() {
        List<String> errors = SettingsShortcutSupport.validate(shortcutManagerDraft(), shortcutManagerScopeDraft());
        if (lblShortcutValidation != null) lblShortcutValidation.setText(errors.isEmpty() ? "No shortcut conflicts detected." : errors.getFirst());
        if (!errors.isEmpty()) {
            warn("Keyboard shortcut conflict:\n" + String.join("\n", errors));
            return false;
        }
        return true;
    }

    private void refreshShortcutValidation() {
        if (lblShortcutValidation == null) return;
        List<String> errors = SettingsShortcutSupport.validate(shortcutManagerDraft(), shortcutManagerScopeDraft());
        lblShortcutValidation.setText(errors.isEmpty() ? "No shortcut conflicts detected." : errors.getFirst());
        lblShortcutValidation.getStyleClass().removeAll("shortcut-validation-ok", "shortcut-validation-warning");
        lblShortcutValidation.getStyleClass().add(errors.isEmpty() ? "shortcut-validation-ok" : "shortcut-validation-warning");
    }

    private void saveShortcutSettings() {
        Map<Action,String> draft = shortcutManagerDraft();
        Map<Action,ShortcutRegistry.Scope> scopes = shortcutManagerScopeDraft();
        List<String> errors = SettingsShortcutSupport.validate(draft, scopes);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        ShortcutRegistry.saveActions(draft, scopes, SettingsShortcutSupport.managerActions());
    }

    @FXML
    private void importShortcuts() {
        if (panelShortcuts == null || panelShortcuts.getScene() == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Shortcut Profile");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Shortcut profile (*.properties)", "*.properties"));
        File selected = chooser.showOpenDialog(panelShortcuts.getScene().getWindow());
        if (selected == null) return;
        try (var reader = Files.newBufferedReader(selected.toPath())) {
            Properties properties = new Properties();
            properties.load(reader);
            Map<Action,String> imported = new LinkedHashMap<>(shortcutDraftValues);
            Map<Action,ShortcutRegistry.Scope> importedScopes = new LinkedHashMap<>(shortcutDraftScopes);
            for (Action action : SettingsShortcutSupport.managerActions()) {
                String value = properties.getProperty(action.id());
                if (value != null) imported.put(action, SettingsShortcutSupport.normalize(value));
                String scopeValue = properties.getProperty(action.id() + ".scope");
                if (scopeValue != null) importedScopes.put(action, SettingsShortcutSupport.scopeFromLabel(scopeValue, action.scope()));
            }
            List<String> errors = SettingsShortcutSupport.validate(imported, importedScopes);
            if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
            shortcutDraftValues.clear(); shortcutDraftValues.putAll(imported);
            shortcutDraftScopes.clear(); shortcutDraftScopes.putAll(importedScopes);
            ShortcutRegistry.saveActions(imported, importedScopes, SettingsShortcutSupport.managerActions());
            for (Action action : SettingsShortcutSupport.managerActions()) {
                boolean allowText = Boolean.parseBoolean(properties.getProperty(action.id() + ".allowText", Boolean.toString(ShortcutRegistry.allowInTextInput(action))));
                boolean requireSelection = Boolean.parseBoolean(properties.getProperty(action.id() + ".requireSelection", Boolean.toString(ShortcutRegistry.requireSelection(action))));
                ShortcutRegistry.saveOptions(action, importedScopes.getOrDefault(action, action.scope()), allowText, requireSelection);
            }
            refreshShortcutWorkspace();
            if (selectedShortcutAction != null) openShortcutEditor(selectedShortcutAction);
            org.example.util.ToastManager.success(panelHost, "Shortcuts imported", "Shortcut profile imported successfully.");
        } catch (Exception exception) {
            showError("The shortcut profile could not be imported: " + exception.getMessage());
        }
    }

    @FXML
    private void exportShortcuts() {
        if (panelShortcuts == null || panelShortcuts.getScene() == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Shortcut Profile");
        chooser.setInitialFileName("dse-erp-shortcuts.properties");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Shortcut profile (*.properties)", "*.properties"));
        File selected = chooser.showSaveDialog(panelShortcuts.getScene().getWindow());
        if (selected == null) return;
        try (var writer = Files.newBufferedWriter(selected.toPath())) {
            Properties properties = new Properties();
            for (Action action : SettingsShortcutSupport.managerActions()) {
                properties.setProperty(action.id(), shortcutDraftValues.getOrDefault(action, ""));
                properties.setProperty(action.id() + ".scope", shortcutDraftScopes.getOrDefault(action, ShortcutRegistry.configuredScope(action)).name());
                properties.setProperty(action.id() + ".allowText", Boolean.toString(ShortcutRegistry.allowInTextInput(action)));
                properties.setProperty(action.id() + ".requireSelection", Boolean.toString(ShortcutRegistry.requireSelection(action)));
            }
            properties.store(writer, "DSE ERP keyboard shortcut profile");
            org.example.util.ToastManager.success(panelHost, "Shortcuts exported", "Shortcut profile exported successfully.");
        } catch (Exception exception) {
            showError("The shortcut profile could not be exported: " + exception.getMessage());
        }
    }
    /* =========================================================
       SAVE
       ========================================================= */

    @FXML
    private void save() {

        if (!saveValues()) {
            return;
        }

        SharedApplicationFooter.refreshAll();

        if (chkNotifications != null && chkNotifications.isSelected()) {
            NotificationService.add("Application settings were updated.");
        }

        org.example.util.ToastManager.success(panelHost, "Settings saved", "Settings saved successfully.");
    }

    private boolean saveValues() {

        if (!validateSettings()) {
            return false;
        }

        batchingSettingsSave = true;
        try {
            if (loadedPanels.containsKey(Section.COMPANY)) saveCompanyDetails();
            if (loadedPanels.containsKey(Section.PAYMENT)) savePaymentDetails();
            if (loadedPanels.containsKey(Section.INVOICE)) saveInvoiceIdentity();
            if (loadedPanels.containsKey(Section.EMAIL)) saveEmailSettings();
            if (loadedPanels.containsKey(Section.NOTIFICATIONS)) saveNotificationSettings();
            if (loadedPanels.containsKey(Section.SECURITY)) saveSecuritySettings();
            if (loadedPanels.containsKey(Section.WORKSPACE)) {
                saveDeploymentSettings();
                saveStorageRetentionSettings();
            }
            if (loadedPanels.containsKey(Section.SHORTCUTS)) saveShortcutSettings();
            if (loadedPanels.containsKey(Section.UPDATES)) saveUpdateSettings();
            ConfigManager.save();
        } finally {
            batchingSettingsSave = false;
        }

        return true;
    }

    private void saveSecuritySettings() {
        if (chkUiDiagnostics != null) UiDiagnostics.setEnabled(chkUiDiagnostics.isSelected());
        if (txtSessionTimeoutMinutes == null || txtSessionWarningMinutes == null || !SessionService.isAdmin()) return;
        int timeout; int warning;
        try { timeout = Integer.parseInt(txtSessionTimeoutMinutes.getText().trim()); warning = Integer.parseInt(txtSessionWarningMinutes.getText().trim()); }
        catch (Exception e) { throw new IllegalArgumentException("Session timeout and warning must be whole minutes."); }
        if (timeout < 5 || timeout > 120) throw new IllegalArgumentException("Session timeout must be between 5 and 120 minutes.");
        if (warning < 1 || warning >= timeout) throw new IllegalArgumentException("Session warning must be at least 1 minute and less than the timeout.");
        var support = new org.example.api.support.SupportApiClient();
        support.setSetting("security.session.timeout.minutes", Integer.toString(timeout));
        support.setSetting("security.session.warning.minutes", Integer.toString(warning));
        if (cmbMfaPolicy != null) {
            String selected=cmbMfaPolicy.getValue();
            String policy="Admin Controlled".equals(selected)?"ADMIN_CONTROLLED":"Disabled".equals(selected)?"DISABLED":"REQUIRED";
            support.setSetting("security.auth.mfa.policy", policy);
        }
        org.example.service.SessionActivityManager.reloadPolicy();
    }

    private void saveDeploymentSettings() {
        if (!SessionService.isAdmin()) return;
        boolean shared = cmbDeploymentMode != null && cmbDeploymentMode.getSelectionModel().getSelectedIndex() == 1;
        DeploymentMode currentMode = ConfigManager.getDeploymentMode();
        if (currentMode == DeploymentMode.LOCAL && shared) {
            throw new IllegalArgumentException("Use the verified Enable Multi-User promotion workflow to move an existing local company to a company server.");
        }
        if (currentMode == DeploymentMode.SHARED_CLIENT && !shared) {
            throw new IllegalArgumentException("Create and verify a standalone company copy before disconnecting this PC from the company server.");
        }
        if (shared) {
            String normalized = DeploymentConnectionService.normalize(txtCompanyServerUrl.getText());
            if (!normalized.equals(validatedCompanyServerUrl))
                throw new IllegalArgumentException("Test the company server connection before saving shared-client mode.");
            ConfigManager.setWithoutSaving("deployment.mode", DeploymentMode.SHARED_CLIENT.name());
            ConfigManager.setWithoutSaving("server.baseUrl", normalized);
        } else {
            ConfigManager.setWithoutSaving("deployment.mode", DeploymentMode.LOCAL.name());
            ConfigManager.setWithoutSaving("server.baseUrl", "");
        }
        if (lblCompanyServerStatus != null) lblCompanyServerStatus.setText("Saved. Restart DSE ERP to apply the deployment change.");
    }

    private void updateDeploymentSettingsControls() {
        boolean shared = cmbDeploymentMode != null && cmbDeploymentMode.getSelectionModel().getSelectedIndex() == 1;
        if (txtCompanyServerUrl != null) txtCompanyServerUrl.setDisable(!shared);
        if (btnTestCompanyServer != null) btnTestCompanyServer.setDisable(!shared);
        if (!shared && lblCompanyServerStatus != null) lblCompanyServerStatus.setText("Local mode: this PC starts its own database and services.");
    }

    @FXML private void testCompanyServer() {
        if (!SessionService.isAdmin()) return;
        if (btnTestCompanyServer == null) return;
        btnTestCompanyServer.setDisable(true);
        lblCompanyServerStatus.setText("Testing company server...");
        String candidate = txtCompanyServerUrl.getText();
        Thread worker = new Thread(() -> {
            try {
                var status = DeploymentConnectionService.test(candidate);
                String normalized = DeploymentConnectionService.normalize(candidate);
                Platform.runLater(() -> {
                    validatedCompanyServerUrl = normalized;
                    lblCompanyServerStatus.setText("Connected: " + status.service() + " " + status.version() + " • Database " + status.database());
                    btnTestCompanyServer.setDisable(false);
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    validatedCompanyServerUrl = null;
                    lblCompanyServerStatus.setText("Connection failed: " + exception.getMessage());
                    btnTestCompanyServer.setDisable(false);
                });
            }
        }, "dse-settings-server-test");
        worker.setDaemon(true); worker.start();
    }
    private void saveCompanyDetails() {
        putSetting("company.name", text(txtCompanyName));
        putSetting("company.phone", text(txtPhone));
        putSetting("company.email", text(txtEmail));
        putSetting("company.gstin", upper(txtGstin));
        putSetting("company.pan", upper(txtCompanyPan));
        putSetting("company.businessType", valueOrEmpty(cmbBusinessType));
        putSetting("company.industry", valueOrEmpty(cmbIndustry));
        putSetting("company.financialYearStart", dpFinancialYearStart.getValue() == null ? "" : dpFinancialYearStart.getValue().toString());
        putSetting("application.displayName", text(txtApplicationName));
        putSetting("application.tagline", text(txtApplicationTagline));
        putSetting("application.startingText", text(txtApplicationStartingText));
    }
    private void savePaymentDetails() {
        putSetting("payment.upiId", text(txtUpiId));
        putSetting("payment.accountHolder", text(txtAccountHolder));
        putSetting("payment.bankName", text(txtBankName));
        putSetting("payment.accountNumber", text(txtAccountNumber));
        putSetting("payment.ifsc", upper(txtIfsc));
        putSetting("payment.branch", text(txtBranch));
        putSetting("payment.bankMatchRoundingTolerance", text(txtBankMatchRoundingTolerance));
    }
    private void saveInvoiceIdentity() {
        putSetting("company.address", text(txtCompanyAddress));
        putSetting("company.state", text(txtCompanyState));
        putSetting("company.website", text(txtCompanyWebsite));
        putSetting("company.tagline", text(txtCompanyTagline));
        putSetting("company.shipAddress", text(txtShipAddress));
        putSetting("company.terms", text(txtInvoiceTerms));
        putSetting("company.currency", valueOrEmpty(cmbCurrency));
        putSetting("company.timeZone", valueOrEmpty(cmbTimeZone));
        putSetting("company.dateFormat", valueOrEmpty(cmbDateFormat));
    }


    private void saveEmailSettings() {
        String email = txtSmtpEmail.getText() == null ? "" : txtSmtpEmail.getText().trim();
        String password = txtSmtpPassword.getText() == null ? "" : txtSmtpPassword.getText();
        String host = txtSmtpHost.getText() == null ? "" : txtSmtpHost.getText().trim();
        String portText = txtSmtpPort.getText() == null ? "587" : txtSmtpPort.getText().trim();
        int port = portText.isBlank() ? 587 : Integer.parseInt(portText);
        if (ConfigManager.isSharedClient()) {
            if (!SessionService.isAdmin()) return;
            new org.example.api.authority.BusinessEmailClient().saveSettings(
                    new org.example.api.authority.BusinessEmailClient.Settings(email, password, host, port, !password.isBlank()));
            return;
        }
        putSetting("smtp.email", email);
        putSetting("smtp.appPassword", password);
        putSetting("smtp.host", host);
        putSetting("smtp.port", Integer.toString(port));
    }

    private void saveNotificationSettings() {

        putSetting(
            "notifications.enabled",
            Boolean.toString(chkNotifications.isSelected())
        );
        saveNotificationCategory(chkNotifySales, "sales");
        saveNotificationCategory(chkNotifyPurchases, "purchases");
        saveNotificationCategory(chkNotifyQuotations, "quotations");
        saveNotificationCategory(chkNotifyReturns, "returns");
        saveNotificationCategory(chkNotifyPayments, "payments");
        saveNotificationCategory(chkNotifyInventory, "inventory");
        saveNotificationCategory(chkNotifyReminders, "reminders");
        saveNotificationCategory(chkNotifyCommunication, "communication");
        saveNotificationCategory(chkNotifySystem, "system");
    }

    private void loadNotificationCategory(CheckBox box, String category) {
        if (box != null) box.setSelected(Boolean.parseBoolean(ConfigManager.get("notifications.category." + category, "true")));
    }

    private void saveNotificationCategory(CheckBox box, String category) {
        if (box != null) putSetting("notifications.category." + category, Boolean.toString(box.isSelected()));
    }

    private void setNotificationCategoriesDisabled(boolean disabled) {
        CheckBox[] boxes = {chkNotifySales, chkNotifyPurchases, chkNotifyQuotations, chkNotifyReturns, chkNotifyPayments, chkNotifyInventory, chkNotifyReminders, chkNotifyCommunication, chkNotifySystem};
        for (CheckBox box : boxes) if (box != null) box.setDisable(disabled);
    }

    private String text(TextInputControl control) {
        return SettingsFieldSupport.text(control == null ? null : control.getText());
    }


    private String upper(TextInputControl control) {
        return SettingsFieldSupport.upper(control == null ? null : control.getText());
    }


    private String valueOrEmpty(ComboBox<String> comboBox) {
        return comboBox == null || comboBox.getValue() == null ? "" : comboBox.getValue().trim();
    }

    /* =========================================================
       EMAIL TEST
       ========================================================= */

    @FXML
    private void testEmail() {
        ensureSectionLoaded(Section.EMAIL);
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

            if (ConfigManager.isSharedClient()) {
                new org.example.api.authority.BusinessEmailClient().test(recipient);
            } else {
                EmailService.send(
                    recipient,
                    "DSE ERP email test",
                    "Your DSE ERP email configuration is working correctly."
                );
            }

            org.example.util.ToastManager.success(panelWorkspace, "Test email sent",
                "Test email sent successfully to " + recipient + ".");

        } catch (RuntimeException exception) {

            showError(EmailFailureMessages.forAdministrator(exception));
        }
    }

    /* =========================================================
       VALIDATION
       ========================================================= */

    private boolean validateSettings() {
        if (SessionService.isAdmin() && loadedPanels.containsKey(Section.WORKSPACE) && cmbDeploymentMode != null) {
            boolean requestedShared = cmbDeploymentMode.getSelectionModel().getSelectedIndex() == 1;
            DeploymentMode currentMode = ConfigManager.getDeploymentMode();
            if (currentMode == DeploymentMode.LOCAL && requestedShared) {
                warn("This PC already owns a local company database. Use Enable Multi-User so the database, attachments, settings and templates are migrated and verified before switching.");
                showWorkspace();
                return false;
            }
            if (currentMode == DeploymentMode.SHARED_CLIENT && !requestedShared) {
                warn("A shared company cannot be changed back to local mode with a simple toggle. Create and verify a standalone company copy first.");
                showWorkspace();
                return false;
            }
        }
        if (SessionService.isAdmin() && loadedPanels.containsKey(Section.WORKSPACE)
                && cmbDeploymentMode != null && cmbDeploymentMode.getSelectionModel().getSelectedIndex() == 1) {
            try {
                String normalized = DeploymentConnectionService.normalize(txtCompanyServerUrl.getText());
                if (!normalized.equals(validatedCompanyServerUrl)) {
                    warn("Test the company server connection before saving shared-client mode.");
                    showWorkspace();
                    return false;
                }
            } catch (IllegalArgumentException exception) {
                warn(exception.getMessage());
                showWorkspace();
                return false;
            }
        }
        if (SessionService.isAdmin() && loadedPanels.containsKey(Section.SECURITY) && txtSessionTimeoutMinutes != null) {
            try {
                int timeout = Integer.parseInt(txtSessionTimeoutMinutes.getText().trim());
                int warning = Integer.parseInt(txtSessionWarningMinutes.getText().trim());
                if (timeout < 5 || timeout > 120) throw new IllegalArgumentException("Session timeout must be between 5 and 120 minutes.");
                if (warning < 1 || warning >= timeout) throw new IllegalArgumentException("Session warning must be at least 1 minute and less than the timeout.");
            } catch (NumberFormatException e) {
                warn("Session timeout and warning must be whole minutes."); showSecurity(); return false;
            } catch (IllegalArgumentException e) {
                warn(e.getMessage()); showSecurity(); return false;
            }
        }
        if (loadedPanels.containsKey(Section.PAYMENT) && !validatePaymentDetails()) {
            showPayment();
            return false;
        }
        if (loadedPanels.containsKey(Section.EMAIL) && !validateEmailSettings()) {
            showEmail();
            return false;
        }
        if (loadedPanels.containsKey(Section.SHORTCUTS) && !validateShortcutSettings()) {
            showShortcuts();
            return false;
        }
        return true;
    }
    private boolean validatePaymentDetails() {
        SettingsValidationSupport.PaymentResult result = SettingsValidationSupport.validatePayment(
                txtUpiId.getText(), txtAccountNumber.getText(), txtIfsc.getText(),
                txtBankMatchRoundingTolerance.getText());
        if (!result.valid()) {
            warn(result.message());
            return false;
        }
        txtBankMatchRoundingTolerance.setText(result.normalizedTolerance());
        return true;
    }
    private boolean validateEmailSettings() {
        String error = SettingsValidationSupport.emailPortError(txtSmtpPort.getText());
        if (error != null) {
            warn(error);
            return false;
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
        putSetting("update.github.owner", txtGitHubOwner.getText().trim());
        putSetting("update.github.repository", txtGitHubRepository.getText().trim());
        putSetting("update.channel", cmbUpdateChannel.getValue() == null ? "STABLE" : cmbUpdateChannel.getValue());
        putSetting("update.checkAtStartup", String.valueOf(chkUpdateAtStartup.isSelected()));
        putSetting("update.downloadInBackground", String.valueOf(chkDownloadInBackground.isSelected()));
    }

    private String formatUpdateTimestamp(String raw) { return SettingsFieldSupport.formatUpdateTimestamp(raw); }

}
