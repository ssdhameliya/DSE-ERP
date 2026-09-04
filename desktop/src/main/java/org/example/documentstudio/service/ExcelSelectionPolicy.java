package org.example.documentstudio.service;

import org.apache.poi.ss.util.CellRangeAddress;

/** Pure spreadsheet selection geometry, independent of JavaFX controls. */
public final class ExcelSelectionPolicy {
    private ExcelSelectionPolicy() { }

    public static int clamp(int value, int maxExclusive) {
        if (maxExclusive <= 0) return 0;
        return Math.max(0, Math.min(maxExclusive - 1, value));
    }

    public static CellRangeAddress range(int anchorRow, int anchorCol, int endRow, int endCol,
                                         int fallbackRow, int fallbackCol) {
        int ar = anchorRow < 0 ? Math.max(0, fallbackRow) : anchorRow;
        int ac = anchorCol < 0 ? Math.max(0, fallbackCol) : anchorCol;
        int er = endRow < 0 ? ar : endRow;
        int ec = endCol < 0 ? ac : endCol;
        return new CellRangeAddress(Math.min(ar, er), Math.max(ar, er), Math.min(ac, ec), Math.max(ac, ec));
    }
}
