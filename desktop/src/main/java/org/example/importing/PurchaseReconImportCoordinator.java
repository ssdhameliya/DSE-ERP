package org.example.importing;

import org.apache.poi.ss.usermodel.*;
import org.example.api.recon.PurchaseReconApiClient;
import org.example.service.ImportService;
import org.example.util.BusinessClock;
import org.example.util.SpreadsheetLayoutDetector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/** Executes Purchase Recon workbook imports without coupling spreadsheet parsing to JavaFX. */
public final class PurchaseReconImportCoordinator {
    private final PurchaseReconApiClient api;

    public PurchaseReconImportCoordinator() { this(new PurchaseReconApiClient()); }
    public PurchaseReconImportCoordinator(PurchaseReconApiClient api) { this.api = Objects.requireNonNull(api); }

    public ImportService.ImportResult execute(Path file, Map<String, String> mapping, String note, boolean dryRun) throws Exception {
        Objects.requireNonNull(file, "file");
        Map<String, String> safeMapping = mapping == null ? Map.of() : mapping;
        List<PurchaseReconApiClient.ImportRow> rows = readRows(file, safeMapping);
        String fingerprint = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        PurchaseReconApiClient.ImportResult result = api.importRows(new PurchaseReconApiClient.ImportRequest(
            file.getFileName().toString(), fingerprint, note == null ? "" : note, dryRun, rows));
        return toImportResult(result, dryRun);
    }

    private List<PurchaseReconApiClient.ImportRow> readRows(Path file, Map<String, String> mapping) throws Exception {
        List<PurchaseReconApiClient.ImportRow> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            List<SpreadsheetLayoutDetector.Layout> layouts = SpreadsheetLayoutDetector.detectAll(workbook, mapping.values());
            if (layouts.isEmpty()) throw new IllegalArgumentException("No Purchase Recon worksheet matches the mapped columns.");
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (SpreadsheetLayoutDetector.Layout layout : layouts) {
                Sheet sheet = workbook.getSheetAt(layout.sheetIndex());
                Row header = sheet.getRow(layout.headerRowIndex());
                Map<String, Integer> indexes = new HashMap<>();
                for (Map.Entry<String, String> entry : mapping.entrySet()) {
                    if (entry.getValue() == null || entry.getValue().isBlank()) continue;
                    indexes.put(entry.getKey(), SpreadsheetLayoutDetector.findHeaderIndex(header, entry.getValue(), evaluator));
                }
                for (int rowIndex = layout.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (SpreadsheetLayoutDetector.isRowBlank(row, evaluator)) continue;
                    rows.add(new PurchaseReconApiClient.ImportRow(
                        sheet.getSheetName(), rowIndex + 1,
                        text(row, indexes.get("supplier_name"), evaluator),
                        text(row, indexes.get("supplier_gstin"), evaluator),
                        text(row, indexes.get("supplier_invoice_no"), evaluator),
                        dateIso(row, indexes.get("invoice_date"), evaluator),
                        amount(row, indexes.get("taxable_value"), evaluator),
                        amount(row, indexes.get("cgst"), evaluator),
                        amount(row, indexes.get("sgst"), evaluator),
                        amount(row, indexes.get("igst"), evaluator),
                        amount(row, indexes.get("invoice_value"), evaluator)));
                }
            }
        }
        return rows;
    }

    private ImportService.ImportResult toImportResult(PurchaseReconApiClient.ImportResult result, boolean dryRun) {
        List<ImportService.ImportRowResult> details = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (result.details() != null) {
            for (PurchaseReconApiClient.ImportRowResult row : result.details()) {
                boolean failed = "FAILED".equalsIgnoreCase(row.status());
                String reference = (row.supplierReference() == null || row.supplierReference().isBlank() ? "" : row.supplierReference() + " • ")
                    + (row.invoiceNo() == null ? "" : row.invoiceNo());
                String message = row.message() == null ? "" : row.message();
                String source = (row.sourceSheet() == null || row.sourceSheet().isBlank() ? "Sheet" : row.sourceSheet())
                    + " • Row " + (row.sourceRow() == null ? "?" : row.sourceRow());
                details.add(new ImportService.ImportRowResult(
                    source, reference, failed ? "FAILED" : "PASSED", row.action(), message, "", 0d));
                if (failed) errors.add(source + ": " + message);
            }
        }
        int skipped = result.alreadyCurrentRows() + result.duplicateRows() + result.conflictRows() + result.ignoredRows();
        return new ImportService.ImportResult(
            result.totalRows(), dryRun ? 0 : result.importedRows(), 0, skipped, errors, details);
    }

    static String text(Row row, Integer index, FormulaEvaluator evaluator) {
        if (row == null || index == null || index < 0) return "";
        return SpreadsheetLayoutDetector.format(row.getCell(index), evaluator);
    }

    static String dateIso(Row row, Integer index, FormulaEvaluator evaluator) {
        if (row == null || index == null || index < 0) return "";
        Cell cell = row.getCell(index);
        LocalDate excelDate = SpreadsheetLayoutDetector.dateValue(cell, evaluator);
        if (excelDate != null) return excelDate.toString();
        String value = SpreadsheetLayoutDetector.format(cell, evaluator);
        if (value.isBlank()) return "";
        try { return BusinessClock.parseDate(value).toString(); }
        catch (Exception ignored) { return value; }
    }

    static double amount(Row row, Integer index, FormulaEvaluator evaluator) {
        String value = text(row, index, evaluator);
        if (value.isBlank()) return 0d;
        String normalized = value.replace(",", "").replace("₹", "").replace("INR", "").trim();
        boolean negative = normalized.startsWith("(") && normalized.endsWith(")");
        if (negative) normalized = normalized.substring(1, normalized.length() - 1);
        try { return (negative ? -1d : 1d) * Double.parseDouble(normalized); }
        catch (NumberFormatException ignored) { return Double.NaN; }
    }
}
