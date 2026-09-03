package org.example.controller;

import org.example.util.BusinessClock;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;



import javafx.fxml.FXML;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Screen;
import javafx.scene.Node;
import javafx.scene.Cursor;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.RotateTransition;
import javafx.util.Duration;
import javafx.scene.input.KeyEvent;
import org.example.navigation.NavigationManager;
import org.example.theme.ThemeManager;
import org.example.util.ClockService;
import org.example.util.PerformanceMonitor;
import org.example.util.ShellIndicatorBus;
import org.example.util.PlatformUiSupport;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.service.SessionService;
import org.example.service.UserService;
import org.example.service.NotificationService;
import org.example.service.GlobalSearchService;
import org.example.service.GlobalSearchService.SearchResult;
import org.example.service.PermissionService;
import org.example.util.IconFactory;
import org.example.util.UiActionIcons;
import org.example.config.ConfigManager;
import org.example.api.insights.InsightsApiClient;
import org.example.shortcut.ShortcutRegistry;
import org.example.shortcut.ApplicationCommandDispatcher;
import org.example.shortcut.ShortcutRegistry.Action;
import org.example.update.UpdateDialogs;

public class DashboardController {
    private static volatile DashboardController CURRENT;
    private final InsightsApiClient insightsApi = new InsightsApiClient();

    /** Periodically refreshes the unread badge while the main shell is open. */
    private Timeline notificationRefresh;
    private final Runnable shellIndicatorListener = this::refreshShellIndicatorsAsync;
    private final Runnable permissionChangeListener = this::refreshRolePermissionsOnFxThread;
    private final AtomicBoolean indicatorRefreshRunning = new AtomicBoolean();
    private Scene shortcutScene;
    private final EventHandler<KeyEvent> dynamicShortcutHandler = this::handleDynamicShortcut;


    public Button btnRunImport;
    public Button btnImport;
    @FXML
    private Button btnDashboard;
    @FXML
    private Button btnMasters;

    @FXML
    private Button btnInventory;

    @FXML
    private Button btnPurchase;

    @FXML
    private Button btnSales;
    @FXML private Button btnSalesRegister;
    @FXML private Button btnCreateSale;
    @FXML private Button btnSalesReturn;
    @FXML
    private Button btnQuotation;
    @FXML private Button btnPurchaseRegister;
    @FXML private Button btnCreatePurchase;
    @FXML private Button btnPurchaseReturn;
    @FXML private Button btnBankEntry;
    @FXML private Button btnExpenseEntry;
    @FXML private Button btnBankStatement;
    @FXML private Button btnPurchaseRecon;
    @FXML private Button btnReconSupplier;
    @FXML private VBox salesSubmenu;
    @FXML private VBox purchaseSubmenu;
    @FXML private VBox bankExpenseSubmenu;
    @FXML private VBox documentStudioSubmenu;
    @FXML private VBox settingsSubmenu;
    @FXML private Label lblSalesChevron;
    @FXML private Label lblPurchaseChevron;
    @FXML private Label lblBankExpenseChevron;
    @FXML private Label lblDocumentStudioChevron;
    @FXML private Label lblSettingsChevron;
    @FXML private Button btnBankExpense;
    @FXML private Button btnReminders;
    @FXML private Button btnUserAccess;
    @FXML private Button btnDocumentStudio;
    @FXML private Button btnPdfStudio;
    @FXML private Button btnExcelStudio;
    @FXML private Button btnBackup;
    @FXML private Button btnSafeRollback;

    @FXML
    private Button btnReports;

    @FXML
    private Button btnSettings;
    @FXML private Button btnSettingsCompany;
    @FXML private Button btnSettingsPayment;
    @FXML private Button btnSettingsInvoice;
    @FXML private Button btnSettingsNotifications;
    @FXML private Button btnSettingsEmail;
    @FXML private Button btnSettingsSecurity;
    @FXML private Button btnSettingsWorkspace;
    @FXML private Button btnSettingsShortcuts;
    @FXML private Button btnSettingsUpdates;

    @FXML
    private Label lblPageTitle;
    @FXML private Label lblBreadcrumb;
    @FXML private StackPane shellPageIcon;
    @FXML private Label lblSidebarUser;

    @FXML
    private Button btnItem;

    @FXML
    private Button btnCustomer;

    @FXML
    private Button btnSupplier;

    @FXML
    private Label lblClock;

    /** Shared footer populated from values maintained in Settings. */
    @FXML
    private Label lblCompanyFooter;
    @FXML private VBox sidebarRoot;
    @FXML private HBox topBar;

    @FXML
    private TextField txtSearch;

    @FXML
    private ToggleButton btnTheme;

    @FXML private Label lblNotificationBadge;
    @FXML private Label lblEmailBadge;
    @FXML private Label lblWhatsappBadge;
    @FXML private Label lblReminderBadge;
    @FXML private Button btnReminderTop;
    @FXML private Button btnNotifications;
    @FXML private Button btnEmailCenter;
    @FXML private Button btnWhatsappCenter;
    @FXML private Button btnShortcutInfo;
    @FXML private Button btnSidebarToggle;
    @FXML private Button shellNewSale;

    @FXML
    private MenuButton menuUser;

    public void initialize() {
        DashboardController previous = CURRENT;
        if (previous != null && previous != this) previous.stopRecurringTasks();
        CURRENT = this;
        ShellIndicatorBus.subscribe(shellIndicatorListener);
        PermissionService.addChangeListener(permissionChangeListener);

        ClockService.start(lblClock);
        // Company details do not change every second. Refreshing the complete
        // footer from the clock listener caused repeated ConfigManager reads and
        // layout pulses, especially on macOS Retina displays.
        refreshCompanyFooter();


        navigationManager = new NavigationManager(contentPane);
        initializeSidebarAccordion();
        applySidebarVisibility(loadSidebarVisiblePreference(), false);

        String landingPath = PermissionService.allowed("DASHBOARD.VIEW") ? "/fxml/pages/DashboardHome.fxml" : "/fxml/pages/Profile.fxml";
        String landingTitle = PermissionService.allowed("DASHBOARD.VIEW") ? "Dashboard" : "My Profile";
        if (navigationManager.loadPage(landingPath)) {
            lblPageTitle.setText(landingTitle);
            updateShellPageIcon(landingTitle);
            selectMenu("Dashboard".equals(landingTitle) ? btnDashboard : null);
        }
        updateThemeButton();
        refreshShellIndicatorsAsync();
        if (SessionService.current() != null) {
            menuUser.setText(SessionService.current().getFullName());
            if (lblSidebarUser != null) lblSidebarUser.setText(SessionService.current().getFullName());
        }
        configureProfileMenuIcons();
        applyNavigationSemanticIcons();
        bindShortcutLabels();
        Platform.runLater(() -> {
            bindShellControls();
            if (contentPane != null && contentPane.getScene() != null)
                UpdateDialogs.showWhatsNewOnce(contentPane.getScene().getWindow());
        });
        applyRolePermissions();
        notificationRefresh = new Timeline(
            new KeyFrame(Duration.seconds(3), event -> { PerformanceMonitor.event("recurring-task", "shell-indicators"); refreshShellIndicatorsAsync(); }));
        notificationRefresh.setCycleCount(Timeline.INDEFINITE);
        notificationRefresh.play();

    }



