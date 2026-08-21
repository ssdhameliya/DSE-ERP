package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.example.documentstudio.model.PdfTextRegion;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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
            List<PdfTextRegion> nativeRegions = stripper.regions().stream()
                    .sorted(Comparator.comparingDouble(PdfTextRegion::y).thenComparingDouble(PdfTextRegion::x))
                    .toList();
            if (!nativeRegions.isEmpty()) return nativeRegions;
            return extractOcr(document, pageIndex);
        }
    }

    /** Uses an installed Tesseract engine only for image-only pages; native PDF text always wins. */
    private static List<PdfTextRegion> extractOcr(PDDocument document, int pageIndex) {
        Path executable = tesseractExecutable();
        if (executable == null) return List.of();
        Path temp = null;
        try {
            temp = Files.createTempDirectory("dse-pdf-ocr-");
            Path imageFile = temp.resolve("page.png");
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(pageIndex, 200, ImageType.RGB);
            ImageIO.write(image, "png", imageFile.toFile());
            String language = ocrLanguage();
            Path outputBase = temp.resolve("ocr");
            Process process = new ProcessBuilder(executable.toString(), imageFile.toString(), outputBase.toString(), "-l", language, "tsv")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); return List.of(); }
            Path tsvFile = temp.resolve("ocr.tsv");
            if (process.exitValue() != 0 || !Files.isRegularFile(tsvFile)) return List.of();
            double pageWidth = document.getPage(pageIndex).getCropBox().getWidth();
            double pageHeight = document.getPage(pageIndex).getCropBox().getHeight();
            return parseTsv(Files.readString(tsvFile, StandardCharsets.UTF_8), pageIndex,
                    pageWidth / image.getWidth(), pageHeight / image.getHeight());
        } catch (Exception error) {
            System.err.println("[DocumentStudio] OCR unavailable: " + error.getMessage());
            return List.of();
        } finally {
            if (temp != null) try (var files = Files.walk(temp)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch (Exception ignored) { }
        }
    }

    private static List<PdfTextRegion> parseTsv(String tsv, int pageIndex, double sx, double sy) {
        record OcrLine(StringBuilder text, double[] bounds) {}
        Map<String, OcrLine> lines = new LinkedHashMap<>();
        for (String row : tsv.split("\\R")) {
            String[] columns = row.split("\\t", 12);
            if (columns.length < 12 || !"5".equals(columns[0]) || columns[11].isBlank()) continue;
            try {
                double confidence = Double.parseDouble(columns[10]);
                if (confidence < 30) continue;
                double x = Double.parseDouble(columns[6]) * sx, y = Double.parseDouble(columns[7]) * sy;
                double w = Double.parseDouble(columns[8]) * sx, h = Double.parseDouble(columns[9]) * sy;
                String key = columns[2] + ":" + columns[3] + ":" + columns[4];
                OcrLine line = lines.computeIfAbsent(key, ignored -> new OcrLine(new StringBuilder(),
                        new double[]{x, y, x + w, y + h}));
                if (!line.text().isEmpty()) line.text().append(' ');
                line.text().append(columns[11].trim());
                line.bounds()[0] = Math.min(line.bounds()[0], x); line.bounds()[1] = Math.min(line.bounds()[1], y);
                line.bounds()[2] = Math.max(line.bounds()[2], x + w); line.bounds()[3] = Math.max(line.bounds()[3], y + h);
            } catch (NumberFormatException ignored) { }
        }
        List<PdfTextRegion> result = new ArrayList<>();
        for (OcrLine line : lines.values()) {
            double[] b = line.bounds();
            double height = Math.max(1, b[3] - b[1]);
            result.add(new PdfTextRegion(pageIndex, line.text().toString(), b[0], b[1],
                    Math.max(1, b[2] - b[0]), height, Math.max(7, height * .78),
                    "OCR", false, false, "#172033", 0));
        }
        return result;
    }

    public static boolean ocrAvailable() { return tesseractExecutable() != null; }

    private static Path tesseractExecutable() {
        List<String> candidates = new ArrayList<>();
        String configured = System.getProperty("dse.ocr.command", "").trim();
        String environment = System.getenv("DSE_OCR_COMMAND");
        if (!configured.isBlank()) candidates.add(configured);
        if (environment != null && !environment.isBlank()) candidates.add(environment.trim());
        candidates.add("C:/Program Files/Tesseract-OCR/tesseract.exe");
        candidates.add("C:/Program Files (x86)/Tesseract-OCR/tesseract.exe");
        candidates.add("/opt/homebrew/bin/tesseract");
        candidates.add("/usr/local/bin/tesseract");
        candidates.add("/usr/bin/tesseract");
        return candidates.stream().map(Path::of).filter(Files::isRegularFile).findFirst().orElse(null);
    }

    private static String ocrLanguage() {
        String language = System.getProperty("dse.ocr.language", System.getenv().getOrDefault("DSE_OCR_LANGUAGE", "eng"));
        return language != null && language.matches("[A-Za-z0-9_+.-]+") ? language : "eng";
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
