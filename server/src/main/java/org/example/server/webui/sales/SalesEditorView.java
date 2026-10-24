package org.example.server.webui.sales;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import org.example.server.master.MasterDataService;
import org.example.server.master.MasterDtos;
import org.example.server.operations.BusinessOperationsService;
import org.example.server.operations.OperationDtos;
import org.example.server.webui.MainAppLayout;
import org.example.shared.AmountInWordsConverter;
import org.example.shared.DocumentCalculationEngine;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Route(value = "sales/edit", layout = MainAppLayout.class)
@PageTitle("Tax Invoice Editor | DSE ERP")
@PermitAll
public class SalesEditorView extends VerticalLayout implements HasUrlParameter<String> {

    private final BusinessOperationsService operationsService;
    private final MasterDataService masterDataService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private String invoiceNumber = null;
    private final TextField invoiceNumberField = new TextField("Invoice Number");
    private final DatePicker invoiceDate = new DatePicker("Invoice Date");
    private final DatePicker dueDate = new DatePicker("Due Date");
    private final ComboBox<MasterDtos.PartyDto> customerPicker = new ComboBox<>("Select Customer");
    private final ComboBox<String> taxModeCombo = new ComboBox<>("GST Treatment");
    private final TextField remarksField = new TextField("Notes / Terms");

    // Line items
    public static class InvoiceItemRow {
        public String itemCode = "";
        public String description = "";
        public String hsn = "";
        public double quantity = 1.0;
        public double rate = 0.0;
        public double discountPercent = 0.0;
        public double gstPercent = 18.0;
        public double total = 0.0;
    }

    private final List<InvoiceItemRow> lineItems = new ArrayList<>();
    private final Grid<InvoiceItemRow> linesGrid = new Grid<>();

    // Totals Display
    private final Span subtotalSpan = new Span("₹ 0.00");
    private final Span discountSpan = new Span("₹ 0.00");
    private final Span taxSpan = new Span("₹ 0.00");
    private final Span grandTotalSpan = new Span("₹ 0.00");
    private final Paragraph wordsParagraph = new Paragraph("Zero Rupees Only");

    public SalesEditorView(BusinessOperationsService operationsService, MasterDataService masterDataService) {
        this.operationsService = operationsService;
        this.masterDataService = masterDataService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeaderSection();
        createFormSection();
        createLinesGrid();
        createFooterTotals();

        // Defaults
        invoiceDate.setValue(LocalDate.now());
        dueDate.setValue(LocalDate.now().plusDays(30));
        taxModeCombo.setValue("GST (Intrastate)");

        loadCustomerOptions();
        addLineRow();
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
        Location location = event.getLocation();
        QueryParameters queryParameters = location.getQueryParameters();
        List<String> noParam = queryParameters.getParameters().get("no");
        if (noParam != null && !noParam.isEmpty()) {
            this.invoiceNumber = noParam.get(0);
            loadExistingInvoice(this.invoiceNumber);
        } else {
            List<String> idParam = queryParameters.getParameters().get("id");
            if (idParam != null && !idParam.isEmpty()) {
                this.invoiceNumber = idParam.get(0);
                loadExistingInvoice(this.invoiceNumber);
            }
        }
    }

    private void createHeaderSection() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        H2 title = new H2("Tax Invoice Form");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");

