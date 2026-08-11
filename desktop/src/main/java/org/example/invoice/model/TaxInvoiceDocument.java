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
        String doorDelivery,
        String vehicleNumber,
        String contactPerson,
        String contactPersonMobile,
        List<TaxInvoiceItem> items,
        String gstType,
        double freightCharges,
        InvoiceTotals totals,
        String amountInWords) {

    public TaxInvoiceDocument {
        invoiceNo = safe(invoiceNo);
        orderNo = safe(orderNo);
        transporter = safe(transporter);
        doorDelivery = safe(doorDelivery);
        vehicleNumber = safe(vehicleNumber);
        contactPerson = safe(contactPerson);
        contactPersonMobile = safe(contactPersonMobile);
        gstType = safe(gstType);
        items = items == null ? List.of() : List.copyOf(items);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
