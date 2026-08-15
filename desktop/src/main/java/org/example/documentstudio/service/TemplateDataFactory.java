package org.example.documentstudio.service;

import org.example.api.quotation.QuotationApiClient;
import org.example.config.ConfigManager;
import org.example.dao.ItemDAO;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateData;
import org.example.invoice.calculation.AmountInWordsConverter;
import org.example.invoice.model.TaxInvoiceItem;
import org.example.model.Item;
import org.example.model.Party;
import org.example.model.Purchase;
import org.example.model.PurchaseLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts ERP records into stable universal Document Studio field keys. */
public final class TemplateDataFactory {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private TemplateDataFactory() {}

    public static TemplateData fromPurchase(Purchase purchase) {
        if (purchase == null) throw new IllegalArgumentException("Purchase invoice is required.");
        Map<String, String> v = new LinkedHashMap<>();
        Map<String, Path> images = new LinkedHashMap<>();
        addCompanyAndPayment(v, images);

        put(v, "purchase.number", purchase.getInvoiceNo());
        put(v, "purchase.date", formatDate(purchase.getInvoiceDate()));
        put(v, "purchase.dueDate", formatDate(purchase.getDueDate()));
        put(v, "purchase.deliveryDate", formatDate(purchase.getDeliveryDate()));
        put(v, "purchase.referenceNo", purchase.getReferenceNo());
        put(v, "purchase.paymentTerms", purchase.getPaymentTerms());
        put(v, "purchase.gstTreatment", purchase.getGstTreatment());
        put(v, "purchase.warehouse", purchase.getWarehouse());
        put(v, "purchase.currency", purchase.getCurrency());
        put(v, "purchase.transporter", purchase.getTransporter());
        put(v, "purchase.lrAwbNo", purchase.getLrAwbNo());
        put(v, "purchase.remarks", purchase.getRemarks());
        put(v, "purchase.createdBy", purchase.getCreatedBy());
        put(v, "purchase.documentStatus", purchase.getDocumentStatus());
        put(v, "purchase.paymentStatus", purchase.getPaymentStatus());
        party(v, purchase.getSupplier(), "supplier");

        put(v, "totals.subtotal", money(purchase.getSubtotal()));
        put(v, "totals.discountAmount", money(purchase.getDiscountAmount()));
        put(v, "totals.gstAmount", money(purchase.getGstAmount()));
        put(v, "totals.grandTotal", money(purchase.getTotalAmount()));
        put(v, "totals.paidAmount", money(purchase.getPaidAmount()));
        put(v, "totals.balanceAmount", money(purchase.getBalanceAmount()));
        put(v, "totals.amountInWords", "INR : " + AmountInWordsConverter.indianRupees(purchase.getTotalAmount()));
        return new TemplateData(v, images, purchaseItems(purchase), safe(purchase.getGstTreatment()));
    }

    public static TemplateData fromQuotation(QuotationApiClient.QuoteDto quote, List<QuotationApiClient.LineDto> lines) {
        if (quote == null) throw new IllegalArgumentException("Quotation is required.");
        Map<String, String> v = new LinkedHashMap<>();
        Map<String, Path> images = new LinkedHashMap<>();
        addCompanyAndPayment(v, images);

        put(v, "quotation.number", quote.no());
        put(v, "quotation.date", displayDate(quote.date()));
        put(v, "quotation.validUntil", displayDate(quote.valid()));
        put(v, "quotation.status", quote.status());
        put(v, "quotation.salesperson", quote.salesperson());
        put(v, "quotation.source", quote.source());
        put(v, "quotation.remarks", quote.remarks());
        put(v, "customer.name", quote.customer());
        put(v, "customer.gstin", quote.gstin());
        put(v, "customer.phone", quote.phone());
        put(v, "customer.email", quote.email());
        put(v, "customer.address", "");

        List<TaxInvoiceItem> items = quotationItems(lines);
        double gross = 0, discount = 0, gst = 0;
        for (TaxInvoiceItem item : items) {
            gross += item.getGrossAmount();
            discount += item.getDiscountAmount();
            gst += item.getTaxAmount();
        }
        double taxable = Math.max(0, gross - discount);
        double total = quote.amount() > 0 ? quote.amount() : taxable + gst;
        put(v, "totals.subtotal", money(taxable));
        put(v, "totals.discountAmount", money(quote.discount() > 0 ? quote.discount() : discount));
        put(v, "totals.gstAmount", money(gst));
        put(v, "totals.grandTotal", money(total));
        put(v, "totals.paidAmount", "0.00");
        put(v, "totals.balanceAmount", money(total));
        put(v, "totals.amountInWords", "INR : " + AmountInWordsConverter.indianRupees(total));
        return new TemplateData(v, images, items, "Registered Business");
    }

