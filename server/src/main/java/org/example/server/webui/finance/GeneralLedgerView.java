package org.example.server.webui.finance;

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
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.accounting.GeneralLedgerDtos;
import org.example.server.accounting.GeneralLedgerService;
import org.example.server.webui.MainAppLayout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Route(value = "finance/ledger", layout = MainAppLayout.class)
@PageTitle("General Ledger | DSE ERP")
@PermitAll
public class GeneralLedgerView extends VerticalLayout {

    private final GeneralLedgerService glService;
    private final Grid<GeneralLedgerDtos.AccountDto> accountsGrid = new Grid<>();
    private final Grid<GeneralLedgerDtos.JournalEntryDto> journalGrid = new Grid<>();
    private final Grid<GeneralLedgerDtos.TrialBalanceItemDto> trialBalanceGrid = new Grid<>();

    public GeneralLedgerView(GeneralLedgerService glService) {
        this.glService = glService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("General Ledger & Accounting Engine");
        title.getStyle().set("margin", "0");

        Button newAccountBtn = new Button("New Account", VaadinIcon.PLUS.create(), e -> openNewAccountDialog());
        newAccountBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button newJournalBtn = new Button("Post Journal Entry", VaadinIcon.BOOK_DOLLAR.create(), e -> openNewJournalDialog());
        newJournalBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        Button refreshBtn = new Button("Refresh", VaadinIcon.REFRESH.create(), e -> refreshAll());

        HorizontalLayout actionRow = new HorizontalLayout(newAccountBtn, newJournalBtn, refreshBtn);
        header.add(title, actionRow);
        header.expand(title);
        add(header);

        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        configureAccountsGrid();
        configureJournalGrid();
        configureTrialBalanceGrid();

        tabSheet.add("Chart of Accounts", accountsGrid);
        tabSheet.add("Journal Entries", journalGrid);
        tabSheet.add("Trial Balance", trialBalanceGrid);

        add(tabSheet);
        refreshAll();
    }

    private void configureAccountsGrid() {
        accountsGrid.addColumn(GeneralLedgerDtos.AccountDto::accountCode).setHeader("Account Code").setAutoWidth(true).setSortable(true);
        accountsGrid.addColumn(GeneralLedgerDtos.AccountDto::accountName).setHeader("Account Name").setFlexGrow(1).setSortable(true);
        accountsGrid.addComponentColumn(a -> {
            Span b = new Span(a.accountType());
            b.getElement().getThemeList().add("badge");
            if ("ASSET".equalsIgnoreCase(a.accountType())) b.getElement().getThemeList().add("success");
            else if ("LIABILITY".equalsIgnoreCase(a.accountType())) b.getElement().getThemeList().add("warning");
            else if ("EXPENSE".equalsIgnoreCase(a.accountType())) b.getElement().getThemeList().add("error");
            return b;
        }).setHeader("Type").setAutoWidth(true);
        accountsGrid.addColumn(GeneralLedgerDtos.AccountDto::accountSubtype).setHeader("Subtype").setAutoWidth(true);
        accountsGrid.addColumn(GeneralLedgerDtos.AccountDto::currency).setHeader("Currency").setAutoWidth(true);
        accountsGrid.addColumn(a -> "₹ " + (a.openingBalance() != null ? a.openingBalance().toPlainString() : "0.00")).setHeader("Opening Bal").setAutoWidth(true);
        accountsGrid.addColumn(a -> "₹ " + (a.currentBalance() != null ? a.currentBalance().toPlainString() : "0.00")).setHeader("Current Balance").setAutoWidth(true);
    }

    private void configureJournalGrid() {
        journalGrid.addColumn(GeneralLedgerDtos.JournalEntryDto::entryNumber).setHeader("Entry #").setAutoWidth(true).setSortable(true);
        journalGrid.addColumn(GeneralLedgerDtos.JournalEntryDto::entryDate).setHeader("Date").setAutoWidth(true).setSortable(true);
        journalGrid.addColumn(GeneralLedgerDtos.JournalEntryDto::entryType).setHeader("Type").setAutoWidth(true);
        journalGrid.addColumn(GeneralLedgerDtos.JournalEntryDto::referenceNo).setHeader("Ref No").setAutoWidth(true);
        journalGrid.addColumn(GeneralLedgerDtos.JournalEntryDto::narration).setHeader("Narration").setFlexGrow(1);
        journalGrid.addColumn(j -> "₹ " + (j.totalDebit() != null ? j.totalDebit().toPlainString() : "0.00")).setHeader("Total Debit").setAutoWidth(true);
        journalGrid.addColumn(j -> "₹ " + (j.totalCredit() != null ? j.totalCredit().toPlainString() : "0.00")).setHeader("Total Credit").setAutoWidth(true);
        journalGrid.addComponentColumn(j -> {
            Span b = new Span(j.status());
            b.getElement().getThemeList().add("badge success");
            return b;
        }).setHeader("Status").setAutoWidth(true);
        journalGrid.addColumn(GeneralLedgerDtos.JournalEntryDto::postedBy).setHeader("Posted By").setAutoWidth(true);
    }

