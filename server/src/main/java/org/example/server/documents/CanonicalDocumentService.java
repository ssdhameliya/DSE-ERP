package org.example.server.documents;

import org.example.config.ConfigManager;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.service.ExcelTemplateRenderer;
import org.example.documentstudio.service.ExcelTemplateStorageService;
import org.example.documentstudio.service.PdfStudioRenderer;
import org.example.documentstudio.service.TemplateStorageService;
import org.example.invoice.pdf.TaxInvoicePdfGenerator;
import org.example.server.authority.ServerResourceService;
import org.example.server.audit.AuditService;
import org.example.server.operations.BusinessOperationsService;
import org.example.server.security.CurrentUser;
import org.example.server.support.SupportService;
import org.example.server.quotation.QuotationService;
import org.example.server.returns.ReturnService;
import org.example.util.ProfessionalDocumentRenderer;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Single canonical renderer for Desktop Shared Client and Mobile.
 * Both clients receive the exact bytes produced here from the server-owned
 * record,
 * server-owned business configuration and server-mirrored Document Studio
 * default.
 */
@Service
public class CanonicalDocumentService {
    private static final String BUSINESS_ASSET = "BUSINESS_ASSET";
    private final BusinessOperationsService operations;
    private final SupportService support;
    private final ServerResourceService resources;
    private final CanonicalTemplateStore templates;
    private final CanonicalDocumentDataFactory dataFactory;
    private final IssuedSalesPdfStore issuedSalesPdf;
    private final AuditService audit;
    private final QuotationService quotationService;
    private final ReturnService returnService;

    public CanonicalDocumentService(BusinessOperationsService operations, SupportService support,
            ServerResourceService resources, CanonicalTemplateStore templates,
            CanonicalDocumentDataFactory dataFactory, IssuedSalesPdfStore issuedSalesPdf,
            AuditService audit, QuotationService quotationService, ReturnService returnService) {
        this.operations = operations;
        this.support = support;
        this.resources = resources;
        this.templates = templates;
        this.dataFactory = dataFactory;
        this.issuedSalesPdf = issuedSalesPdf;
        this.audit = audit;
        this.quotationService = quotationService;
        this.returnService = returnService;
    }

