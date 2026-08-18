package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.util.Matrix;
import org.example.documentstudio.model.PdfImageRegion;

import javax.imageio.ImageIO;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Detects raster images and simple editable vector blocks in an imported PDF. */
public final class PdfImageExtractionService {
    private PdfImageExtractionService() {}

    /** A straight line or rectangular paint operation using Document Studio top-left coordinates. */
    public record VectorPrimitive(
            String kind, double x, double y, double width, double height,
            String fillColor, String strokeColor, double strokeWidth,
            boolean filled, boolean stroked) {
        public VectorPrimitive {
            kind = kind == null ? "LINE" : kind;
            fillColor = fillColor == null ? "#FFFFFF" : fillColor;
            strokeColor = strokeColor == null ? "#172033" : strokeColor;
            strokeWidth = Math.max(0.5, strokeWidth);
        }
    }

    /** One selectable vector target. Connected straight lines are grouped as a grid/table target. */
    public record VectorRegion(
            int pageIndex, String kind, double x, double y, double width, double height,
            List<VectorPrimitive> primitives, String sourceKey) {
        public VectorRegion {
            kind = kind == null ? "BLOCK" : kind;
            primitives = primitives == null ? List.of() : List.copyOf(primitives);
            sourceKey = sourceKey == null ? "" : sourceKey;
        }
    }