    private void configureTrialBalanceGrid() {
        trialBalanceGrid.addColumn(GeneralLedgerDtos.TrialBalanceItemDto::accountCode).setHeader("Code").setAutoWidth(true).setSortable(true);
        trialBalanceGrid.addColumn(GeneralLedgerDtos.TrialBalanceItemDto::accountName).setHeader("Account").setFlexGrow(1).setSortable(true);
        trialBalanceGrid.addColumn(GeneralLedgerDtos.TrialBalanceItemDto::accountType).setHeader("Type").setAutoWidth(true);
        trialBalanceGrid.addColumn(t -> "₹ " + (t.openingBalance() != null ? t.openingBalance().toPlainString() : "0.00")).setHeader("Opening Bal").setAutoWidth(true);
        trialBalanceGrid.addColumn(t -> "₹ " + (t.debitAmount() != null ? t.debitAmount().toPlainString() : "0.00")).setHeader("Debit").setAutoWidth(true);
        trialBalanceGrid.addColumn(t -> "₹ " + (t.creditAmount() != null ? t.creditAmount().toPlainString() : "0.00")).setHeader("Credit").setAutoWidth(true);
        trialBalanceGrid.addColumn(t -> "₹ " + (t.closingBalance() != null ? t.closingBalance().toPlainString() : "0.00")).setHeader("Closing Balance").setAutoWidth(true);
    }

    private void refreshAll() {
        try {
            accountsGrid.setItems(glService.listAccounts());
            journalGrid.setItems(glService.listRecentEntries());
            trialBalanceGrid.setItems(glService.getTrialBalance());
        } catch (Exception ex) {
            Notification.show("Error loading ledger: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openNewAccountDialog() {
        Dialog d = new Dialog();
        d.setHeaderTitle("Create New Account in Chart of Accounts");

        TextField code = new TextField("Account Code");
        TextField name = new TextField("Account Name");
        ComboBox<String> type = new ComboBox<>("Account Type");
        type.setItems("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE");
        type.setValue("EXPENSE");

        TextField subtype = new TextField("Subtype / Group");
        BigDecimalField openingBal = new BigDecimalField("Opening Balance");
        openingBal.setValue(BigDecimal.ZERO);

        VerticalLayout form = new VerticalLayout(code, name, type, subtype, openingBal);
        form.setPadding(false);
        d.add(form);

        Button save = new Button("Save", e -> {
            if (code.isEmpty() || name.isEmpty() || type.isEmpty()) {
                Notification.show("Please fill code, name, and type", 3000, Notification.Position.MIDDLE);
                return;
            }
            try {
                glService.createAccount(new GeneralLedgerDtos.AccountDto(
                        null, code.getValue(), name.getValue(), type.getValue(),
                        subtype.getValue(), null, "INR", openingBal.getValue(),
                        openingBal.getValue(), true, false, null
                ));
                Notification.show("Account created successfully", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                d.close();
                refreshAll();
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

    private void openNewJournalDialog() {
        Dialog d = new Dialog();
        d.setHeaderTitle("Post Double-Entry Journal Voucher");
        d.setWidth("750px");

        DatePicker datePicker = new DatePicker("Entry Date", LocalDate.now());
        ComboBox<String> entryType = new ComboBox<>("Entry Type");
        entryType.setItems("GENERAL", "PAYMENT", "RECEIPT", "CONTRA", "ADJUSTMENT");
        entryType.setValue("GENERAL");

        TextField refNo = new TextField("Reference Number");
        TextField narration = new TextField("Narration");
        narration.setWidthFull();

        // Line 1: Debit Line
        List<GeneralLedgerDtos.AccountDto> accounts = glService.listAccounts();
        ComboBox<GeneralLedgerDtos.AccountDto> drAccount = new ComboBox<>("Debit Account");
        drAccount.setItems(accounts);
        drAccount.setItemLabelGenerator(a -> a.accountCode() + " - " + a.accountName());
        drAccount.setWidthFull();
        BigDecimalField drAmount = new BigDecimalField("Debit Amount (₹)");
        drAmount.setValue(BigDecimal.ZERO);

        // Line 2: Credit Line
        ComboBox<GeneralLedgerDtos.AccountDto> crAccount = new ComboBox<>("Credit Account");
        crAccount.setItems(accounts);
        crAccount.setItemLabelGenerator(a -> a.accountCode() + " - " + a.accountName());
        crAccount.setWidthFull();
        BigDecimalField crAmount = new BigDecimalField("Credit Amount (₹)");
        crAmount.setValue(BigDecimal.ZERO);

        VerticalLayout form = new VerticalLayout(
                new HorizontalLayout(datePicker, entryType, refNo),
                narration,
                new HorizontalLayout(drAccount, drAmount),
                new HorizontalLayout(crAccount, crAmount)
        );
        form.setPadding(false);
        d.add(form);

        Button post = new Button("Post Entry", e -> {
            if (drAccount.isEmpty() || crAccount.isEmpty()) {
                Notification.show("Please select both debit and credit accounts", 3000, Notification.Position.MIDDLE);
                return;
            }
            if (drAmount.getValue() == null || crAmount.getValue() == null ||
                    drAmount.getValue().compareTo(crAmount.getValue()) != 0 ||
                    drAmount.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show("Debit amount must equal credit amount and be greater than 0", 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                List<GeneralLedgerDtos.CreateLineRequest> lines = new ArrayList<>();
                lines.add(new GeneralLedgerDtos.CreateLineRequest(
                        drAccount.getValue().id(), drAccount.getValue().accountCode(),
                        drAmount.getValue(), BigDecimal.ZERO, null, narration.getValue(), null
                ));
                lines.add(new GeneralLedgerDtos.CreateLineRequest(
                        crAccount.getValue().id(), crAccount.getValue().accountCode(),
                        BigDecimal.ZERO, crAmount.getValue(), null, narration.getValue(), null
                ));

                glService.postJournalEntry(new GeneralLedgerDtos.CreateJournalRequest(
                        datePicker.getValue(), entryType.getValue(), "MANUAL", null,
                        refNo.getValue(), narration.getValue(), "WEB_USER", lines
                ));

                Notification.show("Journal Entry posted successfully!", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                d.close();
                refreshAll();
            } catch (Exception ex) {
                Notification.show("Failed to post: " + ex.getMessage(), 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        post.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> d.close());

        d.getFooter().add(cancel, post);
        d.open();
    }
}
