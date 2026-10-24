package org.example.server.webui.quotation;

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
import org.example.server.quotation.QuotationDtos;
import org.example.server.quotation.QuotationService;
import org.example.server.webui.MainAppLayout;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Route(value = "quotations", layout = MainAppLayout.class)
@PageTitle("Quotations Register | DSE ERP")
@PermitAll
public class QuotationsView extends VerticalLayout {

    private final QuotationService quotationService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final Grid<QuotationDtos.QuoteDto> grid = new Grid<>();
    private final TextField searchField = new TextField();

    public QuotationsView(QuotationService quotationService) {
        this.quotationService = quotationService;

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

        H2 title = new H2("Quotations Register");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");
        Paragraph sub = new Paragraph("Prepare client estimates, commercial proposals, and convert to Tax Invoices");
        sub.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, sub);

        Button newBtn = new Button("New Quotation", new Icon(VaadinIcon.PLUS_CIRCLE), e -> UI.getCurrent().navigate("quotations/edit"));
        newBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(titleBox, newBtn);
        add(header);
    }

    private void createToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        searchField.setPlaceholder("Search quotation #, customer, salesperson...");
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

        grid.addColumn(QuotationDtos.QuoteDto::no)
                .setHeader("Quote #")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(QuotationDtos.QuoteDto::date)
                .setHeader("Date")
                .setAutoWidth(true);

        grid.addColumn(QuotationDtos.QuoteDto::customer)
                .setHeader("Customer")
                .setFlexGrow(1);

        grid.addColumn(QuotationDtos.QuoteDto::valid)
                .setHeader("Valid Until")
                .setAutoWidth(true);

        grid.addColumn(dto -> currencyFormat.format(dto.amount()))
                .setHeader("Estimate Total")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addComponentColumn(dto -> {
            String status = dto.status() != null ? dto.status().toUpperCase() : "DRAFT";
            Span badge = new Span(status);
            badge.getElement().getThemeList().add("badge small");
            if ("ACCEPTED".equals(status)) badge.getElement().getThemeList().add("success");
            else if ("CONVERTED".equals(status)) badge.getElement().getThemeList().add("primary");
            else if ("EXPIRED".equals(status)) badge.getElement().getThemeList().add("error");
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        grid.addComponentColumn(dto -> {
            HorizontalLayout actions = new HorizontalLayout();
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> UI.getCurrent().navigate("quotations/edit?id=" + dto.id()));
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button printBtn = new Button(new Icon(VaadinIcon.PRINT), e -> {
                UI.getCurrent().getPage().open("/api/documents/render?type=QUOTATION&number=" + dto.no() + "&format=PDF", "_blank");
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
            List<QuotationDtos.QuoteDto> list = quotationService.list();
            String q = searchField.getValue();
            if (q != null && !q.isBlank()) {
                String term = q.toLowerCase(Locale.ROOT);
                list = list.stream().filter(quote ->
                        (quote.no() != null && quote.no().toLowerCase().contains(term)) ||
                        (quote.customer() != null && quote.customer().toLowerCase().contains(term)) ||
                        (quote.salesperson() != null && quote.salesperson().toLowerCase().contains(term))
                ).toList();
            }
            grid.setItems(list);
        } catch (Exception ex) {
            Notification.show("Error loading quotations: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }
}
