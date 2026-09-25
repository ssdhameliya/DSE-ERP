package org.example.documentstudio.service;

import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateColumnBinding;
import org.example.documentstudio.model.TemplateElement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfStudioSourceTableGeometryTest {
    @Test
    @SuppressWarnings("unchecked")
    void reviewBindingsCannotMoveSourceDesignedRuntimeGrid() throws Exception {
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE,0,24.23,253.04,546.54,362.35);
        table.setUseSourceTableDesign(true);
        table.setTableColumns(List.of("serial","hsn","descriptionWithRemarks","quantity","rate","unit","taxable"));
        List<Double> proven = List.of(33.97,47.98,295.67,29.99,47.98,30.99,59.96);
        table.setTableColumnWidths(proven);
        // Simulate review metadata reconstructed from imperfect text midpoint detection.
        table.setTableColumnBindings(List.of(
                binding("SR. NO.","item.serial",0,38), binding("HSN CODE","item.hsn",38,52),
                binding("PRODUCT DESCRIPTION","item.descriptionWithRemarks",90,250),
                binding("QTY","item.quantity",340,40), binding("UNIT RATE","item.rate",380,60),
                binding("UNIT","item.unit",440,35), binding("AMOUNT (INR)","item.taxable",475,71)));

        Method itemColumns = PdfStudioRenderer.class.getDeclaredMethod("itemColumns", TemplateElement.class);
        itemColumns.setAccessible(true);
        List<?> columns = (List<?>) itemColumns.invoke(null, table);
        Method widths = PdfStudioRenderer.class.getDeclaredMethod("columnWidths", TemplateElement.class, List.class, float.class);
        widths.setAccessible(true);
        List<Float> actual = (List<Float>) widths.invoke(null, table, columns, (float)table.getWidth());

        assertEquals(proven.size(), actual.size());
        for (int i=0;i<proven.size();i++) assertEquals(proven.get(i), actual.get(i), .02,
                "Source body boundary " + i + " must remain on the saved source geometry");
    }

    private static TemplateColumnBinding binding(String label,String key,double x,double width){
        return new TemplateColumnBinding(label,key,x,width,"LEFT",1.0);
    }
}
