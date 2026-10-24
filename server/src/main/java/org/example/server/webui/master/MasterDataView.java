package org.example.server.webui.master;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.master.MasterDataService;
import org.example.server.master.MasterDtos;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "master/lookups", layout = MainAppLayout.class)
@PageTitle("Master Data Lookups | DSE ERP")
@PermitAll
public class MasterDataView extends VerticalLayout {

    private final MasterDataService masterDataService;

    private final Grid<MasterDtos.CategoryDto> categoryGrid = new Grid<>();
    private final Grid<MasterDtos.LookupDto> valueGrid = new Grid<>();
    private final H3 selectedCategoryTitle = new H3("Select a category");

    public MasterDataView(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createSplitLayout();

        loadCategories();
    }

    private void createHeader() {
        VerticalLayout titleBox = new VerticalLayout();
        titleBox.setPadding(false);
        titleBox.setSpacing(false);

        H2 title = new H2("Master Categories & Lookup Values");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");
        Paragraph sub = new Paragraph("Configure system-wide dropdown options, units, payment methods, and brands");
        sub.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, sub);
        add(titleBox);
    }

    private void createSplitLayout() {
        HorizontalLayout split = new HorizontalLayout();
        split.setSizeFull();
        split.getStyle().set("gap", "20px");

        // Left Pane: Categories
        VerticalLayout left = new VerticalLayout();
        left.setWidth("35%");
        left.setHeightFull();
        left.setPadding(false);

        H3 leftTitle = new H3("Categories");
        leftTitle.getStyle().set("font-size", "var(--lumo-font-size-m)").set("margin-bottom", "8px");

        categoryGrid.setSizeFull();
        categoryGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        categoryGrid.addColumn(MasterDtos.CategoryDto::categoryName)
                .setHeader("Category")
                .setFlexGrow(1);

        categoryGrid.addColumn(MasterDtos.CategoryDto::valueCount)
                .setHeader("Values")
                .setAutoWidth(true);

        categoryGrid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                loadValues(e.getValue());
            }
        });

        left.add(leftTitle, categoryGrid);

        // Right Pane: Values
        VerticalLayout right = new VerticalLayout();
        right.setWidth("65%");
        right.setHeightFull();
        right.setPadding(false);

        selectedCategoryTitle.getStyle().set("font-size", "var(--lumo-font-size-m)").set("margin-bottom", "8px");

        valueGrid.setSizeFull();
        valueGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        valueGrid.addColumn(MasterDtos.LookupDto::lookupCode)
                .setHeader("Code")
                .setAutoWidth(true);

        valueGrid.addColumn(MasterDtos.LookupDto::lookupValue)
                .setHeader("Display Value")
                .setFlexGrow(1);

        valueGrid.addColumn(MasterDtos.LookupDto::description)
                .setHeader("Description")
                .setFlexGrow(2);

        valueGrid.addColumn(MasterDtos.LookupDto::displayOrder)
                .setHeader("Order")
                .setAutoWidth(true);

        valueGrid.addComponentColumn(dto -> {
            Span b = new Span(dto.active() ? "ACTIVE" : "DISABLED");
            b.getElement().getThemeList().add("badge small " + (dto.active() ? "success" : "contrast"));
            return b;
        }).setHeader("Status").setAutoWidth(true);

        right.add(selectedCategoryTitle, valueGrid);

        split.add(left, right);
        add(split);
        setFlexGrow(1, split);
    }

    private void loadCategories() {
        try {
            List<MasterDtos.CategoryDto> list = masterDataService.categories();
            categoryGrid.setItems(list);
            if (!list.isEmpty()) {
                categoryGrid.select(list.get(0));
            }
        } catch (Exception ex) {
            Notification.show("Error loading categories: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }

    private void loadValues(MasterDtos.CategoryDto category) {
        selectedCategoryTitle.setText("Lookup Values: " + category.categoryName());
        try {
            List<MasterDtos.LookupDto> values = masterDataService.lookupsByCategoryCode(category.categoryCode());
            valueGrid.setItems(values);
        } catch (Exception ex) {
            Notification.show("Error loading values: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }
}
