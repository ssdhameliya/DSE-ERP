package org.example;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.database.DatabaseManager;
import org.example.service.ImportService;
import org.example.service.PartyService;

import java.nio.file.Files;
import java.util.Map;

/** Verifies customer and supplier spreadsheet rows are persisted and then cleans them up. */
public final class PartyImportSmoke {
    public static void main(String[] args) throws Exception {
        DatabaseManager.initialize();
        var file = Files.createTempFile("erp-party-import-", ".xlsx");
        try (var workbook = new XSSFWorkbook(); var output = Files.newOutputStream(file)) {
            var sheet = workbook.createSheet("Parties");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("party_code");
            header.createCell(1).setCellValue("name");
            header.createCell(2).setCellValue("phone");
            header.createCell(3).setCellValue("is_active");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("TEST-IMPORT-ERP");
            row.createCell(1).setCellValue("Import Verification");
            row.createCell(2).setCellValue("9999999999");
            row.createCell(3).setCellValue("true");
            workbook.write(output);
        }
        Map<String, String> mapping = Map.of(
            "party_code", "party_code", "name", "name", "phone", "phone", "is_active", "is_active");
        ImportService service = new ImportService();
        var result = service.importCustomers(file, mapping, false, (done, total) -> { });
        var party = new PartyService().getByType("CUSTOMER").stream()
            .filter(value -> "TEST-IMPORT-ERP".equals(value.getPartyCode())).findFirst()
            .orElseThrow(() -> new AssertionError("Customer import did not persist"));
        new PartyService().delete(party.getId());
        Files.deleteIfExists(file);
        if (result.imported != 1) throw new AssertionError("Expected one imported customer");
        System.out.println("PARTY_IMPORT_OK");
    }
}
