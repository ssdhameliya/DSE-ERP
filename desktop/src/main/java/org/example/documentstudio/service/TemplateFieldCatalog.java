package org.example.documentstudio.service;

import org.example.documentstudio.model.TemplateFieldDefinition;

import java.util.List;

/** Purchase-only ERP fields available to the 7.2.5 PDF designer. */
public final class TemplateFieldCatalog {
    private static final List<TemplateFieldDefinition> PURCHASE_FIELDS = List.of(
        text("company.name", "Company Name", "Company"),
        text("company.address", "Company Address", "Company"),
        text("company.gstin", "Company GSTIN", "Company"),
        text("company.phone", "Company Phone", "Company"),
        text("company.email", "Company Email", "Company"),
        text("company.alternateEmail", "Alternate Email", "Company"),
        image("company.logo", "Company Logo", "Company"),
        image("company.signature", "Authorized Signature", "Company"),
        text("company.certification", "Certification / ISO Text", "Company"),

        text("purchase.number", "Purchase Invoice Number", "Purchase"),
        text("purchase.date", "Purchase Invoice Date", "Purchase"),
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
        text("supplier.email", "Supplier Email", "Supplier"),

        text("totals.subtotal", "Subtotal / Taxable Amount", "Totals"),
        text("totals.discountAmount", "Discount Amount", "Totals"),
        text("totals.gstAmount", "GST Amount", "Totals"),
        text("totals.grandTotal", "Grand Total", "Totals"),
        text("totals.paidAmount", "Paid Amount", "Totals"),
        text("totals.balanceAmount", "Balance Amount", "Totals"),
        text("totals.amountInWords", "Amount in Words", "Totals"),

        text("payment.bankName", "Bank Name", "Payment"),
        text("payment.branch", "Bank Branch", "Payment"),
        text("payment.accountNumber", "Account Number", "Payment"),
        text("payment.ifsc", "IFSC", "Payment"),
        text("payment.accountType", "Account Type", "Payment"),
        text("payment.mode", "Payment Mode", "Payment"),
        text("company.terms", "Terms & Conditions", "Payment")
    );

    private TemplateFieldCatalog() {}

    public static List<TemplateFieldDefinition> purchaseFields() { return PURCHASE_FIELDS; }

    public static TemplateFieldDefinition find(String key) {
        if (key == null) return null;
        return PURCHASE_FIELDS.stream().filter(field -> field.key().equals(key)).findFirst().orElse(null);
    }

    private static TemplateFieldDefinition text(String key, String label, String category) {
        return new TemplateFieldDefinition(key, label, category, false);
    }

    private static TemplateFieldDefinition image(String key, String label, String category) {
        return new TemplateFieldDefinition(key, label, category, true);
    }
}
