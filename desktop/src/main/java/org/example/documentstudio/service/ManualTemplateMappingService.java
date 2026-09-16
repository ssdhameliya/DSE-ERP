package org.example.documentstudio.service;

import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateElement;
import org.example.documentstudio.model.TemplateFieldDefinition;

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
        element.setFieldKey(field.key());
        if (field.image()) {
            element.setType(ElementType.IMAGE_FIELD);
            element.setText(field.label());
            element.setFillEnabled(false);
            element.setStrokeEnabled(false);
        } else {
            if (element.getType() == ElementType.TEXT) element.setType(ElementType.FIELD);
            element.setText("{{" + field.key() + "}}");
        }
    }

    /** Add one item-column mapping exactly as the Item Table inspector does. */
    public static void mapItemColumn(TemplateElement table, String fieldKey) {
        if (table == null || table.getType() != ElementType.ITEM_TABLE)
            throw new IllegalArgumentException("Select an Item Table before mapping item columns.");
        String normalized = fieldKey == null ? "" : fieldKey.trim();
        if (normalized.startsWith("item.")) normalized = normalized.substring("item.".length());
        normalized = switch (normalized) {
            case "qty" -> "quantity";
            case "discount" -> "discountPercent";
            case "gst" -> "gstPercent";
            case "amount" -> "total";
            default -> normalized;
        };
        if (normalized.isBlank()) throw new IllegalArgumentException("Select an item field before mapping the column.");
        List<String> columns = new ArrayList<>(table.getTableColumns());
        if (!columns.contains(normalized)) columns.add(normalized);
        table.setTableColumns(columns);
    }

    private static boolean isTextLike(TemplateElement e) {
        return e.getType() == ElementType.TEXT || e.getType() == ElementType.FIELD;
    }

    private static boolean isImageLike(TemplateElement e) {
        return e.getType() == ElementType.IMAGE || e.getType() == ElementType.IMAGE_FIELD;
    }
}
