package org.example.documentstudio.service;

import org.example.documentstudio.model.DocumentType;
import org.example.util.ProfessionalDocumentRenderer;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry for ERP document types that are safe to generate automatically.
 *
 * <p>Document Studio may design many document types, but only registered flows
 * are allowed to become live runtime defaults. This keeps future document
 * additions independent from Purchase/Email/WhatsApp controllers.</p>
 */
public final class DocumentFlowRegistry {
    public record Flow(DocumentType type, String outputPrefix, String builtInLabel,
                       ProfessionalDocumentRenderer.Kind fallbackKind) { }

    private static final Map<DocumentType, Flow> FLOWS = new EnumMap<>(DocumentType.class);

    static {
        register(DocumentType.SALES_INVOICE, "Sales-Tax-Invoice-", "Built-in Sales",
                ProfessionalDocumentRenderer.Kind.SALES_INVOICE);
        register(DocumentType.PURCHASE_INVOICE, "Purchase-Tax-Invoice-", "Built-in Purchase",
                ProfessionalDocumentRenderer.Kind.PURCHASE_INVOICE);
        register(DocumentType.PURCHASE_RETURN, "Purchase-Return-Note-", "Built-in Purchase Return",
                ProfessionalDocumentRenderer.Kind.PURCHASE_REFUND);
        register(DocumentType.QUOTATION, "Quotation-", "Built-in Quotation",
                ProfessionalDocumentRenderer.Kind.QUOTATION);
    }

    private DocumentFlowRegistry() { }

    private static void register(DocumentType type, String outputPrefix, String builtInLabel,
                                 ProfessionalDocumentRenderer.Kind fallbackKind) {
        FLOWS.put(type, new Flow(type, outputPrefix, builtInLabel, fallbackKind));
    }

    public static Optional<Flow> flow(DocumentType type) { return Optional.ofNullable(FLOWS.get(type)); }

    /** Existing PDF/runtime automation. Kept intentionally narrow so PDF fallback behavior is unchanged. */
    public static boolean isAutomatic(DocumentType type) { return type != null && FLOWS.containsKey(type); }

    /**
     * Excel has a built-in workbook fallback for every ERP-connected document type.
     * This is deliberately independent from the PDF registry because some document types do not yet
     * have a ProfessionalDocumentRenderer.Kind.
     */
    public static boolean isExcelAutomatic(DocumentType type) {
        return type != null && type.isErpConnected();
    }

    public static String excelBuiltInLabel(DocumentType type) {
        return isExcelAutomatic(type) ? "Built-in " + type.label() + " Excel" : "Design only";
    }

    public static String builtInLabel(DocumentType type) {
        return flow(type).map(Flow::builtInLabel).orElse("Design only");
    }
}
