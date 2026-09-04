package org.example.util;

import org.example.api.master.MasterApiClient;
import org.example.api.operations.OperationsApiClient;
import org.example.api.quotation.QuotationApiClient;
import org.example.api.returns.ReturnApiClient;
import org.example.config.ConfigManager;
import org.example.model.*;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * API/data-loading boundary for ProfessionalDocumentRenderer.
 * Rendering remains purely concerned with layout once this loader returns a normalized document model.
 */
final class ProfessionalDocumentDataLoader {
    private ProfessionalDocumentDataLoader() { }

    static ProfessionalDocumentRenderer.Data load(String number, ProfessionalDocumentRenderer.Kind kind) {
        ProfessionalDocumentRenderer.Data data = switch (kind) {
            case SALES_INVOICE -> loadInvoice(number, true);
            case PURCHASE_INVOICE -> loadInvoice(number, false);
            case QUOTATION -> loadQuotation(number);
            case SALES_REFUND -> loadRefund(number, true);
            case PURCHASE_REFUND -> loadRefund(number, false);
        };
        normalizeTotals(data);
        return data;
    }

    private static ProfessionalDocumentRenderer.Data loadInvoice(String number, boolean sales) {
        OperationsApiClient api = new OperationsApiClient();
        MasterApiClient master = new MasterApiClient();
        ProfessionalDocumentRenderer.Data data = new ProfessionalDocumentRenderer.Data();
        data.title = "TAX INVOICE";
        data.numberLabel = "Invoice No.";
        data.dateLabel = "Invoice Date";
        Map<String, Item> itemMap = new HashMap<>();
        for (Item item : master.items()) itemMap.put(item.getItemCode(), item);
        if (sales) {
            Sales doc = api.sale(number);
            if (doc == null) throw new IllegalArgumentException("Sales not found: " + number);
            data.number = doc.getInvoiceNo();
            data.date = date(doc.getInvoiceDate());
            data.dueDate = date(doc.getDueDate());
            data.poDate = date(doc.getPoDate());
            populateParty(data, doc.getCustomer());
            data.partyAddress = firstNonBlank(doc.getBillingAddress(), data.partyAddress);
            data.billingGstin = firstNonBlank(doc.getBillingGstin(), doc.getGstin(), data.partyGstin);
            data.sameAsBilling = doc.isSameAsBilling();
            data.shipTo = data.sameAsBilling ? data.partyAddress : present(doc.getDeliveryAddress());
            data.deliveryGstin = data.sameAsBilling ? data.billingGstin : present(doc.getDeliveryGstin());
            data.subtotal = doc.getSubtotal();
            data.gst = doc.getGstAmount();
            data.total = doc.getTotalAmount();
            data.salesperson = doc.getSalesperson();
            data.paymentTerms = doc.getPaymentTerms();
            data.transporter = doc.getTransporter();
            data.transporterGstin = doc.getTransporterGstin();
            data.gstType = doc.getGstType();
            data.vehicleNumber = doc.getVehicleNumber();
            data.contactPerson = doc.getContactPerson();
            data.contactPersonMobile = doc.getContactPersonMobile();
            data.transportNote = doc.getTransportNote();
            data.chargeType = doc.getChargeType();
            data.chargeAmount = doc.getChargeAmount();
            data.reference = doc.getReferenceNo();
            data.purchaseOrder = doc.getOrderNo();
            data.partyGstin = data.billingGstin;
            if (doc.getLines() != null) {
                for (SalesLine line : doc.getLines()) {
                    data.lines.add(line(line.getItemCode(), line.getItemDescription(), line.getItemHsn(), line.getItemUnit(),
                        line.getQuantity(), line.getRate(), line.getGstPercent(), line.getDiscountAmount(), itemMap));
                }
            }
        } else {
            Purchase doc = api.purchase(number);
            if (doc == null) throw new IllegalArgumentException("Purchase not found: " + number);
            data.number = doc.getInvoiceNo();
            data.date = date(doc.getInvoiceDate());
            data.dueDate = date(doc.getDueDate());
            populateParty(data, doc.getSupplier());
            data.subtotal = doc.getSubtotal();
            data.gst = doc.getGstAmount();
            data.total = doc.getTotalAmount();
            data.paymentTerms = doc.getPaymentTerms();
            data.transporter = doc.getTransporter();
            data.reference = doc.getReferenceNo();
            data.shipTo = ConfigManager.get("company.shipAddress", ConfigManager.get("company.address", "Company delivery address not configured"));
            if (doc.getLines() != null) {
                for (PurchaseLine line : doc.getLines()) {
                    data.lines.add(line(line.getItemCode(), line.getItemDescription(), line.getItemHsn(), line.getItemUnit(),
                        line.getQuantity(), line.getRate(), line.getGstPercent(), line.getDiscountAmount(), itemMap));
                }
            }
        }
        return data;
    }

