package org.example.documentstudio.service;

import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateElement;
import org.example.documentstudio.model.TemplateFieldDefinition;
import org.example.documentstudio.model.TemplateColumnBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Single mutation path for user-driven PDF Studio field mapping.
 * The controller and regression tests both call this service so a manually mapped
 * template is exercised through the same mapping semantics as the UI Map button.
 */
public final class ManualTemplateMappingService {
    private ManualTemplateMappingService() {}

    public static void mapField(TemplateElement element, TemplateFieldDefinition field) {
        if (element == null) throw new IllegalArgumentException("Select a PDF element before mapping an ERP field.");
        if (field == null) throw new IllegalArgumentException("Select an ERP field before mapping.");
        boolean compatible = field.image() ? isImageLike(element) : isTextLike(element);
        if (!compatible) {
            throw new IllegalArgumentException(field.image()
                    ? "The selected PDF element is not an image/image field."
                    : "The selected PDF element is not a text/field element.");
        }
        element.markUserMapping(field.key());
        if (field.image()) {
            element.setType(ElementType.IMAGE_FIELD);
            element.setText(field.label());
            element.setFillEnabled(false);
            element.setStrokeEnabled(false);
        } else {
            if (element.getType() == ElementType.TEXT) element.setType(ElementType.FIELD);
            element.setText("{{" + field.key() + "}}");
            applyFieldSemantics(element, field);
        }
    }

    /**
     * Apply the centralized PDF field semantic contract. Runtime layout decisions must not be
     * inferred from field-key substrings in controllers.
     */
    public static void applyFieldSemantics(TemplateElement element, TemplateFieldDefinition field) {
        if (element == null || field == null || field.image()) return;
        element.setTextFit(field.textFit());
        if (field.multiline()) {
            element.setLineSpacing(Math.max(1.05, element.getLineSpacing()));
            element.setAutoHeight(field.autoHeight());
            element.setGrowthDirection(field.growthDirection());
            element.setOverflowPolicy(field.overflowPolicy());
            if (element.getFlowRole().isBlank() && !field.blockRole().isBlank()) element.setFlowRole(field.blockRole());
        }
    }

    /** Backward-compatible entry point delegated to the centralized PDF catalogue. */
    public static void configureMultilineMapping(TemplateElement element, String fieldKey) {
        if (element == null || fieldKey == null || fieldKey.isBlank()) return;
        TemplateFieldDefinition field = TemplateFieldCatalog.find(fieldKey);
        if (field != null) applyFieldSemantics(element, field);
    }

    /** Persist an already-detected physical source block as the runtime flow group for multiline fields. */
    public static void applyDetectedFlowBlock(TemplateElement element, TemplateFieldDefinition field, String blockId) {
        if (element == null || field == null || !field.multiline()) return;
        applyFieldSemantics(element, field);
        if (blockId != null && !blockId.isBlank()) element.setFlowGroupId(blockId.trim());
    }

    /** Add one item-column mapping exactly as the Item Table inspector does. */
    public static void mapItemColumn(TemplateElement table, String fieldKey) {
        mapItemColumn(table, fieldKey, -1);
    }

