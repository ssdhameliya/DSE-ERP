package org.example.documentstudio.service;

import org.example.config.ConfigManager;
import org.example.dao.ItemDAO;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts Purchase entities into the stable field keys stored by Purchase Document Studio. */
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

        party(v, purchase.getSupplier());

        put(v, "totals.subtotal", money(purchase.getSubtotal()));
        put(v, "totals.discountAmount", money(purchase.getDiscountAmount()));
        put(v, "totals.gstAmount", money(purchase.getGstAmount()));
        put(v, "totals.grandTotal", money(purchase.getTotalAmount()));
        put(v, "totals.paidAmount", money(purchase.getPaidAmount()));
        put(v, "totals.balanceAmount", money(purchase.getBalanceAmount()));
        put(v, "totals.amountInWords", "INR : " + AmountInWordsConverter.indianRupees(purchase.getTotalAmount()));

        return new TemplateData(v, images, purchaseItems(purchase), safe(purchase.getGstTreatment()));
    }

    /** Used only when the designer has no real saved Purchase selected. */
    public static TemplateData samplePurchase() {
        Map<String, String> v = new LinkedHashMap<>();
        Map<String, Path> images = new LinkedHashMap<>();
        addCompanyAndPayment(v, images);

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

        v.put("totals.subtotal", "29,800.00");
        v.put("totals.discountAmount", "0.00");
        v.put("totals.gstAmount", "5,364.00");
        v.put("totals.grandTotal", "35,164.00");
        v.put("totals.paidAmount", "0.00");
        v.put("totals.balanceAmount", "35,164.00");
        v.put("totals.amountInWords", "INR : Thirty Five Thousand One Hundred Sixty Four Only");

        List<TaxInvoiceItem> items = List.of(
                new TaxInvoiceItem(1, "8483", "Motor Assembly", "", 2, "NOS", 12500, 0, 18),
                new TaxInvoiceItem(2, "8482", "Bearing Set", "", 4, "NOS", 1200, 0, 18)
        );
        return new TemplateData(v, images, items, "Registered Business");
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

    private static void party(Map<String, String> v, Party p) {
        if (p == null) return;
        put(v, "supplier.code", p.getPartyCode());
        put(v, "supplier.name", p.getName());
        put(v, "supplier.address", p.getAddress());
        put(v, "supplier.gstin", p.getGstin());
        put(v, "supplier.contactPerson", p.getContactPerson());
        put(v, "supplier.phone", p.getPhone());
        put(v, "supplier.email", p.getEmail());
    }

    private static List<TaxInvoiceItem> purchaseItems(Purchase purchase) {
        Map<String, Item> itemByCode = new HashMap<>();
        try {
            for (Item item : new ItemDAO().getAll()) {
                if (item != null && item.getItemCode() != null) itemByCode.put(normalize(item.getItemCode()), item);
            }
        } catch (Exception ignored) {
            // A missing master lookup should not prevent the already-saved purchase from rendering.
        }

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

    private static String cleanDescription(String value, String code) {
        String text = safe(value);
        String itemCode = safe(code);
        if (!itemCode.isBlank() && text.startsWith(itemCode + " - ")) return text.substring(itemCode.length() + 3).trim();
        return text;
    }

    private static String money(double value) { synchronized (MONEY) { return MONEY.format(value); } }
    private static String formatDate(java.time.LocalDate value) { return value == null ? "" : DATE.format(value); }
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
