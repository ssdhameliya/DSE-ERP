package org.example.util;

import org.apache.poi.ss.usermodel.*;

import java.util.*;

/** Detects the real data sheet/header in XLS and XLSX imports. */
public final class SpreadsheetLayoutDetector {
    private static final int MAX_HEADER_SCAN_ROWS = 75;

    private SpreadsheetLayoutDetector() {}

    public record Layout(int sheetIndex, int headerRowIndex, List<String> headers) {}

    public static Layout detect(Workbook workbook, Collection<String> expectedFields) {
        DataFormatter formatter = new DataFormatter(Locale.getDefault());
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        Set<String> expected = new HashSet<>();
        if (expectedFields != null) expectedFields.stream().filter(Objects::nonNull)
            .map(SpreadsheetLayoutDetector::normalize).filter(value -> !value.isBlank()).forEach(expected::add);

        Layout best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            if (workbook.isSheetHidden(sheetIndex) || workbook.isSheetVeryHidden(sheetIndex)) continue;
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            int lastCandidate = Math.min(sheet.getLastRowNum(), MAX_HEADER_SCAN_ROWS - 1);
            for (int rowIndex = Math.max(0, sheet.getFirstRowNum()); rowIndex <= lastCandidate; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                List<String> headers = formattedCells(row, formatter, evaluator);
                long nonBlank = headers.stream().filter(value -> !value.isBlank()).count();
                // Minimal migration workbooks may legitimately contain a single
                // mapped field. Prefer richer rows, but do not reject them.
                if (nonBlank < 1) continue;
                int matches = 0;
                Set<String> distinct = new HashSet<>();
                for (String header : headers) {
                    String normalized = normalize(header);
                    if (normalized.isBlank()) continue;
                    distinct.add(normalized);
                    if (expected.stream().anyMatch(field -> equivalent(field, normalized))) matches++;
                }
                int score = matches * 100 + distinct.size() * 4
                    + Math.min(countFollowingDataRows(sheet, rowIndex), 20) - rowIndex;
                if (matches == 0 && !expected.isEmpty()) score -= 80;
                if (score > bestScore) {
                    bestScore = score;
                    best = new Layout(sheetIndex, rowIndex, List.copyOf(headers));
                }
            }
        }
        if (best == null) throw new IllegalArgumentException("No tabular header row was found in any worksheet.");
        return best;
    }

    public static String format(Cell cell, FormulaEvaluator evaluator) {
        return cell == null ? "" : new DataFormatter(Locale.getDefault()).formatCellValue(cell, evaluator).trim();
    }


    /** Returns the real Excel date when the cell is a date-formatted numeric/formula cell. */
    public static java.time.LocalDate dateValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        try {
            CellType type = cell.getCellType();
            if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            if (type == CellType.FORMULA) {
                CellValue evaluated = evaluator == null ? null : evaluator.evaluate(cell);
                if (evaluated != null && evaluated.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                    return DateUtil.getLocalDateTime(evaluated.getNumberValue()).toLocalDate();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Formats real Excel dates with the application's saved date format; other cells remain unchanged. */
    public static String formatForBusiness(Cell cell, FormulaEvaluator evaluator) {
        java.time.LocalDate date = dateValue(cell, evaluator);
        return date == null ? format(cell, evaluator) : BusinessClock.formatDate(date);
    }

    public static int findHeaderIndex(Row headerRow, String heading, FormulaEvaluator evaluator) {
        if (headerRow == null || heading == null || heading.isBlank()) return -1;
        String wanted = normalize(heading);
        for (int index = 0; index < headerRow.getLastCellNum(); index++) {
            if (equivalent(wanted, normalize(format(headerRow.getCell(index), evaluator)))) return index;
        }
        return -1;
    }

    public static boolean isRowBlank(Row row, FormulaEvaluator evaluator) {
        if (row == null) return true;
        int first = Math.max(0, row.getFirstCellNum());
        for (int index = first; index < row.getLastCellNum(); index++)
            if (!format(row.getCell(index), evaluator).isBlank()) return false;
        return true;
    }

    private static List<String> formattedCells(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> values = new ArrayList<>();
        if (row == null || row.getLastCellNum() < 0) return values;
        for (int index = 0; index < row.getLastCellNum(); index++) {
            Cell cell = row.getCell(index);
            values.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim());
        }
        return values;
    }

    private static int countFollowingDataRows(Sheet sheet, int headerRow) {
        int count = 0;
        for (int index = headerRow + 1; index <= Math.min(sheet.getLastRowNum(), headerRow + 25); index++)
            if (sheet.getRow(index) != null && sheet.getRow(index).getPhysicalNumberOfCells() > 0) count++;
        return count;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static boolean equivalent(String left, String right) {
        return left.equals(right) || aliases(left).contains(right) || aliases(right).contains(left);
    }

    private static Set<String> aliases(String value) {
        return switch (value) {
            case "description" -> Set.of("description", "itemname", "name", "itemdescription");
            case "itemcode" -> Set.of("itemcode", "sku", "productcode");
            case "partycode" -> Set.of("partycode", "customercode", "suppliercode");
            case "name" -> Set.of("name", "customer", "supplier", "customername", "suppliername", "partyname");
            case "hsn" -> Set.of("hsn", "hsnsac");
            case "gst", "gstpercent" -> Set.of("gst", "gstpercent", "tax", "taxpercent");
            case "discount", "discountpercent" -> Set.of("discount", "discountpercent", "disc", "discpercent");
            case "invoiceno" -> Set.of("invoiceno", "invoice", "documentno", "saleno", "purchaseno");
            case "invoicedate" -> Set.of("invoicedate", "date", "documentdate");
            case "suppliername" -> Set.of("suppliername", "supplier", "tradelegalname", "tradename", "legalname");
            case "suppliergstin" -> Set.of("suppliergstin", "gstinofsupplier", "gstin");
            case "supplierinvoiceno" -> Set.of("supplierinvoiceno", "invoiceno", "invoicenumber", "billno");
            case "taxablevalue" -> Set.of("taxablevalue", "taxableamount");
            case "cgst" -> Set.of("cgst", "centraltax", "cgstamount");
            case "sgst" -> Set.of("sgst", "stateuttax", "statetax", "sgstamount");
            case "igst" -> Set.of("igst", "integratedtax", "igstamount");
            case "invoicevalue" -> Set.of("invoicevalue", "invoicetotal", "totalinvoicevalue");
            default -> Set.of(value);
        };
    }
}
