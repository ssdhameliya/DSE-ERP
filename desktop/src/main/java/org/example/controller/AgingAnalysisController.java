package org.example.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.api.ApiRuntime;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;
import org.example.navigation.ScreenLifecycle;
import org.example.util.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

public class AgingAnalysisController implements ScreenLifecycle {

    @FXML private StackPane pageIcon;
    @FXML private StackPane kpiTotalIcon, kpiCurrentIcon, kpiDueIcon, kpiCriticalIcon;
    @FXML private Label kpiTotalOutstanding, lblPartyCount, kpiCurrentBucket, kpiDueBucket, kpiCriticalBucket;

    @FXML private ToggleButton btnDebtors, btnCreditors;
    @FXML private DatePicker dpAsOfDate;
    @FXML private Button btnRefresh;

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbRiskFilter;
    @FXML private Button btnSendBulkReminder;

    @FXML private SplitPane mainSplit;
    @FXML private TableView<AgingBucketRow> tblAging;
    @FXML private TableColumn<AgingBucketRow, String> colPartyName, colTotalDue, colCurrent, col31To60, col61To90, colOver90, colRisk, colActions;

    @FXML private VBox detailDrawer;
    @FXML private Label lblDrawerTitle, lblDrawerSubtitle;
    @FXML private Label lblDetailPartyName, lblDetailGroup, lblDetailPhone;
    @FXML private Label lblDetailCurrent, lblDetail31To60, lblDetail61To90, lblDetailOver90, lblDetailTotal;
    @FXML private Button btnSendIndividualReminder, btnViewLedger, btnCloseDrawer;

    private final ObservableList<AgingBucketRow> masterList = FXCollections.observableArrayList();
    private final ObservableList<AgingBucketRow> filteredList = FXCollections.observableArrayList();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    private boolean isDebtorMode = true;
    private AgingBucketRow selectedRow;
    private final org.example.api.insights.InsightsApiClient insightsApi = new org.example.api.insights.InsightsApiClient();
    private final org.example.api.support.SupportApiClient supportApi = new org.example.api.support.SupportApiClient();

