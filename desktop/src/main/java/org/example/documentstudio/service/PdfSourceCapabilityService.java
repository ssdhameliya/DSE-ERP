package org.example.documentstudio.service;

import org.example.documentstudio.model.PdfFormFieldRegion;
import org.example.documentstudio.model.PdfImageRegion;
import org.example.documentstudio.model.PdfTextRegion;

import java.util.List;

/**
 * Classifies an imported PDF page before mapping so the editor never pretends that
 * rasterised/scanned sample text can be removed like native PDF text.
 */
public final class PdfSourceCapabilityService {
    public enum Kind { FILLABLE_FORM, NATIVE_TEXT, MIXED, FLATTENED_IMAGE, ARTWORK_ONLY }

    public record Capability(Kind kind, boolean exactValueReplacementSupported, String userMessage) { }

    private PdfSourceCapabilityService() { }

    public static Capability analyze(double pageWidth, double pageHeight,
                                     List<PdfTextRegion> text,
                                     List<PdfFormFieldRegion> forms,
                                     List<PdfImageRegion> images,
                                     List<PdfImageExtractionService.VectorRegion> vectors) {
        int textCount = text == null ? 0 : (int) text.stream().filter(r -> r != null && r.text() != null && !r.text().isBlank()).count();
        int formCount = forms == null ? 0 : forms.size();
        int imageCount = images == null ? 0 : images.size();
        int vectorCount = vectors == null ? 0 : vectors.size();
        double pageArea = Math.max(1d, pageWidth * pageHeight);
        double maxImageCoverage = images == null ? 0d : images.stream().filter(java.util.Objects::nonNull)
                .mapToDouble(r -> Math.max(0, r.width()) * Math.max(0, r.height()) / pageArea).max().orElse(0d);

        if (formCount > 0) {
            return new Capability(Kind.FILLABLE_FORM, true,
                    "Fillable PDF detected - form fields can be replaced without covering the template artwork.");
        }
        if (textCount > 0 && (imageCount > 0 || vectorCount > 0)) {
            return new Capability(Kind.MIXED, true,
                    "Native PDF text + artwork detected - printed text can be replaced without flattening the background.");
        }
        if (textCount > 0) {
            return new Capability(Kind.NATIVE_TEXT, true,
                    "Native PDF text detected - printed values can be replaced directly.");
        }
        if (maxImageCoverage >= .55d) {
            return new Capability(Kind.FLATTENED_IMAGE, false,
                    "Flattened/scanned PDF detected - printed values are image pixels. Exact value replacement is unavailable; use an editable PDF or intentionally place an overlay in a clean area.");
        }
        return new Capability(Kind.ARTWORK_ONLY, false,
                "Artwork-only PDF page detected - no replaceable text/form fields were found. New ERP fields can only be placed as intentional overlays.");
    }
}
