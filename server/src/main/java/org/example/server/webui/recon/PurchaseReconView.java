package org.example.server.webui.recon;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
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
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.recon.PurchaseReconDtos;
import org.example.server.recon.PurchaseReconService;
import org.example.server.webui.MainAppLayout;

import java.time.LocalDate;
import java.util.List;

@Route(value = "recon/purchase", layout = MainAppLayout.class)
@PageTitle("Purchase Reconciliation Register | DSE ERP")
@PermitAll
public class PurchaseReconView extends VerticalLayout {

    private final PurchaseReconService reconService;
    private final Grid<PurchaseReconDtos.ReconDto> grid = new Grid<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();

    private final Span totalCountKpi = new Span("0");
    private final Span openCountKpi = new Span("0");
    private final Span totalValueKpi = new Span("₹ 0.00");
    private final Span outstandingValueKpi = new Span("₹ 0.00");

    public PurchaseReconView(PurchaseReconService reconService) {
        this.reconService = reconService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Purchase Invoices & Statement Reconciliation");
        title.getStyle().set("margin", "0");

        Button newBtn = new Button("New Purchase Recon", VaadinIcon.PLUS.create(), e -> openEditDialog(null));
        newBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button refreshBtn = new Button("Refresh", VaadinIcon.REFRESH.create(), e -> refreshGrid());

        header.add(title, newBtn, refreshBtn);
        header.expand(title);
        add(header);

        // KPI Banner
        HorizontalLayout kpiRow = new HorizontalLayout();
        kpiRow.setWidthFull();
        kpiRow.add(createKpiCard("Total Records", totalCountKpi));
        kpiRow.add(createKpiCard("Open Invoices", openCountKpi));
        kpiRow.add(createKpiCard("Total Invoice Value", totalValueKpi));
        kpiRow.add(createKpiCard("Unreconciled Outstanding", outstandingValueKpi));
        add(kpiRow);

        // Filters
        searchField.setPlaceholder("Search Reference, Supplier, or Invoice #...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidth("350px");
        searchField.addValueChangeListener(e -> refreshGrid());

        statusFilter.setPlaceholder("Filter Status");
        statusFilter.setItems("All Statuses", "OPEN", "PARTIAL", "RECONCILED", "NEEDS REVIEW");
        statusFilter.setValue("All Statuses");
        statusFilter.addValueChangeListener(e -> refreshGrid());

        HorizontalLayout filterRow = new HorizontalLayout(searchField, statusFilter);
        add(filterRow);

        configureGrid();
        add(grid);
        refreshGrid();
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

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(PurchaseReconDtos.ReconDto::reference).setHeader("Ref #").setAutoWidth(true).setSortable(true);
        grid.addColumn(PurchaseReconDtos.ReconDto::supplierName).setHeader("Supplier Name").setFlexGrow(1).setSortable(true);
        grid.addColumn(PurchaseReconDtos.ReconDto::supplierInvoiceNo).setHeader("Supplier Inv #").setAutoWidth(true);
        grid.addColumn(PurchaseReconDtos.ReconDto::invoiceDate).setHeader("Date").setAutoWidth(true).setSortable(true);
        grid.addColumn(r -> "₹ " + String.format("%.2f", r.taxableValue())).setHeader("Taxable").setAutoWidth(true);
        grid.addColumn(r -> "₹ " + String.format("%.2f", r.invoiceValue())).setHeader("Invoice Value").setAutoWidth(true);
        grid.addColumn(r -> "₹ " + String.format("%.2f", r.linkedAmount())).setHeader("Linked Bank").setAutoWidth(true);
        grid.addColumn(r -> "₹ " + String.format("%.2f", r.balance())).setHeader("Balance Due").setAutoWidth(true);

        grid.addComponentColumn(r -> {
            Span s = new Span(r.status());
            s.getElement().getThemeList().add("badge");
            if ("RECONCILED".equalsIgnoreCase(r.status())) s.getElement().getThemeList().add("success");
            else if ("PARTIAL".equalsIgnoreCase(r.status())) s.getElement().getThemeList().add("warning");
            else if ("NEEDS REVIEW".equalsIgnoreCase(r.status())) s.getElement().getThemeList().add("error");
            else s.getElement().getThemeList().add("contrast");
            return s;
        }).setHeader("Status").setAutoWidth(true);

        grid.addComponentColumn(r -> {
            Button edit = new Button(VaadinIcon.EDIT.create(), e -> openEditDialog(r));
            edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button del = new Button(VaadinIcon.TRASH.create(), e -> handleDelete(r));
            del.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

            return new HorizontalLayout(edit, del);
        }).setHeader("Actions").setAutoWidth(true);
    }

    private void refreshGrid() {
        try {
            String stat = statusFilter.getValue();
            if ("All Statuses".equalsIgnoreCase(stat)) stat = "";
            PurchaseReconDtos.Page page = reconService.page(0, 100, searchField.getValue(), stat);
            if (page != null) {
                grid.setItems(page.rows() != null ? page.rows() : List.of());
                if (page.metrics() != null) {
                    totalCountKpi.setText(String.valueOf(page.metrics().total()));
                    openCountKpi.setText(String.valueOf(page.metrics().open()));
                    totalValueKpi.setText("₹ " + String.format("%.2f", page.metrics().invoiceValue()));
                    outstandingValueKpi.setText("₹ " + String.format("%.2f", page.metrics().outstandingValue()));
                }
            }
        } catch (Exception ex) {
            Notification.show("Error loading purchase recon: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void handleDelete(PurchaseReconDtos.ReconDto r) {
        if (r == null) return;
        try {
            reconService.deleteRecon(r.id());
            Notification.show("Purchase Recon deleted", 2500, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshGrid();
        } catch (Exception ex) {
            Notification.show("Failed to delete: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openEditDialog(PurchaseReconDtos.ReconDto existing) {
        Dialog d = new Dialog();
        boolean editing = existing != null;
        d.setHeaderTitle(editing ? "Edit Purchase Recon (" + existing.reference() + ")" : "Create Purchase Recon");
        d.setWidth("650px");

        ComboBox<PurchaseReconDtos.SupplierDto> supplierCombo = new ComboBox<>("Recon Supplier");
        List<PurchaseReconDtos.SupplierDto> suppliers = reconService.searchSuppliers("", 100);
        supplierCombo.setItems(suppliers);
        supplierCombo.setItemLabelGenerator(s -> s.legalName() + " (" + s.reference() + ")");
        supplierCombo.setWidthFull();

        TextField invNo = new TextField("Supplier Invoice Number");
        DatePicker invDate = new DatePicker("Invoice Date", LocalDate.now());

        NumberField taxable = new NumberField("Taxable Value (₹)");
        NumberField cgst = new NumberField("CGST (₹)");
        NumberField sgst = new NumberField("SGST (₹)");
        NumberField igst = new NumberField("IGST (₹)");
        NumberField other = new NumberField("Other Adjustment (₹)");
        NumberField total = new NumberField("Total Invoice Value (₹)");

        taxable.setValue(0d);
        cgst.setValue(0d);
        sgst.setValue(0d);
        igst.setValue(0d);
        other.setValue(0d);
        total.setValue(0d);

        TextField notes = new TextField("Notes");
        notes.setWidthFull();

        if (editing) {
            suppliers.stream().filter(s -> s.id().equals(existing.supplierId())).findFirst().ifPresent(supplierCombo::setValue);
            invNo.setValue(existing.supplierInvoiceNo());
            try { invDate.setValue(LocalDate.parse(existing.invoiceDate())); } catch (Exception ignored) {}
            taxable.setValue(existing.taxableValue());
            cgst.setValue(existing.cgst());
            sgst.setValue(existing.sgst());
            igst.setValue(existing.igst());
            other.setValue(existing.otherAdjustment());
            total.setValue(existing.invoiceValue());
            notes.setValue(existing.notes() != null ? existing.notes() : "");
        }

        VerticalLayout form = new VerticalLayout(
                supplierCombo,
                new HorizontalLayout(invNo, invDate),
                new HorizontalLayout(taxable, total),
                new HorizontalLayout(cgst, sgst, igst),
                other, notes
        );
        form.setPadding(false);
        d.add(form);

        Button save = new Button("Save", e -> {
            if (supplierCombo.isEmpty() || invNo.isEmpty() || invDate.isEmpty() || total.getValue() == null || total.getValue() <= 0) {
                Notification.show("Please fill required fields and ensure total > 0", 3000, Notification.Position.MIDDLE);
                return;
            }
            try {
                reconService.saveRecon(new PurchaseReconDtos.ReconSaveRequest(
                        editing ? existing.id() : null,
                        supplierCombo.getValue().id(),
                        invNo.getValue(),
                        invDate.getValue().toString(),
                        taxable.getValue() != null ? taxable.getValue() : 0d,
                        cgst.getValue() != null ? cgst.getValue() : 0d,
                        sgst.getValue() != null ? sgst.getValue() : 0d,
                        igst.getValue() != null ? igst.getValue() : 0d,
                        other.getValue() != null ? other.getValue() : 0d,
                        total.getValue(),
                        notes.getValue(),
                        editing ? existing.rowVersion() : 0L
                ));
                Notification.show("Saved successfully!", 2500, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                d.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> d.close());

        d.getFooter().add(cancel, save);
        d.open();
    }
}
