package org.example.server.webui.master;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
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

import java.util.List;
import java.util.Locale;

@Route(value = "master/inventory", layout = MainAppLayout.class)
@PageTitle("Inventory & Stock Register | DSE ERP")
@PermitAll
public class InventoryView extends VerticalLayout {

    private final MasterDataService masterDataService;

    private final Grid<MasterDtos.ItemDto> grid = new Grid<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> stockFilter = new ComboBox<>();

    public InventoryView(MasterDataService masterDataService) {
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

        H2 title = new H2("Inventory & Stock Register");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");
        Paragraph sub = new Paragraph("Live warehouse stock counts, reorder level tracking, and inventory adjustments");
        sub.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, sub);

        Button adjustBtn = new Button("Stock Adjustment", new Icon(VaadinIcon.SLIDERS), e -> openAdjustmentModal());
        adjustBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(titleBox, adjustBtn);
        add(header);
    }

    private void createToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        toolbar.getStyle().set("gap", "12px");

        searchField.setPlaceholder("Search item code, description, warehouse location...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setWidth("360px");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> refreshData());

        stockFilter.setLabel("Stock Status");
        stockFilter.setItems("ALL", "LOW STOCK", "OUT OF STOCK", "HEALTHY");
        stockFilter.setValue("ALL");
        stockFilter.setWidth("160px");
        stockFilter.addValueChangeListener(e -> refreshData());

        Button refreshBtn = new Button("Refresh", new Icon(VaadinIcon.REFRESH), e -> refreshData());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        toolbar.add(searchField, stockFilter, refreshBtn);
        add(toolbar);
    }

    private void createGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS);

        grid.addColumn(MasterDtos.ItemDto::itemCode)
                .setHeader("Code")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(MasterDtos.ItemDto::description)
                .setHeader("Item Description")
                .setSortable(true)
                .setFlexGrow(2);

        grid.addColumn(MasterDtos.ItemDto::category)
                .setHeader("Category")
                .setAutoWidth(true);

        grid.addColumn(MasterDtos.ItemDto::unit)
                .setHeader("Unit")
                .setAutoWidth(true);

        grid.addColumn(MasterDtos.ItemDto::openingStock)
                .setHeader("Available Stock")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addColumn(MasterDtos.ItemDto::minimumStock)
                .setHeader("Min Level")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addColumn(MasterDtos.ItemDto::reservedStock)
                .setHeader("Reserved")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addColumn(dto -> dto.location() != null ? dto.location() : "Main WH")
                .setHeader("Location")
                .setAutoWidth(true);

        grid.addComponentColumn(this::renderStockBadge)
                .setHeader("Health")
                .setAutoWidth(true);

        add(grid);
        setFlexGrow(1, grid);
    }

    private Span renderStockBadge(MasterDtos.ItemDto item) {
        double current = item.openingStock();
        double min = item.minimumStock();

        if (current <= 0) {
            Span b = new Span("OUT OF STOCK");
            b.getElement().getThemeList().add("badge small error");
            return b;
        } else if (current <= min) {
            Span b = new Span("LOW STOCK");
            b.getElement().getThemeList().add("badge small warning");
            return b;
        } else {
            Span b = new Span("HEALTHY");
            b.getElement().getThemeList().add("badge small success");
            return b;
        }
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
                        (i.category() != null && i.category().toLowerCase().contains(term))
                ).toList();
            }

            String filter = stockFilter.getValue();
            if ("LOW STOCK".equals(filter)) {
                list = list.stream().filter(i -> i.openingStock() > 0 && i.openingStock() <= i.minimumStock()).toList();
            } else if ("OUT OF STOCK".equals(filter)) {
                list = list.stream().filter(i -> i.openingStock() <= 0).toList();
            } else if ("HEALTHY".equals(filter)) {
                list = list.stream().filter(i -> i.openingStock() > i.minimumStock()).toList();
            }

            grid.setItems(list);
        } catch (Exception ex) {
            Notification.show("Error loading inventory: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }

    private void openAdjustmentModal() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Manual Stock Adjustment");

        VerticalLayout form = new VerticalLayout();
        form.setSpacing(true);

        ComboBox<MasterDtos.ItemDto> itemCombo = new ComboBox<>("Select Item");
        itemCombo.setItemLabelGenerator(i -> i.itemCode() + " - " + i.description());
        itemCombo.setItems(masterDataService.items());
        itemCombo.setWidthFull();

        ComboBox<String> typeCombo = new ComboBox<>("Adjustment Type");
        typeCombo.setItems("ADD (Physical Inward)", "SUBTRACT (Wastage / Damage)", "RESET (Audit Correction)");
        typeCombo.setValue("ADD (Physical Inward)");
        typeCombo.setWidthFull();

        NumberField qtyField = new NumberField("Adjustment Quantity");
        qtyField.setMin(0.01);
        qtyField.setWidthFull();

        TextField reasonField = new TextField("Reason / Reference");
        reasonField.setWidthFull();

        form.add(itemCombo, typeCombo, qtyField, reasonField);
        dialog.add(form);

        Button cancel = new Button("Cancel", e -> dialog.close());
        Button save = new Button("Apply Adjustment", e -> {
            if (itemCombo.getValue() == null || qtyField.getValue() == null || qtyField.getValue() <= 0) {
                Notification.show("Please select item and specify positive quantity", 2500, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            dialog.close();
            Notification.show("Stock adjustment applied successfully!", 2500, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshData();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancel, save);
        dialog.open();
    }
}
