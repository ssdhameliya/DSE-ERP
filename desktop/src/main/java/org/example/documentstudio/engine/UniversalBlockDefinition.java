package org.example.documentstudio.engine;

import java.util.List;
import java.util.Set;

/**
 * Clean specification of a semantic PDF Studio block.
 * Supports block-wise mapping UI where each block has an isolated, focused dialog.
 */
public record UniversalBlockDefinition(
        String id,
        String displayName,
        String description,
        String icon,
        Set<String> candidateFieldKeys,
        boolean isTableGrid
) {
    public static final UniversalBlockDefinition DOC_HEADER = new UniversalBlockDefinition(
            "DOC_HEADER",
            "Document Header & Meta",
            "Invoice/Document Number, Date, PO Number, Due Date, Delivery Note, Vehicle number.",
            "document",
            Set.of(
                    "document.number", "document.date", "document.dueDate", "document.poNumber",
                    "document.poDate", "document.vehicleNumber", "document.deliveryNote",
                    "document.reference", "document.destination", "document.dispatchThrough"
            ),
            false
    );

    public static final UniversalBlockDefinition COMPANY_HEADER = new UniversalBlockDefinition(
            "COMPANY_HEADER",
            "Company & Seller Details",
            "Your company branding: Name, Address, GSTIN, Phone, Email, PAN, and Logo image.",
            "building",
            Set.of(
                    "company.name", "company.address", "company.gstin", "company.pan",
                    "company.phone", "company.email", "company.website", "company.logo",
                    "company.state", "company.stateCode"
            ),
            false
    );

    public static final UniversalBlockDefinition BILLING_ADDRESS = new UniversalBlockDefinition(
            "BILLING_ADDRESS",
            "Customer Billing Details (Bill To)",
            "Buyer/Client name, billing address, GSTIN, PAN, state, and contact details.",
            "user",
            Set.of(
                    "party.name", "party.billingAddress", "party.gstin", "party.pan",
                    "party.state", "party.stateCode", "party.phone", "party.email",
                    "party.contactPerson"
            ),
            false
    );

    public static final UniversalBlockDefinition SHIPPING_ADDRESS = new UniversalBlockDefinition(
            "SHIPPING_ADDRESS",
            "Consignee Shipping Details (Ship To)",
            "Delivery/Consignee name, delivery address, GSTIN, and destination state.",
            "truck",
            Set.of(
                    "party.deliveryName", "party.deliveryAddress", "party.deliveryGstin",
                    "party.deliveryState", "party.deliveryStateCode", "party.deliveryContact"
            ),
            false
    );

    public static final UniversalBlockDefinition TRANSPORT = new UniversalBlockDefinition(
            "TRANSPORT",
            "Transport & Logistics",
            "Transporter name, vehicle number, LR/RR number, LR date, and E-Way bill number.",
            "truck",
            Set.of(
                    "transport.transporterName", "transport.vehicleNumber", "transport.lrNumber",
                    "transport.lrDate", "transport.ewayBillNumber", "transport.destination", "transport.note"
            ),
            false
    );

    public static final UniversalBlockDefinition ITEM_TABLE = new UniversalBlockDefinition(
            "ITEM_TABLE",
            "Item Table Grid",
            "Physical line items table: Sr, Description, HSN/SAC, Qty, Unit, Rate, Discount, GST, Total.",
            "table",
            Set.of(
                    "item.serial", "item.description", "item.descriptionWithRemarks", "item.hsn",
                    "item.quantity", "item.unit", "item.rate", "item.discountPercent",
                    "item.gstPercent", "item.taxableAmount", "item.taxAmount", "item.totalAmount"
            ),
            true
    );

    public static final UniversalBlockDefinition FINANCIAL_TOTALS = new UniversalBlockDefinition(
            "FINANCIAL_TOTALS",
            "Financial Summary & Taxes",
            "Calculation totals: Subtotal, CGST, SGST, IGST, Charges, Discount, Round Off, Grand Total, and Amount in Words.",
            "calculator",
            Set.of(
                    "calculation.taxableAmount", "calculation.cgstAmount", "calculation.sgstAmount",
                    "calculation.igstAmount", "calculation.totalTaxAmount", "calculation.discountAmount",
                    "calculation.chargesAmount", "calculation.roundOff", "calculation.grandTotal",
                    "calculation.amountInWords"
            ),
            false
    );

    public static final UniversalBlockDefinition BANK_DETAILS = new UniversalBlockDefinition(
            "BANK_DETAILS",
            "Bank & Payment Details",
            "Bank Name, Account Number, IFSC Code, Branch, Account Type, and UPI ID.",
            "bank",
            Set.of(
                    "payment.bankName", "payment.accountNumber", "payment.ifsc",
                    "payment.branch", "payment.accountType", "payment.upiId"
            ),
            false
    );

    public static final UniversalBlockDefinition TERMS_SIGNATURE = new UniversalBlockDefinition(
            "TERMS_SIGNATURE",
            "Terms, Conditions & Signatures",
            "Terms & Conditions, notes, remarks, authorized signatory image, and designation.",
            "file-text",
            Set.of(
                    "company.terms", "document.notes", "document.remarks",
                    "company.signature", "company.signatoryDesignation"
            ),
            false
    );

    public static List<UniversalBlockDefinition> standardBlocks() {
        return List.of(
                DOC_HEADER,
                COMPANY_HEADER,
                BILLING_ADDRESS,
                SHIPPING_ADDRESS,
                TRANSPORT,
                ITEM_TABLE,
                FINANCIAL_TOTALS,
                BANK_DETAILS,
                TERMS_SIGNATURE
        );
    }
}
