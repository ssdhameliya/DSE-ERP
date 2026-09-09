package org.example.documentstudio.model;

/** High-level library grouping used by the universal Document Studio. */
public enum TemplateCategory {
    GENERAL_PDF("General PDF"),
    ERP_TEMPLATE("ERP Template");

    private final String label;
    TemplateCategory(String label) { this.label = label; }
    public String label() { return label; }
    @Override public String toString() { return label; }
}
