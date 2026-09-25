package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.example.documentstudio.model.PdfFormFieldRegion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Extracts selectable AcroForm widget geometry from the protected imported original. */
public final class PdfFormFieldExtractionService {
    private PdfFormFieldExtractionService() { }

    public static List<PdfFormFieldRegion> extract(Path originalPdf, int pageIndex) throws IOException {
        if (originalPdf == null || !Files.isRegularFile(originalPdf)) return List.of();
        try (PDDocument document = Loader.loadPDF(originalPdf.toFile())) {
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) return List.of();
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form == null) return List.of();
            PDPage page = document.getPage(pageIndex);
            List<PdfFormFieldRegion> out = new ArrayList<>();
            for (PDField field : form.getFieldTree()) {
                for (PDAnnotationWidget widget : field.getWidgets()) {
                    if (!belongsToPage(document, widget, page, pageIndex)) continue;
                    PDRectangle rect = widget.getRectangle();
                    if (rect == null || rect.getWidth() <= 0 || rect.getHeight() <= 0) continue;
                    PDRectangle box = page.getCropBox() == null ? page.getMediaBox() : page.getCropBox();
                    double x = rect.getLowerLeftX() - box.getLowerLeftX();
                    double y = box.getUpperRightY() - rect.getUpperRightY();
                    String sample;
                    try { sample = field.getValueAsString(); } catch (Exception ignored) { sample = ""; }
                    out.add(new PdfFormFieldRegion(pageIndex,
                            safe(field.getFullyQualifiedName()), safe(field.getAlternateFieldName()), safe(sample),
                            field.getClass().getSimpleName(), x, y, rect.getWidth(), rect.getHeight()));
                }
            }
            return out;
        }
    }

    private static boolean belongsToPage(PDDocument document, PDAnnotationWidget widget, PDPage target, int targetIndex) {
        PDPage widgetPage = widget.getPage();
        if (widgetPage != null) return samePage(widgetPage, target);
        try {
            for (PDAnnotation annotation : target.getAnnotations()) {
                if (annotation == widget || sameCos(annotation.getCOSObject(), widget.getCOSObject())) return true;
            }
        } catch (Exception ignored) { }
        // Defensive fallback for PDFs whose widgets omit /P and whose annotation wrappers differ.
        for (int p = 0; p < document.getNumberOfPages(); p++) {
            try {
                for (PDAnnotation annotation : document.getPage(p).getAnnotations()) {
                    if (sameCos(annotation.getCOSObject(), widget.getCOSObject())) return p == targetIndex;
                }
            } catch (Exception ignored) { }
        }
        return false;
    }

    private static boolean samePage(PDPage left, PDPage right) {
        return left == right || sameCos(left.getCOSObject(), right.getCOSObject());
    }

    private static boolean sameCos(COSBase left, COSBase right) {
        return left == right || (left != null && right != null && left.equals(right));
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
