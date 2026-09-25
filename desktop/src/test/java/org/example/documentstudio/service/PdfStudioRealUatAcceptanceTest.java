package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.api.ApiSession;
import org.example.api.authority.ServerResourceClient;
import org.example.api.support.SupportApiClient;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceTestSupport;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateElement;
import org.example.documentstudio.model.TemplateFieldDefinition;
import org.example.model.Sales;
import org.example.service.InvoicePdfService;
import org.example.service.SalesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in acceptance against a restored UAT snapshot.  This test is deliberately skipped in ordinary
 * unit runs; provide dse.uat.base + dse.uat.token + dse.pdf.evidence to exercise the complete runtime
 * route with persisted Sales records.  No business record is inserted or updated by this test.
 */
class PdfStudioRealUatAcceptanceTest {
    private AutoCloseable workspaceScope;

    @AfterEach
    void cleanup() throws Exception {
        ApiSession.clear();
        ConfigManager.clearRuntimeApiBaseUrl();
        if (workspaceScope != null) workspaceScope.close();
        workspaceScope = null;
    }

    @Test
    void standardThenExplicitDefaultStudioRenderTheSameRealUatSales() throws Exception {
        String base = System.getProperty("dse.uat.base", "").trim();
        String token = System.getProperty("dse.uat.token", "").trim();
        String expiry = System.getProperty("dse.uat.expiry", "2099-01-01T00:00:00Z").trim();
        String outProperty = System.getProperty("dse.pdf.evidence", "").trim();
        Assumptions.assumeTrue(!base.isBlank() && !token.isBlank() && !outProperty.isBlank(),
                "Real-UAT evidence properties were not supplied");

        Path evidence = Path.of(outProperty).toAbsolutePath().normalize();
        Files.createDirectories(evidence);
        Path workspace = evidence.resolve("workspace");
        deleteTree(workspace);
        workspaceScope = WorkspaceTestSupport.useTransientWorkspace(workspace);
        ConfigManager.load();
        ConfigManager.setWithoutSaving("deployment.mode", "LOCAL");
        ConfigManager.applyRuntimeApiBaseUrl(base);
        ApiSession.establish(token, expiry, base);
        copyServerBusinessSettings(evidence);

        SalesService salesService = new SalesService();
        Sales single = requireSale(salesService, "JI/25-2026/0110");
        Sales multi = requireSale(salesService, "IN/14-08-2026/0006");

        assertEquals(1, single.getLines().size(), "Persisted UAT single record changed unexpectedly");
        assertEquals(27, multi.getLines().size(), "Persisted UAT multi record changed unexpectedly");
        assertEquals("IGST", single.getGstType(), "Single acceptance record must remain IGST");
        assertEquals("GST", multi.getGstType(), "Multi acceptance record must remain GST");
        assertEquals(36290.00, single.getTotalAmount(), .01, "Persisted UAT single total changed unexpectedly");
        assertEquals(91007.50, multi.getTotalAmount(), .01, "Persisted UAT multi total changed unexpectedly");

        // Fresh isolated workspace may contain built-in draft templates, but runtime must still have NO default.
        assertTrue(TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).isEmpty(),
                "No active/default PDF Studio template is allowed before BAU evidence generation");

        Path standardSingle = copyPdf(InvoicePdfService.sales(single),
                evidence.resolve("01-STANDARD-NO-PDF-STUDIO-SINGLE-REAL-UAT-JI_25-2026_0110.pdf"));
        Path standardMulti = copyPdf(InvoicePdfService.sales(multi),
                evidence.resolve("02-STANDARD-NO-PDF-STUDIO-MULTI-REAL-UAT-IN_14-08-2026_0006.pdf"));
        assertPages(standardSingle, 1);
        assertTrue(pageCount(standardMulti) >= 2, "The persisted 27-line UAT Sale must be multi-page in the standard renderer");

