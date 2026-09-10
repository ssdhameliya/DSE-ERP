package org.example.documentstudio.service;

import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateMappingRequirement;
import org.example.documentstudio.model.TemplateMappingRequirement.Kind;
import org.example.documentstudio.model.TemplateMappingRequirement.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for user-facing PDF Studio requirements.
 *
 * <p>The same catalogue drives the checklist, field search suggestions, validation messages and
 * default-activation readiness for Sales, Purchase, Quotation, Sales Return and Purchase Return.
 * Requirements are business concepts and can be satisfied by alternative ERP fields.</p>
 */
public final class TemplateRequirementCatalog {
    private TemplateRequirementCatalog() {}

    public static List<TemplateMappingRequirement> requirementsFor(DocumentType type) {
        if (type == null || !type.isErpConnected() || type == DocumentType.CUSTOM_ERP) return List.of();
        List<TemplateMappingRequirement> out = new ArrayList<>();
        switch (type) {
            case SALES_INVOICE -> sales(out);
            case PURCHASE_INVOICE, PURCHASE_ORDER -> purchase(out);
            case QUOTATION -> quotation(out);
            case SALES_RETURN -> salesReturn(out);
            case PURCHASE_RETURN -> purchaseReturn(out);
            case CREDIT_NOTE, DEBIT_NOTE -> returnBase(out, type.label(), false);
            case DELIVERY_CHALLAN -> delivery(out);
            case PAYMENT_RECEIPT -> receipt(out);
            case GENERAL_PDF, CUSTOM_ERP -> { }
        }
        return List.copyOf(out);
    }

    private static void sales(List<TemplateMappingRequirement> out) {
        document(out, "Sales Invoice", List.of("document.number", "sales.number"), List.of("document.date", "sales.date"));
        party(out, "Customer", true,
                List.of("party.name", "customer.name"),
                List.of("party.billingAddress", "sales.billingAddress", "customer.address"),
                List.of("party.billingGstin", "sales.billingGstin", "sales.gstin", "customer.gstin"));
        itemTable(out, true);
        itemCore(out, true, true);
        total(out);
        out.add(field("TAX_SUMMARY", "GST / IGST Tax Summary", "Tax", Level.REQUIRED,
                List.of("totals.breakdownAmounts", "totals.gstAmount", "totals.cgstAmount", "totals.sgstAmount", "totals.igstAmount", "tax.primaryAmount"),
                aliases("gst", "igst", "cgst", "sgst", "tax amount", "tax summary"),
                "The default Sales template must be able to print both intrastate GST and interstate IGST totals.",
                "Map the Tax Summary / GST amount fields, or use the dynamic totals block."));
        out.add(field("AMOUNT_WORDS", "Amount in Words", "Totals", Level.RECOMMENDED,
                List.of("totals.amountInWords", "totals.amountInWordsText"), aliases("words", "amount in words"),
                "Shows the invoice total in words.", "Map Totals → Amount in Words."));
        out.add(field("DELIVERY_ADDRESS", "Delivery / Ship To Address", "Customer", Level.RECOMMENDED,
                List.of("party.deliveryAddress", "sales.deliveryAddress", "sales.shippingAddress", "customer.address"),
                aliases("ship to", "shipping", "delivery address"), "Shows the delivery destination when it differs from billing.",
                "Map Party → Delivery Address or Shipping Address."));
    }

    private static void purchase(List<TemplateMappingRequirement> out) {
        document(out, "Purchase Invoice", List.of("document.number", "purchase.number"), List.of("document.date", "purchase.date"));
        party(out, "Supplier", true,
                List.of("party.name", "supplier.name"),
                List.of("party.billingAddress", "purchase.billingAddress", "supplier.address", "party.address"),
                List.of("party.billingGstin", "purchase.billingGstin", "supplier.gstin", "party.gstin"));
        itemTable(out, true);
        itemCore(out, true, true);
        total(out);
        out.add(field("TAX_SUMMARY", "GST / IGST Tax Summary", "Tax", Level.REQUIRED,
                List.of("totals.breakdownAmounts", "totals.gstAmount", "totals.cgstAmount", "totals.sgstAmount", "totals.igstAmount", "tax.primaryAmount"),
                aliases("gst", "igst", "cgst", "sgst", "tax amount"),
                "The Purchase template must be able to show the tax charged by the supplier.",
                "Map the Tax Summary / GST amount fields."));
        out.add(field("SUPPLIER_REFERENCE", "Supplier Invoice / Reference", "Document", Level.RECOMMENDED,
                List.of("document.referenceNumber", "document.orderNumber", "purchase.orderNo", "purchase.referenceNo"),
                aliases("supplier invoice", "supplier reference", "vendor invoice", "reference"),
                "Shows the supplier's own invoice or reference number.", "Map Document → Reference Number or Purchase → Supplier Reference."));
    }