    public static List<PdfImageRegion> extract(Path pdf, int pageIndex, Path tempFolder) throws IOException {
        if (pdf == null) return List.of();
        Files.createDirectories(tempFolder);
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) return List.of();
            PDPage page = document.getPage(pageIndex);
            GraphicsEngine engine = new GraphicsEngine(page, pageIndex, tempFolder, true, false);
            engine.run();
            return engine.imageRegions();
        }
    }

    /**
     * Detect simple rectangles and straight line networks. Curves, clipping paths and shadings are
     * intentionally ignored so Document Studio can fall back to Select / Hide Area rather than
     * pretending a complex vector can be safely reconstructed.
     */
    public static List<VectorRegion> extractVectors(Path pdf, int pageIndex) throws IOException {
        if (pdf == null) return List.of();
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) return List.of();
            PDPage page = document.getPage(pageIndex);
            GraphicsEngine engine = new GraphicsEngine(page, pageIndex, null, false, true);
            engine.run();
            return groupVectorRegions(pageIndex, engine.vectorPrimitives());
        }
    }

    private static List<VectorRegion> groupVectorRegions(int pageIndex, List<VectorPrimitive> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<VectorRegion> result = new ArrayList<>();
        List<VectorPrimitive> lines = new ArrayList<>();
        int sequence = 0;
        for (VectorPrimitive primitive : raw) {
            if ("RECTANGLE".equals(primitive.kind())) {
                result.add(region(pageIndex, "BLOCK", List.of(primitive), sequence++));
            } else {
                lines.add(primitive);
            }
        }
        boolean[] used = new boolean[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            if (used[i]) continue;
            List<VectorPrimitive> group = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(i); used[i] = true;
            while (!queue.isEmpty()) {
                int index = queue.removeFirst();
                VectorPrimitive current = lines.get(index); group.add(current);
                for (int j = 0; j < lines.size(); j++) {
                    if (used[j]) continue;
                    if (nearOrIntersecting(current, lines.get(j))) { used[j] = true; queue.addLast(j); }
                }
            }
            result.add(region(pageIndex, group.size() >= 3 ? "TABLE / GRID" : "LINE", group, sequence++));
        }
        return result.stream()
                .filter(r -> r.width() >= 1 && r.height() >= 1)
                .sorted(Comparator.comparingDouble(VectorRegion::y).thenComparingDouble(VectorRegion::x))
                .toList();
    }

    private static VectorRegion region(int pageIndex, String kind, List<VectorPrimitive> primitives, int sequence) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = 0, maxY = 0;
        for (VectorPrimitive p : primitives) {
            minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x() + Math.max(1, p.width()));
            maxY = Math.max(maxY, p.y() + Math.max(1, p.height()));
        }
        if (minX == Double.MAX_VALUE) minX = minY = 0;
        String key = "PDF_BLOCK|" + pageIndex + "|" + sequence + "|" + rounded(minX) + "|" + rounded(minY)
                + "|" + rounded(maxX - minX) + "|" + rounded(maxY - minY);
        return new VectorRegion(pageIndex, kind, minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY), primitives, key);
    }

    private static boolean nearOrIntersecting(VectorPrimitive a, VectorPrimitive b) {
        double pad = 3.5;
        double aLeft = a.x() - pad, aTop = a.y() - pad, aRight = a.x() + Math.max(1, a.width()) + pad, aBottom = a.y() + Math.max(1, a.height()) + pad;
        double bLeft = b.x() - pad, bTop = b.y() - pad, bRight = b.x() + Math.max(1, b.width()) + pad, bBottom = b.y() + Math.max(1, b.height()) + pad;
        return aRight >= bLeft && bRight >= aLeft && aBottom >= bTop && bBottom >= aTop;
    }

    private static String rounded(double value) { return String.valueOf(Math.round(value * 10.0) / 10.0); }

    private static final class GraphicsEngine extends PDFGraphicsStreamEngine {
        private final PDPage page;
        private final int pageIndex;
        private final Path tempFolder;
        private final boolean collectImages;
        private final boolean collectVectors;
        private final List<PdfImageRegion> images = new ArrayList<>();
        private final List<VectorPrimitive> vectors = new ArrayList<>();
        private final List<double[]> pathLines = new ArrayList<>();
        private Point2D currentPoint;
        private Point2D firstPoint;
        private boolean rectanglePath;
        private boolean complexPath;

        private GraphicsEngine(PDPage page, int pageIndex, Path tempFolder, boolean collectImages, boolean collectVectors) {
            super(page);
            this.page = page;
            this.pageIndex = pageIndex;
            this.tempFolder = tempFolder;
            this.collectImages = collectImages;
            this.collectVectors = collectVectors;
        }

        private List<PdfImageRegion> imageRegions() { return images; }
        private List<VectorPrimitive> vectorPrimitives() { return vectors; }
        private void run() throws IOException { processPage(page); }

        @Override public void drawImage(PDImage pdImage) throws IOException {
            if (!collectImages || pdImage == null) return;
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            double width = Math.abs(ctm.getScalingFactorX());
            double height = Math.abs(ctm.getScalingFactorY());
            if (width < 3 || height < 3) return;
            var crop = page.getCropBox();
            double x = ctm.getTranslateX() - crop.getLowerLeftX();
            double y = crop.getUpperRightY() - (ctm.getTranslateY() + height);
            Path extracted = tempFolder.resolve("pdf-image-" + pageIndex + "-" + UUID.randomUUID() + ".png");
            if (!ImageIO.write(pdImage.getImage(), "png", extracted.toFile())) return;
            images.add(new PdfImageRegion(pageIndex, Math.max(0, x), Math.max(0, y), width, height, extracted));
        }

        @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            resetPath(); rectanglePath = true;
            firstPoint = p0; currentPoint = p0;
            addLine(p0, p1); addLine(p1, p2); addLine(p2, p3); addLine(p3, p0);
            currentPoint = p0;
        }
        @Override public void clip(int windingRule) { }
        @Override public void moveTo(float x, float y) { currentPoint = new Point2D.Double(x, y); if (firstPoint == null) firstPoint = currentPoint; }
        @Override public void lineTo(float x, float y) { Point2D next = new Point2D.Double(x, y); if (currentPoint != null) addLine(currentPoint, next); currentPoint = next; }
        @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) { complexPath = true; currentPoint = new Point2D.Double(x3, y3); }
        @Override public Point2D getCurrentPoint() { return currentPoint; }
        @Override public void closePath() { if (currentPoint != null && firstPoint != null && currentPoint.distance(firstPoint) > .01) addLine(currentPoint, firstPoint); currentPoint = firstPoint; }
        @Override public void endPath() { resetPath(); }
        @Override public void strokePath() { paint(false, true); }
        @Override public void fillPath(int windingRule) { paint(true, false); }
        @Override public void fillAndStrokePath(int windingRule) { paint(true, true); }
        @Override public void shadingFill(COSName shadingName) { resetPath(); }

        private void addLine(Point2D a, Point2D b) {
            if (a == null || b == null) return;
            pathLines.add(new double[]{a.getX(), a.getY(), b.getX(), b.getY()});
        }

        private void paint(boolean filled, boolean stroked) {
            try {
                if (!collectVectors || complexPath || pathLines.isEmpty()) { resetPath(); return; }
                String fill = filled ? color(getGraphicsState().getNonStrokingColor(), "#FFFFFF") : "#FFFFFF";
                String stroke = stroked ? color(getGraphicsState().getStrokingColor(), "#172033") : fill;
                double width = Math.max(.5, getGraphicsState().getLineWidth());
                if (rectanglePath && pathLines.size() >= 4 && axisAligned(pathLines)) {
                    double[] bounds = bounds(pathLines);
                    if (bounds[2] >= 2 && bounds[3] >= 2) vectors.add(new VectorPrimitive("RECTANGLE", bounds[0], bounds[1], bounds[2], bounds[3], fill, stroke, width, filled, stroked));
                } else if (!filled) {
                    for (double[] line : pathLines) {
                        double[] top = lineToTop(line);
                        double w = Math.abs(top[2] - top[0]); double h = Math.abs(top[3] - top[1]);
                        if (Math.hypot(w, h) < 2) continue;
                        // Studio's line model is intentionally simple. Preserve business-form/table
                        // horizontal and vertical rules exactly; diagonal/rotated vectors fall back
                        // to Select / Hide Area rather than being reconstructed incorrectly.
                        if (w > 0.75 && h > 0.75) continue;
                        vectors.add(new VectorPrimitive("LINE", Math.min(top[0], top[2]), Math.min(top[1], top[3]), Math.max(1, w), Math.max(1, h), "#FFFFFF", stroke, width, false, true));
                    }
                }
            } catch (Exception ignored) {
                // Complex/unsupported color spaces remain safely editable through Select / Hide Area.
            } finally { resetPath(); }
        }


        private boolean axisAligned(List<double[]> lines) {
            for (double[] line : lines) {
                double[] top = lineToTop(line);
                double w = Math.abs(top[2] - top[0]);
                double h = Math.abs(top[3] - top[1]);
                if (w > 0.75 && h > 0.75) return false;
            }
            return true;
        }

        private double[] bounds(List<double[]> lines) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (double[] line : lines) {
                double[] top = lineToTop(line);
                minX = Math.min(minX, Math.min(top[0], top[2])); maxX = Math.max(maxX, Math.max(top[0], top[2]));
                minY = Math.min(minY, Math.min(top[1], top[3])); maxY = Math.max(maxY, Math.max(top[1], top[3]));
            }
            return new double[]{Math.max(0, minX), Math.max(0, minY), Math.max(1, maxX - minX), Math.max(1, maxY - minY)};
        }

        private double[] lineToTop(double[] line) {
            var crop = page.getCropBox();
            double x1 = line[0] - crop.getLowerLeftX();
            double y1 = crop.getUpperRightY() - line[1];
            double x2 = line[2] - crop.getLowerLeftX();
            double y2 = crop.getUpperRightY() - line[3];
            return new double[]{Math.max(0, x1), Math.max(0, y1), Math.max(0, x2), Math.max(0, y2)};
        }

        private String color(PDColor value, String fallback) throws IOException {
            if (value == null || value.getColorSpace() == null) return fallback;
            float[] rgb = value.getColorSpace().toRGB(value.getComponents());
            if (rgb == null || rgb.length < 3) return fallback;
            double max = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
            double factor = max <= 1.001 ? 255.0 : 1.0;
            int r = clampColor(Math.round((float) (rgb[0] * factor)));
            int g = clampColor(Math.round((float) (rgb[1] * factor)));
            int b = clampColor(Math.round((float) (rgb[2] * factor)));
            return String.format("#%02X%02X%02X", r, g, b);
        }

        private int clampColor(int v) { return Math.max(0, Math.min(255, v)); }
        private void resetPath() { pathLines.clear(); currentPoint = null; firstPoint = null; rectanglePath = false; complexPath = false; }
    }
}
