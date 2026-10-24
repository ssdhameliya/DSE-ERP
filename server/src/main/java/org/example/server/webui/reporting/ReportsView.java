package org.example.server.webui.reporting;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.reporting.ReportingDtos;
import org.example.server.reporting.ReportingService;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "reports", layout = MainAppLayout.class)
@PageTitle("Reports Catalog | DSE ERP")
@PermitAll
public class ReportsView extends VerticalLayout {

    private final ReportingService reportingService;
    private final Grid<ReportingDtos.ReportDefinition> grid = new Grid<>();
    private final TextField searchField = new TextField();

    public ReportsView(ReportingService reportingService) {
        this.reportingService = reportingService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Enterprise Business Intelligence & Reports Catalog");
        title.getStyle().set("margin", "0");

        searchField.setPlaceholder("Search report name, category or description...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidth("350px");
        searchField.addValueChangeListener(e -> filterGrid());

        header.add(title, searchField);
        header.expand(title);
        add(header);

        configureGrid();
        add(grid);
        filterGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();

        grid.addComponentColumn(r -> {
            Span s = new Span(r.category());
            s.getElement().getThemeList().add("badge");
            if ("Sales".equalsIgnoreCase(r.category())) s.getElement().getThemeList().add("primary");
            else if ("Purchase".equalsIgnoreCase(r.category())) s.getElement().getThemeList().add("warning");
            else if ("GST / Tax".equalsIgnoreCase(r.category())) s.getElement().getThemeList().add("success");
            else if ("Inventory".equalsIgnoreCase(r.category())) s.getElement().getThemeList().add("contrast");
            return s;
        }).setHeader("Category").setAutoWidth(true);

        grid.addColumn(ReportingDtos.ReportDefinition::title).setHeader("Report Title").setAutoWidth(true).setSortable(true);
        grid.addColumn(ReportingDtos.ReportDefinition::description).setHeader("Description").setFlexGrow(1);

        grid.addComponentColumn(r -> {
            Button openBtn = new Button("Run Report", VaadinIcon.ARROW_RIGHT.create(), e -> {
                getUI().ifPresent(ui -> ui.navigate(ReportViewerView.class, r.id()));
            });
            openBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            return openBtn;
        }).setHeader("Action").setAutoWidth(true);
    }

    private void filterGrid() {
        List<ReportingDtos.ReportDefinition> defs = reportingService.definitions();
        String q = searchField.getValue() != null ? searchField.getValue().trim().toLowerCase() : "";
        if (q.isBlank()) {
            grid.setItems(defs);
        } else {
            grid.setItems(defs.stream().filter(d ->
                    d.title().toLowerCase().contains(q) ||
                    d.category().toLowerCase().contains(q) ||
                    d.description().toLowerCase().contains(q)
            ).toList());
        }
    }
}
