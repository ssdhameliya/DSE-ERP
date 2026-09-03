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
    private static final int RELEASE_VERSION = 5;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private BuiltInPdfTemplateInstaller() { }

    private static final String SALES_DELETION_MARKER = ".builtin-sales-invoice-deleted";

    static void ensureInstalled(Path root) {
        try {
            if (isIntentionallyDeleted(root)) {
                removeLocalBuiltIn(root);
                return;
            }
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


    static void markIntentionallyDeleted(Path root) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve(SALES_DELETION_MARKER), "deleted=" + Instant.now() + System.lineSeparator());
    }

    static boolean isIntentionallyDeleted(Path root) {
        return root != null && Files.isRegularFile(root.resolve(SALES_DELETION_MARKER));
    }

    static void enforceIntentionalDeletion(Path root) {
        if (!isIntentionallyDeleted(root)) return;
        try { removeLocalBuiltIn(root); }
        catch (Exception error) {
            System.err.println("[PdfStudio] deleted built-in Sales template cleanup skipped: " + error.getMessage());
        }
    }

    private static void removeLocalBuiltIn(Path root) throws IOException {
        Path folder = root.resolve(SALES_TEMPLATE_ID);
        if (!Files.exists(folder)) return;
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
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
        // Reassert the lower structural rule after all address text replacement. PDF source
        // strokes are antialiased, so even an inset whiteout can nick the visible edge.
        // Drawing the rule last makes the template robust to future field-size changes.
        gridLine(e, 24.4, 234.1, 287.0, 234.1, "EVERY");
        gridLine(e, 302.4, 234.1, 568.6, 234.1, "EVERY");

        // Transport strip: rebuild all four Standard Sales segments so the added Vehicle
        // field does not obscure the Contact Details label in the source artwork.
        // Inset transport replacement so its top/bottom blue rules survive.
        e.add(mask(25.8, 237.5, 542.4, 9.4, pale, "EVERY"));
        literal(e, "TRANSPORTER :", 39.0, 238.8, 74.0, 8.0, 5.5, true, "RIGHT", "EVERY");
        field(e, "transport.name", 115.0, 238.8, 78.0, 8.0, 5.5, false, "LEFT", "SHRINK", 1.0, "EVERY");
        literal(e, "GSTIN :", 197.0, 238.8, 43.0, 8.0, 5.5, true, "RIGHT", "EVERY");
        field(e, "transport.gstin", 242.0, 238.8, 96.0, 8.0, 5.5, false, "LEFT", "SHRINK", 1.0, "EVERY");
        literal(e, "VEHICLE :", 341.0, 238.8, 47.0, 8.0, 5.5, true, "RIGHT", "EVERY");
        field(e, "transport.vehicleNumber", 390.0, 238.8, 66.0, 8.0, 5.3, false, "LEFT", "SHRINK", 1.0, "EVERY");
        literal(e, "CONTACT DETAILS :", 458.0, 238.8, 70.0, 8.0, 5.2, true, "RIGHT", "EVERY");
        field(e, "transport.contact", 530.0, 238.8, 37.0, 8.0, 4.9, false, "RIGHT", "SHRINK", 1.0, "EVERY");
        // Repaint the transport strip rules after the content masks. This is intentionally
        // last so no whiteout can cut the blue rules, regardless of source-PDF antialiasing.
        gridLine(e, 24.4, 237.2, 568.6, 237.2, "EVERY");
        gridLine(e, 24.4, 247.1, 568.6, 247.1, "EVERY");

        // Remove the sample row while leaving the original blue grid untouched.
        double[] xs = {24.23, 58.20, 106.18, 401.85, 431.84, 479.82, 510.81, 570.77};
        for (int i = 0; i < xs.length - 1; i++) e.add(mask(xs[i] + .8, 270.9, xs[i+1] - xs[i] - 1.6, 11.7, white, "EVERY"));
        // On intermediate pages erase the copied source closing stack BEFORE the item table
        // is rendered, so expanded real rows are not subsequently wiped out.
        e.add(mask(24.0, 614.5, 547.0, 192.5, white, "INTERMEDIATE"));
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE, 0, 24.23, 253.04, 546.54, 362.35);
        table.setUseSourceTableDesign(true);
        table.setHeaderHeight(17.20);
        table.setRowHeight(18.10);
        table.setFontSize(6.45);
        table.setTextColor("#000000");
        table.setTableColumns(List.of("serial", "hsn", "remarks", "quantity", "rate", "unit", "grossAmount"));
        table.setTableColumnWidths(List.of(33.97, 47.98, 295.67, 29.99, 47.98, 30.99, 59.96));
        table.setTableColumnAlignments(List.of("CENTER", "CENTER", "LEFT", "CENTER", "RIGHT", "CENTER", "RIGHT"));
        table.setFillEnabled(false); table.setStrokeEnabled(false);
        e.add(table);

        // Multi-page continuation pages must match the legacy Sales generator: the closing
        // Bank/Calculation/Terms/Signature stack belongs to the final page only.  The
        // uploaded source PDF contains that artwork on its single source page, so copied
        // continuation pages need one protected whiteout over the complete closing area.
        // The renderer rebuilds the complete multi-page item body with the SAME expanded
        // row height on intermediate and final pages. No fixed 18.10 continuation grid here.

        // Final-page bank/payment block.  Rebuild the inner rows from live ERP settings so
        // Account Type and Payment Mode are present just like the Standard Sales PDF.
        e.add(mask(28.2, 620.2, 344.1, 72.6, white, "LAST"));
        // Reassert the Standard Sales bank/payment card perimeter after replacing source text.
        gridLine(e, 27.0, 619.0, 373.5, 619.0, "LAST");
        gridLine(e, 27.0, 694.0, 373.5, 694.0, "LAST");
        gridLine(e, 27.0, 619.0, 27.0, 694.0, "LAST");
        gridLine(e, 373.5, 619.0, 373.5, 694.0, "LAST");
        String[] bankLabels = {"Supplier GST NO", "BANK NAME", "BRANCH", "A/c NO", "IFSC CODE", "ACCOUNT TYPE", "PAYMENT MODE", "PAYMENT TERMS"};
        String[] bankKeys = {"company.gstin", "payment.bankName", "payment.branch", "payment.accountNumber", "payment.ifsc", "payment.accountType", "payment.mode", "document.paymentTerms"};
        for (int i = 0; i < bankLabels.length; i++) {
            double y = 622.0 + i * 8.7;
            literal(e, bankLabels[i], 32.0, y, 97.0, 8.2, 5.8, true, "LEFT", "LAST");
            literal(e, ":", 132.0, y, 5.0, 8.2, 5.8, true, "LEFT", "LAST");
            field(e, bankKeys[i], 140.0, y, 220.0, 8.2, 5.8, i == 0 || i == 7, "LEFT", "SHRINK", 1.0, "LAST");
        }

        // Standard Sales uses one row per discount/charge/tax entry.  Render two aligned
        // multi-line fields inside the existing fixed totals box: zero charges add no row,
        // while one or many charges remain individually visible without moving the box.
        e.add(mask(385.1, 620.1, 182.3, 72.8, white, "LAST"));
        // Rebuild the complete calculation card geometry. The 9.0.72 whole-card whiteout
        // removed these inner separators even though the numeric data itself was correct.
        gridLine(e, 384.0, 619.0, 568.5, 619.0, "LAST");
        gridLine(e, 384.0, 694.0, 568.5, 694.0, "LAST");
        gridLine(e, 384.0, 619.0, 384.0, 694.0, "LAST");
        gridLine(e, 568.5, 619.0, 568.5, 694.0, "LAST");
        for (int i = 1; i < 8; i++) {
            double yy = 619.0 + i * (75.0 / 8.0);
            gridLine(e, 384.0, yy, 568.5, yy, "LAST");
        }
        multiField(e, "totals.breakdownLabels", 389.0, 621.0, 109.0, 72.0, 5.7, true, "LEFT", 1.42, "LAST");
        multiField(e, "totals.breakdownAmounts", 500.0, 621.0, 65.0, 72.0, 5.7, false, "RIGHT", 1.42, "LAST");

        pair(e, "totals.amountInWordsText", 67.7, 700.7, 291.3, 11, 6.9, false, "LEFT", green, "LAST");
        pair(e, "totals.roundedGrandTotal", 493, 700.7, 73, 11, 8.1, true, "RIGHT", green, "LAST");

        // Terms and signature are live sandbox/company settings rather than sample PDF values.
        e.add(mask(29.0, 731.0, 338.0, 65.0, white, "LAST"));
        multiField(e, "company.terms", 31.0, 733.0, 334.0, 61.0, 6.0, false, "LEFT", 1.20, "LAST");
        e.add(mask(397.0, 735.0, 168.0, 72.0, white, "LAST"));
        imageField(e, "company.signature", 405.0, 741.0, 152.0, 46.0, "LAST");
        literal(e, "AUTHORIZED SIGNATORY", 420.0, 793.0, 130.0, 7.0, 5.3, true, "CENTER", "LAST");

        // Page numbering is needed only for a true multi-page invoice, matching Standard Sales.
        literal(e, "Page {{document.pageNumber}} of {{document.totalPages}}", 515.0, 824.0, 50.0, 7.0, 4.8, false, "RIGHT", "MULTI");
        return e;
    }

    private static void pair(List<TemplateElement> list, String key, double x, double y, double w, double h,
                             double font, boolean bold, String align, String background, String pageRule) {
        // Keep replacement paint inside the content box. Expanding the whiteout by one point
        // erased adjacent blue structural strokes (delivery/transport/calculation borders).
        double inset = 0.65;
        list.add(mask(x + inset, y + inset, Math.max(1, w - inset * 2), Math.max(1, h - inset * 2), background, pageRule));
        TemplateElement f = TemplateElement.of(ElementType.FIELD, 0, x, y, w, h);
        f.setFieldKey(key); f.setText(""); f.setFontSize(font); f.setBold(bold); f.setTextAlignment(align);
        f.setTextColor("#000000"); f.setFillEnabled(false); f.setStrokeEnabled(false); f.setTextFit("SHRINK"); f.setPageRule(pageRule);
        list.add(f);
    }

    private static void literal(List<TemplateElement> list, String text, double x, double y, double w, double h,
                                double font, boolean bold, String align, String pageRule) {
        TemplateElement t = TemplateElement.of(ElementType.TEXT, 0, x, y, w, h);
        t.setText(text); t.setFontSize(font); t.setBold(bold); t.setTextAlignment(align); t.setTextColor("#000000");
        t.setFillEnabled(false); t.setStrokeEnabled(false); t.setTextFit("SHRINK"); t.setPageRule(pageRule);
        list.add(t);
    }

    private static void field(List<TemplateElement> list, String key, double x, double y, double w, double h,
                              double font, boolean bold, String align, String fit, double spacing, String pageRule) {
        TemplateElement f = TemplateElement.of(ElementType.FIELD, 0, x, y, w, h);
        f.setFieldKey(key); f.setFontSize(font); f.setBold(bold); f.setTextAlignment(align); f.setTextColor("#000000");
        f.setFillEnabled(false); f.setStrokeEnabled(false); f.setTextFit(fit); f.setLineSpacing(spacing); f.setPageRule(pageRule);
        list.add(f);
    }

    private static void multiField(List<TemplateElement> list, String key, double x, double y, double w, double h,
                                   double font, boolean bold, String align, double spacing, String pageRule) {
        field(list, key, x, y, w, h, font, bold, align, "WRAP", spacing, pageRule);
    }

    private static void gridLine(List<TemplateElement> list, double x1, double y1, double x2, double y2, String pageRule) {
        TemplateElement line = TemplateElement.of(ElementType.LINE, 0, x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
        line.setStrokeColor("#7FA4D3"); line.setStrokeWidth(0.45); line.setStrokeEnabled(true); line.setPageRule(pageRule);
        list.add(line);
    }

    private static void imageField(List<TemplateElement> list, String key, double x, double y, double w, double h, String pageRule) {
        TemplateElement image = TemplateElement.of(ElementType.IMAGE_FIELD, 0, x, y, w, h);
        image.setFieldKey(key); image.setImageFit("FIT"); image.setPreserveAspectRatio(true); image.setPageRule(pageRule);
        list.add(image);
    }

    private static TemplateElement mask(double x, double y, double w, double h, String color, String pageRule) {
        TemplateElement m = TemplateElement.of(ElementType.WHITEOUT, 0, x, y, w, h);
        m.setFillColor(color); m.setStrokeColor(color); m.setStrokeWidth(0); m.setLocked(true); m.setPageRule(pageRule);
        return m;
    }
}
