package org.example.documentstudio.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.documentstudio.model.TemplateCharge;
import org.example.documentstudio.model.TemplateData;
import org.example.invoice.model.TaxInvoiceItem;

import java.util.List;
import java.util.Map;

/** Regression checks for single-row and multi-row ERP repeating blocks. */
public final class ExcelTemplateRepeatingRowsSmoke {
    private ExcelTemplateRepeatingRowsSmoke() { }

    public static void main(String[] args) throws Exception {
        singleRowStillWorks();
        multiRowItemBlockRepeatsAsOneUnit();
        separatedItemRowsDoNotBecomeOneBlock();
        System.out.println("EXCEL_REPEATING_ROWS_DISTINCT_OK");
    }

    private static void singleRowStillWorks() throws Exception {
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

            ExcelTemplateRenderer.fillWorkbook(workbook, data(), charges());

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
        }
    }

    private static void multiRowItemBlockRepeatsAsOneUnit() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Invoice");
            Row description = sheet.createRow(0);
            description.createCell(0).setCellValue("{{item.descriptionWithRemarks}}");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

            Row numbers = sheet.createRow(1);
            numbers.createCell(0).setCellValue("{{item.quantity}}");
            numbers.createCell(1).setCellValue("{{item.rate}}");
            numbers.createCell(2).setCellFormula("A2*B2");

            sheet.createRow(3).createCell(0).setCellValue("Grand Total");

            if (!ExcelTemplateRenderer.hasCompleteItemRepeatingBlock(workbook))
                throw new AssertionError("A contiguous two-row item block should be valid before rendering");

            ExcelTemplateRenderer.fillWorkbook(workbook, data(), List.of());

            assertText(sheet, 0, 0, "First product\nFirst remark");
            assertNumber(sheet, 1, 0, 2);
            assertNumber(sheet, 1, 1, 10);
            if (!"A2*B2".equals(sheet.getRow(1).getCell(2).getCellFormula()))
                throw new AssertionError("First item formula changed unexpectedly");

            assertText(sheet, 2, 0, "Second product\nSecond remark");
            assertNumber(sheet, 3, 0, 3);
            assertNumber(sheet, 3, 1, 20);
            if (!"A4*B4".equals(sheet.getRow(3).getCell(2).getCellFormula()))
                throw new AssertionError("Second multi-row item formula was not shifted with the block");

            if (!"Grand Total".equals(sheet.getRow(5).getCell(0).getStringCellValue()))
                throw new AssertionError("Rows below the item block were not shifted by the whole block height");

            if (!hasMergedRegion(sheet, 0, 0, 0, 2) || !hasMergedRegion(sheet, 2, 2, 0, 2))
                throw new AssertionError("Merged formatting was not preserved for the repeated multi-row block");
        }
    }

    private static void separatedItemRowsDoNotBecomeOneBlock() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Invoice");
            sheet.createRow(0).createCell(0).setCellValue("{{item.description}}");
            sheet.createRow(1).createCell(0).setCellValue("spacer");
            Row values = sheet.createRow(2);
            values.createCell(0).setCellValue("{{item.quantity}}");
            values.createCell(1).setCellValue("{{item.rate}}");
            values.createCell(2).setCellValue("{{item.total}}");
            if (ExcelTemplateRenderer.hasCompleteItemRepeatingBlock(workbook))
                throw new AssertionError("Separated item areas must not be inferred as one repeating block");
        }
    }

    private static TemplateData data() {
        List<TaxInvoiceItem> items = List.of(
                new TaxInvoiceItem(1, "1111", "First product", "First remark", 2, "NOS", 10, 0, 18),
                new TaxInvoiceItem(2, "2222", "Second product", "Second remark", 3, "NOS", 20, 0, 18));
        List<TemplateCharge> charges = List.of(
                new TemplateCharge("Packing", 100, false, 0, 0, 100),
                new TemplateCharge("Freight", 250, false, 0, 0, 250));
        return new TemplateData(Map.of(), Map.of(), items, charges, "GST");
    }

    private static List<ExcelTemplateRenderer.ChargeData> charges() {
        return List.of(
                new ExcelTemplateRenderer.ChargeData("Packing", 100, false, 0, 0, 100),
                new ExcelTemplateRenderer.ChargeData("Freight", 250, false, 0, 0, 250));
    }

    private static boolean hasMergedRegion(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() == firstRow && region.getLastRow() == lastRow
                    && region.getFirstColumn() == firstCol && region.getLastColumn() == lastCol) return true;
        }
        return false;
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
