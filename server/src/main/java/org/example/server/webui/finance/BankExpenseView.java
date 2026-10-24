package org.example.server.webui.finance;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
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

@Route(value = "finance/bank-expenses", layout = MainAppLayout.class)
@PageTitle("Bank & Expense Register | DSE ERP")
@PermitAll
public class BankExpenseView extends VerticalLayout {

    private final BusinessOperationsService operationsService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final Grid<OperationDtos.FinanceDto> grid = new Grid<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> typeFilter = new ComboBox<>();

    public BankExpenseView(BusinessOperationsService operationsService) {
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

        H2 title = new H2("Bank & Expense Vouchers");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");
        Paragraph sub = new Paragraph("Daily bank deposits, company expenses, withdrawals, and operational vouchers");
        sub.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, sub);

        Button recordBtn = new Button("Record Payment / Expense", new Icon(VaadinIcon.PLUS_CIRCLE), e -> UI.getCurrent().navigate("finance/record-payment"));
        recordBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(titleBox, recordBtn);
        add(header);
    }

    private void createToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        toolbar.getStyle().set("gap", "12px");

        searchField.setPlaceholder("Search voucher #, category, reference, account...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setWidth("380px");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> refreshData());

        typeFilter.setLabel("Voucher Type");
        typeFilter.setItems("ALL", "EXPENSE", "DEPOSIT", "WITHDRAWAL", "PAYMENT");
        typeFilter.setValue("ALL");
        typeFilter.setWidth("160px");
        typeFilter.addValueChangeListener(e -> refreshData());

        Button refreshBtn = new Button("Refresh", new Icon(VaadinIcon.REFRESH), e -> refreshData());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        toolbar.add(searchField, typeFilter, refreshBtn);
        add(toolbar);
    }

    private void createGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS);

        grid.addColumn(OperationDtos.FinanceDto::voucherNo)
                .setHeader("Voucher #")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(OperationDtos.FinanceDto::voucherDate)
                .setHeader("Date")
                .setAutoWidth(true);

        grid.addColumn(OperationDtos.FinanceDto::voucherType)
                .setHeader("Type")
                .setAutoWidth(true);

        grid.addColumn(OperationDtos.FinanceDto::category)
                .setHeader("Category")
                .setFlexGrow(1);

        grid.addColumn(dto -> dto.accountName() != null ? dto.accountName() : "Main Account")
                .setHeader("Account / Bank")
                .setFlexGrow(1);

        grid.addColumn(OperationDtos.FinanceDto::paymentMode)
                .setHeader("Mode")
                .setAutoWidth(true);

        grid.addColumn(dto -> currencyFormat.format(dto.amount()))
                .setHeader("Amount")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addComponentColumn(dto -> {
            Span badge = new Span(dto.reconciled() ? "RECONCILED" : "UNRECONCILED");
            badge.getElement().getThemeList().add("badge small " + (dto.reconciled() ? "success" : "contrast"));
            return badge;
        }).setHeader("Bank Recon").setAutoWidth(true);

        add(grid);
        setFlexGrow(1, grid);
    }

    private void refreshData() {
        try {
            OperationDtos.FinancePage page = operationsService.financePage(
                    0, 100, searchField.getValue(), typeFilter.getValue(), null, null, null, null
            );
            if (page != null && page.rows() != null) {
                grid.setItems(page.rows());
            }
        } catch (Exception ex) {
            Notification.show("Error loading vouchers: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }
}
