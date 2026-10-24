package org.example.server.webui.purchase;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
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

@Route(value = "purchases", layout = MainAppLayout.class)
@PageTitle("Purchase Inward Register | DSE ERP")
@PermitAll
public class PurchaseListView extends VerticalLayout {

    private final BusinessOperationsService operationsService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final Grid<OperationDtos.PurchaseDto> grid = new Grid<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> paymentStatusFilter = new ComboBox<>();
    private final DatePicker fromDate = new DatePicker("From Date");
    private final DatePicker toDate = new DatePicker("To Date");

    public PurchaseListView(BusinessOperationsService operationsService) {
        this.operationsService = operationsService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createToolbar();
        createGrid();

        refreshData();
    }

    private void createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout titleBox = new VerticalLayout();
        titleBox.setPadding(false);
        titleBox.setSpacing(false);

        H2 title = new H2("Purchase Register");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");
        Paragraph subtitle = new Paragraph("Manage supplier bills, inward shipments, and vendor payables");
        subtitle.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, subtitle);

        Button newBtn = new Button("New Purchase Inward", new Icon(VaadinIcon.PLUS_CIRCLE), e -> UI.getCurrent().navigate("purchases/edit"));
        newBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(titleBox, newBtn);
        add(header);
    }

    private void createToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        toolbar.getStyle().set("gap", "12px").set("flex-wrap", "wrap");

        searchField.setPlaceholder("Search bill #, supplier, phone, GSTIN...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setWidth("320px");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> refreshData());

        paymentStatusFilter.setLabel("Payment Status");
        paymentStatusFilter.setItems("ALL", "PAID", "PARTIALLY PAID", "UNPAID");
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

    private void createGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS);

        grid.addColumn(OperationDtos.PurchaseDto::invoiceNo)
                .setHeader("Bill #")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(OperationDtos.PurchaseDto::invoiceDate)
                .setHeader("Date")
                .setAutoWidth(true);

        grid.addColumn(dto -> dto.supplier() != null ? dto.supplier().name() : "—")
                .setHeader("Supplier Name")
                .setFlexGrow(1);

        grid.addColumn(OperationDtos.PurchaseDto::dueDate)
                .setHeader("Due Date")
                .setAutoWidth(true);

        grid.addColumn(dto -> currencyFormat.format(dto.totalAmount()))
                .setHeader("Total Bill")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addColumn(dto -> currencyFormat.format(dto.paidAmount()))
                .setHeader("Paid")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addColumn(dto -> currencyFormat.format(Math.max(0, dto.totalAmount() - dto.paidAmount())))
                .setHeader("Payable Balance")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addComponentColumn(dto -> {
            String status = dto.paymentStatus() != null ? dto.paymentStatus().toUpperCase() : "UNPAID";
            Span badge = new Span(status);
            badge.getElement().getThemeList().add("badge small");
            if ("PAID".equals(status)) badge.getElement().getThemeList().add("success");
            else if ("PARTIALLY PAID".equals(status)) badge.getElement().getThemeList().add("warning");
            else badge.getElement().getThemeList().add("contrast");
            return badge;
        }).setHeader("Payment").setAutoWidth(true);

        grid.addComponentColumn(dto -> {
            HorizontalLayout actions = new HorizontalLayout();
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> UI.getCurrent().navigate("purchases/edit?id=" + dto.id()));
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button printBtn = new Button(new Icon(VaadinIcon.PRINT), e -> {
                UI.getCurrent().getPage().open("/api/documents/render?type=PURCHASE_INVOICE&number=" + dto.invoiceNo() + "&format=PDF", "_blank");
            });
            printBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            actions.add(editBtn, printBtn);
            return actions;
        }).setHeader("Actions").setAutoWidth(true);

        add(grid);
        setFlexGrow(1, grid);
    }

    private void refreshData() {
        try {
            OperationDtos.PurchasePage page = operationsService.purchasesPage(
                    0, 100, searchField.getValue(), null,
                    fromDate.getValue() != null ? fromDate.getValue().toString() : null,
                    toDate.getValue() != null ? toDate.getValue().toString() : null,
                    paymentStatusFilter.getValue(), null, null, null, null, true, false
            );
            if (page != null && page.rows() != null) {
                grid.setItems(page.rows());
            }
        } catch (Exception ex) {
            Notification.show("Error loading purchase inward data: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }
}
