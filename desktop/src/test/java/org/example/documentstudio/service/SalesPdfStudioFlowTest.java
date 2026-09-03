package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ElementType;
import org.example.config.WorkspaceManager;
import org.example.model.Party;
import org.example.model.Sales;
import org.example.model.SalesCharge;
import org.example.model.SalesLine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SalesPdfStudioFlowTest {

    @Test
    void liveSalesFieldsMapToStablePdfStudioJsonAliases() {
        Sales sale = sale("PDF-MAP-001", 2, false);
        ObjectNode json = ErpDocumentJsonService.toJson(DocumentType.SALES_INVOICE, TemplateDataFactory.fromSales(sale));

        assertEquals("PDF-MAP-001", json.path("document").path("number").asText());
        assertEquals("03/09/2026", json.path("document").path("date").asText());
        assertEquals("PO-PDF-7788", json.path("document").path("poNumber").asText());
        assertEquals("PDF Studio Customer", json.path("party").path("name").asText());
        assertEquals("Billing Address PDF Studio, Ahmedabad", json.path("party").path("billingAddress").asText());
        assertEquals("Delivery Address PDF Studio, Surat", json.path("party").path("deliveryAddress").asText());
        assertEquals("PDF Transport", json.path("transport").path("name").asText());
        assertEquals("24TRPDF1234A1Z5", json.path("transport").path("gstin").asText());
        assertEquals("+91 98989 77889", json.path("transport").path("contact").asText());
        assertEquals(2, json.path("items").size());
        assertEquals("PDF Item 01", json.path("items").get(0).path("description").asText());
        assertEquals("PCS", json.path("items").get(0).path("unit").asText());
        assertFalse(json.path("totals").path("basicAmount").asText().isBlank());
        assertFalse(json.path("totals").path("amountInWordsText").asText().isBlank());
    }

    @Test
    void builtInTemplateRendersSingleAndMultiplePageSalesWithoutChangingSourcePageGeometry() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        if (!WorkspaceManager.isConfigured()) WorkspaceManager.configure(evidence.resolve("workspace"));
        Path root = TemplateStorageService.root();
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate template = TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE)
                .orElseThrow(() -> new AssertionError("Built-in Sales Invoice PDF Studio template is not active"));
        assertEquals("STRICT_FIXED", template.getLayoutMode());
        assertEquals(2, template.getDataContractVersion(), "9.0.61 built-in template must use universal JSON contract v2");
        var billingGstin = template.getElements().stream()
                .filter(e -> e.getType() == ElementType.FIELD && "party.billingGstin".equals(e.getFieldKey()))
                .findFirst().orElseThrow();
        var deliveryGstin = template.getElements().stream()
                .filter(e -> e.getType() == ElementType.FIELD && "party.deliveryGstin".equals(e.getFieldKey()))
                .findFirst().orElseThrow();
        assertEquals(58.6688, billingGstin.getX(), 0.0001);
        assertEquals(337.6388, deliveryGstin.getX(), 0.0001);
        assertEquals(224.05, billingGstin.getY(), 0.0001, "Billing GSTIN baseline must match the original PDF row");
        assertEquals(224.05, deliveryGstin.getY(), 0.0001, "Delivery GSTIN baseline must match the original PDF row");

        Path single = evidence.resolve("sales-pdf-studio-single.pdf");
        Path multi = evidence.resolve("sales-pdf-studio-multi.pdf");

        PdfTemplateRenderer.render(template, TemplateDataFactory.fromSales(sale("PDF-SINGLE-001", 5, false)), single);
        PdfTemplateRenderer.render(template, TemplateDataFactory.fromSales(sale("PDF-MULTI-001", 25, true)), multi);

        try (PDDocument source = Loader.loadPDF(TemplateStorageService.sourcePdf(template).toFile());
             PDDocument singleDoc = Loader.loadPDF(single.toFile());
             PDDocument multiDoc = Loader.loadPDF(multi.toFile())) {
            assertEquals(1, source.getNumberOfPages());
            assertEquals(1, singleDoc.getNumberOfPages(), "Five lines must stay on one page");
            assertEquals(2, multiDoc.getNumberOfPages(), "Twenty-five lines must paginate to two pages");

            float sourceW = source.getPage(0).getMediaBox().getWidth();
            float sourceH = source.getPage(0).getMediaBox().getHeight();
            for (int i = 0; i < multiDoc.getNumberOfPages(); i++) {
                assertEquals(sourceW, multiDoc.getPage(i).getMediaBox().getWidth(), 0.001f);
                assertEquals(sourceH, multiDoc.getPage(i).getMediaBox().getHeight(), 0.001f);
                assertEquals(source.getPage(0).getRotation(), multiDoc.getPage(i).getRotation());
                for (COSName fontName : multiDoc.getPage(i).getResources().getFontNames()) {
                    assertNotNull(multiDoc.getPage(i).getResources().getFont(fontName),
                            "Every mapped page must own a valid PDF font resource: " + fontName.getName());
                }
            }
        }
    }

    private static Sales sale(String invoiceNo, int lineCount, boolean longDescriptions) {
        Sales sale = new Sales();
        sale.setInvoiceNo(invoiceNo);
        sale.setInvoiceDate(LocalDate.of(2026, 9, 3));
        sale.setDueDate(LocalDate.of(2026, 10, 3));
        sale.setReferenceNo("PO-PDF-7788");
        sale.setOrderNo("SO-PDF-1001");
        sale.setPoDate(LocalDate.of(2026, 9, 1));
        sale.setPaymentTerms("30 Days");
        sale.setTransporter("PDF Transport");
        sale.setTransporterGstin("24TRPDF1234A1Z5");
        sale.setContactPersonMobile("+91 98989 77889");
        sale.setVehicleNumber("GJ-01-PDF-1001");
        sale.setBillingAddress("Billing Address PDF Studio, Ahmedabad");
        sale.setDeliveryAddress("Delivery Address PDF Studio, Surat");
        sale.setBillingGstin("24PDFCU1234A1Z5");
        sale.setDeliveryGstin("24PDFCU1234A1Z5");
        sale.setGstin("24PDFCU1234A1Z5");
        sale.setGstType("GST");
        sale.setSameAsBilling(false);
        sale.setPaymentStatus("PENDING");
        sale.setDocumentStatus("APPROVED");

        Party customer = new Party();
        customer.setId(9001);
        customer.setPartyType("CUSTOMER");
        customer.setPartyCode("PDF-CUST-001");
        customer.setName("PDF Studio Customer");
        customer.setAddress("Customer master address");
        customer.setGstin("24PDFCU1234A1Z5");
        customer.setPhone("+91 98765 10001");
        customer.setEmail("pdf.customer@example.test");
        sale.setCustomer(customer);

        List<SalesLine> lines = new ArrayList<>();
        double subtotal = 0;
        double tax = 0;
        for (int i = 1; i <= lineCount; i++) {
            SalesLine line = new SalesLine();
            line.setItemCode(String.format("PDF-%03d", i));
            line.setItemDescription(longDescriptions
                    ? String.format("PDF Item %02d - multi-page mapped description with stable fixed-layout rendering", i)
                    : String.format("PDF Item %02d", i));
            line.setItemHsn("8483");
            line.setItemUnit("PCS");
            line.setItemRemarks("Mapped line " + i);
            line.setQuantity(i % 3 + 1);
            line.setRate(100 + i * 7.5);
            line.setDiscountPercent(i % 4 == 0 ? 2.5 : 0);
            line.setGstPercent(18);
            line.recalculate();
            subtotal += line.getNetAmount();
            tax += line.getGstAmount();
            lines.add(line);
        }
        sale.setLines(lines);
        sale.setSubtotal(subtotal);
        sale.setDiscountAmount(lines.stream().mapToDouble(SalesLine::getDiscountAmount).sum());
        sale.setGstAmount(tax);
        sale.setCharges(List.of(new SalesCharge("Freight", 500, false, 0)));
        sale.setTotalAmount(subtotal + tax + 500);
        return sale;
    }
}