        // Re-create the current-standard template through the same mapping service used by the PDF Studio Map button.
        Path root = TemplateStorageService.root();
        Files.deleteIfExists(root.resolve(".builtin-sales-invoice-deleted"));
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate working = TemplateStorageService.find(BuiltInPdfTemplateInstaller.SALES_TEMPLATE_ID).orElseThrow();
        remapThroughProductionPath(working, evidence.resolve("03-PDF-STUDIO-MAPPING-EVIDENCE.txt"));
        DocumentTemplate remapped = TemplateStorageService.find(working.getId()).orElseThrow();
        TemplateMappingValidationService.Result validation = TemplateMappingValidationService.evaluate(remapped);
        assertEquals(validation.requiredCount(), validation.requiredMapped(), "All required PDF Studio mappings must survive reload");
        assertEquals(0, validation.errorCount(), "Mapped current-standard template must be activation-safe");
        TemplateElement validatedTable = remapped.getElements().stream()
                .filter(e -> e != null && e.getType() == ElementType.ITEM_TABLE).findFirst().orElseThrow();
        int effectiveRowsPerPage = TemplateMappingValidationService.effectiveItemRowsPerPage(remapped, validatedTable);
        boolean itemFlowWarning = validation.issues().stream()
                .anyMatch(issue -> "ITEM_TABLE_PAGE_FLOW".equals(issue.requirementId()));
        assertFalse(itemFlowWarning, "Runtime-safe FLOW_FIXED table must not report the old false one-row warning");
        assertTrue(effectiveRowsPerPage > 1, "Runtime planner must expose more than one usable item row");