    public Rendered render(String typeValue, String number, String formatValue) throws Exception {
        DocumentType type = parseType(typeValue);
        Format format = parseFormat(formatValue);
        if (number == null || number.isBlank())
            throw new IllegalArgumentException("A valid document number is required.");
        requireView(type);
        String documentNo = number.trim();
        Path work = Files.createTempDirectory("canonical-document-");
        try (Assets assets = loadAssets(work)) {
            Map<String, String> config = loadConfig();
            assets.applyTo(config);
            Path output = work.resolve(fileName(type, documentNo, format));
            if (type == DocumentType.SALES_INVOICE) {
                var sale = operations.sale(documentNo);
                if (format == Format.PDF) {
                    boolean official = SalesPdfLifecycle.official(sale.documentStatus());
                    String finalName = fileName(type, documentNo, format);
                    if (official) {
                        var issued = issuedSalesPdf.find(sale);
                        if (issued.isPresent()) {
                            auditRequired(sale.id(), "PDF_ACCESSED",
                                    documentNo + " • ISSUED_SNAPSHOT • rowVersion=" + sale.rowVersion());
                            return new Rendered(finalName, format.contentType, issued.get());
                        }
                    }
                    String authority = renderSalesPdf(sale, config, assets, output);
                    validate(output, format, documentNo);
                    byte[] bytes = Files.readAllBytes(output);
                    if (official)
                        issuedSalesPdf.save(sale, finalName, bytes);
                    auditRequired(sale.id(), "DOCUMENT_RENDERED",
                            documentNo + " • PDF • " + authority + " • rowVersion=" + sale.rowVersion());
                    return new Rendered(finalName, format.contentType, bytes);
                }
                renderExcel(type, dataFactory.sales(sale, config, assets.templateImages()), output, "SALE", sale.id(),
                        documentNo);
            } else if (type == DocumentType.PURCHASE_INVOICE) {
                var purchase = operations.purchase(documentNo);
                if (format == Format.PDF)
                    renderPurchasePdf(purchase, config, assets, output);
                else
                    renderExcel(type, dataFactory.purchase(purchase, config, assets.templateImages()), output,
                            "PURCHASE", purchase.id(), documentNo);
            } else if (type == DocumentType.QUOTATION) {
                var quote = quotationService.quoteByNumber(documentNo);
                if (quote == null)
                    throw new IllegalArgumentException("Quotation not found: " + documentNo);
                var lines = quotationService.lines(quote.id());
                if (format == Format.PDF)
                    renderQuotationPdf(quote, lines, config, assets, output);
                else
                    renderExcel(type, dataFactory.quotation(quote, lines, config, assets.templateImages()), output,
                            "QUOTATION", quote.id(), documentNo);
            } else if (type == DocumentType.SALES_RETURN || type == DocumentType.CREDIT_NOTE) {
                var ret = returnService.details(documentNo);
                if (ret == null)
                    throw new IllegalArgumentException("Return not found: " + documentNo);
                if (format == Format.PDF)
                    renderReturnPdf(ret, true, config, assets, output);
                else
                    renderExcel(DocumentType.SALES_RETURN, dataFactory.salesReturn(ret, config, assets.templateImages()), output,
                            "SALES_RETURN", 0, documentNo);
            } else if (type == DocumentType.PURCHASE_RETURN || type == DocumentType.DEBIT_NOTE) {
                var ret = returnService.details(documentNo);
                if (ret == null)
                    throw new IllegalArgumentException("Return not found: " + documentNo);
                if (format == Format.PDF)
                    renderReturnPdf(ret, false, config, assets, output);
                else
                    renderExcel(DocumentType.PURCHASE_RETURN, dataFactory.purchaseReturn(ret, config, assets.templateImages()), output,
                            "PURCHASE_RETURN", 0, documentNo);
            }
            validate(output, format, documentNo);
            return new Rendered(fileName(type, documentNo, format), format.contentType, Files.readAllBytes(output));
        } finally {
            CanonicalTemplateStore.deleteTree(work);
        }
    }

    private String renderSalesPdf(org.example.server.operations.OperationDtos.SaleDto sale, Map<String, String> config,
            Assets assets, Path output) throws Exception {
        String studioFailure = null;
        try (var selected = templates.pdf(DocumentType.SALES_INVOICE).orElse(null)) {
            if (selected != null) {
                try {
                    var data = dataFactory.sales(sale, config, assets.templateImages());
                    ConfigManager.withValues(config, () -> {
                        TemplateStorageService.withRoot(selected.activeRoot(),
                                () -> PdfStudioRenderer.render(selected.template(), data, output));
                        return output;
                    });
                    validate(output, Format.PDF, sale.invoiceNo());
                    return "PDF_STUDIO:" + safeTemplate(selected.template().getId()) + ":v"
                            + selected.template().getActiveVersion();
                } catch (Exception failure) {
                    studioFailure = rootMessage(failure);
                    Files.deleteIfExists(output);
                    auditRequired(sale.id(), "PDF_STUDIO_FALLBACK", sale.invoiceNo() + " • " + studioFailure);
                }
            }
        } catch (Exception failure) {
            studioFailure = rootMessage(failure);
            Files.deleteIfExists(output);
            auditRequired(sale.id(), "PDF_STUDIO_FALLBACK", sale.invoiceNo() + " • " + studioFailure);
        }
        ConfigManager.withValues(config, () -> {
            TaxInvoicePdfGenerator.generate(dataFactory.salesBuiltIn(sale, config, assets.logo, assets.signature), output,
                    TaxInvoicePdfGenerator.Presentation.FULL);
            return output;
        });
        validate(output, Format.PDF, sale.invoiceNo());
        return studioFailure == null ? "STANDARD:NO_ACTIVE_STUDIO" : "STANDARD:FALLBACK";
    }

