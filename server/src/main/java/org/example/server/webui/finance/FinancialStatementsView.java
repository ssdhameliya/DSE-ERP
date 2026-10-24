package org.example.server.webui.finance;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.financialstatements.FinancialStatementsDtos;
import org.example.server.financialstatements.FinancialStatementsService;
import org.example.server.webui.MainAppLayout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Route(value = "finance/statements", layout = MainAppLayout.class)
@PageTitle("Financial Statements | DSE ERP")
@PermitAll
public class FinancialStatementsView extends VerticalLayout {

    private final FinancialStatementsService fsService;

    private final DatePicker fromDate = new DatePicker("From Date", LocalDate.now().withDayOfMonth(1));
    private final DatePicker toDate = new DatePicker("To Date", LocalDate.now());
    private final DatePicker asOfDate = new DatePicker("Balance Sheet Date", LocalDate.now());

    // P&L Grids & KPIs
    private final Span pnlGrossRevenue = new Span("₹ 0.00");
    private final Span pnlGrossProfit = new Span("₹ 0.00");
    private final Span pnlOperatingExpense = new Span("₹ 0.00");
    private final Span pnlNetProfit = new Span("₹ 0.00");
    private final Grid<FinancialStatementsDtos.StatementLineDto> pnlGrid = new Grid<>();

    // Balance Sheet Grids & KPIs
    private final Span bsAssets = new Span("₹ 0.00");
    private final Span bsLiabilities = new Span("₹ 0.00");
    private final Span bsEquity = new Span("₹ 0.00");
    private final Span bsEquationStatus = new Span("Balanced");
    private final Grid<FinancialStatementsDtos.StatementLineDto> bsGrid = new Grid<>();

    public FinancialStatementsView(FinancialStatementsService fsService) {
        this.fsService = fsService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Financial Statements (P&L, Balance Sheet, Ledger Engine)");
        title.getStyle().set("margin", "0");

        Button refreshBtn = new Button("Generate Statements", VaadinIcon.CALC_BOOK.create(), e -> refreshAll());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(title, refreshBtn);
        header.expand(title);
        add(header);

        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        tabSheet.add("Profit & Loss Statement", buildPnlTab());
        tabSheet.add("Balance Sheet", buildBsTab());

        add(tabSheet);
        refreshAll();
    }

