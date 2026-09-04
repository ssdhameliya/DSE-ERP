package org.example.importing;

import org.apache.poi.ss.usermodel.*;
import org.example.util.SpreadsheetLayoutDetector;

import java.time.LocalDate;

/** Spreadsheet cell/date access shared by import processors. */
public final class ImportWorkbookValueReader {
    private ImportWorkbookValueReader() { }

    public static String cellValue(Row row, String header) {
        if (row == null || header == null) return null;
        Workbook workbook = row.getSheet().getWorkbook();
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        int colIndex = findColumn(row, header, evaluator);
        if (colIndex >= 0 && row.getCell(colIndex) != null) {
            return SpreadsheetLayoutDetector.format(row.getCell(colIndex), evaluator);
        }
        return null;
    }

    public static LocalDate requiredDateValue(Row row, String header, String field) {
        if (row == null) throw new IllegalArgumentException("Missing " + field + " row");
        if (header == null || header.isBlank()) throw new IllegalArgumentException("Missing " + field + " mapping");
        Workbook workbook = row.getSheet().getWorkbook();
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        int colIndex = findColumn(row, header, evaluator);
        if (colIndex < 0) throw new IllegalArgumentException("Missing " + field + " column");
        Cell cell = row.getCell(colIndex);
        LocalDate excelDate = SpreadsheetLayoutDetector.dateValue(cell, evaluator);
        if (excelDate != null) return excelDate;
        String text = SpreadsheetLayoutDetector.format(cell, evaluator);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Missing " + field);
        return ImportValueParser.requiredDate(text);
    }

    private static int findColumn(Row row, String header, FormulaEvaluator evaluator) {
        int colIndex = -1;
        for (int headerIndex = Math.max(0, row.getSheet().getFirstRowNum());
             headerIndex < row.getRowNum() && headerIndex < 75; headerIndex++) {
            colIndex = SpreadsheetLayoutDetector.findHeaderIndex(row.getSheet().getRow(headerIndex), header, evaluator);
            if (colIndex >= 0) break;
        }
        return colIndex;
    }
}
