package org.example.documentstudio.service;

import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateFieldDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Universal ERP field catalog used by Document Studio 7.3.0. */
public final class TemplateFieldCatalog {
    private static final List<TemplateFieldDefinition> DOCUMENT = List.of(
            text("document.pageNumber", "Page Number", "Document"),
            text("document.totalPages", "Total Pages", "Document")
    );

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
            text("totals.gstAmount", "Total GST / IGST", "Totals"),
            text("totals.cgstAmount", "CGST Amount", "Totals"),
            text("totals.sgstAmount", "SGST Amount", "Totals"),
            text("totals.igstAmount", "IGST Amount", "Totals"),
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
            text("purchase.transporterGstin", "Transporter GSTIN", "Purchase"),
            text("purchase.vehicleNo", "Vehicle Number", "Purchase"),
            text("purchase.contactPerson", "Contact Person", "Purchase"),
            text("purchase.contactMobile", "Contact Mobile", "Purchase"),
            text("purchase.lrAwbNo", "LR / AWB Number", "Purchase"),
            text("purchase.remarks", "Remarks", "Purchase"),
            text("purchase.notes", "Purchase Notes", "Purchase"),
            text("purchase.billingAddress", "Billing Address", "Purchase"),
            text("purchase.deliveryAddress", "Delivery Address", "Purchase"),
            text("purchase.billingGstin", "Billing GSTIN", "Purchase"),
            text("purchase.deliveryGstin", "Delivery GSTIN", "Purchase"),
            text("purchase.gstType", "GST / IGST Type", "Purchase"),
            text("purchase.orderNo", "PO / Supplier Reference", "Purchase"),
            text("purchase.poDate", "PO Date", "Purchase"),
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
            text("sales.deliveryAddress", "Delivery / Shipping Address", "Sales"),
            text("sales.shippingAddress", "Shipping Address (Alias)", "Sales"),
            text("sales.gstType", "GST / IGST Type", "Sales"),
            text("customer.name", "Customer Name", "Customer"),
            text("customer.address", "Customer Address", "Customer"),
            text("customer.gstin", "Customer GSTIN", "Customer"),
            text("customer.phone", "Customer Phone", "Customer"),
            text("customer.email", "Customer Email", "Customer")
    );

    private static final List<TemplateFieldDefinition> SALES_EXCEL_EXTRA = List.of(
            text("sales.invoiceType", "Invoice Type", "Sales"),
            text("sales.orderNo", "Sales Order Number", "Sales"),
            text("sales.poDate", "Customer PO Date", "Sales"),
            text("sales.transporterGstin", "Transporter GSTIN", "Sales"),
            text("sales.vehicleNo", "Vehicle Number", "Sales"),
            text("sales.doorDelivery", "Door Delivery", "Sales"),
            text("sales.contactPerson", "Contact Person", "Sales"),
            text("sales.contactMobile", "Contact Mobile", "Sales"),
            text("sales.transportNote", "Transport Note", "Sales"),
            text("sales.emailStatus", "Email Status", "Sales"),
            text("sales.whatsappStatus", "WhatsApp Status", "Sales"),
            text("sales.billingGstin", "Billing GSTIN", "Sales"),
            text("sales.deliveryGstin", "Delivery GSTIN", "Sales"),
            text("sales.shippingGstin", "Shipping GSTIN (Alias)", "Sales"),
            text("sales.gstin", "Sales GSTIN / Legacy GSTIN", "Sales"),
            text("sales.createdAt", "Created At", "Sales"),
            text("sales.totalQuantity", "Total Quantity", "Sales"),
            text("sales.sameAsBilling", "Delivery Same As Billing", "Sales"),
            text("customer.code", "Customer Code", "Customer"),
            text("customer.contactPerson", "Customer Contact Person", "Customer")
    );

    private static final List<TemplateFieldDefinition> EXCEL_ITEMS = List.of(
            text("item.serial", "Serial Number", "Item Table • Identity"),
            text("item.code", "Item Code", "Item Table • Identity"),
            text("item.description", "Description", "Item Table • Identity"),
            text("item.descriptionWithRemarks", "Description + Item Remarks", "Item Table • Identity"),
            text("item.remarks", "Item Remarks", "Item Table • Identity"),
            text("item.hsn", "HSN / SAC", "Item Table • Identity"),
            text("item.quantity", "Sale / Transaction Quantity", "Item Table • Transaction"),
            text("item.unit", "Sale / Transaction Unit (KG, SET, NOS...)", "Item Table • Transaction"),
            text("item.rate", "Sale / Transaction Rate", "Item Table • Transaction"),
            text("item.discountPercent", "Discount Rate %", "Item Table • Transaction"),
            text("item.discountAmount", "Discount Amount", "Item Table • Transaction"),
            text("item.taxable", "Taxable Amount", "Item Table • Transaction"),
            text("item.gstPercent", "Combined GST / IGST Rate %", "Item Table • Tax"),
            text("item.gstAmount", "Combined GST / IGST Amount", "Item Table • Tax"),
            text("item.cgstPercent", "CGST Rate %", "Item Table • Tax"),
            text("item.cgstAmount", "CGST Amount", "Item Table • Tax"),
            text("item.sgstPercent", "SGST Rate %", "Item Table • Tax"),
            text("item.sgstAmount", "SGST Amount", "Item Table • Tax"),
            text("item.igstPercent", "IGST Rate %", "Item Table • Tax"),
            text("item.igstAmount", "IGST Amount", "Item Table • Tax"),
            text("item.total", "Line / Row Total", "Item Table • Transaction"),
            text("item.category", "Category", "Item Master"),
            text("item.brand", "Brand", "Item Master"),
            text("item.material", "Material", "Item Master"),
            text("item.size", "Size", "Item Master"),
            text("item.location", "Item Location", "Item Master"),
            text("item.purchasePrice", "Current Purchase Price", "Item Master"),
            text("item.sellingPrice", "Current Selling Price", "Item Master"),
            text("item.availableStock", "Current Available Stock", "Item Master"),
            text("item.openingStock", "Opening Stock", "Item Master"),
            text("item.minimumStock", "Minimum Stock", "Item Master"),
            text("item.reservedStock", "Reserved Stock", "Item Master"),
            text("item.masterGstPercent", "Master GST %", "Item Master"),
            text("item.masterDiscountPercent", "Master Discount %", "Item Master")
    );

    private static final List<TemplateFieldDefinition> EXCEL_CHARGES = List.of(
            text("charge.serial", "Charge Serial Number", "Charges • Repeating"),
            text("charge.type", "Charge Type / Name", "Charges • Repeating"),
            text("charge.amount", "Charge Value / Amount", "Charges • Repeating"),
            text("charge.taxable", "Charge Taxable (Yes / No)", "Charges • Repeating"),
            text("charge.taxableAmount", "Charge Taxable / Base Amount", "Charges • Repeating"),
            text("charge.gstPercent", "Charge GST / IGST Rate %", "Charges • Repeating"),
            text("charge.taxAmount", "Charge GST / IGST Amount", "Charges • Repeating"),
            text("charge.cgstPercent", "Charge CGST Rate %", "Charges • Tax"),
            text("charge.cgstAmount", "Charge CGST Amount", "Charges • Tax"),
            text("charge.sgstPercent", "Charge SGST Rate %", "Charges • Tax"),
            text("charge.sgstAmount", "Charge SGST Amount", "Charges • Tax"),
            text("charge.igstPercent", "Charge IGST Rate %", "Charges • Tax"),
            text("charge.igstAmount", "Charge IGST Amount", "Charges • Tax"),
            text("charge.total", "Charge Total Including Tax", "Charges • Repeating")
    );

    private static final List<TemplateFieldDefinition> EXCEL_TOTALS_EXTRA = List.of(
            text("totals.chargesAmount", "Total Additional Charges (Before Tax)", "Totals • Charges"),
            text("totals.chargeTaxAmount", "Total Tax on Additional Charges", "Totals • Charges"),
            text("totals.chargesTotal", "Total Additional Charges Including Tax", "Totals • Charges"),
            text("totals.grossBeforeTax", "Gross Total Before Tax", "Totals • Charges"),
            text("totals.preRoundTotal", "Total Before Rounding", "Totals • Rounding"),
            text("totals.roundOff", "Round Off / Rounding", "Totals • Rounding"),
            text("totals.roundedGrandTotal", "Grand Total (Rounded)", "Totals • Rounding")
    );

    private TemplateFieldCatalog() {}

    public static List<TemplateFieldDefinition> fieldsFor(DocumentType type) {
        if (type == null || type == DocumentType.GENERAL_PDF) return List.of();
        return switch (type) {
            case PURCHASE_INVOICE, PURCHASE_ORDER -> combine(DOCUMENT, COMPANY, PURCHASE, TOTALS, PAYMENT);
            case PURCHASE_RETURN -> combine(DOCUMENT, COMPANY, RETURN, TOTALS, PAYMENT);
            case QUOTATION -> combine(DOCUMENT, COMPANY, QUOTATION, TOTALS, PAYMENT);
            case DELIVERY_CHALLAN -> combine(DOCUMENT, COMPANY, DELIVERY);
            case CREDIT_NOTE, DEBIT_NOTE, SALES_RETURN -> combine(DOCUMENT, COMPANY, RETURN, TOTALS);
            case PAYMENT_RECEIPT -> combine(DOCUMENT, COMPANY, RECEIPT, PAYMENT);
            case SALES_INVOICE -> combine(DOCUMENT, COMPANY, SALES, TOTALS, PAYMENT);
            case CUSTOM_ERP -> combine(DOCUMENT, COMPANY, TOTALS, PAYMENT);
            case GENERAL_PDF -> List.of();
        };
    }


    /** Excel Studio uses workbook-safe fields and exposes repeating rows for every line-item document type. */
    public static List<TemplateFieldDefinition> excelFieldsFor(DocumentType type) {
        List<TemplateFieldDefinition> base = fieldsFor(type).stream()
                .filter(field -> !field.key().startsWith("document."))
                .toList();
        List<TemplateFieldDefinition> documentSpecific = type == DocumentType.SALES_INVOICE
                ? combine(base, SALES_EXCEL_EXTRA) : base;
        // Keep Excel-only rounding fields out of PDF Studio and only show them for document types that expose invoice totals.
        if (base.stream().anyMatch(field -> field.key().equals("totals.grandTotal")))
            documentSpecific = combine(documentSpecific, EXCEL_TOTALS_EXTRA);
        if (supportsItemRows(type) && supportsChargeRows(type)) return combine(documentSpecific, EXCEL_ITEMS, EXCEL_CHARGES);
        if (supportsItemRows(type)) return combine(documentSpecific, EXCEL_ITEMS);
        return documentSpecific;
    }

    /**
     * PDF Studio V2 field catalogue.  It deliberately has its own expansion path so the richer
     * free-form PDF designer can expose transaction rows, charge rows and calculated totals
     * without changing Excel Studio or the legacy base catalogue used elsewhere.
     */
    public static List<TemplateFieldDefinition> pdfFieldsFor(DocumentType type) {
        List<TemplateFieldDefinition> result = new ArrayList<>(fieldsFor(type));
        if (type == DocumentType.SALES_INVOICE) appendUnique(result, SALES_EXCEL_EXTRA);
        if (result.stream().anyMatch(field -> field.key().equals("totals.grandTotal")))
            appendUnique(result, EXCEL_TOTALS_EXTRA);
        if (supportsItemRows(type)) appendUnique(result, EXCEL_ITEMS);
        if (supportsChargeRows(type)) appendUnique(result, EXCEL_CHARGES);
        return List.copyOf(result);
    }

    public static TemplateFieldDefinition findPdf(DocumentType type, String key) {
        if (key == null) return null;
        return pdfFieldsFor(type).stream().filter(field -> field.key().equals(key)).findFirst().orElse(null);
    }

    /** Minimum mappings required before an Excel template can become the default for its document type. */
    public static List<String> requiredExcelFieldsFor(DocumentType type) {
        if (type == null) return List.of();
        return switch (type) {
            case SALES_INVOICE -> List.of("sales.number", "sales.date", "customer.name");
            case PURCHASE_INVOICE, PURCHASE_ORDER -> List.of("purchase.number", "purchase.date", "supplier.name");
            case PURCHASE_RETURN, SALES_RETURN, CREDIT_NOTE, DEBIT_NOTE -> List.of("return.number", "return.date", "party.name");
            case QUOTATION -> List.of("quotation.number", "quotation.date", "customer.name");
            case DELIVERY_CHALLAN -> List.of("delivery.number", "delivery.date", "customer.name");
            case PAYMENT_RECEIPT -> List.of("receipt.number", "receipt.date", "receipt.partyName", "receipt.amount");
            case CUSTOM_ERP, GENERAL_PDF -> List.of();
        };
    }

    public static boolean supportsItemRows(DocumentType type) {
        if (type == null) return false;
        return switch (type) {
            case SALES_INVOICE, PURCHASE_INVOICE, PURCHASE_ORDER, PURCHASE_RETURN, SALES_RETURN,
                    QUOTATION, DELIVERY_CHALLAN, CREDIT_NOTE, DEBIT_NOTE, CUSTOM_ERP -> true;
            default -> false;
        };
    }

    /** True when a default template for this document type must contain a usable repeating item row. */
    public static boolean requiresItemRowForDefault(DocumentType type) {
        if (type == null) return false;
        return switch (type) {
            case SALES_INVOICE, PURCHASE_INVOICE, PURCHASE_ORDER, PURCHASE_RETURN, SALES_RETURN,
                    QUOTATION, DELIVERY_CHALLAN, CREDIT_NOTE, DEBIT_NOTE -> true;
            default -> false;
        };
    }

    public static boolean supportsChargeRows(DocumentType type) {
        return type == DocumentType.SALES_INVOICE || type == DocumentType.PURCHASE_INVOICE || type == DocumentType.PURCHASE_ORDER;
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

    private static void appendUnique(List<TemplateFieldDefinition> target, List<TemplateFieldDefinition> source) {
        Set<String> keys = new java.util.LinkedHashSet<>();
        for (TemplateFieldDefinition existing : target) keys.add(existing.key());
        for (TemplateFieldDefinition field : source) if (keys.add(field.key())) target.add(field);
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
