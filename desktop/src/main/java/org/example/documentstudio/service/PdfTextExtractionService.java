package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.example.documentstudio.model.PdfTextRegion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Detects selectable text regions in an imported PDF without modifying it.
 *
 * <p>v8.2.2 keeps the detected geometry tight and captures the source text
 * appearance (font family hint, bold/italic, color and rotation). Mapping a
 * printed value to an ERP field can therefore inherit the original look rather
 * than silently switching to Studio defaults.</p>
 */
public final class PdfTextExtractionService {
    private PdfTextExtractionService() {}

    public static List<PdfTextRegion> extract(Path pdf, int pageIndex) throws IOException {
        if (pdf == null) return List.of();
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) return List.of();
            RegionStripper stripper = new RegionStripper(pageIndex);
            stripper.setSortByPosition(true);
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            stripper.getText(document);
            return stripper.regions().stream()
                    .sorted(Comparator.comparingDouble(PdfTextRegion::y).thenComparingDouble(PdfTextRegion::x))
                    .toList();
        }
    }

    private static final class RegionStripper extends PDFTextStripper {
        private final int pageIndex;
        private final List<PdfTextRegion> regions = new ArrayList<>();

        private RegionStripper(int pageIndex) throws IOException {
            this.pageIndex = pageIndex;
        }

        List<PdfTextRegion> regions() { return regions; }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            super.writeString(text, positions);
            if (text == null || text.isBlank() || positions == null || positions.isEmpty()) return;

            String cleaned = text.replace('\u0000', ' ').trim();
            if (cleaned.isBlank()) return;

            double minX = Double.MAX_VALUE;
            double maxX = 0;
            double minTop = Double.MAX_VALUE;
            double maxBottom = 0;
            double fontTotal = 0;
            int fontCount = 0;
            String fontName = "";
            double rotation = 0;

            for (TextPosition position : positions) {
                if (position == null) continue;
                double x = Math.max(0, position.getXDirAdj());
                double height = Math.max(1, position.getHeightDir());
                double top = Math.max(0, position.getYDirAdj() - height);
                double right = x + Math.max(1, position.getWidthDirAdj());
                double bottom = top + height;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, right);
                minTop = Math.min(minTop, top);
                maxBottom = Math.max(maxBottom, bottom);
                if (position.getFontSizeInPt() > 0) {
                    fontTotal += position.getFontSizeInPt();
                    fontCount++;
                }
                if (fontName.isBlank() && position.getFont() != null) {
                    try { fontName = position.getFont().getName(); } catch (Exception ignored) { }
                }
                if (Math.abs(rotation) < 0.01) {
                    try { rotation = position.getDir(); } catch (Exception ignored) { }
                }
            }

            if (minX == Double.MAX_VALUE || minTop == Double.MAX_VALUE) return;
            double width = Math.max(1, maxX - minX);
            double height = Math.max(1, maxBottom - minTop);
            double fontSize = fontCount == 0 ? Math.max(8, height * 0.82) : fontTotal / fontCount;
            String upper = fontName == null ? "" : fontName.toUpperCase(Locale.ROOT);
            boolean bold = upper.contains("BOLD") || upper.contains("BLACK") || upper.contains("SEMIBOLD") || upper.contains("DEMI");
            boolean italic = upper.contains("ITALIC") || upper.contains("OBLIQUE");
            String textColor = currentTextColor();

            regions.add(new PdfTextRegion(
                    pageIndex,
                    cleaned,
                    minX,
                    minTop,
                    width,
                    height,
                    fontSize,
                    fontName,
                    bold,
                    italic,
                    textColor,
                    rotation
            ));
        }

        private String currentTextColor() {
            try {
                int rgb = getGraphicsState().getNonStrokingColor().toRGB();
                return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
            } catch (Exception ignored) {
                return "#172033";
            }
        }
    }
}
