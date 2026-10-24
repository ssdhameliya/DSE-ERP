package org.example.server.webui.master;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
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
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.master.MasterDataService;
import org.example.server.master.MasterDtos;
import org.example.server.webui.MainAppLayout;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Route(value = "master/customers", layout = MainAppLayout.class)
@PageTitle("Customer Master | DSE ERP")
@PermitAll
public class CustomerView extends VerticalLayout {

    private final MasterDataService masterDataService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final Grid<MasterDtos.PartyDto> grid = new Grid<>();
    private final TextField searchField = new TextField();

    public CustomerView(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;

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

        H2 title = new H2("Customer Directory");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");
        Paragraph sub = new Paragraph("Maintain customer accounts, billing addresses, and GST credentials");
        sub.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, sub);

        Button newBtn = new Button("New Customer", new Icon(VaadinIcon.PLUS_CIRCLE), e -> openCustomerModal(null));
        newBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(titleBox, newBtn);
        add(header);
    }

    private void createToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        searchField.setPlaceholder("Search customer name, code, phone, GSTIN, city...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setWidth("380px");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> refreshData());

        Button refreshBtn = new Button("Refresh", new Icon(VaadinIcon.REFRESH), e -> refreshData());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        toolbar.add(searchField, refreshBtn);
        add(toolbar);
    }

    private void createGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS);

        grid.addColumn(MasterDtos.PartyDto::partyCode)
                .setHeader("Code")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(MasterDtos.PartyDto::name)
                .setHeader("Customer Name")
                .setSortable(true)
                .setFlexGrow(2);

        grid.addColumn(MasterDtos.PartyDto::phone)
                .setHeader("Phone")
                .setAutoWidth(true);

        grid.addColumn(MasterDtos.PartyDto::email)
                .setHeader("Email")
                .setAutoWidth(true);

        grid.addColumn(MasterDtos.PartyDto::gstin)
                .setHeader("GSTIN")
                .setAutoWidth(true);

        grid.addColumn(dto -> currencyFormat.format(dto.openingBalance()))
                .setHeader("Opening Bal")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addComponentColumn(dto -> {
            Span badge = new Span(dto.active() ? "ACTIVE" : "INACTIVE");
            badge.getElement().getThemeList().add("badge small " + (dto.active() ? "success" : "contrast"));
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        grid.addComponentColumn(dto -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> openCustomerModal(dto));
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            return editBtn;
        }).setHeader("Actions").setAutoWidth(true);

        add(grid);
        setFlexGrow(1, grid);
    }

    private void refreshData() {
        try {
            List<MasterDtos.PartyDto> list = masterDataService.parties("CUSTOMER");
            String q = searchField.getValue();
            if (q != null && !q.isBlank()) {
                String term = q.toLowerCase(Locale.ROOT);
                list = list.stream().filter(p ->
                        (p.name() != null && p.name().toLowerCase().contains(term)) ||
                        (p.phone() != null && p.phone().toLowerCase().contains(term)) ||
                        (p.gstin() != null && p.gstin().toLowerCase().contains(term)) ||
                        (p.partyCode() != null && p.partyCode().toLowerCase().contains(term))
                ).toList();
            }
            grid.setItems(list);
        } catch (Exception ex) {
            Notification.show("Error loading customers: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }

    private void openCustomerModal(MasterDtos.PartyDto customer) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(customer == null ? "Add New Customer" : "Edit Customer: " + customer.name());

        VerticalLayout form = new VerticalLayout();
        form.setSpacing(true);

        TextField nameField = new TextField("Customer Name");
        nameField.setWidthFull();
        TextField phoneField = new TextField("Contact Phone");
        phoneField.setWidthFull();
        TextField emailField = new TextField("Email Address");
        emailField.setWidthFull();
        TextField gstinField = new TextField("GSTIN Number");
        gstinField.setWidthFull();
        TextField addressField = new TextField("Billing Address");
        addressField.setWidthFull();
        NumberField balField = new NumberField("Opening Balance (₹)");
        balField.setWidthFull();

        if (customer != null) {
            nameField.setValue(customer.name() != null ? customer.name() : "");
            phoneField.setValue(customer.phone() != null ? customer.phone() : "");
            emailField.setValue(customer.email() != null ? customer.email() : "");
            gstinField.setValue(customer.gstin() != null ? customer.gstin() : "");
            addressField.setValue(customer.address() != null ? customer.address() : "");
            balField.setValue(customer.openingBalance());
        }

        form.add(nameField, phoneField, emailField, gstinField, addressField, balField);
        dialog.add(form);

        Button cancel = new Button("Cancel", e -> dialog.close());
        Button save = new Button("Save Customer", e -> {
            if (nameField.getValue().isBlank()) {
                Notification.show("Customer name is required", 2500, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            dialog.close();
            Notification.show("Customer saved successfully!", 2500, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshData();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancel, save);
        dialog.open();
    }
}
