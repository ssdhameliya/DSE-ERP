package org.example.server.webui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.automation.AutomationDtos;
import org.example.server.automation.SmartAutomationService;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "admin/automation", layout = MainAppLayout.class)
@PageTitle("Smart Automation Center | DSE ERP")
@PermitAll
public class AutomationCenterView extends VerticalLayout {

    private final SmartAutomationService autoService;

    private final Grid<AutomationDtos.RuleDto> rulesGrid = new Grid<>();
    private final Grid<AutomationDtos.ThreeWayMatchDto> matchGrid = new Grid<>();
    private final Grid<AutomationDtos.ReorderSuggestionDto> reorderGrid = new Grid<>();

    public AutomationCenterView(SmartAutomationService autoService) {
        this.autoService = autoService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Smart Automation Center (3-Way Match & Auto-Replenishment)");
        title.getStyle().set("margin", "0");

        Button scanReorderBtn = new Button("Scan Reorder Levels", VaadinIcon.CALC.create(), e -> triggerReorderScan());
        scanReorderBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button refreshBtn = new Button("Refresh", VaadinIcon.REFRESH.create(), e -> refreshAll());

        header.add(title, scanReorderBtn, refreshBtn);
        header.expand(title);
        add(header);

        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        configureRulesGrid();
        configureMatchGrid();
        configureReorderGrid();

        tabSheet.add("Autonomous Background Rules", rulesGrid);
        tabSheet.add("3-Way Purchase Match Logs", matchGrid);
        tabSheet.add("Automated Reorder Suggestions", reorderGrid);

        add(tabSheet);
        refreshAll();
    }

    private void configureRulesGrid() {
        rulesGrid.setSizeFull();
        rulesGrid.addColumn(AutomationDtos.RuleDto::ruleCode).setHeader("Rule Code").setAutoWidth(true).setSortable(true);
        rulesGrid.addColumn(AutomationDtos.RuleDto::ruleName).setHeader("Rule Name").setFlexGrow(1).setSortable(true);
        rulesGrid.addColumn(AutomationDtos.RuleDto::category).setHeader("Category").setAutoWidth(true);

        rulesGrid.addComponentColumn(r -> {
            Checkbox cb = new Checkbox(r.isEnabled());
            cb.addValueChangeListener(e -> {
                try {
                    autoService.toggleRule(r.ruleCode(), e.getValue());
                    Notification.show("Rule " + r.ruleCode() + " " + (e.getValue() ? "Enabled" : "Disabled"), 2000, Notification.Position.TOP_CENTER);
                } catch (Exception ex) {
                    Notification.show("Failed: " + ex.getMessage(), 3000, Notification.Position.MIDDLE);
                }
            });
            return cb;
        }).setHeader("Active").setAutoWidth(true);

        rulesGrid.addColumn(r -> r.lastExecutedAt() != null ? r.lastExecutedAt().toString() : "Never").setHeader("Last Executed").setAutoWidth(true);
        rulesGrid.addColumn(AutomationDtos.RuleDto::executionCount).setHeader("Runs").setAutoWidth(true);
    }

    private void configureMatchGrid() {
        matchGrid.setSizeFull();
        matchGrid.addColumn(AutomationDtos.ThreeWayMatchDto::poNumber).setHeader("PO #").setAutoWidth(true);
        matchGrid.addColumn(AutomationDtos.ThreeWayMatchDto::billNumber).setHeader("Bill #").setAutoWidth(true);
        matchGrid.addColumn(AutomationDtos.ThreeWayMatchDto::supplierName).setHeader("Supplier").setFlexGrow(1);
        matchGrid.addColumn(m -> "₹ " + (m.poAmount() != null ? m.poAmount().toPlainString() : "0.00")).setHeader("PO Amt").setAutoWidth(true);
        matchGrid.addColumn(m -> "₹ " + (m.grnReceivedAmount() != null ? m.grnReceivedAmount().toPlainString() : "0.00")).setHeader("GRN Amt").setAutoWidth(true);
        matchGrid.addColumn(m -> "₹ " + (m.billAmount() != null ? m.billAmount().toPlainString() : "0.00")).setHeader("Bill Amt").setAutoWidth(true);
        matchGrid.addColumn(m -> (m.variancePercentage() != null ? m.variancePercentage().toPlainString() : "0") + "%").setHeader("Variance").setAutoWidth(true);

        matchGrid.addComponentColumn(m -> {
            Span b = new Span(m.matchStatus());
            b.getElement().getThemeList().add("badge");
            if ("MATCHED".equalsIgnoreCase(m.matchStatus())) b.getElement().getThemeList().add("success");
            else b.getElement().getThemeList().add("error");
            return b;
        }).setHeader("Status").setAutoWidth(true);
    }

    private void configureReorderGrid() {
        reorderGrid.setSizeFull();
        reorderGrid.addColumn(AutomationDtos.ReorderSuggestionDto::itemCode).setHeader("Item Code").setAutoWidth(true).setSortable(true);
        reorderGrid.addColumn(AutomationDtos.ReorderSuggestionDto::itemName).setHeader("Item Name").setFlexGrow(1).setSortable(true);
        reorderGrid.addColumn(AutomationDtos.ReorderSuggestionDto::currentStock).setHeader("In Stock").setAutoWidth(true);
        reorderGrid.addColumn(AutomationDtos.ReorderSuggestionDto::reorderLevel).setHeader("Min Level").setAutoWidth(true);
        reorderGrid.addColumn(AutomationDtos.ReorderSuggestionDto::suggestedQty).setHeader("Suggested PO Qty").setAutoWidth(true);
        reorderGrid.addColumn(AutomationDtos.ReorderSuggestionDto::primarySupplierName).setHeader("Recommended Supplier").setAutoWidth(true);
        reorderGrid.addColumn(r -> "₹ " + (r.estimatedCost() != null ? r.estimatedCost().toPlainString() : "0.00")).setHeader("Est. Cost").setAutoWidth(true);

        reorderGrid.addComponentColumn(r -> {
            Span b = new Span(r.status());
            b.getElement().getThemeList().add("badge warning");
            return b;
        }).setHeader("Status").setAutoWidth(true);
    }

    private void refreshAll() {
        try {
            rulesGrid.setItems(autoService.listRules());
            matchGrid.setItems(autoService.listMatchLogs());
            reorderGrid.setItems(autoService.listReorderSuggestions());
        } catch (Exception ex) {
            Notification.show("Error loading automation center: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void triggerReorderScan() {
        try {
            List<AutomationDtos.ReorderSuggestionDto> suggestions = autoService.scanAndGenerateReorderSuggestions();
            Notification.show("Inventory reorder scan completed: " + suggestions.size() + " replenishment recommendation(s) generated!", 3500, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshAll();
        } catch (Exception ex) {
            Notification.show("Failed to scan reorder levels: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
