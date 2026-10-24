package org.example.server.webui.crm;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.master.MasterDataService;
import org.example.server.master.MasterDtos;
import org.example.server.supplier360.Supplier360Dtos;
import org.example.server.supplier360.Supplier360Service;
import org.example.server.webui.MainAppLayout;

import java.math.BigDecimal;
import java.util.List;

@Route(value = "crm/supplier-360", layout = MainAppLayout.class)
@PageTitle("Supplier 360 CRM | DSE ERP")
@PermitAll
public class Supplier360View extends VerticalLayout {

    private final Supplier360Service s360Service;
    private final MasterDataService masterService;

    private final ComboBox<MasterDtos.PartyDto> supplierCombo = new ComboBox<>("Select Supplier");

    // Profile Spans
    private final Span suppName = new Span("-");
    private final Span suppGstin = new Span("-");
    private final Span suppPhone = new Span("-");
    private final Span suppEmail = new Span("-");
    private final Span suppAddress = new Span("-");

    // KPIs
    private final Span kpiOutstanding = new Span("₹ 0.00");
    private final Span kpiTotalPurchases = new Span("₹ 0.00");
    private final Span kpiPurchaseCount = new Span("0");
    private final Span kpiLastPayment = new Span("₹ 0.00");

    // Grids
    private final Grid<Supplier360Dtos.PurchaseRow> purchaseGrid = new Grid<>();
    private final Grid<Supplier360Dtos.PaymentRow> paymentGrid = new Grid<>();
    private final Grid<Supplier360Dtos.ContactRow> contactGrid = new Grid<>();
    private final Grid<Supplier360Dtos.NoteRow> noteGrid = new Grid<>();

    public Supplier360View(Supplier360Service s360Service, MasterDataService masterService) {
        this.s360Service = s360Service;
        this.masterService = masterService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Supplier 360° Procurement & Payable Profile");
        title.getStyle().set("margin", "0");

        supplierCombo.setWidth("380px");
        supplierCombo.setItemLabelGenerator(p -> p.partyCode() + " - " + p.name());
        supplierCombo.addValueChangeListener(e -> loadSupplierDetails(e.getValue()));

        header.add(title, supplierCombo);
        header.expand(title);
        add(header);

        // Profile & KPI Container
        VerticalLayout topCard = new VerticalLayout();
        topCard.setWidthFull();
        topCard.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "8px")
                .set("padding", "16px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)");

        HorizontalLayout profileRow = new HorizontalLayout();
        profileRow.setWidthFull();
        profileRow.add(createInfoBadge("Supplier Name", suppName));
        profileRow.add(createInfoBadge("GSTIN", suppGstin));
        profileRow.add(createInfoBadge("Phone", suppPhone));
        profileRow.add(createInfoBadge("Email", suppEmail));
        profileRow.add(createInfoBadge("Address", suppAddress));

        HorizontalLayout kpis = new HorizontalLayout();
        kpis.setWidthFull();
        kpis.add(createKpiCard("Outstanding Payable", kpiOutstanding, "error"));
        kpis.add(createKpiCard("Lifetime Purchases", kpiTotalPurchases, "success"));
        kpis.add(createKpiCard("Total Purchase Orders", kpiPurchaseCount, "primary"));
        kpis.add(createKpiCard("Last Settled Payment", kpiLastPayment, "contrast"));

        topCard.add(profileRow, kpis);
        add(topCard);

        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        configurePurchaseGrid();
        configurePaymentGrid();
        configureContactGrid();
        configureNoteGrid();

        tabSheet.add("Purchase Invoices", purchaseGrid);
        tabSheet.add("Payment Outflows", paymentGrid);
        tabSheet.add("Key Organization Contacts", contactGrid);
        tabSheet.add("Vendor Notes & Terms", noteGrid);

