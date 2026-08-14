package org.example.controller;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;



import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
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
import javafx.scene.Node;
import javafx.scene.Cursor;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.example.navigation.NavigationManager;
import org.example.theme.ThemeManager;
import org.example.util.ClockService;
import org.example.util.PerformanceMonitor;
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
import org.example.config.ConfigManager;
import org.example.api.insights.InsightsApiClient;

public class DashboardController {
    private static volatile DashboardController CURRENT;
    private final InsightsApiClient insightsApi = new InsightsApiClient();

    /** Periodically refreshes the unread badge while the main shell is open. */
    private Timeline notificationRefresh;
    private final AtomicBoolean indicatorRefreshRunning = new AtomicBoolean();


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
    @FXML
    private Button btnQuotation;
    @FXML
    private Button btnOperations;
    @FXML private Button btnBankExpense;
    @FXML private Button btnReminders;
    @FXML private Button btnUserAccess;
    @FXML private Button btnBackup;

    @FXML
    private Button btnReports;

    @FXML
    private Button btnSettings;

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

    @FXML
    private MenuButton menuUser;

    public void initialize() {
        CURRENT = this;

        ClockService.start(lblClock);
        // Company details do not change every second. Refreshing the complete
        // footer from the clock listener caused repeated ConfigManager reads and
        // layout pulses, especially on macOS Retina displays.
        refreshCompanyFooter();


        navigationManager = new NavigationManager(contentPane);

        if (navigationManager.loadPage("/fxml/pages/DashboardHome.fxml")) {
            lblPageTitle.setText("Dashboard");
            updateShellPageIcon("Dashboard");
            selectMenu(btnDashboard);
        }
        updateThemeButton();
        refreshShellIndicatorsAsync();
        if (SessionService.current() != null) {
            menuUser.setText(SessionService.current().getFullName());
            if (lblSidebarUser != null) lblSidebarUser.setText(SessionService.current().getFullName());
        }
        configureProfileMenuIcons();
        Platform.runLater(this::bindShellControls);
        applyRolePermissions();
        notificationRefresh = new Timeline(
            new KeyFrame(Duration.seconds(60), event -> { PerformanceMonitor.event("recurring-task", "shell-indicators"); refreshShellIndicatorsAsync(); }));
        notificationRefresh.setCycleCount(Timeline.INDEFINITE);
        notificationRefresh.play();

    }


    /** Keeps the shared shell readable with macOS font metrics and Retina scaling. */
    private void installResponsiveShellSizing() {
        if (contentPane.getScene() == null) return;
        Runnable resize = () -> {
            double width = contentPane.getScene().getWidth();
            double searchWidth = Math.max(150, Math.min(390, width * (PlatformUiSupport.isMac() ? 0.17 : 0.20)));
            txtSearch.setPrefWidth(searchWidth);
            if (sidebarRoot != null) sidebarRoot.setPrefWidth(width < 1050 ? 164 : width < 1250 ? 178 : PlatformUiSupport.isMac() ? 198 : 215);
            if (menuUser != null) {
                menuUser.setMaxWidth(width < 1400 ? 135 : 175);
                PlatformUiSupport.configureTextOverflow(menuUser);
            }
            if (lblCompanyFooter != null) PlatformUiSupport.configureTextOverflow(lblCompanyFooter);
        };
        contentPane.getScene().widthProperty().addListener((obs, oldValue, newValue) -> resize.run());
        contentPane.getScene().heightProperty().addListener((obs, oldValue, newValue) -> resize.run());
        resize.run();
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
        protect(btnSettings, "SETTINGS.VIEW"); protect(btnImport, "IMPORT.VIEW");
    }

    private void protect(Button button, String permission) {
        if (button != null) button.setDisable(!PermissionService.allowed(permission));
    }

