package org.example.documentstudio.service;

import org.apache.poi.ss.usermodel.*;
import org.example.api.ApiSession;
import org.example.api.authority.ServerResourceClient;
import org.example.api.support.SupportApiClient;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceTestSupport;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ExcelTemplate;
import org.example.documentstudio.model.TemplateData;
import org.example.invoice.model.TaxInvoiceItem;
import org.example.model.Sales;
import org.example.service.SalesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real-UAT acceptance for fallback Excel output and explicit Excel Studio default routing. */
class ExcelStudioRealUatAcceptanceTest {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");
    private AutoCloseable workspaceScope;

    @AfterEach
    void cleanup() throws Exception {
        ApiSession.clear();
        ConfigManager.clearRuntimeApiBaseUrl();
        if (workspaceScope != null) workspaceScope.close();
        workspaceScope = null;
    }

    @Test
    void builtInThenExplicitDefaultExcelStudioRenderSameRealUatSales() throws Exception {
        String base = System.getProperty("dse.uat.base", "").trim();
        String token = System.getProperty("dse.uat.token", "").trim();
        String expiry = System.getProperty("dse.uat.expiry", "2099-01-01T00:00:00Z").trim();
        String outProperty = System.getProperty("dse.excel.evidence", "").trim();
        Assumptions.assumeTrue(!base.isBlank() && !token.isBlank() && !outProperty.isBlank(),
                "Real-UAT Excel evidence properties were not supplied");

        Path evidence = Path.of(outProperty).toAbsolutePath().normalize();
        deleteTree(evidence);
        Files.createDirectories(evidence);
        Path workspace = evidence.resolve("workspace");
        workspaceScope = WorkspaceTestSupport.useTransientWorkspace(workspace);
        ConfigManager.load();
        ConfigManager.setWithoutSaving("deployment.mode", "LOCAL");
        ConfigManager.applyRuntimeApiBaseUrl(base);
        ApiSession.establish(token, expiry, base);
        copyServerBusinessSettings(evidence);

        SalesService salesService = new SalesService();
        Sales single = requireSale(salesService, "JI/25-2026/0110");
        Sales multi = requireSale(salesService, "IN/14-08-2026/0006");
        assertEquals(1, single.getLines().size());
        assertEquals(27, multi.getLines().size());
        assertEquals(36290.00, single.getTotalAmount(), .01);
        assertEquals(91007.50, multi.getTotalAmount(), .01);
        assertEquals("IGST", single.getGstType());
        assertEquals("GST", multi.getGstType());

        assertTrue(ExcelTemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).isEmpty(),
                "Fresh evidence workspace must not start with an Excel Studio default");

        Path fallbackSingle = copyWorkbook(ExcelOutputService.sales(single),
                evidence.resolve("01-STANDARD-NO-EXCEL-STUDIO-SINGLE-REAL-UAT-JI_25-2026_0110.xlsx"));
        Path fallbackMulti = copyWorkbook(ExcelOutputService.sales(multi),
                evidence.resolve("02-STANDARD-NO-EXCEL-STUDIO-MULTI-REAL-UAT-IN_14-08-2026_0006.xlsx"));
        verifyRenderedWorkbook(fallbackSingle, single);
        verifyRenderedWorkbook(fallbackMulti, multi);

