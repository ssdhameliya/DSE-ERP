package org.example.documentstudio.service;

import org.example.api.quotation.QuotationApiClient;
import org.example.api.returns.ReturnApiClient;
import org.example.config.ConfigManager;
import org.example.dao.ItemDAO;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateData;
import org.example.documentstudio.model.TemplateCharge;
import org.example.invoice.calculation.AmountInWordsConverter;
import org.example.invoice.model.TaxInvoiceItem;
import org.example.model.Item;
import org.example.model.Party;
import org.example.model.Purchase;
import org.example.model.PurchaseLine;
import org.example.model.Sales;
import org.example.model.SalesLine;
import org.example.model.SalesCharge;
import org.example.model.PurchaseCharge;
import org.example.shared.DocumentCalculationEngine;
import org.example.util.BusinessClock;

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
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private TemplateDataFactory() {}


    /** Live Sales data used only when a user explicitly activates a Sales Invoice Studio default. */
    public static TemplateData fromSales(Sales sale) {
        if (sale == null) throw new IllegalArgumentException("Sales invoice is required.");
        Map<String, String> v = new LinkedHashMap<>();
        Map<String, Path> images = new LinkedHashMap<>();
        addCompanyAndPayment(v, images);
        put(v, "sales.number", sale.getInvoiceNo());
        put(v, "sales.date", formatDate(sale.getInvoiceDate()));
        put(v, "sales.dueDate", formatDate(sale.getDueDate()));
        put(v, "sales.invoiceType", sale.getInvoiceType());
        put(v, "sales.referenceNo", sale.getReferenceNo());
        put(v, "sales.orderNo", sale.getOrderNo());
        put(v, "sales.poDate", formatDate(sale.getPoDate()));
        put(v, "sales.paymentTerms", sale.getPaymentTerms());
        put(v, "sales.transporter", sale.getTransporter());
        put(v, "sales.transporterGstin", sale.getTransporterGstin());
        put(v, "sales.vehicleNo", sale.getVehicleNumber());
        put(v, "sales.doorDelivery", sale.getDoorDelivery());
        put(v, "sales.contactPerson", sale.getContactPerson());
        put(v, "sales.contactMobile", sale.getContactPersonMobile());
        put(v, "sales.transportNote", sale.getTransportNote());
        put(v, "sales.salesperson", sale.getSalesperson());
        put(v, "sales.source", sale.getSource());
        put(v, "sales.notes", sale.getNotes());
        put(v, "sales.remarks", sale.getRemarks());
        put(v, "sales.documentStatus", sale.getDocumentStatus());
        put(v, "sales.paymentStatus", sale.getPaymentStatus());
        put(v, "sales.emailStatus", sale.isEmailSent() ? "SENT" : "PENDING");
        put(v, "sales.whatsappStatus", sale.isWhatsappSent() ? "SENT" : "PENDING");
        put(v, "sales.billingAddress", sale.getBillingAddress());
        put(v, "sales.deliveryAddress", sale.getDeliveryAddress());
        put(v, "sales.shippingAddress", sale.getDeliveryAddress());
        put(v, "sales.billingGstin", sale.getBillingGstin());
        put(v, "sales.deliveryGstin", sale.getDeliveryGstin());
        put(v, "sales.shippingGstin", sale.getDeliveryGstin());
        put(v, "sales.gstin", sale.getGstin());
        put(v, "sales.gstType", sale.getGstType());
        put(v, "sales.createdAt", sale.getCreatedAt());
        double totalSalesQuantity = sale.getLines() == null ? sale.getQuantity() : sale.getLines().stream().filter(java.util.Objects::nonNull).mapToDouble(SalesLine::getQuantity).sum();
        put(v, "sales.totalQuantity", number(totalSalesQuantity));
        put(v, "sales.sameAsBilling", sale.isSameAsBilling() ? "Yes" : "No");
        party(v, sale.getCustomer(), "customer");
        put(v, "totals.subtotal", money(sale.getSubtotal()));
        put(v, "totals.discountAmount", money(sale.getDiscountAmount()));
        put(v, "totals.gstAmount", money(sale.getGstAmount()));
        putTaxTotals(v, sale.getGstAmount(), sale.getGstType());
        put(v, "totals.grandTotal", money(sale.getTotalAmount()));
        put(v, "totals.paidAmount", money(sale.getPaidAmount()));
        put(v, "totals.balanceAmount", money(sale.getBalanceAmount()));
        put(v, "totals.amountInWords", "INR : " + AmountInWordsConverter.indianRupees(sale.getTotalAmount()));
        return new TemplateData(v, images, salesItems(sale), salesCharges(sale), safe(sale.getGstType()));
    }

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
        put(v, "purchase.transporterGstin", purchase.getTransporterGstin());
        put(v, "purchase.vehicleNo", purchase.getVehicleNumber());
        put(v, "purchase.contactPerson", purchase.getContactPerson());
        put(v, "purchase.contactMobile", purchase.getContactPersonMobile());
        put(v, "purchase.lrAwbNo", purchase.getLrAwbNo());
        put(v, "purchase.remarks", purchase.getRemarks());
        put(v, "purchase.notes", purchase.getNotes());
        put(v, "purchase.billingAddress", purchase.getBillingAddress());
        put(v, "purchase.deliveryAddress", purchase.getDeliveryAddress());
        put(v, "purchase.billingGstin", purchase.getBillingGstin());
        put(v, "purchase.deliveryGstin", purchase.getDeliveryGstin());
        put(v, "purchase.gstType", purchase.getGstType());
        put(v, "purchase.orderNo", purchase.getOrderNo());
        put(v, "purchase.poDate", formatDate(purchase.getPoDate()));
        put(v, "purchase.createdBy", purchase.getCreatedBy());
        put(v, "purchase.documentStatus", purchase.getDocumentStatus());
        put(v, "purchase.paymentStatus", purchase.getPaymentStatus());
        party(v, purchase.getSupplier(), "supplier");

        put(v, "totals.subtotal", money(purchase.getSubtotal()));
        put(v, "totals.discountAmount", money(purchase.getDiscountAmount()));
        put(v, "totals.gstAmount", money(purchase.getGstAmount()));
        putTaxTotals(v, purchase.getGstAmount(), purchase.getGstType());
        put(v, "totals.grandTotal", money(purchase.getTotalAmount()));
        put(v, "totals.paidAmount", money(purchase.getPaidAmount()));
        put(v, "totals.balanceAmount", money(purchase.getBalanceAmount()));
        put(v, "totals.amountInWords", "INR : " + AmountInWordsConverter.indianRupees(purchase.getTotalAmount()));
        return new TemplateData(v, images, purchaseItems(purchase), purchaseCharges(purchase), safe(purchase.getGstType()));
    }

    public static TemplateData fromPurchaseReturn(ReturnApiClient.Details details, Purchase originalPurchase) {
        if (details == null) throw new IllegalArgumentException("Purchase return is required.");
        Map<String, String> v = new LinkedHashMap<>();
        Map<String, Path> images = new LinkedHashMap<>();
        addCompanyAndPayment(v, images);

        put(v, "return.number", details.no());
        put(v, "return.date", displayDate(details.date()));
        put(v, "return.referenceNo", details.invoice());
        String reason = safe(details.notes());
        if (reason.isBlank() && details.lines() != null) {
            reason = details.lines().stream().map(ReturnApiClient.Line::reason).filter(x -> x != null && !x.isBlank()).distinct().reduce((a,b) -> a + "; " + b).orElse("");
        }
        put(v, "return.reason", reason);
        put(v, "party.name", details.party());
        if (originalPurchase != null && originalPurchase.getSupplier() != null) {
            Party supplier = originalPurchase.getSupplier();
            put(v, "party.name", safe(details.party()).isBlank() ? supplier.getName() : details.party());
            put(v, "party.address", supplier.getAddress());
            put(v, "party.gstin", supplier.getGstin());
        } else {
            put(v, "party.address", "");
            put(v, "party.gstin", "");
        }

        List<TaxInvoiceItem> items = new ArrayList<>();
        Map<String, Item> itemByCode = itemMasterByCode();
        double subtotal = 0, gst = 0;
        int serial = 1;
        if (details.lines() != null) for (ReturnApiClient.Line line : details.lines()) {
            double gross = line.quantity() * line.rate();
            double taxAmount = gross * line.tax() / 100.0;
            subtotal += gross;
            gst += taxAmount;
            String code = safe(line.code());
            Item master = itemByCode.get(normalize(code));
            items.add(itemWithMaster(serial++, code, safe(line.name()), "", line.quantity(), safeOr(line.unit(), "Nos"), line.rate(), 0, line.tax(), master));
        }
        put(v, "totals.subtotal", money(subtotal));
        put(v, "totals.discountAmount", "0.00");
        put(v, "totals.gstAmount", money(gst));
        putTaxTotals(v, gst, originalPurchase == null ? "GST" : originalPurchase.getGstType());
        put(v, "totals.grandTotal", money(details.total()));
        put(v, "totals.paidAmount", money(details.refund()));
        put(v, "totals.balanceAmount", money(Math.max(0, details.total() - details.refund())));
        put(v, "totals.amountInWords", "INR : " + AmountInWordsConverter.indianRupees(details.total()));
        return new TemplateData(v, images, items, originalPurchase == null ? "" : safe(originalPurchase.getGstTreatment()));
    }

    public static TemplateData fromSalesReturn(ReturnApiClient.Details details, Sales originalSale) {
        if (details == null) throw new IllegalArgumentException("Sales return is required.");
        Map<String, String> v = new LinkedHashMap<>();
        Map<String, Path> images = new LinkedHashMap<>();
        addCompanyAndPayment(v, images);

        put(v, "return.number", details.no());
        put(v, "return.date", displayDate(details.date()));
        put(v, "return.referenceNo", details.invoice());
        String reason = safe(details.notes());
        if (reason.isBlank() && details.lines() != null) {
            reason = details.lines().stream().map(ReturnApiClient.Line::reason)
                    .filter(x -> x != null && !x.isBlank()).distinct()
                    .reduce((a,b) -> a + "; " + b).orElse("");
        }
        put(v, "return.reason", reason);
        put(v, "party.name", details.party());
        if (originalSale != null && originalSale.getCustomer() != null) {
            Party customer = originalSale.getCustomer();
            put(v, "party.name", safe(details.party()).isBlank() ? customer.getName() : details.party());
            put(v, "party.address", customer.getAddress());
            put(v, "party.gstin", customer.getGstin());
        } else {
            put(v, "party.address", "");
            put(v, "party.gstin", "");
        }

        List<TaxInvoiceItem> items = new ArrayList<>();
        Map<String, Item> itemByCode = itemMasterByCode();
        double subtotal = 0, gst = 0;
        int serial = 1;
        if (details.lines() != null) for (ReturnApiClient.Line line : details.lines()) {
            double gross = line.quantity() * line.rate();
            double taxAmount = gross * line.tax() / 100.0;
            subtotal += gross;
            gst += taxAmount;
            String code = safe(line.code());
            Item master = itemByCode.get(normalize(code));
            items.add(itemWithMaster(serial++, code, safe(line.name()), "", line.quantity(), safeOr(line.unit(), "Nos"), line.rate(), 0, line.tax(), master));
        }
        put(v, "totals.subtotal", money(subtotal));
        put(v, "totals.discountAmount", "0.00");
        put(v, "totals.gstAmount", money(gst));
        putTaxTotals(v, gst, originalSale == null ? "GST" : originalSale.getGstType());
        put(v, "totals.grandTotal", money(details.total()));
        put(v, "totals.paidAmount", money(details.refund()));
        put(v, "totals.balanceAmount", money(Math.max(0, details.total() - details.refund())));
        put(v, "totals.amountInWords", "INR : " + AmountInWordsConverter.indianRupees(details.total()));
        return new TemplateData(v, images, items, originalSale == null ? "" : safe(originalSale.getGstType()));
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
        putTaxTotals(v, gst, "GST");
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
            case PURCHASE_RETURN -> samplePurchaseReturn();
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
        return new TemplateData(v, images, sampleItems(), sampleCharges(), "Registered Business");
    }

    private static TemplateData samplePurchaseReturn() {
        Map<String, String> v = commonSample();
        Map<String, Path> images = configuredImages();
        v.put("return.number", "PUR-RET-2026-0012");
        v.put("return.date", "15-08-2026");
        v.put("return.referenceNo", "PINV-2026-00125");
        v.put("return.reason", "Material returned to supplier");
        v.put("party.name", "ABC Components Pvt Ltd");
        v.put("party.address", "Industrial Estate, Ahmedabad, Gujarat");
        v.put("party.gstin", "24AABCA1234A1Z5");
        addSampleTotals(v, 5000, 0, 900, 5900);
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
        v.put("sales.invoiceType", "TAX INVOICE");
        v.put("sales.referenceNo", "CUS-PO-4587");
        v.put("sales.orderNo", "SO-2026-0042");
        v.put("sales.poDate", "12-08-2026");
        v.put("sales.paymentTerms", "30 Days");
        v.put("sales.transporter", "Local Transport");
        v.put("sales.transporterGstin", "24ABCDE1234F1Z5");
        v.put("sales.vehicleNo", "GJ-01-AB-1234");
        v.put("sales.doorDelivery", "Yes");
        v.put("sales.contactPerson", "Purchase Manager");
        v.put("sales.contactMobile", "+91 98765 43210");
        v.put("sales.transportNote", "Handle with care");
        v.put("sales.salesperson", "Admin");
        v.put("sales.source", "Direct");
        v.put("sales.notes", "Thank you for your business.");
        v.put("sales.remarks", "Dispatch as agreed.");
        v.put("sales.documentStatus", "COMPLETED");
        v.put("sales.paymentStatus", "PENDING");
        v.put("sales.emailStatus", "SENT");
        v.put("sales.whatsappStatus", "PENDING");
        v.put("sales.billingAddress", "Ahmedabad, Gujarat");
        v.put("sales.deliveryAddress", "Sanand, Gujarat");
        v.put("sales.shippingAddress", "Sanand, Gujarat");
        v.put("sales.billingGstin", "24ABCDE1234F1Z5");
        v.put("sales.deliveryGstin", "24ABCDE1234F1Z5");
        v.put("sales.shippingGstin", "24ABCDE1234F1Z5");
        v.put("sales.gstin", "24ABCDE1234F1Z5");
        v.put("sales.gstType", "GST");
        v.put("sales.createdAt", "15-08-2026 10:30");
        v.put("sales.totalQuantity", "6");
        v.put("sales.sameAsBilling", "No");
        v.put("customer.code", "CUS-001");
        v.put("customer.contactPerson", "Purchase Manager");
        v.put("customer.name", "ABC Engineering Pvt Ltd");
        v.put("customer.address", "Ahmedabad, Gujarat");
        v.put("customer.gstin", "24ABCDE1234F1Z5");
        v.put("customer.phone", "+91 98765 43210");
        v.put("customer.email", "accounts@abcengineering.example");
        addSampleTotals(v, 29800, 0, 5364, 35164);
        return new TemplateData(v, images, sampleItems(), sampleCharges(), "Registered Business");
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

    private static void putTaxTotals(Map<String, String> values, double totalTax, String taxType) {
        DocumentCalculationEngine.TaxMode mode = DocumentCalculationEngine.taxMode(taxType);
        double tax = DocumentCalculationEngine.money(totalTax);
        double cgst = mode == DocumentCalculationEngine.TaxMode.GST ? DocumentCalculationEngine.money(tax / 2d) : 0d;
        double sgst = mode == DocumentCalculationEngine.TaxMode.GST ? DocumentCalculationEngine.money(tax - cgst) : 0d;
        double igst = mode == DocumentCalculationEngine.TaxMode.IGST ? tax : 0d;
        values.put("totals.cgstAmount", mode == DocumentCalculationEngine.TaxMode.GST ? money(cgst) : "0.00");
        values.put("totals.sgstAmount", mode == DocumentCalculationEngine.TaxMode.GST ? money(sgst) : "0.00");
        values.put("totals.igstAmount", mode == DocumentCalculationEngine.TaxMode.IGST ? money(igst) : "0.00");
    }

    private static void addSampleTotals(Map<String, String> v, double subtotal, double discount, double gst, double total) {
        v.put("totals.subtotal", money(subtotal));
        v.put("totals.discountAmount", money(discount));
        v.put("totals.gstAmount", money(gst));
        putTaxTotals(v, gst, "GST");
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


    private static List<TaxInvoiceItem> salesItems(Sales sale) {
        Map<String, Item> itemByCode = itemMasterByCode();
        List<TaxInvoiceItem> items = new ArrayList<>();
        int serial = 1;
        for (SalesLine line : sale.getLines() == null ? List.<SalesLine>of() : sale.getLines()) {
            if (line == null) continue;
            String code = safe(line.getItemCode());
            Item master = itemByCode.get(normalize(code));
            items.add(itemWithMaster(serial++, code, cleanDescription(line.getItemDescription(), code),
                    master == null ? "" : safe(master.getRemarks()), line.getQuantity(),
                    master == null ? "NOS" : firstNonBlank(master.getUnit(), "NOS"),
                    line.getRate(), line.getDiscountPercent(), line.getGstPercent(), master));
        }
        return items;
    }

    private static List<TaxInvoiceItem> purchaseItems(Purchase purchase) {
        Map<String, Item> itemByCode = itemMasterByCode();
        List<TaxInvoiceItem> items = new ArrayList<>();
        int serial = 1;
        for (PurchaseLine line : purchase.getLines() == null ? List.<PurchaseLine>of() : purchase.getLines()) {
            if (line == null) continue;
            String code = safe(line.getItemCode());
            Item master = itemByCode.get(normalize(code));
            items.add(itemWithMaster(serial++, code, cleanDescription(line.getItemDescription(), code),
                    master == null ? "" : safe(master.getRemarks()), line.getQuantity(),
                    master == null ? "NOS" : firstNonBlank(master.getUnit(), "NOS"),
                    line.getRate(), line.getDiscountPercent(), line.getGstPercent(), master));
        }
        return items;
    }

    private static List<TaxInvoiceItem> quotationItems(List<QuotationApiClient.LineDto> lines) {
        Map<String, Item> itemByCode = itemMasterByCode();
        List<TaxInvoiceItem> items = new ArrayList<>();
        int serial = 1;
        for (QuotationApiClient.LineDto line : lines == null ? List.<QuotationApiClient.LineDto>of() : lines) {
            if (line == null) continue;
            String code = safe(line.code());
            Item master = itemByCode.get(normalize(code));
            items.add(itemWithMaster(serial++, code, safe(line.description()), master == null ? "" : safe(master.getRemarks()),
                    line.quantity(), master == null ? "NOS" : firstNonBlank(master.getUnit(), "NOS"),
                    line.rate(), line.discount(), line.gst(), master));
        }
        return items;
    }

    private static Map<String, Item> itemMasterByCode() {
        Map<String, Item> itemByCode = new HashMap<>();
        try {
            for (Item item : new ItemDAO().getAll()) {
                if (item != null && item.getItemCode() != null) itemByCode.put(normalize(item.getItemCode()), item);
            }
        } catch (Exception ignored) { }
        return itemByCode;
    }

    private static TaxInvoiceItem itemWithMaster(int serial, String code, String description, String remarks,
                                                  double quantity, String unit, double rate, double discount, double gst,
                                                  Item master) {
        String hsn = master == null ? "" : safe(master.getHsn());
        return new TaxInvoiceItem(serial, hsn, description, remarks, quantity, unit, rate, discount, gst,
                code,
                master == null ? "" : safe(master.getCategory()),
                master == null ? "" : safe(master.getBrand()),
                master == null ? "" : safe(master.getMaterial()),
                master == null ? "" : safe(master.getSize()),
                master == null ? "" : safe(master.getLocation()),
                master == null ? 0 : master.getPurchasePrice(),
                master == null ? 0 : master.getSellingPrice(),
                master == null ? 0 : master.getAvailableStock(),
                master == null ? 0 : master.getOpeningStock(),
                master == null ? 0 : master.getMinimumStock(),
                master == null ? 0 : master.getReservedStock(),
                master == null ? 0 : master.getGst(),
                master == null ? 0 : master.getDiscountPercent());
    }

    private static String cleanDescription(String value, String code) {
        String text = safe(value);
        String itemCode = safe(code);
        if (!itemCode.isBlank() && text.startsWith(itemCode + " - ")) return text.substring(itemCode.length() + 3).trim();
        return text;
    }

    private static String money(double value) { synchronized (MONEY) { return MONEY.format(value); } }
    private static String number(double value) { return Math.rint(value)==value?String.format(Locale.ROOT,"%.0f",value):String.format(Locale.ROOT,"%.2f",value); }
    private static String formatDate(LocalDate value) { return BusinessClock.formatDate(value); }
    private static String displayDate(String value) {
        if (value == null || value.isBlank()) return "";
        try { return BusinessClock.formatDate(LocalDate.parse(value.substring(0, Math.min(10, value.length())))); }
        catch (Exception ignored) { return value; }
    }
    private static void put(Map<String, String> values, String key, String value) { values.put(key, value == null ? "" : value); }
    private static List<TemplateCharge> salesCharges(Sales sale) {
        if (sale == null || sale.getCharges() == null) return List.of();
        List<TemplateCharge> out = new ArrayList<>();
        for (SalesCharge c : sale.getCharges()) {
            if (c == null) continue;
            out.add(new TemplateCharge(c.getChargeType(), c.getAmount(), c.isTaxable(), c.getGstPercent(), c.getTaxAmount(), c.getTotalAmount()));
        }
        return List.copyOf(out);
    }

    private static List<TemplateCharge> purchaseCharges(Purchase purchase) {
        if (purchase == null || purchase.getCharges() == null) return List.of();
        List<TemplateCharge> out = new ArrayList<>();
        for (PurchaseCharge c : purchase.getCharges()) {
            if (c == null) continue;
            out.add(new TemplateCharge(c.getChargeType(), c.getAmount(), c.isTaxable(), c.getGstPercent(), c.getTaxAmount(), c.getTotalAmount()));
        }
        return List.copyOf(out);
    }

    private static List<TemplateCharge> sampleCharges() {
        return List.of(
                new TemplateCharge("Freight", 250, true, 18, 45, 295),
                new TemplateCharge("Packing", 100, false, 0, 0, 100),
                new TemplateCharge("Insurance", 75, true, 18, 13.50, 88.50));
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String safeOr(String value, String fallback) { String result=safe(value); return result.isBlank()?safe(fallback):result; }
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