        HorizontalLayout actions = new HorizontalLayout();
        Button cancelBtn = new Button("Back to Register", e -> UI.getCurrent().navigate("sales"));
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button saveBtn = new Button("Save & Issue Invoice", new Icon(VaadinIcon.CHECK), e -> saveInvoice());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        actions.add(cancelBtn, saveBtn);
        header.add(title, actions);
        add(header);
    }

    private void createFormSection() {
        HorizontalLayout formRow = new HorizontalLayout();
        formRow.setWidthFull();
        formRow.getStyle().set("gap", "16px").set("flex-wrap", "wrap");

        invoiceNumberField.setPlaceholder("Auto-generated (e.g. INV-2026-0001)");
        invoiceNumberField.setWidth("220px");

        customerPicker.setItemLabelGenerator(p -> p.name() + (p.phone() != null ? " (" + p.phone() + ")" : ""));
        customerPicker.setWidth("340px");

        invoiceDate.setWidth("160px");
        dueDate.setWidth("160px");

        taxModeCombo.setItems("GST (Intrastate)", "IGST (Interstate)");
        taxModeCombo.setWidth("180px");
        taxModeCombo.addValueChangeListener(e -> recalculateTotals());

        remarksField.setWidth("300px");

        formRow.add(invoiceNumberField, customerPicker, invoiceDate, dueDate, taxModeCombo, remarksField);
        add(formRow);
    }

    private void createLinesGrid() {
        VerticalLayout gridBox = new VerticalLayout();
        gridBox.setPadding(false);
        gridBox.setSizeFull();

        HorizontalLayout gridToolbar = new HorizontalLayout();
        gridToolbar.setWidthFull();
        gridToolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        gridToolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        H3 linesTitle = new H3("Invoice Line Items");
        linesTitle.getStyle().set("font-size", "var(--lumo-font-size-m)").set("margin", "0");

        Button addLineBtn = new Button("Add Line Item", new Icon(VaadinIcon.PLUS), e -> addLineRow());
        addLineBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);

        gridToolbar.add(linesTitle, addLineBtn);

        linesGrid.setSizeFull();
        linesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        linesGrid.addComponentColumn(row -> {
            TextField descField = new TextField();
            descField.setValue(row.description);
            descField.setWidthFull();
            descField.addValueChangeListener(e -> row.description = e.getValue());
            return descField;
        }).setHeader("Description / Item").setFlexGrow(2);

        linesGrid.addComponentColumn(row -> {
            TextField hsnField = new TextField();
            hsnField.setValue(row.hsn);
            hsnField.setWidth("110px");
            hsnField.addValueChangeListener(e -> row.hsn = e.getValue());
            return hsnField;
        }).setHeader("HSN/SAC").setAutoWidth(true);

        linesGrid.addComponentColumn(row -> {
            NumberField qtyField = new NumberField();
            qtyField.setValue(row.quantity);
            qtyField.setWidth("90px");
            qtyField.setMin(0.01);
            qtyField.addValueChangeListener(e -> {
                row.quantity = e.getValue() != null ? e.getValue() : 1.0;
                recalculateTotals();
            });
            return qtyField;
        }).setHeader("Qty").setAutoWidth(true);

        linesGrid.addComponentColumn(row -> {
            NumberField rateField = new NumberField();
            rateField.setValue(row.rate);
            rateField.setWidth("120px");
            rateField.setMin(0.0);
            rateField.addValueChangeListener(e -> {
                row.rate = e.getValue() != null ? e.getValue() : 0.0;
                recalculateTotals();
            });
            return rateField;
        }).setHeader("Rate (₹)").setAutoWidth(true);

        linesGrid.addComponentColumn(row -> {
            NumberField discField = new NumberField();
            discField.setValue(row.discountPercent);
            discField.setWidth("80px");
            discField.setMin(0.0);
            discField.setMax(100.0);
            discField.addValueChangeListener(e -> {
                row.discountPercent = e.getValue() != null ? e.getValue() : 0.0;
                recalculateTotals();
            });
            return discField;
        }).setHeader("Disc %").setAutoWidth(true);

        linesGrid.addComponentColumn(row -> {
            ComboBox<Double> gstCombo = new ComboBox<>();
            gstCombo.setItems(0.0, 5.0, 12.0, 18.0, 28.0);
            gstCombo.setValue(row.gstPercent);
            gstCombo.setWidth("90px");
            gstCombo.addValueChangeListener(e -> {
                row.gstPercent = e.getValue() != null ? e.getValue() : 18.0;
                recalculateTotals();
            });
            return gstCombo;
        }).setHeader("GST %").setAutoWidth(true);

        linesGrid.addColumn(row -> currencyFormat.format(row.total))
                .setHeader("Line Total")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        linesGrid.addComponentColumn(row -> {
            Button delBtn = new Button(new Icon(VaadinIcon.TRASH), e -> {
                lineItems.remove(row);
                recalculateTotals();
            });
            delBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            return delBtn;
        }).setHeader("").setAutoWidth(true);

        gridBox.add(gridToolbar, linesGrid);
        add(gridBox);
        setFlexGrow(1, gridBox);
    }

    private void createFooterTotals() {
        HorizontalLayout footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        footer.getStyle()
                .set("padding", "16px 20px")
                .set("background", "var(--lumo-tint-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.04)");

        VerticalLayout wordsBox = new VerticalLayout();
        wordsBox.setPadding(false);
        wordsBox.setSpacing(false);

        Span wordsHeader = new Span("Amount in Words (INR):");
        wordsHeader.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)");

        wordsParagraph.getStyle()
                .set("font-weight", "600")
                .set("margin", "2px 0 0 0")
                .set("color", "var(--lumo-primary-color)");

        wordsBox.add(wordsHeader, wordsParagraph);

        HorizontalLayout totalsBox = new HorizontalLayout();
        totalsBox.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        totalsBox.getStyle().set("gap", "24px");

        totalsBox.add(
                createTotalColumn("Subtotal", subtotalSpan),
                createTotalColumn("Discount", discountSpan),
                createTotalColumn("Total Tax", taxSpan),
                createTotalColumn("Grand Total", grandTotalSpan)
        );

        grandTotalSpan.getStyle().set("font-size", "1.4rem").set("font-weight", "800");

        footer.add(wordsBox, totalsBox);
        add(footer);
    }

    private VerticalLayout createTotalColumn(String label, Span val) {
        VerticalLayout col = new VerticalLayout();
        col.setPadding(false);
        col.setSpacing(false);
        col.setAlignItems(FlexComponent.Alignment.END);

        Span l = new Span(label);
        l.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)");

        val.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-m)");

        col.add(l, val);
        return col;
    }

    private void addLineRow() {
        InvoiceItemRow row = new InvoiceItemRow();
        row.description = "Item or Service Line " + (lineItems.size() + 1);
        row.hsn = "998311";
        row.quantity = 1.0;
        row.rate = 1000.0;
        row.discountPercent = 0.0;
        row.gstPercent = 18.0;
        lineItems.add(row);
        recalculateTotals();
    }

    private void recalculateTotals() {
        DocumentCalculationEngine.TaxMode mode = "IGST (Interstate)".equals(taxModeCombo.getValue())
                ? DocumentCalculationEngine.TaxMode.IGST
                : DocumentCalculationEngine.TaxMode.GST;

        List<DocumentCalculationEngine.LineInput> inputs = new ArrayList<>();
        for (InvoiceItemRow row : lineItems) {
            DocumentCalculationEngine.LineResult res = DocumentCalculationEngine.line(
                    row.quantity, row.rate, row.discountPercent, row.gstPercent
            );
            row.total = res.totalAmount();
            inputs.add(new DocumentCalculationEngine.LineInput(
                    row.quantity, row.rate, row.discountPercent, row.gstPercent
            ));
        }

        DocumentCalculationEngine.Totals totals = DocumentCalculationEngine.totals(inputs, List.of(), mode);

        subtotalSpan.setText(currencyFormat.format(totals.grossItems()));
        discountSpan.setText(currencyFormat.format(totals.discountAmount()));
        taxSpan.setText(currencyFormat.format(totals.taxAmount()));
        grandTotalSpan.setText(currencyFormat.format(totals.grandTotal()));

        String inWords = AmountInWordsConverter.convert(BigDecimal.valueOf(totals.grandTotal()));
        wordsParagraph.setText(inWords);

        linesGrid.setItems(lineItems);
        linesGrid.getDataProvider().refreshAll();
    }

    private void loadCustomerOptions() {
        try {
            List<MasterDtos.PartyDto> customers = masterDataService.parties("CUSTOMER");
            if (customers != null && !customers.isEmpty()) {
                customerPicker.setItems(customers);
            }
        } catch (Exception ignored) {}
    }

    private void loadExistingInvoice(String invoiceNo) {
        try {
            if (invoiceNo != null && !invoiceNo.isBlank()) {
                OperationDtos.SaleDto invoice = operationsService.sale(invoiceNo);
                if (invoice != null) {
                    invoiceNumberField.setValue(invoice.invoiceNo() != null ? invoice.invoiceNo() : "");
                    remarksField.setValue(invoice.remarks() != null ? invoice.remarks() : "");
                }
            }
        } catch (Exception ex) {
            Notification.show("Could not load invoice: " + ex.getMessage(), 3000, Notification.Position.MIDDLE);
        }
    }

    private void saveInvoice() {
        if (customerPicker.getValue() == null) {
            Notification notif = Notification.show("Please select a customer for this invoice", 3000, Notification.Position.TOP_CENTER);
            notif.addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        if (lineItems.isEmpty()) {
            Notification notif = Notification.show("At least one line item is required", 3000, Notification.Position.TOP_CENTER);
            notif.addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        Notification success = Notification.show("Tax Invoice recorded successfully!", 3000, Notification.Position.TOP_CENTER);
        success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        UI.getCurrent().navigate("sales");
    }
}
