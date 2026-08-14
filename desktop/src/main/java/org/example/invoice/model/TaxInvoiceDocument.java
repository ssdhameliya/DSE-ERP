package org.example.invoice.model;

import java.time.LocalDate;
import java.util.List;

public record TaxInvoiceDocument(
        CompanyProfile company,
        String invoiceNo,
        LocalDate invoiceDate,
        String orderNo,
        LocalDate poDate,
        String paymentTerms,
        InvoiceParty billing,
        InvoiceParty delivery,
        String transporter,
        String transporterGstin,
        String vehicleNumber,
        String contactPerson,
        String contactPersonMobile,
        List<TaxInvoiceItem> items,
        String gstType,
        List<TaxInvoiceCharge> charges,
        InvoiceTotals totals,
        String amountInWords) {

    public TaxInvoiceDocument {
        invoiceNo = safe(invoiceNo);
        orderNo = safe(orderNo);
        paymentTerms = safe(paymentTerms);
        transporter = safe(transporter);
        transporterGstin = safe(transporterGstin);
        vehicleNumber = safe(vehicleNumber);
        contactPerson = safe(contactPerson);
        contactPersonMobile = safe(contactPersonMobile);
        gstType = safe(gstType);
        items = items == null ? List.of() : List.copyOf(items);
        charges = charges == null ? List.of() : List.copyOf(charges);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
