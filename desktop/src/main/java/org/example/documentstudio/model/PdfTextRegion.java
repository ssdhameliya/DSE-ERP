package org.example.documentstudio.model;

/**
 * Read-only text region detected in an imported PDF page. Coordinates use PDF
 * points measured from the page's top-left, matching the Document Studio canvas.
 *
 * <p>v8.2.2 also carries the source appearance so a mapped ERP value can inherit
 * the original PDF text size/style/color instead of being restyled by Studio.</p>
 */
public record PdfTextRegion(
        int pageIndex,
        String text,
        double x,
        double y,
        double width,
        double height,
        double fontSize,
        String fontName,
        boolean bold,
        boolean italic,
        String textColor,
        double rotation
) {
    public PdfTextRegion {
        text = text == null ? "" : text;
        x = Math.max(0, x);
        y = Math.max(0, y);
        width = Math.max(1, width);
        height = Math.max(1, height);
        fontSize = Math.max(5, Math.min(72, fontSize));
        fontName = fontName == null ? "" : fontName;
        textColor = textColor == null || !textColor.matches("#[0-9a-fA-F]{6}") ? "#172033" : textColor.toUpperCase();
        double normalized = rotation % 360.0;
        if (normalized < 0) normalized += 360.0;
        rotation = normalized;
    }

    /** Backward-compatible constructor used by older callers/tests. */
    public PdfTextRegion(int pageIndex, String text, double x, double y, double width, double height, double fontSize) {
        this(pageIndex, text, x, y, width, height, fontSize, "", false, false, "#172033", 0);
    }
}
