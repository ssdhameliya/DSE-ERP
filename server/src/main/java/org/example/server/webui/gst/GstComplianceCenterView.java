package org.example.server.webui.gst;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.gst.GstComplianceService;
import org.example.server.gst.GstDtos;
import org.example.server.webui.MainAppLayout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Route(value = "gst/compliance", layout = MainAppLayout.class)
@PageTitle("GST Compliance Center | DSE ERP")
@PermitAll
public class GstComplianceCenterView extends VerticalLayout {

    private final GstComplianceService gstService;

    private final ComboBox<String> periodCombo = new ComboBox<>("Filing Period (MMYYYY)");
    private final Grid<GstDtos.Gstr2bRecordDto> reconGrid = new Grid<>();

    // GSTR-3B Metric Spans
    private final Span g3bOutwardTaxable = new Span("₹ 0.00");
    private final Span g3bOutwardTax = new Span("₹ 0.00");
    private final Span g3bItcAvailable = new Span("₹ 0.00");
    private final Span g3bNetCash = new Span("₹ 0.00");

    // Metrics for GSTR-2B
    private final Span reconMatchedCount = new Span("0");
    private final Span reconMissing2bCount = new Span("0");
    private final Span reconEligibleItc = new Span("₹ 0.00");

    public GstComplianceCenterView(GstComplianceService gstService) {
        this.gstService = gstService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("GST Compliance Center (GSTR-2B, GSTR-3B & E-Way)");
        title.getStyle().set("margin", "0");

        setupPeriodCombo();

        Button refreshBtn = new Button("Run Re-Audit", VaadinIcon.REFRESH.create(), e -> refreshAll());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(title, periodCombo, refreshBtn);
        header.expand(title);
        add(header);

        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        tabSheet.add("GSTR-2B ITC Matching Engine", buildGstr2bTab());
        tabSheet.add("GSTR-3B Summary & Cash Liability", buildGstr3bTab());

        add(tabSheet);
        refreshAll();
    }

