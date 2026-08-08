package org.example.invoice.model;

public record InvoiceTotals(
        double basicAmount,
        double discountAmount,
        double freightCharges,
        double grossTotal,
        double cgst,
        double sgst,
        double igst,
        double roundOff,
        double grandTotal) {
}