    private void renderPurchasePdf(org.example.server.operations.OperationDtos.PurchaseDto purchase,
            Map<String, String> config, Assets assets, Path output) throws Exception {
        String studioFailure = null;
        try (var selected = templates.pdf(DocumentType.PURCHASE_INVOICE).orElse(null)) {
            if (selected != null) {
                try {
                    var data = dataFactory.purchase(purchase, config, assets.templateImages());
                    TemplateStorageService.withRoot(selected.activeRoot(),
                            () -> PdfStudioRenderer.render(selected.template(), data, output));
                    validate(output, Format.PDF, purchase.invoiceNo());
                    return;
                } catch (Exception failure) {
                    studioFailure = rootMessage(failure);
                    Files.deleteIfExists(output);
                    audit.log("PURCHASE", purchase.id(), "PDF_STUDIO_FALLBACK",
                            purchase.invoiceNo() + " • " + studioFailure);
                }
            }
        } catch (Exception failure) {
            studioFailure = rootMessage(failure);
            Files.deleteIfExists(output);
            audit.log("PURCHASE", purchase.id(), "PDF_STUDIO_FALLBACK", purchase.invoiceNo() + " • " + studioFailure);
        }
        ConfigManager.withValues(config, () -> {
            ProfessionalDocumentRenderer.render(output, assets.logo, dataFactory.professionalPurchase(purchase, config),
                    ProfessionalDocumentRenderer.Kind.PURCHASE_INVOICE);
            return output;
        });
        validate(output, Format.PDF, purchase.invoiceNo());
    }

    private void renderQuotationPdf(org.example.server.quotation.QuotationDtos.QuoteDto quote,
            List<org.example.server.quotation.QuotationDtos.LineDto> lines,
            Map<String, String> config, Assets assets, Path output) throws Exception {
        String studioFailure = null;
        try (var selected = templates.pdf(DocumentType.QUOTATION).orElse(null)) {
            if (selected != null) {
                try {
                    var data = dataFactory.quotation(quote, lines, config, assets.templateImages());
                    TemplateStorageService.withRoot(selected.activeRoot(),
                            () -> PdfStudioRenderer.render(selected.template(), data, output));
                    validate(output, Format.PDF, quote.no());
                    return;
                } catch (Exception failure) {
                    studioFailure = rootMessage(failure);
                    Files.deleteIfExists(output);
                    audit.log("QUOTATION", quote.id(), "PDF_STUDIO_FALLBACK", quote.no() + " • " + studioFailure);
                }
            }
        } catch (Exception failure) {
            studioFailure = rootMessage(failure);
            Files.deleteIfExists(output);
            audit.log("QUOTATION", quote.id(), "PDF_STUDIO_FALLBACK", quote.no() + " • " + studioFailure);
        }
        ConfigManager.withValues(config, () -> {
            ProfessionalDocumentRenderer.render(output, assets.logo,
                    dataFactory.professionalQuotation(quote, lines, config),
                    ProfessionalDocumentRenderer.Kind.QUOTATION);
            return output;
        });
        validate(output, Format.PDF, quote.no());
    }

    private void renderReturnPdf(org.example.server.returns.ReturnDtos.Details ret, boolean sales,
            Map<String, String> config, Assets assets, Path output) throws Exception {
        DocumentType dt = sales ? DocumentType.SALES_RETURN : DocumentType.PURCHASE_RETURN;
        ProfessionalDocumentRenderer.Kind kind = sales ? ProfessionalDocumentRenderer.Kind.SALES_REFUND
                : ProfessionalDocumentRenderer.Kind.PURCHASE_REFUND;
        String entity = sales ? "SALES_RETURN" : "PURCHASE_RETURN";
        String studioFailure = null;
        try (var selected = templates.pdf(dt).orElse(null)) {
            if (selected != null) {
                try {
                    var data = sales ? dataFactory.salesReturn(ret, config, assets.templateImages())
                            : dataFactory.purchaseReturn(ret, config, assets.templateImages());
                    TemplateStorageService.withRoot(selected.activeRoot(),
                            () -> PdfStudioRenderer.render(selected.template(), data, output));
                    validate(output, Format.PDF, ret.no());
                    return;
                } catch (Exception failure) {
                    studioFailure = rootMessage(failure);
                    Files.deleteIfExists(output);
                    audit.log(entity, 0, "PDF_STUDIO_FALLBACK", ret.no() + " • " + studioFailure);
                }
            }
        } catch (Exception failure) {
            studioFailure = rootMessage(failure);
            Files.deleteIfExists(output);
            audit.log(entity, 0, "PDF_STUDIO_FALLBACK", ret.no() + " • " + studioFailure);
        }
        ConfigManager.withValues(config, () -> {
            ProfessionalDocumentRenderer.render(output, assets.logo,
                    sales ? dataFactory.professionalSalesReturn(ret, config)
                            : dataFactory.professionalPurchaseReturn(ret, config),
                    kind);
            return output;
        });
        validate(output, Format.PDF, ret.no());
    }

