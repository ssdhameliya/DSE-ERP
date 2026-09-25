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
import java.util.Optional;

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

    /**
     * Returns a visual hit-region for the value part of a common "Label : Value" source line.
     * Mapping still retains the original full source line so native-text suppression can redraw the
     * fixed label safely when the PDF stores label and value in one text-show operator.
     */
    public static Optional<PdfTextRegion> valueHitRegion(PdfTextRegion region) {
        if (region == null || region.text() == null) return Optional.empty();
        String raw = region.text().trim();
        int colon = raw.indexOf(':');
        if (colon <= 0 || colon > Math.min(45, raw.length() - 1)) return Optional.empty();
        String prefix = raw.substring(0, colon).trim();
        String lower = prefix.toLowerCase(Locale.ROOT);
        if (prefix.isBlank() || !prefix.matches(".*[A-Za-z].*") || lower.startsWith("http") || lower.startsWith("www")) return Optional.empty();
        int valueStart = colon + 1;
        while (valueStart < raw.length() && Character.isWhitespace(raw.charAt(valueStart))) valueStart++;
        if (valueStart >= raw.length()) return Optional.empty();
        double ratio = Math.min(.90, Math.max(.08, valueStart / (double)Math.max(1, raw.length())));
        double x = region.x() + region.width() * ratio;
        return Optional.of(new PdfTextRegion(region.pageIndex(), raw.substring(valueStart), x, region.y(),
                Math.max(6, region.x() + region.width() - x), region.height(), region.fontSize(), region.fontName(),
                region.bold(), region.italic(), region.textColor(), region.rotation()));
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

            // PDFTextStripper may present visually separate left/right columns as one logical line.
            // Split only on clearly large physical gaps so address/terms/signature columns remain
            // independently selectable without fragmenting ordinary words.
            for (List<TextPosition> run : splitPhysicalRuns(positions)) addRegion(run);
        }

        private List<List<TextPosition>> splitPhysicalRuns(List<TextPosition> positions) {
            List<List<TextPosition>> runs = new ArrayList<>();
            List<TextPosition> current = new ArrayList<>();
            TextPosition previous = null;
            for (TextPosition position : positions) {
                if (position == null) continue;
                if (previous != null) {
                    double previousRight = previous.getXDirAdj() + Math.max(1, previous.getWidthDirAdj());
                    double gap = position.getXDirAdj() - previousRight;
                    double font = Math.max(Math.max(1, previous.getFontSizeInPt()), Math.max(1, position.getFontSizeInPt()));
                    double threshold = Math.max(20.0, font * 2.35);
                    if (!current.isEmpty() && gap > threshold) {
                        runs.add(current);
                        current = new ArrayList<>();
                    }
                }
                current.add(position);
                previous = position;
            }
            if (!current.isEmpty()) runs.add(current);
            return runs;
        }

        private void addRegion(List<TextPosition> positions) {
            if (positions == null || positions.isEmpty()) return;
            double minX = Double.MAX_VALUE;
            double maxX = 0;
            double minTop = Double.MAX_VALUE;
            double maxBottom = 0;
            double fontTotal = 0;
            int fontCount = 0;
            String fontName = "";
            double rotation = 0;
            StringBuilder text = new StringBuilder();
            TextPosition previous = null;

            for (TextPosition position : positions) {
                if (position == null) continue;
                String unicode = position.getUnicode() == null ? "" : position.getUnicode().replace('\u0000', ' ');
                if (previous != null && !unicode.isBlank()) {
                    double previousRight = previous.getXDirAdj() + Math.max(1, previous.getWidthDirAdj());
                    double gap = position.getXDirAdj() - previousRight;
                    double font = Math.max(Math.max(1, previous.getFontSizeInPt()), Math.max(1, position.getFontSizeInPt()));
                    if (gap > Math.max(1.6, font * .30) && text.length() > 0 && !Character.isWhitespace(text.charAt(text.length()-1))) text.append(' ');
                }
                text.append(unicode);

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
                previous = position;
            }

            String cleaned = text.toString().replaceAll("\\s+", " " ).trim();
            if (cleaned.isBlank() || minX == Double.MAX_VALUE || minTop == Double.MAX_VALUE) return;
            double width = Math.max(1, maxX - minX);
            double height = Math.max(1, maxBottom - minTop);
            double fontSize = fontCount == 0 ? Math.max(8, height * 0.82) : fontTotal / fontCount;
            String upper = fontName == null ? "" : fontName.toUpperCase(Locale.ROOT);
            boolean bold = upper.contains("BOLD") || upper.contains("BLACK") || upper.contains("SEMIBOLD") || upper.contains("DEMI");
            boolean italic = upper.contains("ITALIC") || upper.contains("OBLIQUE");
            regions.add(new PdfTextRegion(pageIndex, cleaned, minX, minTop, width, height, fontSize, fontName, bold, italic, currentTextColor(), rotation));
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
