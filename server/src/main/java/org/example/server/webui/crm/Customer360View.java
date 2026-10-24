package org.example.server.webui.crm;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
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
import org.example.server.customer360.Customer360Dtos;
import org.example.server.customer360.Customer360Service;
import org.example.server.master.MasterDataService;
import org.example.server.master.MasterDtos;
import org.example.server.webui.MainAppLayout;

import java.math.BigDecimal;
import java.util.List;

@Route(value = "crm/customer-360", layout = MainAppLayout.class)
@PageTitle("Customer 360 CRM | DSE ERP")
@PermitAll
public class Customer360View extends VerticalLayout {

    private final Customer360Service c360Service;
    private final MasterDataService masterService;

    private final ComboBox<MasterDtos.PartyDto> customerCombo = new ComboBox<>("Select Customer");

    // Profile Spans
    private final Span custName = new Span("-");
    private final Span custGstin = new Span("-");
    private final Span custPhone = new Span("-");
    private final Span custEmail = new Span("-");
    private final Span custAddress = new Span("-");

    // KPIs
    private final Span kpiOutstanding = new Span("₹ 0.00");
    private final Span kpiTotalSales = new Span("₹ 0.00");
    private final Span kpiOpenQuotes = new Span("₹ 0.00");
    private final Span kpiLastPayment = new Span("₹ 0.00");

    // Grids
    private final Grid<Customer360Dtos.InvoiceRow> invoiceGrid = new Grid<>();
    private final Grid<Customer360Dtos.QuotationRow> quoteGrid = new Grid<>();
    private final Grid<Customer360Dtos.ContactRow> contactGrid = new Grid<>();
    private final Grid<Customer360Dtos.NoteRow> noteGrid = new Grid<>();

