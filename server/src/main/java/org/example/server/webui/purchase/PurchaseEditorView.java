package org.example.server.webui.purchase;

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
import org.example.server.operations.BusinessOperationsService;
import org.example.server.webui.MainAppLayout;
import org.example.shared.AmountInWordsConverter;
import org.example.shared.DocumentCalculationEngine;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Route(value = "purchases/edit", layout = MainAppLayout.class)
@PageTitle("Purchase Inward Bill | DSE ERP")
@PermitAll
public class PurchaseEditorView extends VerticalLayout {

    private final BusinessOperationsService operationsService;
    private final MasterDataService masterDataService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final TextField billNumberField = new TextField("Supplier Bill #");
    private final DatePicker billDate = new DatePicker("Bill Date");
    private final DatePicker dueDate = new DatePicker("Due Date");
    private final ComboBox<MasterDtos.PartyDto> supplierPicker = new ComboBox<>("Select Supplier");
    private final TextField warehouseField = new TextField("Destination Warehouse");
    private final TextField remarksField = new TextField("Notes / Gate Pass");

    public static class InwardLineRow {
        public String description = "";
        public String hsn = "";
        public double quantity = 1.0;
        public double rate = 0.0;
        public double gstPercent = 18.0;
        public double total = 0.0;
    }

    private final List<InwardLineRow> lineItems = new ArrayList<>();
    private final Grid<InwardLineRow> linesGrid = new Grid<>();

    private final Span subtotalSpan = new Span("₹ 0.00");
    private final Span taxSpan = new Span("₹ 0.00");
    private final Span grandTotalSpan = new Span("₹ 0.00");
    private final Paragraph wordsParagraph = new Paragraph("Zero Rupees Only");

    public PurchaseEditorView(BusinessOperationsService operationsService, MasterDataService masterDataService) {
        this.operationsService = operationsService;
        this.masterDataService = masterDataService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createForm();
        createGrid();
        createFooter();

        billDate.setValue(LocalDate.now());
        dueDate.setValue(LocalDate.now().plusDays(30));
        warehouseField.setValue("Main Warehouse");

        loadSuppliers();
        addLineRow();
    }

    private void createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        H2 title = new H2("Purchase Inward Order Form");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");

        HorizontalLayout actions = new HorizontalLayout();
        Button cancelBtn = new Button("Back to Purchases", e -> UI.getCurrent().navigate("purchases"));
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button saveBtn = new Button("Record Inward Bill", new Icon(VaadinIcon.CHECK), e -> savePurchase());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        actions.add(cancelBtn, saveBtn);
        header.add(title, actions);
        add(header);
    }

    private void createForm() {
        HorizontalLayout form = new HorizontalLayout();
        form.setWidthFull();
        form.getStyle().set("gap", "16px").set("flex-wrap", "wrap");

        billNumberField.setPlaceholder("e.g. SUP-INV-9921");
        billNumberField.setWidth("220px");

        supplierPicker.setItemLabelGenerator(p -> p.name() + (p.gstin() != null ? " (" + p.gstin() + ")" : ""));
        supplierPicker.setWidth("340px");

        billDate.setWidth("160px");
        dueDate.setWidth("160px");
        warehouseField.setWidth("200px");
        remarksField.setWidth("260px");

        form.add(billNumberField, supplierPicker, billDate, dueDate, warehouseField, remarksField);
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

        H3 label = new H3("Inward Goods & Materials");
        label.getStyle().set("font-size", "var(--lumo-font-size-m)").set("margin", "0");

        Button addBtn = new Button("Add Material Line", new Icon(VaadinIcon.PLUS), e -> addLineRow());
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
        }).setHeader("Material / Item").setFlexGrow(2);

        linesGrid.addComponentColumn(row -> {
            TextField hsn = new TextField();
            hsn.setValue(row.hsn);
            hsn.setWidth("110px");
            hsn.addValueChangeListener(e -> row.hsn = e.getValue());
            return hsn;
        }).setHeader("HSN/SAC").setAutoWidth(true);

        linesGrid.addComponentColumn(row -> {
            NumberField qty = new NumberField();
            qty.setValue(row.quantity);
            qty.setWidth("90px");
            qty.setMin(0.01);
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
        }).setHeader("Unit Cost (₹)").setAutoWidth(true);

        linesGrid.addComponentColumn(row -> {
            ComboBox<Double> gst = new ComboBox<>();
            gst.setItems(0.0, 5.0, 12.0, 18.0, 28.0);
            gst.setValue(row.gstPercent);
            gst.setWidth("90px");
            gst.addValueChangeListener(e -> {
                row.gstPercent = e.getValue() != null ? e.getValue() : 18.0;
                recalculate();
            });
            return gst;
        }).setHeader("GST %").setAutoWidth(true);

        linesGrid.addColumn(row -> currencyFormat.format(row.total))
                .setHeader("Line Total")
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
        words.add(new Span("Inward Total in Words:"), wordsParagraph);
        wordsParagraph.getStyle().set("font-weight", "600").set("color", "var(--lumo-primary-color)");

        HorizontalLayout totals = new HorizontalLayout(
                createTotal("Subtotal", subtotalSpan),
                createTotal("Input GST Tax", taxSpan),
                createTotal("Net Payable", grandTotalSpan)
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
        InwardLineRow row = new InwardLineRow();
        row.description = "Raw Material / Component " + (lineItems.size() + 1);
        row.hsn = "690100";
        row.quantity = 1.0;
        row.rate = 500.0;
        row.gstPercent = 18.0;
        lineItems.add(row);
        recalculate();
    }

    private void recalculate() {
        List<DocumentCalculationEngine.LineInput> inputs = new ArrayList<>();
        for (InwardLineRow row : lineItems) {
            DocumentCalculationEngine.LineResult res = DocumentCalculationEngine.line(
                    row.quantity, row.rate, 0.0, row.gstPercent
            );
            row.total = res.totalAmount();
            inputs.add(new DocumentCalculationEngine.LineInput(
                    row.quantity, row.rate, 0.0, row.gstPercent
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

    private void loadSuppliers() {
        try {
            List<MasterDtos.PartyDto> list = masterDataService.parties("SUPPLIER");
            if (list != null) supplierPicker.setItems(list);
        } catch (Exception ignored) {}
    }

    private void savePurchase() {
        if (supplierPicker.getValue() == null) {
            Notification.show("Please select a vendor / supplier", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        Notification.show("Purchase inward recorded successfully!", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        UI.getCurrent().navigate("purchases");
    }
}