    /** Sample data for any supported document type; used by Design Preview. */
    public static TemplateData sampleFor(DocumentType type) {
        if (type == null) type = DocumentType.GENERAL_PDF;
        return switch (type) {
            case GENERAL_PDF -> new TemplateData(Map.of(), Map.of(), List.of(), "");
            case PURCHASE_INVOICE, PURCHASE_ORDER -> samplePurchase();
            case QUOTATION -> sampleQuotation();
            case DELIVERY_CHALLAN -> sampleDelivery();
            case CREDIT_NOTE, DEBIT_NOTE, SALES_RETURN -> sampleReturn(type);
            case PAYMENT_RECEIPT -> sampleReceipt();
            case SALES_INVOICE -> sampleSales();
            case CUSTOM_ERP -> sampleCustom();
        };
    }

    /** Backward-compatible sample retained for existing code and saved templates. */
    public static TemplateData samplePurchase() {
        Map<String, String> v = commonSample();
        Map<String, Path> images = configuredImages();
        v.put("purchase.number", "PINV-2026-00125");
        v.put("purchase.date", "15-08-2026");
        v.put("purchase.dueDate", "14-09-2026");
        v.put("purchase.deliveryDate", "18-08-2026");
        v.put("purchase.referenceNo", "SUP-REF-4587");
        v.put("purchase.paymentTerms", "30 Days");
        v.put("purchase.gstTreatment", "Registered Business");
        v.put("purchase.warehouse", "Main Warehouse");
        v.put("purchase.currency", "INR - Indian Rupee");
        v.put("purchase.transporter", "Local Transport");
        v.put("purchase.lrAwbNo", "LR-10245");
        v.put("purchase.remarks", "Material received subject to inspection.");
        v.put("purchase.createdBy", "Admin");
        v.put("purchase.documentStatus", "COMPLETED");
        v.put("purchase.paymentStatus", "PENDING");
        v.put("supplier.code", "SUP-001");
        v.put("supplier.name", "ABC Components Pvt Ltd");
        v.put("supplier.address", "Industrial Estate, Ahmedabad, Gujarat");
        v.put("supplier.gstin", "24AABCA1234A1Z5");
        v.put("supplier.contactPerson", "Accounts Department");
        v.put("supplier.phone", "+91 90000 00000");
        v.put("supplier.email", "accounts@supplier.example");
        addSampleTotals(v, 29800, 0, 5364, 35164);
        return new TemplateData(v, images, sampleItems(), "Registered Business");
    }

    private static TemplateData sampleQuotation() {
        Map<String, String> v = commonSample();
        Map<String, Path> images = configuredImages();
        v.put("quotation.number", "QUO-2026-00108");
        v.put("quotation.date", "15-08-2026");
        v.put("quotation.validUntil", "29-08-2026");
        v.put("quotation.status", "DRAFT");
        v.put("quotation.salesperson", "Admin");
        v.put("quotation.source", "Direct");
        v.put("quotation.remarks", "Thank you for the opportunity to quote.");
        v.put("customer.name", "ABC Engineering Pvt Ltd");
        v.put("customer.address", "Ahmedabad, Gujarat");
        v.put("customer.gstin", "24ABCDE1234F1Z5");
        v.put("customer.phone", "+91 98765 43210");
        v.put("customer.email", "purchase@abcengineering.example");
        addSampleTotals(v, 29800, 0, 5364, 35164);
        return new TemplateData(v, images, sampleItems(), "Registered Business");
    }

    private static TemplateData sampleDelivery() {
        Map<String, String> v = commonSample();
        Map<String, Path> images = configuredImages();
        v.put("delivery.number", "DC-2026-0042");
        v.put("delivery.date", "15-08-2026");
        v.put("delivery.vehicleNo", "GJ-01-AB-1234");
        v.put("delivery.transporter", "Local Transport");
        v.put("delivery.address", "ABC Engineering Pvt Ltd, Ahmedabad");
        v.put("customer.name", "ABC Engineering Pvt Ltd");
        v.put("customer.address", "Ahmedabad, Gujarat");
        v.put("customer.gstin", "24ABCDE1234F1Z5");
        return new TemplateData(v, images, sampleItems(), "");
    }

