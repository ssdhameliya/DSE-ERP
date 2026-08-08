package org.example.invoice.model;

import java.time.LocalDate;
import java.util.List;

public record TaxInvoiceDocument(
        CompanyProfile company,
        String invoiceNo,
        LocalDate invoiceDate,
        String orderNo,
        LocalDate poDate,
        InvoiceParty billing,
        InvoiceParty delivery,
        String transporter,
        String vehicleNumber,
        String contactPerson,
        List<TaxInvoiceItem> items,
        String gstType,
        double freightCharges,
        InvoiceTotals totals,
        String amountInWords) {

    public TaxInvoiceDocument {
        invoiceNo = safe(invoiceNo);
        orderNo = safe(orderNo);
        transporter = safe(transporter);
        vehicleNumber = safe(vehicleNumber);
        contactPerson = safe(contactPerson);
        gstType = safe(gstType);
        items = items == null ? List.of() : List.copyOf(items);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