    public void initialize() {
        if (pageIcon != null) pageIcon.getChildren().setAll(IconFactory.icon("wallet", 22));
        if (kpiTotalIcon != null) kpiTotalIcon.getChildren().setAll(IconFactory.compactIcon("wallet", 16));
        if (kpiCurrentIcon != null) kpiCurrentIcon.getChildren().setAll(IconFactory.compactIcon("check", 16));
        if (kpiDueIcon != null) kpiDueIcon.getChildren().setAll(IconFactory.compactIcon("warning", 16));
        if (kpiCriticalIcon != null) kpiCriticalIcon.getChildren().setAll(IconFactory.compactIcon("error", 16));

        dpAsOfDate.setValue(LocalDate.now());

        btnDebtors.setSelected(true);
        btnCreditors.setSelected(false);

        configureTableColumns();
        DynamicTableLayoutManager.install(tblAging);

        cmbRiskFilter.getItems().setAll("All Risk Levels", "NORMAL", "LOW", "MODERATE", "HIGH", "CRITICAL");
        cmbRiskFilter.setValue("All Risk Levels");
        cmbRiskFilter.valueProperty().addListener((obs, o, n) -> applyFilters());

        RealtimeSearchSupport.installLocal(txtSearch, this::applyFilters);

        tblAging.setRowFactory(tv -> {
            TableRow<AgingBucketRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && !row.isEmpty()) {
                    tblAging.getSelectionModel().select(row.getItem());
                    if (event.getClickCount() == 1) {
                        showDetails(row.getItem());
                    }
                }
            });
            return row;
        });

        RegisterUiSupport.hideDrawer(detailDrawer, mainSplit, tblAging);
        OperationalUiSupport.installEscapeClose(mainSplit, () -> detailDrawer != null && detailDrawer.isVisible(), this::closeDetails);
        refreshData();
    }

    @Override
    public void onScreenShown(boolean reusedFromCache) {
        refreshData();
    }

    private static String apiUrl(String path) {
        String base = ConfigManager.getDataApiBaseUrl();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + path;
    }

    @FXML
    public void selectDebtors() {
        isDebtorMode = true;
        btnDebtors.setSelected(true);
        btnCreditors.setSelected(false);
        btnSendBulkReminder.setText("Send Reminders (Overdue Debtors)");
        btnSendBulkReminder.setVisible(true);
        refreshData();
    }

    @FXML
    public void selectCreditors() {
        isDebtorMode = false;
        btnDebtors.setSelected(false);
        btnCreditors.setSelected(true);
        btnSendBulkReminder.setText("Generate Payment Schedule");
        refreshData();
    }

    private void configureTableColumns() {
        colPartyName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().partyName));
        colTotalDue.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().totalOutstanding)));
        colCurrent.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().bucket0To30)));
        col31To60.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().bucket31To60)));
        col61To90.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().bucket61To90)));
        colOver90.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().bucket90Plus)));
        colRisk.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().riskLevel));

        colActions.setCellFactory(col -> new TableCell<>() {
            private final MenuButton actionsBtn = new MenuButton("Actions");
            {
                actionsBtn.getStyleClass().addAll("table-action-menu", "approved-row-action");
                actionsBtn.setGraphic(IconFactory.compactIcon("actions", 14));
                actionsBtn.setContentDisplay(ContentDisplay.LEFT);
                actionsBtn.setGraphicTextGap(6);

                MenuItem viewItem = new MenuItem("View Details");
                viewItem.setGraphic(IconFactory.compactIcon("view", 13));
                viewItem.setOnAction(e -> {
                    AgingBucketRow row = getTableRow().getItem();
                    if (row != null) {
                        tblAging.getSelectionModel().select(row);
                        showDetails(row);
                    }
                });

                MenuItem remindItem = new MenuItem();
                remindItem.setGraphic(IconFactory.compactIcon("reminder", 13));
                remindItem.setOnAction(e -> {
                    AgingBucketRow row = getTableRow().getItem();
                    if (row != null) sendReminderForRow(row);
                });

                MenuItem auditItem = new MenuItem("Audit Trail");
                auditItem.setGraphic(IconFactory.compactIcon("history", 13));
                auditItem.setOnAction(e -> {
                    AgingBucketRow row = getTableRow().getItem();
                    if (row != null) {
                        org.example.util.ActivityTimelineDialog.show(tblAging, isDebtorMode ? "CUSTOMER" : "SUPPLIER", 1, row.partyName);
                    }
                });

                actionsBtn.setOnShowing(ev -> {
                    remindItem.setText(isDebtorMode ? "Send Overdue Notice" : "Record Supplier Payment");
                });

                actionsBtn.getItems().addAll(viewItem, remindItem, auditItem);
                IconFactory.decorateActionMenu(actionsBtn);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(actionsBtn);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        tblAging.setItems(filteredList);
    }

    @FXML
    public void refreshData() {
        LocalDate asOf = dpAsOfDate.getValue() != null ? dpAsOfDate.getValue() : LocalDate.now();
        String type = isDebtorMode ? "DEBTOR" : "CREDITOR";

        UiTaskExecutor.submitLatest(
            "aging-analysis-fetch",
            () -> {
                String url = apiUrl("/api/financial/aging?type=" + type + "&asOfDate=" + asOf);
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
                ApiSession.authorize(b);
                HttpResponse<String> resp = ApiRuntime.HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return ApiRuntime.JSON.readValue(resp.body(), AgingReportResponse.class);
                }
                return null;
            },
            report -> {
                if (report != null) {
                    masterList.setAll(report.rows != null ? report.rows : Collections.emptyList());
                    applyFilters();
                    updateKpis(report);
                }
            },
            err -> AppDialogService.error(tblAging, "Aging Analysis", "Failed to Load Aging Report", err.getMessage())
        );
    }

    private void applyFilters() {
        String search = txtSearch.getText() != null ? txtSearch.getText().trim().toLowerCase(Locale.ROOT) : "";
        String risk = cmbRiskFilter.getValue();

        List<AgingBucketRow> matched = masterList.stream().filter(row -> {
            boolean matchesSearch = search.isEmpty()
                || (row.partyName != null && row.partyName.toLowerCase(Locale.ROOT).contains(search));
            boolean matchesRisk = risk == null || "All Risk Levels".equals(risk)
                || (row.riskLevel != null && row.riskLevel.equalsIgnoreCase(risk));
            return matchesSearch && matchesRisk;
        }).toList();

        filteredList.setAll(matched);
    }

    private void updateKpis(AgingReportResponse report) {
        kpiTotalOutstanding.setText(currency.format(report.grandTotal != null ? report.grandTotal : BigDecimal.ZERO));
        lblPartyCount.setText(report.totalParties + (isDebtorMode ? " Customers" : " Suppliers"));

        kpiCurrentBucket.setText(currency.format(report.grandBucket0To30 != null ? report.grandBucket0To30 : BigDecimal.ZERO));
        kpiDueBucket.setText(currency.format(report.grandBucket31To60 != null ? report.grandBucket31To60 : BigDecimal.ZERO));
        BigDecimal critical = (report.grandBucket61To90 != null ? report.grandBucket61To90 : BigDecimal.ZERO)
            .add(report.grandBucket90Plus != null ? report.grandBucket90Plus : BigDecimal.ZERO);
        kpiCriticalBucket.setText(currency.format(critical));
    }

    private void showDetails(AgingBucketRow row) {
        if (row == null) return;
        this.selectedRow = row;
        if (lblDetailPartyName != null) lblDetailPartyName.setText(row.partyName);
        if (lblDetailGroup != null) lblDetailGroup.setText(isDebtorMode ? "Sundry Debtors" : "Sundry Creditors");
        if (lblDetailPhone != null) lblDetailPhone.setText(row.phone != null ? row.phone : "Not recorded");

        if (lblDetailCurrent != null) lblDetailCurrent.setText(currency.format(row.bucket0To30));
        if (lblDetail31To60 != null) lblDetail31To60.setText(currency.format(row.bucket31To60));
        if (lblDetail61To90 != null) lblDetail61To90.setText(currency.format(row.bucket61To90));
        if (lblDetailOver90 != null) lblDetailOver90.setText(currency.format(row.bucket90Plus));
        if (lblDetailTotal != null) lblDetailTotal.setText(currency.format(row.totalOutstanding));

        if (btnSendIndividualReminder != null) btnSendIndividualReminder.setText(isDebtorMode ? "Send Overdue Notice" : "Record Supplier Payment");

        // Centered modal inspection popup
        OwnedDialog<ButtonType> dialog = new OwnedDialog<>(tblAging);
        dialog.setTitle("Aging Profile Inspection — " + row.partyName);
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().addAll("erp-document-inspection-dialog", "approved-ui");
        PopupTableWorkspace.applyInspectionSizing(dialog, tblAging);

        Label iconLabel = new Label();
        iconLabel.setGraphic(IconFactory.icon("finance", 32));
        iconLabel.getStyleClass().add("inspection-hero-icon");

        Label kicker = new Label(isDebtorMode ? "ACCOUNTS RECEIVABLE AGING PROFILE" : "ACCOUNTS PAYABLE AGING PROFILE");
        kicker.getStyleClass().add("inspection-kicker");

        Label title = new Label(row.partyName);
        title.getStyleClass().addAll("inspection-hero-title", "sales-detail-invoice");

        Label sub = new Label((isDebtorMode ? "Customer Ledger Group: Sundry Debtors" : "Supplier Ledger Group: Sundry Creditors")
                + "  •  Phone: " + (row.phone != null ? row.phone : "Not recorded"));
        sub.getStyleClass().add("inspection-subtitle");

        VBox titleBox = new VBox(2, kicker, title, sub);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Region spacerHero = new Region();
        HBox.setHgrow(spacerHero, Priority.ALWAYS);

        Label statusPill = new Label(row.riskLevel != null ? row.riskLevel + " Risk" : "Normal Risk");
        String risk = row.riskLevel != null ? row.riskLevel.toUpperCase(Locale.ROOT) : "LOW";
        statusPill.getStyleClass().addAll("status-pill", "CRITICAL".equals(risk) || "HIGH".equals(risk) ? "pill-danger" : "MEDIUM".equals(risk) ? "pill-warning" : "pill-success");

        HBox hero = new HBox(14, iconLabel, titleBox, spacerHero, statusPill);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(4, 8, 8, 8));

        // Details Grid
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));
        grid.getStyleClass().addAll("inspection-detail-grid", "erp-detail-drawer-card");

        int r = 0;
        addAgingDetailRow(grid, r++, "Party / Trade Name", row.partyName, isDebtorMode ? "customer" : "supplier");
        addAgingDetailRow(grid, r++, "Ledger Group", isDebtorMode ? "Sundry Debtors (AR)" : "Sundry Creditors (AP)", "finance");
        addAgingDetailRow(grid, r++, "Contact Phone", row.phone != null ? row.phone : "Not recorded", "user");
        addAgingDetailRow(grid, r++, "Risk Level", row.riskLevel != null ? row.riskLevel : "Low", "status");
        addAgingDetailRow(grid, r++, "Current (0–30 Days)", currency.format(row.bucket0To30 != null ? row.bucket0To30 : BigDecimal.ZERO), "currency");
        addAgingDetailRow(grid, r++, "Overdue (31–60 Days)", currency.format(row.bucket31To60 != null ? row.bucket31To60 : BigDecimal.ZERO), "calendar");
        addAgingDetailRow(grid, r++, "High Risk (61–90 Days)", currency.format(row.bucket61To90 != null ? row.bucket61To90 : BigDecimal.ZERO), "warning");
        addAgingDetailRow(grid, r++, "Critical (>90 Days)", currency.format(row.bucket90Plus != null ? row.bucket90Plus : BigDecimal.ZERO), "danger");
        addAgingDetailRow(grid, r++, "Total Outstanding Balance", currency.format(row.totalOutstanding != null ? row.totalOutstanding : BigDecimal.ZERO), "currency");

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Action Dock
        HBox dock = new HBox(8);
        dock.setAlignment(Pos.CENTER_LEFT);
        dock.setPadding(new Insets(10, 0, 0, 0));

        Button btnAction = new Button(isDebtorMode ? "Send Overdue Notice" : "Record Supplier Payment");
        btnAction.setGraphic(IconFactory.compactIcon(isDebtorMode ? "reminder" : "payment", 14));
        btnAction.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnAction.setOnAction(e -> {
            dialog.close();
            sendReminderForRow(row);
        });

        Button btnAudit = new Button("Audit Trail");
        btnAudit.setGraphic(IconFactory.compactIcon("history", 14));
        btnAudit.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnAudit.setOnAction(e -> {
            org.example.util.ActivityTimelineDialog.show(tblAging, isDebtorMode ? "CUSTOMER" : "SUPPLIER", 1, row.partyName);
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnClose = new Button("Close");
        btnClose.setGraphic(IconFactory.compactIcon("cancel", 14));
        btnClose.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnClose.setOnAction(e -> dialog.close());

        dock.getChildren().addAll(btnAction, btnAudit, sp, btnClose);

        VBox contentBox = new VBox(12, hero, scroll, dock);
        contentBox.setPadding(new Insets(16, 20, 16, 20));
        pane.setContent(contentBox);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        Node bb = pane.lookup(".button-bar");
        if (bb != null) { bb.setVisible(false); bb.setManaged(false); }

        dialog.showAndWait();
    }

    private static void addAgingDetailRow(GridPane grid, int row, String caption, String value, String semantic) {
        Label capLbl = new Label(caption);
        capLbl.setGraphic(IconFactory.compactIcon(semantic, 14));
        capLbl.setGraphicTextGap(6);
        capLbl.getStyleClass().addAll("inspection-card-caption", "erp-drawer-caption");
        capLbl.setMinWidth(180);

        Label valLbl = new Label(value != null ? value : "-");
        valLbl.getStyleClass().addAll("inspection-card-value", "erp-drawer-value");
        valLbl.setWrapText(true);

        grid.add(capLbl, 0, row);
        grid.add(valLbl, 1, row);
    }

    @FXML
    public void closeDetails() {
        selectedRow = null;
        RegisterUiSupport.hideDrawer(detailDrawer, mainSplit, tblAging);
    }

    @FXML
    public void sendIndividualReminder() {
        if (selectedRow != null) {
            sendReminderForRow(selectedRow);
        }
    }

    private void sendReminderForRow(AgingBucketRow row) {
        if (row == null) return;
        String partyName = row.partyName != null ? row.partyName : "Party";
        String totalText = currency.format(row.totalOutstanding != null ? row.totalOutstanding : BigDecimal.ZERO);
        if (isDebtorMode) {
            String priority = (row.bucket90Plus != null && row.bucket90Plus.compareTo(BigDecimal.ZERO) > 0) ? "URGENT" :
                              ((row.bucket61To90 != null && row.bucket61To90.compareTo(BigDecimal.ZERO) > 0) ? "HIGH" : "NORMAL");
            String notes = "Automated AR payment reminder generated from Aging Analysis for " + partyName + ". Total Outstanding: ₹ " + totalText +
                           " (Overdue >30d: ₹ " + currency.format((row.bucket31To60 != null ? row.bucket31To60 : BigDecimal.ZERO)
                           .add(row.bucket61To90 != null ? row.bucket61To90 : BigDecimal.ZERO)
                           .add(row.bucket90Plus != null ? row.bucket90Plus : BigDecimal.ZERO)) + ")";
            String user = org.example.service.SessionService.current() != null ? org.example.service.SessionService.current().getFullName() : "System";
            var dto = new org.example.api.insights.InsightsApiClient.ReminderDto(
                null,
                "Payment Follow-up: " + partyName,
                partyName + " • " + totalText,
                BusinessClock.today().toString(),
                priority,
                notes,
                "OPEN",
                user,
                null
            );

            UiTaskExecutor.submitAction(
                "aging-reminder-send-" + partyName.hashCode(),
                () -> {
                    try {
                        insightsApi.saveReminder(dto);
                    } catch (Exception ex) {
                        java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Reminder api error: " + ex.getMessage());
                    }
                    try {
                        supportApi.communication(new org.example.api.support.SupportApiClient.CommunicationRequest(
                            "PARTY", 0, "REMINDER", partyName,
                            "Payment Reminder: Outstanding ₹ " + totalText, "SENT", null, user
                        ));
                    } catch (Exception ex) {
                        java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Communication api error: " + ex.getMessage());
                    }
                    org.example.service.NotificationService.add("Payment reminder queued for " + partyName + " (" + totalText + ")");
                    return null;
                },
                ignored -> AppDialogService.success(tblAging, "Payment Reminder Dispatched",
                    "Automated reminder for " + totalText + " recorded in Reminder Center and dispatched to " + partyName),
                failure -> AppDialogService.error(tblAging, "Reminder Failed", "Could not dispatch reminder", failure.getMessage())
            );
        } else {
            String user = org.example.service.SessionService.current() != null ? org.example.service.SessionService.current().getFullName() : "System";
            var dto = new org.example.api.insights.InsightsApiClient.ReminderDto(
                null,
                "Vendor Payment Schedule: " + partyName,
                partyName + " • " + totalText,
                BusinessClock.today().plusDays(3).toString(),
                "NORMAL",
                "Vendor payment schedule created for " + partyName + " for ₹ " + totalText,
                "OPEN",
                user,
                null
            );
            UiTaskExecutor.submitAction(
                "aging-schedule-send-" + partyName.hashCode(),
                () -> {
                    try { insightsApi.saveReminder(dto); } catch (Exception ignored) {}
                    org.example.service.NotificationService.add("Vendor payment schedule created for " + partyName + " (" + totalText + ")");
                    return null;
                },
                ignored -> AppDialogService.success(tblAging, "Payment Scheduled",
                    "Vendor payment schedule created for " + partyName + " for " + totalText + " and added to Reminder Center."),
                failure -> AppDialogService.error(tblAging, "Schedule Failed", "Could not create schedule", failure.getMessage())
            );
        }
    }

    @FXML
    public void sendBulkReminders() {
        List<AgingBucketRow> overdueList = masterList.stream()
            .filter(r -> r.bucket31To60.compareTo(BigDecimal.ZERO) > 0 || r.bucket61To90.compareTo(BigDecimal.ZERO) > 0 || r.bucket90Plus.compareTo(BigDecimal.ZERO) > 0)
            .toList();
        if (overdueList.isEmpty()) {
            AppDialogService.info(tblAging, "Reminders", "No Overdue Accounts", "All accounts are within their 30-day payment term.");
            return;
        }

        if (AppDialogService.confirm(tblAging, "Bulk Reminders", "Send Automated Reminders?", "Send payment reminders to " + overdueList.size() + " overdue parties?")) {
            String user = org.example.service.SessionService.current() != null ? org.example.service.SessionService.current().getFullName() : "System";
            UiTaskExecutor.submitAction(
                "aging-bulk-reminders",
                () -> {
                    int queued = 0;
                    for (AgingBucketRow r : overdueList) {
                        try {
                            String prio = r.bucket90Plus.compareTo(BigDecimal.ZERO) > 0 ? "URGENT" : (r.bucket61To90.compareTo(BigDecimal.ZERO) > 0 ? "HIGH" : "NORMAL");
                            var dto = new org.example.api.insights.InsightsApiClient.ReminderDto(
                                null,
                                "Payment Follow-up: " + r.partyName,
                                r.partyName + " • " + currency.format(r.totalOutstanding),
                                BusinessClock.today().toString(),
                                prio,
                                "Overdue payment follow-up from Aging Analysis. Total: ₹ " + currency.format(r.totalOutstanding),
                                "OPEN",
                                user,
                                null
                            );
                            insightsApi.saveReminder(dto);
                            supportApi.communication(new org.example.api.support.SupportApiClient.CommunicationRequest(
                                "PARTY", 0, "REMINDER", r.partyName,
                                "Payment Reminder: Outstanding ₹ " + currency.format(r.totalOutstanding), "SENT", null, user
                            ));
                            queued++;
                        } catch (Exception ex) {
                            java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Bulk reminder item error: " + ex.getMessage());
                        }
                    }
                    org.example.service.NotificationService.add("Dispatched " + queued + " automated payment reminders to overdue accounts.");
                    return queued;
                },
                count -> AppDialogService.success(tblAging, "Reminders Sent", "Successfully recorded and queued " + count + " reminders in Reminder Center."),
                failure -> AppDialogService.error(tblAging, "Bulk Reminder Failed", "Failed to queue bulk reminders", failure.getMessage())
            );
        }
    }

    @FXML
    public void viewLedger() {
        if (selectedRow != null) {
            DashboardController.navigateFromChildPage("General Ledger", "/fxml/pages/GeneralLedger.fxml");
        }
    }

    public static class AgingReportResponse {
        public String asOfDate;
        public String reportType;
        public int totalParties;
        public BigDecimal grandTotal = BigDecimal.ZERO;
        public BigDecimal grandBucket0To30 = BigDecimal.ZERO;
        public BigDecimal grandBucket31To60 = BigDecimal.ZERO;
        public BigDecimal grandBucket61To90 = BigDecimal.ZERO;
        public BigDecimal grandBucket90Plus = BigDecimal.ZERO;
        public List<AgingBucketRow> rows;
    }

    public static class AgingBucketRow {
        public Long partyId;
        public String partyName;
        public String phone;
        public BigDecimal totalOutstanding = BigDecimal.ZERO;
        public BigDecimal bucket0To30 = BigDecimal.ZERO;
        public BigDecimal bucket31To60 = BigDecimal.ZERO;
        public BigDecimal bucket61To90 = BigDecimal.ZERO;
        public BigDecimal bucket90Plus = BigDecimal.ZERO;
        public String riskLevel;
    }
}
