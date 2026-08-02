package org.example.controller;



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

import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.example.service.SessionService;
import org.example.service.UserService;
import org.example.service.NotificationService;
import org.example.service.GlobalSearchService;
import org.example.service.GlobalSearchService.SearchResult;
import org.example.service.PermissionService;
import org.example.util.IconFactory;
import org.example.config.ConfigManager;

public class DashboardController {

    /** Periodically refreshes the unread badge while the main shell is open. */
    private Timeline notificationRefresh;


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

    @FXML
    private MenuButton menuUser;

    public void initialize() {

        ClockService.start(lblClock);
        refreshCompanyFooter();


        navigationManager = new NavigationManager(contentPane);

        if (navigationManager.loadPage("/fxml/pages/DashboardHome.fxml")) {
            lblPageTitle.setText("Dashboard");
            selectMenu(btnDashboard);
        }
        updateThemeButton();
        refreshNotificationBadge();
        refreshEmailBadge();
        refreshWhatsappBadge();
        refreshReminderBadge();
        if (SessionService.current() != null) {
            menuUser.setText(SessionService.current().getFullName());
            if (lblSidebarUser != null) lblSidebarUser.setText(SessionService.current().getFullName());
        }
        Platform.runLater(this::bindShellControls);
        applyRolePermissions();
        notificationRefresh = new Timeline(
            new KeyFrame(Duration.seconds(2), event -> {
                refreshNotificationBadge();
                refreshEmailBadge();
                refreshWhatsappBadge();
                refreshReminderBadge();
                refreshCompanyFooter();
            }));
        notificationRefresh.setCycleCount(Timeline.INDEFINITE);
        notificationRefresh.play();

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
        contentPane.getScene().getAccelerators().put(
            new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN),
            () -> { txtSearch.requestFocus(); txtSearch.selectAll(); });
        // ERP keyboard shortcut: F2 opens a new sales invoice from every shell page.
        contentPane.getScene().getAccelerators().put(
            new KeyCodeCombination(KeyCode.F2),
            () -> NavigationManager.getInstance().loadPage("/fxml/pages/Sale.fxml"));
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
        menuUser.setGraphic(IconFactory.icon("user"));
    }

    /** Applies the shared vector icon vocabulary to shell and navigation buttons. */
    private void applyIcon(Button button) {
        String text = button.getText() == null ? "" : button.getText().toLowerCase(Locale.ROOT);
        String icon = text.contains("dashboard") ? "dashboard"
            : text.contains("import") ? "import"
            : text.contains("sale") ? "sale"
            : text.contains("purchase") ? "purchase"
            : text.contains("inventory") ? "inventory"
            : text.contains("item") ? "item"
            : text.contains("master") ? "master"
            : text.contains("customer") || text.contains("crm") ? "customer"
            : text.contains("supplier") || text.contains("hrm") ? "supplier"
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

    private void openPage(Button button,
                          String pageTitle,
                          String fxmlPath) {

        if (navigationManager.loadPage(fxmlPath)) {
            selectMenu(button);
            lblPageTitle.setText(pageTitle);
            if (lblBreadcrumb != null) {
                lblBreadcrumb.setText(pageTitle.equals("Dashboard")
                    ? "Welcome back, Admin!"
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
        try (java.sql.Connection connection = org.example.database.DatabaseManager.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                 "UPDATE communication_log SET is_read=1 WHERE channel=?")) {
            statement.setString(1, channel);
            statement.executeUpdate();
        } catch (Exception ignored) {
            // A delivery screen must remain available even when badge maintenance fails.
        }
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
            new Alert(Alert.AlertType.INFORMATION,
                "Enter a document number, party, item, payment, return, quotation or master value.").showAndWait();
            return;
        }
        List<SearchResult> results = new GlobalSearchService().search(query);
        Dialog<ButtonType> dialog = new Dialog<>();
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
        Dialog<ButtonType> dialog = new Dialog<>();
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
        notificationList.getItems().setAll(NotificationService.findRecent(100));
        notificationList.setPlaceholder(new Label("You are all caught up. New sales, payments, returns and reminders will appear here."));
        notificationList.getStyleClass().add("notification-list");
        notificationList.setPrefWidth(620);
        notificationList.setPrefHeight(380);
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
                    String timestamp = DateTimeFormatter.ofPattern("dd MMM yyyy  hh:mm a")
                        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.createdAt()));
                    Label time = new Label(timestamp);
                    time.getStyleClass().add("notification-row-time");
                    VBox text = new VBox(3, title, message, time);
                    HBox.setHgrow(text, Priority.ALWAYS);
                    String semantic = "ERROR".equalsIgnoreCase(item.severity()) ? "error"
                        : "WARN".equalsIgnoreCase(item.severity()) ? "warning" : "notification";
                    HBox row = new HBox(12, IconFactory.icon(semantic, 26), text);
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

        Label title = new Label("Notifications");
        title.getStyleClass().add("modern-dialog-title");
        Label subtitle = new Label("Recent application activity");
        subtitle.getStyleClass().add("notification-dialog-subtitle");
        VBox heading = new VBox(2, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeTop = new Button("×");
        closeTop.getStyleClass().add("modern-dialog-close");
        closeTop.setOnAction(event -> closeDialog.run());
        HBox titleBar = new HBox(10, IconFactory.icon("notification", 28), heading, spacer, closeTop);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("modern-dialog-titlebar");

        Button markAll = new Button("Mark all read");
        markAll.setGraphic(IconFactory.compactIcon("complete", 16));
        markAll.getProperties().put("erp.icon.semantic", "complete");
        markAll.setOnAction(event -> {
            NotificationService.markAllRead();
            notificationList.getItems().setAll(NotificationService.findRecent(100));
            refreshNotificationBadge();
        });
        Button clear = new Button("Clear history");
        clear.setGraphic(IconFactory.compactIcon("delete", 16));
        clear.getProperties().put("erp.icon.semantic", "delete");
        clear.setOnAction(event -> {
            NotificationService.clear();
            notificationList.getItems().clear();
            refreshNotificationBadge();
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

        VBox content = new VBox(titleBar, notificationList, actions);
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
        int count = 0;
        try (java.sql.Connection connection = org.example.database.DatabaseManager.getConnection();
             java.sql.Statement statement = connection.createStatement();
             java.sql.ResultSet result = statement.executeQuery(
                 "SELECT COUNT(*) FROM communication_log WHERE channel='EMAIL' AND COALESCE(is_read,0)=0")) {
            if (result.next()) count = result.getInt(1);
        } catch (Exception ignored) {
        }
        lblEmailBadge.setText(count > 99 ? "99+" : Integer.toString(count));
        lblEmailBadge.setVisible(count > 0);
        lblEmailBadge.setManaged(count > 0);
    }

    /** Displays unread WhatsApp communication records in the shared application header. */
    private void refreshWhatsappBadge() {
        if (lblWhatsappBadge == null) return;
        int count = 0;
        try (java.sql.Connection connection = org.example.database.DatabaseManager.getConnection();
             java.sql.Statement statement = connection.createStatement();
             java.sql.ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM communication_log WHERE channel='WHATSAPP' AND COALESCE(is_read,0)=0")) {
            if (result.next()) count = result.getInt(1);
        } catch (Exception ignored) { }
        lblWhatsappBadge.setText(count > 99 ? "99+" : Integer.toString(count));
        lblWhatsappBadge.setVisible(count > 0);
        lblWhatsappBadge.setManaged(count > 0);
    }

    /** Shows the number of currently open or overdue reminders in the header. */
    private void refreshReminderBadge() {
        if (lblReminderBadge == null) return;
        int count = 0;
        try (java.sql.Connection connection = org.example.database.DatabaseManager.getConnection();
             java.sql.Statement statement = connection.createStatement();
             java.sql.ResultSet result = statement.executeQuery(
                 "SELECT COUNT(*) FROM reminder_register WHERE status IN ('OPEN','SNOOZED')")) {
            if (result.next()) count = result.getInt(1);
        } catch (Exception ignored) {
        }
        lblReminderBadge.setText(count > 99 ? "99+" : Integer.toString(count));
        lblReminderBadge.setVisible(count > 0);
        lblReminderBadge.setManaged(count > 0);
    }

    @FXML
    private void showProfile() {
        openPage(null, "My Profile", "/fxml/pages/Profile.fxml");
    }

    @FXML
    private void changePassword() {
        if (SessionService.current() == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Set a new password");

        PasswordField newPassword = new PasswordField();
        PasswordField confirmPassword = new PasswordField();
        newPassword.setPromptText("New password");
        confirmPassword.setPromptText("Confirm new password");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("New password:"), newPassword);
        form.addRow(1, new Label("Confirm password:"), confirmPassword);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> {
            String password = newPassword.getText();
            if (password.isBlank()) {
                new Alert(Alert.AlertType.WARNING, "Password cannot be empty.").showAndWait();
                return;
            }
            if (!password.equals(confirmPassword.getText())) {
                new Alert(Alert.AlertType.WARNING, "The passwords do not match.").showAndWait();
                return;
            }

            new UserService().changePassword(SessionService.current().getId(), password);
            NotificationService.add("Your account password was changed.");
            new Alert(Alert.AlertType.INFORMATION, "Password changed successfully.").showAndWait();
        });
    }

    @FXML
    private void logout() {
        SessionService.clear();
        org.example.util.SceneManager.showLogin();
    }

}