    private static TemplateData sampleReturn(DocumentType type) {
        Map<String, String> v = commonSample();
        Map<String, Path> images = configuredImages();
        v.put("return.number", type == DocumentType.CREDIT_NOTE ? "CN-2026-0012" : type == DocumentType.DEBIT_NOTE ? "DN-2026-0012" : "RET-2026-0012");
        v.put("return.date", "15-08-2026");
        v.put("return.referenceNo", "INV-2026-0104");
        v.put("return.reason", "Material return / adjustment");
        v.put("party.name", "ABC Engineering Pvt Ltd");
        v.put("party.address", "Ahmedabad, Gujarat");
        v.put("party.gstin", "24ABCDE1234F1Z5");
        addSampleTotals(v, 5000, 0, 900, 5900);
        return new TemplateData(v, images, sampleItems(), "Registered Business");
    }

    private static TemplateData sampleReceipt() {
        Map<String, String> v = commonSample();
        Map<String, Path> images = configuredImages();
        v.put("receipt.number", "RCPT-2026-0088");
        v.put("receipt.date", "15-08-2026");
        v.put("receipt.partyName", "ABC Engineering Pvt Ltd");
        v.put("receipt.amount", "35,164.00");
        v.put("receipt.reference", "INV-2026-0125");
        v.put("receipt.notes", "Payment received by bank transfer.");
        return new TemplateData(v, images, List.of(), "");
    }

    private static TemplateData sampleSales() {
        Map<String, String> v = commonSample();
        Map<String, Path> images = configuredImages();
        v.put("sales.number", "INV-2026-00125");
        v.put("sales.date", "15-08-2026");
        v.put("sales.dueDate", "14-09-2026");
        v.put("customer.name", "ABC Engineering Pvt Ltd");
        v.put("customer.address", "Ahmedabad, Gujarat");
        v.put("customer.gstin", "24ABCDE1234F1Z5");
        v.put("customer.phone", "+91 98765 43210");
        v.put("customer.email", "accounts@abcengineering.example");
        addSampleTotals(v, 29800, 0, 5364, 35164);
        return new TemplateData(v, images, sampleItems(), "Registered Business");
    }

    private static TemplateData sampleCustom() {
        Map<String, String> v = commonSample();
        return new TemplateData(v, configuredImages(), sampleItems(), "");
    }

    private static Map<String, String> commonSample() {
        Map<String, String> v = new LinkedHashMap<>();
        Map<String, Path> ignored = new LinkedHashMap<>();
        addCompanyAndPayment(v, ignored);
        return v;
    }

    private static Map<String, Path> configuredImages() {
        Map<String, String> ignored = new LinkedHashMap<>();
        Map<String, Path> images = new LinkedHashMap<>();
        addCompanyAndPayment(ignored, images);
        return images;
    }

    private static void addSampleTotals(Map<String, String> v, double subtotal, double discount, double gst, double total) {
        v.put("totals.subtotal", money(subtotal));
        v.put("totals.discountAmount", money(discount));
        v.put("totals.gstAmount", money(gst));
        v.put("totals.grandTotal", money(total));
        v.put("totals.paidAmount", "0.00");
        v.put("totals.balanceAmount", money(total));
        v.put("totals.amountInWords", "INR : " + AmountInWordsConverter.indianRupees(total));
    }

    private static List<TaxInvoiceItem> sampleItems() {
        return List.of(
                new TaxInvoiceItem(1, "8483", "Motor Assembly", "", 2, "NOS", 12500, 0, 18),
                new TaxInvoiceItem(2, "8482", "Bearing Set", "", 4, "NOS", 1200, 0, 18)
        );
    }

