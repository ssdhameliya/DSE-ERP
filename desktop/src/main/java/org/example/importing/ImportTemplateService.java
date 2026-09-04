package org.example.importing;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes import workbooks; no JavaFX or controller state. */
public final class ImportTemplateService {
    public void write(Path target, String module, String appVersion) throws Exception {
        List<String> fields = ImportModuleRegistry.fields(module);
        try (Workbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(target)) {
            Sheet sheet = workbook.createSheet("Import Template");
            Sheet instructions = workbook.createSheet("Instructions");
            String[][] guidance = {
                {"DSE ERP " + appVersion + " Import Template", "Keep identifier and header names unchanged."},
                {"Recommended mode", "Update non-blank fields: blank spreadsheet cells preserve existing master data."},
                {"Create new only", "Existing identifiers are skipped; only new records are created."},
                {"Create or update", "Existing master records are replaced with supplied values."},
                {"Skip existing", "Existing identifiers are never changed."},
                {"Financial documents", "Existing posted Sales and Purchase invoices are always protected and skipped."},
                {"GST / IGST", "For Sales/Purchases use gst_type = GST for intra-state or IGST for inter-state. Enter gst_percent only; DSE ERP calculates tax amounts from line values."},
                {"GST calculation", "GST is calculated as CGST + SGST (equal halves); IGST applies the full GST rate as IGST. Do not enter tax amounts manually."},
                {"Unlimited Purchase charges", "Purchases may use additional_charges with entries separated by semicolons. Each entry is Type|Amount|Taxable|GSTPercent, for example Freight|250|true|18;Packing|100|false|0."},
                {"Multiple Purchase attachments", "Use attachment_files for semicolon-separated file paths. Paths may be absolute or relative to the import workbook. The older attachment_file column remains supported."},
                {"Safe process", "Run Validate only first, review the preview and generated result report, then import."},
                {"Identifiers", ImportModuleRegistry.identifierGuidance(module)}
            };
            for (int i = 0; i < guidance.length; i++) {
                Row row = instructions.createRow(i);
                row.createCell(0).setCellValue(guidance[i][0]);
                row.createCell(1).setCellValue(guidance[i][1]);
            }
            instructions.setColumnWidth(0, 28 * 256);
            instructions.setColumnWidth(1, 92 * 256);

            CellStyle headerStyle = createHeaderStyle(workbook);
            Row header = sheet.createRow(0);
            for (int i = 0; i < fields.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(fields.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, Math.max(14, fields.get(i).length() + 3) * 256);
            }

            List<List<String>> exampleRows = ImportModuleRegistry.exampleRows(module);
            int lastSampleRow = 0;
            for (int sampleIndex = 0; sampleIndex < exampleRows.size(); sampleIndex++) {
                Row sample = sheet.createRow(sampleIndex + 1);
                List<String> examples = exampleRows.get(sampleIndex);
                for (int columnIndex = 0; columnIndex < Math.min(fields.size(), examples.size()); columnIndex++) {
                    sample.createCell(columnIndex).setCellValue(examples.get(columnIndex));
                }
                lastSampleRow = sampleIndex + 1;
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(1, lastSampleRow), 0, fields.size() - 1));
            workbook.write(output);
        }
    }

    public static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
