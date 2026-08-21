package org.example.documentstudio.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.documentstudio.model.TemplateCharge;
import org.example.documentstudio.model.TemplateData;
import org.example.invoice.model.TaxInvoiceItem;

import java.util.List;
import java.util.Map;

/** Regression check: each repeated row must resolve from its own ERP record. */
public final class ExcelTemplateRepeatingRowsSmoke {
    private ExcelTemplateRepeatingRowsSmoke() { }

    public static void main(String[] args) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Invoice");
            Row item = sheet.createRow(0);
            item.createCell(0).setCellValue("{{item.serial}}");
            item.createCell(1).setCellValue("{{item.description}}");
            item.createCell(2).setCellValue("{{item.quantity}}");
            item.createCell(3).setCellValue("{{item.rate}}");
            item.createCell(4).setCellFormula("C1*D1");

            Row charge = sheet.createRow(4);
            charge.createCell(0).setCellValue("{{charge.serial}}");
            charge.createCell(1).setCellValue("{{charge.type}}");
            charge.createCell(2).setCellValue("{{charge.amount}}");

            List<TaxInvoiceItem> items = List.of(
                    new TaxInvoiceItem(1, "1111", "First product", "First remark", 2, "NOS", 10, 0, 18),
                    new TaxInvoiceItem(2, "2222", "Second product", "Second remark", 3, "NOS", 20, 0, 18));
            List<TemplateCharge> charges = List.of(
                    new TemplateCharge("Packing", 100, false, 0, 0, 100),
                    new TemplateCharge("Freight", 250, false, 0, 0, 250));
            TemplateData data = new TemplateData(Map.of(), Map.of(), items, charges, "GST");

            ExcelTemplateRenderer.fillWorkbook(workbook, data, List.of(
                    new ExcelTemplateRenderer.ChargeData("Packing", 100, false, 0, 0, 100),
                    new ExcelTemplateRenderer.ChargeData("Freight", 250, false, 0, 0, 250)));

            assertNumber(sheet, 0, 0, 1);
            assertText(sheet, 0, 1, "First product\nFirst remark");
            assertNumber(sheet, 1, 0, 2);
            assertText(sheet, 1, 1, "Second product\nSecond remark");
            if (!"C2*D2".equals(sheet.getRow(1).getCell(4).getCellFormula()))
                throw new AssertionError("Second-row formula was not shifted correctly");

            // The item expansion shifts the charge template down by one row; charge expansion then repeats it.
            assertNumber(sheet, 5, 0, 1);
            assertText(sheet, 5, 1, "Packing");
            assertNumber(sheet, 6, 0, 2);
            assertText(sheet, 6, 1, "Freight");
            System.out.println("EXCEL_REPEATING_ROWS_DISTINCT_OK");
        }
    }

    private static void assertText(Sheet sheet, int row, int col, String expected) {
        String actual = sheet.getRow(row).getCell(col).toString();
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " at row " + (row + 1) + " but got " + actual);
    }

    private static void assertNumber(Sheet sheet, int row, int col, double expected) {
        double actual = sheet.getRow(row).getCell(col).getNumericCellValue();
        if (Double.compare(expected, actual) != 0) throw new AssertionError("Expected " + expected + " at row " + (row + 1) + " but got " + actual);
    }
}
