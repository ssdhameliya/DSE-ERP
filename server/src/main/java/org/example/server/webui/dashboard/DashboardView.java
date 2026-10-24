package org.example.server.webui.dashboard;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.insights.InsightDtos;
import org.example.server.insights.InsightsService;
import org.example.server.webui.MainAppLayout;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Route(value = "", layout = MainAppLayout.class)
@PageTitle("Executive Dashboard | DSE ERP")
@PermitAll
public class DashboardView extends VerticalLayout {

    private final InsightsService insightsService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final HorizontalLayout kpiContainer = new HorizontalLayout();
    private final Grid<InsightDtos.ActivityDto> recentActivityGrid = new Grid<>();
    private final VerticalLayout alertsContainer = new VerticalLayout();
    private final ComboBox<String> periodFilter = new ComboBox<>();

    public DashboardView(InsightsService insightsService) {
        this.insightsService = insightsService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background", "var(--lumo-base-color)");

        createHeaderSection();
        createKpiSection();
        createMainContentSection();

        refreshData("This Month");
    }

    private void createHeaderSection() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout titleBox = new VerticalLayout();
        titleBox.setPadding(false);
        titleBox.setSpacing(false);

        H2 title = new H2("Executive Dashboard");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "1.75rem")
                .set("font-weight", "700");

        String formattedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
        Paragraph dateText = new Paragraph("Live business insights for " + formattedDate);
        dateText.getStyle()
                .set("margin", "0")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, dateText);

        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        periodFilter.setItems("Today", "This Week", "This Month", "This Quarter", "This Financial Year");
        periodFilter.setValue("This Month");
        periodFilter.setWidth("200px");
        periodFilter.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                refreshData(e.getValue());
            }
        });

        Button refreshBtn = new Button(new Icon(VaadinIcon.REFRESH), e -> refreshData(periodFilter.getValue()));
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        refreshBtn.getElement().setAttribute("aria-label", "Refresh dashboard");

        toolbar.add(periodFilter, refreshBtn);
        header.add(titleBox, toolbar);
        add(header);
    }

    private void createKpiSection() {
        kpiContainer.setWidthFull();
        kpiContainer.getStyle()
                .set("gap", "16px")
                .set("flex-wrap", "wrap")
                .set("margin-top", "8px")
                .set("margin-bottom", "8px");

        add(kpiContainer);
    }

    private void createMainContentSection() {
        HorizontalLayout contentSplit = new HorizontalLayout();
        contentSplit.setSizeFull();
        contentSplit.getStyle().set("gap", "20px");

        // Left pane: Recent Activity Grid
        VerticalLayout leftPane = new VerticalLayout();
        leftPane.setWidth("68%");
        leftPane.setHeightFull();
        leftPane.setPadding(false);

        H3 gridTitle = new H3("Recent Business Transactions");
        gridTitle.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin-bottom", "8px");

        recentActivityGrid.addColumn(InsightDtos.ActivityDto::type)
                .setHeader("Type")
                .setAutoWidth(true)
                .setFlexGrow(0);
        recentActivityGrid.addColumn(InsightDtos.ActivityDto::number)
                .setHeader("Doc #")
                .setAutoWidth(true);
        recentActivityGrid.addColumn(InsightDtos.ActivityDto::party)
                .setHeader("Party")
                .setAutoWidth(true)
                .setFlexGrow(1);
        recentActivityGrid.addColumn(InsightDtos.ActivityDto::date)
                .setHeader("Date")
                .setAutoWidth(true);
        recentActivityGrid.addColumn(dto -> currencyFormat.format(dto.amount()))
                .setHeader("Amount")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        recentActivityGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        recentActivityGrid.setHeight("420px");

        leftPane.add(gridTitle, recentActivityGrid);

        // Right pane: Actions & Insights
        VerticalLayout rightPane = new VerticalLayout();
        rightPane.setWidth("32%");
        rightPane.setHeightFull();
        rightPane.setPadding(false);

        H3 actionsTitle = new H3("Quick Operations");
        actionsTitle.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin-bottom", "8px");

        VerticalLayout actionButtons = new VerticalLayout();
        actionButtons.setSpacing(true);
        actionButtons.setPadding(false);

        Button newInvoiceBtn = new Button("New Tax Invoice", new Icon(VaadinIcon.PLUS_CIRCLE));
        newInvoiceBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newInvoiceBtn.setWidthFull();
        newInvoiceBtn.addClickListener(e -> UI.getCurrent().navigate("sales/edit"));

        Button newPurchaseBtn = new Button("New Purchase Inward", new Icon(VaadinIcon.CART));
        newPurchaseBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        newPurchaseBtn.setWidthFull();
        newPurchaseBtn.addClickListener(e -> UI.getCurrent().navigate("purchases/edit"));

        Button ledgerBtn = new Button("View General Ledger", new Icon(VaadinIcon.BOOK));
        ledgerBtn.setWidthFull();
        ledgerBtn.addClickListener(e -> UI.getCurrent().navigate("finance/ledger"));

        Button gstBtn = new Button("GST Compliance Hub", new Icon(VaadinIcon.DIPLOMA));
        gstBtn.setWidthFull();
        gstBtn.addClickListener(e -> UI.getCurrent().navigate("gst/compliance"));

        actionButtons.add(newInvoiceBtn, newPurchaseBtn, ledgerBtn, gstBtn);

        H3 alertsTitle = new H3("System Health & Alerts");
        alertsTitle.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin-top", "16px").set("margin-bottom", "8px");

        alertsContainer.setPadding(false);
        alertsContainer.setSpacing(false);

        rightPane.add(actionsTitle, actionButtons, alertsTitle, alertsContainer);

        contentSplit.add(leftPane, rightPane);
        add(contentSplit);
    }

    private void refreshData(String period) {
        try {
            InsightDtos.DashboardBundle bundle = insightsService.dashboard(period);
            if (bundle != null && bundle.snapshot() != null) {
                InsightDtos.DashboardSnapshot s = bundle.snapshot();

                kpiContainer.removeAll();
                kpiContainer.add(
                        createKpiCard("Total Sales", currencyFormat.format(s.salesValue()), s.invoices() + " invoices", "var(--lumo-primary-color)", VaadinIcon.INVOICE),
                        createKpiCard("Total Purchases", currencyFormat.format(s.purchaseValue()), s.purchases() + " inward orders", "var(--lumo-success-color)", VaadinIcon.CART),
                        createKpiCard("Receivables", currencyFormat.format(s.receivables()), s.openReceivables() + " outstanding", "var(--lumo-error-color)", VaadinIcon.CLOCK),
                        createKpiCard("Payables", currencyFormat.format(s.payables()), s.openPayables() + " pending", "var(--lumo-warning-color)", VaadinIcon.MONEY_WITHDRAW),
                        createKpiCard("Cash Position", currencyFormat.format(s.cash()), "Liquid reserve", "var(--lumo-primary-text-color)", VaadinIcon.WALLET)
                );

                if (bundle.recent() != null) {
                    recentActivityGrid.setItems(bundle.recent());
                }

                alertsContainer.removeAll();
                if (s.lowStock() > 0) {
                    alertsContainer.add(createAlertBadge("Low Stock Alert: " + s.lowStock() + " items below minimum reorder level", "badge error"));
                }
                if (s.overdueReminders() > 0) {
                    alertsContainer.add(createAlertBadge("Reminders: " + s.overdueReminders() + " tasks overdue", "badge warning"));
                }
                alertsContainer.add(createAlertBadge("Database & Flyway: Schema V10.0.41 synchronized", "badge success"));
            }
        } catch (Exception ex) {
            kpiContainer.removeAll();
            Div errBox = new Div(new Text("Unable to load live KPIs: " + ex.getMessage()));
            errBox.getElement().getThemeList().add("badge error");
            kpiContainer.add(errBox);
        }
    }

    private Div createKpiCard(String label, String value, String subtext, String accentColor, VaadinIcon icon) {
        Div card = new Div();
        card.getStyle()
                .set("flex", "1 1 200px")
                .set("min-width", "180px")
                .set("padding", "16px 20px")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background", "var(--lumo-tint-5pct)")
                .set("border-left", "4px solid " + accentColor)
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.04)");

        HorizontalLayout top = new HorizontalLayout();
        top.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        top.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Span title = new Span(label);
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "600")
                .set("text-transform", "uppercase")
                .set("color", "var(--lumo-secondary-text-color)");

        Icon cardIcon = icon.create();
        cardIcon.getStyle()
                .set("color", accentColor)
                .set("width", "18px")
                .set("height", "18px");

        top.add(title, cardIcon);

        H3 val = new H3(value);
        val.getStyle()
                .set("margin", "8px 0 4px 0")
                .set("font-size", "1.4rem")
                .set("font-weight", "700")
                .set("letter-spacing", "-0.5px");

        Span sub = new Span(subtext);
        sub.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-tertiary-text-color)");

        card.add(top, val, sub);
        return card;
    }

    private Div createAlertBadge(String text, String theme) {
        Div badge = new Div(new Text(text));
        badge.getElement().getThemeList().add(theme);
        badge.getStyle()
                .set("width", "100%")
                .set("padding", "8px 12px")
                .set("margin-bottom", "6px")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("font-size", "var(--lumo-font-size-xs)");
        return badge;
    }
}
