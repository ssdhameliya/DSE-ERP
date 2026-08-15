package org.example.documentstudio.service;

import org.example.api.quotation.QuotationApiClient;
import org.example.dao.PurchaseDAO;
import org.example.documentstudio.model.DocumentSample;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateData;
import org.example.model.Purchase;

import java.util.List;
import java.util.Objects;

/**
 * Document-type data gateway used by the universal designer.
 *
 * <p>It deliberately keeps the editor independent from Purchase/Quotation
 * persistence details. New ERP document types can be added here without
 * creating another PDF designer.</p>
 */
public final class DocumentDataService {
    private DocumentDataService() {}

    public static List<DocumentSample> listSamples(DocumentType type) {
        if (type == null || !type.isErpConnected()) return List.of();
        try {
            return switch (type) {
                case PURCHASE_INVOICE, PURCHASE_ORDER -> new PurchaseDAO().getAll().stream()
                        .filter(Objects::nonNull)
                        .filter(p -> p.getInvoiceNo() != null && !p.getInvoiceNo().isBlank())
                        .limit(150)
                        .map(p -> new DocumentSample(p.getInvoiceNo(), p.getInvoiceNo() + supplierSuffix(p)))
                        .toList();
                case QUOTATION -> new QuotationApiClient().list().stream()
                        .filter(Objects::nonNull)
                        .limit(150)
                        .map(q -> new DocumentSample(q.no(), q.no() + textSuffix(q.customer())))
                        .toList();
                default -> List.of();
            };
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static TemplateData load(DocumentType type, String sampleId) {
        if (sampleId == null || sampleId.isBlank()) return TemplateDataFactory.sampleFor(type);
        return switch (type) {
            case PURCHASE_INVOICE, PURCHASE_ORDER -> loadPurchase(sampleId);
            case QUOTATION -> loadQuotation(sampleId);
            default -> TemplateDataFactory.sampleFor(type);
        };
    }

    public static TemplateData sample(DocumentType type) {
        return TemplateDataFactory.sampleFor(type);
    }

    private static TemplateData loadPurchase(String invoiceNo) {
        try {
            Purchase full = new PurchaseDAO().getByInvoice(invoiceNo);
            if (full == null) throw new IllegalStateException("The selected purchase could not be loaded.");
            return TemplateDataFactory.fromPurchase(full);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("The selected purchase could not be loaded.", error);
        }
    }

    private static TemplateData loadQuotation(String quotationNo) {
        QuotationApiClient api = new QuotationApiClient();
        QuotationApiClient.QuoteDto quote = api.list().stream()
                .filter(q -> q != null && quotationNo.equalsIgnoreCase(q.no()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The selected quotation could not be loaded."));
        return TemplateDataFactory.fromQuotation(quote, api.lines(quote.id()));
    }

    private static String supplierSuffix(Purchase purchase) {
        try {
            if (purchase.getSupplier() != null && purchase.getSupplier().getName() != null && !purchase.getSupplier().getName().isBlank())
                return "  •  " + purchase.getSupplier().getName();
        } catch (Exception ignored) { }
        return "";
    }

    private static String textSuffix(String value) {
        return value == null || value.isBlank() ? "" : "  •  " + value;
    }
}