    /**
     * Maps a physical source header cell when columnIndex is known. Existing templates without
     * source-aware bindings keep the legacy append-in-order behaviour.
     */
    public static void mapItemColumn(TemplateElement table, String fieldKey, int columnIndex) {
        if (table == null || table.getType() != ElementType.ITEM_TABLE)
            throw new IllegalArgumentException("Select an Item Table before mapping item columns.");
        String normalized = normalizeItemColumn(fieldKey);
        if (normalized.isBlank()) throw new IllegalArgumentException("Select an item field before mapping the column.");

        List<TemplateColumnBinding> bindings = new ArrayList<>(table.getTableColumnBindings());
        if (!bindings.isEmpty() && columnIndex >= 0 && columnIndex < bindings.size()) {
            // One ERP meaning may belong to only one physical source column. Clear an older duplicate.
            for (int i = 0; i < bindings.size(); i++) {
                TemplateColumnBinding binding = bindings.get(i).copy();
                if (i != columnIndex && normalizeItemColumn(binding.getFieldKey()).equals(normalized)) binding.setFieldKey("");
                bindings.set(i, binding);
            }
            TemplateColumnBinding target = bindings.get(columnIndex).copy();
            target.markUserMapping("item." + normalized);
            bindings.set(columnIndex, target);
            table.setTableColumnBindings(bindings);
            syncLegacyColumns(table);
            return;
        }

        List<String> columns = new ArrayList<>(table.getTableColumns());
        if (!columns.contains(normalized)) columns.add(normalized);
        table.setTableColumns(columns);
    }

    public static String normalizeItemColumn(String fieldKey) {
        String normalized = fieldKey == null ? "" : fieldKey.trim();
        if (normalized.startsWith("item.")) normalized = normalized.substring("item.".length());
        return switch (normalized) {
            case "qty" -> "quantity";
            case "discount" -> "discountPercent";
            case "gst" -> "gstPercent";
            case "amount" -> "total";
            default -> normalized;
        };
    }


    /**
     * Re-detection may discover a cleaner physical header layout than an older detector version.
     * Preserve any explicit user mapping for the same printed header, but allow newly detected
     * high-confidence meanings to fill previously blank headers.
     */
    public static List<TemplateColumnBinding> mergeDetectedColumnBindings(List<TemplateColumnBinding> existing,
                                                                          List<TemplateColumnBinding> detected) {
        if (detected == null || detected.isEmpty()) return List.of();
        List<TemplateColumnBinding> prior = existing == null ? List.of() : existing;
        List<TemplateColumnBinding> merged = new ArrayList<>();
        for (TemplateColumnBinding candidate : detected) {
            TemplateColumnBinding next = candidate.copy();
            String label = PdfAutoMappingService.normalize(candidate.getSourceLabel());
            TemplateColumnBinding best = prior.stream()
                    .filter(b -> PdfAutoMappingService.normalize(b.getSourceLabel()).equals(label))
                    .min(java.util.Comparator.comparingDouble(b -> Math.abs(b.getXOffset() - candidate.getXOffset())))
                    .orElse(null);
            if (best != null) {
                String state = best.getMappingState();
                if ("STATIC".equals(state)) {
                    // Keep Static is an explicit user decision even though it intentionally has no ERP field.
                    next.setAutoDetectedFieldKey(candidate.getAutoDetectedFieldKey());
                    next.setAutoDetectedConfidence(candidate.getAutoDetectedConfidence());
                    next.setFieldKey("");
                    next.setConfidence(Math.max(best.getConfidence(), next.getConfidence()));
                    next.setMappingState("STATIC");
                } else if (("CONFIRMED".equals(state) || "MANUAL_OVERRIDE".equals(state))
                        && best.getFieldKey() != null && !best.getFieldKey().isBlank()) {
                    next.setAutoDetectedFieldKey(candidate.getAutoDetectedFieldKey());
                    next.setAutoDetectedConfidence(candidate.getAutoDetectedConfidence());
                    next.setFieldKey(best.getFieldKey());
                    next.setConfidence(Math.max(best.getConfidence(), next.getConfidence()));
                    next.setMappingState(state);
                } else {
                    // AUTO mappings may refresh when a newer detector has a clearer semantic result.
                    next.setMappingState(candidate.getMappingState());
                }
            }
            merged.add(next);
        }
        return merged;
    }


