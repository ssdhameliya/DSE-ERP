package org.example.documentstudio.engine;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateColumnBinding;
import org.example.documentstudio.model.TemplateData;
import org.example.documentstudio.model.TemplateElement;
import org.example.invoice.model.TaxInvoiceItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clean end-to-end tests for the rebuilt Universal PDF Studio Engine.
 * Verifies standard multi-page pagination, repeating headers, full item flow on Page 1,
 * and closing stack placement strictly on the final page.
 */
class UniversalPdfEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void singlePageLayoutWhenItemsFitWithinCapacity() {
        DocumentTemplate template = createTestTemplate();
        TemplateElement table = template.getElements().stream()
                .filter(e -> e.getType() == ElementType.ITEM_TABLE)
                .findFirst().orElseThrow();

        // 5 items should comfortably fit on 1 page
        UniversalLayoutPlanner.LayoutPlan plan = UniversalLayoutPlanner.plan(template, table, 5, 842.0, 595.0);

        assertEquals(1, plan.totalPages());
        assertEquals(1, plan.pageSlices().size());
        UniversalLayoutPlanner.PageItemSlice slice = plan.pageSlices().get(0);
        assertEquals(1, slice.pageNumber());
        assertEquals(5, slice.itemCount());
        assertTrue(slice.hasClosingStack(), "Single page must have closing stack");
    }

    @Test
    void multiPageLayoutWhenItemsExceedPageCapacity() {
        DocumentTemplate template = createTestTemplate();
        TemplateElement table = template.getElements().stream()
                .filter(e -> e.getType() == ElementType.ITEM_TABLE)
                .findFirst().orElseThrow();

        // 25 items requires 2 pages
        UniversalLayoutPlanner.LayoutPlan plan = UniversalLayoutPlanner.plan(template, table, 25, 842.0, 595.0);

        assertEquals(2, plan.totalPages());
        assertEquals(2, plan.pageSlices().size());

        UniversalLayoutPlanner.PageItemSlice page1 = plan.pageSlices().get(0);
        assertEquals(1, page1.pageNumber());
        assertFalse(page1.hasClosingStack(), "Page 1 of multi-page document must NOT have closing stack (so items fill page)");
        assertTrue(page1.itemCount() > 10, "Page 1 must fill with items down towards footer margin");

        UniversalLayoutPlanner.PageItemSlice page2 = plan.pageSlices().get(1);
        assertEquals(2, page2.pageNumber());
        assertTrue(page2.hasClosingStack(), "Final page must have closing stack");
        assertEquals(25 - page1.itemCount(), page2.itemCount());
    }

    @Test
    void blockResolverCorrectlyClassifiesElements() {
        DocumentTemplate template = createTestTemplate();
        UniversalBlockResolver.ResolvedBlocks resolved = UniversalBlockResolver.resolve(template, 842.0);

        assertNotNull(resolved.tableElement());
        assertEquals(ElementType.ITEM_TABLE, resolved.tableElement().getType());

        // Elements above table (Y < 250) must be in upperStack
        for (TemplateElement el : resolved.upperStack()) {
            assertTrue(el.getY() < resolved.tableY(), "Upper stack elements must be strictly above table: " + el.getFieldKey());
        }

        // Elements below table (Y >= table.Y + table.Height) must be in closingStack
        for (TemplateElement el : resolved.closingStack()) {
            assertTrue(el.getY() >= resolved.tableBottomY() || el.getY() >= 400, "Closing stack elements must be below table: " + el.getFieldKey());
        }
    }

    @Test
    void rendersSinglePagePdfSuccessfully() throws IOException {
        DocumentTemplate template = createTestTemplate();
        List<TaxInvoiceItem> items = createItems(5);
        TemplateData data = new TemplateData(
                Map.of(
                        "document.number", "INV-2026-001",
                        "document.date", "28/09/2026",
                        "party.name", "Acme Corporation",
                        "party.billingAddress", "123 Business Park, Sector 4\nAhmedabad - 380015",
                        "calculation.grandTotal", "15,000.00"
                ),
                Map.of(),
                items,
                "GST"
        );

        Path pdfOut = tempDir.resolve("test-single-page.pdf");
        UniversalPdfEngine.render(template, data, pdfOut);

        assertTrue(pdfOut.toFile().exists());
        try (PDDocument doc = Loader.loadPDF(pdfOut.toFile())) {
            assertEquals(1, doc.getNumberOfPages(), "5 items must produce exactly 1 page");
        }
    }

    @Test
    void rendersMultiPagePdfSuccessfully() throws IOException {
        DocumentTemplate template = createTestTemplate();
        List<TaxInvoiceItem> items = createItems(25);
        TemplateData data = new TemplateData(
                Map.of(
                        "document.number", "INV-2026-002",
                        "document.date", "28/09/2026",
                        "party.name", "Acme Corporation",
                        "party.billingAddress", "123 Business Park, Sector 4\nAhmedabad - 380015",
                        "calculation.grandTotal", "75,000.00"
                ),
                Map.of(),
                items,
                "GST"
        );

        Path pdfOut = tempDir.resolve("test-multi-page.pdf");
        UniversalPdfEngine.render(template, data, pdfOut);

        assertTrue(pdfOut.toFile().exists());
        try (PDDocument doc = Loader.loadPDF(pdfOut.toFile())) {
            assertEquals(2, doc.getNumberOfPages(), "25 items must produce exactly 2 pages");
        }
    }

    private DocumentTemplate createTestTemplate() {
        DocumentTemplate t = new DocumentTemplate();
        t.setName("Universal Test Template");
        t.setDocumentType(DocumentType.SALES_INVOICE);

        List<TemplateElement> elements = new ArrayList<>();

        // Upper stack elements (Y < 250)
        TemplateElement docNum = TemplateElement.of(ElementType.FIELD, 0, 350, 60, 150, 16);
        docNum.setFieldKey("document.number");
        elements.add(docNum);

        TemplateElement docDate = TemplateElement.of(ElementType.FIELD, 0, 350, 80, 150, 16);
        docDate.setFieldKey("document.date");
        elements.add(docDate);

        TemplateElement partyName = TemplateElement.of(ElementType.FIELD, 0, 40, 120, 200, 16);
        partyName.setFieldKey("party.name");
        elements.add(partyName);

        TemplateElement partyAddr = TemplateElement.of(ElementType.FIELD, 0, 40, 140, 200, 40);
        partyAddr.setFieldKey("party.billingAddress");
        partyAddr.setAutoHeight(true);
        partyAddr.setTextFit("WRAP");
        elements.add(partyAddr);

        // Table grid element (Y = 250, Height = 180)
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE, 0, 25, 250, 545, 180);
        table.setHeaderHeight(20);
        table.setRowHeight(18);
        table.setTableColumnBindings(List.of(
                new TemplateColumnBinding("Sr", "item.serial", 0, 30, "CENTER", 1.0),
                new TemplateColumnBinding("Description", "item.descriptionWithRemarks", 30, 240, "LEFT", 1.0),
                new TemplateColumnBinding("HSN", "item.hsn", 270, 55, "CENTER", 1.0),
                new TemplateColumnBinding("Qty", "item.quantity", 325, 45, "RIGHT", 1.0),
                new TemplateColumnBinding("Unit", "item.unit", 370, 35, "CENTER", 1.0),
                new TemplateColumnBinding("Rate", "item.rate", 405, 55, "RIGHT", 1.0),
                new TemplateColumnBinding("GST %", "item.gstPercent", 460, 35, "RIGHT", 1.0),
                new TemplateColumnBinding("Amount", "item.totalAmount", 495, 50, "RIGHT", 1.0)
        ));
        elements.add(table);

        // Closing stack elements (Y >= 430)
        TemplateElement total = TemplateElement.of(ElementType.FIELD, 0, 400, 680, 150, 20);
        total.setFieldKey("calculation.grandTotal");
        elements.add(total);

        TemplateElement terms = TemplateElement.of(ElementType.FIELD, 0, 40, 710, 300, 40);
        terms.setFieldKey("company.terms");
        elements.add(terms);

        t.setElements(elements);
        return t;
    }

    private List<TaxInvoiceItem> createItems(int count) {
        List<TaxInvoiceItem> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(new TaxInvoiceItem(
                    i,
                    "84818090",
                    "Industrial Valve Model X-" + i,
                    "Standard Grade",
                    2.0 * i,
                    "PCS",
                    500.0,
                    0.0,
                    18.0
            ));
        }
        return list;
    }
}
