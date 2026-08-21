package org.example.documentstudio.service;

import org.example.api.quotation.QuotationApiClient;
import org.example.api.returns.ReturnApiClient;
import org.example.dao.PurchaseDAO;
import org.example.documentstudio.model.DocumentSample;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateData;
import org.example.model.Purchase;
import org.example.model.Sales;
import org.example.service.SalesService;

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
                case SALES_INVOICE -> new SalesService().getAll().stream()
                        .filter(Objects::nonNull)
                        .filter(s -> s.getInvoiceNo() != null && !s.getInvoiceNo().isBlank())
                        .limit(150)
                        .map(s -> new DocumentSample(s.getInvoiceNo(), s.getInvoiceNo() + customerSuffix(s)))
                        .toList();
                case PURCHASE_INVOICE, PURCHASE_ORDER -> new PurchaseDAO().getAll().stream()
                        .filter(Objects::nonNull)
                        .filter(p -> p.getInvoiceNo() != null && !p.getInvoiceNo().isBlank())
                        .limit(150)
                        .map(p -> new DocumentSample(p.getInvoiceNo(), p.getInvoiceNo() + supplierSuffix(p)))
                        .toList();
                case SALES_RETURN -> new ReturnApiClient().list("SALES RETURN").stream()
                        .filter(Objects::nonNull)
                        .limit(150)
                        .map(r -> new DocumentSample(r.no(), r.no() + textSuffix(r.party())))
                        .toList();
                case PURCHASE_RETURN -> new ReturnApiClient().list("PURCHASE RETURN").stream()
                        .filter(Objects::nonNull)
                        .limit(150)
                        .map(r -> new DocumentSample(r.no(), r.no() + textSuffix(r.party())))
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
            case SALES_INVOICE -> loadSales(sampleId);
            case PURCHASE_INVOICE, PURCHASE_ORDER -> loadPurchase(sampleId);
            case PURCHASE_RETURN -> loadPurchaseReturn(sampleId);
            case SALES_RETURN -> loadSalesReturn(sampleId);
            case QUOTATION -> loadQuotation(sampleId);
            default -> throw new IllegalStateException((type == null ? "This document type" : type.label()) + " does not yet have a live ERP record connector. Sample data is available only inside Document Studio preview.");
        };
    }

    public static TemplateData sample(DocumentType type) {
        return TemplateDataFactory.sampleFor(type);
    }

    /** True when Excel/PDF Studio can select an actual persisted ERP record for this document type. */
    public static boolean supportsRealData(DocumentType type) {
        return type == DocumentType.SALES_INVOICE
                || type == DocumentType.PURCHASE_INVOICE
                || type == DocumentType.PURCHASE_ORDER
                || type == DocumentType.PURCHASE_RETURN
                || type == DocumentType.SALES_RETURN
                || type == DocumentType.QUOTATION;
    }


    private static TemplateData loadSales(String invoiceNo) {
        try {
            Sales full = new SalesService().getByInvoice(invoiceNo);
            if (full == null) throw new IllegalStateException("The selected Sale could not be loaded.");
            return TemplateDataFactory.fromSales(full);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("The selected Sale could not be loaded.", error);
        }
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

    private static TemplateData loadPurchaseReturn(String returnNo) {
        try {
            ReturnApiClient.Details details = new ReturnApiClient().details(returnNo);
            Purchase original = null;
            if (details != null && details.invoice() != null && !details.invoice().isBlank()) {
                try { original = new PurchaseDAO().getByInvoice(details.invoice()); } catch (Exception ignored) { }
            }
            return TemplateDataFactory.fromPurchaseReturn(details, original);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("The selected purchase return could not be loaded.", error);
        }
    }

    private static TemplateData loadSalesReturn(String returnNo) {
        try {
            ReturnApiClient.Details details = new ReturnApiClient().details(returnNo);
            Sales original = null;
            if (details != null && details.invoice() != null && !details.invoice().isBlank()) {
                try { original = new SalesService().getByInvoice(details.invoice()); } catch (Exception ignored) { }
            }
            return TemplateDataFactory.fromSalesReturn(details, original);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("The selected sales return could not be loaded.", error);
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

    private static String customerSuffix(Sales sale) {
        try {
            if (sale.getCustomer() != null && sale.getCustomer().getName() != null && !sale.getCustomer().getName().isBlank())
                return "  •  " + sale.getCustomer().getName();
        } catch (Exception ignored) { }
        return "";
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