    private static void quotation(List<TemplateMappingRequirement> out) {
        document(out, "Quotation", List.of("document.number", "quotation.number"), List.of("document.date", "quotation.date"));
        party(out, "Customer", false,
                List.of("party.name", "customer.name"),
                List.of("party.billingAddress", "customer.address", "party.address"),
                List.of("party.billingGstin", "customer.gstin", "party.gstin"));
        itemTable(out, true);
        itemCore(out, false, true);
        total(out);
        out.add(field("VALID_UNTIL", "Valid Until", "Document", Level.RECOMMENDED,
                List.of("document.validUntil", "quotation.validUntil"), aliases("validity", "expiry", "valid until"),
                "Tells the customer how long the quotation is valid.", "Map Quotation → Valid Until."));
        out.add(field("TAX_SUMMARY", "Tax Summary", "Tax", Level.CONDITIONAL,
                List.of("totals.breakdownAmounts", "totals.gstAmount", "totals.cgstAmount", "totals.sgstAmount", "totals.igstAmount", "tax.primaryAmount"),
                aliases("gst", "igst", "tax"), "Required when quotation prices include GST/IGST.",
                "Map Tax Summary fields when this quotation format displays tax."));
    }

    private static void salesReturn(List<TemplateMappingRequirement> out) {
        returnBase(out, "Sales Return", true);
    }

    private static void purchaseReturn(List<TemplateMappingRequirement> out) {
        returnBase(out, "Purchase Return", true);
    }

    private static void returnBase(List<TemplateMappingRequirement> out, String label, boolean taxRequired) {
        document(out, label, List.of("document.number", "return.number"), List.of("document.date", "return.date"));
        out.add(field("ORIGINAL_DOCUMENT", "Original Document Number", "Document", Level.REQUIRED,
                List.of("document.referenceNumber", "document.orderNumber", "return.referenceNo"),
                aliases("original invoice", "original purchase", "source invoice", "reference"),
                "A return must identify the original Sales or Purchase document being reversed.",
                "Map Return → Reference Number or Document → Reference Number."));
        party(out, "Party", true,
                List.of("party.name"), List.of("party.address"), List.of("party.gstin"));
        itemTable(out, true);
        itemCore(out, taxRequired, true);
        total(out);
        out.add(field("RETURN_REASON", "Return Reason", "Return", Level.RECOMMENDED,
                List.of("document.reason", "return.reason"), aliases("reason", "return note", "remarks"),
                "Explains why the goods or value are being returned.", "Map Return → Reason / Remarks."));
        if (taxRequired) out.add(field("TAX_SUMMARY", "Tax Reversal Summary", "Tax", Level.REQUIRED,
                List.of("totals.breakdownAmounts", "totals.gstAmount", "totals.cgstAmount", "totals.sgstAmount", "totals.igstAmount", "tax.primaryAmount"),
                aliases("gst reversal", "igst reversal", "tax refund", "tax"),
                "Return documents must show the GST/IGST value being reversed.",
                "Map the GST/IGST tax summary fields."));
    }

    private static void delivery(List<TemplateMappingRequirement> out) {
        document(out, "Delivery Challan", List.of("document.number", "delivery.number"), List.of("document.date", "delivery.date"));
        party(out, "Customer", false, List.of("party.name", "customer.name"), List.of("delivery.address", "customer.address", "party.address"), List.of("customer.gstin", "party.gstin"));
        itemTable(out, true);
        itemCore(out, false, false);
    }

    private static void receipt(List<TemplateMappingRequirement> out) {
        document(out, "Payment Receipt", List.of("document.number", "receipt.number"), List.of("document.date", "receipt.date"));
        out.add(field("PARTY_NAME", "Party Name", "Party", Level.REQUIRED,
                List.of("party.name", "receipt.partyName"), aliases("customer", "supplier", "party"),
                "Identifies who made or received the payment.", "Map Receipt → Party Name."));
        out.add(field("GRAND_TOTAL", "Receipt Amount", "Totals", Level.REQUIRED,
                List.of("totals.grandTotal", "receipt.amount"), aliases("amount", "receipt total"),
                "Shows the payment amount.", "Map Receipt → Amount."));
    }

    private static void document(List<TemplateMappingRequirement> out, String documentLabel,
                                 List<String> numberFields, List<String> dateFields) {
        out.add(field("DOCUMENT_NUMBER", documentLabel + " Number", "Document", Level.REQUIRED, numberFields,
                aliases("invoice no", "document no", "number", "reference number"),
                "Every final document needs its ERP document number.",
                "Map Document → Number."));
        out.add(field("DOCUMENT_DATE", documentLabel + " Date", "Document", Level.REQUIRED, dateFields,
                aliases("invoice date", "document date", "date"),
                "Every final document needs its ERP transaction date.",
                "Map Document → Date."));
    }

