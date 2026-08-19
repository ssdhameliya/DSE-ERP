package org.example.documentstudio.service;

import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateFieldDefinition;

import java.util.ArrayList;
import java.util.List;

/** Universal ERP field catalog used by Document Studio 7.3.0. */
public final class TemplateFieldCatalog {
    private static final List<TemplateFieldDefinition> COMPANY = List.of(
            text("company.name", "Company Name", "Company"),
            text("company.address", "Company Address", "Company"),
            text("company.gstin", "Company GSTIN", "Company"),
            text("company.phone", "Company Phone", "Company"),
            text("company.email", "Company Email", "Company"),
            text("company.alternateEmail", "Alternate Email", "Company"),
            text("company.certification", "Certification / ISO Text", "Company"),
            text("company.terms", "Terms & Conditions", "Company"),
            image("company.logo", "Company Logo", "Company"),
            image("company.signature", "Authorized Signature", "Company")
    );

    private static final List<TemplateFieldDefinition> PAYMENT = List.of(
            text("payment.bankName", "Bank Name", "Payment"),
            text("payment.branch", "Bank Branch", "Payment"),
            text("payment.accountNumber", "Account Number", "Payment"),
            text("payment.ifsc", "IFSC", "Payment"),
            text("payment.accountType", "Account Type", "Payment"),
            text("payment.mode", "Payment Mode", "Payment")
    );

    private static final List<TemplateFieldDefinition> TOTALS = List.of(
            text("totals.subtotal", "Subtotal / Taxable Amount", "Totals"),
            text("totals.discountAmount", "Discount Amount", "Totals"),
            text("totals.gstAmount", "GST Amount", "Totals"),
            text("totals.grandTotal", "Grand Total", "Totals"),
            text("totals.paidAmount", "Paid Amount", "Totals"),
            text("totals.balanceAmount", "Balance Amount", "Totals"),
            text("totals.amountInWords", "Amount in Words", "Totals")
    );

    private static final List<TemplateFieldDefinition> PURCHASE = List.of(
            text("purchase.number", "Purchase Number", "Purchase"),
            text("purchase.date", "Purchase Date", "Purchase"),
            text("purchase.dueDate", "Due Date", "Purchase"),
            text("purchase.deliveryDate", "Delivery Date", "Purchase"),
            text("purchase.referenceNo", "Reference Number", "Purchase"),
            text("purchase.paymentTerms", "Payment Terms", "Purchase"),
            text("purchase.gstTreatment", "GST Treatment", "Purchase"),
            text("purchase.warehouse", "Warehouse", "Purchase"),
            text("purchase.currency", "Currency", "Purchase"),
            text("purchase.transporter", "Transporter", "Purchase"),
            text("purchase.lrAwbNo", "LR / AWB Number", "Purchase"),
            text("purchase.remarks", "Remarks", "Purchase"),
            text("purchase.createdBy", "Created By", "Purchase"),
            text("purchase.documentStatus", "Document Status", "Purchase"),
            text("purchase.paymentStatus", "Payment Status", "Purchase"),
            text("supplier.code", "Supplier Code", "Supplier"),
            text("supplier.name", "Supplier Name", "Supplier"),
            text("supplier.address", "Supplier Address", "Supplier"),
            text("supplier.gstin", "Supplier GSTIN", "Supplier"),
            text("supplier.contactPerson", "Supplier Contact Person", "Supplier"),
            text("supplier.phone", "Supplier Phone", "Supplier"),
            text("supplier.email", "Supplier Email", "Supplier")
    );

    private static final List<TemplateFieldDefinition> QUOTATION = List.of(
            text("quotation.number", "Quotation Number", "Quotation"),
            text("quotation.date", "Quotation Date", "Quotation"),
            text("quotation.validUntil", "Valid Until", "Quotation"),
            text("quotation.status", "Quotation Status", "Quotation"),
            text("quotation.salesperson", "Salesperson", "Quotation"),
            text("quotation.source", "Source", "Quotation"),
            text("quotation.remarks", "Remarks", "Quotation"),
            text("customer.name", "Customer Name", "Customer"),
            text("customer.address", "Customer Address", "Customer"),
            text("customer.gstin", "Customer GSTIN", "Customer"),
            text("customer.phone", "Customer Phone", "Customer"),
            text("customer.email", "Customer Email", "Customer")
    );

    private static final List<TemplateFieldDefinition> DELIVERY = List.of(
            text("delivery.number", "Challan Number", "Delivery"),
            text("delivery.date", "Challan Date", "Delivery"),
            text("delivery.vehicleNo", "Vehicle Number", "Delivery"),
            text("delivery.transporter", "Transporter", "Delivery"),
            text("delivery.address", "Delivery Address", "Delivery"),
            text("customer.name", "Customer Name", "Customer"),
            text("customer.address", "Customer Address", "Customer"),
            text("customer.gstin", "Customer GSTIN", "Customer")
    );

