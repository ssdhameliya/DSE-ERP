package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateCategory;
import org.example.documentstudio.model.TemplateElement;
import org.example.documentstudio.model.TemplateStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Installs the approved fixed Jasvi Sales Invoice template once for a fresh workspace. */
final class BuiltInPdfTemplateInstaller {
    static final String SALES_TEMPLATE_ID = "builtin-sales-invoice-jasvi-9-0-60";
    private static final String RESOURCE = "/documentstudio/defaults/sales-invoice-jasvi.pdf";
    private static final int RELEASE_VERSION = 3;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private BuiltInPdfTemplateInstaller() { }

    static void ensureInstalled(Path root) {
        try {
            Path folder = root.resolve(SALES_TEMPLATE_ID);
            if (Files.isDirectory(folder)) {
                upgradeBuiltInIfNeeded(folder);
                return; // Never reactivate/demote user choices after first install.
            }
            demoteExistingSalesDefaults(root);

            DocumentTemplate working = template(TemplateStatus.ACTIVE);
            Path published = folder.resolve("published");
            Path active = folder.resolve("active");
            Files.createDirectories(folder.resolve("assets"));
            Files.createDirectories(folder.resolve("history"));
            Files.createDirectories(published.resolve("assets"));
            Files.createDirectories(active.resolve("assets"));

            try (InputStream in = BuiltInPdfTemplateInstaller.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IOException("Built-in Sales Invoice PDF resource is missing.");
                Files.copy(in, folder.resolve("source.pdf"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.copy(folder.resolve("source.pdf"), folder.resolve("original.pdf"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(folder.resolve("source.pdf"), published.resolve("source.pdf"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(folder.resolve("original.pdf"), published.resolve("original.pdf"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(folder.resolve("source.pdf"), active.resolve("source.pdf"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(folder.resolve("original.pdf"), active.resolve("original.pdf"), StandardCopyOption.REPLACE_EXISTING);

            JSON.writeValue(folder.resolve("template.json").toFile(), working);
            DocumentTemplate pub = template(TemplateStatus.PUBLISHED);
            pub.setDefaultTemplate(false); pub.setRuntimeEnabled(false);
            JSON.writeValue(published.resolve("template.json").toFile(), pub);
            JSON.writeValue(active.resolve("template.json").toFile(), working);
            // In shared-client mode publish the same approved built-in template to company storage.
            PdfStudioRemoteStore.publish(SALES_TEMPLATE_ID, folder);
        } catch (Exception error) {
            System.err.println("[PdfStudio] built-in Sales template install skipped: " + error.getMessage());
        }
    }

    /**
     * Upgrade only our own built-in template in place. This preserves the user's
     * active/default decision and avoids demoting any custom Sales PDF Studio
     * template, while still delivering corrected field coordinates to existing
     * older workspaces that installed a prior built-in mapping revision.
     */
    private static void upgradeBuiltInIfNeeded(Path folder) throws IOException {
        Path meta = folder.resolve("template.json");
        if (!Files.isRegularFile(meta)) return;
        DocumentTemplate current = JSON.readValue(meta.toFile(), DocumentTemplate.class);
        if (!SALES_TEMPLATE_ID.equals(current.getId()) || current.getVersion() >= RELEASE_VERSION) return;

        applyReleaseMapping(current);
        JSON.writeValue(meta.toFile(), current);
        upgradeSnapshot(folder.resolve("published").resolve("template.json"));
        upgradeSnapshot(folder.resolve("active").resolve("template.json"));
        PdfStudioRemoteStore.publish(SALES_TEMPLATE_ID, folder);
    }

    private static void upgradeSnapshot(Path meta) throws IOException {
        if (!Files.isRegularFile(meta)) return;
        DocumentTemplate snapshot = JSON.readValue(meta.toFile(), DocumentTemplate.class);
        if (!SALES_TEMPLATE_ID.equals(snapshot.getId())) return;
        applyReleaseMapping(snapshot);
        JSON.writeValue(meta.toFile(), snapshot);
    }

    private static void applyReleaseMapping(DocumentTemplate t) {
        t.setStudioSchemaVersion(4);
        t.setDataContractVersion(2);
        t.setLayoutMode("STRICT_FIXED");
        t.setVersion(RELEASE_VERSION);
        if (t.getPublishedVersion() > 0) t.setPublishedVersion(RELEASE_VERSION);
        if (t.getActiveVersion() > 0) t.setActiveVersion(RELEASE_VERSION);
        t.setElements(elements());
        t.touch();
    }

    private static void demoteExistingSalesDefaults(Path root) throws IOException {
        try (Stream<Path> folders = Files.list(root)) {
            for (Path folder : folders.filter(Files::isDirectory).toList()) {
                Path meta = folder.resolve("template.json");
                if (!Files.isRegularFile(meta)) continue;
                try {
                    DocumentTemplate t = JSON.readValue(meta.toFile(), DocumentTemplate.class);
                    if (t.getDocumentType() != DocumentType.SALES_INVOICE || (!t.isDefaultTemplate() && !t.isRuntimeEnabled())) continue;
                    t.setDefaultTemplate(false);
                    t.setRuntimeEnabled(false);
                    t.setActiveVersion(0);
                    t.setActivatedAt(null);
                    if (t.getPublishedVersion() > 0) t.setStatus(TemplateStatus.PUBLISHED);
                    JSON.writeValue(meta.toFile(), t);
                    PdfStudioRemoteStore.publish(folder.getFileName().toString(), folder);
                } catch (Exception error) {
                    System.err.println("[PdfStudio] existing Sales default could not be demoted: " + folder.getFileName() + " - " + error.getMessage());
                }
            }
        }
    }

    private static DocumentTemplate template(TemplateStatus status) {
        DocumentTemplate t = new DocumentTemplate();
        t.setId(SALES_TEMPLATE_ID);
        t.setName("Jasvi Tax Invoice – Fixed JSON Mapping");
        t.setDocumentType(DocumentType.SALES_INVOICE);
        t.setCategory(TemplateCategory.ERP_TEMPLATE);
        t.setStudioSchemaVersion(4);
        t.setDataContractVersion(2);
        t.setLayoutMode("STRICT_FIXED");
        t.setVersion(RELEASE_VERSION);
        t.setStatus(status);
        t.setDefaultTemplate(status == TemplateStatus.ACTIVE);
        t.setRuntimeEnabled(status == TemplateStatus.ACTIVE);
        t.setUnpublishedChanges(false);
        t.setPublishedVersion(RELEASE_VERSION);
        t.setActiveVersion(status == TemplateStatus.ACTIVE ? RELEASE_VERSION : 0);
        String now = Instant.now().toString();
        t.setPublishedAt(now);
        t.setActivatedAt(status == TemplateStatus.ACTIVE ? now : null);
        t.setSourceFile("source.pdf");
        t.setElements(elements());
        return t;
    }

    private static List<TemplateElement> elements() {
        List<TemplateElement> e = new ArrayList<>();
        String pale = "#EDF3FA";
        String white = "#FFFFFF";
        String green = "#DFF4E3";

        // Header / reference values - labels and artwork remain in the original PDF.
        pair(e, "document.number", 123.5, 142.8, 120, 10, 7, false, "LEFT", pale, "EVERY");
        pair(e, "document.poNumber", 123.5, 155.4, 145, 10, 7, false, "LEFT", pale, "EVERY");
        pair(e, "document.date", 402.5, 142.8, 125, 10, 7, false, "LEFT", pale, "EVERY");
        pair(e, "document.poDate", 402.5, 155.4, 125, 10, 7, false, "LEFT", pale, "EVERY");

        // Billing and delivery blocks.
        pair(e, "party.name", 28.5, 190.6, 245, 11, 8, true, "LEFT", pale, "EVERY");
        pair(e, "party.billingAddress", 28.5, 204.2, 248, 20, 6.8, false, "LEFT", pale, "EVERY");
        // 9.0.61: align mapped GSTIN baseline exactly with the source PDF's GST-IN label/value row.
        pair(e, "party.billingGstin", 58.6688, 224.05, 173.3, 10, 6.8, true, "LEFT", pale, "EVERY");
        pair(e, "party.name", 307.5, 190.6, 245, 11, 8, true, "LEFT", pale, "EVERY");
        pair(e, "party.deliveryAddress", 307.5, 204.2, 248, 20, 6.8, false, "LEFT", pale, "EVERY");
        pair(e, "party.deliveryGstin", 337.6388, 224.05, 173.4, 10, 6.8, true, "LEFT", pale, "EVERY");

        // Transport strip.
        pair(e, "transport.name", 114.8, 238.8, 77.2, 9, 6.45, false, "LEFT", pale, "EVERY");
        pair(e, "transport.gstin", 244.1, 238.8, 102.9, 9, 6.45, false, "LEFT", pale, "EVERY");
        pair(e, "transport.contact", 460.1, 238.8, 101.9, 9, 6.45, false, "LEFT", pale, "EVERY");

        // Remove the sample row while leaving the original blue grid untouched.
        double[] xs = {24.23, 58.20, 106.18, 401.85, 431.84, 479.82, 510.81, 570.77};
        for (int i = 0; i < xs.length - 1; i++) e.add(mask(xs[i] + .8, 270.9, xs[i+1] - xs[i] - 1.6, 11.7, white, "EVERY"));
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE, 0, 24.23, 253.04, 546.54, 362.35);
        table.setUseSourceTableDesign(true);
        table.setHeaderHeight(17.20);
        table.setRowHeight(18.45);
        table.setFontSize(6.45);
        table.setTextColor("#000000");
        table.setTableColumns(List.of("serial", "hsn", "description", "quantity", "rate", "unit", "total"));
        table.setTableColumnWidths(List.of(33.97, 47.98, 295.67, 29.99, 47.98, 30.99, 59.96));
        table.setTableColumnAlignments(List.of("CENTER", "CENTER", "LEFT", "CENTER", "RIGHT", "CENTER", "RIGHT"));
        table.setFillEnabled(false); table.setStrokeEnabled(false);
        e.add(table);

        // Company/bank/payment values.
        pair(e, "company.gstin", 137, 624.8, 120, 9, 6.45, true, "LEFT", white, "LAST");
        pair(e, "payment.bankName", 137, 636.5, 180, 9, 6.45, false, "LEFT", white, "LAST");
        pair(e, "payment.branch", 137, 648.2, 150, 9, 6.45, false, "LEFT", white, "LAST");
        pair(e, "payment.accountNumber", 137, 659.9, 150, 9, 6.45, false, "LEFT", white, "LAST");
        pair(e, "payment.ifsc", 137, 671.6, 150, 9, 6.45, false, "LEFT", white, "LAST");
        pair(e, "document.paymentTerms", 136.2, 683.3, 130.8, 9, 6.45, true, "LEFT", white, "LAST");

        // Totals; preserve the exact labels/boxes but make amounts and tax labels live.
        pair(e, "totals.basicAmount", 500, 623.8, 67, 9, 6.45, true, "RIGHT", white, "LAST");
        pair(e, "totals.freight", 500, 635.8, 67, 9, 6.45, false, "RIGHT", white, "LAST");
        pair(e, "totals.taxableAmount", 500, 647.8, 67, 9, 6.45, true, "RIGHT", white, "LAST");
        pair(e, "tax.primaryLabel", 394, 659.8, 78, 9, 6.45, false, "LEFT", white, "LAST");
        pair(e, "tax.primaryAmount", 500, 659.8, 67, 9, 6.45, false, "RIGHT", white, "LAST");
        pair(e, "tax.secondaryLabel", 394, 671.8, 78, 9, 6.45, false, "LEFT", white, "LAST");
        pair(e, "tax.secondaryAmount", 500, 671.8, 67, 9, 6.45, false, "RIGHT", white, "LAST");
        pair(e, "totals.roundOff", 500, 683.8, 67, 9, 6.45, false, "RIGHT", white, "LAST");
        pair(e, "totals.amountInWordsText", 67.7, 700.7, 291.3, 11, 6.9, false, "LEFT", green, "LAST");
        pair(e, "totals.roundedGrandTotal", 493, 700.7, 73, 11, 8.1, true, "RIGHT", green, "LAST");
        return e;
    }

    private static void pair(List<TemplateElement> list, String key, double x, double y, double w, double h,
                             double font, boolean bold, String align, String background, String pageRule) {
        list.add(mask(x - 1, y - 1, w + 2, h + 2, background, "EVERY"));
        TemplateElement f = TemplateElement.of(ElementType.FIELD, 0, x, y, w, h);
        f.setFieldKey(key); f.setText(""); f.setFontSize(font); f.setBold(bold); f.setTextAlignment(align);
        f.setTextColor("#000000"); f.setFillEnabled(false); f.setStrokeEnabled(false); f.setTextFit("SHRINK"); f.setPageRule(pageRule);
        list.add(f);
    }

    private static TemplateElement mask(double x, double y, double w, double h, String color, String pageRule) {
        TemplateElement m = TemplateElement.of(ElementType.WHITEOUT, 0, x, y, w, h);
        m.setFillColor(color); m.setStrokeColor(color); m.setStrokeWidth(0); m.setLocked(true); m.setPageRule(pageRule);
        return m;
    }
}
