package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateCharge;
import org.example.documentstudio.model.TemplateData;
import org.example.invoice.model.TaxInvoiceItem;

import java.util.Map;

/**
 * Stable JSON contract between ERP business data and PDF Studio.
 *
 * <p>Every ERP template receives the same preferred top-level contract: {@code document.*},
 * {@code party.*}, {@code transport.*}, {@code tax.*}, {@code totals.*}, {@code items[]} and
 * {@code charges[]}. Legacy document-specific keys such as {@code sales.*}, {@code purchase.*},
 * {@code quotation.*} and {@code return.*} are preserved for backward compatibility.</p>
 */
public final class ErpDocumentJsonService {
    /** Additive V2 contract introduced in 9.0.61. Existing V1 template keys remain valid. */
    public static final int SCHEMA_VERSION = 2;
    private static final ObjectMapper JSON = new ObjectMapper();

    private ErpDocumentJsonService() { }

    public static ObjectNode toJson(DocumentType type, TemplateData data) {
        TemplateData source = data == null
                ? new TemplateData(Map.of(), Map.of(), java.util.List.of(), java.util.List.of(), "")
                : data;
        DocumentType resolved = type == null ? DocumentType.CUSTOM_ERP : type;
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("documentType", resolved.name());
        root.put("documentTypeLabel", resolved.label());

        // Preserve every legacy stable ERP key in nested JSON form first.
        source.values().forEach((key, value) -> putPath(root, key, value));

        // Add the universal JSON aliases without removing any legacy mapping.
        switch (resolved) {
            case SALES_INVOICE -> addSalesAliases(root, source);
            case PURCHASE_INVOICE, PURCHASE_ORDER -> addPurchaseAliases(root, source);
            case PURCHASE_RETURN, SALES_RETURN, CREDIT_NOTE, DEBIT_NOTE -> addReturnAliases(root, source);
            case QUOTATION -> addQuotationAliases(root, source);
            case DELIVERY_CHALLAN -> addDeliveryAliases(root, source);
            case PAYMENT_RECEIPT -> addReceiptAliases(root, source);
            case CUSTOM_ERP, GENERAL_PDF -> { }
        }
        addCommonFinancialAliases(root, source);

        ArrayNode items = root.putArray("items");
        for (TaxInvoiceItem item : source.items()) {
            if (item == null) continue;
            ObjectNode n = items.addObject();
            n.put("serial", item.getSerialNo());
            n.put("code", safe(item.getItemCode()));
            n.put("hsn", safe(item.getHsn()));
            n.put("description", safe(item.getDescription()));
            n.put("remarks", safe(item.getRemarks()));
            n.put("quantity", item.getQuantity());
            n.put("unit", safe(item.getUnit()));
            n.put("rate", item.getRate());
            n.put("discountPercent", item.getDiscountPercent());
            n.put("discountAmount", item.getDiscountAmount());
            n.put("gstPercent", item.getGstPercent());
            n.put("taxableAmount", item.getTaxableAmount());
            n.put("gstAmount", item.getTaxAmount());
            n.put("amount", item.getTotalAmount());
        }

        ArrayNode charges = root.putArray("charges");
        for (TemplateCharge charge : source.charges()) {
            if (charge == null) continue;
            ObjectNode n = charges.addObject();
            n.put("type", safe(charge.type()));
            n.put("amount", charge.amount());
            n.put("taxable", charge.taxable());
            n.put("gstPercent", charge.gstPercent());
        }
        root.put("gstType", source.gstType());
        return root;
    }

    /** Round-trips through JSON so the renderer consumes exactly the same field contract shown in PDF Studio. */
    public static TemplateData normalize(DocumentType type, TemplateData data) {
        return JsonTemplateDataAdapter.fromJson(toJson(type, data), data);
    }

    public static String pretty(DocumentType type, TemplateData data) {
        try { return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(toJson(type, data)); }
        catch (Exception error) { return "{}"; }
    }

