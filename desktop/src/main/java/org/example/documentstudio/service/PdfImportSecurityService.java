package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.cos.COSName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Validates imported PDFs once and creates the unencrypted private Document Studio working copy. */
public final class PdfImportSecurityService {
    private PdfImportSecurityService() {}

    public record Inspection(boolean encrypted, boolean ownerPermission, boolean canModify,
                             boolean canExtractContent, int pageCount) {
        public boolean editable() { return ownerPermission || (canModify && canExtractContent); }
    }

    public static Inspection inspect(Path source, String password) throws IOException {
        if (source == null || !Files.isRegularFile(source)) throw new IOException("The selected PDF does not exist.");
        try (PDDocument document = Loader.loadPDF(source.toFile(), password == null ? "" : password)) {
            if (document.getNumberOfPages() < 1) throw new IOException("The selected PDF contains no pages.");
            AccessPermission permission = document.getCurrentAccessPermission();
            boolean owner = permission == null || permission.isOwnerPermission();
            boolean modify = permission == null || permission.canModify();
            boolean extract = permission == null || permission.canExtractContent();
            return new Inspection(document.isEncrypted(), owner, modify, extract, document.getNumberOfPages());
        }
    }

    /**
     * Imported AcroForms often contain sample customer/invoice values.  Keep the protected
     * original untouched, but clear those values from the private Studio working copy so
     * preview/final output can never reveal stale sample data underneath ERP overlays.
     * Field names and widgets remain intact for auto-mapping.
     */
    private static void clearAcroFormSampleValues(PDDocument document) {
        try {
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form == null) return;
            for (PDField field : form.getFieldTree()) {
                try { field.setValue(""); }
                catch (Exception ignored) { /* signature/button fields may not accept text values */ }
                field.getCOSObject().removeItem(COSName.V);
                field.getCOSObject().removeItem(COSName.DV);
                for (var widget : field.getWidgets()) widget.getCOSObject().removeItem(COSName.AP);
            }
        } catch (Exception ignored) {
            // Form cleanup is defensive; a non-AcroForm PDF remains importable.
        }
    }

    public static Inspection normalizeForEditing(Path source, Path target, String password) throws IOException {
        Inspection inspection = inspect(source, password);
        if (!inspection.editable()) {
            throw new PdfPermissionException("This password opens the PDF for viewing, but does not grant both modification and text-extraction permission. Enter the owner password to import it as an editable document.");
        }
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".unlocking.tmp");
        Files.deleteIfExists(temp);
        try (PDDocument document = Loader.loadPDF(source.toFile(), password == null ? "" : password)) {
            document.setAllSecurityToBeRemoved(true);
            clearAcroFormSampleValues(document);
            document.save(temp.toFile());
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        try (PDDocument verify = Loader.loadPDF(target.toFile())) {
            if (verify.getNumberOfPages() < 1) throw new IOException("The normalized PDF contains no pages.");
            if (verify.isEncrypted()) throw new IOException("The private workspace copy could not be normalized without encryption.");
        }
        return inspection;
    }
}
