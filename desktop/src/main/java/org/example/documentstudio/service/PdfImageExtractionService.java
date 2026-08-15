package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.util.Matrix;
import org.example.documentstudio.model.PdfImageRegion;

import javax.imageio.ImageIO;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Detects raster image placements in an imported PDF and extracts preview copies for conversion. */
public final class PdfImageExtractionService {
    private PdfImageExtractionService() {}

    public static List<PdfImageRegion> extract(Path pdf, int pageIndex, Path tempFolder) throws IOException {
        if (pdf == null) return List.of();
        Files.createDirectories(tempFolder);
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) return List.of();
            PDPage page = document.getPage(pageIndex);
            ImageEngine engine = new ImageEngine(page, pageIndex, tempFolder);
            engine.run();
            return engine.regions();
        }
    }

    private static final class ImageEngine extends PDFGraphicsStreamEngine {
        private final PDPage page;
        private final int pageIndex;
        private final Path tempFolder;
        private final List<PdfImageRegion> regions = new ArrayList<>();

        private ImageEngine(PDPage page, int pageIndex, Path tempFolder) {
            super(page);
            this.page = page;
            this.pageIndex = pageIndex;
            this.tempFolder = tempFolder;
        }

        private List<PdfImageRegion> regions() { return regions; }

        private void run() throws IOException { processPage(page); }

        @Override
        public void drawImage(PDImage pdImage) throws IOException {
            if (pdImage == null) return;
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            double width = Math.abs(ctm.getScalingFactorX());
            double height = Math.abs(ctm.getScalingFactorY());
            if (width < 3 || height < 3) return;
            var crop = page.getCropBox();
            double x = ctm.getTranslateX() - crop.getLowerLeftX();
            double y = crop.getUpperRightY() - (ctm.getTranslateY() + height);
            Path extracted = tempFolder.resolve("pdf-image-" + pageIndex + "-" + UUID.randomUUID() + ".png");
            if (!ImageIO.write(pdImage.getImage(), "png", extracted.toFile())) return;
            regions.add(new PdfImageRegion(pageIndex, Math.max(0, x), Math.max(0, y), width, height, extracted));
        }

        @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) { }
        @Override public void clip(int windingRule) { }
        @Override public void moveTo(float x, float y) { }
        @Override public void lineTo(float x, float y) { }
        @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) { }
        @Override public Point2D getCurrentPoint() { return null; }
        @Override public void closePath() { }
        @Override public void endPath() { }
        @Override public void strokePath() { }
        @Override public void fillPath(int windingRule) { }
        @Override public void fillAndStrokePath(int windingRule) { }
        @Override public void shadingFill(COSName shadingName) { }
    }
}
