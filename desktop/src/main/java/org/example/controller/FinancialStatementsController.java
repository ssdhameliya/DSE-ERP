package org.example.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import org.example.api.ApiRuntime;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;
import org.example.navigation.ScreenLifecycle;
import org.example.util.*;

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
import java.io.File;
import java.io.FileOutputStream;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.service.BrandedRegisterPdfService;

public class FinancialStatementsController implements ScreenLifecycle {

    @FXML private StackPane pageIcon;
    @FXML private StackPane kpiRevenueIcon, kpiCogsIcon, kpiGrossProfitIcon, kpiNetProfitIcon;
    @FXML private Label kpiRevenue, kpiCogs, kpiGrossProfit, kpiNetProfit, lblGrossMargin, lblNetMargin;

    @FXML private DatePicker dpFromDate, dpToDate;
    @FXML private Button btnApplyDate, btnExportPdf, btnExportExcel;

    @FXML private TabPane tabStatements;
    @FXML private Tab tabPnl, tabBalanceSheet;

    @FXML private TextField txtSearchPnl, txtSearchBs;
    @FXML private Button btnExpandAllPnl, btnCollapseAllPnl;

    @FXML private TreeTableView<StatementItemRow> treeTblPnl;
    @FXML private TreeTableColumn<StatementItemRow, String> colPnlAccount, colPnlCode, colPnlType, colPnlAmount, colPnlPercentage;

    @FXML private Label lblTotalAssets, lblTotalLiabEquity, lblBsEquilibrium;
    @FXML private TreeTableView<StatementItemRow> treeTblBs;
    @FXML private TreeTableColumn<StatementItemRow, String> colBsClassification, colBsCode, colBsGroup, colBsAmount;

    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    public void initialize() {
        if (pageIcon != null) pageIcon.getChildren().setAll(IconFactory.icon("report", 22));
        if (kpiRevenueIcon != null) kpiRevenueIcon.getChildren().setAll(IconFactory.compactIcon("wallet", 16));
        if (kpiCogsIcon != null) kpiCogsIcon.getChildren().setAll(IconFactory.compactIcon("inventory", 16));
        if (kpiGrossProfitIcon != null) kpiGrossProfitIcon.getChildren().setAll(IconFactory.compactIcon("chart", 16));
        if (kpiNetProfitIcon != null) kpiNetProfitIcon.getChildren().setAll(IconFactory.compactIcon("dashboard", 16));

        LocalDate now = LocalDate.now();
        int fyStartYear = now.getMonthValue() >= 4 ? now.getYear() : now.getYear() - 1;
        dpFromDate.setValue(LocalDate.of(fyStartYear, 4, 1));
        dpToDate.setValue(now);

        configurePnlColumns();
        configureBsColumns();

        RealtimeSearchSupport.installLocal(txtSearchPnl, () -> filterTree(treeTblPnl, txtSearchPnl.getText()));
        RealtimeSearchSupport.installLocal(txtSearchBs, () -> filterTree(treeTblBs, txtSearchBs.getText()));

        ContextMenu pnlContextMenu = new ContextMenu();
        MenuItem miPnlLedger = new MenuItem("View General Ledger");
        miPnlLedger.setOnAction(e -> {
            TreeItem<StatementItemRow> selected = treeTblPnl.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() != null && selected.getValue().accountCode != null) {
                drillDownToLedger(selected.getValue().accountCode);
            }
        });
        pnlContextMenu.getItems().add(miPnlLedger);
        treeTblPnl.setContextMenu(pnlContextMenu);

        ContextMenu bsContextMenu = new ContextMenu();
        MenuItem miBsLedger = new MenuItem("View General Ledger");
        miBsLedger.setOnAction(e -> {
            TreeItem<StatementItemRow> selected = treeTblBs.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() != null && selected.getValue().accountCode != null) {
                drillDownToLedger(selected.getValue().accountCode);
            }
        });
        bsContextMenu.getItems().add(miBsLedger);
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

    @FXML
    public void refreshStatements() {
        LocalDate from = dpFromDate.getValue();
        LocalDate to = dpToDate.getValue();
        if (from == null) from = LocalDate.now().minusMonths(1);
        if (to == null) to = LocalDate.now();

        LocalDate finalFrom = from;
        LocalDate finalTo = to;

        UiTaskExecutor.submitLatest(
            "financial-statements-fetch",
            () -> {
                // Fetch P&L
                String pnlUrl = apiUrl("/api/financial/profit-and-loss?fromDate=" + finalFrom + "&toDate=" + finalTo);
                HttpRequest.Builder pnlReq = HttpRequest.newBuilder(URI.create(pnlUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
                ApiSession.authorize(pnlReq);
                HttpResponse<String> pnlResp = ApiRuntime.HTTP.send(pnlReq.build(), HttpResponse.BodyHandlers.ofString());

                ProfitAndLossResponse pnlData = null;
                if (pnlResp.statusCode() == 200) {
                    pnlData = ApiRuntime.JSON.readValue(pnlResp.body(), ProfitAndLossResponse.class);
                }

                // Fetch Balance Sheet
                String bsUrl = apiUrl("/api/financial/balance-sheet?asOfDate=" + finalTo);
                HttpRequest.Builder bsReq = HttpRequest.newBuilder(URI.create(bsUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
                ApiSession.authorize(bsReq);
                HttpResponse<String> bsResp = ApiRuntime.HTTP.send(bsReq.build(), HttpResponse.BodyHandlers.ofString());

                BalanceSheetResponse bsData = null;
                if (bsResp.statusCode() == 200) {
                    bsData = ApiRuntime.JSON.readValue(bsResp.body(), BalanceSheetResponse.class);
                }

                return new StatementsData(pnlData, bsData);
            },
            data -> {
                if (data.pnl != null) populatePnl(data.pnl);
                if (data.bs != null) populateBs(data.bs);
            },
            err -> AppDialogService.error(tabStatements, "Financials", "Failed to Load Financials", err.getMessage())
        );
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

    private record StatementsData(ProfitAndLossResponse pnl, BalanceSheetResponse bs) {}
}