    private void bindShellControls() {
        if (contentPane.getScene() == null) return;
        PlatformUiSupport.installResponsiveClasses(contentPane.getScene());
        installResponsiveShellSizing();
        contentPane.getScene().getAccelerators().put(
            new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN),
            () -> { txtSearch.requestFocus(); txtSearch.selectAll(); });
        installShortcut(KeyCode.F2, "SALES.VIEW", () -> NavigationManager.getInstance().loadPage("/fxml/pages/Sale.fxml"));
        installShortcut(KeyCode.F3, "QUOTATION.VIEW", this::createQuotationFromShortcut);
        installShortcut(KeyCode.F4, "INVENTORY.VIEW", this::openItemMaster);
        installShortcut(KeyCode.F5, "MASTERS.VIEW", this::openMasters);
        installShortcut(KeyCode.F6, null, this::openBankStatement);
        installShortcut(KeyCode.F7, null, this::openBankEntry);
        installShortcut(KeyCode.F8, null, this::openExpenseEntry);
        Node sidebarUser = contentPane.getScene().lookup(".sidebar-user");
        if (sidebarUser != null) {
            sidebarUser.setCursor(Cursor.HAND);
            sidebarUser.setOnMouseClicked(e -> showProfile());
        }
        for(Node node:contentPane.getScene().getRoot().lookupAll(".button")) if(node instanceof Button b) applyIcon(b);
        for (Node node : contentPane.getScene().getRoot().lookupAll(".toolbar-menu")) {
            if (node instanceof Button button) button.setGraphic(IconFactory.icon("menu"));
        }
        if (btnReminderTop != null) btnReminderTop.setGraphic(IconFactory.icon("reminder"));
        if (btnNotifications != null) btnNotifications.setGraphic(IconFactory.icon("notification"));
        if (btnEmailCenter != null) btnEmailCenter.setGraphic(IconFactory.icon("email"));
        if (btnWhatsappCenter != null) btnWhatsappCenter.setGraphic(IconFactory.icon("whatsapp"));
        if (btnShortcutInfo != null) btnShortcutInfo.setGraphic(IconFactory.icon("info"));
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
        String[][] entries = {
            {"F2", "New Sale"},
            {"F3", "New Quotation"},
            {"F4", "Item Master"},
            {"F5", "Masters"},
            {"F6", "Bank Statement"},
            {"F7", "Bank Entry"},
            {"F8", "Expense Entry"}
        };
        for (int row = 0; row < entries.length; row++) {
            Label key = new Label(entries[row][0]);
            key.getStyleClass().add("shortcut-info-key");
            Label name = new Label(entries[row][1]);
            name.getStyleClass().add("shortcut-info-name");
            shortcuts.add(key, 0, row);
            shortcuts.add(name, 1, row);
        }
        VBox content = new VBox(12,
            new Label("Press a function key to open the related workspace:"), shortcuts);
        content.getStyleClass().add("shortcut-info-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void installShortcut(KeyCode key, String permission, Runnable action) {
        contentPane.getScene().getAccelerators().put(new KeyCodeCombination(key), () -> {
            if (permission == null || PermissionService.allowed(permission)) action.run();
        });
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
            : text.contains("report") ? "report"
            : text.contains("email") || text.contains("communication") ? "email"
            : text.contains("notification") ? "notification"
            : text.contains("reminder") ? "reminder"
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

    private void selectMenu(Button button) {
        clearSelection();
        if (button != null && !button.getStyleClass().contains("menu-selected")) button.getStyleClass().add("menu-selected");
    }


    private void clearSelection() {

        if (btnDashboard != null)
            btnDashboard.getStyleClass().remove("menu-selected");

        if (btnItem != null)
            btnItem.getStyleClass().remove("menu-selected");

        if (btnMasters != null)
            btnMasters.getStyleClass().remove("menu-selected");

        if (btnCustomer != null)
            btnCustomer.getStyleClass().remove("menu-selected");

        if (btnSupplier != null)
            btnSupplier.getStyleClass().remove("menu-selected");

        if (btnInventory != null)
            btnInventory.getStyleClass().remove("menu-selected");

        if (btnPurchase != null)
            btnPurchase.getStyleClass().remove("menu-selected");

        if (btnSales != null)
            btnSales.getStyleClass().remove("menu-selected");
        if (btnQuotation != null)
            btnQuotation.getStyleClass().remove("menu-selected");
        if (btnOperations != null)
            btnOperations.getStyleClass().remove("menu-selected");
        if (btnBankExpense != null) btnBankExpense.getStyleClass().remove("menu-selected");
        if (btnReminders != null) btnReminders.getStyleClass().remove("menu-selected");
        if (btnUserAccess != null) btnUserAccess.getStyleClass().remove("menu-selected");
        if (btnBackup != null) btnBackup.getStyleClass().remove("menu-selected");

        if (btnReports != null)
            btnReports.getStyleClass().remove("menu-selected");

        if (btnImport != null)
            btnImport.getStyleClass().remove("menu-selected");

        if (btnSettings != null)
            btnSettings.getStyleClass().remove("menu-selected");
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

        if (navigationManager.loadPage(fxmlPath)) {
            selectMenu(button);
            lblPageTitle.setText(pageTitle);
            updateShellPageIcon(pageTitle);
            if (lblBreadcrumb != null) {
                lblBreadcrumb.setText(pageTitle.equals("Dashboard")
                    ? "Welcome back, " + currentUserName() + "!"
                    : "ERP  >  " + pageTitle);
            }
        }
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
        openPage(btnPurchase,
            "Purchase",
            "/fxml/pages/PurchaseList.fxml");

    }

    @FXML
    private void openSales() {
        openPage(btnSales,
            "Sales",
            "/fxml/pages/SalesList.fxml");

    }

    @FXML
    private void openQuotations() {
        openPage(btnQuotation, "Quotation Register", "/fxml/pages/Quotations.fxml");
    }

    @FXML
    private void openOperations() {
        openPage(btnOperations, "Returns, Finance & Reminders", "/fxml/pages/Operations.fxml");
    }

    @FXML private void openBankEntry() {
        BankExpenseController.requestMode(BankExpenseController.Mode.BANK);
        openPage(btnBankExpense, "Bank Entry", "/fxml/pages/BankExpense.fxml");
    }

    @FXML private void openExpenseEntry() {
        BankExpenseController.requestMode(BankExpenseController.Mode.EXPENSE);
        openPage(btnBankExpense, "Expense Entry", "/fxml/pages/BankExpense.fxml");
    }

    @FXML private void openBankStatement() {
        openPage(btnBankExpense, "Bank Statement", "/fxml/pages/BankStatement.fxml");
    }

    /** Lets administration child pages navigate inside the existing ERP shell. */
    public static void navigateFromChildPage(String title, String fxmlPath) {
        DashboardController c = CURRENT;
        if (c == null) return;
        javafx.application.Platform.runLater(() -> c.openPage(c.btnUserAccess, title, fxmlPath));
    }

    /** Lets a feature page navigate through the existing cached ERP shell. */
    public static void navigateFromChild(String title, String fxmlPath, BankExpenseController.Mode mode) {
        DashboardController c = CURRENT;
        if (c == null) return;
        if (mode != null) BankExpenseController.requestMode(mode);
        javafx.application.Platform.runLater(() -> c.openPage(c.btnBankExpense, title, fxmlPath));
    }

    @FXML private void createSale() { openPage(btnSales, "Create Sale", "/fxml/pages/Sale.fxml"); }
    @FXML private void createPurchase() { openPage(btnPurchase, "Create Purchase", "/fxml/pages/Purchase.fxml"); }
    @FXML private void openReturns() { openPage(btnSales, "Sales Return Register", "/fxml/pages/SalesReturns.fxml"); }
    @FXML private void openPurchaseReturns() { openPage(btnPurchase, "Purchase Return", "/fxml/pages/PurchaseReturns.fxml"); }
    @FXML private void openFinance() { OperationsController.selectInitialTab(1); openOperations(); }
    @FXML private void openReminders() { OperationsController.selectInitialTab(2); openOperations(); }
    @FXML private void openReminderCenter() {
        openPage(btnReminders, "Reminder Center", "/fxml/pages/ReminderCenter.fxml");
        refreshReminderBadge();
    }
    @FXML private void openUserAccess() { openPage(btnUserAccess, "User Access & Permissions", "/fxml/pages/UserAccess.fxml"); }
    @FXML private void openCommunication() {
        CommunicationScreenContext.select(null);
        openPage(null, "Communication Center", "/fxml/pages/CommunicationCenter.fxml");
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
        openPage(btnSettings,
            "Settings",
            "/fxml/pages/Settings.fxml");

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
        if (query.isEmpty()) {
            new OwnedAlert(Alert.AlertType.INFORMATION,
                "Enter a document number, party, item, payment, return, quotation or master value.").showAndWait();
            return;
        }
        List<SearchResult> results = new GlobalSearchService().search(query);
        Dialog<ButtonType> dialog = new OwnedDialog<>();
        dialog.setTitle("ERP Search");
        dialog.setHeaderText(results.isEmpty() ? "No results for '" + query + "'"
            : results.size() + " result(s) for '" + query + "'");
        ListView<SearchResult> list = new ListView<>();
        list.getItems().setAll(results);
        list.setPrefSize(720, 460);
        list.getStyleClass().add("global-search-results");
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(SearchResult result, boolean empty) {
                super.updateItem(result, empty);
                setText(empty || result == null ? null : result.toString());
                setGraphic(empty || result == null ? null : IconFactory.icon(iconForModule(result.module())));
            }
        });
        ButtonType open = new ButtonType("Open Result", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(list);
        dialog.getDialogPane().getButtonTypes().addAll(open, ButtonType.CLOSE);
        dialog.getDialogPane().lookupButton(open).disableProperty()
            .bind(list.getSelectionModel().selectedItemProperty().isNull());
        dialog.showAndWait().filter(button -> button == open).ifPresent(button -> {
            SearchResult selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) openSearchResult(selected);
        });
    }

    /** Opens the selected result and preserves its document reference for detail screens. */
    private void openSearchResult(SearchResult result) {
        if (result.module().equals("Sales Invoice")) SalesScreenContext.select(result.reference());
        if (result.module().equals("Purchase Invoice")) PurchaseScreenContext.select(result.reference());
        openPage(null, result.module(), result.targetFxml());
    }

    /** Selects a semantic SVG icon for each search result module. */
    private String iconForModule(String module) {
        String value = module == null ? "" : module.toLowerCase(Locale.ROOT);
        if (value.contains("sale")) return "sale";
        if (value.contains("purchase")) return "purchase";
        if (value.contains("customer")) return "customer";
        if (value.contains("supplier")) return "supplier";
        if (value.contains("payment")) return "payment";
        if (value.contains("return")) return "return";
        if (value.contains("quotation")) return "quotation";
        if (value.contains("master")) return "master";
        return "item";
    }

    @FXML
    private void showNotifications() {
        Dialog<ButtonType> dialog = new OwnedDialog<>();
        dialog.initStyle(StageStyle.TRANSPARENT);
        if (btnNotifications != null && btnNotifications.getScene() != null
                && btnNotifications.getScene().getWindow() != null) {
            dialog.initOwner(btnNotifications.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
        } else {
            dialog.initModality(Modality.APPLICATION_MODAL);
        }

        DialogPane pane = dialog.getDialogPane();
        pane.getProperties().put("erp-dialog-custom", true);
        pane.getStyleClass().addAll("modern-dialog", "notification-center-dialog");
        pane.setHeaderText(null);
        pane.setGraphic(null);

        // Register a real cancel/close result so JavaFX always releases the
        // application-modal showAndWait() loop. The native button remains
        // hidden because this dialog supplies its own styled Close controls.
        pane.getButtonTypes().add(ButtonType.CLOSE);
        Node nativeCloseButton = pane.lookupButton(ButtonType.CLOSE);
        nativeCloseButton.setVisible(false);
        nativeCloseButton.setManaged(false);
        Runnable closeDialog = () -> {
            dialog.setResult(ButtonType.CLOSE);
            dialog.close();
        };

        ListView<NotificationService.NotificationItem> notificationList = new ListView<>();
        List<NotificationService.NotificationItem> allNotifications = new ArrayList<>(NotificationService.findRecent(100));
        notificationList.getItems().setAll(allNotifications);
        notificationList.setPlaceholder(new Label("You are all caught up. New sales, payments, returns and reminders will appear here."));
        notificationList.getStyleClass().add("notification-list");
        notificationList.setPrefWidth(1040);
        notificationList.setPrefHeight(540);
        notificationList.setMinHeight(260);
        notificationList.setMaxHeight(520);

        notificationList.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(NotificationService.NotificationItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label title = new Label(item.title());
                    title.getStyleClass().add("notification-row-title");
                    Label message = new Label(item.message());
                    message.setWrapText(true);
                    message.getStyleClass().add("notification-row-message");
                    Instant created = Instant.ofEpochMilli(item.createdAt());
                    Label time = new Label(DateTimeFormatter.ofPattern("hh:mm a").withZone(ZoneId.systemDefault()).format(created));
                    time.getStyleClass().add("notification-row-time");
                    Label date = new Label(DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault()).format(created));
                    date.getStyleClass().add("notification-row-date");
                    Label category = new Label(displayNotificationCategory(item.category()));
                    category.getStyleClass().addAll("notification-category-chip", "notification-category-" + safeNotificationCategory(item.category()).toLowerCase(Locale.ROOT));
                    VBox stamps = new VBox(3, time, date);
                    stamps.setAlignment(Pos.CENTER_RIGHT);
                    Label fresh = new Label("New");
                    fresh.getStyleClass().add("notification-new-badge");
                    fresh.setVisible(!item.read()); fresh.setManaged(!item.read());
                    Label reference = new Label(item.referenceNo() == null || item.referenceNo().isBlank()
                            ? "" : "Reference: " + item.referenceNo());
                    reference.getStyleClass().add("notification-row-reference");
                    reference.setVisible(!reference.getText().isBlank());
                    reference.setManaged(reference.isVisible());
                    HBox metadata = new HBox(8, category, reference);
                    metadata.setAlignment(Pos.CENTER_LEFT);
                    VBox text = new VBox(4, title, message, metadata);
                    HBox.setHgrow(text, Priority.ALWAYS);
                    String semantic = notificationSemantic(item);
                    VBox right = new VBox(5, stamps, fresh); right.setAlignment(Pos.CENTER_RIGHT);
                    StackPane icon = new StackPane(IconFactory.icon(semantic, 28));
                    icon.getStyleClass().addAll("notification-timeline-icon", "notification-icon-" + semantic);
                    HBox row = new HBox(16, icon, text, right);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("notification-row-content");
                    setGraphic(row);
                }
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("unread"),
                    !empty && item != null && !item.read());
            }
        });
        notificationList.setOnMouseClicked(event -> {
            if (event.getClickCount() != 2) return;
            NotificationService.NotificationItem item = notificationList.getSelectionModel().getSelectedItem();
            if (item == null) return;
            NotificationService.markRead(item.id());
            if (item.targetFxml() != null && !item.targetFxml().isBlank())
                openPage(null, item.title(), item.targetFxml());
            refreshNotificationBadge();
        });

        TextField notificationSearch = new TextField();
        notificationSearch.setPromptText("Search title, message or reference...");
        notificationSearch.setPrefWidth(330);
        notificationSearch.getStyleClass().add("notification-search");
        ComboBox<String> viewFilter = new ComboBox<>();
        viewFilter.getItems().setAll("All Notifications", "Unread", "Action Needed", "Sales", "Purchases", "Quotations", "Payments", "Inventory", "Security", "System");
        viewFilter.setValue("All Notifications");
        viewFilter.setPrefWidth(150);
        Runnable applyNotificationFilter = () -> {
            String query = notificationSearch.getText() == null ? "" : notificationSearch.getText().trim().toLowerCase(Locale.ROOT);
            String mode = viewFilter.getValue();
            notificationList.getItems().setAll(allNotifications.stream().filter(item -> {
                if ("Unread".equals(mode) && item.read()) return false;
                String severity = item.severity() == null ? "INFO" : item.severity().toUpperCase(Locale.ROOT);
                if ("Action Needed".equals(mode) && !List.of("WARN", "ERROR", "CRITICAL", "FATAL").contains(severity)) return false;
                if (!List.of("All Notifications", "Unread", "Action Needed").contains(mode)
                    && !safeNotificationCategory(item.category()).equalsIgnoreCase(mode)) return false;
                String haystack = (String.valueOf(item.title()) + " " + String.valueOf(item.message()) + " " + String.valueOf(item.referenceNo())).toLowerCase(Locale.ROOT);
                return query.isBlank() || haystack.contains(query);
            }).toList());
        };
        notificationSearch.textProperty().addListener((o,a,b)->applyNotificationFilter.run());
        viewFilter.valueProperty().addListener((o,a,b)->applyNotificationFilter.run());
        Label filterLabel = new Label("View:");
        filterLabel.getStyleClass().add("notification-filter-label");
        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);
        Button filterButton = new Button();
        filterButton.setGraphic(IconFactory.compactIcon("filter", 18));
        filterButton.getStyleClass().addAll("approved-button", "approved-secondary-button", "notification-filter-button");
        filterButton.setOnAction(event -> applyNotificationFilter.run());
        HBox filters = new HBox(10, notificationSearch, filterSpacer, filterLabel, viewFilter, filterButton);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.getStyleClass().add("notification-filter-bar");

        Label title = new Label("Notifications");
        title.getStyleClass().add("modern-dialog-title");
        Label subtitle = new Label("Recent application activity");
        subtitle.setText("Stay updated with the latest activities and alerts");
        subtitle.getStyleClass().add("notification-dialog-subtitle");
        VBox heading = new VBox(2, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeTop = new Button("×");
        closeTop.getStyleClass().add("modern-dialog-close");
        closeTop.setOnAction(event -> closeDialog.run());
        Label unreadHeader = new Label(NotificationService.unreadCount() + "\nUnread");
        unreadHeader.getStyleClass().add("notification-unread-header");
        HBox titleBar = new HBox(10, IconFactory.icon("notification", 34), heading, spacer, unreadHeader, closeTop);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("modern-dialog-titlebar");

        Button markAll = new Button("Mark all read");
        markAll.setGraphic(IconFactory.compactIcon("complete", 16));
        markAll.getProperties().put("erp.icon.semantic", "complete");
        markAll.setOnAction(event -> {
            NotificationService.markAllRead();
            allNotifications.clear();
            allNotifications.addAll(NotificationService.findRecent(100));
            applyNotificationFilter.run();
            refreshNotificationBadge();
            unreadHeader.setText("0\nUnread");
        });
        Button clear = new Button("Clear history");
        clear.setGraphic(IconFactory.compactIcon("delete", 16));
        clear.getProperties().put("erp.icon.semantic", "delete");
        clear.setOnAction(event -> {
            Alert confirmation = new OwnedAlert(Alert.AlertType.CONFIRMATION,
                "Clear the complete notification history? This cannot be undone.");
            confirmation.setHeaderText("Confirm notification cleanup");
            confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(result -> {
                NotificationService.clear();
                allNotifications.clear();
                notificationList.getItems().clear();
                refreshNotificationBadge();
            });
        });
        Button close = new Button("Close");
        close.setGraphic(IconFactory.compactIcon("cancel", 16));
        close.getProperties().put("erp.icon.semantic", "cancel");
        close.setOnAction(event -> closeDialog.run());
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        HBox actions = new HBox(10, markAll, clear, actionSpacer, close);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("notification-dialog-actions");

        VBox content = new VBox(titleBar, filters, notificationList, actions);
        content.getStyleClass().add("notification-dialog-content");
        pane.setContent(content);
        dialog.setOnCloseRequest(event -> dialog.setResult(ButtonType.CLOSE));
        dialog.setOnShown(event -> {
            Scene scene = pane.getScene();
            if (scene != null) {
                scene.setFill(Color.TRANSPARENT);
                ThemeManager.applyTheme(scene);
            }
        });
        dialog.showAndWait();
        refreshNotificationBadge();
    }

    private static String safeNotificationCategory(String category) {
        return category == null || category.isBlank() || "GENERAL".equalsIgnoreCase(category) ? "System" : category;
    }

    private static String displayNotificationCategory(String category) {
        String value = safeNotificationCategory(category).replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String notificationSemantic(NotificationService.NotificationItem item) {
        String severity = item.severity() == null ? "INFO" : item.severity().toUpperCase(Locale.ROOT);
        if (List.of("ERROR", "CRITICAL", "FATAL").contains(severity)) return "error";
        if ("WARN".equals(severity)) return "warning";
        return switch (safeNotificationCategory(item.category()).toUpperCase(Locale.ROOT)) {
            case "SALES" -> "sale"; case "PURCHASES" -> "purchase"; case "QUOTATIONS" -> "quotation";
            case "PAYMENTS" -> "payment"; case "INVENTORY" -> "inventory"; case "SECURITY" -> "security";
            case "BACKUP" -> "backup"; case "UPDATE" -> "refresh"; default -> "notification";
        };
    }

    /** Refreshes the red unread counter beside the header notification button. */
    private void refreshNotificationBadge() {
        if (lblNotificationBadge == null) return;
        int count = NotificationService.unreadCount();
        lblNotificationBadge.setText(count > 99 ? "99+" : Integer.toString(count));
        lblNotificationBadge.setVisible(count > 0);
        lblNotificationBadge.setManaged(count > 0);
    }

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
        String[] icons = {"user","settings","backup","reminder","users","import",null,"lock",null,"logout"};
        int index = 0;
        for (javafx.scene.control.MenuItem item : menuUser.getItems()) {
            if (item instanceof javafx.scene.control.SeparatorMenuItem) { index++; continue; }
            String semantic = index < icons.length ? icons[index] : "document";
            if (semantic != null) item.setGraphic(IconFactory.compactIcon(semantic, 15));
            index++;
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
            new OwnedAlert(Alert.AlertType.INFORMATION, "Password changed successfully.").showAndWait();
        });
    }

    @FXML
    private void logout() {
        try { new org.example.service.UserService().logout(); }
        finally { SessionService.clear(); }
        org.example.util.SceneManager.showLogin();
    }

}
