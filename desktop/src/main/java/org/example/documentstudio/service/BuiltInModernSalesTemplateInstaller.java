package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.documentstudio.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Adds the user-supplied DS Engineers Sales Invoice as a certified secondary PDF Studio template. */
final class BuiltInModernSalesTemplateInstaller {
    static final String TEMPLATE_ID = "starter-sales-invoice-modern-sal";
    static final String DYNAMIC_FINANCIAL_SUMMARY = "DYNAMIC_FINANCIAL_SUMMARY";
    private static final String RESOURCE = "/documentstudio/defaults/sales-invoice-modern.pdf";
    private static final int RELEASE_VERSION = 3;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final String DELETION_MARKER = ".starter-sales-invoice-modern-deleted";

    private BuiltInModernSalesTemplateInstaller() { }

    static void ensureInstalled(Path root) {
        try {
            if (isIntentionallyDeleted(root)) {
                removeLocalStarter(root);
                return;
            }
            Path folder = root.resolve(TEMPLATE_ID);
            if (Files.isDirectory(folder)) {
                upgradeBuiltInIfNeeded(folder);
                return;
            }
            Files.createDirectories(folder.resolve("assets"));
            Files.createDirectories(folder.resolve("history"));
            Files.createDirectories(folder.resolve("published").resolve("assets"));
            installProtectedSource(folder);

            DocumentTemplate working = template(TemplateStatus.PUBLISHED);
            JSON.writeValue(folder.resolve("template.json").toFile(), working);
            Files.copy(folder.resolve("source.pdf"), folder.resolve("published").resolve("source.pdf"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(folder.resolve("original.pdf"), folder.resolve("published").resolve("original.pdf"), StandardCopyOption.REPLACE_EXISTING);
            DocumentTemplate published = template(TemplateStatus.PUBLISHED);
            published.setDefaultTemplate(false);
            published.setRuntimeEnabled(false);
            JSON.writeValue(folder.resolve("published").resolve("template.json").toFile(), published);
            PdfStudioRemoteStore.publish(TEMPLATE_ID, folder);
        } catch (Exception error) {
            System.err.println("[PdfStudio] modern Sales starter install skipped: " + error.getMessage());
        }
    }

    static void markIntentionallyDeleted(Path root) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve(DELETION_MARKER), "deleted=" + Instant.now() + System.lineSeparator());
    }

    static boolean isIntentionallyDeleted(Path root) {
        return root != null && Files.isRegularFile(root.resolve(DELETION_MARKER));
    }

    static void enforceIntentionalDeletion(Path root) {
        if (!isIntentionallyDeleted(root)) return;
        try { removeLocalStarter(root); }
        catch (Exception error) {
            System.err.println("[PdfStudio] deleted modern Sales starter cleanup skipped: " + error.getMessage());
        }
    }

