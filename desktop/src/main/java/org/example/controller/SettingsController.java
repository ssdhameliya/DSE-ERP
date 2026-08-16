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
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.service.EmailService;
import org.example.service.BrandAssetPolicy;
import org.example.service.BrandImagePresenter;
import org.example.service.NotificationService;
import org.example.ui.SharedApplicationFooter;
import org.example.update.UpdateDialogs;
import org.example.update.UpdateService;
import org.example.update.BuildInfo;
import org.example.util.IconFactory;
import org.example.util.PerformanceMonitor;
import org.example.util.UiTaskExecutor;
import javafx.application.Platform;

import java.io.File;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;

/**
 * Settings entered here are persisted locally.
 *
 * The controller preserves the existing seven settings sections and their
 * existing persistence logic while sidebar navigation chooses the active panel.
 */
public class SettingsController implements ScreenLifecycle {
    public enum Section {
        COMPANY, PAYMENT, INVOICE, NOTIFICATIONS, EMAIL, WORKSPACE, UPDATES
    }

    private static volatile Section requestedSection = Section.COMPANY;
    private boolean batchingSettingsSave;
    private boolean rootInitialized;
    private boolean fragmentLoading;
    private final EnumMap<Section, VBox> loadedPanels = new EnumMap<>(Section.class);

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
    @FXML private StackPane applicationBrandPreview;
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

    @FXML
    private VBox placeholderSignature;

    @FXML
    private VBox placeholderPaymentQr;

    @FXML
    private Label lblLogoFile;

    @FXML private Label lblApplicationBrandFile;

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

    private static final String APPLICATION_BRAND_PATH_KEY =
        "application.brandImagePath";

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
            case UPDATES -> "UpdatesSettingsPanel.fxml";
        };
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/settings/" + file));
            loader.setController(this);
            fragmentLoading = true;
            VBox panel = loader.load();
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
                BrandImagePresenter.contain(imgCompanyLogo, companyLogoPreview);
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
                txtSmtpEmail.setText(ConfigManager.get("smtp.email", ""));
                txtSmtpPassword.setText(ConfigManager.get("smtp.appPassword", ""));
                txtSmtpHost.setText(ConfigManager.get("smtp.host", ""));
                txtSmtpPort.setText(ConfigManager.get("smtp.port", "587"));
            }
            case WORKSPACE -> refreshWorkspacePanel();
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
    private void removeApplicationBrand() {
        removeConfiguredAsset(APPLICATION_BRAND_PATH_KEY, imgApplicationBrand, placeholderApplicationBrand, lblApplicationBrandFile);
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
        Label fileLabel,
        BrandAssetPolicy.Role role
    ) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image files", "*.png", "*.jpg", "*.jpeg"));

        File selected = chooser.showOpenDialog(txtCompanyName.getScene().getWindow());
        if (selected == null) return;

        Path temporary = null;
        try {
            BrandAssetPolicy.Inspection inspection = BrandAssetPolicy.inspect(selected.toPath(), role);
            if (inspection.hasWarnings() && !confirmImageWarnings(role, inspection)) return;

            String extension = getSafeExtension(selected.getName());
            Path assetsFolder = ConfigManager.getConfigFolder().resolve("assets");
            Files.createDirectories(assetsFolder);
            Path destination = assetsFolder.resolve(baseName + extension);

            temporary = Files.createTempFile(assetsFolder, baseName + "-", extension + ".uploading");
            Files.copy(selected.toPath(), temporary, StandardCopyOption.REPLACE_EXISTING);

            // Decode the copied file before replacing the currently working asset.
            // A corrupt upload therefore cannot remove production branding.
            BrandAssetPolicy.inspect(temporary, role);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;

            removeOlderAssetVersions(assetsFolder, baseName, destination);
            putSetting(configKey, destination.toAbsolutePath().toString());
            showImagePreview(destination, imageView, placeholder, fileLabel, role);
        } catch (Exception exception) {
            showError("The image could not be saved: " + exception.getMessage());
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
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

    private void removeOlderAssetVersions(
        Path assetsFolder,
        String baseName,
        Path keep
    ) {
        for (String extension : List.of(".png", ".jpg", ".jpeg")) {
            Path candidate = assetsFolder.resolve(baseName + extension);
            if (keep != null && candidate.toAbsolutePath().normalize().equals(keep.toAbsolutePath().normalize())) continue;
            try {
                Files.deleteIfExists(candidate);
            } catch (Exception ignored) {
                // Cleanup is best-effort after the replacement asset is safe.
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
        List<AssetPreviewRequest> requests = new ArrayList<>();
        if (imgApplicationBrand != null && placeholderApplicationBrand != null && lblApplicationBrandFile != null)
            requests.add(new AssetPreviewRequest(APPLICATION_BRAND_PATH_KEY, imgApplicationBrand, placeholderApplicationBrand, lblApplicationBrandFile, BrandAssetPolicy.Role.APPLICATION_BANNER));
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
            this::applyAssetPreviews,
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
                byte[] bytes = Files.readAllBytes(path);
                Image image = new Image(new ByteArrayInputStream(bytes));
                if (image.isError()) {
                    results.add(new AssetPreviewResult(request, null, null, null));
                    continue;
                }
                BrandAssetPolicy.Inspection inspection = BrandAssetPolicy.inspect(path, request.role());
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
        // synchronous so the preview updates before the chooser action returns;
        // startup previews use refreshAllAssetPreviewsAsync() above.
        try {
            byte[] bytes = Files.readAllBytes(path);
            Image image = new Image(new ByteArrayInputStream(bytes));
            if (image.isError()) {
                clearImagePreview(imageView, placeholder, fileLabel);
                return;
            }

            imageView.setImage(image);
            imageView.setVisible(true);
            imageView.setManaged(true);
            placeholder.setVisible(false);
            placeholder.setManaged(false);

            BrandAssetPolicy.Inspection inspection = BrandAssetPolicy.inspect(path, role);
            fileLabel.setText(path.getFileName() + " • " + inspection.dimensions());
        } catch (Exception exception) {
            clearImagePreview(imageView, placeholder, fileLabel);
        }
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
            // Section visibility changes are synchronous; JavaFX owns the normal CSS/layout pulse.
            if (panelScroll != null) {
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

        batchingSettingsSave = true;
        try {
            if (loadedPanels.containsKey(Section.COMPANY)) saveCompanyDetails();
            if (loadedPanels.containsKey(Section.PAYMENT)) savePaymentDetails();
            if (loadedPanels.containsKey(Section.INVOICE)) saveInvoiceIdentity();
            if (loadedPanels.containsKey(Section.EMAIL)) saveEmailSettings();
            if (loadedPanels.containsKey(Section.NOTIFICATIONS)) saveNotificationSettings();
            if (loadedPanels.containsKey(Section.UPDATES)) saveUpdateSettings();
            ConfigManager.save();
        } finally {
            batchingSettingsSave = false;
        }

        return true;
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

        putSetting(
            "smtp.email",
            txtSmtpEmail
                .getText()
                .trim()
        );

        putSetting(
            "smtp.appPassword",
            txtSmtpPassword.getText()
        );

        putSetting(
            "smtp.host",
            txtSmtpHost
                .getText()
                .trim()
        );

        String port =
            txtSmtpPort
                .getText()
                .trim();

        putSetting(
            "smtp.port",
            port.isBlank()
                ? "587"
                : port
        );
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
        if (loadedPanels.containsKey(Section.PAYMENT) && !validatePaymentDetails()) {
            showPayment();
            return false;
        }
        if (loadedPanels.containsKey(Section.EMAIL) && !validateEmailSettings()) {
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