    private static void addSalesAliases(ObjectNode root, TemplateData data) {
        alias(root, data, "document.number", "sales.number");
        alias(root, data, "document.date", "sales.date");
        alias(root, data, "document.dueDate", "sales.dueDate");
        alias(root, data, "document.referenceNumber", "sales.referenceNo");
        alias(root, data, "document.poNumber", "sales.referenceNo");
        alias(root, data, "document.orderNumber", "sales.orderNo");
        alias(root, data, "document.poDate", "sales.poDate");
        alias(root, data, "document.paymentTerms", "sales.paymentTerms");
        alias(root, data, "document.status", "sales.documentStatus");
        alias(root, data, "document.paymentStatus", "sales.paymentStatus");
        alias(root, data, "document.notes", "sales.notes");
        alias(root, data, "document.remarks", "sales.remarks");

        aliasParty(root, data, "customer");
        alias(root, data, "party.billingAddress", "sales.billingAddress");
        alias(root, data, "party.billingGstin", "sales.billingGstin");
        alias(root, data, "party.deliveryAddress", "sales.deliveryAddress");
        alias(root, data, "party.deliveryGstin", "sales.deliveryGstin");

        alias(root, data, "transport.name", "sales.transporter");
        alias(root, data, "transport.gstin", "sales.transporterGstin");
        alias(root, data, "transport.contact", "sales.contactMobile");
        alias(root, data, "transport.contactPerson", "sales.contactPerson");
        alias(root, data, "transport.vehicleNumber", "sales.vehicleNo");
        alias(root, data, "transport.note", "sales.transportNote");
    }

    private static void addPurchaseAliases(ObjectNode root, TemplateData data) {
        alias(root, data, "document.number", "purchase.number");
        alias(root, data, "document.date", "purchase.date");
        alias(root, data, "document.dueDate", "purchase.dueDate");
        alias(root, data, "document.deliveryDate", "purchase.deliveryDate");
        alias(root, data, "document.referenceNumber", "purchase.referenceNo");
        alias(root, data, "document.poNumber", "purchase.orderNo");
        alias(root, data, "document.orderNumber", "purchase.orderNo");
        alias(root, data, "document.poDate", "purchase.poDate");
        alias(root, data, "document.paymentTerms", "purchase.paymentTerms");
        alias(root, data, "document.status", "purchase.documentStatus");
        alias(root, data, "document.paymentStatus", "purchase.paymentStatus");
        alias(root, data, "document.notes", "purchase.notes");
        alias(root, data, "document.remarks", "purchase.remarks");

        aliasParty(root, data, "supplier");
        alias(root, data, "party.billingAddress", "purchase.billingAddress");
        alias(root, data, "party.billingGstin", "purchase.billingGstin");
        alias(root, data, "party.deliveryAddress", "purchase.deliveryAddress");
        alias(root, data, "party.deliveryGstin", "purchase.deliveryGstin");

        alias(root, data, "transport.name", "purchase.transporter");
        alias(root, data, "transport.gstin", "purchase.transporterGstin");
        alias(root, data, "transport.contact", "purchase.contactMobile");
        alias(root, data, "transport.contactPerson", "purchase.contactPerson");
        alias(root, data, "transport.vehicleNumber", "purchase.vehicleNo");
        alias(root, data, "transport.lrAwbNumber", "purchase.lrAwbNo");
    }

    private static void addReturnAliases(ObjectNode root, TemplateData data) {
        alias(root, data, "document.number", "return.number");
        alias(root, data, "document.date", "return.date");
        alias(root, data, "document.referenceNumber", "return.referenceNo");
        alias(root, data, "document.reason", "return.reason");
        // Return TemplateData already uses party.*; copy common optional values only when present.
        alias(root, data, "party.name", "party.name");
        alias(root, data, "party.address", "party.address");
        alias(root, data, "party.gstin", "party.gstin");
        alias(root, data, "party.phone", "party.phone");
        alias(root, data, "party.email", "party.email");
    }

    private static void addQuotationAliases(ObjectNode root, TemplateData data) {
        alias(root, data, "document.number", "quotation.number");
        alias(root, data, "document.date", "quotation.date");
        alias(root, data, "document.validUntil", "quotation.validUntil");
        alias(root, data, "document.status", "quotation.status");
        alias(root, data, "document.salesperson", "quotation.salesperson");
        alias(root, data, "document.source", "quotation.source");
        alias(root, data, "document.remarks", "quotation.remarks");
        aliasParty(root, data, "customer");
    }

    private static void addDeliveryAliases(ObjectNode root, TemplateData data) {
        alias(root, data, "document.number", "delivery.number");
        alias(root, data, "document.date", "delivery.date");
        aliasParty(root, data, "customer");
        alias(root, data, "party.deliveryAddress", "delivery.address");
        alias(root, data, "transport.name", "delivery.transporter");
        alias(root, data, "transport.vehicleNumber", "delivery.vehicleNo");
    }

