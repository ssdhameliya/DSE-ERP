package org.example.server.webui.banking;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.reconciliation.BankReconciliationDtos;
import org.example.server.reconciliation.BankReconciliationService;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "banking/reconciliation", layout = MainAppLayout.class)
@PageTitle("Bank Statement Reconciliation | DSE ERP")
@PermitAll
public class BankStatementView extends VerticalLayout {

    private final BankReconciliationService reconService;

    private final Grid<BankReconciliationDtos.BatchDto> batchGrid = new Grid<>();
    private final Grid<BankReconciliationDtos.TransactionDto> txGrid = new Grid<>();

    private final Span selectedBatchTitle = new Span("Select a statement batch from the left to view and reconcile transactions");
    private BankReconciliationDtos.BatchDto selectedBatch;

    public BankStatementView(BankReconciliationService reconService) {
        this.reconService = reconService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Bank Statement Import & Automated Reconciliation");
        title.getStyle().set("margin", "0");

        Button refreshBtn = new Button("Refresh Batches", VaadinIcon.REFRESH.create(), e -> refreshBatches());
        header.add(title, refreshBtn);
        header.expand(title);
        add(header);

        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(35);

        // Left Panel: Batches
        VerticalLayout leftPanel = new VerticalLayout();
        leftPanel.setSizeFull();
        leftPanel.setPadding(false);

        Span batchHeader = new Span("Statement Import Batches");
        batchHeader.getStyle().set("font-weight", "bold").set("color", "var(--lumo-secondary-text-color)");

        configureBatchGrid();
        leftPanel.add(batchHeader, batchGrid);
        splitLayout.addToPrimary(leftPanel);

        // Right Panel: Transactions
        VerticalLayout rightPanel = new VerticalLayout();
        rightPanel.setSizeFull();
        rightPanel.setPadding(false);

        HorizontalLayout rightHeader = new HorizontalLayout();
        rightHeader.setWidthFull();
        rightHeader.setAlignItems(FlexComponent.Alignment.CENTER);

        selectedBatchTitle.getStyle().set("font-weight", "600");

        Button ignoreBtn = new Button("Ignore", VaadinIcon.BAN.create(), e -> handleIgnoreTransaction());
        Button expenseBtn = new Button("Mark as Expense", VaadinIcon.MONEY_WITHDRAW.create(), e -> handleExpenseTransaction());
        expenseBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        rightHeader.add(selectedBatchTitle, ignoreBtn, expenseBtn);
        rightHeader.expand(selectedBatchTitle);

        configureTxGrid();
        rightPanel.add(rightHeader, txGrid);
        splitLayout.addToSecondary(rightPanel);

        add(splitLayout);
        refreshBatches();
    }

    private void configureBatchGrid() {
        batchGrid.setSizeFull();
        batchGrid.addColumn(BankReconciliationDtos.BatchDto::bankName).setHeader("Bank").setAutoWidth(true);
        batchGrid.addColumn(BankReconciliationDtos.BatchDto::bankAccount).setHeader("Account").setAutoWidth(true);
        batchGrid.addColumn(b -> b.statementFrom() + " to " + b.statementTo()).setHeader("Period").setAutoWidth(true);
        batchGrid.addColumn(b -> b.transactionCount() + " txs").setHeader("Total").setAutoWidth(true);
        batchGrid.addColumn(b -> String.format("%.0f%%", b.reconciliationPercent())).setHeader("Reconciled").setAutoWidth(true);

        batchGrid.addComponentColumn(b -> {
            Span s = new Span(b.status());
            s.getElement().getThemeList().add("badge");
            if ("RECONCILED".equalsIgnoreCase(b.status())) s.getElement().getThemeList().add("success");
            else s.getElement().getThemeList().add("warning");
            return s;
        }).setHeader("Status").setAutoWidth(true);

        batchGrid.asSingleSelect().addValueChangeListener(e -> {
            selectedBatch = e.getValue();
            if (selectedBatch != null) {
                selectedBatchTitle.setText("Transactions: " + selectedBatch.bankName() + " (" + selectedBatch.bankAccount() + ") - " + selectedBatch.statementFrom() + " to " + selectedBatch.statementTo());
                refreshTransactions(selectedBatch.id());
            } else {
                selectedBatchTitle.setText("Select a statement batch from the left");
                txGrid.setItems(List.of());
            }
        });
    }

