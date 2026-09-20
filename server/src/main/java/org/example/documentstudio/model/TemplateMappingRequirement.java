package org.example.documentstudio.model;

import java.util.List;

/**
 * User-facing business requirement for an ERP PDF template.
 *
 * <p>Requirements intentionally describe business concepts rather than JSON keys. A concept may
 * be satisfied by several ERP fields (for example Product Description can use Description,
 * Description + Remarks, or the legacy Remarks column). This keeps template validation stable
 * when users choose different visual presentations.</p>
 */
public record TemplateMappingRequirement(
        String id,
        String label,
        String category,
        Level level,
        Kind kind,
        List<String> acceptedFields,
        List<String> searchAliases,
        String explanation,
        String fixInstruction) {

    public enum Level { REQUIRED, CONDITIONAL, RECOMMENDED, OPTIONAL }
    public enum Kind { FIELD, ITEM_COLUMN, STRUCTURE }

    public TemplateMappingRequirement {
        id = safe(id);
        label = safe(label);
        category = safe(category);
        level = level == null ? Level.OPTIONAL : level;
        kind = kind == null ? Kind.FIELD : kind;
        acceptedFields = acceptedFields == null ? List.of() : List.copyOf(acceptedFields);
        searchAliases = searchAliases == null ? List.of() : List.copyOf(searchAliases);
        explanation = safe(explanation);
        fixInstruction = safe(fixInstruction);
    }

    public boolean blocksDefault() { return level == Level.REQUIRED; }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
