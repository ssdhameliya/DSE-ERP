package org.example.documentstudio.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateElement;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfStudioTemplatePackageCompatibilityTest {

    @Test
    void packageNormalizerRebuildsItemReviewMetadataFromProtectedSourcePdf() throws Exception {
        Path pdf = Files.createTempFile("pdf-studio-legacy-item-package-", ".pdf");
        try {
            try (PDDocument doc = new PDDocument()) {
                PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                    String[] labels = {"SR. NO.","HSN CODE","PRODUCT DESCRIPTION","QTY","UNIT RATE","UNIT","AMOUNT (INR)"};
                    float[] xs = {25,70,125,405,435,485,515};
                    for (int i=0;i<labels.length;i++) {
                        cs.beginText(); cs.setFont(font,8); cs.newLineAtOffset(xs[i],560); cs.showText(labels[i]); cs.endText();
                    }
                    cs.setStrokingColor(117/255f,153/255f,198/255f); cs.setLineWidth(.45f);
                    float[] gridX = {24f,58f,106f,402f,432f,480f,511f,571f};
                    float top = 572f, bottom = 232f;
                    for (float gx : gridX) { cs.moveTo(gx,top); cs.lineTo(gx,bottom); cs.stroke(); }
                    for (float gy=top; gy>=bottom; gy-=18f) { cs.moveTo(24,gy); cs.lineTo(571,gy); cs.stroke(); }
                }
                doc.save(pdf.toFile());
            }

            DocumentTemplate template = new DocumentTemplate();
            template.setDocumentType(DocumentType.SALES_INVOICE);
            TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE,0,24,250,546,360);
            table.setTableColumns(List.of("serial","hsn","descriptionWithRemarks","quantity","rate","unit","taxable"));
            // Simulate the V18 regression: semantics are correct, but old text-midpoint geometry is wrong.
            table.setTableColumnWidths(List.of(35d,97d,196d,78d,50d,29d,62d));
            table.setTableColumnAlignments(List.of("CENTER","CENTER","LEFT","CENTER","RIGHT","CENTER","RIGHT"));
            table.setUseSourceTableDesign(true);
            table.setHeaderHeight(17.2);
            table.setRowHeight(18.0);
            template.setElements(List.of(table));

            PdfStudioTemplatePackageService.normalizeItemReviewMetadata(template,pdf);

            assertEquals(7,table.getTableColumnBindings().size());
            assertEquals("SR. NO.",table.getTableColumnBindings().get(0).getSourceLabel());
            assertEquals("item.serial",table.getTableColumnBindings().get(0).getFieldKey());
            assertEquals("PRODUCT DESCRIPTION",table.getTableColumnBindings().get(2).getSourceLabel());
            assertEquals("item.descriptionWithRemarks",table.getTableColumnBindings().get(2).getFieldKey());
            assertEquals("UNIT RATE",table.getTableColumnBindings().get(4).getSourceLabel());
            assertEquals("item.rate",table.getTableColumnBindings().get(4).getFieldKey());
            assertEquals("AMOUNT (INR)",table.getTableColumnBindings().get(6).getSourceLabel());
            assertEquals("item.taxable",table.getTableColumnBindings().get(6).getFieldKey());
            assertEquals(34d, table.getTableColumnWidths().get(0), 1.0);
            assertEquals(48d, table.getTableColumnWidths().get(1), 1.0);
            assertEquals(296d, table.getTableColumnWidths().get(2), 1.0,
                    "Import normalization must repair text-midpoint geometry from the protected vector grid");
            assertEquals(30d, table.getTableColumnWidths().get(3), 1.0);
            assertEquals(48d, table.getTableColumnWidths().get(4), 1.0);
            assertEquals(31d, table.getTableColumnWidths().get(5), 1.0);
            assertEquals(60d, table.getTableColumnWidths().get(6), 1.0);
            assertTrue(table.isSourceStyleCaptured(), "Protected source grid style should be recovered during package normalization");
            assertTrue(table.isStrokeEnabled());
            assertEquals("#7599C6", table.getStrokeColor());
        } finally {
            Files.deleteIfExists(pdf);
        }
    }
}
