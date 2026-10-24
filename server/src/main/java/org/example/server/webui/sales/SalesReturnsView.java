package org.example.server.webui.sales;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import org.example.server.returns.ReturnDtos;
import org.example.server.returns.ReturnService;
import org.example.server.webui.MainAppLayout;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Route(value = "sales/returns", layout = MainAppLayout.class)
@PageTitle("Sales Returns Register | DSE ERP")
@PermitAll
public class SalesReturnsView extends VerticalLayout {

    private final ReturnService returnService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final Grid<ReturnDtos.Summary> grid = new Grid<>();
    private final TextField searchField = new TextField();

    public SalesReturnsView(ReturnService returnService) {
        this.returnService = returnService;

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

        H2 title = new H2("Sales Returns Register");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");
        Paragraph sub = new Paragraph("Track customer returns, damaged goods receipts, and settlement credits");
        sub.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, sub);

        Button backBtn = new Button("Back to Sales", new Icon(VaadinIcon.ARROW_LEFT), e -> UI.getCurrent().navigate("sales"));
        backBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        header.add(titleBox, backBtn);
        add(header);
    }

    private void createToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        searchField.setPlaceholder("Search return #, original invoice, customer, reason...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setWidth("360px");
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

        grid.addColumn(ReturnDtos.Summary::no)
                .setHeader("Return #")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(ReturnDtos.Summary::date)
                .setHeader("Return Date")
                .setAutoWidth(true);

        grid.addColumn(ReturnDtos.Summary::invoice)
                .setHeader("Original Invoice #")
                .setAutoWidth(true);

        grid.addColumn(ReturnDtos.Summary::party)
                .setHeader("Customer")
                .setFlexGrow(1);

        grid.addColumn(dto -> currencyFormat.format(dto.total()))
                .setHeader("Return Value")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addColumn(dto -> currencyFormat.format(dto.refund()))
                .setHeader("Credit/Refund")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addColumn(ReturnDtos.Summary::reason)
                .setHeader("Reason")
                .setFlexGrow(1);

        grid.addComponentColumn(dto -> {
            Span badge = new Span(dto.status() != null ? dto.status() : "COMPLETED");
            badge.getElement().getThemeList().add("badge small success");
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        add(grid);
        setFlexGrow(1, grid);
    }

    private void refreshData() {
        try {
            List<ReturnDtos.Summary> list = returnService.summaries("SALES RETURN");
            String q = searchField.getValue();
            if (q != null && !q.isBlank()) {
                String term = q.toLowerCase(Locale.ROOT);
                list = list.stream().filter(s ->
                        (s.no() != null && s.no().toLowerCase().contains(term)) ||
                        (s.invoice() != null && s.invoice().toLowerCase().contains(term)) ||
                        (s.party() != null && s.party().toLowerCase().contains(term)) ||
                        (s.reason() != null && s.reason().toLowerCase().contains(term))
                ).toList();
            }
            grid.setItems(list);
        } catch (Exception ex) {
            Notification.show("Error loading sales returns: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }
}