        TemplateStorageService.publish(remapped);
        DocumentTemplate published = TemplateStorageService.find(remapped.getId()).orElseThrow();
        TemplateStorageService.activateAndSetDefault(published);
        DocumentTemplate active = TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).orElseThrow();
        assertTrue(active.isDefaultTemplate() && active.isRuntimeEnabled());
        assertEquals(BuiltInPdfTemplateInstaller.SALES_TEMPLATE_ID, active.getId());

        Path studioSingle = copyPdf(InvoicePdfService.sales(single),
                evidence.resolve("04-PDF-STUDIO-DEFAULT-SINGLE-REAL-UAT-JI_25-2026_0110.pdf"));
        Path studioMulti = copyPdf(InvoicePdfService.sales(multi),
                evidence.resolve("05-PDF-STUDIO-DEFAULT-MULTI-REAL-UAT-IN_14-08-2026_0006.pdf"));
        assertPages(studioSingle, 1);
        assertTrue(pageCount(studioMulti) >= 2, "The mapped current-standard template must paginate the 27-line UAT Sale");

        String studioSingleText = pdfText(studioSingle);
        String studioMultiText = pdfText(studioMulti);
        assertTrue(studioSingleText.contains("JI/25-2026/0110"));
        assertTrue(studioMultiText.contains("IN/14-08-2026/0006"));
        assertTrue(studioSingleText.toUpperCase(Locale.ROOT).contains("IGST"), "IGST row must be data-driven");
        assertTrue(studioMultiText.toUpperCase(Locale.ROOT).contains("CGST"));
        assertTrue(studioMultiText.toUpperCase(Locale.ROOT).contains("SGST"));
        assertTrue(studioMultiText.toUpperCase(Locale.ROOT).contains("FREIGHT"));
        assertTrue(studioMultiText.toUpperCase(Locale.ROOT).contains("PACKING"));

        String manifest = """
                DSE ERP %s - REAL UAT STANDARD VS PDF STUDIO ACCEPTANCE
                ============================================================
                Source server: %s
                Data source: restored isolated UAT recovery database (persisted records; no dummy Sale inserted)
                Single Sale: JI/25-2026/0110 | lines=%d | tax=%s | total=%.2f
                Multi Sale: IN/14-08-2026/0006 | lines=%d | charges=%d | tax=%s | total=%.2f

                Runtime routing proof:
                - Before activation: defaultFor(SALES_INVOICE) = EMPTY; InvoicePdfService -> standard SalesTaxInvoiceService path.
                - PDF Studio current-standard template remapped through ManualTemplateMappingService and saved after each mapping.
                - Template reloaded, validated, published, then explicitly activated/defaulted.
                - Central FLOW_FIXED planner effective Item capacity: %d row(s) before continuation pagination.
                - ITEM_TABLE_PAGE_FLOW warning present after mapping: %s.
                - After activation: InvoicePdfService -> active PDF Studio mapped renderer for the same persisted Sales objects.

                Outputs:
                01 standard single: %d page(s)
                02 standard multi : %d page(s)
                04 Studio single  : %d page(s)
                05 Studio multi   : %d page(s)

                RESULT: PASS
                """.formatted(org.example.update.BuildInfo.version(), base, single.getLines().size(), single.getGstType(), single.getTotalAmount(),
                multi.getLines().size(), multi.getCharges().size(), multi.getGstType(), multi.getTotalAmount(),
                effectiveRowsPerPage, itemFlowWarning,
                pageCount(standardSingle), pageCount(standardMulti), pageCount(studioSingle), pageCount(studioMulti));
        Files.writeString(evidence.resolve("00-REAL-UAT-ACCEPTANCE-MANIFEST.txt"), manifest);
    }

    private static void remapThroughProductionPath(DocumentTemplate template, Path logPath) throws Exception {
        record FieldTarget(TemplateElement element, TemplateFieldDefinition field) {}
        List<FieldTarget> fields = new ArrayList<>();
        for (TemplateElement element : template.getElements()) {
            if (element == null || element.getFieldKey().isBlank()) continue;
            TemplateFieldDefinition field = TemplateFieldCatalog.findPdf(DocumentType.SALES_INVOICE, element.getFieldKey());
            if (field == null) continue;
            fields.add(new FieldTarget(element, field));
            element.setFieldKey("");
            if (field.image()) { element.setType(ElementType.IMAGE); element.setText(""); }
            else { element.setType(ElementType.TEXT); element.setText("UNMAPPED " + field.label()); }
        }
        TemplateElement itemTable = template.getElements().stream()
                .filter(e -> e != null && e.getType() == ElementType.ITEM_TABLE).findFirst().orElseThrow();
        List<String> itemColumns = new ArrayList<>(itemTable.getTableColumns());
        itemTable.setTableColumns(List.of());
        itemTable.setTableColumnBindings(List.of());
        TemplateStorageService.saveDraft(template);

        StringBuilder log = new StringBuilder("PDF Studio current-standard production-path mapping\n");
        int number = 0;
        for (FieldTarget target : fields) {
            ManualTemplateMappingService.mapField(target.element(), target.field());
            TemplateStorageService.saveDraft(template);
            log.append(String.format(Locale.ROOT, "%02d FIELD %-32s -> %s SAVED%n",
                    ++number, target.field().label(), target.field().key()));
        }
        for (String column : itemColumns) {
            String key = TemplateMappingValidationService.itemColumnKey(column);
            ManualTemplateMappingService.mapItemColumn(itemTable, key);
            TemplateStorageService.saveDraft(template);
            log.append(String.format(Locale.ROOT, "ITEM %-32s -> %s SAVED%n", column, key));
        }
        Files.writeString(logPath, log.toString());
    }

    private static void copyServerBusinessSettings(Path evidence) throws Exception {
        SupportApiClient support = new SupportApiClient();
        Map<String, String> keys = new LinkedHashMap<>();
        for (String key : List.of(
                "company.name", "company.address", "company.shipAddress", "company.gstin", "company.phone",
                "company.email", "company.website", "company.terms", "company.logoPath", "company.signaturePath",
                "payment.bankName", "payment.branch", "payment.accountNumber", "payment.ifsc", "payment.accountType",
                "payment.mode", "payment.accountHolder", "payment.upiId")) {
            keys.put(key, support.setting(key, ""));
        }
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            if (!entry.getKey().endsWith("Path")) ConfigManager.setWithoutSaving(entry.getKey(), entry.getValue());
        }
        ConfigManager.setWithoutSaving("deployment.mode", "LOCAL");

        for (String assetKey : List.of("company.logoPath", "company.signaturePath")) {
            String stored = keys.getOrDefault(assetKey, "");
            if (!stored.startsWith("server-resource:")) continue;
            try {
                ServerResourceClient resources = new ServerResourceClient();
                byte[] bytes = resources.get("BUSINESS_ASSET", assetKey);
                if (bytes.length == 0) continue;
                String ext = assetKey.contains("logo") ? ".png" : ".png";
                Path local = evidence.resolve("workspace").resolve(assetKey.replace('.', '-') + ext);
                Files.createDirectories(local.getParent());
                Files.write(local, bytes);
                ConfigManager.setWithoutSaving(assetKey, local.toString());
            } catch (Exception missingOptionalAsset) {
                ConfigManager.setWithoutSaving(assetKey, "");
            }
        }
        ConfigManager.save();
    }

    private static Sales requireSale(SalesService service, String invoice) {
        Sales sale = service.getByInvoice(invoice);
        assertNotNull(sale, "Persisted UAT Sale is missing: " + invoice);
        assertNotNull(sale.getLines(), "Persisted UAT lines are missing: " + invoice);
        return sale;
    }

    private static Path copyPdf(Path source, Path target) throws Exception {
        assertTrue(Files.isRegularFile(source) && Files.size(source) > 100, "PDF was not generated: " + source);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static void assertPages(Path pdf, int pages) throws Exception { assertEquals(pages, pageCount(pdf), pdf.toString()); }
    private static int pageCount(Path pdf) throws Exception { try (PDDocument doc = Loader.loadPDF(pdf.toFile())) { return doc.getNumberOfPages(); } }
    private static String pdfText(Path pdf) throws Exception { try (PDDocument doc = Loader.loadPDF(pdf.toFile())) { return new PDFTextStripper().getText(doc); } }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
