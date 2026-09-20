package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.documentstudio.model.*;
import org.example.invoice.model.TaxInvoiceItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Generic production gate for line-item PDF Studio defaults.
 *
 * <p>The same certification policy is used for Sales, Purchase, Quotation, Sales Return and
 * Purchase Return. Templates own appearance; this gate protects the shared document-flow rule
 * from one-row-per-page layouts and broken continuation behavior.</p>
 */
final class PdfDefaultCertification {
    private PdfDefaultCertification() {}

    static void validate(DocumentTemplate template) throws IOException {
        if (template == null || !certifiedType(template.getDocumentType())) return;
        TemplateData base = TemplateDataFactory.sampleFor(template.getDocumentType());
        List<String> taxModes = List.of("GST", "IGST");
        List<Integer> chargeCounts = TemplateFieldCatalog.supportsChargeRows(template.getDocumentType()) ? List.of(0, 3) : List.of(0);
        for (String taxMode : taxModes) {
            for (int lineCount : List.of(5, 25)) {
                for (int chargeCount : chargeCounts) {
                    TemplateData data = scenario(base, template.getDocumentType(), taxMode, lineCount, chargeCount);
                    Path output = Files.createTempFile("pdf-default-certification-", ".pdf");
                    try {
                        PdfTemplateRenderer.render(template, data, output);
                        try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
                            int pages = pdf.getNumberOfPages();
                            if (pages < 1) throw new IOException("the renderer produced zero pages");
                            if (lineCount == 5 && pages != 1) {
                                throw new IOException("5 items generated " + pages + " pages. A normal short document must remain on one page");
                            }
                            if (lineCount == 25 && pages > 3) {
                                throw new IOException("25 items generated " + pages + " pages. The Item Table is not using the shared continuation-page flow");
                            }
                            String text = new PDFTextStripper().getText(pdf);
                            requireText(text, data.value("document.number"), "document number");
                            requireText(text, data.value("party.name"), "party name");
                            requireText(text, "CERT ITEM 01", "first item row");
                            requireText(text, data.value("party.billingGstin"), "party GSTIN value");
                            if ("IGST".equals(taxMode)) {
                                requireText(text, "IGST", "IGST tax row");
                                // A Financial Summary deliberately masks and redraws the complete source
                                // calculation region. PDF text extraction still sees covered background text,
                                // so stale-token extraction is meaningful only when no such structural mask exists.
                                if (!hasDynamicFinancialSummary(template) && (containsToken(text,"CGST") || containsToken(text,"SGST")))
                                    throw new IOException("IGST scenario still contains stale CGST/SGST labels from the source PDF");
                            } else {
                                requireText(text, "CGST", "CGST tax row");
                                requireText(text, "SGST", "SGST tax row");
                            }
                            if (TemplateMappingValidationService.mappedFields(template).contains("document.paymentTerms"))
                                requireText(text, "CERT-PAYMENT-TERMS", "payment terms");
                        }
                    } catch (Exception error) {
                        String cause = root(error);
                        String lower = cause.toLowerCase(java.util.Locale.ROOT);
                        String fix = (lower.contains("payment") || lower.contains("cert-payment-terms"))
                                ? "Fix: keep Payment Terms in its bounded multiline financial-summary area; it must wrap without clipping before making this template Default."
                                : "Fix: open Item Table, increase the dynamic row area or reduce row height, and preview the 25-item case before making this template Default.";
                        throw new IOException(template.getDocumentType().label() + " default certification failed for " +
                                taxMode + " / " + lineCount + " items" + (chargeCount > 0 ? " / " + chargeCount + " charges" : "") +
                                ": " + cause + ". " + fix, error);
                    } finally {
                        Files.deleteIfExists(output);
                    }
                }
            }
        }
    }

    private static boolean certifiedType(DocumentType type) {
        return type == DocumentType.SALES_INVOICE || type == DocumentType.PURCHASE_INVOICE ||
                type == DocumentType.PURCHASE_ORDER || type == DocumentType.QUOTATION ||
                type == DocumentType.SALES_RETURN || type == DocumentType.PURCHASE_RETURN;
    }

    private static TemplateData scenario(TemplateData base, DocumentType type, String taxMode, int lineCount, int chargeCount) {
        var values = new LinkedHashMap<>(base.values());
        String certNumber = "CERT-" + taxMode + "-" + lineCount + "-" + chargeCount;
        String certParty = "CERT PARTY " + taxMode;
        String billingAddress = "CERT-ADDRESS-ALPHA First line\nCERT-ADDRESS-BETA Second line\nCERT-ADDRESS-GAMMA Third line";
        String deliveryAddress = "CERT-DELIVERY-ALPHA First line\nCERT-DELIVERY-BETA Second line";
        String certGstin = "24CERTGSTIN0001Z";
        // Set canonical values and the document-specific aliases that normalize() treats as
        // authoritative. Otherwise sample alias values can overwrite certification sentinels.
        values.put("document.number", certNumber);
        values.put("sales.number", certNumber);
        values.put("party.name", certParty);
        values.put("customer.name", certParty);
        values.put("party.billingAddress", billingAddress);
        values.put("sales.billingAddress", billingAddress);
        values.put("customer.address", billingAddress);
        values.put("party.deliveryAddress", deliveryAddress);
        values.put("sales.deliveryAddress", deliveryAddress);
        values.put("party.billingGstin", certGstin);
        values.put("sales.billingGstin", certGstin);
        values.put("sales.gstin", certGstin);
        values.put("customer.gstin", certGstin);
        values.put("party.deliveryGstin", certGstin);
        values.put("sales.deliveryGstin", certGstin);
        values.put("document.paymentTerms", "CERT-PAYMENT-TERMS\nSecond payment line\nThird payment line");
        values.put("sales.paymentTerms", "CERT-PAYMENT-TERMS\nSecond payment line\nThird payment line");
        values.put("sales.gstType", taxMode);
        values.put("purchase.gstType", taxMode);
        values.put("totals.cgstAmount", "GST".equals(taxMode) ? "2,250.00" : "0.00");
        values.put("totals.sgstAmount", "GST".equals(taxMode) ? "2,250.00" : "0.00");
        values.put("totals.igstAmount", "IGST".equals(taxMode) ? "4,500.00" : "0.00");
        values.put("totals.gstAmount", "4,500.00");
        values.put("totals.grandTotal", "29,500.00");
        values.put("totals.roundedGrandTotal", "29,500.00");
        values.put("totals.amountInWords", "INR : Twenty Nine Thousand Five Hundred Only");
        values.put("totals.amountInWordsText", "Twenty Nine Thousand Five Hundred Only");

        TaxInvoiceItem seed = base.items().isEmpty()
                ? new TaxInvoiceItem(1,"8481","Certification item","Long technical item remark for pagination verification",1,"NOS",1000,0,18)
                : base.items().getFirst();
        List<TaxInvoiceItem> items = new ArrayList<>();
        for (int i=1;i<=lineCount;i++) {
            String description = i % 4 == 0
                    ? "CERT ITEM "+String.format("%02d",i)+" - extended production description used to certify wrapping and pagination safety"
                    : "CERT ITEM "+String.format("%02d",i)+" "+seed.getDescription();
            String remarks = "CERT ITEM "+String.format("%02d",i)+" " + (i % 5 == 0
                    ? "Extended technical remark line for PDF Studio default certification and continuation-page validation"
                    : seed.getRemarks());
            items.add(new TaxInvoiceItem(i,seed.getHsn(),description,remarks,1+(i%3),seed.getUnit(),seed.getRate(),
                    seed.getDiscountPercent(),seed.getGstPercent(),seed.getItemCode(),seed.getCategory(),seed.getBrand(),
                    seed.getMaterial(),seed.getSize(),seed.getLocation(),seed.getPurchasePrice(),seed.getSellingPrice(),
                    seed.getAvailableStock(),seed.getOpeningStock(),seed.getMinimumStock(),seed.getReservedStock(),
                    seed.getMasterGstPercent(),seed.getMasterDiscountPercent()));
        }
        List<TemplateCharge> charges = chargeCount == 0 ? List.of() :
                List.of(charge("FREIGHT",500), charge("PACKING",250), charge("INSURANCE",100));
        return new TemplateData(values, base.images(), items, charges, taxMode);
    }

    private static TemplateCharge charge(String type,double amount){double tax=amount*.18;return new TemplateCharge(type,amount,true,18,tax,amount+tax);}
    private static void requireText(String body,String expected,String label) throws IOException {
        if(expected==null||expected.isBlank())return;
        String normalized=(body==null?"":body).replaceAll("\\s+"," ");
        String needle=expected.replaceAll("\\s+"," ").trim();
        if(!normalized.contains(needle)) throw new IOException("required rendered "+label+" is missing: "+needle);
    }
    private static boolean hasDynamicFinancialSummary(DocumentTemplate template) {
        return template != null && template.getElements().stream().anyMatch(e -> e != null
                && e.getType() == ElementType.BLOCK
                && PdfStyleResolver.effectivelyVisible(template, e)
                && "DYNAMIC_FINANCIAL_SUMMARY".equals(e.getReplacementGroupId()));
    }
    private static boolean containsToken(String body,String token){return java.util.regex.Pattern.compile("(?i)\\b"+java.util.regex.Pattern.quote(token)+"\\b").matcher(body==null?"":body).find();}
    private static String root(Throwable error){Throwable r=error;while(r.getCause()!=null&&r.getCause()!=r)r=r.getCause();String m=r.getMessage();return m==null||m.isBlank()?r.getClass().getSimpleName():m;}
}
