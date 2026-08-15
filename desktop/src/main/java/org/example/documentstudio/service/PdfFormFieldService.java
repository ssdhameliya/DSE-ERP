package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.example.documentstudio.model.PdfFormFieldRegion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Detects visible AcroForm widgets so they can be replaced non-destructively in Document Studio. */
public final class PdfFormFieldService {
    private PdfFormFieldService() {}

    public static List<PdfFormFieldRegion> extract(Path pdf, int pageIndex) throws IOException {
        if (pdf == null) return List.of();
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) return List.of();
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form == null) return List.of();
            PDPage target = document.getPage(pageIndex);
            double pageHeight = target.getCropBox().getHeight();
            List<PdfFormFieldRegion> result = new ArrayList<>();
            for (PDField field : form.getFieldTree()) {
                if (!(field instanceof PDTerminalField terminal)) continue;
                for (PDAnnotationWidget widget : terminal.getWidgets()) {
                    if (widget.getRectangle() == null) continue;
                    PDPage widgetPage = widget.getPage();
                    if (widgetPage != null && widgetPage.getCOSObject() != target.getCOSObject()) continue;
                    if (widgetPage == null) {
                        boolean belongsToTarget = target.getAnnotations().stream()
                                .anyMatch(annotation -> annotation.getCOSObject() == widget.getCOSObject());
                        if (!belongsToTarget) continue;
                    }
                    PDRectangle r = widget.getRectangle();
                    double x = Math.max(0, r.getLowerLeftX());
                    double y = Math.max(0, pageHeight - r.getUpperRightY());
                    double w = Math.max(6, r.getWidth());
                    double h = Math.max(6, r.getHeight());
                    String value;
                    try { value = field.getValueAsString(); } catch (Exception ignored) { value = ""; }
                    String name = field.getFullyQualifiedName();
                    if (name == null || name.isBlank()) name = field.getPartialName();
                    if (name == null || name.isBlank()) name = "Form Field";
                    result.add(new PdfFormFieldRegion(pageIndex, name, value, x, y, w, h));
                }
            }
            return result;
        }
    }
}
