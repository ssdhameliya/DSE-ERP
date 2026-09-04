package org.example.documentstudio.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.function.Function;

/** Workbook dimension conversions and AutoFit estimation, independent of JavaFX controls. */
public final class ExcelDimensionPolicy {
    private ExcelDimensionPolicy() { }

    public static double columnWidthPixels(Sheet sheet, int col) {
        if (sheet == null) return 70;
        return Math.max(24, Math.min(1800, (sheet.getColumnWidth(col) / 256d) * 7d + 5d));
    }

    public static int pixelsToColumnWidth(double pixels) {
        double chars = Math.max(1, (pixels - 5d) / 7d);
        return Math.max(256, Math.min(255 * 256, (int) Math.round(chars * 256d)));
    }

    public static double rowHeightPixels(Sheet sheet, int rowIndex) {
        if (sheet == null) return 22;
        Row row = sheet.getRow(rowIndex);
        float points = row == null ? sheet.getDefaultRowHeightInPoints() : row.getHeightInPoints();
        if (points <= 0) points = 15f;
        return Math.max(18, Math.min(560, points * 96d / 72d + 2d));
    }

    public static float pixelsToRowPoints(double pixels) {
        return (float) Math.max(2, Math.min(409, (pixels - 2d) * 72d / 96d));
    }

    public static float estimateAutoRowHeightPoints(Sheet sheet, int rowIndex, Function<Cell, String> textProvider) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) return sheet.getDefaultRowHeightInPoints();
        int lines = 1;
        for (Cell cell : row) {
            String text = textProvider.apply(cell);
            if (text == null || text.isBlank()) continue;
            int explicit = Math.max(1, text.split("\\R", -1).length);
            CellStyle style = cell.getCellStyle();
            if (style != null && style.getWrapText()) {
                double chars = Math.max(4, sheet.getColumnWidth(cell.getColumnIndex()) / 256d - 1d);
                explicit = Math.max(explicit, (int) Math.ceil(text.length() / chars));
            }
            lines = Math.max(lines, explicit);
        }
        return (float) Math.max(15, Math.min(409, 15d * lines + 2d));
    }
}
