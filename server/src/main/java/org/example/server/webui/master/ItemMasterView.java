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

@Route(value = "master/items", layout = MainAppLayout.class)
@PageTitle("Item Master Catalog | DSE ERP")
@PermitAll
public class ItemMasterView extends VerticalLayout {

    private final MasterDataService masterDataService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final Grid<MasterDtos.ItemDto> grid = new Grid<>();
    private final TextField searchField = new TextField();

    public ItemMasterView(MasterDataService masterDataService) {
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

        H2 title = new H2("Item Master Catalog");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");
        Paragraph sub = new Paragraph("Products, raw materials, ceramic components, pricing, and HSN codes");
        sub.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, sub);

        Button newBtn = new Button("New Item", new Icon(VaadinIcon.PLUS_CIRCLE), e -> openItemModal(null));
        newBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(titleBox, newBtn);
        add(header);
    }

    private void createToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        searchField.setPlaceholder("Search item code, description, category, brand, HSN...");
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

        grid.addColumn(MasterDtos.ItemDto::itemCode)
                .setHeader("Item Code")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(MasterDtos.ItemDto::description)
                .setHeader("Description")
                .setSortable(true)
                .setFlexGrow(2);

        grid.addColumn(MasterDtos.ItemDto::category)
                .setHeader("Category")
                .setAutoWidth(true);

        grid.addColumn(MasterDtos.ItemDto::hsn)
                .setHeader("HSN/SAC")
                .setAutoWidth(true);

        grid.addColumn(MasterDtos.ItemDto::unit)
                .setHeader("Unit")
                .setAutoWidth(true);

        grid.addColumn(dto -> currencyFormat.format(dto.sellingPrice()))
                .setHeader("Sale Price")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addColumn(dto -> currencyFormat.format(dto.purchasePrice()))
                .setHeader("Cost Price")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addColumn(dto -> dto.gst() + "%")
                .setHeader("GST")
                .setAutoWidth(true);

        grid.addColumn(MasterDtos.ItemDto::openingStock)
                .setHeader("Stock")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addComponentColumn(dto -> {
            Span badge = new Span(dto.active() ? "ACTIVE" : "INACTIVE");
            badge.getElement().getThemeList().add("badge small " + (dto.active() ? "success" : "contrast"));
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        grid.addComponentColumn(dto -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> openItemModal(dto));
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            return editBtn;
        }).setHeader("Actions").setAutoWidth(true);

        add(grid);
        setFlexGrow(1, grid);
    }

    private void refreshData() {
        try {
            List<MasterDtos.ItemDto> list = masterDataService.items();
            String q = searchField.getValue();
            if (q != null && !q.isBlank()) {
                String term = q.toLowerCase(Locale.ROOT);
                list = list.stream().filter(i ->
                        (i.itemCode() != null && i.itemCode().toLowerCase().contains(term)) ||
                        (i.description() != null && i.description().toLowerCase().contains(term)) ||
                        (i.category() != null && i.category().toLowerCase().contains(term)) ||
                        (i.hsn() != null && i.hsn().toLowerCase().contains(term))
                ).toList();
            }
            grid.setItems(list);
        } catch (Exception ex) {
            Notification.show("Error loading items: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }

    private void openItemModal(MasterDtos.ItemDto item) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(item == null ? "Add New Catalog Item" : "Edit Item: " + item.itemCode());

        VerticalLayout form = new VerticalLayout();
        form.setSpacing(true);

        TextField codeField = new TextField("Item Code");
        codeField.setWidthFull();
        TextField descField = new TextField("Description / Item Name");
        descField.setWidthFull();
        TextField catField = new TextField("Category");
        catField.setWidthFull();
        TextField hsnField = new TextField("HSN / SAC Code");
        hsnField.setWidthFull();
        TextField unitField = new TextField("Unit of Measure");
        unitField.setWidthFull();

        NumberField sellField = new NumberField("Selling Rate (₹)");
        sellField.setWidthFull();
        NumberField costField = new NumberField("Purchase Cost (₹)");
        costField.setWidthFull();
        NumberField gstField = new NumberField("GST %");
        gstField.setWidthFull();

        if (item != null) {
            codeField.setValue(item.itemCode() != null ? item.itemCode() : "");
            descField.setValue(item.description() != null ? item.description() : "");
            catField.setValue(item.category() != null ? item.category() : "");
            hsnField.setValue(item.hsn() != null ? item.hsn() : "");
            unitField.setValue(item.unit() != null ? item.unit() : "");
            sellField.setValue(item.sellingPrice());
            costField.setValue(item.purchasePrice());
            gstField.setValue(item.gst());
        }

        form.add(codeField, descField, catField, hsnField, unitField, sellField, costField, gstField);
        dialog.add(form);

        Button cancel = new Button("Cancel", e -> dialog.close());
        Button save = new Button("Save Item", e -> {
            if (descField.getValue().isBlank()) {
                Notification.show("Description is required", 2500, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            dialog.close();
            Notification.show("Item saved successfully!", 2500, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshData();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancel, save);
        dialog.open();
    }
}
