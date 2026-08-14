package org.example.invoice.model;

public record InvoiceTotals(
        double basicAmount,
        double discountAmount,
        double chargesAmount,
        double taxableAmount,
        double nonTaxableCharges,
        double cgst,
        double sgst,
        double igst,
        double roundOff,
        double grandTotal) {
}
