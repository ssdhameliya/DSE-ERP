package org.example.documentstudio.model;

/**
 * Read-only AcroForm widget detected in an imported PDF. Coordinates use PDF
 * points measured from the page's top-left, matching the PDF Studio canvas.
 *
 * <p>The protected original may contain a sample value while the normalized
 * Studio working copy intentionally clears it. Keeping the widget name and
 * original sample here makes a blank editable form field selectable/mappable
 * without ever leaking stale source data into preview/final output.</p>
 */
public record PdfFormFieldRegion(
        int pageIndex,
        String fieldName,
        String alternateName,
        String sampleValue,
        String fieldType,
        double x,
        double y,
        double width,
        double height
) {
    public PdfFormFieldRegion {
        fieldName = fieldName == null ? "" : fieldName;
        alternateName = alternateName == null ? "" : alternateName;
        sampleValue = sampleValue == null ? "" : sampleValue;
        fieldType = fieldType == null ? "" : fieldType;
        x = Math.max(0, x);
        y = Math.max(0, y);
        width = Math.max(1, width);
        height = Math.max(1, height);
    }

    public String displayName() {
        if (!alternateName.isBlank()) return alternateName;
        if (!fieldName.isBlank()) return fieldName;
        return "PDF form field";
    }
}