    private void renderExcel(DocumentType type, org.example.documentstudio.model.TemplateData data, Path output,
            String entity, Number entityId, String number) throws Exception {
        String studioFailure = null;
        try (var selected = templates.excel(type).orElse(null)) {
            if (selected != null) {
                try {
                    ExcelTemplateStorageService.withRoot(selected.root(),
                            () -> ExcelTemplateRenderer.render(selected.template(), data, List.of(), output));
                    return;
                } catch (Exception failure) {
                    studioFailure = rootMessage(failure);
                    Files.deleteIfExists(output);
                }
            }
        } catch (Exception failure) {
            studioFailure = rootMessage(failure);
            Files.deleteIfExists(output);
        }
        if (studioFailure != null)
            audit.log(entity, entityId, "EXCEL_STUDIO_FALLBACK", number + " • " + studioFailure);
        ExcelTemplateRenderer.renderBuiltIn(type, data, List.of(), output);
    }

    private Map<String, String> loadConfig() {
        Map<String, String> c = new HashMap<>();
        List<String> keys = List.of(
                "company.name", "company.address", "company.shipAddress", "company.gstin", "company.phone",
                "company.email", "company.alternateEmail", "company.website", "company.terms",
                "company.certificationText", "company.dateFormat", "company.timeZone",
                "payment.bankName", "payment.branch", "payment.accountNumber", "payment.ifsc", "payment.accountType",
                "payment.mode", "payment.accountHolder", "date.format", "timezone.business");
        for (String key : keys)
            c.put(key, support.setting(key, ""));
        String dateFmt = c.get("company.dateFormat");
        if (dateFmt == null || dateFmt.isBlank()) dateFmt = c.get("date.format");
        if (dateFmt != null && !dateFmt.isBlank()) {
            String norm = org.example.server.util.BusinessClock.normalizeDatePattern(dateFmt);
            c.put("company.dateFormat", norm);
            c.put("date.format", norm);
        }
        return c;
    }

    private Assets loadAssets(Path work) throws java.io.IOException {
        Path logo = asset(work, "company.logoPath", "logo"),
                signature = asset(work, "company.signaturePath", "signature"),
                qr = asset(work, "payment.qrImagePath", "qr");
        return new Assets(work, logo, signature, qr);
    }

    private Path asset(Path work, String key, String fallbackName) throws java.io.IOException {
        try {
            var f = resources.get(BUSINESS_ASSET, key);
            String original = f.fileName() == null ? "" : f.fileName();
            String ext = original.contains(".")
                    ? original.substring(original.lastIndexOf('.')).replaceAll("[^A-Za-z0-9.]", "")
                    : ".bin";
            Path path = work.resolve(fallbackName + ext);
            Files.write(path, f.content());
            return path;
        } catch (IllegalArgumentException missing) {
            if (missing.getMessage() != null && missing.getMessage().contains("Server resource not found"))
                return null;
            throw missing;
        }
    }

