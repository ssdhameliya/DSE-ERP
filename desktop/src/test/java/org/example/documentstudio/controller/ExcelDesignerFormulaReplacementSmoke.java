package org.example.documentstudio.controller;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** Regression check: replacing a formula with an ERP token must remove the formula permanently. */
public final class ExcelDesignerFormulaReplacementSmoke {
    private ExcelDesignerFormulaReplacementSmoke() { }

    public static void main(String[] args) throws Exception {
        byte[] saved;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Cell igst = workbook.createSheet("Invoice").createRow(34).createCell(6);
            igst.setCellFormula("G32*18%");
            Cell grandTotal = workbook.getSheetAt(0).createRow(36).createCell(6);
            grandTotal.setCellFormula("G32+G33+G34+G35+G36");

            ExcelDesignerController.writeCellValue(igst, "{{totals.igstAmount}}");
            ExcelDesignerController.writeCellValue(grandTotal, "{{totals.roundedGrandTotal}}");
            workbook.write(out);
            saved = out.toByteArray();
        }

        try (Workbook reopened = WorkbookFactory.create(new ByteArrayInputStream(saved))) {
            assertToken(reopened.getSheetAt(0).getRow(34).getCell(6), "{{totals.igstAmount}}");
            assertToken(reopened.getSheetAt(0).getRow(36).getCell(6), "{{totals.roundedGrandTotal}}");
        }
        System.out.println("EXCEL_FORMULA_REPLACEMENT_PERSISTS_OK");
    }

    private static void assertToken(Cell cell, String expected) {
        if (cell.getCellType() != CellType.STRING)
            throw new AssertionError("Expected STRING after reopening, found " + cell.getCellType());
        if (!expected.equals(cell.getStringCellValue()))
            throw new AssertionError("Expected " + expected + ", found " + cell.getStringCellValue());
    }
}