    /** Explicit business semantics prevent parent/submenu and import/export actions from sharing generic glyphs. */
    private void applyNavigationSemanticIcons() {
        UiActionIcons.apply(btnDashboard,"dashboard","Dashboard");
        UiActionIcons.apply(btnSales,"sale","Sales");
        UiActionIcons.apply(btnSalesRegister,"sale","Sales Register");
        UiActionIcons.apply(btnCreateSale,"add","Create Sale");
        UiActionIcons.apply(btnSalesReturn,"return","Sales Return");
        UiActionIcons.apply(btnQuotation,"quotation","Quotations");
        UiActionIcons.apply(btnPurchase,"purchase","Purchase");
        UiActionIcons.apply(btnPurchaseRegister,"purchase","Purchase Register");
        UiActionIcons.apply(btnCreatePurchase,"add","Create Purchase");
        UiActionIcons.apply(btnPurchaseReturn,"return","Purchase Return");
        UiActionIcons.apply(btnItem,"item","Item Master");
        UiActionIcons.apply(btnMasters,"master","Master Data");
        UiActionIcons.apply(btnBankExpense,"bank","Bank & Expense");
        UiActionIcons.apply(btnImport,"import","Data Import");
        UiActionIcons.apply(btnInventory,"inventory","Inventory");
        UiActionIcons.apply(btnCustomer,"customer","Customers");
        UiActionIcons.apply(btnSupplier,"supplier","Suppliers");
        UiActionIcons.apply(btnReports,"report","Reports");
        UiActionIcons.apply(btnReminders,"reminder","Reminder Center");
        UiActionIcons.apply(btnUserAccess,"permission","User Access");
        UiActionIcons.apply(btnDocumentStudio,"document","Document Studio");
        UiActionIcons.apply(btnSettings,"settings","Settings");
        UiActionIcons.apply(btnSettingsSecurity,"security","Security & Session");
        UiActionIcons.apply(btnSafeRollback,"rollback","Safe Rollback");
        UiActionIcons.apply(btnBackup,"backup","Backup & Restore");
    }

