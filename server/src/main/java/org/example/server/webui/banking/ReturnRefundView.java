package org.example.server.webui.banking;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.returns.ReturnDtos;
import org.example.server.returns.ReturnService;
import org.example.server.webui.MainAppLayout;

import java.time.LocalDate;
import java.util.List;

@Route(value = "banking/refunds", layout = MainAppLayout.class)
@PageTitle("Return Refunds & Credit Settlements | DSE ERP")
@PermitAll
public class ReturnRefundView extends VerticalLayout {

    private final ReturnService returnService;
    private final Grid<ReturnDtos.Summary> returnsGrid = new Grid<>();
    private final TextField searchField = new TextField();
    private String currentType = "SALE"; // "SALE" or "PURCHASE"

    private final Span totalReturnsKpi = new Span("₹ 0.00");
    private final Span approvedAmountKpi = new Span("₹ 0.00");
    private final Span refundSettledKpi = new Span("₹ 0.00");

    public ReturnRefundView(ReturnService returnService) {
        this.returnService = returnService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Return Refunds & Settlement Ledger");
        title.getStyle().set("margin", "0");

        Button recordRefundBtn = new Button("Record Direct Refund", VaadinIcon.MONEY.create(), e -> openRecordRefundDialog());
        recordRefundBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button quickApplyBtn = new Button("Quick Apply Credit Note", VaadinIcon.CHECK_CIRCLE.create(), e -> handleQuickApply());
        quickApplyBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        header.add(title, quickApplyBtn, recordRefundBtn);
        header.expand(title);
        add(header);

        // KPI Banner
        HorizontalLayout kpis = new HorizontalLayout();
        kpis.setWidthFull();
        kpis.add(createKpiCard("Total Returns Volume", totalReturnsKpi));
        kpis.add(createKpiCard("Approved Return Value", approvedAmountKpi));
        kpis.add(createKpiCard("Settled Refund Value", refundSettledKpi));
        add(kpis);

        // Tabs: Sales Return Refunds vs Purchase Return Refunds
        Tab salesTab = new Tab("Customer Sales Return Refunds");
        Tab purchaseTab = new Tab("Supplier Purchase Return Refunds");
        Tabs tabs = new Tabs(salesTab, purchaseTab);
        tabs.setWidthFull();
        tabs.addSelectedChangeListener(e -> {
            currentType = e.getSelectedTab().equals(salesTab) ? "SALE" : "PURCHASE";
            refreshGrid();
        });

        // Search & Refresh
        searchField.setPlaceholder("Search Return No, Invoice, or Party...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidth("350px");
        searchField.addValueChangeListener(e -> refreshGrid());

        Button refreshBtn = new Button("Refresh", VaadinIcon.REFRESH.create(), e -> refreshGrid());
        HorizontalLayout filterRow = new HorizontalLayout(tabs, searchField, refreshBtn);
        filterRow.setWidthFull();
        filterRow.setAlignItems(FlexComponent.Alignment.CENTER);
        filterRow.expand(tabs);
        add(filterRow);

        configureGrid();
        add(returnsGrid);
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
        returnsGrid.setSizeFull();
        returnsGrid.addColumn(ReturnDtos.Summary::no).setHeader("Return #").setAutoWidth(true).setSortable(true);
        returnsGrid.addColumn(ReturnDtos.Summary::date).setHeader("Date").setAutoWidth(true).setSortable(true);
        returnsGrid.addColumn(ReturnDtos.Summary::invoice).setHeader("Original Invoice").setAutoWidth(true);
        returnsGrid.addColumn(ReturnDtos.Summary::party).setHeader("Party Name").setFlexGrow(1).setSortable(true);
        returnsGrid.addColumn(r -> "₹ " + String.format("%.2f", r.total())).setHeader("Return Amount").setAutoWidth(true);
        returnsGrid.addColumn(r -> "₹ " + String.format("%.2f", r.refund())).setHeader("Refunded").setAutoWidth(true);

        returnsGrid.addComponentColumn(r -> {
            Span s = new Span(r.status());
            s.getElement().getThemeList().add("badge");
            if ("APPROVED".equalsIgnoreCase(r.status())) s.getElement().getThemeList().add("success");
            else if ("REJECTED".equalsIgnoreCase(r.status())) s.getElement().getThemeList().add("error");
            else s.getElement().getThemeList().add("warning");
            return s;
        }).setHeader("Approval").setAutoWidth(true);

        returnsGrid.addComponentColumn(r -> {
            Span s = new Span(r.refundStatus() != null ? r.refundStatus() : "PENDING");
            s.getElement().getThemeList().add("badge");
            if ("SETTLED".equalsIgnoreCase(r.refundStatus()) || "REFUNDED".equalsIgnoreCase(r.refundStatus())) {
                s.getElement().getThemeList().add("success");
            } else if ("PARTIAL".equalsIgnoreCase(r.refundStatus())) {
                s.getElement().getThemeList().add("warning");
            } else {
                s.getElement().getThemeList().add("contrast");
            }
            return s;
        }).setHeader("Refund Status").setAutoWidth(true);
    }

    private void refreshGrid() {
        try {
            ReturnDtos.Page page = returnService.page(currentType, 0, 100, searchField.getValue(), "", "", "", "");
            if (page != null) {
                returnsGrid.setItems(page.rows() != null ? page.rows() : List.of());
                if (page.metrics() != null) {
                    totalReturnsKpi.setText("₹ " + String.format("%.2f", page.metrics().total()));
                    approvedAmountKpi.setText("₹ " + String.format("%.2f", page.metrics().approvedAmount()));
                    refundSettledKpi.setText("₹ " + String.format("%.2f", page.metrics().refundAmount()));
                }
            }
        } catch (Exception ex) {
            Notification.show("Error loading returns: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void handleQuickApply() {
        ReturnDtos.Summary sel = returnsGrid.asSingleSelect().getValue();
        if (sel == null) {
            Notification.show("Select an approved return to quick-apply credit note", 3000, Notification.Position.MIDDLE);
            return;
        }
        try {
            int count = returnService.applyCreditNoteToOutstandingInvoices(sel.no());
            Notification.show("Credit note applied successfully to " + count + " outstanding invoice(s)!", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshGrid();
        } catch (Exception ex) {
            Notification.show("Failed: " + ex.getMessage(), 4000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openRecordRefundDialog() {
        ReturnDtos.Summary sel = returnsGrid.asSingleSelect().getValue();
        if (sel == null) {
            Notification.show("Please select a return record first", 3000, Notification.Position.MIDDLE);
            return;
        }

        Dialog d = new Dialog();
        d.setHeaderTitle("Record Direct Refund / Bank Settlement for " + sel.no());

        DatePicker datePicker = new DatePicker("Refund Date", LocalDate.now());
        NumberField amountField = new NumberField("Refund Amount (₹)");
        double pendingRefund = Math.max(0, sel.total() - sel.refund());
        amountField.setValue(pendingRefund);

        ComboBox<String> mode = new ComboBox<>("Payment Mode");
        mode.setItems("BANK_TRANSFER", "NEFT/RTGS", "UPI", "CHEQUE", "CASH");
        mode.setValue("BANK_TRANSFER");

        TextField bankAccount = new TextField("Bank Account");
        bankAccount.setValue("HDFC Primary");

        TextField ref = new TextField("UTR / Reference #");
        TextField notes = new TextField("Notes");
        notes.setWidthFull();

        VerticalLayout form = new VerticalLayout(
                new HorizontalLayout(datePicker, mode),
                new HorizontalLayout(amountField, bankAccount),
                ref, notes
        );
        form.setPadding(false);
        d.add(form);

        Button save = new Button("Record Refund", e -> {
            if (amountField.getValue() == null || amountField.getValue() <= 0) {
                Notification.show("Enter a valid refund amount", 3000, Notification.Position.MIDDLE);
                return;
            }
            try {
                returnService.recordRefund(sel.no(), new ReturnDtos.RefundCreateRequest(
                        datePicker.getValue().toString(),
                        amountField.getValue(),
                        mode.getValue(),
                        ref.getValue(),
                        bankAccount.getValue(),
                        sel.party(),
                        notes.getValue(),
                        "DIRECT_REFUND",
                        "WEB_USER"
                ));
                Notification.show("Refund recorded successfully!", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                d.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> d.close());

        d.getFooter().add(cancel, save);
        d.open();
    }
}
