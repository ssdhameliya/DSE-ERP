package org.example.server.webui.reporting;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import org.example.server.reporting.ReportingDtos;
import org.example.server.reporting.ReportingService;
import org.example.server.webui.MainAppLayout;

import java.time.LocalDate;
import java.util.List;

@Route(value = "reports/view", layout = MainAppLayout.class)
@PageTitle("Interactive Report Viewer | DSE ERP")
@PermitAll
public class ReportViewerView extends VerticalLayout implements HasUrlParameter<String> {

    private final ReportingService reportingService;

    private String reportId = "SALES_REGISTER";
    private final H3 title = new H3("Report Viewer");
    private final Span description = new Span();

    private final DatePicker fromDate = new DatePicker("From Date", LocalDate.now().withDayOfMonth(1));
    private final DatePicker toDate = new DatePicker("To Date", LocalDate.now());
    private final ComboBox<String> groupByCombo = new ComboBox<>("Group By");
    private final TextField searchField = new TextField("Search Filter");

    private final HorizontalLayout kpiContainer = new HorizontalLayout();
    private final VerticalLayout gridContainer = new VerticalLayout();

    public ReportViewerView(ReportingService reportingService) {
        this.reportingService = reportingService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout topBar = new HorizontalLayout();
        topBar.setWidthFull();
        topBar.setAlignItems(FlexComponent.Alignment.CENTER);

        Button backBtn = new Button("Back to Catalog", VaadinIcon.ARROW_LEFT.create(), e -> {
            getUI().ifPresent(ui -> ui.navigate(ReportsView.class));
        });

        VerticalLayout titles = new VerticalLayout(title, description);
        titles.setPadding(false);
        titles.setSpacing(false);
        description.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "13px");

        Button runBtn = new Button("Run Report", VaadinIcon.PLAY.create(), e -> executeReport());
        runBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        topBar.add(backBtn, titles, runBtn);
        topBar.expand(titles);
        add(topBar);

        // Filter Bar
        HorizontalLayout filterBar = new HorizontalLayout(fromDate, toDate, groupByCombo, searchField);
        filterBar.setAlignItems(FlexComponent.Alignment.BASELINE);
        add(filterBar);

        // KPI container
        kpiContainer.setWidthFull();
        kpiContainer.setSpacing(true);
        add(kpiContainer);

        // Grid Container
        gridContainer.setSizeFull();
        gridContainer.setPadding(false);
        add(gridContainer);
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
        if (parameter != null && !parameter.isBlank()) {
            this.reportId = parameter;
        }
        setupReportDefinition();
        executeReport();
    }

    private void setupReportDefinition() {
        reportingService.definitions().stream()
                .filter(d -> d.id().equalsIgnoreCase(reportId))
                .findFirst()
                .ifPresentOrElse(d -> {
                    title.setText(d.title());
                    description.setText(d.description());
                    groupByCombo.setItems(d.groupByOptions() != null ? d.groupByOptions() : List.of("None"));
                    groupByCombo.setValue(d.groupByOptions() != null && !d.groupByOptions().isEmpty() ? d.groupByOptions().getFirst() : "None");
                }, () -> {
                    title.setText(reportId);
                    description.setText("Standard system report");
                });
    }

    private void executeReport() {
        try {
            LocalDate f = fromDate.getValue() != null ? fromDate.getValue() : LocalDate.now().withDayOfMonth(1);
            LocalDate t = toDate.getValue() != null ? toDate.getValue() : LocalDate.now();

            ReportingDtos.ReportRequest req = new ReportingDtos.ReportRequest(
                    reportId,
                    f.toString(),
                    t.toString(),
                    "", "", "", "", "", "", "", "", "",
                    searchField.getValue(),
                    groupByCombo.getValue(),
                    null, null, null, null,
                    0, 200, null
            );

            ReportingDtos.ReportResult result = reportingService.run(req, "WEB_USER");
            renderReportResult(result);
        } catch (Exception ex) {
            Notification.show("Error running report: " + ex.getMessage(), 4000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void renderReportResult(ReportingDtos.ReportResult result) {
        // Render KPIs
        kpiContainer.removeAll();
        if (result.metrics() != null) {
            for (ReportingDtos.ReportMetric m : result.metrics()) {
                VerticalLayout card = new VerticalLayout();
                card.getStyle()
                        .set("background", "var(--lumo-base-color)")
                        .set("border-radius", "8px")
                        .set("padding", "12px 16px")
                        .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)");
                Span lbl = new Span(m.label());
                lbl.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");
                Span val = new Span(m.format() != null && m.format().contains("₹") ? "₹ " + String.format("%.2f", m.value()) : String.format("%.2f", m.value()));
                val.getStyle().set("font-size", "18px").set("font-weight", "bold");
                card.add(lbl, val);
                kpiContainer.add(card);
            }
        }

        // Render Dynamic Grid
        gridContainer.removeAll();
        Grid<ReportingDtos.ReportRow> grid = new Grid<>();
        grid.setSizeFull();

        List<ReportingDtos.ReportColumn> cols = result.columns();
        for (int i = 0; i < cols.size(); i++) {
            final int colIdx = i;
            ReportingDtos.ReportColumn colDef = cols.get(i);
            grid.addColumn(row -> {
                if (row.values() != null && colIdx < row.values().size()) {
                    return row.values().get(colIdx);
                }
                return "";
            }).setHeader(colDef.label()).setAutoWidth(true).setSortable(true);
        }

        grid.setItems(result.rows() != null ? result.rows() : List.of());
        gridContainer.add(grid);
    }
}