    private static void removeLocalStarter(Path root) throws IOException {
        Path folder = root.resolve(TEMPLATE_ID);
        if (!Files.exists(folder)) return;
        try (var walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void upgradeBuiltInIfNeeded(Path folder) throws IOException {
        Path meta = folder.resolve("template.json");
        if (!Files.isRegularFile(meta)) return;
        DocumentTemplate current = JSON.readValue(meta.toFile(), DocumentTemplate.class);
        if (!TEMPLATE_ID.equals(current.getId()) || current.getVersion() >= RELEASE_VERSION) return;
        boolean defaultTemplate = current.isDefaultTemplate();
        boolean runtimeEnabled = current.isRuntimeEnabled();
        TemplateStatus status = current.getStatus();
        int activeVersion = current.getActiveVersion();

        applyReleaseMapping(current);
        current.setDefaultTemplate(defaultTemplate);
        current.setRuntimeEnabled(runtimeEnabled);
        current.setStatus(status == null ? TemplateStatus.PUBLISHED : status);
        current.setActiveVersion(runtimeEnabled ? RELEASE_VERSION : activeVersion);
        installProtectedSource(folder);
        JSON.writeValue(meta.toFile(), current);
        upgradeSnapshot(folder.resolve("published").resolve("template.json"), folder.resolve("published"));
        upgradeSnapshot(folder.resolve("active").resolve("template.json"), folder.resolve("active"));
        PdfStudioRemoteStore.publish(TEMPLATE_ID, folder);
    }

    private static void upgradeSnapshot(Path meta, Path snapshotFolder) throws IOException {
        if (!Files.isRegularFile(meta)) return;
        DocumentTemplate snapshot = JSON.readValue(meta.toFile(), DocumentTemplate.class);
        if (!TEMPLATE_ID.equals(snapshot.getId())) return;
        boolean isActive = snapshot.isRuntimeEnabled() || snapshot.getStatus() == TemplateStatus.ACTIVE;
        applyReleaseMapping(snapshot);
        snapshot.setRuntimeEnabled(isActive);
        snapshot.setDefaultTemplate(isActive);
        snapshot.setStatus(isActive ? TemplateStatus.ACTIVE : TemplateStatus.PUBLISHED);
        snapshot.setActiveVersion(isActive ? RELEASE_VERSION : 0);
        JSON.writeValue(meta.toFile(), snapshot);
        if (Files.isDirectory(snapshotFolder)) {
            Files.copy(snapshotFolder.getParent().resolve("source.pdf"), snapshotFolder.resolve("source.pdf"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(snapshotFolder.getParent().resolve("original.pdf"), snapshotFolder.resolve("original.pdf"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * The uploaded AcroForm is retained byte-for-byte as original.pdf.  Studio renders from a
     * private normalized source copy so field widgets cannot leak stale sample values while the
     * user's artwork remains the immutable background.
     */
    private static void installProtectedSource(Path folder) throws IOException {
        Path original = folder.resolve("original.pdf");
        try (InputStream in = BuiltInModernSalesTemplateInstaller.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("Modern Sales starter PDF resource is missing.");
            Files.copy(in, original, StandardCopyOption.REPLACE_EXISTING);
        }
        PdfImportSecurityService.normalizeForEditing(original, folder.resolve("source.pdf"), "");
    }

    private static DocumentTemplate template(TemplateStatus status) {
        DocumentTemplate t = new DocumentTemplate();
        t.setId(TEMPLATE_ID);
        t.setName("DS Engineers Sales Invoice – Dynamic Secondary");
        t.setDocumentType(DocumentType.SALES_INVOICE);
        t.setCategory(TemplateCategory.ERP_TEMPLATE);
        t.setStudioSchemaVersion(4);
        t.setDataContractVersion(2);
        t.setLayoutMode("FLOW_FIXED");
        t.setVersion(RELEASE_VERSION);
        t.setStatus(status);
        t.setDefaultTemplate(status == TemplateStatus.ACTIVE);
        t.setRuntimeEnabled(status == TemplateStatus.ACTIVE);
        t.setUnpublishedChanges(false);
        t.setPublishedVersion(RELEASE_VERSION);
        t.setActiveVersion(status == TemplateStatus.ACTIVE ? RELEASE_VERSION : 0);
        t.setSourceFile("source.pdf");
        t.setCreatedAt(Instant.now().toString());
        t.setUpdatedAt(Instant.now().toString());
        t.setElements(elements());
        return t;
    }

    private static void applyReleaseMapping(DocumentTemplate t) {
        t.setName("DS Engineers Sales Invoice – Dynamic Secondary");
        t.setStudioSchemaVersion(4);
        t.setDataContractVersion(2);
        t.setLayoutMode("FLOW_FIXED");
        t.setVersion(RELEASE_VERSION);
        if (t.getPublishedVersion() > 0) t.setPublishedVersion(RELEASE_VERSION);
        if (t.getActiveVersion() > 0) t.setActiveVersion(RELEASE_VERSION);
        t.setElements(elements());
        t.touch();
    }

    private static List<TemplateElement> elements() {
        List<TemplateElement> e = new ArrayList<>();
        String white = "#FFFFFF";

        // Company / invoice header. The supplied artwork is protected; only live value areas are replaced.
        pair(e,"company.name",50,73,270,12,6.3,true,"LEFT",white,"EVERY");
        pair(e,"company.address",50,84,270,12,5.8,false,"LEFT",white,"EVERY");
        pair(e,"company.phone",50,94,90,10,5.8,false,"LEFT",white,"EVERY");
        pair(e,"company.email",50,103,150,11,5.8,false,"LEFT",white,"EVERY");
        pair(e,"company.gstin",53,122,120,11,6.0,true,"LEFT",white,"EVERY");
        pair(e,"document.number",525,48,54,16,7.0,true,"RIGHT","#0E3F79","EVERY");
        pair(e,"document.date",514,69,62,12,5.8,false,"LEFT",white,"EVERY");
        pair(e,"document.dueDate",514,79,62,12,5.8,false,"LEFT",white,"EVERY");
        pair(e,"document.poNumber",514,89,66,12,5.8,false,"LEFT",white,"EVERY");
        pair(e,"document.referenceNumber",514,99,66,12,5.8,false,"LEFT",white,"EVERY");
        pair(e,"sales.salesperson",514,109,66,12,5.8,false,"LEFT",white,"EVERY");
        maskedLiteral(e,"INR",514,119,66,12,5.8,false,"LEFT",white,"EVERY");

        // Bill To / Ship To.
        pair(e,"party.name",48,163,132,14,6.2,true,"LEFT",white,"EVERY");
        whiteout(e,48.5,176.5,136,21,white,"EVERY");
        multi(e,"party.billingAddress",48,176,137,21,5.0,false,"LEFT",1.05,"EVERY");
        pair(e,"party.phone",65,187,85,12,5.4,false,"LEFT",white,"EVERY");
        pair(e,"party.email",65,197,120,12,5.1,false,"LEFT",white,"EVERY");
        pair(e,"party.billingGstin",72,207,113,12,5.5,true,"LEFT",white,"EVERY");
        pair(e,"party.name",238,163,132,14,6.2,true,"LEFT",white,"EVERY");
        whiteout(e,238.5,176.5,136,21,white,"EVERY");
        multi(e,"party.deliveryAddress",238,176,137,21,5.0,false,"LEFT",1.05,"EVERY");
        pair(e,"party.phone",255,187,85,12,5.4,false,"LEFT",white,"EVERY");
        pair(e,"party.email",255,197,120,12,5.1,false,"LEFT",white,"EVERY");
        pair(e,"party.deliveryGstin",262,207,113,12,5.5,true,"LEFT",white,"EVERY");

        // Tax/GST details. ERP data replaces the sample values while source labels/artwork stay untouched.
        pair(e,"party.gstin",478,162,97,12,5.8,false,"LEFT",white,"EVERY");
        whiteout(e,478.5,177.5,96,23,white,"EVERY");
        field(e,"party.stateCode",478,177,97,14,5.6,false,"LEFT","EVERY");
        pair(e,"party.placeOfSupply",478,203,97,23,5.3,false,"LEFT",white,"EVERY");
        maskedLiteral(e,"No",478,228,52,13,5.8,false,"LEFT",white,"EVERY");
        pair(e,"document.referenceNumber",478,243,97,13,5.8,false,"LEFT",white,"EVERY");

        // Repeating item body. Generic FLOW_FIXED pagination owns only the item-table zone:
        // intermediate pages contain real rows only; the final/only page pads unused slots with
        // blank rows while all fixed closing blocks remain at their mapped LAST-only coordinates.
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE,0,13.0,261.0,568.0,65.0);
        table.setHeaderHeight(22.0); table.setRowHeight(8.0); table.setFontSize(5.8);
        table.setTextColor("#000000"); table.setUseSourceTableDesign(true); table.setFillEnabled(false); table.setStrokeEnabled(false);
        table.setTableColumns(List.of("serial","hsn","description","quantity","rate","discountPercent","gstPercent","total"));
        table.setTableColumnWidths(List.of(24.0,69.0,179.0,47.0,62.0,54.0,44.0,89.0));
        table.setTableColumnAlignments(List.of("CENTER","LEFT","LEFT","RIGHT","RIGHT","RIGHT","RIGHT","RIGHT"));
        e.add(table);

        // Last-page closing stack. Amount-in-words and terms remain on the left. The right side is
        // rebuilt dynamically from ERP values so GST/IGST/no-GST, discounts, charges and round-off
        // occupy exactly the rows they need instead of being hard-coded to the sample CGST/SGST layout.
        pair(e,"totals.amountInWordsText",18,365,301,25,5.8,false,"LEFT",white,"LAST");
        whiteout(e,18,441,302,47,white,"LAST");
        multi(e,"company.terms",18,443,302,44,5.4,false,"LEFT",1.15,"LAST");
        whiteout(e,327,328,254,188,white,"LAST");
        TemplateElement financial = TemplateElement.of(ElementType.BLOCK,0,329,330,250,183);
        financial.setReplacementGroupId(DYNAMIC_FINANCIAL_SUMMARY);
        financial.setFillEnabled(false); financial.setStrokeEnabled(false); financial.setLocked(true); financial.setPageRule("LAST");
        e.add(financial);

        // Bank / QR / signature artwork stays at the bottom and appears only on the final page.
        pair(e,"payment.bankName",61,548,89,12,5.4,false,"LEFT",white,"LAST");
        pair(e,"payment.accountNumber",48,557,102,12,5.4,false,"LEFT",white,"LAST");
        pair(e,"payment.ifsc",59,566,91,12,5.4,false,"LEFT",white,"LAST");
        pair(e,"payment.branch",47,575,103,12,5.4,false,"LEFT",white,"LAST");
        // Preserve the supplied QR and authorized-signature artwork exactly; PDF Studio must not repaint it.

        // Invisible LAST marker tells FLOW_FIXED where the protected footer begins on intermediate pages.
        TemplateElement footerGuard = TemplateElement.of(ElementType.BLOCK,0,13,772,568,1);
        footerGuard.setFillEnabled(false); footerGuard.setStrokeEnabled(false); footerGuard.setOpacity(0); footerGuard.setLocked(true); footerGuard.setPageRule("LAST");
        e.add(footerGuard);
        maskedLiteral(e,"Page {{document.pageNumber}} of {{document.totalPages}}",270,817,58,20,5.0,false,"CENTER",white,"EVERY");
        return e;
    }

    private static void field(List<TemplateElement> list,String key,double x,double y,double w,double h,double font,boolean bold,String align,String rule){TemplateElement f=TemplateElement.of(ElementType.FIELD,0,x,y,w,h);f.setFieldKey(key);f.setFontSize(font);f.setBold(bold);f.setTextAlignment(align);f.setTextFit("SHRINK");f.setFillEnabled(false);f.setStrokeEnabled(false);f.setPageRule(rule);f.setTextColor("#000000");list.add(f);}
    private static void pair(List<TemplateElement> list,String key,double x,double y,double w,double h,double font,boolean bold,String align,String bg,String rule){
        TemplateElement m=TemplateElement.of(ElementType.WHITEOUT,0,x+.5,y+.5,Math.max(1,w-1),Math.max(1,h-1));m.setFillColor(bg);m.setStrokeColor(bg);m.setStrokeWidth(0);m.setLocked(true);m.setPageRule(rule);list.add(m);
        TemplateElement f=TemplateElement.of(ElementType.FIELD,0,x,y,w,h);f.setFieldKey(key);f.setFontSize(font);f.setBold(bold);f.setTextAlignment(align);f.setTextFit("SHRINK");f.setFillEnabled(false);f.setStrokeEnabled(false);f.setPageRule(rule);f.setTextColor("#000000");list.add(f);
    }
    private static void maskedLiteral(List<TemplateElement> list,String text,double x,double y,double w,double h,double font,boolean bold,String align,String bg,String rule){
        TemplateElement m=TemplateElement.of(ElementType.WHITEOUT,0,x+.5,y+.5,Math.max(1,w-1),Math.max(1,h-1));m.setFillColor(bg);m.setStrokeColor(bg);m.setStrokeWidth(0);m.setLocked(true);m.setPageRule(rule);list.add(m);
        literal(list,text,x,y,w,h,font,bold,align,rule);
    }
    private static void literal(List<TemplateElement> list,String text,double x,double y,double w,double h,double font,boolean bold,String align,String rule){TemplateElement t=TemplateElement.of(ElementType.TEXT,0,x,y,w,h);t.setText(text);t.setFontSize(font);t.setBold(bold);t.setTextAlignment(align);t.setTextFit("SHRINK");t.setFillEnabled(false);t.setStrokeEnabled(false);t.setPageRule(rule);list.add(t);}
    private static void multi(List<TemplateElement> list,String key,double x,double y,double w,double h,double font,boolean bold,String align,double spacing,String rule){TemplateElement f=TemplateElement.of(ElementType.FIELD,0,x,y,w,h);f.setFieldKey(key);f.setFontSize(font);f.setBold(bold);f.setTextAlignment(align);f.setTextFit("WRAP");f.setLineSpacing(spacing);f.setFillEnabled(false);f.setStrokeEnabled(false);f.setPageRule(rule);list.add(f);}
    private static void whiteout(List<TemplateElement> list,double x,double y,double w,double h,String color,String rule){TemplateElement m=TemplateElement.of(ElementType.WHITEOUT,0,x,y,w,h);m.setFillColor(color);m.setStrokeColor(color);m.setStrokeWidth(0);m.setLocked(true);m.setPageRule(rule);list.add(m);}
}