    private static void validate(Path file, Format format, String number) throws Exception {
        if (!Files.isRegularFile(file) || Files.size(file) < 100)
            throw new IllegalStateException("Document generation failed for " + number + ".");
        byte[] b = Files.readAllBytes(file);
        if (format == Format.PDF) {
            if (b.length < 4 || b[0] != '%' || b[1] != 'P' || b[2] != 'D' || b[3] != 'F')
                throw new IllegalStateException("Generated PDF is invalid for " + number + ".");
        } else if (b.length < 4 || b[0] != 'P' || b[1] != 'K')
            throw new IllegalStateException("Generated Excel workbook is invalid for " + number + ".");
    }

    private void auditRequired(Number saleId, String action, String detail) {
        audit.log("SALE", saleId, action, detail);
    }

    private static String safeTemplate(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root)
            root = root.getCause();
        String m = root.getMessage();
        return m == null || m.isBlank() ? root.getClass().getSimpleName() : m;
    }

    private static DocumentType parseType(String value) {
        String n = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "SALE", "SALES", "SALES_INVOICE" -> DocumentType.SALES_INVOICE;
            case "PURCHASE", "PURCHASES", "PURCHASE_INVOICE" -> DocumentType.PURCHASE_INVOICE;
            case "QUOTATION", "QUOTE" -> DocumentType.QUOTATION;
            case "SALES_RETURN", "CREDIT_NOTE", "SALE_RETURN" -> DocumentType.SALES_RETURN;
            case "PURCHASE_RETURN", "DEBIT_NOTE" -> DocumentType.PURCHASE_RETURN;
            default -> throw new IllegalArgumentException(
                    "Unsupported document type: " + value + ". Supported types are Sales, Purchase, Quotation, Sales Return, and Purchase Return.");
        };
    }

    private static Format parseFormat(String value) {
        try {
            return Format.valueOf(value == null ? "PDF" : value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Format must be PDF or XLSX.");
        }
    }

    private static void requireView(DocumentType type) {
        if (type == DocumentType.SALES_INVOICE || type == DocumentType.SALES_RETURN || type == DocumentType.CREDIT_NOTE || type == DocumentType.QUOTATION)
            CurrentUser.requirePermission("SALES.VIEW", "Render " + type.label());
        else
            CurrentUser.requirePermission("PURCHASE.VIEW", "Render " + type.label());
    }

    private static String fileName(DocumentType type, String no, Format f) {
        String prefix = switch (type) {
            case SALES_INVOICE -> "Sales-Tax-Invoice-";
            case PURCHASE_INVOICE -> "Purchase-Invoice-";
            case QUOTATION -> "Quotation-";
            case SALES_RETURN, CREDIT_NOTE -> "Sales-Return-Credit-Note-";
            case PURCHASE_RETURN, DEBIT_NOTE -> "Purchase-Return-Debit-Note-";
            default -> type.name().replace('_', '-') + "-";
        };
        return prefix + no.replaceAll("[\\\\/:*?\"<>|]", "_") + f.extension;
    }

    private enum Format {
        PDF(".pdf", "application/pdf"),
        XLSX(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        final String extension, contentType;

        Format(String e, String c) {
            extension = e;
            contentType = c;
        }
    }

    public record Rendered(String fileName, String contentType, byte[] bytes) {
    }

    private static final class Assets implements AutoCloseable {
        final Path work, logo, signature, qr;

        Assets(Path w, Path l, Path s, Path q) {
            work = w;
            logo = l;
            signature = s;
            qr = q;
        }

        void applyTo(Map<String, String> c) {
            if (logo != null)
                c.put("company.logoPath", logo.toString());
            if (signature != null)
                c.put("company.signaturePath", signature.toString());
            if (qr != null)
                c.put("payment.qrImagePath", qr.toString());
        }

        Map<String, Path> templateImages() {
            Map<String, Path> m = new HashMap<>();
            if (logo != null)
                m.put("company.logo", logo);
            if (signature != null)
                m.put("company.signature", signature);
            if (qr != null) {
                m.put("payment.qr", qr);
                m.put("payment.qrImage", qr);
            }
            return m;
        }

        public void close() {
        }
    }
}
