package org.example.documentstudio.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Regression check: rendering mutations must never reach the persisted token workbook. */
public final class ExcelTemplateRendererMutationSmoke {
    private ExcelTemplateRendererMutationSmoke() { }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("excel-template-mutation-");
        Path source = folder.resolve("source.xlsx");
        try {
            try (Workbook seed = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(source)) {
                seed.createSheet("Invoice").createRow(0).createCell(0).setCellValue("{{sales.number}}");
                seed.write(out);
            }
            byte[] before = Files.readAllBytes(source);
            try (Workbook detached = ExcelTemplateRenderer.openDetachedWorkbook(source)) {
                detached.getSheetAt(0).getRow(0).getCell(0).setCellValue("INV-SAMPLE-001");
                detached.getSheetAt(0).createRow(1).createCell(0).setCellValue("expanded row");
            }
            byte[] after = Files.readAllBytes(source);
            if (!Arrays.equals(before, after)) throw new AssertionError("Persisted source workbook was mutated");
            try (Workbook verify = ExcelTemplateRenderer.openDetachedWorkbook(source)) {
                String token = verify.getSheetAt(0).getRow(0).getCell(0).getStringCellValue();
                if (!"{{sales.number}}".equals(token)) throw new AssertionError("Mapping token was replaced: " + token);
                if (verify.getSheetAt(0).getRow(1) != null) throw new AssertionError("Expanded row leaked into source workbook");
            }
            System.out.println("EXCEL_TEMPLATE_SOURCE_IMMUTABLE_OK");
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(folder);
        }
    }
}
