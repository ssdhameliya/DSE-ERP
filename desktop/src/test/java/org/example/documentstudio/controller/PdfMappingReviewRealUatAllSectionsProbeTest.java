package org.example.documentstudio.controller;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.api.ApiSession;
import org.example.api.authority.ServerResourceClient;
import org.example.api.support.SupportApiClient;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceTestSupport;
import org.example.documentstudio.model.*;
import org.example.documentstudio.service.DocumentDataService;
import org.example.documentstudio.service.PdfMappingReviewSession;
import org.example.documentstudio.view.PdfMappingReviewWorkspace;
import org.example.theme.ThemeManager;
import org.example.util.AppDialogRenderer;
import org.example.util.OwnedDialog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PdfMappingReviewRealUatAllSectionsProbeTest {
    private AutoCloseable workspaceScope;

    @AfterEach
    void cleanup() throws Exception {
        ApiSession.clear();
        ConfigManager.clearRuntimeApiBaseUrl();
        if (workspaceScope != null) workspaceScope.close();
        workspaceScope = null;
    }

    @Test
    void captureEveryReviewSectionFromPersistedUatRecordInLightAndDark() throws Exception {
        String base = System.getProperty("dse.uat.base", "").trim();
        String token = System.getProperty("dse.uat.token", "").trim();
        String expiry = System.getProperty("dse.uat.expiry", "2099-01-01T00:00:00Z").trim();
        String folderProp = System.getProperty("dse.pdf.mapping.review.uat.screens", "").trim();
        Assumptions.assumeTrue(!base.isBlank() && !token.isBlank() && !folderProp.isBlank(),
                "Real-UAT popup screenshot properties were not supplied");

        Path folder = Path.of(folderProp).toAbsolutePath().normalize();
        Files.createDirectories(folder);
        workspaceScope = WorkspaceTestSupport.useTransientWorkspace(folder.resolve("workspace"));
        ConfigManager.load();
        ConfigManager.setWithoutSaving("deployment.mode", "LOCAL");
        ConfigManager.applyRuntimeApiBaseUrl(base);
        ApiSession.establish(token, expiry, base);
        copyServerBusinessSettings(folder);

        TemplateData data = DocumentDataService.load(DocumentType.SALES_INVOICE, "IN/14-08-2026/0006");
        assertEquals("IN/14-08-2026/0006", data.value("document.number"));
        assertEquals(27, data.items().size());
        assertEquals("GST", data.gstType());
        DocumentTemplate template = reviewTemplate(data);
        List<PdfMappingReviewSession.DetectedBlock> detectedBlocks = detectedBlocks(data);
        Map<String,List<PdfMappingReviewSession.FinancialRoleDetection>> financialRoles = financialRoles(template, data);
        PdfMappingReviewSession proof = PdfMappingReviewSession.from(template, detectedBlocks, financialRoles);
        assertEquals(4, proof.pendingEntries().size(), "XYZ source values must be reviewed before scalar TemplateElements exist");
        assertTrue(proof.navigationItems().stream().anyMatch(n -> n.dynamic() && "XYZ DETAILS".equals(n.label())));
        assertTrue(proof.entries(PdfMappingReviewSession.Section.FINANCIAL).stream()
                .anyMatch(e -> e.fieldKey().startsWith("charge.*") && data.charges().stream().anyMatch(c -> c.type().equalsIgnoreCase(e.sourceLabel()))));

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Runnable work = () -> {
            try {
                StackPane ownerRoot = new StackPane();
                ownerRoot.setPrefSize(1440, 900);
                Scene ownerScene = new Scene(ownerRoot, 1440, 900);
                ThemeManager.applyTheme(ownerScene);
                Stage owner = new Stage();
                owner.setScene(ownerScene);
                owner.setTitle("DSE ERP - PDF Studio - Real UAT IN/14-08-2026/0006");
                owner.show();
                captureAll(ownerRoot, template, detectedBlocks, financialRoles, folder, "LIGHT");
                ThemeManager.toggle(ownerScene);
                captureAll(ownerRoot, template, detectedBlocks, financialRoles, folder, "DARK");
                owner.close();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        };
        try { Platform.startup(work); } catch (IllegalStateException alreadyStarted) { Platform.runLater(work); }
        assertTrue(done.await(90, TimeUnit.SECONDS), "Real-UAT mapping review screenshots timed out");
        if (failure.get() != null) throw new AssertionError("Real-UAT mapping review screenshots failed", failure.get());

        long pngs;
        try (var files = Files.list(folder)) {
            pngs = files.filter(p -> p.getFileName().toString().endsWith(".png")).count();
        }
        assertEquals(24, pngs);

        Files.writeString(folder.resolve("00-REAL-UAT-POPUP-EVIDENCE.txt"), """
                DSE ERP %s - REVIEW AUTO MAPPING REAL-UAT UI EVIDENCE
                ==========================================================
                API source: %s
                Persisted UAT Sale: IN/14-08-2026/0006
                Item rows: %d
                Tax mode: %s
                Popup screenshots: 9 sections x Light/Dark plus Billing/Financial/Terms detail captures = 24
                Business values in rows come from the authenticated persisted UAT record and server business settings.
                XYZ DETAILS is supplied as an uncommitted detected source block to prove dynamic navigation and pre-commit review;
                business values come from the persisted UAT Sale and no fake Sale record is inserted or modified.
                Centralized dialog path: OwnedDialog -> AppDialogRenderer -> app-dialog.css / active theme.
                """.formatted(org.example.update.BuildInfo.version(), base, data.items().size(), data.gstType()));
    }

    private static void captureAll(StackPane ownerRoot, DocumentTemplate template,
                                   List<PdfMappingReviewSession.DetectedBlock> detectedBlocks,
                                   Map<String,List<PdfMappingReviewSession.FinancialRoleDetection>> financialRoles,
                                   Path folder, String mode) throws Exception {
        int i = 1;
        for (PdfMappingReviewSession.Section section : PdfMappingReviewSession.Section.values()) {
            PdfMappingReviewSession session = PdfMappingReviewSession.from(template, detectedBlocks, financialRoles);
            PdfMappingReviewWorkspace workspace = new PdfMappingReviewWorkspace(session, section);
            ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            ButtonType save = new ButtonType("Save Mapping", ButtonBar.ButtonData.OK_DONE);
            OwnedDialog<ButtonType> dialog = new OwnedDialog<>(ownerRoot);
            AppDialogRenderer.configureWorkspace(dialog, "mapping", "Review Auto Mapping",
                    "Real-UAT record IN/14-08-2026/0006 • " + section.label(),
                    "Review detected ERP meanings. Auto detection stays editable; confirmed/manual choices are preserved on re-detection.", null);
            dialog.getDialogPane().setContent(workspace);
            dialog.getDialogPane().setPrefSize(1240, 790);
            dialog.getDialogPane().getButtonTypes().setAll(cancel, save);
            dialog.show();
            dialog.getDialogPane().applyCss();
            dialog.getDialogPane().layout();
            String filename = "%02d-%s-%s.png".formatted(i++, safe(section.label()), mode);
            snapshot(dialog, folder.resolve(filename));
            if (section == PdfMappingReviewSession.Section.BILLING
                    || section == PdfMappingReviewSession.Section.FINANCIAL
                    || section == PdfMappingReviewSession.Section.TERMS_FOOTER) {
                var node = workspace.lookup(".dse-workspace-scroll");
                if (node instanceof ScrollPane scroll) {
                    scroll.setVvalue(scroll.getVmax());
                    scroll.applyCss(); scroll.requestLayout(); scroll.layout();
                    Object pulseKey = new Object();
                    Platform.runLater(() -> Platform.exitNestedEventLoop(pulseKey, null));
                    Platform.enterNestedEventLoop(pulseKey);
                }
                workspace.applyCss(); workspace.layout();
                String detail = "%02d-%s-%s-DETAIL.png".formatted(i - 1, safe(section.label()), mode);
                snapshot(dialog, folder.resolve(detail));
            }
            dialog.close();
        }
    }

    private static void snapshot(OwnedDialog<ButtonType> dialog, Path output) throws Exception {
        WritableImage image = dialog.getDialogPane().getScene().snapshot(null);
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var reader = image.getPixelReader();
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            reader.getPixels(0, y, width, 1, javafx.scene.image.PixelFormat.getIntArgbInstance(), row, 0, width);
            buffered.setRGB(0, y, width, 1, row, 0, width);
        }
        ImageIO.write(buffered, "png", output.toFile());
    }

    private static DocumentTemplate reviewTemplate(TemplateData d) {
        DocumentTemplate t = new DocumentTemplate();
        t.setDocumentType(DocumentType.SALES_INVOICE);
        List<TemplateElement> e = new ArrayList<>();

        e.add(field("INVOICE NO : " + value(d,"document.number"), "document.number", .99, "HEADER", "DOCUMENT HEADER"));
        e.add(field("INVOICE DATE : " + value(d,"document.date"), "document.date", .99, "HEADER", "DOCUMENT HEADER"));
        e.add(field("PO NO : " + value(d,"document.poNumber"), "document.poNumber", .96, "HEADER", "DOCUMENT HEADER"));
        e.add(field("PO DATE : " + value(d,"document.poDate"), "document.poDate", .96, "HEADER", "DOCUMENT HEADER"));

        e.add(field(value(d,"party.name"), "party.name", .99, "BILLING", "BILLING / BILL TO"));
        TemplateElement billing = field(value(d,"party.billingAddress"), "party.billingAddress", .98, "BILLING", "BILLING / BILL TO");
        billing.setAutoHeight(true); billing.setGrowthDirection("DOWN"); e.add(billing);
        e.add(field("GSTIN : " + value(d,"party.billingGstin"), "party.billingGstin", .99, "BILLING", "BILLING / BILL TO"));
        e.add(field("Mobile : " + value(d,"party.phone"), "party.phone", .98, "BILLING", "BILLING / BILL TO"));
        e.add(field("Email : " + value(d,"party.email"), "party.email", .99, "BILLING", "BILLING / BILL TO"));

        e.add(field(value(d,"party.name"), "party.name", .98, "DELIVERY", "DELIVERY / SHIP TO"));
        TemplateElement delivery = field(value(d,"party.deliveryAddress"), "party.deliveryAddress", .98, "DELIVERY", "DELIVERY / SHIP TO");
        delivery.setAutoHeight(true); delivery.setGrowthDirection("DOWN"); e.add(delivery);
        e.add(field("GSTIN : " + value(d,"party.deliveryGstin"), "party.deliveryGstin", .99, "DELIVERY", "DELIVERY / SHIP TO"));

        e.add(field("TRANSPORTER : " + value(d,"transport.name"), "transport.name", .98, "TRANSPORT", "TRANSPORT DETAILS"));
        e.add(field("GSTIN : " + value(d,"transport.gstin"), "transport.gstin", .97, "TRANSPORT", "TRANSPORT DETAILS"));
        e.add(field("VEHICLE : " + value(d,"transport.vehicleNumber"), "transport.vehicleNumber", .99, "TRANSPORT", "TRANSPORT DETAILS"));
        e.add(field("CONTACT : " + value(d,"transport.contactPerson") + " / " + value(d,"transport.contact"), "transport.contact", .97, "TRANSPORT", "TRANSPORT DETAILS"));
        if (!value(d,"transport.note").isBlank()) e.add(field("NOTE : " + value(d,"transport.note"), "transport.note", .92, "TRANSPORT", "TRANSPORT DETAILS"));

        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE,0,25,250,540,230);
        table.setSourceStyleCaptured(true);
        table.setUseSourceTableDesign(true);
        table.setTableColumnBindings(List.of(
                binding("SR. NO.","item.serial",.99), binding("HSN CODE","item.hsn",.99),
                binding("PRODUCT DESCRIPTION","item.descriptionWithRemarks",.99), binding("QTY","item.quantity",.99),
                binding("UNIT RATE","item.rate",.99), binding("UNIT","item.unit",.99),
                binding("AMOUNT (INR)","item.taxable",.97)));
        e.add(table);

        TemplateElement financial = TemplateElement.of(ElementType.BLOCK,0,390,620,175,118);
        financial.setReplacementGroupId("DYNAMIC_FINANCIAL_SUMMARY");
        financial.setSummaryLabelRatio(.66);
        financial.setGrowthDirection("UP");
        financial.setAutoDetectedFieldKey("DYNAMIC_FINANCIAL_SUMMARY");
        financial.setAutoDetectedConfidence(.98);
        financial.setMappingState("AUTO");
        e.add(financial);

        e.add(field("BANK NAME : " + value(d,"payment.bankName"), "payment.bankName", .98, "PAYMENT", "BANK / PAYMENT"));
        e.add(field("BRANCH : " + value(d,"payment.branch"), "payment.branch", .98, "PAYMENT", "BANK / PAYMENT"));
        e.add(field("A/c No : " + value(d,"payment.accountNumber"), "payment.accountNumber", .99, "PAYMENT", "BANK / PAYMENT"));
        e.add(field("IFSC : " + value(d,"payment.ifsc"), "payment.ifsc", .99, "PAYMENT", "BANK / PAYMENT"));
        e.add(field("ACCOUNT TYPE : " + value(d,"payment.accountType"), "payment.accountType", .96, "PAYMENT", "BANK / PAYMENT"));
        e.add(field("PAYMENT MODE : " + value(d,"payment.mode"), "payment.mode", .96, "PAYMENT", "BANK / PAYMENT"));

        e.add(field("PAYMENT TERMS : " + value(d,"document.paymentTerms"), "document.paymentTerms", .99, "TERMS_FOOTER", "TERMS & FOOTER"));
        TemplateElement terms = field(value(d,"company.terms"), "company.terms", .98, "TERMS_FOOTER", "TERMS & CONDITIONS");
        terms.setAutoHeight(true); terms.setGrowthDirection("DOWN"); e.add(terms);
        e.add(field("INR : " + value(d,"totals.amountInWordsText"), "totals.amountInWordsText", .99, "TERMS_FOOTER", "TERMS & FOOTER"));
        e.add(field("AUTHORIZED SIGNATORY", "company.signature", .94, "TERMS_FOOTER", "SIGNATURE"));

        t.setElements(e);
        return t;
    }

    private static TemplateElement field(String source, String key, double confidence, String blockType, String blockLabel) {
        TemplateElement e = TemplateElement.of(ElementType.FIELD,0,40,40,210,22);
        e.setFieldKey(key);
        e.setText("{{" + key + "}}");
        e.markAutoDetectedMapping(source,key,confidence);
        org.example.documentstudio.service.ManualTemplateMappingService.configureMultilineMapping(e, key);
        e.setMappingBlockType(blockType);
        e.setMappingBlockLabel(blockLabel);
        e.setMappingBlockId(blockType + "-" + blockLabel);
        return e;
    }

    private static List<PdfMappingReviewSession.DetectedBlock> detectedBlocks(TemplateData d) {
        List<PdfMappingReviewSession.DetectedBlockEntry> values = List.of(
                detected("xyz-ref", "Reference / PO", value(d,"document.poNumber"), "document.poNumber", .93, 0, 430, 115),
                detected("xyz-email", "Customer Email", value(d,"party.email"), "party.email", .97, 0, 430, 137),
                detected("xyz-person", "Contact Person", value(d,"transport.contactPerson"), "transport.contactPerson", .96, 0, 430, 159),
                detected("xyz-mobile", "Contact Mobile", value(d,"transport.contact"), "transport.contact", .96, 0, 430, 181));
        return List.of(new PdfMappingReviewSession.DetectedBlock("XYZ-UAT", "XYZ DETAILS", "GENERIC",
                0, 420, 100, 170, 105, values));
    }

    private static PdfMappingReviewSession.DetectedBlockEntry detected(String id, String label, String sourceValue,
                                                                        String field, double confidence, int page, double x, double y) {
        PdfTextRegion region = new PdfTextRegion(page, sourceValue, x, y, 130, 14, 8);
        return new PdfMappingReviewSession.DetectedBlockEntry(id, label, sourceValue, field, confidence, region,
                "{{" + field + "}}", "UAT|" + id);
    }

    private static Map<String,List<PdfMappingReviewSession.FinancialRoleDetection>> financialRoles(DocumentTemplate template, TemplateData d) {
        TemplateElement financial = template.getElements().stream()
                .filter(e -> e.getType() == ElementType.BLOCK && "DYNAMIC_FINANCIAL_SUMMARY".equals(e.getReplacementGroupId()))
                .findFirst().orElseThrow();
        List<PdfMappingReviewSession.FinancialRoleDetection> roles = new ArrayList<>();
        roles.add(new PdfMappingReviewSession.FinancialRoleDetection("BASIC AMOUNT","Basic / Sub Total","totals.basicAmount",.99));
        roles.add(new PdfMappingReviewSession.FinancialRoleDetection("DISCOUNT","Discount","totals.discountAmount",.99));
        for (TemplateCharge charge : d.charges()) roles.add(new PdfMappingReviewSession.FinancialRoleDetection(
                charge.type(),"Dynamic Charge Rows","charge.* (dynamic rows)",.99));
        roles.add(new PdfMappingReviewSession.FinancialRoleDetection("TAXABLE AMOUNT","Taxable Amount","totals.taxableAmount",.99));
        roles.add(new PdfMappingReviewSession.FinancialRoleDetection("CGST","Automatic CGST Row","tax.cgstLabel / tax.primaryAmount",.99));
        roles.add(new PdfMappingReviewSession.FinancialRoleDetection("SGST","Automatic SGST Row","tax.sgstLabel / tax.secondaryAmount",.99));
        roles.add(new PdfMappingReviewSession.FinancialRoleDetection("IGST","Automatic IGST Row","tax.igstLabel / tax.primaryAmount",.99));
        roles.add(new PdfMappingReviewSession.FinancialRoleDetection("ROUND OFF","Rounding","totals.roundOff",.99));
        roles.add(new PdfMappingReviewSession.FinancialRoleDetection("GRAND TOTAL","Rounded Grand Total","totals.roundedGrandTotal",.99));
        return Map.of(financial.getId(), List.copyOf(roles));
    }

    private static TemplateColumnBinding binding(String label, String key, double confidence) {
        TemplateColumnBinding b = new TemplateColumnBinding(label,key,10,70,"LEFT",confidence);
        b.setAutoDetectedFieldKey(key);
        b.setAutoDetectedConfidence(confidence);
        b.setMappingState("AUTO");
        return b;
    }

    private static String value(TemplateData d, String key) {
        String v = d.value(key);
        return v == null ? "" : v;
    }

    private static String safe(String s) {
        return s.toUpperCase(Locale.ROOT).replace('&','-').replaceAll("[^A-Z0-9]+","-").replaceAll("^-|-$","");
    }

    private static void copyServerBusinessSettings(Path evidence) throws Exception {
        SupportApiClient support = new SupportApiClient();
        Map<String,String> values = new LinkedHashMap<>();
        for (String key : List.of(
                "company.name","company.address","company.shipAddress","company.gstin","company.phone","company.email","company.website","company.terms",
                "company.logoPath","company.signaturePath","payment.bankName","payment.branch","payment.accountNumber","payment.ifsc","payment.accountType",
                "payment.mode","payment.accountHolder","payment.upiId")) {
            values.put(key, support.setting(key, ""));
        }
        for (var entry : values.entrySet()) {
            if (!entry.getKey().endsWith("Path")) ConfigManager.setWithoutSaving(entry.getKey(), entry.getValue());
        }
        for (String assetKey : List.of("company.logoPath","company.signaturePath")) {
            String stored = values.getOrDefault(assetKey, "");
            if (!stored.startsWith("server-resource:")) continue;
            try {
                byte[] bytes = new ServerResourceClient().get("BUSINESS_ASSET", assetKey);
                if (bytes.length == 0) continue;
                Path local = evidence.resolve("workspace").resolve(assetKey.replace('.','-') + ".png");
                Files.createDirectories(local.getParent());
                Files.write(local,bytes);
                ConfigManager.setWithoutSaving(assetKey,local.toString());
            } catch (Exception ignored) {
                ConfigManager.setWithoutSaving(assetKey,"");
            }
        }
        ConfigManager.save();
    }
}
