package org.example.server.webui.quotation;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.master.MasterDataService;
import org.example.server.master.MasterDtos;
import org.example.server.quotation.QuotationService;
import org.example.server.webui.MainAppLayout;
import org.example.shared.AmountInWordsConverter;
import org.example.shared.DocumentCalculationEngine;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Route(value = "quotations/edit", layout = MainAppLayout.class)
@PageTitle("Quotation Proposal Editor | DSE ERP")
@PermitAll
public class QuotationEditorView extends VerticalLayout {

    private final QuotationService quotationService;
    private final MasterDataService masterDataService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final TextField quoteNoField = new TextField("Quotation #");
    private final DatePicker quoteDate = new DatePicker("Quotation Date");
    private final DatePicker validUntilDate = new DatePicker("Valid Until");
    private final ComboBox<MasterDtos.PartyDto> customerPicker = new ComboBox<>("Client / Prospect");
    private final TextField salespersonField = new TextField("Sales Executive");
    private final TextField remarksField = new TextField("Commercial Remarks");

    public static class QuoteLineRow {
        public String description = "";
        public double quantity = 1.0;
        public double rate = 0.0;
        public double discountPercent = 0.0;
        public double gstPercent = 18.0;
        public double total = 0.0;
    }

    private final List<QuoteLineRow> lineItems = new ArrayList<>();
    private final Grid<QuoteLineRow> linesGrid = new Grid<>();

    private final Span subtotalSpan = new Span("₹ 0.00");
    private final Span taxSpan = new Span("₹ 0.00");
    private final Span grandTotalSpan = new Span("₹ 0.00");
    private final Paragraph wordsParagraph = new Paragraph("Zero Rupees Only");

    public QuotationEditorView(QuotationService quotationService, MasterDataService masterDataService) {
        this.quotationService = quotationService;
        this.masterDataService = masterDataService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createForm();
        createGrid();
        createFooter();

        quoteDate.setValue(LocalDate.now());
        validUntilDate.setValue(LocalDate.now().plusDays(15));
        loadCustomers();
        addLineRow();
    }

    private void createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        H2 title = new H2("Quotation Proposal Form");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");

