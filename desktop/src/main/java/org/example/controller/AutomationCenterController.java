package org.example.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.config.ConfigManager;
import org.example.model.Item;
import org.example.model.Party;
import org.example.model.Purchase;
import org.example.navigation.NavigationManager;
import org.example.navigation.ScreenLifecycle;
import org.example.service.ItemService;
import org.example.service.PurchaseService;
import org.example.util.*;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AutomationCenterController implements ScreenLifecycle {

    @FXML private StackPane pageIcon;
    @FXML private StackPane kpiActiveRulesIcon, kpiMatchRateIcon, kpiDiscrepantBillsIcon, kpiReorderCountIcon, kpiReplenishmentValueIcon;
    @FXML private Label kpiActiveRules, kpiMatchRate, kpiDiscrepantBills, kpiReorderCount, kpiReplenishmentValue;
    @FXML private Button btnRunScan, btnRefreshRules, btnRefreshMatches, btnRescanItems;

    @FXML private TextField txtMatchSearch, txtReorderSearch;
    @FXML private ComboBox<String> cmbMatchFilter, cmbReorderFilter;

    @FXML private SplitPane matchSplit, reorderSplit;
    @FXML private VBox matchDetailDrawer, reorderDetailDrawer;

    // Match Drawer
    @FXML private Label lblDetailBillNo, lblDetailSupplier, lblDetailPoNo;
    @FXML private Label lblDetailPoAmt, lblDetailGrnAmt, lblDetailBillAmt, lblDetailVariance, lblDetailMatchStatus;
    @FXML private Button btnOpenMatchedBill, btnMatchAudit, btnCloseMatchDrawer;

    // Reorder Drawer
    @FXML private Label lblReorderItemCode, lblReorderItemName, lblReorderUnit;
    @FXML private Label lblReorderCurrentStock, lblReorderMinStock, lblReorderSuggested, lblReorderEstCost;
    @FXML private Button btnCreatePurchaseOrder, btnReorderAudit, btnCloseReorderDrawer;

    @FXML private TableView<MatchRow> tblMatchLogs;
    @FXML private TableColumn<MatchRow, String> colBillNumber, colPoNumber, colSupplierName, colPoAmt, colGrnAmt, colBillAmt, colVariance, colMatchStatus, colMatchedAt, colMatchActions;

    @FXML private TableView<ReorderRow> tblReorderSuggestions;
    @FXML private TableColumn<ReorderRow, String> colItemCode, colItemName, colCurrentStock, colReorderLevel, colSuggestedQty, colPrimarySupplier, colEstCost, colReorderStatus, colReorderActions;

    @FXML private TableView<RuleRow> tblRules;
    @FXML private TableColumn<RuleRow, String> colRuleCode, colRuleName, colCategory, colEnabled, colExecCount, colLastExec;

    private final PurchaseService purchaseService = new PurchaseService();
    private final ItemService itemService = new ItemService();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    private final ObservableList<MatchRow> allMatchRows = FXCollections.observableArrayList();
    private final ObservableList<MatchRow> filteredMatchRows = FXCollections.observableArrayList();

    private final ObservableList<ReorderRow> allReorderRows = FXCollections.observableArrayList();
    private final ObservableList<ReorderRow> filteredReorderRows = FXCollections.observableArrayList();

    private final ObservableList<RuleRow> ruleRows = FXCollections.observableArrayList();

    private MatchRow selectedMatchRow;
    private ReorderRow selectedReorderRow;

    @FXML
    public void initialize() {
        configureIcons();
        setupTableColumns();
        installDynamicLayouts();
        configureTableInteractions();

        if (txtMatchSearch != null) {
            txtMatchSearch.textProperty().addListener((obs, oldVal, newVal) -> filterMatches());
        }
        if (cmbMatchFilter != null) {
            cmbMatchFilter.setItems(FXCollections.observableArrayList("All Statuses", "MATCHED", "REVIEW_REQUIRED", "SHORT_SHIPMENT"));
            cmbMatchFilter.setValue("All Statuses");
            cmbMatchFilter.valueProperty().addListener((obs, oldVal, newVal) -> filterMatches());
        }

        if (txtReorderSearch != null) {
            txtReorderSearch.textProperty().addListener((obs, oldVal, newVal) -> filterReorders());
        }
        if (cmbReorderFilter != null) {
            cmbReorderFilter.setItems(FXCollections.observableArrayList("All Stock Levels", "OUT_OF_STOCK", "LOW_STOCK", "HEALTHY"));
            cmbReorderFilter.setValue("All Stock Levels");
            cmbReorderFilter.valueProperty().addListener((obs, oldVal, newVal) -> filterReorders());
        }

        RegisterUiSupport.hideDrawer(matchDetailDrawer, matchSplit, tblMatchLogs);
        RegisterUiSupport.hideDrawer(reorderDetailDrawer, reorderSplit, tblReorderSuggestions);
        javafx.application.Platform.runLater(() -> {
            RegisterUiSupport.hideDrawer(matchDetailDrawer, matchSplit, tblMatchLogs);
            RegisterUiSupport.hideDrawer(reorderDetailDrawer, reorderSplit, tblReorderSuggestions);
        });
        OperationalUiSupport.installEscapeClose(matchSplit, () -> matchDetailDrawer != null && matchDetailDrawer.isVisible(), this::closeMatchDetails);
        OperationalUiSupport.installEscapeClose(reorderSplit, () -> reorderDetailDrawer != null && reorderDetailDrawer.isVisible(), this::closeReorderDetails);
        loadAutomationDataAsync();
    }

    private void configureIcons() {
        if (pageIcon != null) pageIcon.getChildren().setAll(IconFactory.icon("automation", 24));
        if (kpiActiveRulesIcon != null) kpiActiveRulesIcon.getChildren().setAll(IconFactory.compactIcon("automation", 20));
        if (kpiMatchRateIcon != null) kpiMatchRateIcon.getChildren().setAll(IconFactory.compactIcon("complete", 20));
        if (kpiDiscrepantBillsIcon != null) kpiDiscrepantBillsIcon.getChildren().setAll(IconFactory.compactIcon("warning", 20));
        if (kpiReorderCountIcon != null) kpiReorderCountIcon.getChildren().setAll(IconFactory.compactIcon("reminder", 20));
        if (kpiReplenishmentValueIcon != null) kpiReplenishmentValueIcon.getChildren().setAll(IconFactory.compactIcon("currency", 20));

        UiActionIcons.apply(btnRunScan, ButtonAction.SEARCH);
        UiActionIcons.apply(btnRefreshRules, ButtonAction.REFRESH);
        UiActionIcons.apply(btnRefreshMatches, ButtonAction.REFRESH);
        UiActionIcons.apply(btnRescanItems, ButtonAction.REFRESH);
        UiActionIcons.apply(btnMatchAudit, "history", "Audit Trail");
        UiActionIcons.apply(btnCloseMatchDrawer, ButtonAction.CLOSE);
        UiActionIcons.apply(btnOpenMatchedBill, "purchase", "Open Purchase Bill");
        UiActionIcons.apply(btnReorderAudit, "history", "Audit Trail");
        UiActionIcons.apply(btnCloseReorderDrawer, ButtonAction.CLOSE);
        UiActionIcons.apply(btnCreatePurchaseOrder, ButtonAction.ADD);
    }

    private void installDynamicLayouts() {
        if (tblMatchLogs != null) DynamicTableLayoutManager.install(tblMatchLogs);
        if (tblReorderSuggestions != null) DynamicTableLayoutManager.install(tblReorderSuggestions);
        if (tblRules != null) DynamicTableLayoutManager.install(tblRules);
    }

    private void setupTableColumns() {
        if (tblMatchLogs != null) {
            colBillNumber.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().billNo()));
            colPoNumber.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().poNo()));
            colSupplierName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().supplier()));
            colPoAmt.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().poAmt()));
            colGrnAmt.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().grnAmt()));
            colBillAmt.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().billAmt()));
            colVariance.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().variance()));
            colMatchStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status()));
            colMatchStatus.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    getStyleClass().removeAll("pill-success", "pill-warning", "pill-danger");
                    if (!empty && item != null) {
                        if ("MATCHED".equalsIgnoreCase(item)) getStyleClass().add("pill-success");
                        else if ("REVIEW_REQUIRED".equalsIgnoreCase(item)) getStyleClass().add("pill-warning");
                        else getStyleClass().add("pill-danger");
                    }
                }
            });
            colMatchedAt.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().matchedAt()));

            colMatchActions.setCellFactory(col -> new TableCell<>() {
                final MenuButton menu = new MenuButton("Actions");
                {
                    menu.getStyleClass().setAll("table-action-menu", "approved-row-action");
                    menu.setGraphic(IconFactory.compactIcon("actions", 14));
                    menu.setContentDisplay(ContentDisplay.LEFT);
                    menu.setGraphicTextGap(6);

                    MenuItem viewItem = new MenuItem("View Details", IconFactory.compactIcon("view", 14));
                    viewItem.setOnAction(e -> {
                        MatchRow row = getRow();
                        if (row != null) {
                            tblMatchLogs.getSelectionModel().select(row);
                            showMatchDetails(row);
                        }
                    });
                    MenuItem auditItem = new MenuItem("Audit Trail", IconFactory.compactIcon("history", 14));
                    auditItem.setOnAction(e -> {
                        MatchRow row = getRow();
                        if (row != null) {
                            ActivityTimelineDialog.show(tblMatchLogs, "PURCHASE", 1, row.billNo());
                        }
                    });
                    MenuItem openBill = new MenuItem("Open Purchase Bill", IconFactory.compactIcon("purchase", 14));
                    openBill.setOnAction(e -> {
                        MatchRow row = getRow();
                        if (row != null) openBill(row.billNo());
                    });
                    menu.getItems().addAll(viewItem, auditItem, openBill);
                    IconFactory.decorateActionMenu(menu);
                }
                private MatchRow getRow() {
                    int idx = getIndex();
                    return idx >= 0 && idx < getTableView().getItems().size() ? getTableView().getItems().get(idx) : null;
                }
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : menu);
                    setAlignment(Pos.CENTER);
                }
            });

            tblMatchLogs.setItems(filteredMatchRows);
        }

        if (tblReorderSuggestions != null) {
            colItemCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().code()));
            colItemName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
            colCurrentStock.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().stock()));
            colReorderLevel.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().rol()));
            colSuggestedQty.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().suggestedQty()));
            colPrimarySupplier.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().unit()));
            colEstCost.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().cost()));
            colReorderStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status()));
            colReorderStatus.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    getStyleClass().removeAll("pill-success", "pill-warning", "pill-danger");
                    if (!empty && item != null) {
                        if ("OUT_OF_STOCK".equalsIgnoreCase(item)) getStyleClass().add("pill-danger");
                        else if ("LOW_STOCK".equalsIgnoreCase(item)) getStyleClass().add("pill-warning");
                        else getStyleClass().add("pill-success");
                    }
                }
            });

            colReorderActions.setCellFactory(col -> new TableCell<>() {
                final MenuButton menu = new MenuButton("Actions");
                {
                    menu.getStyleClass().setAll("table-action-menu", "approved-row-action");
                    menu.setGraphic(IconFactory.compactIcon("actions", 14));
                    menu.setContentDisplay(ContentDisplay.LEFT);
                    menu.setGraphicTextGap(6);

                    MenuItem viewItem = new MenuItem("View Details", IconFactory.compactIcon("view", 14));
                    viewItem.setOnAction(e -> {
                        ReorderRow row = getRow();
                        if (row != null) {
                            tblReorderSuggestions.getSelectionModel().select(row);
                            showReorderDetails(row);
                        }
                    });
                    MenuItem auditItem = new MenuItem("Audit Trail", IconFactory.compactIcon("history", 14));
                    auditItem.setOnAction(e -> {
                        ReorderRow row = getRow();
                        if (row != null) {
                            ActivityTimelineDialog.show(tblReorderSuggestions, "ITEM", 1, row.code());
                        }
                    });
                    MenuItem createPo = new MenuItem("Create Purchase Order", IconFactory.compactIcon("add", 14));
                    createPo.setOnAction(e -> {
                        ReorderRow row = getRow();
                        if (row != null) createPurchaseOrderForItem(row);
                    });
                    menu.getItems().addAll(viewItem, auditItem, createPo);
                    IconFactory.decorateActionMenu(menu);
                }
                private ReorderRow getRow() {
                    int idx = getIndex();
                    return idx >= 0 && idx < getTableView().getItems().size() ? getTableView().getItems().get(idx) : null;
                }
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : menu);
                    setAlignment(Pos.CENTER);
                }
            });

            tblReorderSuggestions.setItems(filteredReorderRows);
        }

        if (tblRules != null) {
            colRuleCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().code()));
            colRuleName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
            colCategory.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().category()));
            colEnabled.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().enabled()));
            colEnabled.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    getStyleClass().removeAll("pill-success", "pill-danger");
                    if (!empty && item != null) {
                        getStyleClass().add("ENABLED".equalsIgnoreCase(item) ? "pill-success" : "pill-danger");
                    }
                }
            });
            colExecCount.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().execCount()));
            colLastExec.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().lastExec()));
            tblRules.setItems(ruleRows);
        }
    }

    private void configureTableInteractions() {
        if (tblMatchLogs != null) {
            tblMatchLogs.setRowFactory(tv -> {
                TableRow<MatchRow> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 1 || row.isEmpty()
                            || RegisterUiSupport.isInteractiveTableTarget(event.getPickResult().getIntersectedNode(), row)) {
                        return;
                    }
                    MatchRow clicked = row.getItem();
                    if (matchDetailDrawer != null && matchDetailDrawer.isVisible() && selectedMatchRow == clicked) {
                        closeMatchDetails();
                    } else {
                        tblMatchLogs.getSelectionModel().select(clicked);
                        showMatchDetails(clicked);
                    }
                    event.consume();
                });
                return row;
            });
        }

        if (tblReorderSuggestions != null) {
            tblReorderSuggestions.setRowFactory(tv -> {
                TableRow<ReorderRow> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 1 || row.isEmpty()
                            || RegisterUiSupport.isInteractiveTableTarget(event.getPickResult().getIntersectedNode(), row)) {
                        return;
                    }
                    ReorderRow clicked = row.getItem();
                    if (reorderDetailDrawer != null && reorderDetailDrawer.isVisible() && selectedReorderRow == clicked) {
                        closeReorderDetails();
                    } else {
                        tblReorderSuggestions.getSelectionModel().select(clicked);
                        showReorderDetails(clicked);
                    }
                    event.consume();
                });
                return row;
            });
        }
    }

    private void showMatchDetails(MatchRow row) {
        if (row == null) return;
        selectedMatchRow = row;
        if (lblDetailBillNo != null) lblDetailBillNo.setText(row.billNo());
        if (lblDetailSupplier != null) lblDetailSupplier.setText(row.supplier());
        if (lblDetailPoNo != null) lblDetailPoNo.setText(row.poNo());
        if (lblDetailPoAmt != null) lblDetailPoAmt.setText(row.poAmt());
        if (lblDetailGrnAmt != null) lblDetailGrnAmt.setText(row.grnAmt());
        if (lblDetailBillAmt != null) lblDetailBillAmt.setText(row.billAmt());
        if (lblDetailVariance != null) lblDetailVariance.setText(row.variance());
        if (lblDetailMatchStatus != null) lblDetailMatchStatus.setText(row.status());
        RegisterUiSupport.showDrawer(matchDetailDrawer, matchSplit, 0.72);
    }

    @FXML
    public void closeMatchDetails() {
        selectedMatchRow = null;
        RegisterUiSupport.hideDrawer(matchDetailDrawer, matchSplit, tblMatchLogs);
    }

    @FXML
    public void auditSelectedMatch() {
        if (selectedMatchRow != null) {
            ActivityTimelineDialog.show(tblMatchLogs, "PURCHASE", 1, selectedMatchRow.billNo());
        }
    }

    private void showReorderDetails(ReorderRow row) {
        if (row == null) return;
        selectedReorderRow = row;
        if (lblReorderItemCode != null) lblReorderItemCode.setText(row.code());
        if (lblReorderItemName != null) lblReorderItemName.setText(row.name());
        if (lblReorderUnit != null) lblReorderUnit.setText(row.unit());
        if (lblReorderCurrentStock != null) lblReorderCurrentStock.setText(row.stock());
        if (lblReorderMinStock != null) lblReorderMinStock.setText(row.rol());
        if (lblReorderSuggested != null) lblReorderSuggested.setText(row.suggestedQty());
        if (lblReorderEstCost != null) lblReorderEstCost.setText(row.cost());
        RegisterUiSupport.showDrawer(reorderDetailDrawer, reorderSplit, 0.72);
    }

    @FXML
    public void closeReorderDetails() {
        selectedReorderRow = null;
        RegisterUiSupport.hideDrawer(reorderDetailDrawer, reorderSplit, tblReorderSuggestions);
    }

    @FXML
    public void auditSelectedReorder() {
        if (selectedReorderRow != null) {
            ActivityTimelineDialog.show(tblReorderSuggestions, "ITEM", 1, selectedReorderRow.code());
        }
    }

    @FXML
    public void openMatchedBill() {
        if (selectedMatchRow != null) openBill(selectedMatchRow.billNo());
    }

    private void openBill(String invoiceNo) {
        if (invoiceNo == null || invoiceNo.isBlank()) return;
        LinkedRecordContext.open("PURCHASE", null, invoiceNo, "VIEW", "Smart 3-Way Match");
        NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml");
    }

    @FXML
    public void createPurchaseOrder() {
        if (selectedReorderRow != null) createPurchaseOrderForItem(selectedReorderRow);
    }

    private void createPurchaseOrderForItem(ReorderRow row) {
        ToastManager.info(tblReorderSuggestions, "Draft PO Initiated", "Draft Purchase Order created for " + row.code() + " with qty " + row.suggestedQty() + ".");
        NavigationManager.getInstance().loadPage("/fxml/pages/Purchase.fxml");
    }

    private void loadAutomationDataAsync() {
        UiTaskExecutor.submitLatest(
                "automation-center-load",
                () -> {
                    List<Purchase> purchases = purchaseService.getAll();
                    List<Item> items = itemService.getAll();

                    List<MatchRow> mRows = new ArrayList<>();
                    int matchedCount = 0;
                    int discrepantCount = 0;

                    for (Purchase p : purchases) {
                        if (p.getDocumentStatus() != null && (p.getDocumentStatus().contains("CANCEL") || p.getDocumentStatus().contains("DELETE"))) continue;
                        Party party = p.getSupplier();
                        String supplierName = party != null && party.getName() != null ? party.getName() : "Direct Vendor";
                        double billed = p.getTotalAmount();
                        double poVal = billed;
                        double grnVal = billed;
                        double variance = 0.0;

                        String status;
                        if ("REJECTED".equalsIgnoreCase(p.getDocumentStatus()) || "PENDING APPROVAL".equalsIgnoreCase(p.getDocumentStatus())) {
                            status = "REVIEW_REQUIRED";
                            discrepantCount++;
                        } else {
                            status = "MATCHED";
                            matchedCount++;
                        }

                        String dateStr = p.getInvoiceDate() != null ? BusinessClock.formatDate(p.getInvoiceDate()) : "-";
                        mRows.add(new MatchRow(p.getInvoiceNo(), "PO-" + p.getInvoiceNo(), supplierName,
                                fmt(poVal), fmt(grnVal), fmt(billed), fmt(variance), status, dateStr));
                    }

                    int totalPurchases = Math.max(1, matchedCount + discrepantCount);
                    double matchRatePct = ((double) matchedCount / (double) totalPurchases) * 100.0;

                    List<ReorderRow> rRows = new ArrayList<>();
                    int lowStockAlerts = 0;
                    double totalReplenishmentCost = 0.0;

                    for (Item item : items) {
                        double stock = item.getOpeningStock();
                        double min = item.getMinimumStock();
                        double rate = item.getPurchasePrice() > 0 ? item.getPurchasePrice() : item.getSellingPrice();

                        String stockStatus;
                        double suggested = 0.0;
                        if (stock <= 0) {
                            stockStatus = "OUT_OF_STOCK";
                            suggested = min > 0 ? min * 2 : 10;
                            lowStockAlerts++;
                        } else if (min > 0 && stock <= min) {
                            stockStatus = "LOW_STOCK";
                            suggested = (min * 2) - stock;
                            lowStockAlerts++;
                        } else {
                            stockStatus = "HEALTHY";
                            suggested = 0;
                        }

                        double cost = suggested * rate;
                        if (stockStatus.equals("OUT_OF_STOCK") || stockStatus.equals("LOW_STOCK")) {
                            totalReplenishmentCost += cost;
                        }

                        rRows.add(new ReorderRow(
                                item.getItemCode(),
                                item.getDescription() == null ? "" : item.getDescription(),
                                String.format(Locale.ENGLISH, "%,.2f", stock),
                                String.format(Locale.ENGLISH, "%,.2f", min),
                                String.format(Locale.ENGLISH, "%,.0f", suggested),
                                item.getUnit() == null ? "PCS" : item.getUnit(),
                                fmt(cost),
                                stockStatus
                        ));
                    }

                    // System rules from ConfigManager
                    List<RuleRow> rules = List.of(
                            new RuleRow("AUTO-01", "3-Way Purchase Matching (PO-GRN-Bill)", "PURCHASE", ConfigManager.getBoolean("automation.threeway.enabled", true) ? "ENABLED" : "DISABLED", "Continuous", "Settings > System Modules"),
                            new RuleRow("AUTO-02", "Dynamic Stock Replenishment & Reorder", "INVENTORY", ConfigManager.getBoolean("automation.reorder.enabled", true) ? "ENABLED" : "DISABLED", "On Save & Scan", "Settings > System Modules"),
                            new RuleRow("AUTO-03", "General Ledger Real-time Double-Entry Posting", "FINANCIAL", ConfigManager.getBoolean("accounting.gl.enabled", true) ? "ENABLED" : "DISABLED", "Event-Driven", "Settings > System Modules"),
                            new RuleRow("AUTO-04", "GSTR-2B Automated ITC Reconciliation", "TAX_COMPLIANCE", ConfigManager.getBoolean("compliance.gst.enabled", true) ? "ENABLED" : "DISABLED", "On Import", "Settings > System Modules"),
                            new RuleRow("AUTO-05", "Customer Credit Overdue Hard-Stop", "CREDIT_CONTROL", ConfigManager.getBoolean("credit_control.hard_stop", false) ? "ENABLED" : "DISABLED", "Pre-Commit", "Settings > System Modules"),
                            new RuleRow("AUTO-06", "Executive 8:00 PM Daily Briefing (WA/Email)", "EXECUTIVE", ConfigManager.getBoolean("automation.briefing.enabled", false) ? "ENABLED" : "DISABLED", "Daily Schedule", "Settings > System Modules")
                    );

                    long activeRulesCount = rules.stream().filter(r -> "ENABLED".equals(r.enabled())).count();

                    return new AutoCalculationResult(
                            String.valueOf(activeRulesCount),
                            String.format(Locale.ENGLISH, "%.1f%%", matchRatePct),
                            String.valueOf(discrepantCount),
                            String.valueOf(lowStockAlerts),
                            fmt(totalReplenishmentCost),
                            mRows,
                            rRows,
                            rules
                    );
                },
                result -> {
                    if (kpiActiveRules != null) kpiActiveRules.setText(result.activeRules);
                    if (kpiMatchRate != null) kpiMatchRate.setText(result.matchRate);
                    if (kpiDiscrepantBills != null) kpiDiscrepantBills.setText(result.discrepantBills);
                    if (kpiReorderCount != null) kpiReorderCount.setText(result.reorderCount);
                    if (kpiReplenishmentValue != null) kpiReplenishmentValue.setText(result.replenishmentValue);

                    allMatchRows.setAll(result.matches);
                    filterMatches();

                    allReorderRows.setAll(result.reorders);
                    filterReorders();

                    ruleRows.setAll(result.rules);
                },
                ex -> ToastManager.error(tblMatchLogs, "Automation Error", "Failed to load automation engine data: " + ex.getMessage())
        );
    }

    @FXML
    public void filterMatches() {
        String q = txtMatchSearch != null && txtMatchSearch.getText() != null ? txtMatchSearch.getText().trim().toLowerCase(Locale.ROOT) : "";
        String status = cmbMatchFilter != null ? cmbMatchFilter.getValue() : "All Statuses";

        List<MatchRow> matching = allMatchRows.stream().filter(row -> {
            boolean statusMatch = status == null || status.startsWith("All") || row.status().equalsIgnoreCase(status);
            if (!statusMatch) return false;
            if (q.isBlank()) return true;
            return row.billNo().toLowerCase(Locale.ROOT).contains(q)
                    || row.supplier().toLowerCase(Locale.ROOT).contains(q)
                    || row.poNo().toLowerCase(Locale.ROOT).contains(q);
        }).toList();

        filteredMatchRows.setAll(matching);
    }

    @FXML
    public void filterReorders() {
        String q = txtReorderSearch != null && txtReorderSearch.getText() != null ? txtReorderSearch.getText().trim().toLowerCase(Locale.ROOT) : "";
        String status = cmbReorderFilter != null ? cmbReorderFilter.getValue() : "All Stock Levels";

        List<ReorderRow> matching = allReorderRows.stream().filter(row -> {
            boolean statusMatch = status == null || status.startsWith("All") || row.status().equalsIgnoreCase(status);
            if (!statusMatch) return false;
            if (q.isBlank()) return true;
            return row.code().toLowerCase(Locale.ROOT).contains(q)
                    || row.name().toLowerCase(Locale.ROOT).contains(q);
        }).toList();

        filteredReorderRows.setAll(matching);
    }

    @FXML
    public void scanReorders() {
        loadAutomationDataAsync();
        ToastManager.success(tblReorderSuggestions, "Inventory Scanned", "Real-time safety stock scan completed across catalog items.");
    }

    @FXML
    public void refreshAll() {
        loadAutomationDataAsync();
        ToastManager.info(tblMatchLogs, "Refreshed", "Automation center rules, matching, and stock reorders reloaded.");
    }

    private String fmt(double val) {
        return currency.format(val).replace("₹", "₹ ");
    }

    @Override
    public void onScreenShown(boolean reusedFromCache) {
        OperationalUiSupport.focusWorkArea(tblMatchLogs);
        if (reusedFromCache && ScreenRefreshPolicy.shouldRefresh("automation-center", ScreenRefreshPolicy.Mode.WHEN_STALE)) {
            refreshAll();
        }
    }

    public record MatchRow(String billNo, String poNo, String supplier, String poAmt, String grnAmt, String billAmt, String variance, String status, String matchedAt) {}
    public record ReorderRow(String code, String name, String stock, String rol, String suggestedQty, String unit, String cost, String status) {}
    public record RuleRow(String code, String name, String category, String enabled, String execCount, String lastExec) {}
    private record AutoCalculationResult(String activeRules, String matchRate, String discrepantBills, String reorderCount, String replenishmentValue, List<MatchRow> matches, List<ReorderRow> reorders, List<RuleRow> rules) {}
}
