package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.documentstudio.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

/** Adds the user-supplied modern Sales Invoice as a mapped, non-default starter template. */
final class BuiltInModernSalesTemplateInstaller {
    static final String TEMPLATE_ID = "starter-sales-invoice-modern-sal";
    private static final String RESOURCE = "/documentstudio/defaults/sales-invoice-modern.pdf";
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String DELETION_MARKER = ".starter-sales-invoice-modern-deleted";

    private BuiltInModernSalesTemplateInstaller() { }

    static void ensureInstalled(Path root) {
        try {
            if (isIntentionallyDeleted(root)) {
                removeLocalStarter(root);
                return;
            }
            Path folder = root.resolve(TEMPLATE_ID);
            if (Files.isDirectory(folder)) return;
            Files.createDirectories(folder.resolve("assets"));
            Files.createDirectories(folder.resolve("history"));
            try (InputStream in = BuiltInModernSalesTemplateInstaller.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IOException("Modern Sales starter PDF resource is missing.");
                Files.copy(in, folder.resolve("source.pdf"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.copy(folder.resolve("source.pdf"), folder.resolve("original.pdf"), StandardCopyOption.REPLACE_EXISTING);
            DocumentTemplate t = template();
            JSON.writeValue(folder.resolve("template.json").toFile(), t);
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

    private static DocumentTemplate template() {
        DocumentTemplate t = new DocumentTemplate();
        t.setId(TEMPLATE_ID);
        t.setName("DS Engineers Sales Invoice – Mapped Starter");
        t.setDocumentType(DocumentType.SALES_INVOICE);
        t.setCategory(TemplateCategory.ERP_TEMPLATE);
        t.setStudioSchemaVersion(4);
        t.setDataContractVersion(2);
        // This design is intentionally mapping-driven. The existing Jasvi template remains
        // the default/shared-dynamic reference until the user explicitly publishes/activates this one.
        t.setLayoutMode("MAPPED_FIXED");
        t.setVersion(1);
        t.setStatus(TemplateStatus.DRAFT);
        t.setDefaultTemplate(false);
        t.setRuntimeEnabled(false);
        t.setUnpublishedChanges(true);
        t.setPublishedVersion(0);
        t.setActiveVersion(0);
        t.setSourceFile("source.pdf");
        t.setCreatedAt(Instant.now().toString());
        t.setUpdatedAt(Instant.now().toString());
        t.setElements(elements());
        return t;
    }

    private static List<TemplateElement> elements() {
        List<TemplateElement> e = new ArrayList<>();
        String white = "#FFFFFF";

        // Company / invoice header.
        pair(e,"company.name",50,76,280,10,6.3,true,"LEFT",white,"EVERY");
        pair(e,"company.address",50,86,285,11,5.8,false,"LEFT",white,"EVERY");
        pair(e,"company.phone",50,96,90,9,5.8,false,"LEFT",white,"EVERY");
        pair(e,"company.email",50,105,150,9,5.8,false,"LEFT",white,"EVERY");
        pair(e,"company.gstin",53,123,120,9,6.0,true,"LEFT",white,"EVERY");
        pair(e,"document.number",525,51,51,11,7.0,true,"RIGHT","#0E3F79","EVERY");
        pair(e,"document.date",514,71,62,10,5.8,false,"LEFT",white,"EVERY");
        pair(e,"document.dueDate",514,81,62,10,5.8,false,"LEFT",white,"EVERY");
        pair(e,"document.poNumber",514,91,62,10,5.8,false,"LEFT",white,"EVERY");
        pair(e,"document.referenceNumber",514,101,62,10,5.8,false,"LEFT",white,"EVERY");
        pair(e,"sales.salesperson",514,111,62,10,5.8,false,"LEFT",white,"EVERY");
        literal(e,"INR",514,121,62,10,5.8,false,"LEFT","EVERY");

        // Bill To / Ship To.
        pair(e,"party.name",49,165,125,11,6.2,true,"LEFT",white,"EVERY");
        pair(e,"party.billingAddress",49,178,130,15,5.6,false,"LEFT",white,"EVERY");
        pair(e,"party.phone",65,190,105,9,5.4,false,"LEFT",white,"EVERY");
        pair(e,"party.email",65,200,110,9,5.1,false,"LEFT",white,"EVERY");
        pair(e,"party.billingGstin",72,209,105,9,5.5,true,"LEFT",white,"EVERY");
        pair(e,"party.name",239,165,125,11,6.2,true,"LEFT",white,"EVERY");
        pair(e,"party.deliveryAddress",239,178,130,15,5.6,false,"LEFT",white,"EVERY");
        pair(e,"party.phone",255,190,105,9,5.4,false,"LEFT",white,"EVERY");
        pair(e,"party.email",255,200,110,9,5.1,false,"LEFT",white,"EVERY");
        pair(e,"party.deliveryGstin",262,209,105,9,5.5,true,"LEFT",white,"EVERY");

        // Tax/GST details using currently available Sales fields.
        pair(e,"party.gstin",478,164,96,10,5.8,false,"LEFT",white,"EVERY");
        pair(e,"party.billingAddress",478,179,96,22,5.3,false,"LEFT",white,"EVERY");
        pair(e,"party.deliveryAddress",478,203,96,22,5.3,false,"LEFT",white,"EVERY");
        literal(e,"No",478,231,96,10,5.8,false,"LEFT","EVERY");
        pair(e,"document.referenceNumber",478,244,96,10,5.8,false,"LEFT",white,"EVERY");

        // Item table. Every visible column maps to an existing Sales item field.
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE,0,13.5,263.0,568.0,63.0);
        table.setHeaderHeight(22.0); table.setRowHeight(38.0); table.setFontSize(5.8);
        table.setTextColor("#000000"); table.setUseSourceTableDesign(true); table.setFillEnabled(false); table.setStrokeEnabled(false);
        table.setTableColumns(List.of("serial","code","description","quantity","rate","discountPercent","gstPercent","total"));
        table.setTableColumnWidths(List.of(24.5,70.0,186.0,45.0,65.0,56.0,47.0,74.5));
        table.setTableColumnAlignments(List.of("CENTER","LEFT","LEFT","RIGHT","RIGHT","RIGHT","RIGHT","RIGHT"));
        e.add(table);

        // Amount in words / terms / existing calculation values. Source artwork remains intact;
        // only the sample values are replaced.
        pair(e,"totals.amountInWordsText",21,365,300,23,5.8,false,"LEFT",white,"LAST");
        whiteout(e,21,443,302,43,white,"LAST");
        multi(e,"company.terms",21,446,302,38,5.4,false,"LEFT",1.15,"LAST");
        pair(e,"totals.basicAmount",505,334,72,17,6.2,true,"RIGHT",white,"LAST");
        pair(e,"totals.taxableAmount",505,359,72,17,6.2,true,"RIGHT",white,"LAST");
        pair(e,"totals.cgstAmount",505,383,72,17,6.2,true,"RIGHT",white,"LAST");
        pair(e,"totals.sgstAmount",505,408,72,17,6.2,true,"RIGHT",white,"LAST");
        pair(e,"totals.roundedGrandTotal",505,432,72,18,6.8,true,"RIGHT","#0E3F79","LAST");

        // Bank + signature + footer. QR remains the user's source artwork until a dynamic QR field is added.
        pair(e,"payment.bankName",60,550,82,9,5.4,false,"LEFT",white,"LAST");
        pair(e,"payment.accountNumber",48,559,94,9,5.4,false,"LEFT",white,"LAST");
        pair(e,"payment.ifsc",59,568,92,9,5.4,false,"LEFT",white,"LAST");
        pair(e,"payment.branch",48,577,94,9,5.4,false,"LEFT",white,"LAST");
        imageField(e,"company.signature",386,545,183,198,"LAST");
        pair(e,"company.phone",73,781,75,10,5.0,false,"CENTER","#0E3F79","LAST");
        pair(e,"company.email",191,781,105,10,5.0,false,"CENTER","#0E3F79","LAST");
        literal(e,"Page {{document.pageNumber}} of {{document.totalPages}}",270,825,58,10,5.0,false,"CENTER","EVERY");
        return e;
    }

    private static void pair(List<TemplateElement> list,String key,double x,double y,double w,double h,double font,boolean bold,String align,String bg,String rule){
        TemplateElement m=TemplateElement.of(ElementType.WHITEOUT,0,x+.5,y+.5,Math.max(1,w-1),Math.max(1,h-1));m.setFillColor(bg);m.setStrokeColor(bg);m.setStrokeWidth(0);m.setLocked(true);m.setPageRule(rule);list.add(m);
        TemplateElement f=TemplateElement.of(ElementType.FIELD,0,x,y,w,h);f.setFieldKey(key);f.setFontSize(font);f.setBold(bold);f.setTextAlignment(align);f.setTextFit("SHRINK");f.setFillEnabled(false);f.setStrokeEnabled(false);f.setPageRule(rule);f.setTextColor("#000000");list.add(f);
    }
    private static void literal(List<TemplateElement> list,String text,double x,double y,double w,double h,double font,boolean bold,String align,String rule){TemplateElement t=TemplateElement.of(ElementType.TEXT,0,x,y,w,h);t.setText(text);t.setFontSize(font);t.setBold(bold);t.setTextAlignment(align);t.setTextFit("SHRINK");t.setFillEnabled(false);t.setStrokeEnabled(false);t.setPageRule(rule);list.add(t);}
    private static void multi(List<TemplateElement> list,String key,double x,double y,double w,double h,double font,boolean bold,String align,double spacing,String rule){TemplateElement f=TemplateElement.of(ElementType.FIELD,0,x,y,w,h);f.setFieldKey(key);f.setFontSize(font);f.setBold(bold);f.setTextAlignment(align);f.setTextFit("WRAP");f.setLineSpacing(spacing);f.setFillEnabled(false);f.setStrokeEnabled(false);f.setPageRule(rule);list.add(f);}
    private static void imageField(List<TemplateElement> list,String key,double x,double y,double w,double h,String rule){TemplateElement f=TemplateElement.of(ElementType.IMAGE_FIELD,0,x,y,w,h);f.setFieldKey(key);f.setImageFit("FIT");f.setPreserveAspectRatio(true);f.setPageRule(rule);list.add(f);}
    private static void whiteout(List<TemplateElement> list,double x,double y,double w,double h,String color,String rule){TemplateElement m=TemplateElement.of(ElementType.WHITEOUT,0,x,y,w,h);m.setFillColor(color);m.setStrokeColor(color);m.setStrokeWidth(0);m.setLocked(true);m.setPageRule(rule);list.add(m);}
}
