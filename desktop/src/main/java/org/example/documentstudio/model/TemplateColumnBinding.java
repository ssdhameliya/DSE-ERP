package org.example.documentstudio.model;

/**
 * Source-aware mapping for one physical PDF table column.
 *
 * <p>The source header and geometry belong to the imported PDF. The field key belongs to the
 * ERP data contract. Keeping them separate lets PDF Studio map templates whose labels and column
 * order differ without rewriting the original artwork.</p>
 */
public class TemplateColumnBinding {
    private String sourceLabel = "";
    private String fieldKey = "";
    /** X offset from the table left edge in PDF points. */
    private double xOffset;
    /** Physical source column width in PDF points. */
    private double width;
    private String alignment = "LEFT";
    /** 0..1 confidence for the current/detected semantic suggestion. */
    private double confidence;
    /** Original detector suggestion retained even after the user overrides the mapping. */
    private String autoDetectedFieldKey = "";
    private double autoDetectedConfidence;
    /** AUTO, CONFIRMED, MANUAL_OVERRIDE, REVIEW_REQUIRED, UNMAPPED or STATIC. */
    private String mappingState = "";

    public TemplateColumnBinding() { }

    public TemplateColumnBinding(String sourceLabel, String fieldKey, double xOffset, double width,
                                 String alignment, double confidence) {
        setSourceLabel(sourceLabel);
        setFieldKey(fieldKey);
        setXOffset(xOffset);
        setWidth(width);
        setAlignment(alignment);
        setConfidence(confidence);
    }

    public TemplateColumnBinding copy() {
        TemplateColumnBinding copy = new TemplateColumnBinding(sourceLabel, fieldKey, xOffset, width, alignment, confidence);
        copy.autoDetectedFieldKey = autoDetectedFieldKey;
        copy.autoDetectedConfidence = autoDetectedConfidence;
        copy.mappingState = mappingState;
        return copy;
    }

    public String getSourceLabel() { return sourceLabel == null ? "" : sourceLabel; }
    public void setSourceLabel(String sourceLabel) { this.sourceLabel = sourceLabel == null ? "" : sourceLabel.trim(); }
    public String getFieldKey() { return fieldKey == null ? "" : fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey == null ? "" : fieldKey.trim(); }
    public double getXOffset() { return xOffset; }
    public void setXOffset(double xOffset) { this.xOffset = finiteNonNegative(xOffset); }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = Math.max(0, Double.isFinite(width) ? width : 0); }
    public String getAlignment() { return alignment == null || alignment.isBlank() ? "LEFT" : alignment; }
    public void setAlignment(String alignment) {
        String value = alignment == null ? "LEFT" : alignment.trim().toUpperCase(java.util.Locale.ROOT);
        this.alignment = switch (value) { case "CENTER", "RIGHT" -> value; default -> "LEFT"; };
    }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) {
        this.confidence = bounded(confidence);
    }
    public String getAutoDetectedFieldKey() { return autoDetectedFieldKey == null ? "" : autoDetectedFieldKey; }
    public void setAutoDetectedFieldKey(String value) { autoDetectedFieldKey = value == null ? "" : value.trim(); }
    public double getAutoDetectedConfidence() { return autoDetectedConfidence; }
    public void setAutoDetectedConfidence(double value) { autoDetectedConfidence = bounded(value); }
    public String getMappingState() {
        if (mappingState != null && !mappingState.isBlank()) return normalizeState(mappingState);
        return getFieldKey().isBlank() ? "UNMAPPED" : "CONFIRMED";
    }
    public void setMappingState(String value) { mappingState = normalizeState(value); }
    public void markAutoDetected(String fieldKey, double detectedConfidence) {
        setAutoDetectedFieldKey(fieldKey);
        setAutoDetectedConfidence(detectedConfidence);
        setFieldKey(fieldKey);
        setConfidence(detectedConfidence);
        setMappingState(fieldKey == null || fieldKey.isBlank() ? "REVIEW_REQUIRED" : "AUTO");
    }
    public void markUserMapping(String fieldKey) {
        setFieldKey(fieldKey);
        setConfidence(1.0);
        if (fieldKey == null || fieldKey.isBlank()) setMappingState("UNMAPPED");
        else if (!getAutoDetectedFieldKey().isBlank() && !getAutoDetectedFieldKey().equals(fieldKey)) setMappingState("MANUAL_OVERRIDE");
        else setMappingState("CONFIRMED");
    }
    private static String normalizeState(String value) {
        String state = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (state) {
            case "AUTO", "CONFIRMED", "MANUAL_OVERRIDE", "REVIEW_REQUIRED", "UNMAPPED", "STATIC" -> state;
            default -> "";
        };
    }
    private static double bounded(double value) {
        return Math.max(0, Math.min(1, Double.isFinite(value) ? value : 0));
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }
}
