package org.example.controller;

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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.config.DeploymentMode;
import org.example.api.runtime.DeploymentConnectionService;
import org.example.service.EmailService;
import org.example.service.BrandAssetPolicy;
import org.example.service.BrandImagePresenter;
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
import org.example.util.UiTaskExecutor;
import javafx.application.Platform;

import java.io.File;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
        COMPANY, PAYMENT, INVOICE, NOTIFICATIONS, EMAIL, WORKSPACE, SHORTCUTS, UPDATES
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

    public static void requestSection(Section section) {
        requestedSection = section == null ? Section.COMPANY : section;
    }


    @FXML private StackPane panelHost;
    @FXML private ScrollPane panelScroll;

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
    private String validatedCompanyServerUrl;

    /* =========================================================
       KEYBOARD SHORTCUTS
       ========================================================= */
    @FXML private VBox panelShortcuts;
    @FXML private GridPane shortcutCards;
    @FXML private StackPane shortcutWorkspaceStack;
    @FXML private VBox shortcutListPanel;
    @FXML private ListView<Action> lstShortcutActions;
    @FXML private Label lblShortcutListTitle;
    @FXML private Label lblShortcutListSubtitle;
    @FXML private TextField txtShortcutSearch;
    @FXML private TextField txtShortcutKeys;
    @FXML private ComboBox<String> cmbShortcutCategory;
    @FXML private ComboBox<String> cmbShortcutScope;
    @FXML private ComboBox<Action> cmbShortcutAction;
    @FXML private TextArea txtShortcutDescription;
    @FXML private ToggleButton chkShortcutActive;
    @FXML private CheckBox chkShortcutAllowTextInput;
    @FXML private CheckBox chkShortcutRequireSelection;
    @FXML private VBox shortcutDrawer;
    @FXML private VBox shortcutConflictBox;
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
                        txtSmtpPassword.setText(smtp.appPassword());
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
            case WORKSPACE -> {
                refreshWorkspacePanel();
                boolean admin = SessionService.isAdmin();
                if (deploymentSection != null) { deploymentSection.setVisible(admin); deploymentSection.setManaged(admin); }
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
            case WORKSPACE -> showWorkspace();
            case SHORTCUTS -> showShortcuts();
            case UPDATES -> showUpdates();
            case COMPANY -> showCompany();
        }
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
            String configured = ConfigManager.get(configKey, "");
            if (configured == null || configured.isBlank()) throw new IllegalStateException("No " + label + " is attached.");
            Path path = Path.of(configured).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) throw new IllegalStateException("The configured " + label + " is unavailable.");
            if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Preview is not supported on this computer.");
            Desktop.getDesktop().open(path.toFile());
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
            () -> new AssetSelection(selectedPath, BrandAssetPolicy.inspect(selectedPath, role)),
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
        AssetSelection selection
    ) {
        String previousConfiguredPath = ConfigManager.get(configKey, "");
        String taskKey = "settings-asset-store-" + configKey;
        UiTaskExecutor.submitAction(
            taskKey,
            () -> storeSelectedImage(configKey, baseName, role, selection, previousConfiguredPath),
            result -> {
                ++assetPreviewRevision; // invalidate an older queued preview refresh
                applyImagePreview(result.path(), result.previewImage(), result.inspection(),
                        imageView, placeholder, fileLabel);
            },
            error -> showError("The image could not be saved: " + safeMessage(error))
        );
    }

    private AssetStoreResult storeSelectedImage(
        String configKey,
        String baseName,
        BrandAssetPolicy.Role role,
        AssetSelection selection,
        String previousConfiguredPath
    ) throws Exception {
        String extension = getSafeExtension(selection.path().getFileName().toString());
        Path assetsFolder = ConfigManager.getConfigurationFolder().resolve("assets");
        Files.createDirectories(assetsFolder);

        // Use a new managed filename for every replacement. Consumers never see a
        // partially overwritten image and Windows cannot hold us on a stale file handle.
        String revision = Long.toUnsignedString(System.nanoTime());
        Path destination = assetsFolder.resolve(baseName + "-" + revision + extension);
        Path temporary = Files.createTempFile(assetsFolder, "." + baseName + "-", ".uploading");
        boolean configCommitted = false;
        try {
            Files.copy(selection.path(), temporary, StandardCopyOption.REPLACE_EXISTING);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Image upload was superseded.");

            // Decode exactly one bounded preview from the copied bytes before committing.
            // Corrupt files therefore cannot replace a previously working asset.
            Image previewImage = loadPreviewImage(temporary, role);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Image upload was superseded.");
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination);
            }

            if (Thread.currentThread().isInterrupted()) {
                Files.deleteIfExists(destination);
                throw new InterruptedException("Image upload was superseded.");
            }
            try {
                ConfigManager.set(configKey, destination.toAbsolutePath().toString());
                String persisted = ConfigManager.get(configKey, "");
                if (!ConfigManager.isSharedClient() && !destination.toAbsolutePath().toString().equals(persisted)) {
                    throw new IllegalStateException("The saved image path could not be verified.");
                }
                configCommitted = true;
            } catch (Exception configError) {
                ConfigManager.setWithoutSaving(configKey, previousConfiguredPath);
                try { Files.deleteIfExists(destination); } catch (Exception ignored) { }
                throw configError;
            }

            removeOlderManagedAssetVersions(assetsFolder, baseName, destination);
            return new AssetStoreResult(destination, previewImage, selection.inspection());
        } finally {
            try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
            if (!configCommitted) {
                try { Files.deleteIfExists(destination); } catch (Exception ignored) { }
            }
        }
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

    private void removeOlderManagedAssetVersions(
        Path assetsFolder,
        String baseName,
        Path keep
    ) {
        try (var files = Files.list(assetsFolder)) {
            files.filter(Files::isRegularFile)
                .filter(path -> isManagedAssetVersion(path.getFileName().toString(), baseName))
                .filter(path -> keep == null || !path.toAbsolutePath().normalize()
                        .equals(keep.toAbsolutePath().normalize()))
                .forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
        } catch (Exception ignored) {
            // Cleanup is best-effort only after the new path is safely persisted.
        }
    }

    private boolean isManagedAssetVersion(String fileName, String baseName) {
        if (fileName == null || baseName == null) return false;
        String lower = fileName.toLowerCase();
        String base = baseName.toLowerCase();
        boolean supported = lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        return supported && (lower.equals(base + ".png")
                || lower.equals(base + ".jpg")
                || lower.equals(base + ".jpeg")
                || lower.startsWith(base + "-"));
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
        long started = System.nanoTime();
        List<AssetPreviewResult> results = new ArrayList<>(requests.size());
        for (AssetPreviewRequest request : requests) {
            String configuredPath = ConfigManager.get(request.configKey(), "");
            if (configuredPath.isBlank()) {
                results.add(new AssetPreviewResult(request, null, null, null));
                continue;
            }
            try {
                Path path = Path.of(configuredPath);
                if (!Files.isRegularFile(path)) {
                    results.add(new AssetPreviewResult(request, null, null, null));
                    continue;
                }
                BrandAssetPolicy.Inspection inspection = BrandAssetPolicy.inspect(path, request.role());
                Image image = loadPreviewImage(path, request.role());
                results.add(new AssetPreviewResult(request, image, path, inspection));
            } catch (Exception ignored) {
                results.add(new AssetPreviewResult(request, null, null, null));
            }
        }
        long elapsed = (System.nanoTime() - started) / 1_000_000L;
        if (elapsed >= 20) PerformanceMonitor.event("controller-phase", "settings-preview-background | " + elapsed + " ms");
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

    private void refreshAssetPreview(
        String configKey,
        ImageView imageView,
        VBox placeholder,
        Label fileLabel,
        BrandAssetPolicy.Role role
    ) {
        String configuredPath = ConfigManager.get(configKey, "");
        if (configuredPath.isBlank()) {
            clearImagePreview(imageView, placeholder, fileLabel);
            return;
        }
        try {
            Path path = Path.of(configuredPath);
            if (!Files.isRegularFile(path)) {
                clearImagePreview(imageView, placeholder, fileLabel);
                return;
            }
            showImagePreview(path, imageView, placeholder, fileLabel, role);
        } catch (Exception ignored) {
            clearImagePreview(imageView, placeholder, fileLabel);
        }
    }

    private void showImagePreview(
        Path path,
        ImageView imageView,
        VBox placeholder,
        Label fileLabel,
        BrandAssetPolicy.Role role
    ) {
        // User-selected image changes are infrequent. Keep this immediate path
        // synchronous, but decode only a preview-sized bitmap rather than the full
        // source image. Startup previews use refreshAllAssetPreviewsAsync() above.
        try {
            BrandAssetPolicy.Inspection inspection = BrandAssetPolicy.inspect(path, role);
            Image image = loadPreviewImage(path, role);
            applyImagePreview(path, image, inspection, imageView, placeholder, fileLabel);
        } catch (Exception exception) {
            clearImagePreview(imageView, placeholder, fileLabel);
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

    private Image loadPreviewImage(Path path, BrandAssetPolicy.Role role) throws Exception {
        double requestedWidth = switch (role) {
            case APPLICATION_BANNER -> 1200.0;
            case APPLICATION_MARK -> 420.0;
            case COMPANY_LOGO -> 720.0;
            case SIGNATURE -> 720.0;
            case PAYMENT_QR -> 420.0;
        };
        double requestedHeight = switch (role) {
            case APPLICATION_BANNER -> 320.0;
            case APPLICATION_MARK -> 420.0;
            case COMPANY_LOGO -> 260.0;
            case SIGNATURE -> 260.0;
            case PAYMENT_QR -> 420.0;
        };

        try (InputStream input = Files.newInputStream(path)) {
            Image image = new Image(input, requestedWidth, requestedHeight, true, true);
            if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
                Throwable cause = image.getException();
                throw new IllegalArgumentException(
                        cause == null ? "The selected image could not be decoded." : cause.getMessage(),
                        cause);
            }
            return image;
        }
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

    private record AssetSelection(
        Path path,
        BrandAssetPolicy.Inspection inspection
    ) { }

    private record AssetStoreResult(
        Path path,
        Image previewImage,
        BrandAssetPolicy.Inspection inspection
    ) { }

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

        if (selectedPanel != null && panelHost != null) {
            selectedPanel.setVisible(true);
            selectedPanel.setManaged(true);
            if (panelHost.getChildren().size() != 1 || panelHost.getChildren().getFirst() != selectedPanel) {
                panelHost.getChildren().setAll(selectedPanel);
            }
            // Shortcut Manager owns a premium full-width identity header and must not
            // compete with the generic Settings header. Other Settings sections keep
            // the standard page identity/actions unchanged.
            boolean shortcutMode = selectedPanel == panelShortcuts;
            if (settingsPageHeader != null) {
                settingsPageHeader.setVisible(!shortcutMode);
                settingsPageHeader.setManaged(!shortcutMode);
            }
            // Section visibility changes are synchronous; JavaFX owns the normal CSS/layout pulse.
            if (panelScroll != null) {
                panelScroll.setVbarPolicy(shortcutMode
                        ? ScrollPane.ScrollBarPolicy.NEVER : ScrollPane.ScrollBarPolicy.AS_NEEDED);
                Platform.runLater(() -> panelScroll.setVvalue(0.0));
            }
        }
    }

    @FXML
    private void showCompany() {

        selectSection(navCompany, ensureSectionLoaded(Section.COMPANY));
    }

    @FXML
    private void showPayment() {

        selectSection(navPayment, ensureSectionLoaded(Section.PAYMENT));
    }

    @FXML
    private void showInvoice() {

        selectSection(navInvoice, ensureSectionLoaded(Section.INVOICE));
    }

    @FXML
    private void showNotifications() {

        selectSection(navNotifications, ensureSectionLoaded(Section.NOTIFICATIONS));
    }

    @FXML
    private void showEmail() {

        selectSection(navEmail, ensureSectionLoaded(Section.EMAIL));
    }


    @FXML
    private void showWorkspace() {
        selectSection(navWorkspace, ensureSectionLoaded(Section.WORKSPACE));
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
                    "DSE ERP will copy the workspace while managed services are stopped, verify the destination, and retain the current workspace as a safety copy.",
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
    private void showShortcuts() {
        selectSection(null, ensureSectionLoaded(Section.SHORTCUTS));
        refreshShortcutValidation();
    }

    @FXML
    private void resetAllShortcuts() {
        ensureSectionLoaded(Section.SHORTCUTS);
        for (Action action : shortcutManagerActions()) {
            shortcutDraftValues.put(action, action.defaultBinding());
            shortcutDraftScopes.put(action, action.scope());
            ShortcutRegistry.saveOptions(action, action.scope(), false,
                    action == Action.EDIT_CURRENT || action == Action.OPEN_SELECTED || action == Action.DELETE_SELECTED);
        }
        ShortcutRegistry.saveActions(shortcutManagerDraft(), shortcutManagerScopeDraft(), shortcutManagerActions());
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
        if (shortcutCards == null || txtShortcutSearch == null || cmbShortcutCategory == null) return;

        shortcutDraftValues.clear();
        shortcutDraftScopes.clear();
        for (Action action : ShortcutRegistry.actions()) {
            shortcutDraftValues.put(action, ShortcutRegistry.configuredBinding(action));
            shortcutDraftScopes.put(action, ShortcutRegistry.configuredScope(action));
        }

        installShortcutIcons();
        configureShortcutActionConverter();
        configureShortcutList();

        List<String> categories = shortcutUiCategories();
        shortcutUiLoading = true;
        cmbShortcutCategory.setItems(FXCollections.observableArrayList(categories));
        if (cmbShortcutCategory.getSelectionModel().getSelectedIndex() < 0) cmbShortcutCategory.getSelectionModel().selectFirst();
        cmbShortcutAction.setItems(FXCollections.observableArrayList(shortcutManagerActions()));
        cmbShortcutScope.setItems(FXCollections.observableArrayList(
                java.util.Arrays.stream(ShortcutRegistry.Scope.values()).map(ShortcutRegistry.Scope::label).toList()));
        shortcutUiLoading = false;

        if (!Boolean.TRUE.equals(panelShortcuts.getProperties().get("dse.shortcut-v3.listeners"))) {
            panelShortcuts.getProperties().put("dse.shortcut-v3.listeners", Boolean.TRUE);
            txtShortcutSearch.textProperty().addListener((obs, oldValue, value) -> refreshShortcutWorkspace());
            cmbShortcutCategory.valueProperty().addListener((obs, oldValue, value) -> {
                if (!shortcutUiLoading) refreshShortcutWorkspace();
            });
            cmbShortcutAction.valueProperty().addListener((obs, oldValue, value) -> {
                if (!shortcutUiLoading && value != null) openShortcutEditor(value);
            });
            cmbShortcutScope.valueProperty().addListener((obs, oldValue, value) -> {
                if (!shortcutUiLoading && selectedShortcutAction != null) {
                    ShortcutRegistry.Scope scope = shortcutScopeFromLabel(value, selectedShortcutAction.scope());
                    if (lblShortcutScopeHint != null) lblShortcutScopeHint.setText(shortcutScopeHint(scope));
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

        if (selectedShortcutAction == null || !ShortcutRegistry.permitted(selectedShortcutAction)) {
            selectedShortcutAction = shortcutManagerActions().stream().findFirst().orElse(Action.SAVE_CURRENT);
        }
        refreshShortcutWorkspace();
        closeShortcutDrawer();
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
                return action == null ? "" : shortcutUiCategory(action) + "  •  " + action.label();
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
                Label name = new Label(action.label());
                name.getStyleClass().add("dse-shortcut-v3-list-name");
                Label category = new Label(shortcutUiCategory(action));
                category.getStyleClass().add("dse-shortcut-v3-list-category");
                VBox labels = new VBox(2, name, category);
                HBox.setHgrow(labels, javafx.scene.layout.Priority.ALWAYS);
                Label scope = new Label(shortcutDraftScopes.getOrDefault(action, ShortcutRegistry.configuredScope(action)).label());
                scope.getStyleClass().add("dse-shortcut-v3-scope-badge");
                Label key = new Label(displayShortcut(shortcutDraftValues.get(action)));
                key.getStyleClass().add("dse-shortcut-v3-key-badge");
                ToggleButton enabled = shortcutToggle(action);
                Button delete = new Button("Delete");
                delete.getStyleClass().addAll("dse-shortcut-v3-button", "dse-shortcut-v3-list-delete");
                delete.setOnAction(event -> deleteShortcutAssignment(action));
                HBox row = new HBox(8, labels, scope, key, enabled, delete);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.getStyleClass().add("dse-shortcut-v3-list-row");
                setText(null); setGraphic(row);
            }
        });
        lstShortcutActions.setOnMouseClicked(event -> {
            Action selected = lstShortcutActions.getSelectionModel().getSelectedItem();
            if (selected != null && event.getClickCount() >= 2) openShortcutEditor(selected);
        });
    }

    private List<String> shortcutUiCategories() {
        List<String> categories = new ArrayList<>();
        categories.add("All Categories");
        for (Action action : shortcutManagerActions()) {
            String category = shortcutUiCategory(action);
            if (!categories.contains(category)) categories.add(category);
        }
        return categories;
    }

    private List<Action> shortcutManagerActions() {
        return ShortcutRegistry.availableActions().stream().filter(action -> {
            String category=shortcutUiCategory(action);
            return "Application Actions".equals(category)||"Quick Create".equals(category)||"Navigation".equals(category);
        }).toList();
    }

    private Map<Action,String> shortcutManagerDraft() {
        Map<Action,String> values = new LinkedHashMap<>();
        for (Action action : shortcutManagerActions()) {
            values.put(action, shortcutDraftValues.getOrDefault(action, ShortcutRegistry.configuredBinding(action)));
        }
        return values;
    }

    private Map<Action,ShortcutRegistry.Scope> shortcutManagerScopeDraft() {
        Map<Action,ShortcutRegistry.Scope> scopes = new LinkedHashMap<>();
        for (Action action : shortcutManagerActions()) {
            scopes.put(action, shortcutDraftScopes.getOrDefault(action, ShortcutRegistry.configuredScope(action)));
        }
        return scopes;
    }

    private List<String> validateShortcutManager(Map<Action,String> values, Map<Action,ShortcutRegistry.Scope> scopes) {
        return ShortcutRegistry.validateActions(values, scopes, shortcutManagerActions());
    }

    private String shortcutUiCategory(Action action) {
        if(action==Action.GLOBAL_SEARCH)return "Application Actions";
        String category=action == null || action.category() == null || action.category().isBlank() ? "Application Actions" : action.category();
        return "Search & Filter".equals(category)?"Application Actions":category;
    }

    private String shortcutCategoryAccent(String category) {
        if (category == null) return "purple";
        return switch (category) {
            case "Quick Create" -> "green";
            case "Navigation" -> "blue";
            case "Search & Filter" -> "amber";
            case "PDF Studio" -> "pink";
            case "Excel Studio" -> "teal";
            case "Master Data" -> "violet";
            case "Reports & Tools" -> "teal";
            case "Settings & Tools" -> "pink";
            default -> "purple";
        };
    }

    private String shortcutCategoryIcon(String category) {
        if (category == null) return "adjust";
        return switch (category) {
            case "Quick Create" -> "register";
            case "Navigation" -> "link";
            case "Search & Filter" -> "search";
            case "PDF Studio" -> "document";
            case "Excel Studio" -> "file";
            case "Master Data" -> "database";
            case "Reports & Tools" -> "report";
            case "Settings & Tools" -> "settings";
            default -> "adjust";
        };
    }

    private void refreshShortcutWorkspace() {
        if (shortcutCards == null) return;
        refreshShortcutCards();
        refreshShortcutKpis();
        refreshShortcutValidation();
    }

    private void refreshShortcutCards() {
        String categoryFilter = cmbShortcutCategory == null ? "All Categories" : cmbShortcutCategory.getValue();
        String query = txtShortcutSearch == null || txtShortcutSearch.getText() == null
                ? "" : txtShortcutSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);

        List<Action> filtered = new ArrayList<>();
        for (Action action : shortcutManagerActions()) {
            String category = shortcutUiCategory(action);
            if (categoryFilter != null && !categoryFilter.equals("All Categories") && !categoryFilter.equals(category)) continue;
            if (!query.isBlank()) {
                String haystack = (action.label() + " " + category + " " + displayShortcut(shortcutDraftValues.get(action)) + " "
                        + shortcutDraftScopes.getOrDefault(action, ShortcutRegistry.configuredScope(action)).label()).toLowerCase(java.util.Locale.ROOT);
                if (!haystack.contains(query)) continue;
            }
            filtered.add(action);
        }

        // Search/category filtering uses the virtualized full-height list. Cards never expand.
        if (!query.isBlank() || (categoryFilter != null && !categoryFilter.equals("All Categories"))) {
            String title = !query.isBlank() ? "Search Results" : categoryFilter;
            String subtitle = filtered.size() + (filtered.size() == 1 ? " shortcut" : " shortcuts")
                    + (!query.isBlank() ? " matching ‘" + txtShortcutSearch.getText().trim() + "’" : "");
            showShortcutList(filtered, title, subtitle);
            return;
        }

        showShortcutOverview();
        shortcutCards.getChildren().clear();
        shortcutCards.getColumnConstraints().clear();
        shortcutCards.getRowConstraints().clear();
        for (int i = 0; i < 3; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0/3.0); column.setFillWidth(true); column.setHgrow(javafx.scene.layout.Priority.ALWAYS);
            shortcutCards.getColumnConstraints().add(column);
        }
        RowConstraints rowConstraint=new RowConstraints();rowConstraint.setVgrow(javafx.scene.layout.Priority.ALWAYS);rowConstraint.setFillHeight(true);shortcutCards.getRowConstraints().add(rowConstraint);

        Map<String,List<Action>> grouped = new LinkedHashMap<>();
        for (Action action : shortcutManagerActions())
            grouped.computeIfAbsent(shortcutUiCategory(action), ignored -> new ArrayList<>()).add(action);

        int cardIndex = 0;
        for (Map.Entry<String,List<Action>> entry : grouped.entrySet()) {
            VBox card = buildShortcutCategoryCard(entry.getKey(), entry.getValue());
            int column = cardIndex % 3; int row = cardIndex / 3;
            GridPane.setHgrow(card, javafx.scene.layout.Priority.ALWAYS);GridPane.setVgrow(card, javafx.scene.layout.Priority.ALWAYS);
            shortcutCards.add(card, column, row); cardIndex++;
        }
    }

    private void showShortcutOverview() {
        if (shortcutCards != null) { shortcutCards.setVisible(true); shortcutCards.setManaged(true); }
        if (shortcutListPanel != null) { shortcutListPanel.setVisible(false); shortcutListPanel.setManaged(false); }
    }

    private void showShortcutList(List<Action> actions, String title, String subtitle) {
        if (shortcutCards != null) { shortcutCards.setVisible(false); shortcutCards.setManaged(false); }
        if (shortcutListPanel != null) { shortcutListPanel.setVisible(true); shortcutListPanel.setManaged(true); }
        if (lblShortcutListTitle != null) lblShortcutListTitle.setText(title == null ? "Shortcuts" : title);
        if (lblShortcutListSubtitle != null) lblShortcutListSubtitle.setText(subtitle == null ? "" : subtitle);
        if (lstShortcutActions != null) lstShortcutActions.setItems(FXCollections.observableArrayList(actions));
    }

    @FXML
    private void showShortcutOverviewFromList() {
        shortcutUiLoading = true;
        if (txtShortcutSearch != null) txtShortcutSearch.clear();
        if (cmbShortcutCategory != null) cmbShortcutCategory.getSelectionModel().select("All Categories");
        shortcutUiLoading = false;
        refreshShortcutWorkspace();
    }

    private VBox buildShortcutCategoryCard(String category, List<Action> actions) {
        String accent = shortcutCategoryAccent(category);
        VBox card = new VBox(7);
        card.getStyleClass().addAll("dse-shortcut-v3-card", "dse-shortcut-v3-card-" + accent);
        card.setMaxWidth(Double.MAX_VALUE);card.setMaxHeight(Double.MAX_VALUE);

        StackPane iconBox = new StackPane();
        iconBox.getStyleClass().addAll("dse-shortcut-v3-card-icon", "dse-shortcut-v3-accent-" + accent);
        setShortcutIcon(iconBox, shortcutCategoryIcon(category), 15);
        Label title = new Label(category); title.getStyleClass().add("dse-shortcut-v3-card-title");
        Label count = new Label(Integer.toString(actions.size())); count.getStyleClass().addAll("dse-shortcut-v3-card-count", "dse-shortcut-v3-accent-" + accent);
        HBox spacer = new HBox(); HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        Button add = new Button("+ Add"); add.getStyleClass().addAll("dse-shortcut-v3-card-add", "dse-shortcut-v3-text-" + accent);
        add.setOnAction(event -> addShortcut(category));
        HBox header = new HBox(7, iconBox, title, count, spacer, add); header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        card.getChildren().add(header);

        VBox rows=new VBox(5);rows.setFillWidth(true);
        for(Action action:actions)rows.getChildren().add(buildShortcutRow(action,accent));
        ScrollPane scroll=new ScrollPane(rows);scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);scroll.getStyleClass().add("dse-shortcut-v3-card-scroll");
        VBox.setVgrow(scroll,javafx.scene.layout.Priority.ALWAYS);card.getChildren().add(scroll);
        return card;
    }

    private HBox buildShortcutRow(Action action, String accent) {
        Label name = new Label(action.label()); name.setMaxWidth(Double.MAX_VALUE);
        name.getStyleClass().add("dse-shortcut-v3-action-name"); HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);
        Label key = new Label(displayShortcut(shortcutDraftValues.get(action))); key.getStyleClass().add("dse-shortcut-v3-key-badge");
        ToggleButton enabled = shortcutToggle(action);
        Button delete = new Button("×"); delete.setTooltip(new Tooltip("Delete shortcut assignment")); delete.getStyleClass().addAll("dse-shortcut-v3-more", "dse-shortcut-v3-row-delete");
        delete.setOnAction(event -> deleteShortcutAssignment(action));
        delete.setOnMouseClicked(event -> event.consume());
        HBox row = new HBox(5, name, key, enabled, delete); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getStyleClass().add("dse-shortcut-v3-action-row");
        row.setOnMouseClicked(event -> { if (!(event.getTarget() instanceof ToggleButton) && !(event.getTarget() instanceof Button)) openShortcutEditor(action); });
        return row;
    }

    private void refreshShortcutKpis() {
        if (lblShortcutTotal == null) return;
        int total = shortcutManagerActions().size();
        int custom = 0;
        for (Action action : shortcutManagerActions()) {
            String current = normalizeShortcut(shortcutDraftValues.get(action));
            String defaults = normalizeShortcut(action.defaultBinding());
            if (!current.equalsIgnoreCase(defaults)) custom++;
        }
        List<String> conflicts = validateShortcutManager(shortcutManagerDraft(), shortcutManagerScopeDraft());
        lblShortcutTotal.setText(Integer.toString(total));
        lblShortcutCustom.setText(Integer.toString(custom));
        lblShortcutConflicts.setText(Integer.toString(conflicts.size()));
        lblShortcutCategories.setText(Integer.toString(shortcutUiCategories().size() - 1));
    }

    @FXML
    private void addShortcut() {
        addShortcut(null);
    }

    private void addShortcut(String preferredCategory) {
        List<Action> candidates = shortcutManagerActions().stream()
                .filter(action -> preferredCategory == null || preferredCategory.equals(shortcutUiCategory(action)))
                .toList();
        Action candidate = candidates.stream()
                .filter(action -> shortcutDraftValues.getOrDefault(action, ShortcutRegistry.configuredBinding(action)).isBlank())
                .findFirst().orElse(candidates.stream().findFirst().orElse(Action.SAVE_CURRENT));
        openShortcutEditor(candidate);
        if (lblShortcutDrawerTitle != null) lblShortcutDrawerTitle.setText("Add Shortcut");
        if (shortcutDrawer != null) { shortcutDrawer.setVisible(true); shortcutDrawer.setManaged(true); }
        if (cmbShortcutAction != null) cmbShortcutAction.requestFocus();
    }

    private void openShortcutEditor(Action action) {
        if (action == null || cmbShortcutAction == null) return;
        selectedShortcutAction = action;
        shortcutUiLoading = true;
        cmbShortcutAction.getSelectionModel().select(action);
        String raw = shortcutDraftValues.getOrDefault(action, ShortcutRegistry.configuredBinding(action));
        txtShortcutKeys.setText(displayShortcut(raw));
        chkShortcutActive.setSelected(raw != null && !raw.isBlank());
        ShortcutRegistry.Scope configuredScope = shortcutDraftScopes.getOrDefault(action, ShortcutRegistry.configuredScope(action));
        List<ShortcutRegistry.Scope> allowedScopes = shortcutScopesForAction(action);
        cmbShortcutScope.setItems(FXCollections.observableArrayList(allowedScopes.stream().map(ShortcutRegistry.Scope::label).toList()));
        if (!allowedScopes.contains(configuredScope)) configuredScope = action.scope();
        cmbShortcutScope.getSelectionModel().select(configuredScope.label());
        cmbShortcutScope.setDisable(allowedScopes.size() == 1);
        txtShortcutDescription.setText(shortcutDescription(action));
        if (chkShortcutAllowTextInput != null) chkShortcutAllowTextInput.setSelected(ShortcutRegistry.allowInTextInput(action));
        if (chkShortcutRequireSelection != null) chkShortcutRequireSelection.setSelected(ShortcutRegistry.requireSelection(action));
        lblShortcutDrawerTitle.setText("Edit Shortcut");
        if (lblShortcutScopeHint != null) lblShortcutScopeHint.setText(shortcutScopeHint(configuredScope));
        shortcutUiLoading = false;
        if (shortcutDrawer != null) { shortcutDrawer.setVisible(true); shortcutDrawer.setManaged(true); }
        refreshSelectedShortcutConflict();
    }

    @FXML
    private void closeShortcutDrawer() {
        if (shortcutDrawer == null) return;
        shortcutDrawer.setVisible(false);
        shortcutDrawer.setManaged(false);
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
            txtShortcutKeys.setText(displayShortcut(shortcutDraftValues.get(selectedShortcutAction)));
            event.consume();
            return;
        }
        if ((event.getCode() == KeyCode.BACK_SPACE || event.getCode() == KeyCode.DELETE)
                && !event.isControlDown() && !event.isMetaDown() && !event.isAltDown() && !event.isShiftDown()) {
            txtShortcutKeys.clear();
            chkShortcutActive.setSelected(false);
            refreshSelectedShortcutConflict();
            event.consume();
            return;
        }
        String captured = ShortcutRegistry.fromEvent(event);
        if (!captured.isBlank()) {
            txtShortcutKeys.setText(displayShortcut(captured));
            chkShortcutActive.setSelected(true);
            refreshSelectedShortcutConflict();
        }
        event.consume();
    }


    private ToggleButton shortcutToggle(Action action) {
        boolean active = !shortcutDraftValues.getOrDefault(action, ShortcutRegistry.configuredBinding(action)).isBlank();
        ToggleButton toggle = new ToggleButton("●");
        toggle.setSelected(active);
        toggle.setAlignment(active ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
        toggle.setTooltip(new Tooltip(active ? "Disable shortcut" : "Enable shortcut"));
        toggle.getStyleClass().add("dse-shortcut-v3-toggle");
        toggle.selectedProperty().addListener((obs, oldValue, enabled) -> {
            toggle.setAlignment(enabled ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
            toggle.setTooltip(new Tooltip(enabled ? "Disable shortcut" : "Enable shortcut"));
        });
        toggle.setOnAction(event -> setShortcutEnabled(action, toggle.isSelected()));
        toggle.setOnMouseClicked(event -> event.consume());
        return toggle;
    }

    private void setShortcutEnabled(Action action, boolean enabled) {
        if (action == null) return;
        String binding = enabled ? shortcutDraftValues.getOrDefault(action, "") : "";
        if (enabled && binding.isBlank()) binding = action.defaultBinding();
        shortcutDraftValues.put(action, binding);
        ShortcutRegistry.Scope scope = shortcutDraftScopes.getOrDefault(action, ShortcutRegistry.configuredScope(action));
        ShortcutRegistry.saveActions(Map.of(action, binding), Map.of(action, scope), List.of(action));
        refreshShortcutWorkspace();
        org.example.util.ToastManager.success(panelHost, enabled ? "Shortcut enabled" : "Shortcut disabled",
                action.label() + (enabled ? " is enabled for this user." : " is disabled for this user."));
    }

    @FXML
    private void disableSelectedShortcut() {
        if (selectedShortcutAction != null) disableShortcut(selectedShortcutAction);
    }

    @FXML
    private void deleteSelectedShortcut() {
        if (selectedShortcutAction != null) deleteShortcutAssignment(selectedShortcutAction);
    }

    private void disableShortcut(Action action) {
        if (action == null) return;
        shortcutDraftValues.put(action, "");
        ShortcutRegistry.saveActions(Map.of(action, ""),
                Map.of(action, shortcutDraftScopes.getOrDefault(action, ShortcutRegistry.configuredScope(action))),
                List.of(action));
        refreshShortcutWorkspace();
        if (selectedShortcutAction == action) openShortcutEditor(action);
        org.example.util.ToastManager.success(panelHost, "Shortcut disabled", action.label() + " is disabled for this user.");
    }

    private void deleteShortcutAssignment(Action action) {
        if (action == null) return;
        shortcutDraftValues.put(action, "");
        shortcutDraftScopes.put(action, action.scope());
        ShortcutRegistry.saveActions(Map.of(action, ""), Map.of(action, action.scope()), List.of(action));
        ShortcutRegistry.saveOptions(action, action.scope(), false,
                action == Action.EDIT_CURRENT || action == Action.OPEN_SELECTED || action == Action.DELETE_SELECTED);
        refreshShortcutWorkspace();
        if (selectedShortcutAction == action) closeShortcutDrawer();
        org.example.util.ToastManager.success(panelHost, "Shortcut removed", action.label() + " has no assigned key. You can add it again at any time.");
    }

    @FXML
    private void resetSelectedShortcut() {
        if (selectedShortcutAction == null) return;
        txtShortcutKeys.setText(displayShortcut(selectedShortcutAction.defaultBinding()));
        chkShortcutActive.setSelected(!selectedShortcutAction.defaultBinding().isBlank());
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
                ? normalizeShortcut(txtShortcutKeys == null ? "" : txtShortcutKeys.getText()) : "";
        ShortcutRegistry.Scope selectedScope = shortcutScopeFromLabel(
                cmbShortcutScope == null ? null : cmbShortcutScope.getValue(), selectedShortcutAction.scope());
        Map<Action,String> candidate = new LinkedHashMap<>(shortcutDraftValues);
        Map<Action,ShortcutRegistry.Scope> candidateScopes = new LinkedHashMap<>(shortcutDraftScopes);
        candidate.put(selectedShortcutAction, raw);
        candidateScopes.put(selectedShortcutAction, selectedScope);
        List<String> errors = validateShortcutManager(candidate, candidateScopes);
        if (!errors.isEmpty()) {
            refreshSelectedShortcutConflict();
            warn("Keyboard shortcut conflict:\n" + String.join("\n", errors));
            return;
        }
        shortcutDraftValues.put(selectedShortcutAction, raw);
        shortcutDraftScopes.put(selectedShortcutAction, selectedScope);
        ShortcutRegistry.saveActions(shortcutManagerDraft(), shortcutManagerScopeDraft(), shortcutManagerActions());
        ShortcutRegistry.saveOptions(selectedShortcutAction, selectedScope,
                chkShortcutAllowTextInput != null && chkShortcutAllowTextInput.isSelected(),
                chkShortcutRequireSelection != null && chkShortcutRequireSelection.isSelected());
        refreshShortcutWorkspace();
        openShortcutEditor(selectedShortcutAction);
        org.example.util.ToastManager.success(panelHost, "Shortcut saved", selectedShortcutAction.label() + " shortcut updated.");
    }

    private void refreshSelectedShortcutConflict() {
        if (selectedShortcutAction == null || lblShortcutConflict == null) return;
        String raw = chkShortcutActive != null && chkShortcutActive.isSelected()
                ? normalizeShortcut(txtShortcutKeys == null ? "" : txtShortcutKeys.getText()) : "";
        Map<Action,String> candidate = new LinkedHashMap<>(shortcutDraftValues);
        Map<Action,ShortcutRegistry.Scope> candidateScopes = new LinkedHashMap<>(shortcutDraftScopes);
        candidate.put(selectedShortcutAction, raw);
        candidateScopes.put(selectedShortcutAction, shortcutScopeFromLabel(
                cmbShortcutScope == null ? null : cmbShortcutScope.getValue(), selectedShortcutAction.scope()));
        List<String> errors = validateShortcutManager(candidate, candidateScopes);
        String relevant = errors.stream()
                .filter(error -> error.contains(selectedShortcutAction.label()) || (!raw.isBlank() && error.toLowerCase(java.util.Locale.ROOT).contains(displayShortcut(raw).toLowerCase(java.util.Locale.ROOT))))
                .findFirst().orElse("");
        boolean conflict = !relevant.isBlank();
        lblShortcutConflict.setText(conflict ? relevant : "No conflicts for this shortcut.");
        if (shortcutConflictBox != null) {
            shortcutConflictBox.getStyleClass().removeAll("dse-shortcut-v3-conflict-box-ok", "dse-shortcut-v3-conflict-box-warning");
            shortcutConflictBox.getStyleClass().add(conflict ? "dse-shortcut-v3-conflict-box-warning" : "dse-shortcut-v3-conflict-box-ok");
        }
    }

    private Map<Action, String> shortcutDraft() {
        Map<Action,String> draft = new LinkedHashMap<>();
        for (Action action : ShortcutRegistry.actions()) {
            draft.put(action, shortcutDraftValues.getOrDefault(action, ShortcutRegistry.configuredBinding(action)));
        }
        return draft;
    }

    private boolean validateShortcutSettings() {
        List<String> errors = validateShortcutManager(shortcutManagerDraft(), shortcutManagerScopeDraft());
        if (lblShortcutValidation != null) lblShortcutValidation.setText(errors.isEmpty() ? "No shortcut conflicts detected." : errors.getFirst());
        if (!errors.isEmpty()) {
            warn("Keyboard shortcut conflict:\n" + String.join("\n", errors));
            return false;
        }
        return true;
    }

    private void refreshShortcutValidation() {
        if (lblShortcutValidation == null) return;
        List<String> errors = validateShortcutManager(shortcutManagerDraft(), shortcutManagerScopeDraft());
        lblShortcutValidation.setText(errors.isEmpty() ? "No shortcut conflicts detected." : errors.getFirst());
        lblShortcutValidation.getStyleClass().removeAll("dse-shortcut-v3-validation-ok", "dse-shortcut-v3-validation-warning");
        lblShortcutValidation.getStyleClass().add(errors.isEmpty() ? "dse-shortcut-v3-validation-ok" : "dse-shortcut-v3-validation-warning");
    }

    private void saveShortcutSettings() {
        Map<Action,String> draft = shortcutManagerDraft();
        Map<Action,ShortcutRegistry.Scope> scopes = shortcutManagerScopeDraft();
        List<String> errors = validateShortcutManager(draft, scopes);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        ShortcutRegistry.saveActions(draft, scopes, shortcutManagerActions());
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
            for (Action action : shortcutManagerActions()) {
                String value = properties.getProperty(action.id());
                if (value != null) imported.put(action, normalizeShortcut(value));
                String scopeValue = properties.getProperty(action.id() + ".scope");
                if (scopeValue != null) importedScopes.put(action, shortcutScopeFromLabel(scopeValue, action.scope()));
            }
            List<String> errors = validateShortcutManager(imported, importedScopes);
            if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
            shortcutDraftValues.clear(); shortcutDraftValues.putAll(imported);
            shortcutDraftScopes.clear(); shortcutDraftScopes.putAll(importedScopes);
            ShortcutRegistry.saveActions(imported, importedScopes, shortcutManagerActions());
            for (Action action : shortcutManagerActions()) {
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
            for (Action action : shortcutManagerActions()) {
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


    private List<ShortcutRegistry.Scope> shortcutScopesForAction(Action action) {
        if (action == null) return List.of(ShortcutRegistry.Scope.GLOBAL);
        if (action.scope() == ShortcutRegistry.Scope.PDF_STUDIO) return List.of(ShortcutRegistry.Scope.PDF_STUDIO);
        if (action.scope() == ShortcutRegistry.Scope.EXCEL_STUDIO) return List.of(ShortcutRegistry.Scope.EXCEL_STUDIO);
        if (action.scope() == ShortcutRegistry.Scope.MASTER_DATA) return List.of(ShortcutRegistry.Scope.MASTER_DATA);
        return java.util.Arrays.asList(ShortcutRegistry.Scope.values());
    }
    private ShortcutRegistry.Scope shortcutScopeFromLabel(String value, ShortcutRegistry.Scope fallback) {
        return ShortcutRegistry.Scope.fromStored(value, fallback == null ? ShortcutRegistry.Scope.GLOBAL : fallback);
    }

    private String shortcutScopeHint(ShortcutRegistry.Scope scope) {
        if (scope == null) scope = ShortcutRegistry.Scope.GLOBAL;
        return switch (scope) {
            case GLOBAL -> "Runs across the ERP when the signed-in user has permission for the selected action.";
            case CURRENT_SCREEN -> "Runs only in the currently active page context; useful for Save, Edit, Refresh and other contextual commands.";
            case SALES -> "Runs only while a Sales or Quotation screen is active.";
            case PURCHASE -> "Runs only while a Purchase screen is active.";
            case INVENTORY -> "Runs only in Inventory or Item Master screens.";
            case CUSTOMERS -> "Runs only in Customer screens.";
            case SUPPLIERS -> "Runs only in Supplier screens.";
            case REPORTS -> "Runs only in Reports.";
            case COMMUNICATION -> "Runs only in Communication screens.";
            case SETTINGS -> "Runs only inside Settings.";
            case PDF_STUDIO -> "Runs only while PDF Studio owns the keyboard context.";
            case EXCEL_STUDIO -> "Runs only while Excel Studio owns the keyboard context.";
            case MASTER_DATA -> "Runs only inside the Master Data workspace.";
        };
    }

    private String shortcutDescription(Action action) {
        return switch (action) {
            case GLOBAL_SEARCH -> "Opens Global Search across every ERP module permitted for the signed-in user.";
            case SAVE_CURRENT -> "Saves the current record or document when the active screen supports Save.";
            case EDIT_CURRENT -> "Edits the current or selected record when the active screen supports Edit.";
            case REFRESH_CURRENT -> "Refreshes the data on the current application page.";
            case NEW_CURRENT -> "Creates a new record in the current page when that page supports New.";
            case OPEN_SELECTED -> "Opens the currently selected record.";
            case DELETE_SELECTED -> "Deletes the selected record after the screen's normal permission and confirmation checks.";
            case PRINT_CURRENT -> "Prints the current document or page when printing is supported.";
            case EXPORT_CURRENT -> "Exports the current data when the active screen supports Export.";
            case CLOSE_BACK -> "Closes the current editor or returns to the previous application view.";
            case NEW_SALE -> "Opens a new Sales Invoice quickly.";
            case NEW_PURCHASE -> "Opens a new Purchase document quickly.";
            case NEW_QUOTATION -> "Opens a new Quotation quickly.";
            case ITEM_MASTER -> "Navigates directly to Item Master.";
            case MASTERS -> "Navigates directly to Master Data.";
            case BANK_STATEMENT -> "Navigates directly to Bank Statement reconciliation.";
            case BANK_ENTRY -> "Opens Bank Entry.";
            case EXPENSE_ENTRY -> "Opens Expense Entry.";
            default -> "Opens or executes " + action.label() + " using the selected shortcut scope.";
        };
    }

    private String displayShortcut(String raw) {
        if (raw == null || raw.isBlank()) return "Disabled";
        return raw.replace("Shortcut", "Ctrl/Cmd");
    }

    private String normalizeShortcut(String value) {
        if (value == null) return "";
        String raw = value.trim();
        if (raw.equalsIgnoreCase("Disabled") || raw.equalsIgnoreCase("None")) return "";
        return raw.replaceAll("(?i)ctrl/cmd", "Shortcut")
                .replaceAll("(?i)cmd", "Shortcut")
                .replaceAll("(?i)command", "Shortcut")
                .replaceAll("(?i)control", "Shortcut")
                .replaceAll("(?i)ctrl", "Shortcut")
                .replaceAll("\\s*\\+\\s*", "+");
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
            if (loadedPanels.containsKey(Section.WORKSPACE)) saveDeploymentSettings();
            if (loadedPanels.containsKey(Section.SHORTCUTS)) saveShortcutSettings();
            if (loadedPanels.containsKey(Section.UPDATES)) saveUpdateSettings();
            ConfigManager.save();
        } finally {
            batchingSettingsSave = false;
        }

        return true;
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

        putSetting(
            "company.name",
            txtCompanyName
                .getText()
                .trim()
        );

        putSetting(
            "company.phone",
            txtPhone
                .getText()
                .trim()
        );

        putSetting(
            "company.email",
            txtEmail
                .getText()
                .trim()
        );

        putSetting(
            "company.gstin",
            txtGstin
                .getText()
                .trim()
                .toUpperCase()
        );

        putSetting(
            "company.pan",
            txtCompanyPan
                .getText()
                .trim()
                .toUpperCase()
        );

        putSetting(
            "company.businessType",
            valueOrEmpty(cmbBusinessType)
        );

        putSetting(
            "company.industry",
            valueOrEmpty(cmbIndustry)
        );

        putSetting(
            "company.financialYearStart",
            dpFinancialYearStart.getValue() == null
                ? ""
                : dpFinancialYearStart
                .getValue()
                .toString()
        );

        putSetting("application.displayName", txtApplicationName.getText().trim());
        putSetting("application.tagline", txtApplicationTagline.getText().trim());
        putSetting("application.startingText", txtApplicationStartingText.getText().trim());
    }

    private void savePaymentDetails() {

        putSetting(
            "payment.upiId",
            txtUpiId
                .getText()
                .trim()
        );

        putSetting(
            "payment.accountHolder",
            txtAccountHolder
                .getText()
                .trim()
        );

        putSetting(
            "payment.bankName",
            txtBankName
                .getText()
                .trim()
        );

        putSetting(
            "payment.accountNumber",
            txtAccountNumber
                .getText()
                .trim()
        );

        putSetting(
            "payment.ifsc",
            txtIfsc
                .getText()
                .trim()
                .toUpperCase()
        );

        putSetting(
            "payment.branch",
            txtBranch
                .getText()
                .trim()
        );
    }

    private void saveInvoiceIdentity() {

        putSetting(
            "company.address",
            txtCompanyAddress
                .getText()
                .trim()
        );

        putSetting(
            "company.state",
            txtCompanyState
                .getText()
                .trim()
        );

        putSetting(
            "company.website",
            txtCompanyWebsite
                .getText()
                .trim()
        );

        putSetting(
            "company.tagline",
            txtCompanyTagline
                .getText()
                .trim()
        );

        putSetting(
            "company.shipAddress",
            txtShipAddress
                .getText()
                .trim()
        );

        putSetting(
            "company.terms",
            txtInvoiceTerms
                .getText()
                .trim()
        );

        putSetting(
            "company.currency",
            valueOrEmpty(cmbCurrency)
        );

        putSetting(
            "company.timeZone",
            valueOrEmpty(cmbTimeZone)
        );

        putSetting(
            "company.dateFormat",
            valueOrEmpty(cmbDateFormat)
        );
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
                    new org.example.api.authority.BusinessEmailClient.Settings(email, password, host, port));
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

            EmailService.send(
                recipient,
                "DSE ERP email test",
                "Your DSE ERP email configuration is working correctly."
            );

            org.example.util.ToastManager.success(panelWorkspace, "Test email sent",
                "Test email sent successfully to " + recipient + ".");

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
        putSetting("update.github.owner", txtGitHubOwner.getText().trim());
        putSetting("update.github.repository", txtGitHubRepository.getText().trim());
        putSetting("update.channel", cmbUpdateChannel.getValue() == null ? "STABLE" : cmbUpdateChannel.getValue());
        putSetting("update.checkAtStartup", String.valueOf(chkUpdateAtStartup.isSelected()));
        putSetting("update.downloadInBackground", String.valueOf(chkDownloadInBackground.isSelected()));
    }

    private String formatUpdateTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return "Never";
        try {
            return java.time.format.DateTimeFormatter.ofPattern(BusinessClock.datePattern() + ", hh:mm a")
                    .withZone(BusinessClock.zone()).format(java.time.Instant.parse(raw));
        } catch (Exception ignored) {
            return raw;
        }
    }

}
