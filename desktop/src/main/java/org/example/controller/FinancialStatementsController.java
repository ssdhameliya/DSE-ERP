package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.api.ApiRuntime;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;
import org.example.navigation.ScreenLifecycle;
import org.example.service.BrandedRegisterPdfService;
import org.example.util.*;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FinancialStatementsController implements ScreenLifecycle {

    @FXML private StackPane pageIcon;
    @FXML private StackPane kpiRevenueIcon, kpiCogsIcon, kpiGrossProfitIcon, kpiNetProfitIcon;
    @FXML private Label kpiRevenue, kpiCogs, kpiGrossProfit, kpiNetProfit, lblGrossMargin, lblNetMargin;

    @FXML private ComboBox<String> cmbEngineMode;
    @FXML private DatePicker dpFromDate, dpToDate;
    @FXML private Button btnApplyDate, btnExportPdf, btnExportExcel;

    @FXML private Label lblCurrentRatio, lblQuickRatio, lblDso, lblDpo, lblWorkingCapital;

    @FXML private TabPane tabStatements;
    @FXML private Tab tabPnl, tabBalanceSheet, tabRatios;

    @FXML private TextField txtSearchPnl, txtSearchBs;
    @FXML private Button btnDrillDownPnl, btnDrillDownBs, btnExpandAllPnl, btnCollapseAllPnl;

    @FXML private TreeTableView<StatementItemRow> treeTblPnl;
    @FXML private TreeTableColumn<StatementItemRow, String> colPnlAccount, colPnlCode, colPnlType, colPnlAmount, colPnlPercentage;

    @FXML private Label lblTotalAssets, lblTotalLiabEquity, lblBsEquilibrium;
    @FXML private TreeTableView<StatementItemRow> treeTblBs;
    @FXML private TreeTableColumn<StatementItemRow, String> colBsClassification, colBsCode, colBsGroup, colBsAmount;

    @FXML private TableView<RatioRow> tblRatios;
    @FXML private TableColumn<RatioRow, String> colRatioCategory, colRatioMetric, colRatioValue, colRatioBenchmark, colRatioStatus;

    private final ObservableList<RatioRow> ratioRows = FXCollections.observableArrayList();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    public void initialize() {
        if (pageIcon != null) pageIcon.getChildren().setAll(IconFactory.icon("report", 22));
        if (kpiRevenueIcon != null) kpiRevenueIcon.getChildren().setAll(IconFactory.compactIcon("wallet", 16));
        if (kpiCogsIcon != null) kpiCogsIcon.getChildren().setAll(IconFactory.compactIcon("inventory", 16));
        if (kpiGrossProfitIcon != null) kpiGrossProfitIcon.getChildren().setAll(IconFactory.compactIcon("chart", 16));
        if (kpiNetProfitIcon != null) kpiNetProfitIcon.getChildren().setAll(IconFactory.compactIcon("dashboard", 16));

        if (cmbEngineMode != null) {
            cmbEngineMode.setItems(FXCollections.observableArrayList("Live Operational (Real-time)", "Canonical General Ledger"));
            cmbEngineMode.setValue("Live Operational (Real-time)");
            cmbEngineMode.valueProperty().addListener((obs, oldVal, newVal) -> refreshStatements());
        }

        LocalDate now = LocalDate.now();
        int fyStartYear = now.getMonthValue() >= 4 ? now.getYear() : now.getYear() - 1;
        dpFromDate.setValue(LocalDate.of(fyStartYear, 4, 1));
        dpToDate.setValue(now);

        configurePnlColumns();
        configureBsColumns();
        configureRatioColumns();

        RealtimeSearchSupport.installLocal(txtSearchPnl, () -> filterTree(treeTblPnl, txtSearchPnl.getText()));
        RealtimeSearchSupport.installLocal(txtSearchBs, () -> filterTree(treeTblBs, txtSearchBs.getText()));

        ContextMenu pnlContextMenu = new ContextMenu();
        MenuItem miPnlDrill = new MenuItem("Inspect / Drill-Down Line Item", IconFactory.compactIcon("view", 14));
        miPnlDrill.setOnAction(e -> drillDownSelectedPnl());
        MenuItem miPnlLedger = new MenuItem("View General Ledger", IconFactory.compactIcon("history", 14));
        miPnlLedger.setOnAction(e -> {
            TreeItem<StatementItemRow> selected = treeTblPnl.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() != null && selected.getValue().accountCode != null) {
                drillDownToLedger(selected.getValue().accountCode);
            }
        });
        pnlContextMenu.getItems().addAll(miPnlDrill, miPnlLedger);
        treeTblPnl.setContextMenu(pnlContextMenu);

        ContextMenu bsContextMenu = new ContextMenu();
        MenuItem miBsDrill = new MenuItem("Inspect / Drill-Down Line Item", IconFactory.compactIcon("view", 14));
        miBsDrill.setOnAction(e -> drillDownSelectedBs());
        MenuItem miBsLedger = new MenuItem("View General Ledger", IconFactory.compactIcon("history", 14));
        miBsLedger.setOnAction(e -> {
            TreeItem<StatementItemRow> selected = treeTblBs.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() != null && selected.getValue().accountCode != null) {
                drillDownToLedger(selected.getValue().accountCode);
            }
        });
        bsContextMenu.getItems().addAll(miBsDrill, miBsLedger);
        treeTblBs.setContextMenu(bsContextMenu);

        refreshStatements();
    }

    @Override
    public void onScreenShown(boolean reusedFromCache) {
        refreshStatements();
    }

    private static String apiUrl(String path) {
        String base = ConfigManager.getDataApiBaseUrl();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + path;
    }

    private static StatementItemRow safeRow(TreeTableColumn.CellDataFeatures<StatementItemRow, String> p) {
        if (p == null || p.getValue() == null) return null;
        return p.getValue().getValue();
    }

    private void configurePnlColumns() {
        colPnlAccount.setCellValueFactory(p -> {
            StatementItemRow r = safeRow(p);
            return new SimpleStringProperty(r != null && r.name != null ? r.name : "");
        });
        colPnlCode.setCellValueFactory(p -> {
            StatementItemRow r = safeRow(p);
            return new SimpleStringProperty(r != null && r.accountCode != null ? r.accountCode : "");
        });
        colPnlType.setCellValueFactory(p -> {
            StatementItemRow r = safeRow(p);
            return new SimpleStringProperty(r != null && r.groupType != null ? r.groupType : "");
        });
        colPnlAmount.setCellValueFactory(p -> {
            StatementItemRow r = safeRow(p);
            return new SimpleStringProperty(r != null && r.amount != null ? currency.format(r.amount) : "");
        });
        colPnlPercentage.setCellValueFactory(p -> {
            StatementItemRow r = safeRow(p);
            return new SimpleStringProperty(r != null && r.percentage != null && !r.percentage.isBlank() ? r.percentage + "%" : "");
        });
    }

    private void configureBsColumns() {
        colBsClassification.setCellValueFactory(p -> {
            StatementItemRow r = safeRow(p);
            return new SimpleStringProperty(r != null && r.name != null ? r.name : "");
        });
        colBsCode.setCellValueFactory(p -> {
            StatementItemRow r = safeRow(p);
            return new SimpleStringProperty(r != null && r.accountCode != null ? r.accountCode : "");
        });
        colBsGroup.setCellValueFactory(p -> {
            StatementItemRow r = safeRow(p);
            return new SimpleStringProperty(r != null && r.groupType != null ? r.groupType : "");
        });
        colBsAmount.setCellValueFactory(p -> {
            StatementItemRow r = safeRow(p);
            return new SimpleStringProperty(r != null && r.amount != null ? currency.format(r.amount) : "");
        });
    }

    private void configureRatioColumns() {
        if (tblRatios == null) return;
        colRatioCategory.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().category()));
        colRatioMetric.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().metric()));
        colRatioValue.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().value()));
        colRatioBenchmark.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().benchmark()));
        colRatioStatus.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().status()));

        colRatioStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().removeAll("status-pill-green", "status-pill-blue", "status-pill-orange", "status-pill-red");
                if (!empty && item != null) {
                    if (item.contains("Optimal") || item.contains("Strong") || item.contains("Adequate")) {
                        getStyleClass().add("status-pill-green");
                    } else if (item.contains("Watch") || item.contains("Review")) {
                        getStyleClass().add("status-pill-orange");
                    } else {
                        getStyleClass().add("status-pill-blue");
                    }
                }
            }
        });

        tblRatios.setItems(ratioRows);
        DynamicTableLayoutManager.install(tblRatios);
    }

    @FXML
    public void refreshStatements() {
        LocalDate from = dpFromDate.getValue();
        LocalDate to = dpToDate.getValue();
        if (from == null) from = LocalDate.now().minusMonths(1);
        if (to == null) to = LocalDate.now();

        LocalDate finalFrom = from;
        LocalDate finalTo = to;
        String engineMode = (cmbEngineMode != null && cmbEngineMode.getValue() != null && cmbEngineMode.getValue().contains("Canonical"))
                ? "CANONICAL_GL" : "OPERATIONAL";

        UiTaskExecutor.submitLatest(
            "financial-statements-fetch",
            () -> {
                // Try three-tier endpoint first
                String threeTierUrl = apiUrl("/api/financial/three-tier?fromDate=" + finalFrom + "&toDate=" + finalTo + "&engine=" + engineMode);
                HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(threeTierUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
                ApiSession.authorize(req);
                HttpResponse<String> resp = ApiRuntime.HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    ThreeTierResponse threeTier = ApiRuntime.JSON.readValue(resp.body(), ThreeTierResponse.class);
                    return new StatementsData(threeTier.profitAndLoss, threeTier.balanceSheet, threeTier.tradingAccount, threeTier.ratios);
                }

                // Fallback to individual endpoints
                String pnlUrl = apiUrl("/api/financial/profit-and-loss?fromDate=" + finalFrom + "&toDate=" + finalTo);
                HttpRequest.Builder pnlReq = HttpRequest.newBuilder(URI.create(pnlUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
                ApiSession.authorize(pnlReq);
                HttpResponse<String> pnlResp = ApiRuntime.HTTP.send(pnlReq.build(), HttpResponse.BodyHandlers.ofString());
                ProfitAndLossResponse pnlData = pnlResp.statusCode() == 200
                        ? ApiRuntime.JSON.readValue(pnlResp.body(), ProfitAndLossResponse.class) : null;

                String bsUrl = apiUrl("/api/financial/balance-sheet?asOfDate=" + finalTo);
                HttpRequest.Builder bsReq = HttpRequest.newBuilder(URI.create(bsUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
                ApiSession.authorize(bsReq);
                HttpResponse<String> bsResp = ApiRuntime.HTTP.send(bsReq.build(), HttpResponse.BodyHandlers.ofString());
                BalanceSheetResponse bsData = bsResp.statusCode() == 200
                        ? ApiRuntime.JSON.readValue(bsResp.body(), BalanceSheetResponse.class) : null;

                return new StatementsData(pnlData, bsData, null, null);
            },
            data -> {
                if (data.trading != null && data.pnl != null) {
                    populateThreeTierPnl(data.trading, data.pnl);
                } else if (data.pnl != null) {
                    populatePnl(data.pnl);
                }
                if (data.bs != null) populateBs(data.bs);
                if (data.ratios != null) populateRatios(data.ratios);
            },
            err -> AppDialogService.error(tabStatements, "Financials", "Failed to Load Financials", err.getMessage())
        );
    }

    private void populateThreeTierPnl(TradingAccountData trading, ProfitAndLossResponse pnl) {
        BigDecimal rev = trading.netSales != null && trading.netSales.compareTo(BigDecimal.ZERO) > 0 ? trading.netSales : BigDecimal.ONE;
        kpiRevenue.setText(currency.format(trading.netSales != null ? trading.netSales : BigDecimal.ZERO));
        kpiCogs.setText(currency.format(trading.cogs != null ? trading.cogs : BigDecimal.ZERO));
        kpiGrossProfit.setText(currency.format(trading.grossProfit != null ? trading.grossProfit : BigDecimal.ZERO));
        kpiNetProfit.setText(currency.format(pnl.netProfit != null ? pnl.netProfit : BigDecimal.ZERO));

        lblGrossMargin.setText("Margin: " + (trading.grossMarginPct != null ? trading.grossMarginPct : "0.0") + "%");
        BigDecimal netPct = (pnl.netProfit != null && trading.netSales != null && trading.netSales.compareTo(BigDecimal.ZERO) > 0)
                ? pnl.netProfit.multiply(new BigDecimal(100)).divide(rev, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        lblNetMargin.setText("Net Margin: " + netPct + "%");

        TreeItem<StatementItemRow> root = new TreeItem<>(new StatementItemRow("Root", null, null, null, null));
        root.setExpanded(true);

        // Tier 1: Trading Account
        TreeItem<StatementItemRow> tradingNode = new TreeItem<>(new StatementItemRow("1. TRADING ACCOUNT (GROSS TRADING MARGIN)", null, "Tier 1", trading.grossProfit, "100.0"));
        tradingNode.setExpanded(true);
        tradingNode.getChildren().add(new TreeItem<>(new StatementItemRow("Opening Stock", "1030-OP", "Stock", trading.openingStock, null)));
        tradingNode.getChildren().add(new TreeItem<>(new StatementItemRow("Gross Sales (Invoices)", "4000", "Revenue", trading.netSales, "100.0")));
        tradingNode.getChildren().add(new TreeItem<>(new StatementItemRow("Gross Purchases (Bills)", "5000", "Direct Cost", trading.netPurchases, null)));
        if (trading.directExpenses != null && trading.directExpenses.compareTo(BigDecimal.ZERO) > 0) {
            tradingNode.getChildren().add(new TreeItem<>(new StatementItemRow("Direct Inward Freight & Labor", "5010", "Direct Cost", trading.directExpenses, null)));
        }
        tradingNode.getChildren().add(new TreeItem<>(new StatementItemRow("Closing Inventory Stock", "1030-CL", "Stock Valuation", trading.closingStock, null)));
        tradingNode.getChildren().add(new TreeItem<>(new StatementItemRow("Cost of Goods Sold (COGS)", "5099", "COGS", trading.cogs, null)));
        tradingNode.getChildren().add(new TreeItem<>(new StatementItemRow("GROSS PROFIT C/F", "4099", "Trading Margin", trading.grossProfit, String.valueOf(trading.grossMarginPct))));
        root.getChildren().add(tradingNode);

        // Tier 2: Operating Overheads & Indirect Expenses
        TreeItem<StatementItemRow> opexNode = new TreeItem<>(new StatementItemRow("2. OPERATING OVERHEADS & INDIRECT EXPENSES", null, "Tier 2", pnl.operatingExpenses, null));
        opexNode.setExpanded(true);
        if (pnl.expenseAccounts != null && !pnl.expenseAccounts.isEmpty()) {
            for (FinancialAccountRow a : pnl.expenseAccounts) {
                BigDecimal pct = rev.compareTo(BigDecimal.ONE) > 0 && a.amount != null
                        ? a.amount.multiply(new BigDecimal(100)).divide(rev, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                opexNode.getChildren().add(new TreeItem<>(new StatementItemRow(a.accountName, a.accountCode, a.accountGroup, a.amount, pct.toString())));
            }
        } else {
            opexNode.getChildren().add(new TreeItem<>(new StatementItemRow("Administrative & General Overhead", "6010", "Operating Expense", BigDecimal.ZERO, "0.0")));
        }
        root.getChildren().add(opexNode);

        // Tier 3: Bottom-line Net Profit
        TreeItem<StatementItemRow> bottomNode = new TreeItem<>(new StatementItemRow("3. NET OPERATING PROFIT / (LOSS)", null, "Tier 3", pnl.netProfit, String.valueOf(netPct)));
        bottomNode.setExpanded(true);
        root.getChildren().add(bottomNode);

        treeTblPnl.setRoot(root);
        treeTblPnl.setShowRoot(false);
    }

    private void populatePnl(ProfitAndLossResponse pnl) {
        kpiRevenue.setText(currency.format(pnl.totalRevenue != null ? pnl.totalRevenue : BigDecimal.ZERO));
        kpiCogs.setText(currency.format(pnl.costOfGoodsSold != null ? pnl.costOfGoodsSold : BigDecimal.ZERO));
        kpiGrossProfit.setText(currency.format(pnl.grossProfit != null ? pnl.grossProfit : BigDecimal.ZERO));
        kpiNetProfit.setText(currency.format(pnl.netProfit != null ? pnl.netProfit : BigDecimal.ZERO));

        BigDecimal rev = pnl.totalRevenue != null && pnl.totalRevenue.compareTo(BigDecimal.ZERO) > 0 ? pnl.totalRevenue : BigDecimal.ONE;
        if (pnl.grossProfit != null && pnl.totalRevenue != null && pnl.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal gm = pnl.grossProfit.multiply(new BigDecimal(100)).divide(rev, 1, RoundingMode.HALF_UP);
            lblGrossMargin.setText("Margin: " + gm + "%");
        } else {
            lblGrossMargin.setText("Margin: 0.0%");
        }

        if (pnl.netProfit != null && pnl.totalRevenue != null && pnl.totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal nm = pnl.netProfit.multiply(new BigDecimal(100)).divide(rev, 1, RoundingMode.HALF_UP);
            lblNetMargin.setText("Net Margin: " + nm + "%");
        } else {
            lblNetMargin.setText("Net Margin: 0.0%");
        }

        TreeItem<StatementItemRow> root = new TreeItem<>(new StatementItemRow("Root", null, null, null, null));
        root.setExpanded(true);

        // Revenue Section
        TreeItem<StatementItemRow> revNode = new TreeItem<>(new StatementItemRow("REVENUE / INCOME", null, "Category", pnl.totalRevenue, "100.0"));
        revNode.setExpanded(true);
        if (pnl.revenueAccounts != null) {
            for (FinancialAccountRow a : pnl.revenueAccounts) {
                BigDecimal pct = rev.compareTo(BigDecimal.ONE) > 0 && a.amount != null ? a.amount.multiply(new BigDecimal(100)).divide(rev, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                revNode.getChildren().add(new TreeItem<>(new StatementItemRow(a.accountName, a.accountCode, a.accountGroup, a.amount, pct.toString())));
            }
        }
        root.getChildren().add(revNode);

        // COGS Section
        TreeItem<StatementItemRow> cogsNode = new TreeItem<>(new StatementItemRow("COST OF GOODS SOLD / DIRECT COSTS", null, "Category", pnl.costOfGoodsSold, null));
        cogsNode.setExpanded(true);
        if (pnl.cogsAccounts != null) {
            for (FinancialAccountRow a : pnl.cogsAccounts) {
                cogsNode.getChildren().add(new TreeItem<>(new StatementItemRow(a.accountName, a.accountCode, a.accountGroup, a.amount, null)));
            }
        }
        root.getChildren().add(cogsNode);

        // Gross Profit Summary
        root.getChildren().add(new TreeItem<>(new StatementItemRow("GROSS PROFIT", null, "Summary", pnl.grossProfit, null)));

        // Operating Expenses Section
        TreeItem<StatementItemRow> opexNode = new TreeItem<>(new StatementItemRow("OPERATING / INDIRECT EXPENSES", null, "Category", pnl.operatingExpenses, null));
        opexNode.setExpanded(true);
        if (pnl.expenseAccounts != null) {
            for (FinancialAccountRow a : pnl.expenseAccounts) {
                opexNode.getChildren().add(new TreeItem<>(new StatementItemRow(a.accountName, a.accountCode, a.accountGroup, a.amount, null)));
            }
        }
        root.getChildren().add(opexNode);

        // Net Profit Summary
        root.getChildren().add(new TreeItem<>(new StatementItemRow("NET PROFIT / (LOSS)", null, "Summary", pnl.netProfit, null)));

        treeTblPnl.setRoot(root);
        treeTblPnl.setShowRoot(false);
    }

    private void populateBs(BalanceSheetResponse bs) {
        lblTotalAssets.setText(currency.format(bs.totalAssets != null ? bs.totalAssets : BigDecimal.ZERO));
        BigDecimal liabEquity = (bs.totalLiabilities != null ? bs.totalLiabilities : BigDecimal.ZERO)
            .add(bs.totalEquity != null ? bs.totalEquity : BigDecimal.ZERO);
        lblTotalLiabEquity.setText(currency.format(liabEquity));

        BigDecimal diff = (bs.totalAssets != null ? bs.totalAssets : BigDecimal.ZERO).subtract(liabEquity).abs();
        if (diff.compareTo(new BigDecimal("0.05")) <= 0) {
            lblBsEquilibrium.setText("BALANCED");
            lblBsEquilibrium.getStyleClass().setAll("status-pill", "status-pill-green");
        } else {
            lblBsEquilibrium.setText("OUT OF BALANCE: " + currency.format(diff));
            lblBsEquilibrium.getStyleClass().setAll("status-pill", "status-pill-red");
        }

        TreeItem<StatementItemRow> root = new TreeItem<>(new StatementItemRow("Root", null, null, null, null));
        root.setExpanded(true);

        // Assets
        TreeItem<StatementItemRow> assetsNode = new TreeItem<>(new StatementItemRow("TOTAL ASSETS", null, "Category", bs.totalAssets, null));
        assetsNode.setExpanded(true);
        if (bs.currentAssets != null && !bs.currentAssets.isEmpty()) {
            TreeItem<StatementItemRow> caNode = new TreeItem<>(new StatementItemRow("Current Assets", null, "Subgroup", null, null));
            caNode.setExpanded(true);
            for (FinancialAccountRow r : bs.currentAssets) {
                caNode.getChildren().add(new TreeItem<>(new StatementItemRow(r.accountName, r.accountCode, r.accountGroup, r.amount, null)));
            }
            assetsNode.getChildren().add(caNode);
        }
        if (bs.nonCurrentAssets != null && !bs.nonCurrentAssets.isEmpty()) {
            TreeItem<StatementItemRow> ncaNode = new TreeItem<>(new StatementItemRow("Non-Current / Fixed Assets", null, "Subgroup", null, null));
            ncaNode.setExpanded(true);
            for (FinancialAccountRow r : bs.nonCurrentAssets) {
                ncaNode.getChildren().add(new TreeItem<>(new StatementItemRow(r.accountName, r.accountCode, r.accountGroup, r.amount, null)));
            }
            assetsNode.getChildren().add(ncaNode);
        }
        root.getChildren().add(assetsNode);

        // Liabilities
        TreeItem<StatementItemRow> liabNode = new TreeItem<>(new StatementItemRow("TOTAL LIABILITIES", null, "Category", bs.totalLiabilities, null));
        liabNode.setExpanded(true);
        if (bs.currentLiabilities != null && !bs.currentLiabilities.isEmpty()) {
            TreeItem<StatementItemRow> clNode = new TreeItem<>(new StatementItemRow("Current Liabilities", null, "Subgroup", null, null));
            clNode.setExpanded(true);
            for (FinancialAccountRow r : bs.currentLiabilities) {
                clNode.getChildren().add(new TreeItem<>(new StatementItemRow(r.accountName, r.accountCode, r.accountGroup, r.amount, null)));
            }
            liabNode.getChildren().add(clNode);
        }
        if (bs.nonCurrentLiabilities != null && !bs.nonCurrentLiabilities.isEmpty()) {
            TreeItem<StatementItemRow> nclNode = new TreeItem<>(new StatementItemRow("Long-Term Liabilities", null, "Subgroup", null, null));
            nclNode.setExpanded(true);
            for (FinancialAccountRow r : bs.nonCurrentLiabilities) {
                nclNode.getChildren().add(new TreeItem<>(new StatementItemRow(r.accountName, r.accountCode, r.accountGroup, r.amount, null)));
            }
            liabNode.getChildren().add(nclNode);
        }
        root.getChildren().add(liabNode);

        // Equity
        TreeItem<StatementItemRow> eqNode = new TreeItem<>(new StatementItemRow("EQUITY & CAPITAL", null, "Category", bs.totalEquity, null));
        eqNode.setExpanded(true);
        if (bs.equityAccounts != null) {
            for (FinancialAccountRow r : bs.equityAccounts) {
                eqNode.getChildren().add(new TreeItem<>(new StatementItemRow(r.accountName, r.accountCode, r.accountGroup, r.amount, null)));
            }
        }
        root.getChildren().add(eqNode);

        treeTblBs.setRoot(root);
        treeTblBs.setShowRoot(false);
    }

    private void populateRatios(RatiosData r) {
        if (lblCurrentRatio != null) lblCurrentRatio.setText(String.format(Locale.of("en", "IN"), "%.2f", r.currentRatio != null ? r.currentRatio : BigDecimal.ONE));
        if (lblQuickRatio != null) lblQuickRatio.setText(String.format(Locale.of("en", "IN"), "%.2f", r.quickRatio != null ? r.quickRatio : BigDecimal.ONE));
        if (lblDso != null) lblDso.setText(String.format(Locale.of("en", "IN"), "%.1f days", r.daysSalesOutstanding != null ? r.daysSalesOutstanding : BigDecimal.ZERO));
        if (lblDpo != null) lblDpo.setText(String.format(Locale.of("en", "IN"), "%.1f days", r.daysPayablesOutstanding != null ? r.daysPayablesOutstanding : BigDecimal.ZERO));
        if (lblWorkingCapital != null) lblWorkingCapital.setText(currency.format(r.workingCapital != null ? r.workingCapital : BigDecimal.ZERO));

        ratioRows.clear();
        ratioRows.add(new RatioRow("Liquidity", "Current Ratio", String.format(Locale.of("en", "IN"), "%.2fx", r.currentRatio), "1.33x - 2.00x", r.currentRatio.compareTo(new BigDecimal("1.33")) >= 0 ? "Optimal Liquidity" : "Watch Cash Flow"));
        ratioRows.add(new RatioRow("Liquidity", "Quick Ratio (Acid-Test)", String.format(Locale.of("en", "IN"), "%.2fx", r.quickRatio), ">= 1.00x", r.quickRatio.compareTo(BigDecimal.ONE) >= 0 ? "Strong Quick Cover" : "Inventory Heavy"));
        ratioRows.add(new RatioRow("Liquidity", "Net Working Capital", currency.format(r.workingCapital), "> ₹ 0", r.workingCapital.compareTo(BigDecimal.ZERO) >= 0 ? "Adequate Cushion" : "Working Capital Deficit"));
        ratioRows.add(new RatioRow("Profitability", "Gross Profit Margin", (r.grossProfitMarginPct != null ? r.grossProfitMarginPct : "0.0") + "%", "15.0% - 25.0%", r.grossProfitMarginPct.compareTo(new BigDecimal("15")) >= 0 ? "Optimal Margin" : "Low Margin"));
        ratioRows.add(new RatioRow("Profitability", "Net Profit Margin", (r.netProfitMarginPct != null ? r.netProfitMarginPct : "0.0") + "%", "5.0% - 12.0%", r.netProfitMarginPct.compareTo(new BigDecimal("5")) >= 0 ? "Strong Bottom-Line" : "Watch Overheads"));
        ratioRows.add(new RatioRow("Profitability", "Return on Equity (ROE)", (r.returnOnEquityPct != null ? r.returnOnEquityPct : "0.0") + "%", "12.0% - 20.0%", r.returnOnEquityPct.compareTo(new BigDecimal("10")) >= 0 ? "Good Shareholder Return" : "Low Return"));
        ratioRows.add(new RatioRow("Solvency", "Debt to Equity Ratio", String.format(Locale.of("en", "IN"), "%.2fx", r.debtToEquityRatio), "< 1.50x", r.debtToEquityRatio.compareTo(new BigDecimal("1.5")) <= 0 ? "Low Financial Risk" : "Leveraged"));
        ratioRows.add(new RatioRow("Activity", "Days Sales Outstanding (DSO)", String.format(Locale.of("en", "IN"), "%.1f days", r.daysSalesOutstanding), "30 - 45 days", r.daysSalesOutstanding.compareTo(new BigDecimal("45")) <= 0 ? "Fast Collections" : "Review Receivables"));
        ratioRows.add(new RatioRow("Activity", "Days Payables Outstanding (DPO)", String.format(Locale.of("en", "IN"), "%.1f days", r.daysPayablesOutstanding), "45 - 60 days", "Normal Credit Cycle"));
    }

    @FXML
    public void drillDownSelectedPnl() {
        TreeItem<StatementItemRow> sel = treeTblPnl.getSelectionModel().getSelectedItem();
        String cat = "SALES";
        if (sel != null && sel.getValue() != null && sel.getValue().name != null) {
            String n = sel.getValue().name.toUpperCase(Locale.ROOT);
            if (n.contains("PURCHASE") || n.contains("COGS") || n.contains("DIRECT COST")) cat = "PURCHASES";
            else if (n.contains("EXPENSE") || n.contains("OVERHEAD") || n.contains("OFFICE")) cat = "EXPENSES";
            else if (n.contains("STOCK") || n.contains("INVENTORY")) cat = "INVENTORY";
            else cat = "SALES";
        }
        openDrillDownDialog(cat);
    }

    @FXML
    public void drillDownSelectedBs() {
        TreeItem<StatementItemRow> sel = treeTblBs.getSelectionModel().getSelectedItem();
        String cat = "DEBTORS";
        if (sel != null && sel.getValue() != null && sel.getValue().name != null) {
            String n = sel.getValue().name.toUpperCase(Locale.ROOT);
            if (n.contains("CREDITOR") || n.contains("PAYABLE")) cat = "CREDITORS";
            else if (n.contains("STOCK") || n.contains("INVENTORY")) cat = "INVENTORY";
            else if (n.contains("CASH") || n.contains("BANK")) cat = "CASH_BANK";
            else cat = "DEBTORS";
        }
        openDrillDownDialog(cat);
    }

    private void openDrillDownDialog(String category) {
        LocalDate from = dpFromDate.getValue() != null ? dpFromDate.getValue() : LocalDate.now().minusMonths(1);
        LocalDate to = dpToDate.getValue() != null ? dpToDate.getValue() : LocalDate.now();

        UiTaskExecutor.submitAction("financial-drilldown", () -> {
            String url = apiUrl("/api/financial/drill-down?category=" + category + "&fromDate=" + from + "&toDate=" + to);
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET();
            ApiSession.authorize(req);
            HttpResponse<String> resp = ApiRuntime.HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                return ApiRuntime.JSON.readValue(resp.body(), DrillDownResponse.class);
            }
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }, data -> showDrillDownModal(data, category), err -> ToastManager.error(treeTblPnl, "Drill-Down Error", "Could not load transaction details: " + err.getMessage()));
    }

    private void showDrillDownModal(DrillDownResponse res, String category) {
        Dialog<Void> dialog = new OwnedDialog<>(treeTblPnl);
        dialog.setTitle("Financial Drill-Down Inspection — " + category);
        dialog.setHeaderText("Transaction-level ledger details backing " + category + " figures (" + (res.rows != null ? res.rows.size() : 0) + " transactions)");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(10);
        content.setPrefWidth(850);
        content.setPrefHeight(450);
        content.setPadding(new Insets(10));

        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);
        Label lblTotal = new Label("Net Total: " + currency.format(res.totalAmount != null ? res.totalAmount : BigDecimal.ZERO));
        lblTotal.getStyleClass().add("bold-label");
        TextField txtSearch = new TextField();
        txtSearch.setPromptText("Filter transactions by party, voucher or note...");
        HBox.setHgrow(txtSearch, Priority.ALWAYS);

        Button btnExportCsv = new Button("Export CSV");
        btnExportCsv.getStyleClass().addAll("approved-button", "approved-secondary-button");
        topBar.getChildren().addAll(lblTotal, txtSearch, btnExportCsv);

        TableView<DrillDownRow> table = new TableView<>();
        table.getStyleClass().addAll("approved-table", "erp-table-profile-responsive");
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<DrillDownRow, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().date));
        TableColumn<DrillDownRow, String> colVoucher = new TableColumn<>("Voucher / Ref");
        colVoucher.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().voucherNo));
        TableColumn<DrillDownRow, String> colParty = new TableColumn<>("Party / Account");
        colParty.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().partyOrAccount));
        TableColumn<DrillDownRow, String> colDesc = new TableColumn<>("Description");
        colDesc.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().description));
        TableColumn<DrillDownRow, String> colDebit = new TableColumn<>("Debit (₹)");
        colDebit.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().debit != null ? currency.format(r.getValue().debit) : ""));
        TableColumn<DrillDownRow, String> colCredit = new TableColumn<>("Credit (₹)");
        colCredit.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().credit != null ? currency.format(r.getValue().credit) : ""));
        TableColumn<DrillDownRow, String> colBal = new TableColumn<>("Balance (₹)");
        colBal.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().balance != null ? currency.format(r.getValue().balance) : ""));

        table.getColumns().addAll(List.of(colDate, colVoucher, colParty, colDesc, colDebit, colCredit, colBal));
        ObservableList<DrillDownRow> allRows = FXCollections.observableArrayList(res.rows != null ? res.rows : List.of());
        table.setItems(allRows);
        DynamicTableLayoutManager.install(table);

        txtSearch.textProperty().addListener((obs, oldV, q) -> {
            if (q == null || q.isBlank()) {
                table.setItems(allRows);
            } else {
                String lq = q.toLowerCase(Locale.ROOT);
                table.setItems(allRows.filtered(r ->
                        (r.partyOrAccount != null && r.partyOrAccount.toLowerCase(Locale.ROOT).contains(lq)) ||
                        (r.voucherNo != null && r.voucherNo.toLowerCase(Locale.ROOT).contains(lq)) ||
                        (r.description != null && r.description.toLowerCase(Locale.ROOT).contains(lq))
                ));
            }
        });

        btnExportCsv.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Drill-Down CSV");
            chooser.setInitialFileName("DrillDown_" + category + "_" + BusinessClock.today() + ".csv");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
            File file = chooser.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
            if (file != null) {
                try (java.io.PrintWriter pw = new java.io.PrintWriter(file)) {
                    pw.println("Date,Voucher No,Party / Account,Description,Debit,Credit,Balance");
                    for (DrillDownRow r : table.getItems()) {
                        pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.2f%n",
                                r.date != null ? r.date : "",
                                r.voucherNo != null ? r.voucherNo : "",
                                r.partyOrAccount != null ? r.partyOrAccount.replace("\"", "\"\"") : "",
                                r.description != null ? r.description.replace("\"", "\"\"") : "",
                                r.debit != null ? r.debit.doubleValue() : 0.0,
                                r.credit != null ? r.credit.doubleValue() : 0.0,
                                r.balance != null ? r.balance.doubleValue() : 0.0);
                    }
                    ToastManager.success(treeTblPnl, "CSV Exported", "Saved to: " + file.getAbsolutePath());
                } catch (Exception ex) {
                    ToastManager.error(treeTblPnl, "Export Error", ex.getMessage());
                }
            }
        });

        content.getChildren().addAll(topBar, table);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void filterTree(TreeTableView<StatementItemRow> treeTable, String search) {
        if (treeTable.getRoot() == null) return;
        boolean hasFilter = search != null && !search.trim().isEmpty();
        String query = hasFilter ? search.trim().toLowerCase(Locale.ROOT) : "";
        for (TreeItem<StatementItemRow> cat : treeTable.getRoot().getChildren()) {
            boolean catMatch = hasFilter && cat.getValue().name.toLowerCase(Locale.ROOT).contains(query);
            for (TreeItem<StatementItemRow> item : cat.getChildren()) {
                boolean itemMatch = catMatch || (hasFilter && item.getValue().name.toLowerCase(Locale.ROOT).contains(query));
                if (itemMatch) {
                    cat.setExpanded(true);
                    item.setExpanded(true);
                }
            }
        }
    }

    @FXML
    public void expandAllPnl() {
        if (treeTblPnl.getRoot() != null) setExpandedRecursive(treeTblPnl.getRoot(), true);
    }

    @FXML
    public void collapseAllPnl() {
        if (treeTblPnl.getRoot() != null) setExpandedRecursive(treeTblPnl.getRoot(), false);
    }

    private void setExpandedRecursive(TreeItem<?> item, boolean expanded) {
        item.setExpanded(expanded);
        for (TreeItem<?> child : item.getChildren()) {
            setExpandedRecursive(child, expanded);
        }
    }

    private void drillDownToLedger(String accountCode) {
        AppDialogService.info(tabStatements, "General Ledger", "Ledger Drill-Down", "Navigating to General Ledger for account: " + accountCode);
        DashboardController.navigateFromChildPage("General Ledger", "/fxml/pages/GeneralLedger.fxml");
    }

    @FXML
    public void exportPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Financial Statements");
        chooser.setInitialFileName("Financial_Statements_" + BusinessClock.today() + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Document (*.pdf)", "*.pdf"));
        File file = chooser.showSaveDialog(treeTblPnl.getScene().getWindow());
        if (file == null) return;

        try {
            List<String[]> rows = new ArrayList<>();
            if (treeTblPnl.getRoot() != null) {
                flattenTreeRowsForPdf(treeTblPnl.getRoot(), rows, 0);
            }
            BrandedRegisterPdfService.export(
                file.toPath(),
                "Profit & Loss Statement",
                new String[]{"Account / Item", "Code", "Type", "Amount (₹)", "% Revenue"},
                rows,
                new float[]{3.5f, 1.2f, 1.8f, 2.0f, 1.2f}
            );

            String path = file.getAbsolutePath();
            ToastManager.success(treeTblPnl, "Export Complete", "Financial statements PDF saved to:\n" + path);
            Alert alert = new OwnedAlert(Alert.AlertType.INFORMATION, "Financial statements PDF saved successfully:\n\n" + path, ButtonType.OK);
            alert.setHeaderText("PDF Export Successful");
            alert.showAndWait();
        } catch (Exception ex) {
            AppDialogService.error(treeTblPnl, "Export Failed", "Could not export PDF statement", ex.getMessage());
        }
    }

    private void flattenTreeRowsForPdf(TreeItem<StatementItemRow> item, List<String[]> list, int depth) {
        if (item == null) return;
        StatementItemRow row = item.getValue();
        if (row != null && !"Root".equalsIgnoreCase(row.name)) {
            String indent = "  ".repeat(Math.max(0, depth - 1));
            list.add(new String[]{
                indent + (row.name != null ? row.name : ""),
                row.accountCode != null ? row.accountCode : "",
                row.groupType != null ? row.groupType : "",
                row.amount != null ? currency.format(row.amount) : "",
                row.percentage != null && !row.percentage.isBlank() ? row.percentage + "%" : ""
            });
        }
        for (TreeItem<StatementItemRow> child : item.getChildren()) {
            flattenTreeRowsForPdf(child, list, depth + 1);
        }
    }

    @FXML
    public void exportExcel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Financial Statements");
        chooser.setInitialFileName("Financial_Statements_" + BusinessClock.today() + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));
        File file = chooser.showSaveDialog(treeTblPnl.getScene().getWindow());
        if (file == null) return;

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet pnlSheet = workbook.createSheet("Profit & Loss");
            Row pr0 = pnlSheet.createRow(0);
            String[] pnlHeaders = {"Account / Line Item", "Code", "Type", "Amount (₹)", "% of Revenue"};
            for (int i = 0; i < pnlHeaders.length; i++) pr0.createCell(i).setCellValue(pnlHeaders[i]);
            int rowIdx = 1;
            if (treeTblPnl.getRoot() != null) {
                rowIdx = writeTreeRowsToExcel(pnlSheet, treeTblPnl.getRoot(), rowIdx, 0);
            }

            Sheet bsSheet = workbook.createSheet("Balance Sheet");
            Row br0 = bsSheet.createRow(0);
            String[] bsHeaders = {"Classification / Account", "Code", "Group", "Amount (₹)"};
            for (int i = 0; i < bsHeaders.length; i++) br0.createCell(i).setCellValue(bsHeaders[i]);
            rowIdx = 1;
            if (treeTblBs.getRoot() != null) {
                writeTreeRowsToExcel(bsSheet, treeTblBs.getRoot(), rowIdx, 0);
            }

            for (int i = 0; i < pnlHeaders.length; i++) pnlSheet.autoSizeColumn(i);
            for (int i = 0; i < bsHeaders.length; i++) bsSheet.autoSizeColumn(i);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }

            String path = file.getAbsolutePath();
            ToastManager.success(treeTblPnl, "Export Complete", "Financial statements exported to:\n" + path);
            Alert alert = new OwnedAlert(Alert.AlertType.INFORMATION, "Financial statements workbook saved successfully:\n\n" + path, ButtonType.OK);
            alert.setHeaderText("Excel Export Successful");
            alert.showAndWait();
        } catch (Exception ex) {
            AppDialogService.error(treeTblPnl, "Export Failed", "Could not export financial statements", ex.getMessage());
        }
    }

    private int writeTreeRowsToExcel(Sheet sheet, TreeItem<StatementItemRow> item, int rowIdx, int depth) {
        if (item == null) return rowIdx;
        StatementItemRow row = item.getValue();
        if (row != null && !"Root".equalsIgnoreCase(row.name)) {
            Row r = sheet.createRow(rowIdx++);
            String indent = "  ".repeat(Math.max(0, depth - 1));
            r.createCell(0).setCellValue(indent + (row.name != null ? row.name : ""));
            r.createCell(1).setCellValue(row.accountCode != null ? row.accountCode : "");
            r.createCell(2).setCellValue(row.groupType != null ? row.groupType : "");
            if (row.amount != null) {
                r.createCell(3).setCellValue(row.amount.doubleValue());
            } else {
                r.createCell(3).setCellValue("");
            }
            if (sheet.getSheetName().contains("Loss") && row.percentage != null) {
                r.createCell(4).setCellValue(row.percentage + "%");
            }
        }
        for (TreeItem<StatementItemRow> child : item.getChildren()) {
            rowIdx = writeTreeRowsToExcel(sheet, child, rowIdx, depth + 1);
        }
        return rowIdx;
    }

    public static class StatementItemRow {
        public String name;
        public String accountCode;
        public String groupType;
        public BigDecimal amount;
        public String percentage;

        public StatementItemRow(String name, String accountCode, String groupType, BigDecimal amount, String percentage) {
            this.name = name;
            this.accountCode = accountCode;
            this.groupType = groupType;
            this.amount = amount;
            this.percentage = percentage;
        }
    }

    public static class ProfitAndLossResponse {
        public String fromDate;
        public String toDate;
        public BigDecimal totalRevenue = BigDecimal.ZERO;
        public BigDecimal costOfGoodsSold = BigDecimal.ZERO;
        public BigDecimal grossProfit = BigDecimal.ZERO;
        public BigDecimal operatingExpenses = BigDecimal.ZERO;
        public BigDecimal netProfit = BigDecimal.ZERO;
        public List<FinancialAccountRow> revenueAccounts;
        public List<FinancialAccountRow> cogsAccounts;
        public List<FinancialAccountRow> expenseAccounts;
    }

    public static class BalanceSheetResponse {
        public String asOfDate;
        public BigDecimal totalAssets = BigDecimal.ZERO;
        public BigDecimal totalLiabilities = BigDecimal.ZERO;
        public BigDecimal totalEquity = BigDecimal.ZERO;
        public List<FinancialAccountRow> currentAssets;
        public List<FinancialAccountRow> nonCurrentAssets;
        public List<FinancialAccountRow> currentLiabilities;
        public List<FinancialAccountRow> nonCurrentLiabilities;
        public List<FinancialAccountRow> equityAccounts;
    }

    public static class FinancialAccountRow {
        public String accountCode;
        public String accountName;
        public String accountGroup;
        public BigDecimal amount = BigDecimal.ZERO;
    }

    public record RatioRow(String category, String metric, String value, String benchmark, String status) {}

    public static class ThreeTierResponse {
        public String engine;
        public TradingAccountData tradingAccount;
        public ProfitAndLossResponse profitAndLoss;
        public BalanceSheetResponse balanceSheet;
        public RatiosData ratios;
        public String statementDate;
    }

    public static class TradingAccountData {
        public BigDecimal openingStock = BigDecimal.ZERO;
        public BigDecimal netSales = BigDecimal.ZERO;
        public BigDecimal netPurchases = BigDecimal.ZERO;
        public BigDecimal directExpenses = BigDecimal.ZERO;
        public BigDecimal closingStock = BigDecimal.ZERO;
        public BigDecimal cogs = BigDecimal.ZERO;
        public BigDecimal grossProfit = BigDecimal.ZERO;
        public BigDecimal grossMarginPct = BigDecimal.ZERO;
    }

    public static class RatiosData {
        public BigDecimal currentRatio = BigDecimal.ONE;
        public BigDecimal quickRatio = BigDecimal.ONE;
        public BigDecimal grossProfitMarginPct = BigDecimal.ZERO;
        public BigDecimal netProfitMarginPct = BigDecimal.ZERO;
        public BigDecimal returnOnEquityPct = BigDecimal.ZERO;
        public BigDecimal debtToEquityRatio = BigDecimal.ZERO;
        public BigDecimal daysSalesOutstanding = BigDecimal.ZERO;
        public BigDecimal daysPayablesOutstanding = BigDecimal.ZERO;
        public BigDecimal workingCapital = BigDecimal.ZERO;
    }

    public static class DrillDownResponse {
        public String category;
        public BigDecimal totalAmount = BigDecimal.ZERO;
        public List<DrillDownRow> rows;
    }

    public static class DrillDownRow {
        public String date;
        public String voucherNo;
        public String partyOrAccount;
        public String description;
        public BigDecimal debit = BigDecimal.ZERO;
        public BigDecimal credit = BigDecimal.ZERO;
        public BigDecimal balance = BigDecimal.ZERO;
    }

    private record StatementsData(ProfitAndLossResponse pnl, BalanceSheetResponse bs, TradingAccountData trading, RatiosData ratios) {}
}
