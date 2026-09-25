package org.example.documentstudio.service;

import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateColumnBinding;
import org.example.documentstudio.model.TemplateElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfStudioPhysicalGridColumnGeometryTest {

    @Test
    void vectorGridOwnsSevenColumnGeometryWhileHeaderTextOnlySuppliesSemantics() {
        var cells = List.of(
                cell("SR. NO.", 30, "item.serial"),
                cell("HSN CODE", 70, "item.hsn"),
                // Deliberately narrow text box centered in a very wide physical description cell.
                new PdfAutoMappingService.ItemHeaderCell("PRODUCT DESCRIPTION", 215, 200, 85, 10, "item.descriptionWithRemarks", .99),
                cell("QTY", 410, "item.quantity"),
                cell("UNIT RATE", 450, "item.rate"),
                cell("UNIT", 493, "item.unit"),
                cell("AMOUNT (INR)", 535, "item.taxable"));
        var header = new PdfAutoMappingService.ItemHeaderLayout(0, 24, 200, 547, 12, cells);
        double[] x = {24.23,58.20,106.18,401.85,431.84,479.82,510.81,570.77};
        var grid = grid(x, 190, 360);
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE,0,24,190,547,360);
        table.setUseSourceTableDesign(true);

        assertTrue(PdfSourceTableDetectionService.applySourceColumnEnvelope(table, grid, header));
        List<TemplateColumnBinding> bindings = PdfSourceTableDetectionService.sourceColumnBindings(header, table, grid);

        assertEquals(7, bindings.size());
        assertEquals(24.23, table.getX(), .01);
        assertEquals(546.54, table.getWidth(), .02);
        assertEquals(47.98, bindings.get(1).getWidth(), .02);
        assertEquals(295.67, bindings.get(2).getWidth(), .02,
                "PRODUCT DESCRIPTION must use the vector cell, not midpoint of printed header text");
        assertEquals(29.99, bindings.get(3).getWidth(), .02);
        assertEquals("item.descriptionWithRemarks", bindings.get(2).getFieldKey());
        assertEquals("item.quantity", bindings.get(3).getFieldKey());
    }

    @Test
    void structurallyDifferentTenColumnGridUsesItsOwnBoundaries() {
        double[] x = {18,48,112,245,300,338,389,447,502,548,582};
        List<PdfAutoMappingService.ItemHeaderCell> cells = List.of(
                new PdfAutoMappingService.ItemHeaderCell("LINE",22,100,20,10,"item.serial",.99),
                new PdfAutoMappingService.ItemHeaderCell("PRODUCT CODE",60,100,44,10,"item.code",.99),
                new PdfAutoMappingService.ItemHeaderCell("DESCRIPTION",170,100,65,10,"item.descriptionWithRemarks",.99),
                new PdfAutoMappingService.ItemHeaderCell("BATCH",260,100,30,10,"",.2),
                new PdfAutoMappingService.ItemHeaderCell("QTY",310,100,20,10,"item.quantity",.99),
                new PdfAutoMappingService.ItemHeaderCell("UOM",350,100,25,10,"item.unit",.99),
                new PdfAutoMappingService.ItemHeaderCell("RATE",405,100,30,10,"item.rate",.99),
                new PdfAutoMappingService.ItemHeaderCell("DISCOUNT",460,100,35,10,"item.discountPercent",.99),
                new PdfAutoMappingService.ItemHeaderCell("TAX",515,100,22,10,"item.gstPercent",.99),
                new PdfAutoMappingService.ItemHeaderCell("NET",558,100,18,10,"",.3));
        var header = new PdfAutoMappingService.ItemHeaderLayout(0,18,100,564,12,cells);
        var grid = grid(x,90,310);
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE,0,18,90,564,310);
        table.setUseSourceTableDesign(true);
        PdfSourceTableDetectionService.applySourceColumnEnvelope(table, grid, header);
        var bindings = PdfSourceTableDetectionService.sourceColumnBindings(header, table, grid);

        assertEquals(10, bindings.size());
        assertEquals(133.0, bindings.get(2).getWidth(), .001);
        assertEquals(55.0, bindings.get(3).getWidth(), .001);
        assertEquals("REVIEW_REQUIRED", bindings.get(3).getMappingState());
        assertEquals("BATCH", bindings.get(3).getSourceLabel());
        assertEquals("REVIEW_REQUIRED", bindings.get(9).getMappingState());
        assertEquals("NET", bindings.get(9).getSourceLabel());
    }

    @Test
    void keepStaticSurvivesPhysicalRedetectionWithNewGeometry() {
        TemplateColumnBinding oldStatic = new TemplateColumnBinding("BATCH", "", 100, 40, "LEFT", .2);
        oldStatic.setMappingState("STATIC");
        TemplateColumnBinding newDetected = new TemplateColumnBinding("BATCH", "", 120, 55, "LEFT", .2);
        newDetected.setAutoDetectedFieldKey("");
        newDetected.setMappingState("REVIEW_REQUIRED");
        var merged = ManualTemplateMappingService.mergeDetectedColumnBindings(List.of(oldStatic), List.of(newDetected));
        assertEquals(1, merged.size());
        assertEquals("STATIC", merged.getFirst().getMappingState());
        assertEquals(120, merged.getFirst().getXOffset(), .001);
        assertEquals(55, merged.getFirst().getWidth(), .001);
    }

    private static PdfAutoMappingService.ItemHeaderCell cell(String label, double x, String key) {
        return new PdfAutoMappingService.ItemHeaderCell(label,x,200,25,10,key,.99);
    }

    private static PdfImageExtractionService.VectorRegion grid(double[] xs, double top, double height) {
        List<PdfImageExtractionService.VectorPrimitive> p = new ArrayList<>();
        for (double x : xs) p.add(new PdfImageExtractionService.VectorPrimitive(
                "LINE",x,top,1,height,"#FFFFFF","#7599C6",.45,false,true));
        p.add(new PdfImageExtractionService.VectorPrimitive("LINE",xs[0],top,xs[xs.length-1]-xs[0],1,
                "#FFFFFF","#7599C6",.45,false,true));
        p.add(new PdfImageExtractionService.VectorPrimitive("LINE",xs[0],top+height,xs[xs.length-1]-xs[0],1,
                "#FFFFFF","#7599C6",.45,false,true));
        return new PdfImageExtractionService.VectorRegion(0,"TABLE / GRID",xs[0],top,
                xs[xs.length-1]-xs[0],height,p,"test-grid");
    }
}
