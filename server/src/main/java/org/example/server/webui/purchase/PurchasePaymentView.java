package org.example.server.webui.purchase;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
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
import org.example.server.master.MasterDataService;
import org.example.server.master.MasterDtos;
import org.example.server.operations.BusinessOperationsService;
import org.example.server.operations.OperationDtos;
import org.example.server.webui.MainAppLayout;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Route(value = "purchases/payments", layout = MainAppLayout.class)
@PageTitle("Vendor Payment Entry | DSE ERP")
@PermitAll
public class PurchasePaymentView extends VerticalLayout {

    private final BusinessOperationsService operationsService;
    private final MasterDataService masterDataService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final ComboBox<MasterDtos.PartyDto> supplierPicker = new ComboBox<>("Vendor / Supplier");
    private final ComboBox<OperationDtos.PurchaseDto> billPicker = new ComboBox<>("Inward Bill #");
    private final DatePicker paymentDate = new DatePicker("Payment Date");
    private final ComboBox<String> modePicker = new ComboBox<>("Payment Mode");
    private final NumberField amountField = new NumberField("Payment Amount (₹)");
    private final TextField referenceField = new TextField("Cheque / UTR / Transaction #");
    private final TextField remarksField = new TextField("Remarks");

    private final Span billTotalSpan = new Span("₹ 0.00");
    private final Span billPaidSpan = new Span("₹ 0.00");
    private final Span billBalanceSpan = new Span("₹ 0.00");

    public PurchasePaymentView(BusinessOperationsService operationsService, MasterDataService masterDataService) {
        this.operationsService = operationsService;
        this.masterDataService = masterDataService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createForm();

        paymentDate.setValue(LocalDate.now());
        modePicker.setItems("NEFT/RTGS", "IMPS", "CHEQUE", "CASH", "UPI", "DIRECT DEBIT");
        modePicker.setValue("NEFT/RTGS");

        loadSuppliers();
    }

    private void createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout titleBox = new VerticalLayout();
        titleBox.setPadding(false);
        titleBox.setSpacing(false);

        H2 title = new H2("Record Vendor Payment");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");
        Paragraph sub = new Paragraph("Disburse bank or cash payments to suppliers and clear payables");
        sub.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, sub);

        Button backBtn = new Button("Back to Purchases", new Icon(VaadinIcon.ARROW_LEFT), e -> UI.getCurrent().navigate("purchases"));
        backBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        header.add(titleBox, backBtn);
        add(header);
    }

    private void createForm() {
        VerticalLayout card = new VerticalLayout();
        card.setWidth("680px");
        card.getStyle()
                .set("padding", "28px")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background", "var(--lumo-tint-5pct)")
                .set("box-shadow", "0 2px 10px rgba(0,0,0,0.05)");

        supplierPicker.setItemLabelGenerator(MasterDtos.PartyDto::name);
        supplierPicker.setWidthFull();
        supplierPicker.addValueChangeListener(e -> loadBillsForSupplier(e.getValue()));

        billPicker.setItemLabelGenerator(b -> b.invoiceNo() + " (Total: " + currencyFormat.format(b.totalAmount()) + ")");
        billPicker.setWidthFull();
        billPicker.addValueChangeListener(e -> updateBillBalance(e.getValue()));

        HorizontalLayout billStats = new HorizontalLayout();
        billStats.setWidthFull();
        billStats.getStyle()
                .set("padding", "12px 16px")
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin-top", "8px")
                .set("margin-bottom", "8px");

        billStats.add(
                createStatBox("Bill Total", billTotalSpan),
                createStatBox("Already Paid", billPaidSpan),
                createStatBox("Payable Balance", billBalanceSpan)
        );

        billBalanceSpan.getStyle().set("color", "var(--lumo-error-color)");

        HorizontalLayout row1 = new HorizontalLayout(paymentDate, modePicker);
        row1.setWidthFull();
        paymentDate.setWidth("50%");
        modePicker.setWidth("50%");

        HorizontalLayout row2 = new HorizontalLayout(amountField, referenceField);
        row2.setWidthFull();
        amountField.setWidth("50%");
        referenceField.setWidth("50%");

        remarksField.setWidthFull();

        Button submitBtn = new Button("Confirm Vendor Payment", new Icon(VaadinIcon.CHECK), e -> submitPayment());
        submitBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submitBtn.setWidthFull();

        card.add(supplierPicker, billPicker, billStats, row1, row2, remarksField, submitBtn);
        add(card);
    }

    private VerticalLayout createStatBox(String label, Span val) {
        VerticalLayout box = new VerticalLayout();
        box.setPadding(false);
        box.setSpacing(false);
        Span l = new Span(label);
        l.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)");
        val.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-m)");
        box.add(l, val);
        return box;
    }

    private void loadSuppliers() {
        try {
            List<MasterDtos.PartyDto> list = masterDataService.parties("SUPPLIER");
            if (list != null) supplierPicker.setItems(list);
        } catch (Exception ignored) {}
    }

    private void loadBillsForSupplier(MasterDtos.PartyDto supplier) {
        if (supplier == null) {
            billPicker.clear();
            return;
        }
        try {
            OperationDtos.PurchasePage page = operationsService.purchasesPage(
                    0, 50, null, supplier.name(), null, null, "UNPAID", null, null, null, null, false, false
            );
            if (page != null && page.rows() != null) {
                billPicker.setItems(page.rows());
            }
        } catch (Exception ignored) {}
    }

    private void updateBillBalance(OperationDtos.PurchaseDto bill) {
        if (bill == null) {
            billTotalSpan.setText("₹ 0.00");
            billPaidSpan.setText("₹ 0.00");
            billBalanceSpan.setText("₹ 0.00");
            amountField.clear();
            return;
        }
        double total = bill.totalAmount();
        double paid = bill.paidAmount();
        double bal = Math.max(0, total - paid);

        billTotalSpan.setText(currencyFormat.format(total));
        billPaidSpan.setText(currencyFormat.format(paid));
        billBalanceSpan.setText(currencyFormat.format(bal));
        amountField.setValue(bal);
    }

    private void submitPayment() {
        if (supplierPicker.getValue() == null || billPicker.getValue() == null) {
            Notification.show("Please select both a supplier and an inward bill", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }
        if (amountField.getValue() == null || amountField.getValue() <= 0) {
            Notification.show("Please specify a valid payment amount", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        Notification.show("Vendor payment of " + currencyFormat.format(amountField.getValue()) + " recorded successfully!", 3500, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        UI.getCurrent().navigate("purchases");
    }
}