        add(tabSheet);
        loadSupplierCombo();
    }

    private VerticalLayout createInfoBadge(String label, Span valSpan) {
        VerticalLayout vl = new VerticalLayout();
        vl.setPadding(false);
        vl.setSpacing(false);
        Span lbl = new Span(label);
        lbl.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "11px");
        valSpan.getStyle().set("font-weight", "600").set("font-size", "14px");
        vl.add(lbl, valSpan);
        return vl;
    }

    private VerticalLayout createKpiCard(String label, Span valSpan, String theme) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "6px")
                .set("padding", "12px");
        Span lbl = new Span(label);
        lbl.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");
        valSpan.getStyle().set("font-size", "18px").set("font-weight", "bold");
        card.add(lbl, valSpan);
        return card;
    }

    private void configurePurchaseGrid() {
        purchaseGrid.setSizeFull();
        purchaseGrid.addColumn(Supplier360Dtos.PurchaseRow::invoiceNo).setHeader("Invoice #").setAutoWidth(true);
        purchaseGrid.addColumn(Supplier360Dtos.PurchaseRow::invoiceDate).setHeader("Date").setAutoWidth(true);
        purchaseGrid.addColumn(p -> "₹ " + fmt(p.totalAmount())).setHeader("Total Amount").setAutoWidth(true);
        purchaseGrid.addColumn(p -> "₹ " + fmt(p.paidAmount())).setHeader("Paid").setAutoWidth(true);
        purchaseGrid.addColumn(p -> "₹ " + fmt(p.outstanding())).setHeader("Outstanding").setAutoWidth(true);
        purchaseGrid.addComponentColumn(p -> {
            Span s = new Span(p.paymentStatus());
            s.getElement().getThemeList().add("badge");
            if ("PAID".equalsIgnoreCase(p.paymentStatus())) s.getElement().getThemeList().add("success");
            else s.getElement().getThemeList().add("warning");
            return s;
        }).setHeader("Payment").setAutoWidth(true);
    }

    private void configurePaymentGrid() {
        paymentGrid.setSizeFull();
        paymentGrid.addColumn(Supplier360Dtos.PaymentRow::paymentDate).setHeader("Date").setAutoWidth(true);
        paymentGrid.addColumn(Supplier360Dtos.PaymentRow::referenceNo).setHeader("UTR / Ref #").setAutoWidth(true);
        paymentGrid.addColumn(Supplier360Dtos.PaymentRow::paymentMode).setHeader("Mode").setAutoWidth(true);
        paymentGrid.addColumn(p -> "₹ " + fmt(p.amount())).setHeader("Amount").setAutoWidth(true);
        paymentGrid.addColumn(Supplier360Dtos.PaymentRow::invoiceNo).setHeader("Against Invoice").setAutoWidth(true);
        paymentGrid.addColumn(Supplier360Dtos.PaymentRow::notes).setHeader("Notes").setFlexGrow(1);
    }

    private void configureContactGrid() {
        contactGrid.setSizeFull();
        contactGrid.addColumn(Supplier360Dtos.ContactRow::name).setHeader("Contact Person").setAutoWidth(true);
        contactGrid.addColumn(Supplier360Dtos.ContactRow::designation).setHeader("Designation").setAutoWidth(true);
        contactGrid.addColumn(Supplier360Dtos.ContactRow::mobile).setHeader("Mobile").setAutoWidth(true);
        contactGrid.addColumn(Supplier360Dtos.ContactRow::email).setHeader("Email").setAutoWidth(true);
        contactGrid.addComponentColumn(c -> {
            Span s = new Span(c.primary() ? "PRIMARY" : "SECONDARY");
            s.getElement().getThemeList().add("badge");
            if (c.primary()) s.getElement().getThemeList().add("success");
            return s;
        }).setHeader("Type").setAutoWidth(true);
    }

    private void configureNoteGrid() {
        noteGrid.setSizeFull();
        noteGrid.addColumn(Supplier360Dtos.NoteRow::createdAt).setHeader("Logged At").setAutoWidth(true);
        noteGrid.addColumn(Supplier360Dtos.NoteRow::createdBy).setHeader("Author").setAutoWidth(true);
        noteGrid.addColumn(Supplier360Dtos.NoteRow::note).setHeader("Vendor Note").setFlexGrow(1);
    }

    private String fmt(BigDecimal b) {
        return b != null ? b.toPlainString() : "0.00";
    }

    private void loadSupplierCombo() {
        try {
            List<MasterDtos.PartyDto> suppliers = masterService.parties("SUPPLIER");
            supplierCombo.setItems(suppliers);
            if (!suppliers.isEmpty()) {
                supplierCombo.setValue(suppliers.getFirst());
            }
        } catch (Exception ex) {
            Notification.show("Error loading supplier catalog: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }

    private void loadSupplierDetails(MasterDtos.PartyDto party) {
        if (party == null || party.id() == null) return;
        try {
            Supplier360Dtos.Summary s = s360Service.summary(party.id());
            if (s != null) {
                if (s.supplier() != null) {
                    suppName.setText(s.supplier().name());
                    suppGstin.setText(s.supplier().gstin() != null && !s.supplier().gstin().isBlank() ? s.supplier().gstin() : "Unregistered");
                    suppPhone.setText(s.supplier().phone());
                    suppEmail.setText(s.supplier().email());
                    suppAddress.setText(s.supplier().address());
                }

                kpiOutstanding.setText("₹ " + fmt(s.outstandingPayable()));
                kpiTotalPurchases.setText("₹ " + fmt(s.totalPurchases()));
                kpiPurchaseCount.setText(String.valueOf(s.purchaseCount()));
                kpiLastPayment.setText("₹ " + fmt(s.lastPaymentAmount()) + (s.lastPaymentDate() != null && !s.lastPaymentDate().isBlank() ? " on " + s.lastPaymentDate() : ""));

                purchaseGrid.setItems(s360Service.purchases(party.id()));
                paymentGrid.setItems(s360Service.payments(party.id()));
                contactGrid.setItems(s360Service.contacts(party.id()));
                noteGrid.setItems(s360Service.notes(party.id()));
            }
        } catch (Exception ex) {
            Notification.show("Error loading Supplier 360 profile: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