    public Customer360View(Customer360Service c360Service, MasterDataService masterService) {
        this.c360Service = c360Service;
        this.masterService = masterService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Customer 360° Relationship & Revenue Dashboard");
        title.getStyle().set("margin", "0");

        customerCombo.setWidth("380px");
        customerCombo.setItemLabelGenerator(p -> p.partyCode() + " - " + p.name());
        customerCombo.addValueChangeListener(e -> loadCustomerDetails(e.getValue()));

        header.add(title, customerCombo);
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
        profileRow.add(createInfoBadge("Customer Name", custName));
        profileRow.add(createInfoBadge("GSTIN", custGstin));
        profileRow.add(createInfoBadge("Phone", custPhone));
        profileRow.add(createInfoBadge("Email", custEmail));
        profileRow.add(createInfoBadge("Address", custAddress));

        HorizontalLayout kpis = new HorizontalLayout();
        kpis.setWidthFull();
        kpis.add(createKpiCard("Outstanding Receivable", kpiOutstanding, "error"));
        kpis.add(createKpiCard("Lifetime Sales", kpiTotalSales, "success"));
        kpis.add(createKpiCard("Active Quotations", kpiOpenQuotes, "primary"));
        kpis.add(createKpiCard("Last Received Payment", kpiLastPayment, "contrast"));

        topCard.add(profileRow, kpis);
        add(topCard);

        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        configureInvoiceGrid();
        configureQuoteGrid();
        configureContactGrid();
        configureNoteGrid();

        tabSheet.add("Sales Invoices", invoiceGrid);
        tabSheet.add("Quotations & Proposals", quoteGrid);
        tabSheet.add("Key Organization Contacts", contactGrid);
        tabSheet.add("CRM Interaction Notes", noteGrid);

        add(tabSheet);
        loadCustomerCombo();
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

    private void configureInvoiceGrid() {
        invoiceGrid.setSizeFull();
        invoiceGrid.addColumn(Customer360Dtos.InvoiceRow::invoiceNo).setHeader("Invoice #").setAutoWidth(true);
        invoiceGrid.addColumn(Customer360Dtos.InvoiceRow::invoiceDate).setHeader("Date").setAutoWidth(true);
        invoiceGrid.addColumn(i -> "₹ " + fmt(i.totalAmount())).setHeader("Total Amount").setAutoWidth(true);
        invoiceGrid.addColumn(i -> "₹ " + fmt(i.paidAmount())).setHeader("Paid").setAutoWidth(true);
        invoiceGrid.addColumn(i -> "₹ " + fmt(i.outstanding())).setHeader("Outstanding").setAutoWidth(true);
        invoiceGrid.addComponentColumn(i -> {
            Span s = new Span(i.paymentStatus());
            s.getElement().getThemeList().add("badge");
            if ("PAID".equalsIgnoreCase(i.paymentStatus())) s.getElement().getThemeList().add("success");
            else s.getElement().getThemeList().add("warning");
            return s;
        }).setHeader("Payment").setAutoWidth(true);
    }

    private void configureQuoteGrid() {
        quoteGrid.setSizeFull();
        quoteGrid.addColumn(Customer360Dtos.QuotationRow::no).setHeader("Quote #").setAutoWidth(true);
        quoteGrid.addColumn(Customer360Dtos.QuotationRow::date).setHeader("Date").setAutoWidth(true);
        quoteGrid.addColumn(Customer360Dtos.QuotationRow::salesperson).setHeader("Salesperson").setAutoWidth(true);
        quoteGrid.addColumn(q -> "₹ " + fmt(q.amount())).setHeader("Amount").setAutoWidth(true);
        quoteGrid.addComponentColumn(q -> {
            Span s = new Span(q.status());
            s.getElement().getThemeList().add("badge");
            return s;
        }).setHeader("Status").setAutoWidth(true);
    }

    private void configureContactGrid() {
        contactGrid.setSizeFull();
        contactGrid.addColumn(Customer360Dtos.ContactRow::name).setHeader("Contact Person").setAutoWidth(true);
        contactGrid.addColumn(Customer360Dtos.ContactRow::designation).setHeader("Designation").setAutoWidth(true);
        contactGrid.addColumn(Customer360Dtos.ContactRow::department).setHeader("Department").setAutoWidth(true);
        contactGrid.addColumn(Customer360Dtos.ContactRow::mobile).setHeader("Mobile").setAutoWidth(true);
        contactGrid.addColumn(Customer360Dtos.ContactRow::email).setHeader("Email").setAutoWidth(true);
        contactGrid.addComponentColumn(c -> {
            Span s = new Span(c.primary() ? "PRIMARY" : "SECONDARY");
            s.getElement().getThemeList().add("badge");
            if (c.primary()) s.getElement().getThemeList().add("success");
            return s;
        }).setHeader("Type").setAutoWidth(true);
    }

    private void configureNoteGrid() {
        noteGrid.setSizeFull();
        noteGrid.addColumn(Customer360Dtos.NoteRow::createdAt).setHeader("Logged At").setAutoWidth(true);
        noteGrid.addColumn(Customer360Dtos.NoteRow::createdBy).setHeader("Author").setAutoWidth(true);
        noteGrid.addColumn(Customer360Dtos.NoteRow::note).setHeader("Interaction Note").setFlexGrow(1);
    }

    private String fmt(BigDecimal b) {
        return b != null ? b.toPlainString() : "0.00";
    }

    private void loadCustomerCombo() {
        try {
            List<MasterDtos.PartyDto> customers = masterService.parties("CUSTOMER");
            customerCombo.setItems(customers);
            if (!customers.isEmpty()) {
                customerCombo.setValue(customers.getFirst());
            }
        } catch (Exception ex) {
            Notification.show("Error loading customer catalog: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }

    private void loadCustomerDetails(MasterDtos.PartyDto party) {
        if (party == null || party.id() == null) return;
        try {
            Customer360Dtos.Summary s = c360Service.summary(party.id());
            if (s != null) {
                if (s.customer() != null) {
                    custName.setText(s.customer().name());
                    custGstin.setText(s.customer().gstin() != null && !s.customer().gstin().isBlank() ? s.customer().gstin() : "Unregistered");
                    custPhone.setText(s.customer().phone());
                    custEmail.setText(s.customer().email());
                    custAddress.setText(s.customer().address());
                }

                kpiOutstanding.setText("₹ " + fmt(s.outstandingReceivable()));
                kpiTotalSales.setText("₹ " + fmt(s.totalSales()));
                kpiOpenQuotes.setText("₹ " + fmt(s.openQuotationValue()) + " (" + s.openQuotationCount() + " quotes)");
                kpiLastPayment.setText("₹ " + fmt(s.lastPaymentAmount()) + (s.lastPaymentDate() != null && !s.lastPaymentDate().isBlank() ? " on " + s.lastPaymentDate() : ""));

                invoiceGrid.setItems(c360Service.invoices(party.id()));
                quoteGrid.setItems(c360Service.quotations(party.id()));
                contactGrid.setItems(c360Service.contacts(party.id()));
                noteGrid.setItems(c360Service.notes(party.id()));
            }
        } catch (Exception ex) {
            Notification.show("Error loading Customer 360 profile: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