    private static void party(List<TemplateMappingRequirement> out, String partyLabel, boolean taxDocument,
                              List<String> nameFields, List<String> addressFields, List<String> gstinFields) {
        out.add(field("PARTY_NAME", partyLabel + " Name", partyLabel, Level.REQUIRED, nameFields,
                aliases("bill to", "ship to", "customer", "supplier", "party name"),
                "Identifies the customer or supplier on the document.", "Map " + partyLabel + " → Name."));
        out.add(field("PARTY_ADDRESS", partyLabel + " Address", partyLabel, taxDocument ? Level.REQUIRED : Level.RECOMMENDED, addressFields,
                aliases("billing address", "bill to address", "supplier address", "customer address"),
                "Prints the party address used for this document.", "Map " + partyLabel + " → Address and enable Wrap / Auto Height for long addresses."));
        out.add(field("PARTY_GSTIN", partyLabel + " GSTIN", partyLabel, taxDocument ? Level.REQUIRED : Level.RECOMMENDED, gstinFields,
                aliases("gst no", "gst number", "tax id", "gstin"),
                "Prints the party GST registration number.", "Map " + partyLabel + " → GSTIN."));
    }

    private static void itemTable(List<TemplateMappingRequirement> out, boolean required) {
        out.add(structure("ITEM_TABLE", "Repeating Item Table", "Item Table", required ? Level.REQUIRED : Level.RECOMMENDED,
                "All transaction rows must flow through one repeating table so single and multi-page documents use the same page rule.",
                "Add an Item Table and enable the repeating page flow."));
    }

    private static void itemCore(List<TemplateMappingRequirement> out, boolean hsnRequired, boolean amountRequired) {
        out.add(item("PRODUCT_DESCRIPTION", "Product Description", "Item Table", Level.REQUIRED,
                List.of("item.description", "item.descriptionWithRemarks", "item.remarks"),
                aliases("product", "description", "remark", "item name"),
                "Choose how the product is described. Description, Description + Remarks, and the legacy Remarks column all satisfy this requirement.",
                "Map Item Table → Product Description to Description or Description + Item Remarks."));
        out.add(item("HSN_SAC", "HSN / SAC", "Item Table", hsnRequired ? Level.REQUIRED : Level.CONDITIONAL,
                List.of("item.hsn"), aliases("hsn", "sac", "commodity code"),
                "HSN/SAC identifies the tax classification of each item.",
                "Map Item Table → HSN / SAC to Item → HSN / SAC."));
        out.add(item("QUANTITY", "Quantity", "Item Table", Level.REQUIRED,
                List.of("item.quantity", "item.qty"), aliases("qty", "quantity"),
                "Shows the transaction quantity for each line.", "Map Item Table → Quantity."));
        out.add(item("RATE", "Rate", "Item Table", Level.REQUIRED,
                List.of("item.rate"), aliases("price", "unit price", "rate"),
                "Shows the ERP transaction rate for each item.", "Map Item Table → Rate."));
        out.add(item("LINE_AMOUNT", "Line Amount", "Item Table", amountRequired ? Level.REQUIRED : Level.RECOMMENDED,
                List.of("item.total", "item.grossAmount", "item.taxable", "item.amount"),
                aliases("amount", "line total", "gross amount", "taxable amount"),
                "Shows the calculated amount for each item row.", "Map Item Table → Amount / Line Total."));
    }

    private static void total(List<TemplateMappingRequirement> out) {
        out.add(field("GRAND_TOTAL", "Grand Total", "Totals", Level.REQUIRED,
                List.of("totals.grandTotal", "totals.roundedGrandTotal"), aliases("total", "grand total", "net amount", "invoice total"),
                "Shows the authoritative final document total.", "Map Totals → Grand Total (Rounded or Unrounded as configured by ERP)."));
    }

    private static TemplateMappingRequirement field(String id, String label, String category, Level level,
                                                     List<String> accepted, List<String> aliases, String why, String fix) {
        return new TemplateMappingRequirement(id, label, category, level, Kind.FIELD, accepted, aliases, why, fix);
    }

    private static TemplateMappingRequirement item(String id, String label, String category, Level level,
                                                    List<String> accepted, List<String> aliases, String why, String fix) {
        return new TemplateMappingRequirement(id, label, category, level, Kind.ITEM_COLUMN, accepted, aliases, why, fix);
    }

    private static TemplateMappingRequirement structure(String id, String label, String category, Level level,
                                                         String why, String fix) {
        return new TemplateMappingRequirement(id, label, category, level, Kind.STRUCTURE, List.of(), List.of(), why, fix);
    }

    private static List<String> aliases(String... values) { return List.of(values); }
}