    private VerticalLayout buildPnlTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);

        HorizontalLayout filterRow = new HorizontalLayout(fromDate, toDate);
        filterRow.setAlignItems(FlexComponent.Alignment.BASELINE);

        HorizontalLayout kpis = new HorizontalLayout();
        kpis.setWidthFull();
        kpis.add(createKpiCard("Gross Revenue", pnlGrossRevenue));
        kpis.add(createKpiCard("Gross Profit", pnlGrossProfit));
        kpis.add(createKpiCard("Operating Expenses", pnlOperatingExpense));
        kpis.add(createKpiCard("Net Profit / Margin", pnlNetProfit));

        pnlGrid.setSizeFull();
        configureStatementLineGrid(pnlGrid);

        layout.add(filterRow, kpis, pnlGrid);
        return layout;
    }

    private VerticalLayout buildBsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);

        HorizontalLayout filterRow = new HorizontalLayout(asOfDate);
        filterRow.setAlignItems(FlexComponent.Alignment.BASELINE);

        HorizontalLayout kpis = new HorizontalLayout();
        kpis.setWidthFull();
        kpis.add(createKpiCard("Total Assets", bsAssets));
        kpis.add(createKpiCard("Total Liabilities", bsLiabilities));
        kpis.add(createKpiCard("Total Equity", bsEquity));
        kpis.add(createKpiCard("Accounting Equation", bsEquationStatus));

        bsGrid.setSizeFull();
        configureStatementLineGrid(bsGrid);

        layout.add(filterRow, kpis, bsGrid);
        return layout;
    }

    private void configureStatementLineGrid(Grid<FinancialStatementsDtos.StatementLineDto> grid) {
        grid.addColumn(FinancialStatementsDtos.StatementLineDto::accountCode).setHeader("Code").setAutoWidth(true).setSortable(true);
        grid.addColumn(FinancialStatementsDtos.StatementLineDto::accountName).setHeader("Account").setFlexGrow(1).setSortable(true);
        grid.addColumn(FinancialStatementsDtos.StatementLineDto::category).setHeader("Category").setAutoWidth(true);
        grid.addColumn(FinancialStatementsDtos.StatementLineDto::subcategory).setHeader("Subcategory").setAutoWidth(true);
        grid.addColumn(l -> "₹ " + (l.amount() != null ? l.amount().toPlainString() : "0.00")).setHeader("Amount").setAutoWidth(true);
    }

    private VerticalLayout createKpiCard(String label, Span valSpan) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "8px")
                .set("padding", "14px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)");
        Span lbl = new Span(label);
        lbl.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");
        valSpan.getStyle().set("font-size", "20px").set("font-weight", "bold");
        card.add(lbl, valSpan);
        return card;
    }

    private void refreshAll() {
        try {
            LocalDate f = fromDate.getValue() != null ? fromDate.getValue() : LocalDate.now().withDayOfMonth(1);
            LocalDate t = toDate.getValue() != null ? toDate.getValue() : LocalDate.now();
            FinancialStatementsDtos.ProfitAndLossDto pnl = fsService.getProfitAndLoss(f, t);
            if (pnl != null) {
                pnlGrossRevenue.setText("₹ " + (pnl.grossRevenue() != null ? pnl.grossRevenue().toPlainString() : "0.00"));
                pnlGrossProfit.setText("₹ " + (pnl.grossProfit() != null ? pnl.grossProfit().toPlainString() : "0.00"));
                pnlOperatingExpense.setText("₹ " + (pnl.operatingExpenses() != null ? pnl.operatingExpenses().toPlainString() : "0.00"));
                pnlNetProfit.setText("₹ " + (pnl.netProfit() != null ? pnl.netProfit().toPlainString() : "0.00"));

                List<FinancialStatementsDtos.StatementLineDto> combined = new ArrayList<>();
                if (pnl.revenueLines() != null) combined.addAll(pnl.revenueLines());
                if (pnl.cogsLines() != null) combined.addAll(pnl.cogsLines());
                if (pnl.expenseLines() != null) combined.addAll(pnl.expenseLines());
                pnlGrid.setItems(combined);
            }

            LocalDate asOf = asOfDate.getValue() != null ? asOfDate.getValue() : LocalDate.now();
            FinancialStatementsDtos.BalanceSheetDto bs = fsService.getBalanceSheet(asOf);
            if (bs != null) {
                bsAssets.setText("₹ " + (bs.totalAssets() != null ? bs.totalAssets().toPlainString() : "0.00"));
                bsLiabilities.setText("₹ " + (bs.totalLiabilities() != null ? bs.totalLiabilities().toPlainString() : "0.00"));
                bsEquity.setText("₹ " + (bs.totalEquity() != null ? bs.totalEquity().toPlainString() : "0.00"));

                BigDecimal totalLAndE = (bs.totalLiabilities() != null ? bs.totalLiabilities() : BigDecimal.ZERO)
                        .add(bs.totalEquity() != null ? bs.totalEquity() : BigDecimal.ZERO);
                BigDecimal diff = (bs.totalAssets() != null ? bs.totalAssets() : BigDecimal.ZERO).subtract(totalLAndE).abs();
                if (diff.compareTo(new BigDecimal("0.05")) <= 0) {
                    bsEquationStatus.setText("Balanced (A = L + E)");
                    bsEquationStatus.getStyle().set("color", "var(--lumo-success-text-color)");
                } else {
                    bsEquationStatus.setText("Variance: ₹ " + diff.toPlainString());
                    bsEquationStatus.getStyle().set("color", "var(--lumo-error-text-color)");
                }

                List<FinancialStatementsDtos.StatementLineDto> combinedBs = new ArrayList<>();
                if (bs.currentAssetLines() != null) combinedBs.addAll(bs.currentAssetLines());
                if (bs.nonCurrentAssetLines() != null) combinedBs.addAll(bs.nonCurrentAssetLines());
                if (bs.currentLiabilityLines() != null) combinedBs.addAll(bs.currentLiabilityLines());
                if (bs.nonCurrentLiabilityLines() != null) combinedBs.addAll(bs.nonCurrentLiabilityLines());
                if (bs.equityLines() != null) combinedBs.addAll(bs.equityLines());
                bsGrid.setItems(combinedBs);
            }
        } catch (Exception ex) {
            Notification.show("Error generating statements: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
