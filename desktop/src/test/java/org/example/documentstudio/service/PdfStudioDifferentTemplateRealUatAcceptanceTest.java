package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.api.ApiSession;
import org.example.api.authority.ServerResourceClient;
import org.example.api.support.SupportApiClient;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceTestSupport;
import org.example.documentstudio.model.*;
import org.example.model.Sales;
import org.example.service.InvoicePdfService;
import org.example.service.SalesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in acceptance proving an unrelated fillable/source-styled PDF can be manually mapped,
 * activated, and used for the same persisted UAT Sales records without changing the BAU route.
 */
class PdfStudioDifferentTemplateRealUatAcceptanceTest {
    private AutoCloseable workspaceScope;

    @AfterEach
    void cleanup() throws Exception {
        ApiSession.clear();
        ConfigManager.clearRuntimeApiBaseUrl();
        if (workspaceScope != null) workspaceScope.close();
        workspaceScope = null;
    }

    @Test
    void differentFillableTemplateMapsAndRendersSingleAndMultiPageRealUatSales() throws Exception {
        String base = System.getProperty("dse.uat.base", "").trim();
        String token = System.getProperty("dse.uat.token", "").trim();
        String expiry = System.getProperty("dse.uat.expiry", "2099-01-01T00:00:00Z").trim();
        String outProperty = System.getProperty("dse.pdf.evidence", "").trim();
        String templateProperty = System.getProperty("dse.pdf.alt-template", "").trim();
        Assumptions.assumeTrue(!base.isBlank() && !token.isBlank() && !outProperty.isBlank() && !templateProperty.isBlank(),
                "Different-template real-UAT evidence properties were not supplied");

        Path inputTemplate = Path.of(templateProperty).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(inputTemplate), "Different source template is missing: " + inputTemplate);
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
        assertEquals(1, single.getLines().size());
        assertEquals(27, multi.getLines().size());

