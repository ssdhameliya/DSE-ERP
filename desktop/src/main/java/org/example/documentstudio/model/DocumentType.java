package org.example.documentstudio.model;

/** Universal document types supported by Document Studio. Runtime automation is owned by DocumentFlowRegistry. */
public enum DocumentType {
    GENERAL_PDF("General PDF", false),
    PURCHASE_INVOICE("Purchase Invoice", true),
    PURCHASE_RETURN("Purchase Return", true),
    PURCHASE_ORDER("Purchase Order", true),
    QUOTATION("Quotation", true),
    DELIVERY_CHALLAN("Delivery Challan", true),
    CREDIT_NOTE("Credit Note", true),
    DEBIT_NOTE("Debit Note", true),
    PAYMENT_RECEIPT("Payment Receipt", true),
    SALES_INVOICE("Sales Invoice", true),
    SALES_RETURN("Sales Return", true),
    CUSTOM_ERP("Custom ERP Document", true);

    private final String label;
    private final boolean erpConnected;

    DocumentType(String label, boolean erpConnected) {
        this.label = label;
        this.erpConnected = erpConnected;
    }

    public String label() { return label; }
    public boolean isErpConnected() { return erpConnected; }
    public boolean isGeneral() { return this == GENERAL_PDF; }
    @Override public String toString() { return label; }
}
