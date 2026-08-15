package org.example.documentstudio.model;

/**
 * Universal document types supported by Document Studio 7.3.0.
 *
 * <p>Only Purchase Invoice and Quotation are wired into automatic ERP PDF
 * generation in 7.3.0. Sales remains design/preview only so the established
 * Sales PDF runtime path is not changed.</p>
 */
public enum DocumentType {
    GENERAL_PDF("General PDF", false, false),
    PURCHASE_INVOICE("Purchase Invoice", true, true),
    PURCHASE_ORDER("Purchase Order", true, false),
    QUOTATION("Quotation", true, true),
    DELIVERY_CHALLAN("Delivery Challan", true, false),
    CREDIT_NOTE("Credit Note", true, false),
    DEBIT_NOTE("Debit Note", true, false),
    PAYMENT_RECEIPT("Payment Receipt", true, false),
    SALES_INVOICE("Sales Invoice", true, false),
    SALES_RETURN("Sales Return", true, false),
    CUSTOM_ERP("Custom ERP Document", true, false);

    private final String label;
    private final boolean erpConnected;
    private final boolean automaticRuntime;

    DocumentType(String label, boolean erpConnected, boolean automaticRuntime) {
        this.label = label;
        this.erpConnected = erpConnected;
        this.automaticRuntime = automaticRuntime;
    }

    public String label() { return label; }
    public boolean isErpConnected() { return erpConnected; }
    public boolean isAutomaticRuntime() { return automaticRuntime; }
    public boolean isGeneral() { return this == GENERAL_PDF; }

    @Override public String toString() { return label; }
}