        // Control: no Studio default means BAU/standard renderer only.
        assertTrue(TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).isEmpty());
        Path standardSingle = copyPdf(InvoicePdfService.sales(single), evidence.resolve("01-STANDARD-NO-PDF-STUDIO-SINGLE-REAL-UAT-JI_25-2026_0110.pdf"));
        Path standardMulti = copyPdf(InvoicePdfService.sales(multi), evidence.resolve("02-STANDARD-NO-PDF-STUDIO-MULTI-REAL-UAT-IN_14-08-2026_0006.pdf"));

        DocumentTemplate template = TemplateStorageService.importPdf(inputTemplate, "Variant 08 - Manual Universal Mapping", DocumentType.SALES_INVOICE);
        template.setLayoutMode("MAPPED_FIXED");
        List<PdfFormFieldRegion> forms = PdfFormFieldExtractionService.extract(TemplateStorageService.originalPdf(template), 0);
        assertTrue(forms.size() >= 150, "Variant 08 AcroForm targets were not discovered");
        Map<String,PdfFormFieldRegion> byName = new LinkedHashMap<>();
        forms.forEach(r -> byName.put(r.fieldName(), r));

        List<TemplateElement> elements = new ArrayList<>();
        StringBuilder mapping = new StringBuilder("DSE ERP universal PDF Studio mapping - different fillable template\n");
        mapping.append("Source: ").append(inputTemplate.getFileName()).append("\n");
        mapping.append("AcroForm targets detected: ").append(forms.size()).append("\n\n");

        addFormField(elements, byName, "invoice_no", "document.number", mapping);
        addFormField(elements, byName, "invoice_date", "document.date", mapping);
        addFormField(elements, byName, "po_no", "document.poNumber", mapping);
        addFormField(elements, byName, "po_date", "document.poDate", mapping);
        addFormField(elements, byName, "transporter", "transport.name", mapping);
        addFormField(elements, byName, "contact", "transport.contact", mapping);

        addFormField(elements, byName, "bill_name", "party.name", mapping);
        addCombinedAddress(elements, byName, List.of("bill_addr1","bill_addr2","bill_addr3"), "party.billingAddress", "Billing Address", mapping);
        addFormField(elements, byName, "bill_gst", "party.billingGstin", mapping);
        addFormField(elements, byName, "ship_name", "party.name", mapping);
        addCombinedAddress(elements, byName, List.of("ship_addr1","ship_addr2","ship_addr3"), "party.deliveryAddress", "Delivery Address", mapping);
        addFormField(elements, byName, "ship_gst", "party.deliveryGstin", mapping);
        addFormField(elements, byName, "consignee_name", "party.name", mapping);
        addCombinedAddress(elements, byName, List.of("consignee_addr1","consignee_addr2","consignee_addr3"), "party.deliveryAddress", "Consignee Address", mapping);
        addFormField(elements, byName, "consignee_gst", "party.deliveryGstin", mapping);

        Path source = TemplateStorageService.sourcePdf(template);
        PdfSourceTableDetectionService.Detection tableDetection = PdfSourceTableDetectionService.detectItemTable(source, 0).orElseThrow();
        PdfAutoMappingService.ItemHeaderLayout header = tableDetection.header();
        List<String> mappedHeaderFields = header.cells().stream()
                .sorted(Comparator.comparingDouble(PdfAutoMappingService.ItemHeaderCell::x))
                .map(PdfAutoMappingService.ItemHeaderCell::suggestedField)
                .filter(v -> v != null && !v.isBlank())
                .toList();
        assertEquals(List.of("item.serial","item.hsn","item.descriptionWithRemarks","item.quantity","item.rate","item.discountPercent","item.gstPercent","item.total"),
                mappedHeaderFields, "Variant 08 recognized item meanings/order changed");
        assertTrue(header.cells().stream().anyMatch(c -> c.suggestedField().isBlank()),
                "Unknown physical source columns must remain visible for Review Mapping");
        assertTrue(header.cells().stream().noneMatch(c -> "INVOICE NO".equalsIgnoreCase(c.label())),
                "Source-grid bounds must exclude neighbouring labels from the Item Table");
        TemplateElement table = createItemTable(tableDetection, mapping);
        elements.add(table);

        TemplateElement financial = createFinancialSummary(byName, mapping);
        elements.add(financial);
        TemplateElement terms = createTerms(byName, mapping);
        elements.add(terms);

        template.setElements(elements);
        TemplateStorageService.saveDraft(template);
        DocumentTemplate reloaded = TemplateStorageService.find(template.getId()).orElseThrow();
        TemplateMappingValidationService.Result validation = TemplateMappingValidationService.evaluate(reloaded);
        if (validation.errorCount() > 0) {
            mapping.append("\nVALIDATION ERRORS:\n");
            validation.issues().stream().filter(TemplateValidationIssue::error).forEach(i -> mapping.append("- ").append(i.requirementId()).append(": ").append(i.userMessage()).append("\n"));
        }
        Files.writeString(evidence.resolve("03-DIFFERENT-TEMPLATE-MAPPING-EVIDENCE.txt"), mapping.toString());
        assertEquals(validation.requiredCount(), validation.requiredMapped(), "Different template is missing required mappings");
        assertEquals(0, validation.errorCount(), "Different template must be safe before activation");

        TemplateStorageService.publish(reloaded);
        DocumentTemplate published = TemplateStorageService.find(reloaded.getId()).orElseThrow();
        TemplateStorageService.activateAndSetDefault(published);
        DocumentTemplate active = TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).orElseThrow();
        assertEquals(reloaded.getId(), active.getId());
        assertTrue(active.isRuntimeEnabled() && active.isDefaultTemplate());

        Path studioSingle = copyPdf(InvoicePdfService.sales(single), evidence.resolve("04-PDF-STUDIO-DIFFERENT-TEMPLATE-SINGLE-REAL-UAT-JI_25-2026_0110.pdf"));
        Path studioMulti = copyPdf(InvoicePdfService.sales(multi), evidence.resolve("05-PDF-STUDIO-DIFFERENT-TEMPLATE-MULTI-REAL-UAT-IN_14-08-2026_0006.pdf"));

        assertEquals(1, pageCount(studioSingle));
        assertTrue(pageCount(studioMulti) >= 2, "27-line UAT invoice must paginate in the different template");
        String singleText = pdfText(studioSingle).toUpperCase(Locale.ROOT);
        String multiText = pdfText(studioMulti).toUpperCase(Locale.ROOT);
        assertTrue(singleText.contains("JI/25-2026/0110"));
        assertTrue(singleText.contains("IGST"));
        assertTrue(multiText.contains("IN/14-08-2026/0006"));
        assertTrue(multiText.contains("CGST"));
        assertTrue(multiText.contains("SGST"));
        assertTrue(multiText.contains("FREIGHT"));
        assertTrue(multiText.contains("PACKING"));

        String manifest = """
                DSE ERP - DIFFERENT TEMPLATE REAL-UAT UNIVERSAL MAPPING ACCEPTANCE
                ================================================================
                Code revision under test: %s (completed universal PDF Studio fixes)
                Server: %s
                Source PDF: %s
                Source kind: fillable AcroForm with fixed artwork and editable widgets
                AcroForm targets detected: %d

                Persisted UAT records (no dummy Sale inserted):
                - JI/25-2026/0110 | lines=%d | tax=%s | total=%.2f
                - IN/14-08-2026/0006 | lines=%d | charges=%d | tax=%s | total=%.2f

                Runtime routing:
                - Before activation: no PDF Studio default -> BAU/standard renderer.
                - Different PDF imported as Draft and mapped through PDF Studio model/mapping services.
                - Required mappings validated, then explicitly Published + Marked Default.
                - After activation: the same persisted Sales objects rendered through the different PDF Studio template.

                Page counts:
                - Standard single: %d
                - Standard multi : %d
                - Different-template Studio single: %d
                - Different-template Studio multi : %d

                RESULT: PASS
                """.formatted(org.example.update.BuildInfo.version(), base, inputTemplate.getFileName(), forms.size(),
                single.getLines().size(), single.getGstType(), single.getTotalAmount(),
                multi.getLines().size(), multi.getCharges().size(), multi.getGstType(), multi.getTotalAmount(),
                pageCount(standardSingle), pageCount(standardMulti), pageCount(studioSingle), pageCount(studioMulti));
        Files.writeString(evidence.resolve("00-DIFFERENT-TEMPLATE-REAL-UAT-MANIFEST.txt"), manifest);
    }

    private static void addFormField(List<TemplateElement> elements, Map<String,PdfFormFieldRegion> forms,
                                     String sourceName, String fieldKey, StringBuilder log) {
        PdfFormFieldRegion r = require(forms, sourceName);
        TemplateElement e = formElement(r);
        TemplateFieldDefinition field = TemplateFieldCatalog.findPdf(DocumentType.SALES_INVOICE, fieldKey);
        assertNotNull(field, "ERP field is not exposed to PDF Studio: " + fieldKey);
        ManualTemplateMappingService.mapField(e, field);
        configureFlow(e, fieldKey);
        e.setPageRule("EVERY");
        elements.add(e);
        log.append("FORM ").append(sourceName).append(" -> ").append(fieldKey).append("\n");
    }

    private static void addCombinedAddress(List<TemplateElement> elements, Map<String,PdfFormFieldRegion> forms,
                                           List<String> names, String fieldKey, String label, StringBuilder log) {
        List<PdfFormFieldRegion> parts = names.stream().map(n -> require(forms,n)).toList();
        double x=parts.stream().mapToDouble(PdfFormFieldRegion::x).min().orElseThrow();
        double y=parts.stream().mapToDouble(PdfFormFieldRegion::y).min().orElseThrow();
        double right=parts.stream().mapToDouble(r->r.x()+r.width()).max().orElseThrow();
        double bottom=parts.stream().mapToDouble(r->r.y()+r.height()).max().orElseThrow();
        TemplateElement e=TemplateElement.of(ElementType.TEXT,0,x,y,right-x,bottom-y);
        e.setReplacementSourceKey("PDF_FORM_GROUP|"+String.join("+",names));
        e.setReplacementGroupId("form-group-"+UUID.randomUUID());e.setSourceReplacementMode("FORM");e.setSourceMaskSafe(true);
        e.setFillEnabled(false);e.setStrokeEnabled(false);e.setFontFamily("HELVETICA");e.setFontSize(7.2);e.setTextFit("WRAP");
        TemplateFieldDefinition field=TemplateFieldCatalog.findPdf(DocumentType.SALES_INVOICE,fieldKey);assertNotNull(field);
        String blockId="FORM-BLOCK|"+label.toUpperCase(Locale.ROOT).replace(' ','_');
        e.setMappingBlockId(blockId);e.setMappingBlockLabel(label);e.setMappingBlockType("DETECTED_FORM_BLOCK");
        ManualTemplateMappingService.mapField(e,field);ManualTemplateMappingService.applyDetectedFlowBlock(e,field,blockId);
        e.setLineSpacing(.92);e.setFontSize(5.5);e.setPageRule("EVERY");
        elements.add(e);log.append("FORM GROUP ").append(label).append(" ").append(names).append(" -> ").append(fieldKey)
                .append(" | Multiline/WRAP/Auto Height/Grow Down\n");
    }

    private static TemplateElement createItemTable(PdfSourceTableDetectionService.Detection detection,
                                                    StringBuilder log) {
        TemplateElement table=detection.table();
        table.setFontSize(6.6);table.setFontFamily("HELVETICA");table.setSourceMaskSafe(false);
        table.setSourceReplacementMode("FORM");table.setFlowRole("ITEM_TABLE");table.setOverflowPolicy("PAGINATE");
        List<TemplateColumnBinding> bindings=new ArrayList<>();
        for(TemplateColumnBinding source:table.getTableColumnBindings()){
            TemplateColumnBinding binding=source.copy();
            if(binding.getFieldKey().isBlank()) binding.setMappingState("STATIC"); // explicit Review Mapping decision for ADD/DEL source controls
            bindings.add(binding);
        }
        table.setTableColumnBindings(bindings);
        ManualTemplateMappingService.syncLegacyColumns(table);
        log.append("ITEM TABLE physical headers:\n");
        for(TemplateColumnBinding b:table.getTableColumnBindings())log.append("  ").append(b.getSourceLabel()).append(" -> ")
                .append(b.getFieldKey().isBlank()?"STATIC":b.getFieldKey()).append(" | x=").append(String.format(Locale.ROOT,"%.1f",b.getXOffset()))
                .append(" w=").append(String.format(Locale.ROOT,"%.1f",b.getWidth())).append("\n");
        return table;
    }

    private static TemplateElement createFinancialSummary(Map<String,PdfFormFieldRegion> forms,StringBuilder log){
        PdfFormFieldRegion l1=require(forms,"calc_label_1"),v1=require(forms,"calc_value_1"),l9=require(forms,"calc_label_9"),v9=require(forms,"calc_value_9");
        double x=Math.min(l1.x(),v1.x()),y=Math.min(l1.y(),v1.y()),right=Math.max(l9.x()+l9.width(),v9.x()+v9.width()),bottom=Math.max(l9.y()+l9.height(),v9.y()+v9.height());
        TemplateElement e=TemplateElement.of(ElementType.BLOCK,0,x,y,right-x,bottom-y);e.setReplacementGroupId("DYNAMIC_FINANCIAL_SUMMARY");e.setSourceReplacementMode("FORM");
        e.setPageRule("LAST");e.setSourceStyleCaptured(true);e.setFillEnabled(false);e.setStrokeEnabled(false);e.setFontFamily("HELVETICA");e.setFontSize(6.4);e.setTextColor("#172033");
        e.setSummaryLabelRatio((v1.x()-x)/(right-x));e.setGrowthDirection("UP");e.setFlowAnchorMode("ABSOLUTE");e.setOverflowPolicy("ERROR");
        log.append("DYNAMIC FINANCIAL SUMMARY -> calc_label_1..calc_value_9 | source-positioned / grows up inside captured calculation region\n");return e;
    }

    private static TemplateElement createTerms(Map<String,PdfFormFieldRegion> forms,StringBuilder log){
        List<PdfFormFieldRegion> parts=new ArrayList<>();for(int i=1;i<=6;i++)parts.add(require(forms,"term_"+i));
        double x=parts.stream().mapToDouble(PdfFormFieldRegion::x).min().orElseThrow(),y=parts.stream().mapToDouble(PdfFormFieldRegion::y).min().orElseThrow();
        double right=parts.stream().mapToDouble(r->r.x()+r.width()).max().orElseThrow(),bottom=parts.stream().mapToDouble(r->r.y()+r.height()).max().orElseThrow();
        TemplateElement e=TemplateElement.of(ElementType.TEXT,0,x,y,right-x,bottom-y);e.setReplacementSourceKey("PDF_FORM_GROUP|terms");e.setReplacementGroupId("terms-"+UUID.randomUUID());e.setSourceReplacementMode("FORM");e.setSourceMaskSafe(true);
        e.setFillEnabled(false);e.setStrokeEnabled(false);e.setFontFamily("HELVETICA");e.setFontSize(6.6);e.setPageRule("LAST");
        TemplateFieldDefinition field=TemplateFieldCatalog.findPdf(DocumentType.SALES_INVOICE,"company.terms");assertNotNull(field);
        String blockId="FORM-BLOCK|TERMS";e.setMappingBlockId(blockId);e.setMappingBlockLabel("Terms & Conditions");e.setMappingBlockType("DETECTED_FORM_BLOCK");
        ManualTemplateMappingService.mapField(e,field);ManualTemplateMappingService.applyDetectedFlowBlock(e,field,blockId);
        log.append("FORM GROUP terms 1..6 -> company.terms | Multiline/WRAP/Auto Height/Grow Down\n");return e;
    }

    private static TemplateElement formElement(PdfFormFieldRegion r){
        TemplateElement e=TemplateElement.of(ElementType.TEXT,r.pageIndex(),r.x(),r.y(),r.width(),r.height());e.setText(r.sampleValue());e.setFontFamily("HELVETICA");e.setFontSize(Math.max(6,Math.min(8.5,r.height()*.6)));e.setTextFit("SHRINK");
        e.setFillEnabled(false);e.setStrokeEnabled(false);e.setReplacementSourceKey("PDF_FORM|"+r.pageIndex()+"|"+r.fieldName());e.setReplacementGroupId("form-"+UUID.randomUUID());e.setSourceReplacementMode("FORM");e.setSourceMaskSafe(true);return e;
    }

    private static void configureFlow(TemplateElement e,String key){ManualTemplateMappingService.configureMultilineMapping(e,key);}
    private static PdfFormFieldRegion require(Map<String,PdfFormFieldRegion> forms,String name){PdfFormFieldRegion r=forms.get(name);assertNotNull(r,"Missing source form field: "+name);return r;}

    private static void copyServerBusinessSettings(Path evidence) throws Exception {
        SupportApiClient support=new SupportApiClient();Map<String,String> keys=new LinkedHashMap<>();
        for(String key:List.of("company.name","company.address","company.shipAddress","company.gstin","company.phone","company.email","company.website","company.terms","company.logoPath","company.signaturePath","payment.bankName","payment.branch","payment.accountNumber","payment.ifsc","payment.accountType","payment.mode","payment.accountHolder","payment.upiId"))keys.put(key,support.setting(key,""));
        for(var entry:keys.entrySet())if(!entry.getKey().endsWith("Path"))ConfigManager.setWithoutSaving(entry.getKey(),entry.getValue());
        ConfigManager.setWithoutSaving("deployment.mode","LOCAL");
        for(String assetKey:List.of("company.logoPath","company.signaturePath")){String stored=keys.getOrDefault(assetKey,"");if(!stored.startsWith("server-resource:"))continue;try{byte[] bytes=new ServerResourceClient().get("BUSINESS_ASSET",assetKey);if(bytes.length==0)continue;Path local=evidence.resolve("workspace").resolve(assetKey.replace('.','-')+".png");Files.createDirectories(local.getParent());Files.write(local,bytes);ConfigManager.setWithoutSaving(assetKey,local.toString());}catch(Exception ex){ConfigManager.setWithoutSaving(assetKey,"");}}
        ConfigManager.save();
    }

    private static Sales requireSale(SalesService service,String invoice){Sales sale=service.getByInvoice(invoice);assertNotNull(sale,"Persisted UAT Sale is missing: "+invoice);assertNotNull(sale.getLines());return sale;}
    private static Path copyPdf(Path source,Path target)throws Exception{assertTrue(Files.isRegularFile(source)&&Files.size(source)>100,"PDF was not generated: "+source);Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING);return target;}
    private static int pageCount(Path pdf)throws Exception{try(PDDocument doc=Loader.loadPDF(pdf.toFile())){return doc.getNumberOfPages();}}
    private static String pdfText(Path pdf)throws Exception{try(PDDocument doc=Loader.loadPDF(pdf.toFile())){return new PDFTextStripper().getText(doc);}}
    private static void deleteTree(Path root)throws Exception{if(!Files.exists(root))return;try(var stream=Files.walk(root)){for(Path p:stream.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
}
