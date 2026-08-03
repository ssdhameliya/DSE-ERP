package org.example.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import org.example.util.IconFactory;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ProgressIndicator;
import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import org.example.database.DatabaseManager;
import org.example.navigation.NavigationManager;
import org.example.service.NotificationService;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class DashboardHomeController {
    private String fallbackPeriod = "This Month";
    public DashboardHomeController() {
        // JavaFX creates the controller before injecting the FXML controls.
    }
    @FXML private Label lblSalesValue, lblPurchaseValue, lblStockValue, lblLowStock;
    @FXML private Label lblSalesNote, lblPurchaseNote, lblProductsNote, lblStockNote;
    @FXML private Label lblReceivableNote;
    @FXML private Label lblCustomers, lblProducts, lblOrders;
    @FXML private Label lblCash;
    @FXML private Label lblLowStockValue, lblLowStockNote, lblTrendSales, lblReminderCount, lblReminderSummary;
    @FXML private Label lblTrendPeriod, lblComparisonPeriod;
    @FXML private ComboBox<String> cmbPeriod;
    @FXML private ListView<String> topCustomerList, agingList, activityList;
    @FXML private TableView<ActivityRow> recentTable;
    @FXML private TableColumn<ActivityRow, String> colType, colNumber, colParty, colDate, colAmount;
    @FXML private StackPane salesKpiIcon, purchaseKpiIcon, receivableKpiIcon, payableKpiIcon, cashKpiIcon, lowStockKpiIcon, dashboardTitleIcon;
    @FXML private StackPane dashboardRoot, loadingOverlay;
    @FXML private Node dashboardContent;
    @FXML private Label loadingMessage;
    @FXML private ProgressIndicator loadingProgress;
    private final AtomicBoolean dashboardLoadRunning = new AtomicBoolean();
    @FXML private Button quickSale, quickPurchase, quickQuotation, quickPayment,
        quickCustomer, quickSupplier, quickBank, quickExpense, refreshDashboardButton;

    private final NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @FXML
    public void initialize() {
        configureExplicitTableHeaderIcons();
        installKpiIcons();
        installQuickActionIcons();
        installDashboardHeaderIcons();
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

    /** Cycles the shared dashboard reporting period and reloads all database widgets. */

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
    @FXML private void viewReceivables() { openFromNode(agingList, "/fxml/pages/SalesList.fxml"); }
    @FXML private void viewRecentInvoices() { openFromNode(recentTable, "/fxml/pages/SalesList.fxml"); }
    @FXML private void viewReminders() { openFromNode(activityList, "/fxml/pages/ReminderCenter.fxml"); }

    private void reload() {
        if (!dashboardLoadRunning.compareAndSet(false, true)) return;
        showLoading("Loading dashboard data…");
        String period = selectedPeriod();
        Task<DashboardSnapshot> task = new Task<>() {
            @Override protected DashboardSnapshot call() { return querySnapshot(period); }
        };
        task.setOnSucceeded(event -> {
            try { applySnapshot(task.getValue()); }
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

    private DashboardSnapshot querySnapshot(String period) {
        org.example.util.PerformanceMonitor.start("dashboard:" + period);
        try {
            String salesCondition = periodCondition(period, "invoice_date");
            long products = count("SELECT COUNT(*) FROM item_master");
            long customers = count("SELECT COUNT(*) FROM party_master WHERE party_type='CUSTOMER' AND COALESCE(is_active,1)=1");
            long invoices = count("SELECT COUNT(*) FROM sales_header WHERE " + salesCondition);
            long purchases = count("SELECT COUNT(*) FROM purchase_header WHERE " + periodCondition(period, "invoice_date"));
            long lowStock = count("SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=COALESCE(minimum_stock,0)");
            double salesValue = number("SELECT COALESCE(SUM(total_amount),0) FROM sales_header WHERE " + salesCondition);
            double purchaseValue = number("SELECT COALESCE(SUM(total_amount),0) FROM purchase_header WHERE " + periodCondition(period, "invoice_date"));
            double receivables = number("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM sales_header");
            double payables = number("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM purchase_header");
            long openReceivables = count("SELECT COUNT(*) FROM sales_header WHERE total_amount>COALESCE(paid_amount,0)");
            long openPayables = count("SELECT COUNT(*) FROM purchase_header WHERE total_amount>COALESCE(paid_amount,0)");
            double received = number("SELECT COALESCE(SUM(paid_amount),0) FROM sales_header");
            double paid = number("SELECT COALESCE(SUM(paid_amount),0) FROM purchase_header");
            double expenses = number("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(voucher_type)='EXPENSE'");
            long openReminders = count("SELECT COUNT(*) FROM reminder_register WHERE UPPER(COALESCE(status,'OPEN')) NOT IN ('COMPLETED','CANCELLED')");
            long overdueReminders = count("SELECT COUNT(*) FROM reminder_register WHERE UPPER(COALESCE(status,'OPEN')) NOT IN ('COMPLETED','CANCELLED') AND due_date IS NOT NULL AND date(due_date) < date('now')");
            return new DashboardSnapshot(period, products, customers, invoices, purchases, lowStock, salesValue, purchaseValue, receivables, payables, openReceivables, openPayables, received-paid-expenses, openReminders, overdueReminders);
        } finally { org.example.util.PerformanceMonitor.finish("dashboard:" + period); }
    }

    private void applySnapshot(DashboardSnapshot d) {
        if (lblTrendPeriod != null) lblTrendPeriod.setText(d.period());
        if (lblComparisonPeriod != null) lblComparisonPeriod.setText(d.period());
        lblSalesValue.setText(money(d.salesValue())); lblPurchaseValue.setText(money(d.purchaseValue()));
        lblStockValue.setText(money(d.receivables())); lblLowStock.setText(money(d.payables())); lblCash.setText(money(d.cash()));
        if (lblLowStockValue != null) lblLowStockValue.setText(String.valueOf(d.lowStock()));
        if (lblLowStockNote != null) lblLowStockNote.setText(d.lowStock()==0 ? "Stock levels healthy" : d.lowStock()+" item"+plural(d.lowStock())+" need attention");
        if (lblTrendSales != null) lblTrendSales.setText(money(d.salesValue()));
        lblSalesNote.setText(d.invoices()+" sales invoice"+plural(d.invoices()));
        lblPurchaseNote.setText(d.purchases()+" purchase invoice"+plural(d.purchases()));
        lblReceivableNote.setText(d.openReceivables()+" open sales invoice"+plural(d.openReceivables()));
        lblStockNote.setText(d.openPayables()+" open purchase invoice"+plural(d.openPayables()));
        if (lblProductsNote != null) lblProductsNote.setText(d.products()+" active catalog item"+plural(d.products()));
        if (lblCustomers != null) lblCustomers.setText(String.valueOf(d.customers()));
        if (lblProducts != null) lblProducts.setText(String.valueOf(d.products()));
        if (lblOrders != null) lblOrders.setText(String.valueOf(d.invoices()));
        if (lblReminderCount != null) lblReminderCount.setText(String.valueOf(d.openReminders()));
        if (lblReminderSummary != null) lblReminderSummary.setText(d.overdueReminders() > 0
            ? d.overdueReminders() + " overdue reminder" + plural(d.overdueReminders())
            : "No overdue reminders");
        configureTable(); loadRecentActivity(); loadDashboardLists(); loadLiveActivities();
    }

    private String periodCondition(String period, String column) {
        return switch (period) {
            case "This Month" -> "date(" + column + ") >= date('now','start of month')";
            case "This Quarter" -> "date(" + column + ") >= date('now','start of month','-' || ((cast(strftime('%m','now') as integer)-1)%3) || ' months')";
            case "This Year" -> "date(" + column + ") >= date('now','start of year')";
            default -> "1=1";
        };
    }

    private record DashboardSnapshot(String period,long products,long customers,long invoices,long purchases,long lowStock,double salesValue,double purchaseValue,double receivables,double payables,long openReceivables,long openPayables,double cash,long openReminders,long overdueReminders) {}

    private String selectedPeriod() {
        return cmbPeriod == null || cmbPeriod.getValue() == null ? fallbackPeriod : cmbPeriod.getValue();
    }

    private String periodCondition(String column) {
        return switch (selectedPeriod()) {
            case "This Month" -> "date(" + column + ") >= date('now','start of month')";
            case "This Quarter" -> "date(" + column + ") >= date('now','start of month','-' || ((cast(strftime('%m','now') as integer)-1)%3) || ' months')";
            case "This Year" -> "date(" + column + ") >= date('now','start of year')";
            default -> "1=1";
        };
    }

    private void loadSummary() {
        long products = count("SELECT COUNT(*) FROM item_master");
        long customers = count("SELECT COUNT(*) FROM party_master WHERE party_type='CUSTOMER' AND COALESCE(is_active,1)=1");
        long invoices = count("SELECT COUNT(*) FROM sales_header WHERE " + periodCondition("invoice_date"));
        long purchases = count("SELECT COUNT(*) FROM purchase_header WHERE " + periodCondition("invoice_date"));
        long lowStock = count("SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=COALESCE(minimum_stock,0)");
        double salesValue = number("SELECT COALESCE(SUM(total_amount),0) FROM sales_header WHERE " + periodCondition("invoice_date"));
        double purchaseValue = number("SELECT COALESCE(SUM(total_amount),0) FROM purchase_header WHERE " + periodCondition("invoice_date"));
        double receivables = number("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM sales_header");
        double payables = number("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM purchase_header");
        long openReceivables = count("SELECT COUNT(*) FROM sales_header WHERE total_amount>COALESCE(paid_amount,0)");
        long openPayables = count("SELECT COUNT(*) FROM purchase_header WHERE total_amount>COALESCE(paid_amount,0)");
        double received = number("SELECT COALESCE(SUM(paid_amount),0) FROM sales_header");
        double paid = number("SELECT COALESCE(SUM(paid_amount),0) FROM purchase_header");
        double expenses = number("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(voucher_type)='EXPENSE'");

        lblSalesValue.setText(money(salesValue));
        lblPurchaseValue.setText(money(purchaseValue));
        lblStockValue.setText(money(receivables));
        lblLowStock.setText(money(payables));
        lblCash.setText(money(received - paid - expenses));
        if (lblLowStockValue != null) lblLowStockValue.setText(String.valueOf(lowStock));
        if (lblLowStockNote != null) lblLowStockNote.setText(lowStock == 0 ? "Stock levels healthy" : lowStock + " item" + plural(lowStock) + " need attention");
        if (lblTrendSales != null) lblTrendSales.setText(money(salesValue));
        lblSalesNote.setText(invoices + " sales invoice" + plural(invoices));
        lblPurchaseNote.setText(purchases + " purchase invoice" + plural(purchases));
        lblReceivableNote.setText(openReceivables + " open sales invoice" + plural(openReceivables));
        lblProductsNote.setText(products + " active catalog item" + plural(products));
        lblStockNote.setText(openPayables + " open purchase invoice" + plural(openPayables));
        lblCustomers.setText(String.valueOf(customers));
        lblProducts.setText(String.valueOf(products));
        lblOrders.setText(String.valueOf(invoices));
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

    private void loadRecentActivity() {
        String sql = """
            SELECT * FROM (
              SELECT 'Sale' type, s.invoice_no doc_no, p.name party, s.invoice_date doc_date, s.total_amount amount
              FROM sales_header s JOIN party_master p ON p.id=s.customer_id
              UNION ALL
              SELECT 'Purchase', h.invoice_no, p.name, h.invoice_date, h.total_amount
              FROM purchase_header h JOIN party_master p ON p.id=h.supplier_id
            ) ORDER BY doc_date DESC, doc_no DESC LIMIT 8
            """;
        List<ActivityRow> rows = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection(); Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) rows.add(new ActivityRow(rs.getString("type"), rs.getString("doc_no"), rs.getString("party"), rs.getString("doc_date"), money(rs.getDouble("amount"))));
        } catch (Exception ignored) { }
        recentTable.getItems().setAll(rows);
        // Do not inject sample transactions: the dashboard must reflect only persisted data.
        if (false && rows.isEmpty()) recentTable.getItems().setAll(
            new ActivityRow("Partial","INV-2024-0186","ABC Pvt Ltd","28/05/2024","₹ 1,24,000"),
            new ActivityRow("Paid","INV-2024-0185","XYZ Industries","27/05/2024","₹ 75,000"),
            new ActivityRow("Unpaid","INV-2024-0184","LMN Enterprises","26/05/2024","₹ 98,000"),
            new ActivityRow("Partial","INV-2024-0183","PQR Traders","25/05/2024","₹ 1,45,000"));
    }

    private void loadDashboardLists() {
        List<String> customers = new ArrayList<>();
        String customerSql = "SELECT p.name, COALESCE(SUM(s.total_amount),0) amount FROM sales_header s JOIN party_master p ON p.id=s.customer_id WHERE " + periodCondition("s.invoice_date") + " GROUP BY p.id,p.name ORDER BY amount DESC LIMIT 5";
        try (Connection c=DatabaseManager.getConnection(); Statement st=c.createStatement(); ResultSet rs=st.executeQuery(customerSql)) {
            while(rs.next()) customers.add(String.format("%-28s %s", rs.getString(1), money(rs.getDouble(2))));
        } catch (Exception ignored) { }
        if (customers.isEmpty()) customers.add("No customer sales for " + selectedPeriod().toLowerCase(Locale.ROOT));
        topCustomerList.getItems().setAll(customers);
        agingList.getItems().setAll("●  Overdue (> 30 Days)                              ₹ 4,25,000","●  21 - 30 Days                                           ₹ 2,35,000","●  11 - 20 Days                                           ₹ 1,35,000","●  1 - 10 Days                                             ₹ 3,75,000","●  Not Due                                                   ₹ 7,05,000");
        activityList.getItems().setAll("▤  Sales Invoice INV-2024-0186 created                  10:30 AM","●  Payment received from ABC Pvt Ltd                        11:45 AM","▤  Purchase Bill PUR-2024-0008 created                   01:15 PM","♧  Payment reminder sent to XYZ Industries             03:20 PM","▣  Quotation QT-2024-0067 accepted                         04:45 PM");
        // Replace legacy placeholder ageing values with current open-balance data.
        agingList.getItems().setAll(
            ageing("Overdue (> 30 Days)", "due_date < date('now','-30 day')"),
            ageing("21 - 30 Days", "due_date BETWEEN date('now','-30 day') AND date('now','-21 day')"),
            ageing("11 - 20 Days", "due_date BETWEEN date('now','-20 day') AND date('now','-11 day')"),
            ageing("1 - 10 Days", "due_date BETWEEN date('now','-10 day') AND date('now','-1 day')"),
            ageing("Not Due", "due_date IS NULL OR due_date >= date('now')"));
    }

    /** Replaces placeholder activity content with the latest persisted notifications. */
    private void loadLiveActivities() {
        List<String> activities = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a");
        for (NotificationService.NotificationItem entry : NotificationService.findRecent(5)) {
            String created = Instant.ofEpochMilli(entry.createdAt())
                .atZone(ZoneId.systemDefault()).format(formatter);
            activities.add(entry.message() + "    " + created);
        }
        if (activities.isEmpty()) activities.add("No recent application activity");
        activityList.getItems().setAll(activities);
    }

    /** Builds the sales-vs-purchase graph from the database rather than sample data. */
    /** Formats one live receivable-ageing bucket. */
    private String ageing(String label, String dueCondition) {
        double amount = number("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM sales_header WHERE COALESCE(total_amount-paid_amount,0)>0 AND (" + dueCondition + ")");
        return "•  " + label + "    " + money(amount);
    }

    @FXML private void newSale(ActionEvent event) { open(event, "/fxml/pages/Sale.fxml"); }
    @FXML private void newPurchase(ActionEvent event) { open(event, "/fxml/pages/Purchase.fxml"); }
    @FXML private void newQuotation(ActionEvent event) { open(event, "/fxml/pages/Quotations.fxml"); }
    @FXML private void addPayment(ActionEvent event) { open(event, "/fxml/pages/SalesList.fxml"); }
    @FXML private void addCustomer(ActionEvent event) { open(event, "/fxml/pages/Customer.fxml"); }
    @FXML private void addSupplier(ActionEvent event) { open(event, "/fxml/pages/Suppliers.fxml"); }
    @FXML private void bankEntry(ActionEvent event) { open(event, "/fxml/pages/Operations.fxml"); }
    @FXML private void expenseEntry(ActionEvent event) { open(event, "/fxml/pages/Operations.fxml"); }
    @FXML private void openItems(ActionEvent event) { open(event, "/fxml/pages/ItemMaster.fxml"); }
    @FXML private void openImport(ActionEvent event) { open(event, "/fxml/pages/Import.fxml"); }

    private void open(ActionEvent event, String fxml) {
        Node source = (Node) event.getSource();
        StackPane content = (StackPane) source.getScene().lookup("#contentPane");
        if (content != null) new NavigationManager(content).loadPage(fxml);
    }
    private void openFromNode(Node source,String fxml){StackPane content=(StackPane)source.getScene().lookup("#contentPane");if(content!=null)new NavigationManager(content).loadPage(fxml);}

    private long count(String sql) { return (long) number(sql); }
    private double number(String sql) {
        try (Connection con = DatabaseManager.getConnection(); Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (Exception ignored) { return 0; }
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
        IconFactory.applyTableHeaderIcon(colType, "status");
        IconFactory.applyTableHeaderIcon(colNumber, "document");
        IconFactory.applyTableHeaderIcon(colParty, "customer");
        IconFactory.applyTableHeaderIcon(colDate, "calendar");
        IconFactory.applyTableHeaderIcon(colAmount, "currency");
    }
}
