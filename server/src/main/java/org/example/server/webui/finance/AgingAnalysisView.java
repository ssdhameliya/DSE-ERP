package org.example.server.webui.finance;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
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
import org.example.server.aging.AgingAnalysisDtos;
import org.example.server.aging.AgingAnalysisService;
import org.example.server.webui.MainAppLayout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Route(value = "finance/aging", layout = MainAppLayout.class)
@PageTitle("Aging Analysis | DSE ERP")
@PermitAll
public class AgingAnalysisView extends VerticalLayout {

    private final AgingAnalysisService agingService;
    private final DatePicker asOfDatePicker = new DatePicker("As of Date", LocalDate.now());
    private final Grid<AgingAnalysisDtos.PartyAgingDto> receivablesGrid = new Grid<>();
    private final Grid<AgingAnalysisDtos.PartyAgingDto> payablesGrid = new Grid<>();

    private final Span totalReceivablesKpi = new Span("₹ 0.00");
    private final Span totalPayablesKpi = new Span("₹ 0.00");
    private final Span highRiskReceivablesCount = new Span("0");

    public AgingAnalysisView(AgingAnalysisService agingService) {
        this.agingService = agingService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Accounts Receivable & Payable Aging Analysis");
        title.getStyle().set("margin", "0");

        asOfDatePicker.addValueChangeListener(e -> refreshData());

        Button refreshBtn = new Button("Recalculate", VaadinIcon.REFRESH.create(), e -> refreshData());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(title, asOfDatePicker, refreshBtn);
        header.expand(title);
        add(header);

        // KPI Banner
        HorizontalLayout kpiRow = new HorizontalLayout();
        kpiRow.setWidthFull();
        kpiRow.setSpacing(true);

        kpiRow.add(createKpiCard("Total Receivables Outstanding", totalReceivablesKpi, "text-blue"));
        kpiRow.add(createKpiCard("Total Payables Outstanding", totalPayablesKpi, "text-amber"));
        kpiRow.add(createKpiCard("High Risk (>90 Days) Debts", highRiskReceivablesCount, "text-red"));
        add(kpiRow);

        // TabSheet for Receivables vs Payables
        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        configureGrid(receivablesGrid, "Customer");
        configureGrid(payablesGrid, "Supplier");

        tabSheet.add("Customer Receivables Aging", receivablesGrid);
        tabSheet.add("Supplier Payables Aging", payablesGrid);

        add(tabSheet);
        refreshData();
    }

    private VerticalLayout createKpiCard(String label, Span valSpan, String colorClass) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "8px")
                .set("padding", "16px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)");
        Span lbl = new Span(label);
        lbl.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "13px");
        valSpan.getStyle().set("font-size", "22px").set("font-weight", "bold");
        card.add(lbl, valSpan);
        return card;
    }

    private void configureGrid(Grid<AgingAnalysisDtos.PartyAgingDto> grid, String partyLabel) {
        grid.addColumn(AgingAnalysisDtos.PartyAgingDto::partyName).setHeader(partyLabel + " Name").setFlexGrow(1).setSortable(true);
        grid.addColumn(AgingAnalysisDtos.PartyAgingDto::gstin).setHeader("GSTIN").setAutoWidth(true);
        grid.addColumn(AgingAnalysisDtos.PartyAgingDto::phone).setHeader("Phone").setAutoWidth(true);

        grid.addColumn(p -> fmt(p.bucket0To30())).setHeader("0 - 30 Days").setAutoWidth(true);
        grid.addColumn(p -> fmt(p.bucket31To60())).setHeader("31 - 60 Days").setAutoWidth(true);
        grid.addColumn(p -> fmt(p.bucket61To90())).setHeader("61 - 90 Days").setAutoWidth(true);
        grid.addColumn(p -> fmt(p.bucket90Plus())).setHeader("90+ Days").setAutoWidth(true);
        grid.addColumn(p -> fmt(p.totalDue())).setHeader("Total Balance").setAutoWidth(true);

        grid.addComponentColumn(p -> {
            Span b = new Span(p.riskLevel());
            b.getElement().getThemeList().add("badge");
            if ("HIGH".equalsIgnoreCase(p.riskLevel()) || "CRITICAL".equalsIgnoreCase(p.riskLevel())) {
                b.getElement().getThemeList().add("error");
            } else if ("MEDIUM".equalsIgnoreCase(p.riskLevel())) {
                b.getElement().getThemeList().add("warning");
            } else {
                b.getElement().getThemeList().add("success");
            }
            return b;
        }).setHeader("Risk Profile").setAutoWidth(true);
    }

    private String fmt(BigDecimal val) {
        return "₹ " + (val != null ? val.toPlainString() : "0.00");
    }

    private void refreshData() {
        LocalDate asOf = asOfDatePicker.getValue() != null ? asOfDatePicker.getValue() : LocalDate.now();
        try {
            AgingAnalysisDtos.AgingSummaryDto rec = agingService.getReceivablesAging(asOf);
            if (rec != null) {
                receivablesGrid.setItems(rec.parties() != null ? rec.parties() : List.of());
                totalReceivablesKpi.setText("₹ " + (rec.totalOutstanding() != null ? rec.totalOutstanding().toPlainString() : "0.00"));
                long highRisk = rec.parties() != null ? rec.parties().stream().filter(p -> "HIGH".equalsIgnoreCase(p.riskLevel()) || "CRITICAL".equalsIgnoreCase(p.riskLevel())).count() : 0;
                highRiskReceivablesCount.setText(String.valueOf(highRisk));
            }

            AgingAnalysisDtos.AgingSummaryDto pay = agingService.getPayablesAging(asOf);
            if (pay != null) {
                payablesGrid.setItems(pay.parties() != null ? pay.parties() : List.of());
                totalPayablesKpi.setText("₹ " + (pay.totalOutstanding() != null ? pay.totalOutstanding().toPlainString() : "0.00"));
            }
        } catch (Exception ex) {
            Notification.show("Error loading aging: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