    private void configureTxGrid() {
        txGrid.setSizeFull();
        txGrid.addColumn(BankReconciliationDtos.TransactionDto::transactionDate).setHeader("Date").setAutoWidth(true).setSortable(true);
        txGrid.addColumn(BankReconciliationDtos.TransactionDto::description).setHeader("Description").setFlexGrow(1);
        txGrid.addColumn(BankReconciliationDtos.TransactionDto::reference).setHeader("Ref / UTR").setAutoWidth(true);
        txGrid.addColumn(t -> t.debit() > 0 ? "₹ " + String.format("%.2f", t.debit()) : "-").setHeader("Debit (Withdrawal)").setAutoWidth(true);
        txGrid.addColumn(t -> t.credit() > 0 ? "₹ " + String.format("%.2f", t.credit()) : "-").setHeader("Credit (Deposit)").setAutoWidth(true);
        txGrid.addColumn(t -> "₹ " + String.format("%.2f", t.balance())).setHeader("Balance").setAutoWidth(true);

        txGrid.addComponentColumn(t -> {
            Span s = new Span(t.status());
            s.getElement().getThemeList().add("badge");
            if ("MATCHED".equalsIgnoreCase(t.status())) s.getElement().getThemeList().add("success");
            else if ("EXPENSE".equalsIgnoreCase(t.status())) s.getElement().getThemeList().add("primary");
            else if ("IGNORED".equalsIgnoreCase(t.status())) s.getElement().getThemeList().add("contrast");
            else s.getElement().getThemeList().add("warning");
            return s;
        }).setHeader("Status").setAutoWidth(true);

        txGrid.addColumn(BankReconciliationDtos.TransactionDto::suggestedMatchType).setHeader("Match Suggestion").setAutoWidth(true);
    }

    private void refreshBatches() {
        try {
            List<BankReconciliationDtos.BatchDto> batches = reconService.batches();
            batchGrid.setItems(batches);
            if (!batches.isEmpty() && selectedBatch == null) {
                batchGrid.select(batches.getFirst());
            }
        } catch (Exception ex) {
            Notification.show("Error loading statement batches: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void refreshTransactions(Long batchId) {
        try {
            List<BankReconciliationDtos.TransactionDto> txs = reconService.transactions(batchId);
            txGrid.setItems(txs);
        } catch (Exception ex) {
            Notification.show("Error loading transactions: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void handleIgnoreTransaction() {
        BankReconciliationDtos.TransactionDto sel = txGrid.asSingleSelect().getValue();
        if (sel == null) {
            Notification.show("Select a transaction to ignore", 2500, Notification.Position.MIDDLE);
            return;
        }
        try {
            reconService.ignore(sel.id(), new BankReconciliationDtos.IgnoreRequest("Ignored from Web UI", "WEB_USER"));
            Notification.show("Transaction marked as ignored", 2500, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            if (selectedBatch != null) refreshTransactions(selectedBatch.id());
        } catch (Exception ex) {
            Notification.show("Failed: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void handleExpenseTransaction() {
        BankReconciliationDtos.TransactionDto sel = txGrid.asSingleSelect().getValue();
        if (sel == null) {
            Notification.show("Select a transaction to record expense", 2500, Notification.Position.MIDDLE);
            return;
        }

        Dialog d = new Dialog();
        d.setHeaderTitle("Record as Operating Expense");

        ComboBox<String> category = new ComboBox<>("Expense Category");
        category.setItems("OFFICE", "TRAVEL", "BANK_CHARGES", "UTILITIES", "SALARY", "MAINTENANCE", "MARKETING", "OTHER");
        category.setValue("BANK_CHARGES");

        TextField notes = new TextField("Notes", sel.description());
        notes.setWidthFull();

        VerticalLayout form = new VerticalLayout(category, notes);
        form.setPadding(false);
        d.add(form);

        Button save = new Button("Record Expense", e -> {
            try {
                reconService.expense(sel.id(), new BankReconciliationDtos.ExpenseRequest(
                        category.getValue(), "Bank Statement Expense", "BANK", notes.getValue(), null, "WEB_USER"
                ));
                Notification.show("Recorded as expense successfully", 2500, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                d.close();
                if (selectedBatch != null) refreshTransactions(selectedBatch.id());
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> d.close());

        d.getFooter().add(cancel, save);
        d.open();
    }
}