    private static final List<TemplateFieldDefinition> RETURN = List.of(
            text("return.number", "Return / Note Number", "Return"),
            text("return.date", "Return Date", "Return"),
            text("return.referenceNo", "Reference Number", "Return"),
            text("return.reason", "Reason / Remarks", "Return"),
            text("party.name", "Party Name", "Party"),
            text("party.address", "Party Address", "Party"),
            text("party.gstin", "Party GSTIN", "Party")
    );

    private static final List<TemplateFieldDefinition> RECEIPT = List.of(
            text("receipt.number", "Receipt Number", "Receipt"),
            text("receipt.date", "Receipt Date", "Receipt"),
            text("receipt.partyName", "Party Name", "Receipt"),
            text("receipt.amount", "Amount", "Receipt"),
            text("receipt.reference", "Reference", "Receipt"),
            text("receipt.notes", "Notes", "Receipt")
    );

    private static final List<TemplateFieldDefinition> SALES = List.of(
            text("sales.number", "Sales Invoice Number", "Sales"),
            text("sales.date", "Sales Invoice Date", "Sales"),
            text("sales.dueDate", "Due Date", "Sales"),
            text("sales.referenceNo", "Reference / PO Number", "Sales"),
            text("sales.paymentTerms", "Payment Terms", "Sales"),
            text("sales.transporter", "Transporter", "Sales"),
            text("sales.salesperson", "Sales Person", "Sales"),
            text("sales.source", "Source", "Sales"),
            text("sales.notes", "Invoice Notes", "Sales"),
            text("sales.remarks", "Remarks", "Sales"),
            text("sales.documentStatus", "Document Status", "Sales"),
            text("sales.paymentStatus", "Payment Status", "Sales"),
            text("sales.billingAddress", "Billing Address", "Sales"),
            text("sales.deliveryAddress", "Delivery Address", "Sales"),
            text("customer.name", "Customer Name", "Customer"),
            text("customer.address", "Customer Address", "Customer"),
            text("customer.gstin", "Customer GSTIN", "Customer"),
            text("customer.phone", "Customer Phone", "Customer"),
            text("customer.email", "Customer Email", "Customer")
    );

    private TemplateFieldCatalog() {}

    public static List<TemplateFieldDefinition> fieldsFor(DocumentType type) {
        if (type == null || type == DocumentType.GENERAL_PDF) return List.of();
        return switch (type) {
            case PURCHASE_INVOICE, PURCHASE_ORDER -> combine(COMPANY, PURCHASE, TOTALS, PAYMENT);
            case PURCHASE_RETURN -> combine(COMPANY, RETURN, TOTALS, PAYMENT);
            case QUOTATION -> combine(COMPANY, QUOTATION, TOTALS, PAYMENT);
            case DELIVERY_CHALLAN -> combine(COMPANY, DELIVERY);
            case CREDIT_NOTE, DEBIT_NOTE, SALES_RETURN -> combine(COMPANY, RETURN, TOTALS);
            case PAYMENT_RECEIPT -> combine(COMPANY, RECEIPT, PAYMENT);
            case SALES_INVOICE -> combine(COMPANY, SALES, TOTALS, PAYMENT);
            case CUSTOM_ERP -> combine(COMPANY, TOTALS, PAYMENT);
            case GENERAL_PDF -> List.of();
        };
    }

    /** Backward-compatible alias retained for existing 7.2.x callers. */
    public static List<TemplateFieldDefinition> purchaseFields() { return fieldsFor(DocumentType.PURCHASE_INVOICE); }

    public static TemplateFieldDefinition find(DocumentType type, String key) {
        if (key == null) return null;
        return fieldsFor(type).stream().filter(field -> field.key().equals(key)).findFirst().orElse(null);
    }

    public static TemplateFieldDefinition find(String key) {
        if (key == null) return null;
        for (DocumentType type : DocumentType.values()) {
            TemplateFieldDefinition found = find(type, key);
            if (found != null) return found;
        }
        return null;
    }

    @SafeVarargs
    private static List<TemplateFieldDefinition> combine(List<TemplateFieldDefinition>... groups) {
        List<TemplateFieldDefinition> result = new ArrayList<>();
        for (List<TemplateFieldDefinition> group : groups) result.addAll(group);
        return List.copyOf(result);
    }

    private static TemplateFieldDefinition text(String key, String label, String category) {
        return new TemplateFieldDefinition(key, label, category, false);
    }

    private static TemplateFieldDefinition image(String key, String label, String category) {
        return new TemplateFieldDefinition(key, label, category, true);
    }
}
