package org.example.documentstudio.model;

import java.util.List;
import java.util.Locale;

/**
 * User-facing ERP field shown in the designer together with its PDF Studio semantic contract.
 *
 * <p>PDF Studio must not infer runtime layout from field-key substrings inside controllers.  The
 * catalogue supplies aliases and content behaviour, while source analysis supplies the physical
 * block/table geometry.  Review Mapping can therefore show the same semantic contract that
 * Preview/Validate/Publish/Runtime later consume.</p>
 */
public record TemplateFieldDefinition(
        String key,
        String label,
        String category,
        boolean image,
        List<String> aliases,
        ContentMode contentMode,
        String textFit,
        boolean autoHeight,
        String growthDirection,
        String overflowPolicy,
        String blockRole
) {
    public enum ContentMode { SINGLE_LINE, MULTILINE, IMAGE, DYNAMIC_ROLE }

    /** Backward-compatible constructor used by non-PDF catalogues and existing callers. */
    public TemplateFieldDefinition(String key, String label, String category, boolean image) {
        this(key, label, category, image, List.of(), image ? ContentMode.IMAGE : ContentMode.SINGLE_LINE,
                image ? "SHRINK" : "SHRINK", false, "FIXED", "ERROR", "");
    }

    public TemplateFieldDefinition {
        key = key == null ? "" : key.trim();
        label = label == null ? key : label.trim();
        category = category == null ? "" : category.trim();
        aliases = aliases == null ? List.of() : aliases.stream()
                .filter(java.util.Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).distinct().toList();
        contentMode = contentMode == null ? (image ? ContentMode.IMAGE : ContentMode.SINGLE_LINE) : contentMode;
        textFit = normalizeTextFit(textFit, contentMode == ContentMode.MULTILINE ? "WRAP" : "SHRINK");
        growthDirection = normalizeGrowth(growthDirection, autoHeight ? "DOWN" : "FIXED");
        overflowPolicy = normalizeOverflow(overflowPolicy, autoHeight ? "ERROR" : "ERROR");
        blockRole = blockRole == null ? "" : blockRole.trim().toUpperCase(Locale.ROOT);
    }

    public boolean multiline() { return contentMode == ContentMode.MULTILINE; }

    public String behaviorSummary() {
        if (image) return "Image";
        if (!multiline()) return "Single Line • " + textFit;
        return "Multiline • " + textFit + (autoHeight ? " • Auto Height" : "")
                + " • Grow " + pretty(growthDirection);
    }

    private static String normalizeTextFit(String value, String fallback) {
        String v = value == null ? fallback : value.trim().toUpperCase(Locale.ROOT);
        return switch (v) { case "WRAP", "CLIP" -> v; default -> "SHRINK"; };
    }

    private static String normalizeGrowth(String value, String fallback) {
        String v = value == null ? fallback : value.trim().toUpperCase(Locale.ROOT);
        return switch (v) { case "DOWN", "UP", "BOTH" -> v; default -> "FIXED"; };
    }

    private static String normalizeOverflow(String value, String fallback) {
        String v = value == null ? fallback : value.trim().toUpperCase(Locale.ROOT);
        return switch (v) { case "PAGINATE", "SHRINK", "CLIP" -> v; default -> "ERROR"; };
    }

    private static String pretty(String value) {
        if (value == null || value.isBlank()) return "Fixed";
        String v = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }

    @Override public String toString() { return label; }
}
