package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
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
                        }
                    } catch (Exception error) {
                        throw new IOException(template.getDocumentType().label() + " default certification failed for " +
                                taxMode + " / " + lineCount + " items" + (chargeCount > 0 ? " / " + chargeCount + " charges" : "") +
                                ": " + root(error) + ". Fix: open Item Table, increase the dynamic row area or reduce row height, and preview the 25-item case before making this template Default.", error);
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
                    ? seed.getDescription()+" - extended production description used to certify wrapping and pagination safety"
                    : seed.getDescription()+" "+String.format("%02d",i);
            String remarks = i % 5 == 0
                    ? "Extended technical remark line for PDF Studio default certification and continuation-page validation"
                    : seed.getRemarks();
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
    private static String root(Throwable error){Throwable r=error;while(r.getCause()!=null&&r.getCause()!=r)r=r.getCause();String m=r.getMessage();return m==null||m.isBlank()?r.getClass().getSimpleName():m;}
}
