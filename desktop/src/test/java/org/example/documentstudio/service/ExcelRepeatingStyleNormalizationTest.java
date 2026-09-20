package org.example.documentstudio.service;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.documentstudio.model.TemplateData;
import org.example.invoice.model.TaxInvoiceItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelRepeatingStyleNormalizationTest {
    @Test
    void staleSampleFormattingDoesNotLeakIntoGeneratedItems() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Invoice");
            Row template = sheet.createRow(0);
            template.createCell(0).setCellValue("{{item.descriptionWithRemarks}}");
            template.createCell(1).setCellValue("{{item.quantity}}");
            template.createCell(2).setCellValue("{{item.rate}}");
            template.createCell(3).setCellValue("{{item.taxable}}");
            CellStyle canonical = workbook.createCellStyle();
            canonical.setFillForegroundColor(IndexedColors.WHITE.getIndex());
            canonical.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int c = 0; c < 4; c++) template.getCell(c).setCellStyle(canonical);

            Row stale = sheet.createRow(1);
            CellStyle bad = workbook.createCellStyle();
            bad.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            bad.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            stale.createCell(0).setCellValue("Old sample item");
            stale.createCell(1).setCellValue(99);
            stale.createCell(2).setCellValue(99);
            stale.createCell(3).setCellValue(99);
            for (int c = 0; c < 4; c++) stale.getCell(c).setCellStyle(bad);
            sheet.createRow(3).createCell(0).setCellValue("Grand Total");

            TemplateData data = new TemplateData(Map.of(), Map.of(), List.of(
                    new TaxInvoiceItem(1, "1111", "First", "", 1, "NOS", 10, 0, 18),
                    new TaxInvoiceItem(2, "2222", "Second", "", 1, "NOS", 20, 0, 18)
            ), List.of(), "GST");
            ExcelTemplateRenderer.fillWorkbook(workbook, data, List.of());

            assertEquals(IndexedColors.WHITE.getIndex(), sheet.getRow(1).getCell(0).getCellStyle().getFillForegroundColor(),
                    "generated item row must not retain stale pink/red fill");
            assertEquals(FillPatternType.SOLID_FOREGROUND, sheet.getRow(1).getCell(0).getCellStyle().getFillPattern());
        }
    }
}
