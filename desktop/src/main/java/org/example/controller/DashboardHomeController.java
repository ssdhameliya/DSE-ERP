package org.example.controller;

import org.example.util.BusinessClock;

import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import org.example.util.IconFactory;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ProgressIndicator;
import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;

import org.example.config.ConfigManager;
import org.example.api.insights.InsightsApiClient;
import org.example.navigation.NavigationManager;
import org.example.navigation.ScreenLifecycle;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class DashboardHomeController implements ScreenLifecycle {
    private String fallbackPeriod = "This Month";
    public DashboardHomeController() {
        // JavaFX creates the controller before injecting the FXML controls.
    }
    @FXML private Label lblSalesValue, lblPurchaseValue, lblStockValue, lblLowStock;
    @FXML private Label lblSalesNote, lblPurchaseNote, lblProductsNote, lblStockNote;
    @FXML private Label lblReceivableNote;
    @FXML private Label lblCustomers, lblProducts, lblOrders, lblPurchases;
    @FXML private Label lblCash;
    @FXML private Label lblLowStockValue, lblLowStockNote, lblTrendSales, lblReminderCount, lblReminderSummary;
    @FXML private Label lblTrendPeriod, lblComparisonPeriod;
    @FXML private ComboBox<String> cmbPeriod;
    @FXML private ListView<String> topCustomerList, agingList, activityList;
    @FXML private TableView<ActivityRow> recentTable;
    @FXML private TableColumn<ActivityRow, String> colType, colNumber, colParty, colDate, colAmount;
    @FXML private StackPane salesKpiIcon, purchaseKpiIcon, receivableKpiIcon, payableKpiIcon, cashKpiIcon, lowStockKpiIcon, dashboardTitleIcon, customersMiniIcon, itemsMiniIcon, salesMiniIcon, purchasesMiniIcon;
    @FXML private StackPane dashboardRoot, loadingOverlay;
    @FXML private Node dashboardContent;
    @FXML private Label loadingMessage;
    @FXML private ProgressIndicator loadingProgress;
    private final AtomicBoolean dashboardLoadRunning = new AtomicBoolean();
    @FXML private Button quickSale, quickPurchase, quickQuotation, quickPayment,
        quickCustomer, quickSupplier, quickBank, quickExpense, refreshDashboardButton;

    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    private final InsightsApiClient insightsApi = new InsightsApiClient();

    @FXML
    public void initialize() {
        configureExplicitTableHeaderIcons();
        installKpiIcons();
        installQuickActionIcons();
        installDashboardHeaderIcons();
        installColorfulDashboardLists();
        if (cmbPeriod != null) {
            cmbPeriod.getItems().setAll("This Month", "This Quarter", "This Year", "All Time");
            cmbPeriod.setValue("This Month");
            cmbPeriod.valueProperty().addListener((obs, oldValue, value) -> reload());
        }
        ensureQuickActionsVisible();
        reload();
    }

    /** Installs vector KPI symbols so no platform-dependent emoji can disappear. */
    private void installKpiIcons() {
        setIcon(salesKpiIcon, "report");
        setIcon(purchaseKpiIcon, "purchase");
        setIcon(receivableKpiIcon, "payment");
        setIcon(payableKpiIcon, "document");
        setIcon(cashKpiIcon, "payment");
        setIcon(lowStockKpiIcon, "item");
        setIcon(customersMiniIcon, "customer");
        setIcon(itemsMiniIcon, "item");
        setIcon(salesMiniIcon, "sales");
        setIcon(purchasesMiniIcon, "purchase");
    }

    private void setIcon(StackPane target, String icon) {
        if (target != null) target.getChildren().setAll(IconFactory.icon(icon, 28));
    }

    private void installDashboardHeaderIcons() {
        setIcon(dashboardTitleIcon, "dashboard");
        if (refreshDashboardButton != null) {
            refreshDashboardButton.setGraphic(IconFactory.icon("refresh", 16));
            refreshDashboardButton.getProperties().put("erp-icon-preserve", true);
        }
    }

    /** Gives every quick action a meaningful, theme-aware vector icon. */
    private void installQuickActionIcons() {
        setButtonIcon(quickSale, "sale");
        setButtonIcon(quickPurchase, "purchase");
        setButtonIcon(quickQuotation, "quotation");
        setButtonIcon(quickPayment, "payment");
        setButtonIcon(quickCustomer, "customer");
        setButtonIcon(quickSupplier, "supplier");
        setButtonIcon(quickBank, "payment");
        setButtonIcon(quickExpense, "document");
    }

    private void setButtonIcon(Button button, String icon) {
        if (button == null) return;
        button.setGraphic(IconFactory.compactIcon(icon, 24));
        button.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        button.setGraphicTextGap(6);
        button.setVisible(true);
        button.setManaged(true);
        button.getProperties().put("erp-icon-preserve", true);
        button.getProperties().put("erp.icon.semantic", icon);
    }

    /** Keeps the three dashboard insight lists readable and gives each row a clear visual accent. */
    private void installColorfulDashboardLists() {
        installColorfulList(topCustomerList, "dashboard-customer-row");
        installColorfulList(agingList, "dashboard-aging-row");
        installColorfulList(activityList, "dashboard-activity-row");
    }

    private void installColorfulList(ListView<String> list, String baseStyle) {
        if (list == null) return;
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll(
                    "dashboard-customer-row", "dashboard-aging-row", "dashboard-activity-row",
                    "dashboard-row-accent-0", "dashboard-row-accent-1", "dashboard-row-accent-2",
                    "dashboard-row-accent-3", "dashboard-row-accent-4"
                );
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item);
                getStyleClass().add(baseStyle);
                getStyleClass().add("dashboard-row-accent-" + Math.floorMod(getIndex(), 5));
            }
        });
    }

    /** Cycles the shared dashboard reporting period and reloads all database widgets. */

    @Override public void onScreenShown(boolean reusedFromCache) {
        if (reusedFromCache) reload();
    }

    @FXML
    private void refreshDashboard() {
        reload();
    }

    @FXML private void cyclePeriod(ActionEvent event) {
        String[] periods = {"This Month", "This Quarter", "This Year", "All Time"};
        int index = java.util.Arrays.asList(periods).indexOf(fallbackPeriod);
        fallbackPeriod = periods[(index + 1) % periods.length];
        if (event.getSource() instanceof Button button) button.setText(fallbackPeriod);
        if (cmbPeriod != null) cmbPeriod.setValue(fallbackPeriod); else reload();
    }

    @FXML private void viewTopCustomers() { openFromNode(topCustomerList, "/fxml/pages/Customer.fxml"); }
    @FXML private void viewCustomers() { openFromNode(lblCustomers, "/fxml/pages/Customer.fxml"); }
    @FXML private void viewItems() { openFromNode(lblProducts, "/fxml/pages/ItemMaster.fxml"); }
    @FXML private void viewSalesInvoices() { openFromNode(lblOrders, "/fxml/pages/SalesList.fxml"); }
    @FXML private void viewPurchaseInvoices() { openFromNode(lblPurchases, "/fxml/pages/PurchaseList.fxml"); }
    @FXML private void viewReceivables() { openFromNode(agingList, "/fxml/pages/SalesList.fxml"); }
    @FXML private void viewRecentInvoices() { openFromNode(recentTable, "/fxml/pages/SalesList.fxml"); }
    @FXML private void viewReminders() { openFromNode(activityList, "/fxml/pages/ReminderCenter.fxml"); }

    private void reload() {
        if (!dashboardLoadRunning.compareAndSet(false, true)) return;
        showLoading("Loading dashboard data…");
        String period = selectedPeriod();
        Task<DashboardData> task = new Task<>() {
            @Override protected DashboardData call() { return queryDashboard(period); }
        };
        task.setOnSucceeded(event -> {
            try { applyDashboard(task.getValue()); }
            finally { dashboardLoadRunning.set(false); hideLoading(); }
        });
        task.setOnFailed(event -> {
            dashboardLoadRunning.set(false); hideLoading();
            org.example.util.ModernDialog.error(dashboardRoot, "Dashboard could not refresh",
                "The ERP remains available", task.getException() == null ? "Unknown dashboard error" : task.getException().getMessage());
        });
        Thread thread = new Thread(task, "dse-dashboard-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void showLoading(String message) {
        if (loadingMessage != null) loadingMessage.setText(message);
        if (loadingOverlay != null) { loadingOverlay.setManaged(true); loadingOverlay.setVisible(true); loadingOverlay.toFront(); }
        if (dashboardContent != null) dashboardContent.setDisable(true);
    }

    private void hideLoading() {
        if (loadingOverlay != null) { loadingOverlay.setVisible(false); loadingOverlay.setManaged(false); }
        if (dashboardContent != null) dashboardContent.setDisable(false);
        ensureQuickActionsVisible();
    }

    private void ensureQuickActionsVisible() {
        for (Button button : new Button[]{quickSale, quickPurchase, quickQuotation, quickPayment, quickCustomer, quickSupplier, quickBank, quickExpense}) {
            if (button == null) continue;
            button.setVisible(true); button.setManaged(true); button.setMinHeight(70);
            if (button.getGraphic() == null) installQuickActionIcons();
        }
    }

    /**
     * Loads the complete dashboard bundle once. Prior releases fetched the same
     * /dashboard endpoint twice (primary KPIs and secondary panels), doubling
     * server/database work on every refresh.
     */
    private DashboardData queryDashboard(String period) {
        var bundle = insightsApi.dashboard(period);
        var d = bundle.snapshot();
        DashboardSnapshot snapshot = new DashboardSnapshot(
            d.period(), d.products(), d.customers(), d.invoices(), d.purchases(),
            d.lowStock(), d.salesValue(), d.purchaseValue(), d.receivables(),
            d.payables(), d.openReceivables(), d.openPayables(), d.cash(),
            d.openReminders(), d.overdueReminders());
        List<ActivityRow> recent = bundle.recent().stream()
            .map(x -> new ActivityRow(x.type(), x.number(), x.party(), x.date(), money(x.amount())))
            .toList();
        List<String> customers = bundle.topCustomers().stream().map(this::formatPair).toList();
        List<String> ageing = bundle.ageing().stream().map(this::formatPair).toList();
        List<String> activities = bundle.activities().stream()
            .map(x -> x.message() + "    " + Instant.ofEpochMilli(x.createdAt())
                .atZone(BusinessClock.zone())
                .format(DateTimeFormatter.ofPattern(BusinessClock.datePattern() + ", hh:mm a")))
            .toList();
        if (activities.isEmpty()) activities = List.of("No recent application activity");
        return new DashboardData(snapshot, new SecondaryPanels(recent, customers, ageing, activities));
    }

    private void applyDashboard(DashboardData data) {
        if (data == null) return;
        applySnapshot(data.snapshot());
        applySecondaryPanels(data.secondary());
    }

    private void applySnapshot(DashboardSnapshot d) {
        long applyStarted = System.nanoTime();
        if (lblTrendPeriod != null) lblTrendPeriod.setText(d.period());
        if (lblComparisonPeriod != null) lblComparisonPeriod.setText(d.period());
        lblSalesValue.setText(money(d.salesValue())); lblPurchaseValue.setText(money(d.purchaseValue()));
        lblStockValue.setText(money(d.receivables())); lblLowStock.setText(money(d.payables())); lblCash.setText(money(d.cash()));
        if (lblLowStockValue != null) lblLowStockValue.setText(String.valueOf(d.lowStock()));
        if (lblLowStockNote != null) lblLowStockNote.setText(d.lowStock()==0 ? "Stock levels healthy" : d.lowStock()+" item"+plural(d.lowStock())+" need attention");
        if (lblTrendSales != null) lblTrendSales.setText(money(d.salesValue()));
        lblSalesNote.setText(d.invoices()+" active sales invoice"+plural(d.invoices()));
        lblPurchaseNote.setText(d.purchases()+" active purchase invoice"+plural(d.purchases()));
        lblReceivableNote.setText(d.openReceivables()+" open sales invoice"+plural(d.openReceivables()));
        lblStockNote.setText(d.openPayables()+" open purchase invoice"+plural(d.openPayables()));
        if (lblProductsNote != null) lblProductsNote.setText(d.products()+" active catalog item"+plural(d.products()));
        if (lblCustomers != null) lblCustomers.setText(String.valueOf(d.customers()));
        if (lblProducts != null) lblProducts.setText(String.valueOf(d.products()));
        if (lblOrders != null) lblOrders.setText(String.valueOf(d.invoices()));
        if (lblPurchases != null) lblPurchases.setText(String.valueOf(d.purchases()));
        if (lblReminderCount != null) lblReminderCount.setText(String.valueOf(d.openReminders()));
        if (lblReminderSummary != null) lblReminderSummary.setText(d.overdueReminders() > 0
            ? d.overdueReminders() + " overdue reminder" + plural(d.overdueReminders())
            : "No overdue reminders");
        configureTable();
        long applyMs = (System.nanoTime() - applyStarted) / 1_000_000L;
        if (applyMs >= 15) org.example.util.PerformanceMonitor.event("controller-phase", "dashboard-primary-apply | " + applyMs + " ms");
    }

    private void applySecondaryPanels(SecondaryPanels data) {
        long started = System.nanoTime();
        recentTable.getItems().setAll(data.recent());
        topCustomerList.getItems().setAll(data.customers());
        agingList.getItems().setAll(data.ageing());
        activityList.getItems().setAll(data.activities());
        long ms = (System.nanoTime() - started) / 1_000_000L;
        if (ms >= 15) org.example.util.PerformanceMonitor.event("controller-phase", "dashboard-secondary-apply | " + ms + " ms");
    }

    private String formatPair(String raw) {
        if (raw == null) return "";
        int split=raw.lastIndexOf('|');
        if(split<0) return raw;
        try{return raw.substring(0,split)+"    "+money(Double.parseDouble(raw.substring(split+1)));}catch(Exception e){return raw;}
    }

    private record SecondaryPanels(List<ActivityRow> recent, List<String> customers, List<String> ageing, List<String> activities) {}
    private record DashboardData(DashboardSnapshot snapshot, SecondaryPanels secondary) {}

    private record DashboardSnapshot(String period,long products,long customers,long invoices,long purchases,long lowStock,double salesValue,double purchaseValue,double receivables,double payables,long openReceivables,long openPayables,double cash,long openReminders,long overdueReminders) {}

    private String selectedPeriod() {
        return cmbPeriod == null || cmbPeriod.getValue() == null ? fallbackPeriod : cmbPeriod.getValue();
    }





    private String plural(long value) { return value == 1 ? "" : "s"; }

    private void configureTable() {
        colType.setCellValueFactory(row -> row.getValue().type);
        colNumber.setCellValueFactory(row -> row.getValue().number);
        colParty.setCellValueFactory(row -> row.getValue().party);
        colDate.setCellValueFactory(row -> row.getValue().date);
        colAmount.setCellValueFactory(row -> row.getValue().amount);
        recentTable.setPlaceholder(new Label("No transactions recorded yet"));
    }





    /** Builds the sales-vs-purchase graph from the database rather than sample data. */
    /** Formats one live receivable-ageing bucket. */


    @FXML private void newSale(ActionEvent event) { open(event, "/fxml/pages/Sale.fxml"); }
    @FXML private void newPurchase(ActionEvent event) { open(event, "/fxml/pages/Purchase.fxml"); }
    @FXML private void newQuotation(ActionEvent event) { open(event, "/fxml/pages/Quotations.fxml"); }
    @FXML private void addPayment(ActionEvent event) { open(event, "/fxml/pages/SalesList.fxml"); }
    @FXML private void addCustomer(ActionEvent event) { open(event, "/fxml/pages/Customer.fxml"); }
    @FXML private void addSupplier(ActionEvent event) { open(event, "/fxml/pages/Suppliers.fxml"); }
    @FXML private void bankEntry(ActionEvent event) { BankExpenseController.requestMode(BankExpenseController.Mode.BANK); open(event, "/fxml/pages/BankExpense.fxml"); }
    @FXML private void expenseEntry(ActionEvent event) { BankExpenseController.requestMode(BankExpenseController.Mode.EXPENSE); open(event, "/fxml/pages/BankExpense.fxml"); }
    @FXML private void openItems(ActionEvent event) { open(event, "/fxml/pages/ItemMaster.fxml"); }
    @FXML private void openImport(ActionEvent event) { open(event, "/fxml/pages/Import.fxml"); }

    private void open(ActionEvent event, String fxml) {
        NavigationManager.navigateOrReport(fxml);
    }
    private void openFromNode(Node source, String fxml) {
        NavigationManager.navigateOrReport(fxml);
    }



    private String money(double value) { return currency.format(value).replace("₹", "₹ "); }

    public static final class ActivityRow {
        final SimpleStringProperty type, number, party, date, amount;
        ActivityRow(String type, String number, String party, String date, String amount) {
            this.type = new SimpleStringProperty(type); this.number = new SimpleStringProperty(number);
            this.party = new SimpleStringProperty(party); this.date = new SimpleStringProperty(date); this.amount = new SimpleStringProperty(amount);
        }
    }


    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colType, "category");
        IconFactory.applyTableHeaderIcon(colNumber, "document");
        IconFactory.applyTableHeaderIcon(colParty, "customer");
        IconFactory.applyTableHeaderIcon(colDate, "calendar");
        IconFactory.applyTableHeaderIcon(colAmount, "currency");
    }
}