    /** Keeps visible shell/navigation labels synchronized with the user shortcut registry. */
    private void bindShortcutLabels() {
        org.example.shortcut.ShortcutRegistry.bindLabel(shellNewSale, org.example.shortcut.ShortcutRegistry.Action.NEW_SALE, "New Sale");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnCreateSale, org.example.shortcut.ShortcutRegistry.Action.NEW_SALE, "Create Sale");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnCreatePurchase, org.example.shortcut.ShortcutRegistry.Action.NEW_PURCHASE, "Create Purchase");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnDashboard, org.example.shortcut.ShortcutRegistry.Action.DASHBOARD, "Dashboard");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnSalesRegister, org.example.shortcut.ShortcutRegistry.Action.SALES_REGISTER, "Sales Register");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnSalesReturn, org.example.shortcut.ShortcutRegistry.Action.SALES_RETURN, "Sales Return");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnQuotation, org.example.shortcut.ShortcutRegistry.Action.QUOTATION_REGISTER, "Quotation");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnPurchaseRegister, org.example.shortcut.ShortcutRegistry.Action.PURCHASE_REGISTER, "Purchase Register");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnPurchaseReturn, org.example.shortcut.ShortcutRegistry.Action.PURCHASE_RETURN, "Purchase Return");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnItem, org.example.shortcut.ShortcutRegistry.Action.ITEM_MASTER, "Item Master");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnInventory, org.example.shortcut.ShortcutRegistry.Action.INVENTORY, "Inventory");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnCustomer, org.example.shortcut.ShortcutRegistry.Action.CUSTOMERS, "Customers");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnSupplier, org.example.shortcut.ShortcutRegistry.Action.SUPPLIERS, "Suppliers");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnMasters, org.example.shortcut.ShortcutRegistry.Action.MASTERS, "Master Data");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnBankStatement, org.example.shortcut.ShortcutRegistry.Action.BANK_STATEMENT, "Bank Statement");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnBankEntry, org.example.shortcut.ShortcutRegistry.Action.BANK_ENTRY, "Bank Entry");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnExpenseEntry, org.example.shortcut.ShortcutRegistry.Action.EXPENSE_ENTRY, "Expense Entry");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnReminders, org.example.shortcut.ShortcutRegistry.Action.REMINDERS, "Reminder Center");
        org.example.shortcut.ShortcutRegistry.bindLabel(btnUserAccess, org.example.shortcut.ShortcutRegistry.Action.USER_ACCESS, "User Access");
    }

    /** Reads all four shell counters off the JavaFX thread in one database round-trip. */
    private void refreshShellIndicatorsAsync() {
        if (!indicatorRefreshRunning.compareAndSet(false, true)) return;
        CompletableFuture.supplyAsync(() -> {
            try { var c=insightsApi.shellCounts(); return new int[]{c.notifications(),c.email(),c.whatsapp(),c.reminders()}; }
            catch(Exception ignored) { return new int[]{0,0,0,0}; }
        }).whenComplete((counts, error) -> Platform.runLater(() -> {
            try {
                if (counts != null) {
                    applyBadge(lblNotificationBadge, counts[0]);
                    applyBadge(lblEmailBadge, counts[1]);
                    applyBadge(lblWhatsappBadge, counts[2]);
                    applyBadge(lblReminderBadge, counts[3]);
                }
            } finally { indicatorRefreshRunning.set(false); }
        }));
    }

    private void applyBadge(Label badge, int count) {
        if (badge == null) return;
        badge.setText(count > 99 ? "99+" : Integer.toString(count));
        badge.setVisible(count > 0);
        badge.setManaged(count > 0);
    }

    /** Stops timers owned by an ERP shell that is no longer visible. */
    private void stopRecurringTasks() {
        ShellIndicatorBus.unsubscribe(shellIndicatorListener);
        PermissionService.removeChangeListener(permissionChangeListener);
        if (shortcutScene != null) {
            shortcutScene.removeEventFilter(KeyEvent.KEY_PRESSED, dynamicShortcutHandler);
            if (shortcutScene.getProperties().get("dse.dynamic-shortcuts.owner") == this)
                shortcutScene.getProperties().remove("dse.dynamic-shortcuts.owner");
            shortcutScene = null;
        }
        if (notificationRefresh != null) {
            notificationRefresh.stop();
            notificationRefresh = null;
        }
    }

    /** Re-applies permission state immediately after a permission cache refresh. */
    private void refreshRolePermissionsOnFxThread() {
        Runnable apply = () -> {
            if (CURRENT == this) applyRolePermissions();
        };
        if (Platform.isFxApplicationThread()) apply.run(); else Platform.runLater(apply);
    }

    /** Refreshes the shell footer from the current company configuration. */
    private void refreshCompanyFooter() {
        if (lblCompanyFooter == null) return;
        String company = ConfigManager.get("company.name", "DSE ERP").trim();
        String phone = ConfigManager.get("company.phone", "").trim();
        String email = ConfigManager.get("company.email", "").trim();
        String website = ConfigManager.get("company.website", "").trim();
        String gstin = ConfigManager.get("company.gstin", "").trim();
        String address = ConfigManager.get("company.address", "").trim();
        List<String> details = new java.util.ArrayList<>();
        if (!phone.isBlank()) details.add("Phone: " + phone);
        if (!email.isBlank()) details.add("Email: " + email);
        if (!website.isBlank()) details.add("Website: " + website);
        if (!gstin.isBlank()) details.add("GSTIN: " + gstin);
        if (!address.isBlank()) details.add("Address: " + address.replaceAll("[\\r\\n]+", ", "));
        lblCompanyFooter.setText(company + (details.isEmpty() ? "" : "   •   " + String.join("   •   ", details)));
    }

    /** Disables protected navigation modules when the signed-in role lacks VIEW access. */
    private void applyRolePermissions() {
        protect(btnSales, "SALES.VIEW"); protect(btnPurchase, "PURCHASE.VIEW");
        protect(btnQuotation, "QUOTATION.VIEW"); protect(btnItem, "INVENTORY.VIEW");
        protect(btnInventory, "INVENTORY.VIEW"); protect(btnCustomer, "CUSTOMERS.VIEW");
        protect(btnSupplier, "SUPPLIERS.VIEW"); protect(btnMasters, "MASTERS.VIEW");
        protect(btnReports, "REPORTS.VIEW"); protect(btnReminders, "REMINDERS.VIEW");
        protect(btnUserAccess, "USERS.VIEW"); protect(btnBackup, "BACKUP.VIEW");
        protect(btnSettings, "SETTINGS.VIEW"); protect(btnSafeRollback, "SAFE_ROLLBACK.VIEW"); protect(btnDocumentStudio, "DOCUMENT_STUDIO.VIEW"); protect(btnImport, "IMPORT.VIEW");

        // Quotations have their own permission but live inside the Sales accordion.
        // Keep the parent expandable when either Sales or Quotations is available.
        boolean salesAllowed = PermissionService.allowed("SALES.VIEW");
        boolean quotationAllowed = PermissionService.allowed("QUOTATION.VIEW");
        if (btnSales != null) btnSales.setDisable(!(salesAllowed || quotationAllowed));
        for (Button child : new Button[]{btnSalesRegister, btnCreateSale, btnSalesReturn}) {
            if (child != null) child.setDisable(!salesAllowed);
        }
        if (btnQuotation != null) btnQuotation.setDisable(!quotationAllowed);

        inheritGroupPermission(btnPurchase, btnPurchaseRegister, btnCreatePurchase, btnPurchaseReturn);

        // Purchase Recon is a server-backed reconciliation domain with its own permissions.
        // Keep the Bank & Expense parent available when the user can access any child domain.
        boolean bankAllowed = PermissionService.allowed("BANK_EXPENSE.VIEW");
        boolean purchaseReconAllowed = PermissionService.allowed("PURCHASE_RECON.VIEW");
        boolean reconSupplierAllowed = PermissionService.allowed("RECON_SUPPLIER.VIEW");
        if (btnBankExpense != null) btnBankExpense.setDisable(!(bankAllowed || purchaseReconAllowed || reconSupplierAllowed));
        for (Button child : new Button[]{btnBankEntry, btnExpenseEntry, btnBankStatement}) {
            if (child != null) child.setDisable(!bankAllowed);
        }
        if (btnPurchaseRecon != null) btnPurchaseRecon.setDisable(!purchaseReconAllowed);
        if (btnReconSupplier != null) btnReconSupplier.setDisable(!reconSupplierAllowed);
        inheritGroupPermission(btnDocumentStudio, btnPdfStudio, btnExcelStudio);
        inheritGroupPermission(btnSettings, btnSettingsCompany, btnSettingsPayment, btnSettingsInvoice,
                btnSettingsNotifications, btnSettingsEmail, btnSettingsSecurity, btnSettingsWorkspace, btnSettingsShortcuts, btnSettingsUpdates);
    }

    private void protect(Button button, String permission) {
        if (button != null) button.setDisable(!PermissionService.allowed(permission));
    }

    private void inheritGroupPermission(Button parent, Button... children) {
        if (parent == null) return;
        boolean disabled = parent.isDisable();
        for (Button child : children) if (child != null) child.setDisable(disabled);
    }

    private void bindShellControls() {
        if (contentPane.getScene() == null) return;
        PlatformUiSupport.installResponsiveClasses(contentPane.getScene());
        if (lblCompanyFooter != null) PlatformUiSupport.configureTextOverflow(lblCompanyFooter);
        installDynamicShortcuts(contentPane.getScene());
        Node sidebarUser = contentPane.getScene().lookup(".sidebar-user");
        if (sidebarUser != null) {
            sidebarUser.setCursor(Cursor.HAND);
            sidebarUser.setOnMouseClicked(e -> showProfile());
        }
        // Decorate only the persistent shell. Loaded page buttons are owned by their
        // controllers/SharedUiFramework and must not be re-inferred from visible text here.
        if (sidebarRoot != null) {
            for (Node node : sidebarRoot.lookupAll(".button")) if (node instanceof Button b) applyIcon(b);
        }
        if (topBar != null) {
            for (Node node : topBar.lookupAll(".button")) if (node instanceof Button b) applyIcon(b);
        }
        for (Node node : contentPane.getScene().getRoot().lookupAll(".toolbar-menu")) {
            if (node instanceof Button button) button.setGraphic(IconFactory.icon("menu"));
        }
        if (btnReminderTop != null) btnReminderTop.setGraphic(IconFactory.icon("reminder"));
        if (btnNotifications != null) btnNotifications.setGraphic(IconFactory.icon("notification"));
        if (btnEmailCenter != null) btnEmailCenter.setGraphic(IconFactory.icon("email"));
        if (btnWhatsappCenter != null) btnWhatsappCenter.setGraphic(IconFactory.icon("whatsapp"));
        if (btnShortcutInfo != null) btnShortcutInfo.setGraphic(IconFactory.icon("info"));
        refreshSidebarTogglePresentation();
        if (btnDocumentStudio != null) {
            btnDocumentStudio.setGraphic(IconFactory.icon("document", 18));
            btnDocumentStudio.getProperties().put("erp.icon.semantic", "document");
            btnDocumentStudio.setContentDisplay(ContentDisplay.LEFT);
            btnDocumentStudio.setGraphicTextGap(10);
        }
        menuUser.setGraphic(IconFactory.icon("user"));
    }

    @FXML
    private void showShortcutInfo() {
        Dialog<ButtonType> dialog = new OwnedDialog<>();
        dialog.setTitle("Keyboard Shortcuts");
        dialog.setHeaderText("Quick navigation from anywhere in DSE ERP");
        if (btnShortcutInfo != null && btnShortcutInfo.getScene() != null
                && btnShortcutInfo.getScene().getWindow() != null) {
            dialog.initOwner(btnShortcutInfo.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        GridPane shortcuts = new GridPane();
        shortcuts.setHgap(18);
        shortcuts.setVgap(10);
        shortcuts.getStyleClass().add("shortcut-info-grid");
        List<Action> entries = ShortcutRegistry.actions(ShortcutRegistry.Scope.GLOBAL);
        for (int row = 0; row < entries.size(); row++) {
            Action action = entries.get(row);
            Label key = new Label(ShortcutRegistry.display(action));
            key.getStyleClass().add("shortcut-info-key");
            Label name = new Label(action.label());
            name.getStyleClass().add("shortcut-info-name");
            shortcuts.add(key, 0, row);
            shortcuts.add(name, 1, row);
        }
        Label intro = new Label("Current global shortcuts (configure them in Settings → Keyboard Shortcuts):");
        intro.setWrapText(true);
        VBox content = new VBox(12, intro, shortcuts);
        content.getStyleClass().add("shortcut-info-content");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.getStyleClass().add("shortcut-info-scroll");
        scroll.setPrefViewportWidth(560);
        scroll.setPrefViewportHeight(520);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(620);
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setOnShown(event -> {
            var window = dialog.getDialogPane().getScene() == null ? null : dialog.getDialogPane().getScene().getWindow();
            if (window == null) return;
            var screen = Screen.getScreensForRectangle(window.getX(), window.getY(), Math.max(1, window.getWidth()), Math.max(1, window.getHeight()))
                    .stream().findFirst().orElse(Screen.getPrimary());
            double maxHeight = Math.max(420, screen.getVisualBounds().getHeight() * 0.84);
            window.setHeight(Math.min(window.getHeight(), maxHeight));
            window.centerOnScreen();
        });
        dialog.showAndWait();
    }

    private void installDynamicShortcuts(Scene scene) {
        if (scene == null) return;
        if (shortcutScene != null && shortcutScene != scene) shortcutScene.removeEventFilter(KeyEvent.KEY_PRESSED, dynamicShortcutHandler);
        shortcutScene = scene;
        // Keep a single shell-owned filter. Scoped editors suppress these global commands and handle their own bindings.
        Object marker = scene.getProperties().get("dse.dynamic-shortcuts.owner");
        if (marker != this) {
            scene.getProperties().put("dse.dynamic-shortcuts.owner", this);
            scene.addEventFilter(KeyEvent.KEY_PRESSED, dynamicShortcutHandler);
        }
    }

    private void handleDynamicShortcut(KeyEvent event) {
        if (event == null || event.isConsumed() || ShortcutRegistry.isEditorTarget(event.getTarget())) return;
        // Shell-owned actions are catalog entries whose product/default scope is global. Users may
        // narrow those actions to a module/screen; configured scope is checked before execution.
        for (Action action : ShortcutRegistry.actions()) {
            if (action.scope() == ShortcutRegistry.Scope.PDF_STUDIO || action.scope() == ShortcutRegistry.Scope.EXCEL_STUDIO) continue;
            if (!ShortcutRegistry.matches(event, action)) continue;
            if (!ShortcutRegistry.permitted(action) || !ShortcutRegistry.scopeActive(action, event.getTarget())) continue;
            if (ShortcutRegistry.textInputBlocked(action, event.getTarget())) continue;
            if (ShortcutRegistry.requireSelection(action) && !ApplicationCommandDispatcher.hasSelection()) continue;
            runShortcutAction(action);
            event.consume();
            return;
        }
    }

    private void runShortcutAction(Action action) {
        switch (action) {
            case GLOBAL_SEARCH -> { txtSearch.requestFocus(); txtSearch.selectAll(); }
            case TOGGLE_SIDEBAR -> toggleSidebar();
            case SAVE_CURRENT, EDIT_CURRENT, REFRESH_CURRENT, NEW_CURRENT, OPEN_SELECTED, DELETE_SELECTED, PRINT_CURRENT, EXPORT_CURRENT, CLOSE_BACK,
                 MASTER_DELETE, MASTER_EDIT, MASTER_REFRESH, MASTER_NEW -> ApplicationCommandDispatcher.execute(action);
            case NEW_SALE -> createSale();
            case NEW_PURCHASE -> createPurchase();
            case NEW_QUOTATION -> createQuotationFromShortcut();
            case NEW_CUSTOMER -> { openCustomers(); ApplicationCommandDispatcher.execute(Action.NEW_CURRENT); }
            case NEW_SUPPLIER -> { openSupplier(); ApplicationCommandDispatcher.execute(Action.NEW_CURRENT); }
            case NEW_MASTER -> { openMasters(); ApplicationCommandDispatcher.execute(Action.MASTER_NEW); }
            case DASHBOARD -> openDashboard();
            case SALES_REGISTER -> openSales();
            case SALES_RETURN -> openReturns();
            case QUOTATION_REGISTER -> openQuotations();
            case PURCHASE_REGISTER -> openPurchase();
            case PURCHASE_RETURN -> openPurchaseReturns();
            case ITEM_MASTER -> openItemMaster();
            case INVENTORY -> openInventory();
            case CUSTOMERS -> openCustomers();
            case SUPPLIERS -> openSupplier();
            case MASTERS -> openMasters();
            case BANK_STATEMENT -> openBankStatement();
            case BANK_ENTRY -> openBankEntry();
            case EXPENSE_ENTRY -> openExpenseEntry();
            case REMINDERS -> openReminderCenter();
            case USER_ACCESS -> openUserAccess();
            case NOTIFICATION_CENTER -> showNotifications();
            case MY_PROFILE -> showProfile();
            case CHANGE_PASSWORD -> changePassword();
            case TOGGLE_THEME -> toggleTheme();
            case SHORTCUT_HELP -> showShortcutInfo();
            case LOGOUT -> logout();
            case REPORTS -> openReports();
            case DATA_IMPORT -> openImport();
            case COMMUNICATION -> openCommunication();
            case EMAIL_CENTER -> openEmailCenter();
            case WHATSAPP_CENTER -> openWhatsappCenter();
            case PDF_STUDIO_OPEN -> openPdfStudio();
            case EXCEL_STUDIO_OPEN -> openExcelStudio();
            case BACKUP_RESTORE -> openBackup();
            case SAFE_ROLLBACK -> openSafeRollback();
            case SETTINGS_COMPANY -> openSettingsCompany();
            case SETTINGS_PAYMENT -> openSettingsPayment();
            case SETTINGS_INVOICE -> openSettingsInvoice();
            case SETTINGS_NOTIFICATIONS -> openSettingsNotifications();
            case SETTINGS_EMAIL -> openSettingsEmail();
            case SETTINGS_WORKSPACE -> openSettingsWorkspace();
            case SETTINGS_SHORTCUTS -> openSettingsShortcuts();
            case SETTINGS_UPDATES -> openSettingsUpdates();
            default -> { }
        }
    }

    private void createQuotationFromShortcut() {
        QuotationEditorContext.open(null);
        openPage(btnQuotation, "Create Quotation", "/fxml/pages/QuotationEditor.fxml");
        if (lblBreadcrumb != null) lblBreadcrumb.setText("ERP  >  Quotations");
    }

    /** Applies the shared vector icon vocabulary to shell and navigation buttons. */
    private void applyIcon(Button button) {
        String text = button.getText() == null ? "" : button.getText().toLowerCase(Locale.ROOT);
        String icon = text.contains("dashboard") ? "dashboard"
            : text.contains("quotation") ? "quotation"
            : text.contains("import") ? "import"
            : text.contains("sale") ? "sale"
            : text.contains("purchase") ? "purchase"
            : text.contains("inventory") ? "inventory"
            : text.contains("item") ? "item"
            : text.contains("master") ? "master"
            : text.contains("customer") || text.contains("crm") ? "customer"
            : text.contains("supplier") || text.contains("hrm") ? "supplier"
            : text.contains("bank") || text.contains("expense") ? "bank"
            : text.contains("pdf studio") ? "pdf"
            : text.contains("excel studio") ? "excel"
            : text.contains("report") ? "report"
            : text.contains("email") || text.contains("communication") ? "email"
            : text.contains("document studio") ? "document"
            : text.contains("company") ? "business"
            : text.contains("invoice") || text.contains("delivery") ? "document"
            : text.contains("workspace") || text.contains("storage") ? "workspace"
            : text.contains("application update") ? "update"
            : text.contains("keyboard shortcut") ? "shortcut"
            : text.contains("notification") ? "notification"
            : text.contains("reminder") ? "reminder"
            : text.contains("rollback") ? "rollback"
            : text.contains("backup") ? "save"
            : text.contains("user access") ? "users"
            : text.contains("setting") ? "settings"
            : null;
        if (icon != null) button.setGraphic(IconFactory.icon(icon));
    }

    @FXML
    private StackPane contentPane;

    private NavigationManager navigationManager;


    @FXML
    private void toggleTheme() {

        ThemeManager.toggle(btnTheme.getScene());
        updateThemeButton();

    }

    private void updateThemeButton() {
        boolean dark = ThemeManager.getCurrentTheme() == ThemeManager.Theme.DARK;
        btnTheme.setSelected(dark);
        btnTheme.setText(dark ? "Dark" : "Light");
        btnTheme.setGraphic(IconFactory.icon(dark ? "moon" : "sun"));
    }


    @FXML
    private void toggleSidebar() {
        boolean visible = sidebarRoot == null || !sidebarRoot.isManaged();
        applySidebarVisibility(visible, true);
    }

    private void applySidebarVisibility(boolean visible, boolean persist) {
        if (sidebarRoot == null) return;
        sidebarRoot.setManaged(visible);
        sidebarRoot.setVisible(visible);
        if (persist) ConfigManager.set(sidebarPreferenceKey(), Boolean.toString(visible));
        refreshSidebarTogglePresentation();
        if (contentPane != null) {
            contentPane.requestLayout();
            if (contentPane.getParent() != null) contentPane.getParent().requestLayout();
            org.example.util.RegisterUiSupport.reflowAfterShellResize(contentPane);
        }
    }

    private boolean loadSidebarVisiblePreference() {
        return Boolean.parseBoolean(ConfigManager.get(sidebarPreferenceKey(), "true"));
    }

    private String sidebarPreferenceKey() {
        String username = SessionService.current() == null ? "" : SessionService.current().getUsername();
        if (username == null || username.isBlank()) return "ui.sidebar.visible";
        String safe = username.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return "ui.user." + safe + ".sidebar.visible";
    }

    private void refreshSidebarTogglePresentation() {
        if (btnSidebarToggle == null) return;
        boolean visible = sidebarRoot == null || sidebarRoot.isManaged();
        btnSidebarToggle.setGraphic(IconFactory.icon("menu"));
        btnSidebarToggle.setAccessibleText(visible ? "Hide navigation" : "Show navigation");
        if (btnSidebarToggle.getTooltip() != null) {
            String shortcut = ShortcutRegistry.display(Action.TOGGLE_SIDEBAR);
            String suffix = "Disabled".equals(shortcut) ? "" : " (" + shortcut + ")";
            btnSidebarToggle.getTooltip().setText((visible ? "Hide navigation" : "Show navigation") + suffix);
        }
    }

    private void selectMenu(Button button) {
        clearSelection();
        if (button != null && !button.getStyleClass().contains("menu-selected")) button.getStyleClass().add("menu-selected");
    }

    private enum NavGroup { NONE, SALES, PURCHASE, BANK_EXPENSE, DOCUMENT_STUDIO, SETTINGS }

    /** Initializes the sidebar in a compact state so a new login shows only top-level modules. */
    private void initializeSidebarAccordion() {
        configureChevron(lblSalesChevron);
        configureChevron(lblPurchaseChevron);
        configureChevron(lblBankExpenseChevron);
        configureChevron(lblDocumentStudioChevron);
        configureChevron(lblSettingsChevron);
        setGroupExpanded(NavGroup.SALES, false, false);
        setGroupExpanded(NavGroup.PURCHASE, false, false);
        setGroupExpanded(NavGroup.BANK_EXPENSE, false, false);
        setGroupExpanded(NavGroup.DOCUMENT_STUDIO, false, false);
        setGroupExpanded(NavGroup.SETTINGS, false, false);
    }

    private void configureChevron(Label chevron) {
        if (chevron == null) return;
        chevron.setText("");
        chevron.setGraphic(IconFactory.compactIcon("chevron", 13));
        chevron.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    @FXML private void toggleSalesMenu() { toggleGroup(NavGroup.SALES); }
    @FXML private void togglePurchaseMenu() { toggleGroup(NavGroup.PURCHASE); }
    @FXML private void toggleBankExpenseMenu() { toggleGroup(NavGroup.BANK_EXPENSE); }
    @FXML private void toggleDocumentStudioMenu() { toggleGroup(NavGroup.DOCUMENT_STUDIO); }
    @FXML private void toggleSettingsMenu() { toggleGroup(NavGroup.SETTINGS); }

    private void toggleGroup(NavGroup group) {
        if (isGroupDisabled(group)) return;
        boolean willOpen = !isGroupExpanded(group);
        collapseAllSubmenus(group);
        setGroupExpanded(group, willOpen, true);
    }

    private boolean isGroupDisabled(NavGroup group) {
        Button parent = parentButton(group);
        return parent != null && parent.isDisable();
    }

    private boolean isGroupExpanded(NavGroup group) {
        VBox submenu = submenuFor(group);
        return submenu != null && submenu.isManaged();
    }

    private void collapseAllSubmenus(NavGroup except) {
        for (NavGroup group : new NavGroup[]{NavGroup.SALES, NavGroup.PURCHASE, NavGroup.BANK_EXPENSE, NavGroup.DOCUMENT_STUDIO, NavGroup.SETTINGS}) {
            if (group != except) setGroupExpanded(group, false, true);
        }
    }

    private void collapseAllSubmenus() { collapseAllSubmenus(NavGroup.NONE); }

    private void setGroupExpanded(NavGroup group, boolean expanded, boolean animateChevron) {
        VBox submenu = submenuFor(group);
        Label chevron = chevronFor(group);
        Button parent = parentButton(group);
        if (submenu == null) return;

        submenu.setManaged(expanded);
        submenu.setVisible(expanded);
        if (parent != null) {
            if (expanded && !parent.getStyleClass().contains("nav-expanded")) parent.getStyleClass().add("nav-expanded");
            if (!expanded) parent.getStyleClass().remove("nav-expanded");
        }
        if (chevron != null) {
            double target = expanded ? 90.0 : 0.0;
            if (animateChevron && chevron.getScene() != null) {
                RotateTransition rt = new RotateTransition(Duration.millis(130), chevron);
                rt.setToAngle(target);
                rt.play();
            } else chevron.setRotate(target);
        }
    }

    private VBox submenuFor(NavGroup group) {
        return switch (group) {
            case SALES -> salesSubmenu;
            case PURCHASE -> purchaseSubmenu;
            case BANK_EXPENSE -> bankExpenseSubmenu;
            case DOCUMENT_STUDIO -> documentStudioSubmenu;
            case SETTINGS -> settingsSubmenu;
            default -> null;
        };
    }

    private Label chevronFor(NavGroup group) {
        return switch (group) {
            case SALES -> lblSalesChevron;
            case PURCHASE -> lblPurchaseChevron;
            case BANK_EXPENSE -> lblBankExpenseChevron;
            case DOCUMENT_STUDIO -> lblDocumentStudioChevron;
            case SETTINGS -> lblSettingsChevron;
            default -> null;
        };
    }

    private Button parentButton(NavGroup group) {
        return switch (group) {
            case SALES -> btnSales;
            case PURCHASE -> btnPurchase;
            case BANK_EXPENSE -> btnBankExpense;
            case DOCUMENT_STUDIO -> btnDocumentStudio;
            case SETTINGS -> btnSettings;
            default -> null;
        };
    }

    private NavGroup groupFor(Button button, String fxmlPath) {
        if (button == btnSales || button == btnSalesRegister || button == btnCreateSale || button == btnSalesReturn || button == btnQuotation)
            return NavGroup.SALES;
        if (button == btnPurchase || button == btnPurchaseRegister || button == btnCreatePurchase || button == btnPurchaseReturn)
            return NavGroup.PURCHASE;
        if (button == btnBankExpense || button == btnBankEntry || button == btnExpenseEntry || button == btnBankStatement || button == btnPurchaseRecon || button == btnReconSupplier)
            return NavGroup.BANK_EXPENSE;
        if (button == btnDocumentStudio || button == btnPdfStudio || button == btnExcelStudio)
            return NavGroup.DOCUMENT_STUDIO;
        if (button == btnSettings || button == btnSettingsCompany || button == btnSettingsPayment
                || button == btnSettingsInvoice || button == btnSettingsNotifications || button == btnSettingsEmail || button == btnSettingsSecurity
                || button == btnSettingsWorkspace || button == btnSettingsShortcuts || button == btnSettingsUpdates)
            return NavGroup.SETTINGS;
        String path = fxmlPath == null ? "" : fxmlPath.toLowerCase(Locale.ROOT);
        if (path.contains("quotation") || path.contains("saleslist") || path.contains("salesreturns") || path.endsWith("/sale.fxml")) return NavGroup.SALES;
        if (path.contains("purchaselist") || path.contains("purchasereturns") || path.endsWith("/purchase.fxml")) return NavGroup.PURCHASE;
        if (path.contains("bankexpense") || path.contains("bankstatement") || path.contains("purchaserecon") || path.contains("reconsupplier")) return NavGroup.BANK_EXPENSE;
        if (path.contains("documentstudio") || path.contains("pdfdesigner") || path.contains("exceldesigner")) return NavGroup.DOCUMENT_STUDIO;
        if (path.contains("settings")) return NavGroup.SETTINGS;
        return NavGroup.NONE;
    }

    private Button selectionButtonFor(Button button, String fxmlPath) {
        String path = fxmlPath == null ? "" : fxmlPath.toLowerCase(Locale.ROOT);
        if (path.contains("saleslist")) return btnSalesRegister;
        if (path.endsWith("/sale.fxml")) return btnCreateSale;
        if (path.contains("salesreturns")) return btnSalesReturn;
        if (path.contains("quotation")) return btnQuotation;
        if (path.contains("purchaselist")) return btnPurchaseRegister;
        if (path.endsWith("/purchase.fxml")) return btnCreatePurchase;
        if (path.contains("purchasereturns")) return btnPurchaseReturn;
        if (path.contains("bankstatement")) return btnBankStatement;
        if (path.contains("purchaserecon")) return btnPurchaseRecon;
        if (path.contains("reconsupplier")) return btnReconSupplier;
        if (path.contains("exceldesigner")) return btnExcelStudio;
        if (path.contains("pdfdesigner")) return btnPdfStudio;
        return button;
    }

    private void synchronizeSidebar(NavGroup group) {
        if (group == NavGroup.NONE) collapseAllSubmenus();
        else {
            collapseAllSubmenus(group);
            setGroupExpanded(group, true, true);
        }
    }

    private void markGroupActive(NavGroup group) {
        Button parent = parentButton(group);
        if (parent != null && !parent.getStyleClass().contains("menu-group-active")) parent.getStyleClass().add("menu-group-active");
    }


    private List<Button> navigationButtons() {
        return java.util.stream.Stream.of(
                btnDashboard,
                btnSales, btnSalesRegister, btnCreateSale, btnSalesReturn, btnQuotation,
                btnPurchase, btnPurchaseRegister, btnCreatePurchase, btnPurchaseReturn,
                btnItem, btnMasters, btnBankExpense, btnBankEntry, btnExpenseEntry, btnBankStatement, btnPurchaseRecon, btnReconSupplier,
                btnImport, btnInventory, btnCustomer, btnSupplier, btnReports, btnReminders, btnUserAccess,
                btnDocumentStudio, btnPdfStudio, btnExcelStudio, btnSettings, btnSettingsCompany, btnSettingsPayment,
                btnSettingsInvoice, btnSettingsNotifications, btnSettingsEmail, btnSettingsSecurity, btnSettingsWorkspace,
                btnSettingsShortcuts, btnSettingsUpdates, btnSafeRollback, btnBackup)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private void clearSelection() {
        for (Button button : navigationButtons()) button.getStyleClass().remove("menu-selected");
        for (Button parent : new Button[]{btnSales, btnPurchase, btnBankExpense, btnDocumentStudio, btnSettings}) {
            if (parent != null) parent.getStyleClass().remove("menu-group-active");
        }
    }

    private String currentUserName() {
        var user = SessionService.current();
        if (user == null || user.getFullName() == null || user.getFullName().isBlank()) return "User";
        return user.getFullName().trim();
    }

    /**
     * Keeps the shell page icon synchronized with the destination page.
     * The semantic is derived centrally from the page title, so individual FXML
     * screens never hardcode a second copy of their navigation icon.
     */
    private void updateShellPageIcon(String pageTitle) {
        if (shellPageIcon == null) return;
        String semantic = IconFactory.semanticForPageTitle(pageTitle);
        shellPageIcon.getChildren().setAll(IconFactory.icon(semantic, 28));
    }

    private void openPage(Button button,
                          String pageTitle,
                          String fxmlPath) {

        String requiredPermission = permissionForPage(fxmlPath);
        if (requiredPermission != null && !PermissionService.allowed(requiredPermission)) {
            new OwnedAlert(Alert.AlertType.WARNING, "You do not have permission to open " + pageTitle + ". Required: " + requiredPermission).showAndWait();
            return;
        }
        if (navigationManager.loadPage(fxmlPath)) {
            NavGroup group = groupFor(button, fxmlPath);
            Button selectedButton = selectionButtonFor(button, fxmlPath);
            synchronizeSidebar(group);
            selectMenu(selectedButton);
            markGroupActive(group);
            lblPageTitle.setText(pageTitle);
            updateShellPageIcon(pageTitle);
            if (lblBreadcrumb != null) {
                lblBreadcrumb.setText(pageTitle.equals("Dashboard")
                    ? "Welcome back, " + currentUserName() + "!"
                    : "ERP  >  " + pageTitle);
            }
        }
    }

    private String permissionForPage(String fxmlPath) {
        String path = fxmlPath == null ? "" : fxmlPath.toLowerCase(Locale.ROOT);
        if (path.contains("dashboardhome")) return "DASHBOARD.VIEW";
        if (path.contains("quotation")) return "QUOTATION.VIEW";
        if (path.contains("saleslist") || path.contains("salesreturns") || path.endsWith("/sale.fxml")) return "SALES.VIEW";
        if (path.contains("purchaselist") || path.contains("purchasereturns") || path.endsWith("/purchase.fxml")) return "PURCHASE.VIEW";
        if (path.contains("itemmaster") || path.contains("inventory")) return "INVENTORY.VIEW";
        if (path.contains("customer.fxml")) return "CUSTOMERS.VIEW";
        if (path.contains("suppliers.fxml")) return "SUPPLIERS.VIEW";
        if (path.contains("masterdata")) return "MASTERS.VIEW";
        if (path.contains("reports")) return "REPORTS.VIEW";
        if (path.contains("reminder")) return "REMINDERS.VIEW";
        if (path.contains("useraccess")) return "USERS.VIEW";
        if (path.contains("backuprestore")) return "BACKUP.VIEW";
        if (path.contains("settings")) return "SETTINGS.VIEW";
        if (path.contains("saferollback")) return "SAFE_ROLLBACK.VIEW";
        if (path.contains("documentstudio") || path.contains("pdfdesigner") || path.contains("exceldesigner")) return "DOCUMENT_STUDIO.VIEW";
        if (path.contains("import.fxml")) return "IMPORT.VIEW";
        if (path.contains("communication")) return "COMMUNICATION.VIEW";
        if (path.contains("bankexpense") || path.contains("bankstatement")) return "BANK_EXPENSE.VIEW";
        if (path.contains("purchaserecon")) return "PURCHASE_RECON.VIEW";
        if (path.contains("reconsupplier")) return "RECON_SUPPLIER.VIEW";
        return null;
    }

    @FXML
    private void openDashboard() {

        openPage(
            btnDashboard,
            "Dashboard",
            "/fxml/pages/DashboardHome.fxml"
        );

    }


    @FXML
    private void openItemMaster() {

        openPage(
            btnItem,
            "Item Master",
            "/fxml/pages/ItemMaster.fxml"
        );

    }

    @FXML
    private void openCustomers() {

        openPage(
            btnCustomer,
            "Customers",
            "/fxml/pages/Customer.fxml"
        );

    }

    @FXML
    private void openSupplier() {

        openPage(btnSupplier,
            "Suppliers",
            "/fxml/pages/Suppliers.fxml");

    }

    @FXML
    private void openInventory() {

        openPage(btnInventory,
            "Inventory",
            "/fxml/pages/Inventory.fxml");

    }

    @FXML
    private void openPurchase() {
        openPage(btnPurchaseRegister,
            "Purchase",
            "/fxml/pages/PurchaseList.fxml");

    }

    @FXML
    private void openSales() {
        openPage(btnSalesRegister,
            "Sales",
            "/fxml/pages/SalesList.fxml");

    }

    @FXML
    private void openQuotations() {
        openPage(btnQuotation, "Quotation Register", "/fxml/pages/Quotations.fxml");
    }

    @FXML private void openBankEntry() {
        BankExpenseController.requestMode(BankExpenseController.Mode.BANK);
        openPage(btnBankEntry, "Bank Entry", "/fxml/pages/BankExpense.fxml");
    }

    @FXML private void openExpenseEntry() {
        BankExpenseController.requestMode(BankExpenseController.Mode.EXPENSE);
        openPage(btnExpenseEntry, "Expense Entry", "/fxml/pages/BankExpense.fxml");
    }

    @FXML private void openBankStatement() {
        openPage(btnBankStatement, "Bank Statement", "/fxml/pages/BankStatement.fxml");
    }

    @FXML private void openPurchaseRecon() {
        openPage(btnPurchaseRecon, "Purchase Recon", "/fxml/pages/PurchaseRecon.fxml");
    }

    @FXML private void openReconSupplier() {
        openPage(btnReconSupplier, "Recon Supplier", "/fxml/pages/ReconSupplier.fxml");
    }

    /** Lets administration child pages navigate inside the existing ERP shell. */
    public static void navigateFromChildPage(String title, String fxmlPath) {
        DashboardController c = currentVisibleShell();
        if (c == null) {
            javafx.application.Platform.runLater(() -> NavigationManager.navigateOrReport(fxmlPath));
            return;
        }
        javafx.application.Platform.runLater(() -> c.openPage(c.btnUserAccess, title, fxmlPath));
    }

    /** Lets Document Studio child pages keep the shell title and menu selection synchronized. */
    public static void navigateFromDocumentStudio(String title, String fxmlPath) {
        DashboardController c = currentVisibleShell();
        if (c == null) {
            javafx.application.Platform.runLater(() -> NavigationManager.navigateOrReport(fxmlPath));
            return;
        }
        javafx.application.Platform.runLater(() -> {
            Button target = title != null && title.toLowerCase(Locale.ROOT).contains("excel") ? c.btnExcelStudio : c.btnPdfStudio;
            c.openPage(target == null ? c.btnDocumentStudio : target, title, fxmlPath);
        });
    }

    /** Lets a feature page navigate through the existing cached ERP shell. */
    public static void navigateFromChild(String title, String fxmlPath, BankExpenseController.Mode mode) {
        DashboardController c = currentVisibleShell();
        if (mode != null) BankExpenseController.requestMode(mode);
        if (c == null) {
            javafx.application.Platform.runLater(() -> NavigationManager.navigateOrReport(fxmlPath));
            return;
        }
        Button target = mode == BankExpenseController.Mode.EXPENSE ? c.btnExpenseEntry
            : mode == BankExpenseController.Mode.BANK ? c.btnBankEntry
            : fxmlPath != null && fxmlPath.endsWith("/PurchaseRecon.fxml") ? c.btnPurchaseRecon
            : fxmlPath != null && fxmlPath.endsWith("/BankStatement.fxml") ? c.btnBankStatement
            : c.btnBankExpense;
        javafx.application.Platform.runLater(() -> c.openPage(target, title, fxmlPath));
    }

    private static DashboardController currentVisibleShell() {
        DashboardController c = CURRENT;
        if (c == null || c.contentPane == null || c.contentPane.getScene() == null
                || c.contentPane.getScene().getWindow() == null
                || !c.contentPane.getScene().getWindow().isShowing()) return null;
        return c;
    }

    @FXML private void createSale() { openPage(btnCreateSale, "Create Sale", "/fxml/pages/Sale.fxml"); }
    public static void createSaleFromWorkflow() {
        DashboardController c = currentVisibleShell();
        if (c == null) { NavigationManager.navigateOrReport("/fxml/pages/Sale.fxml"); return; }
        javafx.application.Platform.runLater(() -> c.openPage(c.btnCreateSale, "Create Sale", "/fxml/pages/Sale.fxml"));
    }

    public static void createPurchaseFromWorkflow() {
        DashboardController c = currentVisibleShell();
        if (c == null) { NavigationManager.navigateOrReport("/fxml/pages/Purchase.fxml"); return; }
        javafx.application.Platform.runLater(() -> c.openPage(c.btnCreatePurchase, "Create Purchase", "/fxml/pages/Purchase.fxml"));
    }
    @FXML private void createPurchase() { openPage(btnCreatePurchase, "Create Purchase", "/fxml/pages/Purchase.fxml"); }
    @FXML private void openReturns() { openPage(btnSalesReturn, "Sales Return Register", "/fxml/pages/SalesReturns.fxml"); }
    @FXML private void openPurchaseReturns() { openPage(btnPurchaseReturn, "Purchase Return", "/fxml/pages/PurchaseReturns.fxml"); }
    @FXML private void openReminderCenter() {
        openPage(btnReminders, "Reminder Center", "/fxml/pages/ReminderCenter.fxml");
        refreshReminderBadge();
    }
    @FXML private void openUserAccess() { openPage(btnUserAccess, "User Access & Permissions", "/fxml/pages/UserAccess.fxml"); }
    @FXML private void openCommunication() {
        CommunicationScreenContext.select(null);
        openPage(null, "Communication Center", "/fxml/pages/CommunicationCenter.fxml");
    }
    @FXML private void openDocumentStudio() { openPdfStudio(); }
    @FXML private void openPdfStudio() {
        org.example.documentstudio.controller.DocumentStudioContext.selectMode(org.example.documentstudio.controller.DocumentStudioContext.Mode.PDF);
        openPage(btnPdfStudio, "PDF Studio", "/fxml/pages/DocumentStudio.fxml");
    }
    @FXML private void openExcelStudio() {
        org.example.documentstudio.controller.DocumentStudioContext.selectMode(org.example.documentstudio.controller.DocumentStudioContext.Mode.EXCEL);
        openPage(btnExcelStudio, "Excel Studio", "/fxml/pages/DocumentStudio.fxml");
    }
    @FXML private void openEmailCenter() {
        markCommunicationRead("EMAIL");
        CommunicationScreenContext.select("EMAIL");
        refreshEmailBadge();
        openPage(null, "Email Center", "/fxml/pages/CommunicationCenter.fxml");
    }

    /** Opens only WhatsApp delivery activity and clears its own unread badge. */
    @FXML private void openWhatsappCenter() {
        markCommunicationRead("WHATSAPP");
        CommunicationScreenContext.select("WHATSAPP");
        refreshWhatsappBadge();
        openPage(null, "WhatsApp Activity", "/fxml/pages/CommunicationCenter.fxml");
    }

    private void markCommunicationRead(String channel) {
        try { insightsApi.markCommunicationRead(channel); }
        catch (Exception ignored) { }
    }

    @FXML
    private void openReports() {
        openPage(btnReports,
            "Reports",
            "/fxml/pages/Reports.fxml");

    }

    @FXML
    private void openImport() {
        openPage(btnImport,
            "Import",
            "/fxml/pages/Import.fxml");

    }

    @FXML
    private void openSettings() {
        openSettingsSection(btnSettingsCompany, SettingsController.Section.COMPANY, "Company & Billing");
    }

    @FXML private void openSettingsCompany() {
        openSettingsSection(btnSettingsCompany, SettingsController.Section.COMPANY, "Company & Billing");
    }

    @FXML private void openSettingsPayment() {
        openSettingsSection(btnSettingsPayment, SettingsController.Section.PAYMENT, "Payment & Bank");
    }

    @FXML private void openSettingsInvoice() {
        openSettingsSection(btnSettingsInvoice, SettingsController.Section.INVOICE, "Invoice & Delivery");
    }

    @FXML private void openSettingsNotifications() {
        openSettingsSection(btnSettingsNotifications, SettingsController.Section.NOTIFICATIONS, "Notifications");
    }

    @FXML private void openSettingsEmail() {
        openSettingsSection(btnSettingsEmail, SettingsController.Section.EMAIL, "Email Settings");
    }

    @FXML private void openSettingsSecurity() {
        openSettingsSection(btnSettingsSecurity, SettingsController.Section.SECURITY, "Security & Session");
    }

    @FXML private void openSettingsWorkspace() {
        openSettingsSection(btnSettingsWorkspace, SettingsController.Section.WORKSPACE, "Workspace & Storage");
    }

    @FXML private void openSettingsShortcuts() {
        openSettingsSection(btnSettingsShortcuts, SettingsController.Section.SHORTCUTS, "Keyboard Shortcuts");
    }

    @FXML private void openSettingsUpdates() {
        openSettingsSection(btnSettingsUpdates, SettingsController.Section.UPDATES, "Application Updates");
    }

    private void openSettingsSection(Button selectedButton, SettingsController.Section section, String title) {
        SettingsController.requestSection(section);
        openPage(selectedButton, "Settings • " + title, "/fxml/pages/Settings.fxml");
    }

    @FXML
    private void openSafeRollback() {
        openPage(btnSafeRollback, "Safe Rollback", "/fxml/pages/SafeRollback.fxml");
    }

    @FXML
    private void openBackup() {
        openPage(btnBackup, "Backup & Restore", "/fxml/pages/BackupRestore.fxml");
    }

    @FXML
    private void openMasters() {

        openPage(
            btnMasters,
            "Masters",
            "/fxml/pages/Masterdata.fxml"
        );

    }

    @FXML
    private void search() {
        String query = txtSearch.getText() == null ? "" : txtSearch.getText().trim();
        if (query.isEmpty()) { txtSearch.requestFocus(); return; }
        GlobalSearchContext.open(query);
        openPage(null, "Global Search", "/fxml/pages/GlobalSearch.fxml");
    }

    /** Opens the selected result and preserves its document reference for detail screens. */

    /** Selects a semantic SVG icon for each search result module. */

    @FXML
    private void showNotifications() {
        openPage(null, "Notification Center", "/fxml/pages/NotificationCenter.fxml");
    }




    /** Refreshes the red unread counter beside the header notification button. */

    /** Refreshes the unread email-delivery activity badge in the application header. */
    private void refreshEmailBadge() {
        if (lblEmailBadge == null) return;
        int count = 0; try { count = insightsApi.shellCounts().email(); } catch (Exception ignored) { }
        lblEmailBadge.setText(count > 99 ? "99+" : Integer.toString(count));
        lblEmailBadge.setVisible(count > 0); lblEmailBadge.setManaged(count > 0);
    }

    /** Displays unread WhatsApp communication records in the shared application header. */
    private void refreshWhatsappBadge() {
        if (lblWhatsappBadge == null) return;
        int count = 0; try { count = insightsApi.shellCounts().whatsapp(); } catch (Exception ignored) { }
        lblWhatsappBadge.setText(count > 99 ? "99+" : Integer.toString(count));
        lblWhatsappBadge.setVisible(count > 0); lblWhatsappBadge.setManaged(count > 0);
    }

    /** Shows the number of currently open or overdue reminders in the header. */
    private void refreshReminderBadge() {
        if (lblReminderBadge == null) return;
        int count = 0; try { count = insightsApi.shellCounts().reminders(); } catch (Exception ignored) { }
        lblReminderBadge.setText(count > 99 ? "99+" : Integer.toString(count));
        lblReminderBadge.setVisible(count > 0); lblReminderBadge.setManaged(count > 0);
    }


    private void configureProfileMenuIcons() {
        if (menuUser == null) return;
        for (javafx.scene.control.MenuItem item : menuUser.getItems()) {
            if (item instanceof javafx.scene.control.SeparatorMenuItem) continue;
            String text = item.getText() == null ? "" : item.getText().toLowerCase(java.util.Locale.ROOT);
            String semantic = text.contains("profile") ? "user"
                : text.contains("setting") ? "settings"
                : text.contains("backup") ? "backup"
                : text.contains("reminder") ? "reminder"
                : text.contains("user access") ? "user"
                : text.contains("import") ? "import"
                : text.contains("document studio") ? "document"
                : text.contains("password") ? "lock"
                : text.contains("logout") ? "lock"
                : "document";
            item.setGraphic(IconFactory.compactIcon(semantic, 15));
        }
    }

    @FXML
    private void showProfile() {
        openPage(null, "My Profile", "/fxml/pages/Profile.fxml");
    }

    @FXML private void openBackupRestore() { openPage(null, "Backup & Restore", "/fxml/pages/BackupRestore.fxml"); }
    @FXML private void openDataImport() { openPage(null, "Data Import", "/fxml/pages/Import.fxml"); }

    @FXML
    private void changePassword() {
        if (SessionService.current() == null) {
            return;
        }

        Dialog<ButtonType> dialog = new OwnedDialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Set a new password");

        PasswordField newPassword = new PasswordField();
        PasswordField confirmPassword = new PasswordField();
        PasswordField currentPassword = new PasswordField();
        currentPassword.setPromptText("Current password");
        newPassword.setPromptText("New password");
        confirmPassword.setPromptText("Confirm new password");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Current password:"), currentPassword);
        form.addRow(1, new Label("New password:"), newPassword);
        form.addRow(2, new Label("Confirm password:"), confirmPassword);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> {
            String password = newPassword.getText();
            if (password.isBlank()) {
                new OwnedAlert(Alert.AlertType.WARNING, "Password cannot be empty.").showAndWait();
                return;
            }
            if (!password.equals(confirmPassword.getText())) {
                new OwnedAlert(Alert.AlertType.WARNING, "The passwords do not match.").showAndWait();
                return;
            }

            new UserService().changePassword(SessionService.current().getId(), currentPassword.getText(), password);
            NotificationService.add("Your account password was changed.");
            org.example.util.ToastManager.success(contentPane,"Password changed","Password changed successfully.");
        });
    }

    @FXML
    private void logout() {
        try { new org.example.service.UserService().logout(); }
        finally { SessionService.clear(); }
        org.example.util.SceneManager.showLogin();
    }

}
