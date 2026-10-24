package org.example.server.webui.notifications;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.webui.MainAppLayout;
import org.example.server.webui.banking.BankStatementView;
import org.example.server.webui.finance.AgingAnalysisView;
import org.example.server.webui.master.InventoryView;
import org.example.server.webui.sales.SalesReturnsView;

import java.math.BigDecimal;

@Route(value = "notifications", layout = MainAppLayout.class)
@PageTitle("Notification & Alert Center | DSE ERP")
@PermitAll
public class NotificationCenterView extends VerticalLayout {

    private final JpaNativeRepository jdbc;
    private final VerticalLayout alertsContainer = new VerticalLayout();

    public NotificationCenterView(JpaNativeRepository jdbc) {
        this.jdbc = jdbc;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Operational Notifications & Real-Time Alerts");
        title.getStyle().set("margin", "0");

        Button refreshBtn = new Button("Refresh Alerts", VaadinIcon.REFRESH.create(), e -> refreshAlerts());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(title, refreshBtn);
        header.expand(title);
        add(header);

        alertsContainer.setSizeFull();
        alertsContainer.setPadding(false);
        add(alertsContainer);

        refreshAlerts();
    }

    private void refreshAlerts() {
        alertsContainer.removeAll();

        // 1. Overdue Receivables Alert
        try {
            Long overdueCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sales_header
                WHERE (total_amount - COALESCE(paid_amount,0)) > 0
                  AND UPPER(COALESCE(document_status,'')) NOT IN ('DELETED', 'CANCELLED')
                  AND invoice_date < CURRENT_DATE - 30
                """, Long.class);
            if (overdueCount != null && overdueCount > 0) {
                BigDecimal overdueVal = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(total_amount - COALESCE(paid_amount,0)), 0) FROM sales_header
                    WHERE (total_amount - COALESCE(paid_amount,0)) > 0
                      AND UPPER(COALESCE(document_status,'')) NOT IN ('DELETED', 'CANCELLED')
                      AND invoice_date < CURRENT_DATE - 30
                    """, BigDecimal.class);
                alertsContainer.add(createAlertCard(
                        VaadinIcon.EXCLAMATION_CIRCLE,
                        "error",
                        "Overdue Customer Invoices (" + overdueCount + " pending)",
                        "You have " + overdueCount + " customer invoice(s) exceeding 30 days overdue totaling ₹ " + (overdueVal != null ? overdueVal.toPlainString() : "0.00") + ". Take immediate follow-up action.",
                        "Open Aging Analysis",
                        () -> getUI().ifPresent(ui -> ui.navigate(AgingAnalysisView.class))
                ));
            }
        } catch (Exception ignored) {}

        // 2. Low Stock Warning
        try {
            Long lowStock = jdbc.queryForObject("""
                SELECT COUNT(*) FROM item_master
                WHERE COALESCE(opening_stock, 0) <= COALESCE(min_stock_level, 5)
                  AND COALESCE(is_active::text, '1') IN ('1', 'true', 't')
                """, Long.class);
            if (lowStock != null && lowStock > 0) {
                alertsContainer.add(createAlertCard(
                        VaadinIcon.PACKAGE,
                        "warning",
                        "Low Stock Inventory Warning (" + lowStock + " items below reorder level)",
                        lowStock + " item(s) in catalog have fallen to or below their configured minimum threshold. Initiate supplier procurement orders.",
                        "View Inventory Stock",
                        () -> getUI().ifPresent(ui -> ui.navigate(InventoryView.class))
                ));
            }
        } catch (Exception ignored) {}

        // 3. Pending Approval Returns Alert
        try {
            Long pendingReturns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM return_register
                WHERE UPPER(COALESCE(status, 'PENDING APPROVAL')) = 'PENDING APPROVAL'
                """, Long.class);
            if (pendingReturns != null && pendingReturns > 0) {
                alertsContainer.add(createAlertCard(
                        VaadinIcon.ARROW_BACKWARD,
                        "primary",
                        "Pending Returns Awaiting Manager Approval (" + pendingReturns + " requests)",
                        pendingReturns + " return document(s) have been submitted and are pending administrative audit and credit authorization.",
                        "Inspect Sales Returns",
                        () -> getUI().ifPresent(ui -> ui.navigate(SalesReturnsView.class))
                ));
            }
        } catch (Exception ignored) {}

        // 4. Unmatched Bank Statements Alert
        try {
            Long unreconTxs = jdbc.queryForObject("""
                SELECT COUNT(*) FROM bank_statement_transaction
                WHERE UPPER(COALESCE(status, 'UNMATCHED')) IN ('UNMATCHED', 'SUGGESTED')
                """, Long.class);
            if (unreconTxs != null && unreconTxs > 0) {
                alertsContainer.add(createAlertCard(
                        VaadinIcon.RECORDS,
                        "contrast",
                        "Unreconciled Bank Transactions (" + unreconTxs + " items)",
                        unreconTxs + " imported bank statement transaction(s) have not been matched to sales receipts or operating expenses.",
                        "Reconcile Bank Statements",
                        () -> getUI().ifPresent(ui -> ui.navigate(BankStatementView.class))
                ));
            }
        } catch (Exception ignored) {}

        if (alertsContainer.getComponentCount() == 0) {
            VerticalLayout cleanCard = new VerticalLayout();
            cleanCard.setAlignItems(FlexComponent.Alignment.CENTER);
            cleanCard.setPadding(true);
            cleanCard.add(VaadinIcon.CHECK_CIRCLE.create(), new H4("All Clear! No Pending Operational Alerts"));
            cleanCard.getStyle().set("color", "var(--lumo-success-text-color)");
            alertsContainer.add(cleanCard);
        }
    }

    private VerticalLayout createAlertCard(VaadinIcon icon, String themeBadge, String heading, String description, String actionLabel, Runnable action) {
        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "8px")
                .set("padding", "16px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)");

        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);

        var ico = icon.create();
        ico.getStyle().set("font-size", "24px");

        VerticalLayout textCol = new VerticalLayout();
        textCol.setPadding(false);
        textCol.setSpacing(false);

        H4 h = new H4(heading);
        h.getStyle().set("margin", "0");
        Paragraph p = new Paragraph(description);
        p.getStyle().set("margin", "4px 0 0 0").set("color", "var(--lumo-secondary-text-color)");

        textCol.add(h, p);

        Button actBtn = new Button(actionLabel, VaadinIcon.ARROW_RIGHT.create(), e -> action.run());
        actBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        row.add(ico, textCol, actBtn);
        row.expand(textCol);
        card.add(row);
        return card;
    }
}
