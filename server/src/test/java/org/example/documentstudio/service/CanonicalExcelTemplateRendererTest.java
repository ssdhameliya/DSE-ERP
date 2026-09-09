package org.example.documentstudio.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.documentstudio.model.TemplateData;
import org.example.invoice.model.TaxInvoiceItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalExcelTemplateRendererTest {
    @Test
    void serverRendererRepeatsContiguousMultiRowItemBlockAsOneUnit() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Invoice");
            Row description = sheet.createRow(0);
            description.createCell(0).setCellValue("{{item.descriptionWithRemarks}}");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

            Row values = sheet.createRow(1);
            values.createCell(0).setCellValue("{{item.quantity}}");
            values.createCell(1).setCellValue("{{item.rate}}");
            values.createCell(2).setCellFormula("A2*B2");
            sheet.createRow(3).createCell(0).setCellValue("Grand Total");

            assertTrue(ExcelTemplateRenderer.hasCompleteItemRepeatingBlock(workbook));
            ExcelTemplateRenderer.fillWorkbook(workbook, data(), List.of());

            assertEquals("First product\nFirst remark", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(2d, sheet.getRow(1).getCell(0).getNumericCellValue());
            assertEquals(10d, sheet.getRow(1).getCell(1).getNumericCellValue());
            assertEquals("A2*B2", sheet.getRow(1).getCell(2).getCellFormula());

            assertEquals("Second product\nSecond remark", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals(3d, sheet.getRow(3).getCell(0).getNumericCellValue());
            assertEquals(20d, sheet.getRow(3).getCell(1).getNumericCellValue());
            assertEquals("A4*B4", sheet.getRow(3).getCell(2).getCellFormula());
            assertEquals("Grand Total", sheet.getRow(5).getCell(0).getStringCellValue());
            assertTrue(hasMergedRegion(sheet, 0, 0, 0, 2));
            assertTrue(hasMergedRegion(sheet, 2, 2, 0, 2));
        }
    }

    @Test
    void serverRendererDoesNotMergeSeparatedItemAreasIntoOneBlock() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Invoice");
            sheet.createRow(0).createCell(0).setCellValue("{{item.description}}");
            sheet.createRow(1).createCell(0).setCellValue("spacer");
            Row values = sheet.createRow(2);
            values.createCell(0).setCellValue("{{item.quantity}}");
            values.createCell(1).setCellValue("{{item.rate}}");
            values.createCell(2).setCellValue("{{item.total}}");
            assertFalse(ExcelTemplateRenderer.hasCompleteItemRepeatingBlock(workbook));
        }
    }

    private static TemplateData data() {
        return new TemplateData(Map.of(), Map.of(), List.of(
                new TaxInvoiceItem(1, "1111", "First product", "First remark", 2, "NOS", 10, 0, 18),
                new TaxInvoiceItem(2, "2222", "Second product", "Second remark", 3, "NOS", 20, 0, 18)
        ), List.of(), "GST");
    }

    private static boolean hasMergedRegion(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() == firstRow && region.getLastRow() == lastRow
                    && region.getFirstColumn() == firstCol && region.getLastColumn() == lastCol) return true;
        }
        return false;
    }
}