    private static void addCompanyAndPayment(Map<String, String> v, Map<String, Path> images) {
        put(v, "company.name", ConfigManager.get("company.name", ""));
        put(v, "company.address", ConfigManager.get("company.address", ""));
        put(v, "company.gstin", ConfigManager.get("company.gstin", ""));
        put(v, "company.phone", ConfigManager.get("company.phone", ""));
        put(v, "company.email", ConfigManager.get("company.email", ""));
        put(v, "company.alternateEmail", ConfigManager.get("company.alternateEmail", ""));
        put(v, "company.certification", ConfigManager.get("company.certificationText", "AN ISO 9001 : 2015 COMPANY"));
        put(v, "company.terms", ConfigManager.get("company.terms", ""));
        put(v, "payment.bankName", ConfigManager.get("payment.bankName", ""));
        put(v, "payment.branch", ConfigManager.get("payment.branch", ""));
        put(v, "payment.accountNumber", ConfigManager.get("payment.accountNumber", ""));
        put(v, "payment.ifsc", ConfigManager.get("payment.ifsc", ""));
        put(v, "payment.accountType", ConfigManager.get("payment.accountType", ""));
        put(v, "payment.mode", ConfigManager.get("payment.mode", ""));
        Path logo = resolveConfiguredAsset("company.logoPath", "logo.png");
        if (logo != null) images.put("company.logo", logo);
        Path signature = resolveConfiguredAsset("company.signaturePath", null);
        if (signature != null) images.put("company.signature", signature);
    }

    private static void party(Map<String, String> v, Party p, String prefix) {
        if (p == null) return;
        put(v, prefix + ".code", p.getPartyCode());
        put(v, prefix + ".name", p.getName());
        put(v, prefix + ".address", p.getAddress());
        put(v, prefix + ".gstin", p.getGstin());
        put(v, prefix + ".contactPerson", p.getContactPerson());
        put(v, prefix + ".phone", p.getPhone());
        put(v, prefix + ".email", p.getEmail());
    }

    private static List<TaxInvoiceItem> purchaseItems(Purchase purchase) {
        Map<String, Item> itemByCode = new HashMap<>();
        try {
            for (Item item : new ItemDAO().getAll()) {
                if (item != null && item.getItemCode() != null) itemByCode.put(normalize(item.getItemCode()), item);
            }
        } catch (Exception ignored) { }
        List<TaxInvoiceItem> items = new ArrayList<>();
        int serial = 1;
        for (PurchaseLine line : purchase.getLines() == null ? List.<PurchaseLine>of() : purchase.getLines()) {
            if (line == null) continue;
            Item master = itemByCode.get(normalize(line.getItemCode()));
            String hsn = master == null ? "" : safe(master.getHsn());
            String unit = master == null ? "NOS" : firstNonBlank(master.getUnit(), "NOS");
            String remarks = master == null ? "" : safe(master.getRemarks());
            items.add(new TaxInvoiceItem(serial++, hsn, cleanDescription(line.getItemDescription(), line.getItemCode()),
                    remarks, line.getQuantity(), unit, line.getRate(), line.getDiscountPercent(), line.getGstPercent()));
        }
        return items;
    }

    private static List<TaxInvoiceItem> quotationItems(List<QuotationApiClient.LineDto> lines) {
        List<TaxInvoiceItem> items = new ArrayList<>();
        int serial = 1;
        for (QuotationApiClient.LineDto line : lines == null ? List.<QuotationApiClient.LineDto>of() : lines) {
            if (line == null) continue;
            items.add(new TaxInvoiceItem(serial++, "", safe(line.description()), safe(line.code()),
                    line.quantity(), "NOS", line.rate(), line.discount(), line.gst()));
        }
        return items;
    }

    private static String cleanDescription(String value, String code) {
        String text = safe(value);
        String itemCode = safe(code);
        if (!itemCode.isBlank() && text.startsWith(itemCode + " - ")) return text.substring(itemCode.length() + 3).trim();
        return text;
    }

    private static String money(double value) { synchronized (MONEY) { return MONEY.format(value); } }
    private static String formatDate(LocalDate value) { return value == null ? "" : DATE.format(value); }
    private static String displayDate(String value) {
        if (value == null || value.isBlank()) return "";
        try { return DATE.format(LocalDate.parse(value.substring(0, Math.min(10, value.length())))); }
        catch (Exception ignored) { return value; }
    }
    private static void put(Map<String, String> values, String key, String value) { values.put(key, value == null ? "" : value); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String normalize(String value) { return safe(value).toUpperCase(Locale.ROOT); }
    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private static Path resolveConfiguredAsset(String key, String fallbackFileName) {
        String configured = ConfigManager.get(key, "").trim();
        Path configuredPath = pathOrNull(configured);
        if (configuredPath != null && Files.isRegularFile(configuredPath)) return configuredPath;
        if (fallbackFileName != null) {
            try {
                Path fallback = ConfigManager.getConfigFolder().resolve(fallbackFileName);
                if (Files.isRegularFile(fallback)) return fallback;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static Path pathOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Path.of(value).toAbsolutePath().normalize(); }
        catch (Exception ignored) { return null; }
    }
}