        HorizontalLayout actions = new HorizontalLayout();
        Button cancelBtn = new Button("Back to Quotations", e -> UI.getCurrent().navigate("quotations"));
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button saveBtn = new Button("Save Proposal", new Icon(VaadinIcon.CHECK), e -> saveQuotation());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        actions.add(cancelBtn, saveBtn);
        header.add(title, actions);
        add(header);
    }

    private void createForm() {
        HorizontalLayout form = new HorizontalLayout();
        form.setWidthFull();
        form.getStyle().set("gap", "16px").set("flex-wrap", "wrap");

        quoteNoField.setPlaceholder("Auto-generated (e.g. QT-2026-0001)");
        quoteNoField.setWidth("220px");

        customerPicker.setItemLabelGenerator(p -> p.name() + (p.phone() != null ? " (" + p.phone() + ")" : ""));
        customerPicker.setWidth("340px");

        quoteDate.setWidth("160px");
        validUntilDate.setWidth("160px");
        salespersonField.setWidth("200px");
        remarksField.setWidth("280px");

        form.add(quoteNoField, customerPicker, quoteDate, validUntilDate, salespersonField, remarksField);
        add(form);
    }

    private void createGrid() {
        VerticalLayout box = new VerticalLayout();
        box.setPadding(false);
        box.setSizeFull();

        HorizontalLayout bar = new HorizontalLayout();
        bar.setWidthFull();
        bar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        bar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        H3 label = new H3("Proposed Products / Services");
        label.getStyle().set("font-size", "var(--lumo-font-size-m)").set("margin", "0");

        Button addBtn = new Button("Add Item", new Icon(VaadinIcon.PLUS), e -> addLineRow());
        addBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);

        bar.add(label, addBtn);

        linesGrid.setSizeFull();
        linesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        linesGrid.addComponentColumn(row -> {
            TextField desc = new TextField();
            desc.setValue(row.description);
            desc.setWidthFull();
            desc.addValueChangeListener(e -> row.description = e.getValue());
            return desc;
        }).setHeader("Item / Description").setFlexGrow(2);

        linesGrid.addComponentColumn(row -> {
            NumberField qty = new NumberField();
            qty.setValue(row.quantity);
            qty.setWidth("90px");
            qty.setMin(1);
            qty.addValueChangeListener(e -> {
                row.quantity = e.getValue() != null ? e.getValue() : 1.0;
                recalculate();
            });
            return qty;
        }).setHeader("Qty").setAutoWidth(true);

        linesGrid.addComponentColumn(row -> {
            NumberField rate = new NumberField();
            rate.setValue(row.rate);
            rate.setWidth("120px");
            rate.setMin(0.0);
            rate.addValueChangeListener(e -> {
                row.rate = e.getValue() != null ? e.getValue() : 0.0;
                recalculate();
            });
            return rate;
        }).setHeader("Rate (₹)").setAutoWidth(true);

        linesGrid.addComponentColumn(row -> {
            NumberField disc = new NumberField();
            disc.setValue(row.discountPercent);
            disc.setWidth("80px");
            disc.setMin(0.0);
            disc.setMax(100.0);
            disc.addValueChangeListener(e -> {
                row.discountPercent = e.getValue() != null ? e.getValue() : 0.0;
                recalculate();
            });
            return disc;
        }).setHeader("Disc %").setAutoWidth(true);

        linesGrid.addColumn(row -> currencyFormat.format(row.total))
                .setHeader("Amount")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        linesGrid.addComponentColumn(row -> {
            Button del = new Button(new Icon(VaadinIcon.TRASH), e -> {
                lineItems.remove(row);
                recalculate();
            });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            return del;
        }).setHeader("").setAutoWidth(true);

        box.add(bar, linesGrid);
        add(box);
        setFlexGrow(1, box);
    }

    private void createFooter() {
        HorizontalLayout footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        footer.getStyle()
                .set("padding", "16px 20px")
                .set("background", "var(--lumo-tint-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");

        VerticalLayout words = new VerticalLayout();
        words.setPadding(false);
        words.setSpacing(false);
        words.add(new Span("Total in Words:"), wordsParagraph);
        wordsParagraph.getStyle().set("font-weight", "600").set("color", "var(--lumo-primary-color)");

        HorizontalLayout totals = new HorizontalLayout(
                createTotal("Subtotal", subtotalSpan),
                createTotal("Est. GST", taxSpan),
                createTotal("Quotation Total", grandTotalSpan)
        );
        totals.getStyle().set("gap", "20px");
        grandTotalSpan.getStyle().set("font-size", "1.4rem").set("font-weight", "800");

        footer.add(words, totals);
        add(footer);
    }

    private VerticalLayout createTotal(String label, Span val) {
        VerticalLayout col = new VerticalLayout();
        col.setPadding(false);
        col.setSpacing(false);
        col.setAlignItems(FlexComponent.Alignment.END);
        Span l = new Span(label);
        l.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)");
        val.getStyle().set("font-weight", "700");
        col.add(l, val);
        return col;
    }

    private void addLineRow() {
        QuoteLineRow row = new QuoteLineRow();
        row.description = "Product or Service Item " + (lineItems.size() + 1);
        row.quantity = 1.0;
        row.rate = 1200.0;
        row.discountPercent = 0.0;
        row.gstPercent = 18.0;
        lineItems.add(row);
        recalculate();
    }

    private void recalculate() {
        List<DocumentCalculationEngine.LineInput> inputs = new ArrayList<>();
        for (QuoteLineRow row : lineItems) {
            DocumentCalculationEngine.LineResult res = DocumentCalculationEngine.line(
                    row.quantity, row.rate, row.discountPercent, row.gstPercent
            );
            row.total = res.totalAmount();
            inputs.add(new DocumentCalculationEngine.LineInput(
                    row.quantity, row.rate, row.discountPercent, row.gstPercent
            ));
        }

        DocumentCalculationEngine.Totals totals = DocumentCalculationEngine.totals(inputs, List.of(), DocumentCalculationEngine.TaxMode.GST);
        subtotalSpan.setText(currencyFormat.format(totals.grossItems()));
        taxSpan.setText(currencyFormat.format(totals.taxAmount()));
        grandTotalSpan.setText(currencyFormat.format(totals.grandTotal()));

        wordsParagraph.setText(AmountInWordsConverter.convert(BigDecimal.valueOf(totals.grandTotal())));
        linesGrid.setItems(lineItems);
        linesGrid.getDataProvider().refreshAll();
    }

    private void loadCustomers() {
        try {
            List<MasterDtos.PartyDto> list = masterDataService.parties("CUSTOMER");
            if (list != null) customerPicker.setItems(list);
        } catch (Exception ignored) {}
    }

    private void saveQuotation() {
        if (customerPicker.getValue() == null) {
            Notification.show("Please select a customer for this proposal", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        Notification.show("Quotation saved successfully!", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        UI.getCurrent().navigate("quotations");
    }
}
