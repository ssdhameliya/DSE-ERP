package org.example.documentstudio.model;

/** User-facing ERP field shown in the designer. */
public record TemplateFieldDefinition(String key, String label, String category, boolean image) {
    @Override public String toString() { return label; }
}
