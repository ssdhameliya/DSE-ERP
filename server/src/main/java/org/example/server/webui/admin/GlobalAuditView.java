package org.example.server.webui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.audit.AuditDtos;
import org.example.server.audit.AuditService;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "admin/audit", layout = MainAppLayout.class)
@PageTitle("Global Audit Trail | DSE ERP")
@PermitAll
public class GlobalAuditView extends VerticalLayout {

    private final AuditService auditService;
    private final Grid<AuditDtos.EventRow> grid = new Grid<>();

    // KPIs
    private final Span kpiChanges = new Span("0");
    private final Span kpiFinancial = new Span("0");
    private final Span kpiDocs = new Span("0");
    private final Span kpiComms = new Span("0");

    // Filters
    private final ComboBox<String> moduleFilter = new ComboBox<>("Entity Module");
    private final TextField userFilter = new TextField("User");
    private final TextField searchField = new TextField("Search Details / Ref");

    public GlobalAuditView(AuditService auditService) {
        this.auditService = auditService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Enterprise Global Audit Trail & Event Forensics");
        title.getStyle().set("margin", "0");

        Button refreshBtn = new Button("Query Audit Logs", VaadinIcon.SEARCH.create(), e -> refreshGrid());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(title, refreshBtn);
        header.expand(title);
        add(header);

        // KPI Banner
        HorizontalLayout kpis = new HorizontalLayout();
        kpis.setWidthFull();
        kpis.add(createKpiCard("Business Modifications", kpiChanges));
        kpis.add(createKpiCard("Financial & Bank Events", kpiFinancial));
        kpis.add(createKpiCard("Document Operations", kpiDocs));
        kpis.add(createKpiCard("Communications & Outbox", kpiComms));
        add(kpis);

        // Filter Bar
        moduleFilter.setItems("ALL", "SALE", "PURCHASE", "QUOTATION", "SALES_RETURN", "PURCHASE_RETURN", "PARTY", "ITEM", "FINANCE", "USER");
        moduleFilter.setValue("ALL");
        moduleFilter.addValueChangeListener(e -> refreshGrid());

        searchField.setWidth("300px");
        searchField.addValueChangeListener(e -> refreshGrid());

        userFilter.setWidth("200px");
        userFilter.addValueChangeListener(e -> refreshGrid());

        HorizontalLayout filterRow = new HorizontalLayout(moduleFilter, userFilter, searchField);
        filterRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        add(filterRow);

        configureGrid();
        add(grid);
        refreshGrid();
    }

    private VerticalLayout createKpiCard(String label, Span valSpan) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "8px")
                .set("padding", "14px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)");
        Span lbl = new Span(label);
        lbl.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");
        valSpan.getStyle().set("font-size", "20px").set("font-weight", "bold");
        card.add(lbl, valSpan);
        return card;
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(AuditDtos.EventRow::id).setHeader("Event #").setAutoWidth(true).setSortable(true);
        grid.addColumn(AuditDtos.EventRow::createdAt).setHeader("Timestamp").setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(e -> {
            Span b = new Span(e.entityType());
            b.getElement().getThemeList().add("badge");
            return b;
        }).setHeader("Module").setAutoWidth(true);

        grid.addColumn(AuditDtos.EventRow::referenceNo).setHeader("Reference / Voucher").setAutoWidth(true);
        grid.addColumn(AuditDtos.EventRow::action).setHeader("Action").setAutoWidth(true);

        grid.addComponentColumn(e -> {
            Span b = new Span(e.category());
            b.getElement().getThemeList().add("badge");
            if ("FINANCIAL".equalsIgnoreCase(e.category())) b.getElement().getThemeList().add("success");
            else if ("COMMUNICATION".equalsIgnoreCase(e.category())) b.getElement().getThemeList().add("primary");
            else if ("LIFECYCLE".equalsIgnoreCase(e.category())) b.getElement().getThemeList().add("warning");
            else b.getElement().getThemeList().add("contrast");
            return b;
        }).setHeader("Category").setAutoWidth(true);

        grid.addColumn(AuditDtos.EventRow::detail).setHeader("Audit Details").setFlexGrow(1);
        grid.addColumn(AuditDtos.EventRow::createdBy).setHeader("User").setAutoWidth(true);
        grid.addColumn(AuditDtos.EventRow::source).setHeader("Origin").setAutoWidth(true);
    }

    private void refreshGrid() {
        try {
            String mod = moduleFilter.getValue();
            if ("ALL".equalsIgnoreCase(mod)) mod = "";
            AuditDtos.GlobalPage page = auditService.global(0, 100, mod, "", userFilter.getValue(), "", searchField.getValue());
            if (page != null) {
                grid.setItems(page.rows() != null ? page.rows() : List.of());
                kpiChanges.setText(String.valueOf(page.businessChanges()));
                kpiFinancial.setText(String.valueOf(page.financialEvents()));
                kpiDocs.setText(String.valueOf(page.documentEvents()));
                kpiComms.setText(String.valueOf(page.communications()));
            }
        } catch (Exception ex) {
            Notification.show("Error loading audit trail: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
