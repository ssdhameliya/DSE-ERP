package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Validates imported PDFs once and creates the unencrypted private Document Studio working copy. */
public final class PdfImportSecurityService {
    private PdfImportSecurityService() {}

    public record Inspection(boolean encrypted, boolean ownerPermission, boolean canModify,
                             boolean canExtractContent, int pageCount) {
        public boolean editable() { return ownerPermission || (canModify && canExtractContent); }
    }

    /** Persistable import-time capability summary used to set honest editor expectations. */
    public record ContentAnalysis(int pageCount, int nativeTextPages, int scannedPages,
                                  int imageObjects, int formWidgets, int rotatedPages,
                                  int embeddedFonts, boolean complexGraphics,
                                  String capability, List<String> warnings) {
        public ContentAnalysis {
            capability = capability == null ? "PARTIAL" : capability;
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public static ContentAnalysis analyze(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) throw new IOException("The selected PDF does not exist.");
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            int nativeTextPages = 0, scannedPages = 0, images = 0, widgets = 0, rotated = 0, fonts = 0;
            boolean complex = false;
            PDFTextStripper stripper = new PDFTextStripper();
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                PDPage page = document.getPage(index);
                stripper.setStartPage(index + 1);
                stripper.setEndPage(index + 1);
                boolean hasText = !stripper.getText(document).isBlank();
                if (hasText) nativeTextPages++;
                PDResources resources = page.getResources();
                int pageImages = 0;
                if (resources != null) {
                    for (var ignored : resources.getFontNames()) fonts++;
                    for (var name : resources.getXObjectNames()) {
                        try {
                            PDXObject object = resources.getXObject(name);
                            if (object instanceof PDImageXObject) { images++; pageImages++; }
                            if (object instanceof PDFormXObject) complex = true;
                        } catch (IOException ignored) { complex = true; }
                    }
                    if (resources.getShadingNames().iterator().hasNext() || resources.getPatternNames().iterator().hasNext()) complex = true;
                }
                if (!hasText && pageImages > 0) scannedPages++;
                widgets += (int) page.getAnnotations().stream().filter(annotation -> "Widget".equals(annotation.getSubtype())).count();
                if (Math.floorMod(page.getRotation(), 360) != 0) rotated++;
            }
            List<String> warnings = new ArrayList<>();
            boolean ocrAvailable = PdfTextExtractionService.ocrAvailable();
            if (scannedPages > 0) warnings.add(scannedPages + " page(s) appear image-only. "
                    + (ocrAvailable ? "Installed OCR will be used when text mapping starts." : "Install Tesseract OCR or configure DSE_OCR_COMMAND for text mapping."));
            if (complex) warnings.add("Complex forms, patterns or shading were detected; background replacement may be approximate.");
            if (rotated > 0) warnings.add(rotated + " rotated page(s) require coordinate normalization during editing.");
            String capability = scannedPages == document.getNumberOfPages() && !ocrAvailable ? "OCR_REQUIRED"
                    : scannedPages > 0 || complex ? "PARTIAL" : "MAPPABLE";
            return new ContentAnalysis(document.getNumberOfPages(), nativeTextPages, scannedPages, images,
                    widgets, rotated, fonts, complex, capability, warnings);
        }
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
