package org.example.server.webui.finance;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.operations.BusinessOperationsService;
import org.example.server.webui.MainAppLayout;

import java.time.LocalDate;

@Route(value = "finance/record-payment", layout = MainAppLayout.class)
@PageTitle("Record Payment & Expense | DSE ERP")
@PermitAll
public class RecordPaymentView extends VerticalLayout {

    private final BusinessOperationsService operationsService;

    private final ComboBox<String> voucherType = new ComboBox<>("Voucher Classification");
    private final DatePicker voucherDate = new DatePicker("Voucher Date");
    private final ComboBox<String> categoryPicker = new ComboBox<>("Expense / Income Category");
    private final ComboBox<String> accountPicker = new ComboBox<>("Bank Account");
    private final ComboBox<String> modePicker = new ComboBox<>("Payment Mode");
    private final NumberField amountField = new NumberField("Transaction Amount (₹)");
    private final TextField referenceNoField = new TextField("Reference / Cheque / UTR #");
    private final TextField notesField = new TextField("Description & Notes");

    public RecordPaymentView(BusinessOperationsService operationsService) {
        this.operationsService = operationsService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createForm();
    }

    private void createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout titleBox = new VerticalLayout();
        titleBox.setPadding(false);
        titleBox.setSpacing(false);

        H2 title = new H2("Direct Voucher Entry");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");
        Paragraph sub = new Paragraph("Record standalone expenses, bank deposits, withdrawals, and petty cash");
        sub.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, sub);

        Button backBtn = new Button("Back to Vouchers", new Icon(VaadinIcon.ARROW_LEFT), e -> UI.getCurrent().navigate("finance/bank-expenses"));
        backBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        header.add(titleBox, backBtn);
        add(header);
    }

    private void createForm() {
        VerticalLayout card = new VerticalLayout();
        card.setWidth("640px");
        card.getStyle()
                .set("padding", "28px")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background", "var(--lumo-tint-5pct)")
                .set("box-shadow", "0 2px 10px rgba(0,0,0,0.05)");

        voucherType.setItems("EXPENSE", "DEPOSIT", "WITHDRAWAL", "GENERAL PAYMENT");
        voucherType.setValue("EXPENSE");
        voucherType.setWidthFull();

        voucherDate.setValue(LocalDate.now());
        voucherDate.setWidthFull();

        categoryPicker.setItems("Office Rent", "Electricity & Power", "Staff Welfare", "Freight & Cartage", "Factory Maintenance", "Fuel & Transport", "Professional Fees", "Miscellaneous");
        categoryPicker.setValue("Office Rent");
        categoryPicker.setWidthFull();

        accountPicker.setItems("Current Account - HDFC", "Cash in Hand", "Current Account - SBI", "Petty Cash");
        accountPicker.setValue("Current Account - HDFC");
        accountPicker.setWidthFull();

        modePicker.setItems("NEFT/RTGS", "IMPS", "CHEQUE", "CASH", "UPI");
        modePicker.setValue("NEFT/RTGS");
        modePicker.setWidthFull();

        amountField.setMin(0.01);
        amountField.setWidthFull();

        referenceNoField.setWidthFull();
        notesField.setWidthFull();

        Button submitBtn = new Button("Post Voucher to Ledger", new Icon(VaadinIcon.CHECK), e -> postVoucher());
        submitBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submitBtn.setWidthFull();

        card.add(voucherType, voucherDate, categoryPicker, accountPicker, modePicker, amountField, referenceNoField, notesField, submitBtn);
        add(card);
    }

    private void postVoucher() {
        if (amountField.getValue() == null || amountField.getValue() <= 0) {
            Notification.show("Please enter a valid amount", 2500, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        Notification.show(voucherType.getValue() + " voucher of ₹ " + amountField.getValue() + " recorded!", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        UI.getCurrent().navigate("finance/bank-expenses");
    }
}