    private void setupPeriodCombo() {
        LocalDate now = LocalDate.now();
        List<String> periods = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            LocalDate d = now.minusMonths(i);
            periods.add(d.format(DateTimeFormatter.ofPattern("MMyyyy")));
        }
        periodCombo.setItems(periods);
        periodCombo.setValue(periods.getFirst());
        periodCombo.addValueChangeListener(e -> refreshAll());
    }

    private VerticalLayout buildGstr2bTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);

        HorizontalLayout kpis = new HorizontalLayout();
        kpis.setWidthFull();
        kpis.add(createKpiCard("Fully Matched Invoices", reconMatchedCount));
        kpis.add(createKpiCard("Missing in GSTR-2B (Supplier Delay)", reconMissing2bCount));
        kpis.add(createKpiCard("Eligible ITC Ready to Claim", reconEligibleItc));

        configureReconGrid();
        layout.add(kpis, reconGrid);
        layout.expand(reconGrid);
        return layout;
    }

    private VerticalLayout buildGstr3bTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(true);

        HorizontalLayout kpis = new HorizontalLayout();
        kpis.setWidthFull();
        kpis.add(createKpiCard("Total Outward Taxable Turnover", g3bOutwardTaxable));
        kpis.add(createKpiCard("Total Outward Output Tax", g3bOutwardTax));
        kpis.add(createKpiCard("Eligible Input Tax Credit (ITC)", g3bItcAvailable));
        kpis.add(createKpiCard("Net Cash Tax Payable", g3bNetCash));

        Span info = new Span("The GSTR-3B values above are calculated in real time directly from your validated Sales registers, Purchase credits, and GSTR-2B reconciliations adhering to CBIC statutory rules.");
        info.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "14px").set("margin-top", "24px");

        layout.add(kpis, info);
        return layout;
    }

    private void configureReconGrid() {
        reconGrid.setSizeFull();
        reconGrid.addColumn(GstDtos.Gstr2bRecordDto::supplierGstin).setHeader("Supplier GSTIN").setAutoWidth(true);
        reconGrid.addColumn(GstDtos.Gstr2bRecordDto::supplierTradeName).setHeader("Supplier Trade Name").setFlexGrow(1).setSortable(true);
        reconGrid.addColumn(GstDtos.Gstr2bRecordDto::invoiceNumber).setHeader("Invoice #").setAutoWidth(true);
        reconGrid.addColumn(GstDtos.Gstr2bRecordDto::invoiceDate).setHeader("Date").setAutoWidth(true).setSortable(true);
        reconGrid.addColumn(r -> "₹ " + fmt(r.taxableValue())).setHeader("Taxable Value").setAutoWidth(true);
        reconGrid.addColumn(r -> "₹ " + fmt(r.igstAmount())).setHeader("IGST").setAutoWidth(true);
        reconGrid.addColumn(r -> "₹ " + fmt(r.cgstAmount())).setHeader("CGST").setAutoWidth(true);
        reconGrid.addColumn(r -> "₹ " + fmt(r.sgstAmount())).setHeader("SGST").setAutoWidth(true);

        reconGrid.addComponentColumn(r -> {
            Span s = new Span(r.matchStatus());
            s.getElement().getThemeList().add("badge");
            if ("MATCHED".equalsIgnoreCase(r.matchStatus())) s.getElement().getThemeList().add("success");
            else if ("MISSING_IN_2B".equalsIgnoreCase(r.matchStatus())) s.getElement().getThemeList().add("error");
            else s.getElement().getThemeList().add("warning");
            return s;
        }).setHeader("Match Status").setAutoWidth(true);

        reconGrid.addComponentColumn(r -> {
            Button noticeBtn = new Button("Notice", VaadinIcon.ENVELOPE.create(), e -> openNoticeDialog(r.id()));
            noticeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
            return noticeBtn;
        }).setHeader("Action").setAutoWidth(true);
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

    private String fmt(BigDecimal val) {
        return val != null ? val.toPlainString() : "0.00";
    }

    private void refreshAll() {
        String period = periodCombo.getValue();
        if (period == null || period.isBlank()) return;

        try {
            List<GstDtos.Gstr2bRecordDto> records = gstService.listReconciliation(period);
            reconGrid.setItems(records);

            GstDtos.ReconciliationMetricsDto metrics = gstService.getMetrics(period);
            if (metrics != null) {
                reconMatchedCount.setText(String.valueOf(metrics.matchedCount()));
                reconMissing2bCount.setText(String.valueOf(metrics.missingIn2bCount()));
                reconEligibleItc.setText("₹ " + fmt(metrics.eligibleItcAmount()));
            }

            GstDtos.Gstr3bSummaryDto g3b = gstService.calculateGstr3b(period);
            if (g3b != null) {
                g3bOutwardTaxable.setText("₹ " + fmt(g3b.outwardTaxable()));
                BigDecimal totalOutput = (g3b.outwardIgst() != null ? g3b.outwardIgst() : BigDecimal.ZERO)
                        .add(g3b.outwardCgst() != null ? g3b.outwardCgst() : BigDecimal.ZERO)
                        .add(g3b.outwardSgst() != null ? g3b.outwardSgst() : BigDecimal.ZERO);
                g3bOutwardTax.setText("₹ " + fmt(totalOutput));

                BigDecimal totalItc = (g3b.itcAvailableIgst() != null ? g3b.itcAvailableIgst() : BigDecimal.ZERO)
                        .add(g3b.itcAvailableCgst() != null ? g3b.itcAvailableCgst() : BigDecimal.ZERO)
                        .add(g3b.itcAvailableSgst() != null ? g3b.itcAvailableSgst() : BigDecimal.ZERO);
                g3bItcAvailable.setText("₹ " + fmt(totalItc));
                g3bNetCash.setText("₹ " + fmt(g3b.totalNetCashPayable()));
            }
        } catch (Exception ex) {
            Notification.show("Error loading GST data: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openNoticeDialog(Long reconId) {
        if (reconId == null) return;
        try {
            GstDtos.SupplierNoticeDto notice = gstService.generateSupplierNotice(reconId);
            Dialog d = new Dialog();
            d.setHeaderTitle("Supplier Notice Draft (" + notice.supplierName() + ")");
            d.setWidth("650px");

            TextArea body = new TextArea("Message Content");
            body.setValue(notice.messageText());
            body.setWidthFull();
            body.setHeight("250px");

            d.add(body);
            Button close = new Button("Close", e -> d.close());
            d.getFooter().add(close);
            d.open();
        } catch (Exception ex) {
            Notification.show("Error generating notice: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
