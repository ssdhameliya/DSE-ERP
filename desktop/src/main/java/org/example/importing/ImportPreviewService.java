package org.example.importing;

import org.apache.poi.ss.usermodel.*;
import org.example.bank.KotakBankStatementCsvParser;
import org.example.util.SpreadsheetLayoutDetector;

import java.io.File;
import java.util.*;

/**
 * Workbook inspection and preview loading for the Import wizard.
 *
 * <p>This service deliberately owns no JavaFX controls.  The controller supplies
 * the selected module and domain-to-source mapping and only renders the returned
 * rows/status.  Keeping spreadsheet parsing here makes preview behaviour testable
 * without starting JavaFX.</p>
 */
public final class ImportPreviewService {
    private static final int STANDARD_PREVIEW_LIMIT = 50;
    private static final int RECON_PREVIEW_LIMIT = 100;

    public record Inspection(SpreadsheetLayoutDetector.Layout layout, List<String> headers) { }
    public record Preview(List<Map<String, String>> rows, int sheetCount, String message, boolean success) { }

    public Inspection inspect(File file, String module, List<String> domainFields) {
        Objects.requireNonNull(file, "file");
        if ("Bank Statement".equals(module)) {
            return new Inspection(null, List.of(
                "Transaction Date", "Value Date", "Description", "Chq / Ref No.", "Amount", "Dr / Cr", "Balance"));
        }
        try (Workbook workbook = WorkbookFactory.create(file)) {
            SpreadsheetLayoutDetector.Layout layout = SpreadsheetLayoutDetector.detect(workbook, domainFields);
            List<String> headers = layout.headers().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(header -> !header.isBlank())
                .distinct()
                .toList();
            return new Inspection(layout, headers);
        } catch (Exception exception) {
            throw new IllegalArgumentException("The workbook could not be inspected: " + safeMessage(exception), exception);
        }
    }

    public Preview preview(File file, String module, Map<String, String> mapping, List<String> mappedFields) throws Exception {
        Objects.requireNonNull(file, "file");
        Map<String, String> safeMapping = mapping == null ? Map.of() : mapping;
        List<String> safeFields = mappedFields == null ? List.of() : mappedFields;
        return switch (module == null ? "" : module) {
            case "Bank Statement" -> bankPreview(file);
            case "Purchase Recon" -> purchaseReconPreview(file, safeMapping, safeFields);
            default -> workbookPreview(file, safeMapping, safeFields);
        };
    }

    private Preview bankPreview(File file) throws Exception {
        var parsed = new KotakBankStatementCsvParser().parse(file.toPath());
        List<Map<String, String>> rows = new ArrayList<>();
        for (var row : parsed.rows().stream().limit(STANDARD_PREVIEW_LIMIT).toList()) {
            Map<String, String> value = new LinkedHashMap<>();
            value.put("transaction_date", row.transactionTimestamp());
            value.put("value_date", row.valueDate());
            value.put("description", row.description());
            value.put("reference", row.reference());
            value.put("amount", String.format(Locale.ROOT, "%.2f", row.debit() > 0 ? row.debit() : row.credit()));
            value.put("direction", row.debit() > 0 ? "DR" : "CR");
            value.put("balance", String.format(Locale.ROOT, "%.2f", row.balance()));
            rows.add(value);
        }
        return new Preview(List.copyOf(rows), 1, "Kotak bank statement preview loaded successfully", true);
    }

    private Preview purchaseReconPreview(File file, Map<String, String> mapping, List<String> mappedFields) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(file)) {
            List<String> expected = mapping.values().stream().filter(Objects::nonNull).filter(v -> !v.isBlank()).toList();
            List<SpreadsheetLayoutDetector.Layout> layouts = SpreadsheetLayoutDetector.detectAll(workbook, expected);
            if (layouts.isEmpty()) throw new IllegalArgumentException("No mapped Purchase Recon worksheet was found.");
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<Map<String, String>> rows = new ArrayList<>();
            for (SpreadsheetLayoutDetector.Layout layout : layouts) {
                Sheet sheet = workbook.getSheetAt(layout.sheetIndex());
                Row headerRow = sheet.getRow(layout.headerRowIndex());
                for (int rowIndex = layout.headerRowIndex() + 1;
                     rowIndex <= sheet.getLastRowNum() && rows.size() < RECON_PREVIEW_LIMIT;
                     rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (SpreadsheetLayoutDetector.isRowBlank(row, evaluator)) continue;
                    Map<String, String> rowMap = new LinkedHashMap<>();
                    rowMap.put("_source_sheet", sheet.getSheetName());
                    rowMap.put("_source_row", String.valueOf(rowIndex + 1));
                    copyMappedCells(rowMap, row, headerRow, evaluator, mapping, mappedFields);
                    rows.add(rowMap);
                }
                if (rows.size() >= RECON_PREVIEW_LIMIT) break;
            }
            String message = rows.isEmpty()
                ? "No usable Purchase Recon rows were found"
                : "Mapped Purchase Recon data preview loaded across all matching sheets";
            return new Preview(List.copyOf(rows), layouts.size(), message, true);
        }
    }

    private Preview workbookPreview(File file, Map<String, String> mapping, List<String> mappedFields) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(file)) {
            SpreadsheetLayoutDetector.Layout layout = SpreadsheetLayoutDetector.detect(workbook, mapping.values());
            Sheet sheet = workbook.getSheetAt(layout.sheetIndex());
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row headerRow = sheet.getRow(layout.headerRowIndex());
            int lastRow = Math.min(sheet.getLastRowNum(), layout.headerRowIndex() + STANDARD_PREVIEW_LIMIT);
            List<Map<String, String>> rows = new ArrayList<>();
            for (int rowIndex = layout.headerRowIndex() + 1; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (SpreadsheetLayoutDetector.isRowBlank(row, evaluator)) continue;
                Map<String, String> rowMap = new LinkedHashMap<>();
                copyMappedCells(rowMap, row, headerRow, evaluator, mapping, mappedFields);
                rows.add(rowMap);
            }
            boolean success = !rows.isEmpty();
            return new Preview(List.copyOf(rows), 1,
                success ? "Preview loaded successfully" : "No usable data rows were found", success);
        }
    }

    private void copyMappedCells(Map<String, String> target, Row row, Row headerRow,
                                 FormulaEvaluator evaluator, Map<String, String> mapping,
                                 List<String> mappedFields) {
        for (String domainField : mappedFields) {
            String excelHeader = mapping.get(domainField);
            if (excelHeader == null || excelHeader.isBlank()) continue;
            int columnIndex = SpreadsheetLayoutDetector.findHeaderIndex(headerRow, excelHeader, evaluator);
            String value = "";
            if (columnIndex >= 0 && row != null && row.getCell(columnIndex) != null) {
                Cell cell = row.getCell(columnIndex);
                boolean dateField = domainField != null && domainField.toLowerCase(Locale.ROOT).contains("date");
                value = dateField
                    ? SpreadsheetLayoutDetector.formatForBusiness(cell, evaluator)
                    : SpreadsheetLayoutDetector.format(cell, evaluator);
            }
            target.put(domainField, value);
        }
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) return "An unknown error occurred.";
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message.trim();
    }
}
