package org.example.importing;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.config.WorkspaceManager;
import org.example.service.ImportService;
import org.example.util.BusinessClock;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/** Writes deterministic import-result evidence outside the JavaFX controller. */
public final class ImportResultReportService {
    private ImportResultReportService() { }

    public record Context(String module, String sourceFileName, boolean dryRun) { }

    public static Path write(ImportService.ImportResult result, Context context) throws Exception {
        Path folder = WorkspaceManager.getImportsFolder().resolve("Results");
        Files.createDirectories(folder);
        String moduleFile = context.module() == null ? "Import" : context.module().replaceAll("[^A-Za-z0-9]+", "_");
        String stamp = BusinessClock.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path target = folder.resolve("Import_Result_" + moduleFile + "_" + stamp + ".xlsx");

        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream output = new FileOutputStream(target.toFile())) {
            CellStyle headerStyle = ImportTemplateService.createHeaderStyle(workbook);
            Sheet summary = workbook.createSheet("Summary");
            String[][] summaryRows = {
                {"Module", context.module() == null ? "" : context.module()},
                {"Source File", context.sourceFileName() == null ? "" : context.sourceFileName()},
                {"Mode", context.dryRun() ? "Validate only" : "Import"},
                {"Processed", String.valueOf(result.processed)},
                {"Passed", String.valueOf(result.passedCount())},
                {"Imported", String.valueOf(result.imported)},
                {"Updated", String.valueOf(result.updated)},
                {"Skipped", String.valueOf(result.skipped)},
                {"Failed", String.valueOf(result.failedCount())},
                {"Business Date", BusinessClock.formatDate(BusinessClock.today())},
                {"Business Time", BusinessClock.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a")) + " " + BusinessClock.zoneAbbreviation()}
            };
            for (int i = 0; i < summaryRows.length; i++) {
                Row row = summary.createRow(i);
                row.createCell(0).setCellValue(summaryRows[i][0]);
                row.createCell(1).setCellValue(summaryRows[i][1]);
            }
            summary.setColumnWidth(0, 24 * 256);
            summary.setColumnWidth(1, 70 * 256);

            Sheet details = workbook.createSheet("Import Results");
            String[] headers = {"Source Row(s)", "Reference", "Status", "Action", "Tax Type", "GST %", "Message"};
            Row header = details.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowIndex = 1;
            if (!result.details.isEmpty()) {
                for (ImportService.ImportRowResult detail : result.details) {
                    Row row = details.createRow(rowIndex++);
                    row.createCell(0).setCellValue(detail.sourceRows);
                    row.createCell(1).setCellValue(detail.reference);
                    row.createCell(2).setCellValue(detail.status);
                    row.createCell(3).setCellValue(detail.action);
                    row.createCell(4).setCellValue(detail.taxType);
                    row.createCell(5).setCellValue(detail.gstPercent);
                    row.createCell(6).setCellValue(detail.message);
                }
            } else {
                for (String error : result.errors) {
                    Row row = details.createRow(rowIndex++);
                    row.createCell(2).setCellValue("FAILED");
                    row.createCell(3).setCellValue("NONE");
                    row.createCell(6).setCellValue(error);
                }
                if (rowIndex == 1) {
                    Row row = details.createRow(rowIndex);
                    row.createCell(2).setCellValue("PASSED");
                    row.createCell(3).setCellValue("SUMMARY");
                    row.createCell(6).setCellValue("Import completed without row-level errors.");
                }
            }
            details.createFreezePane(0, 1);
            details.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(1, rowIndex - 1), 0, headers.length - 1));
            int[] widths = {16, 24, 14, 16, 14, 12, 70};
            for (int i = 0; i < widths.length; i++) details.setColumnWidth(i, widths[i] * 256);
            workbook.write(output);
        }
        return target;
    }
}
