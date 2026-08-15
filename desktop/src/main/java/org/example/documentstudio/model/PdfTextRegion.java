package org.example.documentstudio.model;

/**
 * Read-only text region detected in an imported PDF page. Coordinates use PDF
 * points measured from the page's top-left, matching the Document Studio canvas.
 */
public record PdfTextRegion(
        int pageIndex,
        String text,
        double x,
        double y,
        double width,
        double height,
        double fontSize
) {
    public PdfTextRegion {
        text = text == null ? "" : text;
        x = Math.max(0, x);
        y = Math.max(0, y);
        width = Math.max(1, width);
        height = Math.max(1, height);
        fontSize = Math.max(5, Math.min(72, fontSize));
    }
}