        ExcelTemplate template = ExcelTemplateStorageService.createBlank("Sales Invoice - UAT Manual Mapping Standard", DocumentType.SALES_INVOICE);
        writeMappingEvidence(template, evidence.resolve("03-EXCEL-STUDIO-MAPPING-EVIDENCE.txt"));
        ExcelDefaultCertification.certify(template);
        ExcelTemplateStorageService.activateAndSetDefault(template);
        ExcelTemplate active = ExcelTemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).orElseThrow();
        assertEquals(template.getId(), active.getId());
        assertTrue(active.isDefaultTemplate());

        Path studioSingle = copyWorkbook(ExcelOutputService.sales(single),
                evidence.resolve("04-EXCEL-STUDIO-DEFAULT-SINGLE-REAL-UAT-JI_25-2026_0110.xlsx"));
        Path studioMulti = copyWorkbook(ExcelOutputService.sales(multi),
                evidence.resolve("05-EXCEL-STUDIO-DEFAULT-MULTI-REAL-UAT-IN_14-08-2026_0006.xlsx"));
        verifyRenderedWorkbook(studioSingle, single);
        verifyRenderedWorkbook(studioMulti, multi);

        // The 27-line output must actually contain all 27 rendered item identities, not only a valid ZIP container.
        assertItemIdentityMultiset(studioMulti, TemplateDataFactory.fromSales(multi));
        assertChargeTypes(studioMulti, multi);

        String manifest = """
                DSE ERP %s - REAL UAT STANDARD VS EXCEL STUDIO ACCEPTANCE
                ==============================================================
                Source server: %s
                Data source: restored isolated UAT recovery database (persisted records; no dummy Sale inserted)
                Single Sale: JI/25-2026/0110 | lines=%d | tax=%s | total=%.2f
                Multi Sale: IN/14-08-2026/0006 | lines=%d | charges=%d | tax=%s | total=%.2f

                Runtime routing proof:
                - Before activation: defaultFor(SALES_INVOICE) = EMPTY; ExcelOutputService uses the built-in workbook fallback.
                - A Sales Invoice Excel Studio template was created from the standard starter workbook.
                - Mapping evidence records every {{ERP field}} token and its workbook cell address.
                - ExcelDefaultCertification passed using real ERP validation data.
                - Template was explicitly activated/defaulted.
                - After activation: ExcelOutputService renders the same persisted Sales records through the active Excel Studio workbook.

                Output verification:
                - all four .xlsx files reopen successfully through Apache POI
                - invoice number is present
                - Grand Total numeric value matches persisted UAT Sale total
                - no unresolved {{...}} ERP tokens remain
                - active multi workbook contains all 27 item identities
                - Freight/Packing charge names from the persisted UAT Sale are rendered

                RESULT: PASS
                """.formatted(org.example.update.BuildInfo.version(), base, single.getLines().size(), single.getGstType(), single.getTotalAmount(),
                multi.getLines().size(), multi.getCharges().size(), multi.getGstType(), multi.getTotalAmount());
        Files.writeString(evidence.resolve("00-REAL-UAT-EXCEL-ACCEPTANCE-MANIFEST.txt"), manifest);
    }

    private static void writeMappingEvidence(ExcelTemplate template, Path output) throws Exception {
        StringBuilder log = new StringBuilder("Excel Studio standard Sales Invoice mappings (" + org.example.update.BuildInfo.version() + ")\n");
        try (Workbook workbook = ExcelTemplateStorageService.openWorkbookDetached(template)) {
            for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
                Sheet sheet = workbook.getSheetAt(si);
                for (Row row : sheet) for (Cell cell : row) {
                    String text = cell.getCellType() == CellType.STRING ? cell.getStringCellValue()
                            : cell.getCellType() == CellType.FORMULA ? cell.getCellFormula() : "";
                    Matcher matcher = TOKEN.matcher(text == null ? "" : text);
                    while (matcher.find()) log.append(String.format(Locale.ROOT, "%s!%-6s -> %s%n",
                            sheet.getSheetName(), cell.getAddress().formatAsString(), matcher.group(1)));
                }
            }
        }
        Files.writeString(output, log.toString());
    }

    private static void verifyRenderedWorkbook(Path file, Sales sale) throws Exception {
        assertTrue(Files.isRegularFile(file) && Files.size(file) > 1000, "Workbook was not generated: " + file);
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            assertTrue(workbook.getNumberOfSheets() > 0);
            List<String> strings = allStrings(workbook);
            assertTrue(strings.stream().anyMatch(v -> v.contains(sale.getInvoiceNo())), "Invoice number missing: " + sale.getInvoiceNo());
            assertTrue(findNumeric(workbook, sale.getTotalAmount(), .011), "Grand Total missing: " + sale.getTotalAmount());
            assertTrue(strings.stream().noneMatch(v -> TOKEN.matcher(v).find()), "Rendered workbook contains unresolved ERP tokens");
        }
    }

    private static void assertItemIdentityMultiset(Path workbookFile, TemplateData data) throws Exception {
        Map<String,Integer> expected = new LinkedHashMap<>();
        for (TaxInvoiceItem item : data.items()) expected.merge(descriptionWithRemarks(item), 1, Integer::sum);
        Map<String,Integer> actual = new LinkedHashMap<>();
        try (Workbook workbook = WorkbookFactory.create(workbookFile.toFile())) {
            for (String value : allStrings(workbook)) if (expected.containsKey(value)) actual.merge(value, 1, Integer::sum);
        }
        assertEquals(expected, actual, "Rendered Excel item rows do not match all persisted UAT items");
    }

    private static void assertChargeTypes(Path workbookFile, Sales sale) throws Exception {
        List<String> expected = sale.getCharges().stream().map(c -> c.getChargeType() == null ? "" : c.getChargeType().trim()).filter(s -> !s.isBlank()).toList();
        try (Workbook workbook = WorkbookFactory.create(workbookFile.toFile())) {
            List<String> values = allStrings(workbook);
            for (String charge : expected) assertTrue(values.stream().anyMatch(v -> v.equalsIgnoreCase(charge)), "Charge not rendered: " + charge);
        }
    }

    private static String descriptionWithRemarks(TaxInvoiceItem item) {
        String d = item.getDescription() == null ? "" : item.getDescription().trim();
        String r = item.getRemarks() == null ? "" : item.getRemarks().trim();
        return r.isBlank() ? d : d.isBlank() ? r : d + "\n" + r;
    }

    private static List<String> allStrings(Workbook workbook) {
        List<String> out = new ArrayList<>();
        for (int si=0; si<workbook.getNumberOfSheets(); si++) for (Row row : workbook.getSheetAt(si)) for (Cell cell : row) {
            if (cell.getCellType() == CellType.STRING) out.add(cell.getStringCellValue());
            else if (cell.getCellType() == CellType.FORMULA) out.add(cell.getCellFormula());
        }
        return out;
    }

    private static boolean findNumeric(Workbook workbook, double expected, double tolerance) {
        for (int si=0; si<workbook.getNumberOfSheets(); si++) for (Row row : workbook.getSheetAt(si)) for (Cell cell : row) {
            if (cell.getCellType() == CellType.NUMERIC && Math.abs(cell.getNumericCellValue() - expected) <= tolerance) return true;
        }
        return false;
    }

    private static Path copyWorkbook(Path source, Path target) throws Exception {
        assertTrue(Files.isRegularFile(source) && Files.size(source) > 1000, "Excel output missing: " + source);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static Sales requireSale(SalesService service, String invoice) {
        Sales sale = service.getByInvoice(invoice);
        assertNotNull(sale, "Persisted UAT Sale is missing: " + invoice);
        assertNotNull(sale.getLines());
        return sale;
    }

    private static void copyServerBusinessSettings(Path evidence) throws Exception {
        SupportApiClient support = new SupportApiClient();
        Map<String,String> keys = new LinkedHashMap<>();
        for (String key : List.of("company.name","company.address","company.shipAddress","company.gstin","company.phone",
                "company.email","company.website","company.terms","company.logoPath","company.signaturePath",
                "payment.bankName","payment.branch","payment.accountNumber","payment.ifsc","payment.accountType",
                "payment.mode","payment.accountHolder","payment.upiId")) keys.put(key, support.setting(key, ""));
        for (var entry : keys.entrySet()) if (!entry.getKey().endsWith("Path")) ConfigManager.setWithoutSaving(entry.getKey(), entry.getValue());
        ConfigManager.setWithoutSaving("deployment.mode", "LOCAL");
        for (String assetKey : List.of("company.logoPath","company.signaturePath")) {
            String stored = keys.getOrDefault(assetKey, "");
            if (!stored.startsWith("server-resource:")) continue;
            try {
                byte[] bytes = new ServerResourceClient().get("BUSINESS_ASSET", assetKey);
                if (bytes.length == 0) continue;
                Path local = evidence.resolve("workspace").resolve(assetKey.replace('.', '-') + ".png");
                Files.createDirectories(local.getParent()); Files.write(local, bytes); ConfigManager.setWithoutSaving(assetKey, local.toString());
            } catch (Exception ignored) { ConfigManager.setWithoutSaving(assetKey, ""); }
        }
        ConfigManager.save();
    }

    private static void deleteTree(Path p) throws Exception {
        if (!Files.exists(p)) return;
        try (var walk = Files.walk(p)) { for (Path x : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(x); }
    }
}
