package org.example.documentstudio.model;

/** Document Studio is intentionally limited to Purchase Invoice in DSE ERP 7.2.5. */
public enum DocumentType {
    PURCHASE_INVOICE("Purchase Invoice");

    private final String label;

    DocumentType(String label) { this.label = label; }

    public String label() { return label; }

    @Override public String toString() { return label; }
}