    /**
     * Returns detached source-aware bindings for an older Item Table that only persisted legacy
     * tableColumns/tableColumnWidths/tableColumnAlignments. Opening Review Mapping must still show
     * those mappings even before the template is re-saved in the current schema.
     */
    public static List<TemplateColumnBinding> legacyItemBindings(TemplateElement table) {
        if (table == null || table.getType() != ElementType.ITEM_TABLE) return List.of();
        List<String> columns = table.getTableColumns();
        if (columns == null || columns.isEmpty()) return List.of();
        List<Double> widths = table.getTableColumnWidths();
        List<String> alignments = table.getTableColumnAlignments();
        int count = columns.size();
        double fallbackWidth = Math.max(1, table.getWidth() / Math.max(1, count));
        double x = 0;
        List<TemplateColumnBinding> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String normalized = normalizeItemColumn(columns.get(i));
            if (normalized.isBlank()) continue;
            double width = i < widths.size() && widths.get(i) != null && Double.isFinite(widths.get(i)) && widths.get(i) > 0
                    ? widths.get(i) : fallbackWidth;
            String alignment = i < alignments.size() ? alignments.get(i) : itemHeaderAlignment("item." + normalized);
            String fieldKey = "item." + normalized;
            TemplateColumnBinding binding = new TemplateColumnBinding(legacySourceLabel(normalized), fieldKey, x, width, alignment, 1.0);
            binding.setAutoDetectedFieldKey(fieldKey);
            binding.setAutoDetectedConfidence(1.0);
            binding.setMappingState("CONFIRMED");
            out.add(binding);
            x += width;
        }
        return List.copyOf(out);
    }

    /**
     * Hydrates current source-aware Item bindings from an older legacy mapping. When current source
     * header detections are supplied and the column count matches, their physical labels/geometry
     * are retained while the already-saved ERP semantics remain authoritative.
     */
    public static boolean hydrateLegacyItemBindings(TemplateElement table, List<TemplateColumnBinding> detected) {
        if (table == null || table.getType() != ElementType.ITEM_TABLE || !table.getTableColumnBindings().isEmpty()) return false;
        List<String> legacy = table.getTableColumns();
        if (legacy == null || legacy.isEmpty()) return false;
        List<TemplateColumnBinding> next = new ArrayList<>();
        List<Double> legacyWidths = table.getTableColumnWidths();
        List<String> legacyAlignments = table.getTableColumnAlignments();
        boolean hasStableLegacyGeometry = legacyWidths != null && legacyWidths.size() == legacy.size()
                && legacyWidths.stream().allMatch(w -> w != null && Double.isFinite(w) && w > 0);
        if (detected != null && detected.size() == legacy.size()) {
            double xOffset = 0;
            for (int i = 0; i < legacy.size(); i++) {
                String normalized = normalizeItemColumn(legacy.get(i));
                if (normalized.isBlank()) continue;
                TemplateColumnBinding binding = detected.get(i).copy();
                String fieldKey = "item." + normalized;
                // Detection is authoritative for the printed label, but an already-rendering legacy
                // template owns the physical table geometry. Replacing its proven widths with OCR/text
                // midpoint estimates can visibly move body grid lines after a metadata-only upgrade.
                // Preserve those widths/x offsets whenever they are complete; current user edits still
                // update the legacy mirror through syncLegacyColumns().
                if (hasStableLegacyGeometry) {
                    double width = legacyWidths.get(i);
                    binding.setXOffset(xOffset);
                    binding.setWidth(width);
                    xOffset += width;
                    if (legacyAlignments != null && i < legacyAlignments.size())
                        binding.setAlignment(legacyAlignments.get(i));
                }
                binding.setFieldKey(fieldKey);
                binding.setConfidence(1.0);
                if (binding.getAutoDetectedFieldKey().isBlank()) binding.setAutoDetectedFieldKey(fieldKey);
                if (binding.getAutoDetectedConfidence() <= 0) binding.setAutoDetectedConfidence(binding.getConfidence());
                binding.setMappingState("CONFIRMED");
                next.add(binding);
            }
        } else {
            next.addAll(legacyItemBindings(table));
        }
        if (next.isEmpty()) return false;
        table.setTableColumnBindings(next);
        syncLegacyColumns(table);
        return true;
    }

    /** Builds source-header bindings using the same physical geometry rules as Detect Item Headers. */
    public static List<TemplateColumnBinding> sourceColumnBindings(PdfAutoMappingService.ItemHeaderLayout header, TemplateElement table) {
        if (header == null || table == null) return List.of();
        List<PdfAutoMappingService.ItemHeaderCell> cells = header.cells().stream()
                .sorted(java.util.Comparator.comparingDouble(PdfAutoMappingService.ItemHeaderCell::x)).toList();
        List<TemplateColumnBinding> out = new ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i);
            double left = i == 0 ? table.getX() : (cells.get(i-1).x() + cells.get(i-1).width() + cell.x()) / 2.0;
            double right = i == cells.size()-1 ? table.getX()+table.getWidth() : (cell.x()+cell.width()+cells.get(i+1).x()) / 2.0;
            left = Math.max(table.getX(), left);
            right = Math.min(table.getX()+table.getWidth(), right);
            String key = cell.confidence() >= .90 ? cell.suggestedField() : "";
            if (!key.isBlank() && !used.add(key)) key = "";
            TemplateColumnBinding binding = new TemplateColumnBinding(cell.label(), key, Math.max(0,left-table.getX()),
                    Math.max(8,right-left), itemHeaderAlignment(key), cell.confidence());
            binding.setAutoDetectedFieldKey(cell.suggestedField());
            binding.setAutoDetectedConfidence(cell.confidence());
            binding.setMappingState(key.isBlank() ? "REVIEW_REQUIRED" : "AUTO");
            out.add(binding);
        }
        return List.copyOf(out);
    }

    static String itemHeaderAlignment(String fieldKey) {
        String k = fieldKey == null ? "" : fieldKey;
        if (k.endsWith("quantity") || k.endsWith("unit") || k.contains("Percent") || k.endsWith("serial")) return "CENTER";
        if (k.endsWith("rate") || k.endsWith("total") || k.contains("Amount") || k.endsWith("taxable")) return "RIGHT";
        return "LEFT";
    }

    private static String legacySourceLabel(String normalized) {
        return switch (normalized) {
            case "serial" -> "SR. NO.";
            case "hsn" -> "HSN CODE";
            case "description", "descriptionWithRemarks" -> "PRODUCT DESCRIPTION";
            case "quantity" -> "QTY";
            case "rate" -> "UNIT RATE";
            case "unit" -> "UNIT";
            case "taxable" -> "AMOUNT";
            case "discountPercent" -> "DISCOUNT";
            case "gstPercent" -> "GST %";
            case "total" -> "TOTAL";
            default -> normalized.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_',' ').toUpperCase(java.util.Locale.ROOT);
        };
    }

    /** Keeps old renderers/tests/packages interoperable while bindings are the source of truth. */
    public static void syncLegacyColumns(TemplateElement table) {
        if (table == null || table.getTableColumnBindings().isEmpty()) return;
        List<String> columns = new ArrayList<>();
        List<Double> widths = new ArrayList<>();
        List<String> alignments = new ArrayList<>();
        for (TemplateColumnBinding binding : table.getTableColumnBindings()) {
            String key = normalizeItemColumn(binding.getFieldKey());
            if (key.isBlank()) continue;
            columns.add(key);
            widths.add(binding.getWidth());
            alignments.add(binding.getAlignment());
        }
        table.setTableColumns(columns);
        if (widths.stream().allMatch(w -> w > 0)) table.setTableColumnWidths(widths);
        table.setTableColumnAlignments(alignments);
    }

    private static boolean isTextLike(TemplateElement e) {
        return e.getType() == ElementType.TEXT || e.getType() == ElementType.FIELD;
    }

    private static boolean isImageLike(TemplateElement e) {
        return e.getType() == ElementType.IMAGE || e.getType() == ElementType.IMAGE_FIELD;
    }
}