    private static ProfessionalDocumentRenderer.Data loadQuotation(String number) {
        QuotationApiClient api = new QuotationApiClient();
        MasterApiClient master = new MasterApiClient();
        QuotationApiClient.QuoteDto quote = api.list().stream()
            .filter(value -> value.no() != null && value.no().equalsIgnoreCase(number))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Quotation not found: " + number));
        ProfessionalDocumentRenderer.Data data = new ProfessionalDocumentRenderer.Data();
        data.quotation = true;
        data.title = "QUOTATION";
        data.numberLabel = "Quotation No.";
        data.dateLabel = "Quotation Date";
        data.number = quote.no();
        data.date = quote.date();
        data.dueDate = quote.valid();
        Party party = master.parties("CUSTOMER").stream().filter(value -> value.getId() == quote.customerId()).findFirst().orElse(null);
        populateParty(data, party);
        data.subtotal = quote.amount();
        data.total = quote.amount();
        data.salesperson = quote.salesperson();
        data.paymentTerms = "As agreed";
        data.reference = quote.source();
        data.shipTo = data.partyAddress;
        Map<String, Item> itemMap = new HashMap<>();
        for (Item item : master.items()) itemMap.put(item.getItemCode(), item);
        for (QuotationApiClient.LineDto value : api.lines(quote.id())) {
            data.lines.add(line(value.code(), value.description(), "", "", value.quantity(), value.rate(), value.gst(),
                Math.max(0, value.quantity() * value.rate() - value.total() / (1 + value.gst() / 100.0)), itemMap));
        }
        normalizeTotals(data);
        return data;
    }

    private static ProfessionalDocumentRenderer.Data loadRefund(String number, boolean sales) {
        ReturnApiClient returns = new ReturnApiClient();
        OperationsApiClient operations = new OperationsApiClient();
        MasterApiClient master = new MasterApiClient();
        ReturnApiClient.Details details = returns.details(number);
        if (details == null || details.type() == null
            || sales && !details.type().toUpperCase(Locale.ROOT).startsWith("SALES")
            || !sales && !details.type().toUpperCase(Locale.ROOT).startsWith("PURCHASE")) {
            throw new IllegalArgumentException("Refund record not found: " + number);
        }
        ProfessionalDocumentRenderer.Data data = new ProfessionalDocumentRenderer.Data();
        data.refund = true;
        data.title = sales ? "SALES REFUND NOTE" : "PURCHASE REFUND NOTE";
        data.numberLabel = "Refund Note No.";
        data.dateLabel = "Refund Note Date";
        data.number = details.no();
        data.date = details.date();
        data.originalNumber = details.invoice();
        try {
            data.originalDate = sales
                ? date(operations.sale(details.invoice()).getInvoiceDate())
                : date(operations.purchase(details.invoice()).getInvoiceDate());
        } catch (Exception ignored) { }
        Party party = master.parties(sales ? "CUSTOMER" : "SUPPLIER").stream()
            .filter(value -> value.getName() != null && value.getName().equalsIgnoreCase(details.party()))
            .findFirst().orElse(null);
        populateParty(data, party);
        data.status = details.refundStatus();
        data.total = details.total();
        if (details.lines() != null) {
            for (ReturnApiClient.Line value : details.lines()) {
                ProfessionalDocumentRenderer.Line item = new ProfessionalDocumentRenderer.Line();
                item.code = value.code();
                item.description = present(value.name());
                item.unit = present(value.unit());
                item.quantity = value.quantity();
                item.rate = value.rate();
                item.gst = value.tax();
                item.discount = Math.max(0, item.quantity * item.rate - value.amount() / (1 + item.gst / 100.0));
                data.lines.add(item);
                if (data.reason.isBlank()) data.reason = present(value.reason());
            }
        }
        normalizeTotals(data);
        return data;
    }

    private static void populateParty(ProfessionalDocumentRenderer.Data data, Party party) {
        if (party == null) return;
        data.partyCode = present(party.getPartyCode());
        data.partyName = present(party.getName());
        data.partyAddress = present(party.getAddress());
        data.partyGstin = present(party.getGstin());
        data.partyPhone = present(party.getPhone());
        data.partyEmail = present(party.getEmail());
    }

    private static ProfessionalDocumentRenderer.Line line(String code, String description, String snapshotHsn, String snapshotUnit,
                                                           double quantity, double rate, double gst, double discount,
                                                           Map<String, Item> itemMap) {
        ProfessionalDocumentRenderer.Line item = new ProfessionalDocumentRenderer.Line();
        item.code = present(code);
        item.description = present(description);
        Item master = itemMap.get(code);
        item.hsn = firstNonBlank(snapshotHsn, master == null ? "" : present(master.getHsn()));
        item.unit = firstNonBlank(snapshotUnit, master == null ? "Nos" : present(master.getUnit()));
        item.quantity = quantity;
        item.rate = rate;
        item.gst = gst;
        item.discount = Math.max(0, discount);
        return item;
    }

    static void normalizeTotals(ProfessionalDocumentRenderer.Data data) {
        if (data.lines.isEmpty()) return;
        double taxable = 0;
        double gst = 0;
        for (ProfessionalDocumentRenderer.Line line : data.lines) {
            double base = line.quantity * line.rate - line.discount;
            taxable += base;
            gst += base * line.gst / 100;
        }
        data.subtotal = taxable;
        data.gst = gst;
        data.total = taxable + gst;
    }

    private static String date(java.time.LocalDate date) { return date == null ? "" : date.toString(); }
    private static String present(String value) { return ProfessionalDocumentFormatSupport.present(value); }
    private static String firstNonBlank(String first, String fallback) { return ProfessionalDocumentFormatSupport.firstNonBlank(first, fallback); }
    private static String firstNonBlank(String first, String second, String fallback) { return ProfessionalDocumentFormatSupport.firstNonBlank(first, second, fallback); }
}