    private static void addReceiptAliases(ObjectNode root, TemplateData data) {
        alias(root, data, "document.number", "receipt.number");
        alias(root, data, "document.date", "receipt.date");
        alias(root, data, "document.referenceNumber", "receipt.reference");
        alias(root, data, "document.notes", "receipt.notes");
        alias(root, data, "party.name", "receipt.partyName");
        alias(root, data, "totals.grandTotal", "receipt.amount");
    }

    private static void aliasParty(ObjectNode root, TemplateData data, String prefix) {
        alias(root, data, "party.code", prefix + ".code");
        alias(root, data, "party.name", prefix + ".name");
        alias(root, data, "party.address", prefix + ".address");
        alias(root, data, "party.gstin", prefix + ".gstin");
        alias(root, data, "party.phone", prefix + ".phone");
        alias(root, data, "party.email", prefix + ".email");
        alias(root, data, "party.contactPerson", prefix + ".contactPerson");
    }

    private static void addCommonFinancialAliases(ObjectNode root, TemplateData data) {
        alias(root, data, "totals.basicAmount", "totals.subtotal");
        aliasFirst(root, data, "totals.taxableAmount", "totals.grossBeforeTax", "totals.subtotal");
        putPath(root, "totals.freight", freightAmount(data));
        String words = data.value("totals.amountInWords");
        if (words != null && !words.isBlank())
            putPath(root, "totals.amountInWordsText", words.replaceFirst("(?i)^\\s*INR\\s*:\\s*", ""));

        double rate = singleGstRate(data);
        String taxMode = data.gstType() == null ? "" : data.gstType().toUpperCase(java.util.Locale.ROOT);
        if (taxMode.contains("IGST")) {
            String label = rate > 0 ? "IGST @ " + percent(rate) + "%" : "IGST";
            putPath(root, "tax.igstLabel", label);
            putPath(root, "tax.primaryLabel", label);
            alias(root, data, "tax.primaryAmount", "totals.igstAmount");
            putPath(root, "tax.secondaryLabel", "");
            putPath(root, "tax.secondaryAmount", "");
        } else {
            String cgst = rate > 0 ? "CGST @ " + percent(rate / 2d) + "%" : "CGST";
            String sgst = rate > 0 ? "SGST @ " + percent(rate / 2d) + "%" : "SGST";
            putPath(root, "tax.cgstLabel", cgst);
            putPath(root, "tax.sgstLabel", sgst);
            putPath(root, "tax.primaryLabel", cgst);
            alias(root, data, "tax.primaryAmount", "totals.cgstAmount");
            putPath(root, "tax.secondaryLabel", sgst);
            alias(root, data, "tax.secondaryAmount", "totals.sgstAmount");
        }
    }

    private static String freightAmount(TemplateData data) {
        double freight = 0d;
        for (TemplateCharge charge : data.charges()) {
            if (charge == null || charge.type() == null) continue;
            String type = charge.type().trim().toUpperCase(java.util.Locale.ROOT);
            if (type.contains("FREIGHT")) freight += charge.amount();
        }
        return String.format(java.util.Locale.ENGLISH, "%,.2f", freight);
    }

    private static double singleGstRate(TemplateData data) {
        java.util.LinkedHashSet<Double> rates = new java.util.LinkedHashSet<>();
        for (TaxInvoiceItem item : data.items()) if (item != null && item.getGstPercent() > 0) rates.add(item.getGstPercent());
        return rates.size() == 1 ? rates.iterator().next() : 0d;
    }

    private static String percent(double value) {
        if (Math.rint(value) == value) return Long.toString(Math.round(value));
        return String.format(java.util.Locale.ENGLISH, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static void alias(ObjectNode root, TemplateData data, String target, String source) {
        String value = data.value(source);
        if (value != null && !value.isBlank()) putPath(root, target, value);
    }

    private static void aliasFirst(ObjectNode root, TemplateData data, String target, String... sources) {
        for (String source : sources) {
            String value = data.value(source);
            if (value != null && !value.isBlank()) {
                putPath(root, target, value);
                return;
            }
        }
    }

    private static void putPath(ObjectNode root, String path, String value) {
        if (path == null || path.isBlank()) return;
        String[] parts = path.split("\\.");
        ObjectNode cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode existing = cursor.get(parts[i]);
            if (existing instanceof ObjectNode object) cursor = object;
            else cursor = cursor.putObject(parts[i]);
        }
        cursor.put(parts[parts.length - 1], value == null ? "" : value);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
