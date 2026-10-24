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
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.operations.BusinessOperationsService;
import org.example.server.operations.OperationDtos;
import org.example.server.webui.MainAppLayout;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Route(value = "sales", layout = MainAppLayout.class)
@PageTitle("Sales Register | DSE ERP")
@PermitAll
public class SalesListView extends VerticalLayout {

    private final BusinessOperationsService operationsService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final Grid<OperationDtos.SaleDto> grid = new Grid<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> paymentStatusFilter = new ComboBox<>();
    private final DatePicker fromDate = new DatePicker("From Date");
    private final DatePicker toDate = new DatePicker("To Date");

    private final Span totalSalesLabel = new Span("₹ 0.00");
    private final Span totalPaidLabel = new Span("₹ 0.00");
    private final Span totalReceivableLabel = new Span("₹ 0.00");

    public SalesListView(BusinessOperationsService operationsService) {
        this.operationsService = operationsService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeaderSection();
        createFilterToolbar();
        createKpiStrip();
        createGrid();

        refreshData();
    }

    private void createHeaderSection() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout titleBox = new VerticalLayout();
        titleBox.setPadding(false);
        titleBox.setSpacing(false);

        H2 title = new H2("Sales Register");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");
        Paragraph subtitle = new Paragraph("Manage tax invoices, receivables, and customer billing");
        subtitle.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, subtitle);

        Button newInvoiceBtn = new Button("New Tax Invoice", new Icon(VaadinIcon.PLUS_CIRCLE), e -> UI.getCurrent().navigate("sales/edit"));
        newInvoiceBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(titleBox, newInvoiceBtn);
        add(header);
    }

    private void createFilterToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        toolbar.getStyle().set("gap", "12px").set("flex-wrap", "wrap");

        searchField.setPlaceholder("Search invoice #, customer, phone, GSTIN...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setWidth("320px");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> refreshData());

        paymentStatusFilter.setLabel("Payment Status");
        paymentStatusFilter.setItems("ALL", "PAID", "PARTIALLY PAID", "UNPAID", "OVERDUE");
        paymentStatusFilter.setValue("ALL");
        paymentStatusFilter.setWidth("160px");
        paymentStatusFilter.addValueChangeListener(e -> refreshData());

        fromDate.setWidth("150px");
        fromDate.addValueChangeListener(e -> refreshData());

        toDate.setWidth("150px");
        toDate.addValueChangeListener(e -> refreshData());

        Button resetBtn = new Button("Reset", new Icon(VaadinIcon.CLOSE_CIRCLE), e -> {
            searchField.clear();
            paymentStatusFilter.setValue("ALL");
            fromDate.clear();
            toDate.clear();
            refreshData();
        });
        resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        toolbar.add(searchField, paymentStatusFilter, fromDate, toDate, resetBtn);
        add(toolbar);
    }

    private void createKpiStrip() {
        HorizontalLayout strip = new HorizontalLayout();
        strip.setWidthFull();
        strip.getStyle()
                .set("gap", "16px")
                .set("margin-top", "4px")
                .set("margin-bottom", "4px");

        strip.add(
                createMiniKpi("Total Invoiced", totalSalesLabel, "var(--lumo-primary-color)"),
                createMiniKpi("Total Collected", totalPaidLabel, "var(--lumo-success-color)"),
                createMiniKpi("Outstanding Receivables", totalReceivableLabel, "var(--lumo-error-color)")
        );
        add(strip);
    }

    private Div createMiniKpi(String label, Span val, String accent) {
        Div card = new Div();
        card.getStyle()
                .set("flex", "1")
                .set("padding", "10px 16px")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background", "var(--lumo-tint-5pct)")
                .set("border-left", "3px solid " + accent);

        Span title = new Span(label);
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-weight", "600")
                .set("text-transform", "uppercase")
                .set("display", "block");

        val.getStyle()
                .set("font-size", "1.2rem")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

        card.add(title, val);
        return card;
    }

    private void createGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS);

        grid.addColumn(OperationDtos.SaleDto::invoiceNo)
                .setHeader("Invoice #")
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(OperationDtos.SaleDto::invoiceDate)
                .setHeader("Date")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(dto -> dto.customer() != null ? dto.customer().name() : "—")
                .setHeader("Customer Name")
                .setSortable(true)
                .setFlexGrow(1);

        grid.addColumn(OperationDtos.SaleDto::dueDate)
                .setHeader("Due Date")
                .setAutoWidth(true);

        grid.addColumn(dto -> currencyFormat.format(dto.totalAmount()))
                .setHeader("Net Amount")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addColumn(dto -> currencyFormat.format(dto.paidAmount()))
                .setHeader("Paid")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addColumn(dto -> currencyFormat.format(Math.max(0, dto.totalAmount() - dto.paidAmount())))
                .setHeader("Balance")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addComponentColumn(this::renderStatusBadge)
                .setHeader("Status")
                .setAutoWidth(true)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        grid.addComponentColumn(dto -> {
            HorizontalLayout actions = new HorizontalLayout();
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> UI.getCurrent().navigate("sales/edit?id=" + dto.id()));
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            editBtn.getElement().setAttribute("title", "Edit Invoice");

            Button printBtn = new Button(new Icon(VaadinIcon.PRINT), e -> {
                UI.getCurrent().getPage().open("/api/documents/render?type=SALES_INVOICE&number=" + dto.invoiceNo() + "&format=PDF", "_blank");
            });
            printBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            printBtn.getElement().setAttribute("title", "Print PDF");

            actions.add(editBtn, printBtn);
            return actions;
        }).setHeader("Actions").setAutoWidth(true);

        add(grid);
        setFlexGrow(1, grid);
    }

    private Span renderStatusBadge(OperationDtos.SaleDto dto) {
        String status = dto.paymentStatus() != null ? dto.paymentStatus().toUpperCase() : "UNPAID";
        Span badge = new Span(status);
        badge.getElement().getThemeList().add("badge small");

        if ("PAID".equals(status)) {
            badge.getElement().getThemeList().add("success");
        } else if ("PARTIALLY PAID".equals(status)) {
            badge.getElement().getThemeList().add("warning");
        } else if ("OVERDUE".equals(status)) {
            badge.getElement().getThemeList().add("error");
        } else {
            badge.getElement().getThemeList().add("contrast");
        }
        return badge;
    }

    private void refreshData() {
        try {
            String q = searchField.getValue();
            String status = paymentStatusFilter.getValue();
            String from = fromDate.getValue() != null ? fromDate.getValue().toString() : null;
            String to = toDate.getValue() != null ? toDate.getValue().toString() : null;

            OperationDtos.SalePage page = operationsService.salesPage(
                    0, 100, q, null, null, from, to, status, null, null, null, null, null, null, null, null, true, false, false
            );

            if (page != null && page.rows() != null) {
                grid.setItems(page.rows());
                if (page.filteredTotals() != null) {
                    OperationDtos.RegisterTotals t = page.filteredTotals();
                    totalSalesLabel.setText(currencyFormat.format(t.total()));
                    totalPaidLabel.setText(currencyFormat.format(t.paid()));
                    totalReceivableLabel.setText(currencyFormat.format(t.balance()));
                }
            }
        } catch (Exception ex) {
            Notification.show("Error loading sales data: " + ex.getMessage(), 4000, Notification.Position.TOP_CENTER);
        }
    }
}
