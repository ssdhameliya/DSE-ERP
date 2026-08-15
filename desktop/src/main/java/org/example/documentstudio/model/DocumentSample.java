package org.example.documentstudio.model;

/** Lightweight selector entry for real ERP data preview inside Document Studio. */
public record DocumentSample(String id, String label) {
    @Override public String toString() { return label == null || label.isBlank() ? id : label; }
}
