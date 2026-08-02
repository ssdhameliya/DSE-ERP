package org.example;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.service.ImportService;
import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;
import org.example.util.SpreadsheetLayoutDetector;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** End-to-end dry-run coverage for every supported import module and flexible workbook layout. */
public final class FlexibleImportSmoke {
    private FlexibleImportSmoke() {}

    public static void main(String[] args) throws Exception {
        ConfigManager.load();
        DatabaseManager.initialize();
        Path directory = Files.createTempDirectory("dse-import-smoke-");
        ImportService service = new ImportService();

        verify(service.importItems(workbook(directory, "items.xlsx", false,
            List.of("item_code", "description", "gst", "selling_price", "opening_stock"),
            List.of("IMP-ITEM-1", "Imported item", "18", "1250", "100")),
            mapping("item_code", "description", "gst", "selling_price", "opening_stock"), false, (a,b) -> {}), "Item Master");

        Path customers = workbook(directory, "customers.xls", true,
            List.of("Customer Code", "Customer Name", "Phone", "Active"),
            List.of("IMP-CUS-1", "Imported customer", "9876543210", "true"));
        verify(service.importCustomers(customers,
            Map.of("party_code", "Customer Code", "name", "Customer Name", "phone", "Phone", "is_active", "Active"),
            false, (a,b) -> {}), "Customers/CRM XLS");

        verify(service.importSuppliers(workbook(directory, "suppliers.xlsx", false,
            List.of("party_code", "name", "email", "is_active"),
            List.of("IMP-SUP-1", "Imported supplier", "supplier@example.com", "true")),
            mapping("party_code", "name", "email", "is_active"), false, (a,b) -> {}), "Suppliers/HRM");

        verify(service.importSales(workbook(directory, "sales.xlsx", false,
            List.of("invoice_no", "invoice_date", "party_code", "item_code", "quantity", "rate", "gst_percent"),
            List.of("IMP-SAL-1", "2026-07-28", "IMP-CUS-1", "IMP-ITEM-1", "2", "100", "18")),
            mapping("invoice_no", "invoice_date", "party_code", "item_code", "quantity", "rate", "gst_percent"),
            false, (a,b) -> {}), "Sales");

        verify(service.importPurchases(workbook(directory, "purchases.xlsx", false,
            List.of("invoice_no", "invoice_date", "party_code", "item_code", "quantity", "rate", "gst_percent"),
            List.of("IMP-PUR-1", "28/07/2026", "IMP-SUP-1", "IMP-ITEM-1", "5", "80", "18")),
            mapping("invoice_no", "invoice_date", "party_code", "item_code", "quantity", "rate", "gst_percent"),
            false, (a,b) -> {}), "Purchases");

        verify(service.importMasterValues(workbook(directory, "masters.xlsx", false,
            List.of("category_code", "category_name", "value_code", "value", "display_order", "is_active"),
            List.of("TEST", "Test Category", "TST001", "Test Value", "1", "true")),
            mapping("category_code", "category_name", "value_code", "value", "display_order", "is_active"),
            false, (a,b) -> {}), "Master Categories and Values");

        try (Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(customers.toFile())) {
            SpreadsheetLayoutDetector.Layout layout = SpreadsheetLayoutDetector.detect(workbook,
                List.of("party_code", "name", "phone", "is_active"));
            if (layout.sheetIndex() != 1 || layout.headerRowIndex() != 3)
                throw new AssertionError("Detector selected sheet " + layout.sheetIndex() + ", row " + layout.headerRowIndex());
        }
        System.out.println("FlexibleImportSmoke passed: all six modules, multi-sheet/title rows, XLS and XLSX.");
    }

    private static Path workbook(Path directory, String name, boolean legacy,
                                 List<String> headers, List<String> values) throws Exception {
        Path target = directory.resolve(name);
        try (Workbook workbook = legacy ? new HSSFWorkbook() : new XSSFWorkbook();
             OutputStream output = Files.newOutputStream(target)) {
            Sheet cover = workbook.createSheet("Instructions");
            cover.createRow(0).createCell(0).setCellValue("DSE ERP Import Workbook");
            Sheet data = workbook.createSheet("Data");
            data.createRow(0).createCell(0).setCellValue("Import data below");
            data.createRow(2); // Deliberate blank/title spacing.
            Row header = data.createRow(3);
            for (int index = 0; index < headers.size(); index++) header.createCell(index).setCellValue(headers.get(index));
            Row row = data.createRow(4);
            for (int index = 0; index < values.size(); index++) row.createCell(index).setCellValue(values.get(index));
            workbook.write(output);
        }
        return target;
    }

    private static Map<String,String> mapping(String... fields) {
        Map<String,String> mapping = new LinkedHashMap<>();
        for (String field : fields) mapping.put(field, field);
        return mapping;
    }

    private static void verify(ImportService.ImportResult result, String module) {
        if (result.processed != 1 || result.imported != 1 || !result.errors.isEmpty())
            throw new AssertionError(module + " failed: processed=" + result.processed
                + ", imported=" + result.imported + ", errors=" + result.errors);
    }
}
