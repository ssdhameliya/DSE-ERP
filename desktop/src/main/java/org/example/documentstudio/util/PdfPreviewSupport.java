package org.example.documentstudio.util;

import javafx.scene.image.Image;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/** Lightweight PDF-to-JavaFX preview bridge without a javafx-swing dependency. */
public final class PdfPreviewSupport {
    private PdfPreviewSupport() {}

    public static Image renderPage(Path pdf, int pageIndex, float dpi) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            if (document.getNumberOfPages() == 0) throw new IOException("PDF contains no pages.");
            int index = Math.max(0, Math.min(pageIndex, document.getNumberOfPages() - 1));
            var image = new PDFRenderer(document).renderImageWithDPI(index, dpi, ImageType.RGB);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return new Image(new ByteArrayInputStream(bytes.toByteArray()));
        }
    }

    public static PageSize pageSize(Path pdf, int pageIndex) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            if (document.getNumberOfPages() == 0) throw new IOException("PDF contains no pages.");
            int index = Math.max(0, Math.min(pageIndex, document.getNumberOfPages() - 1));
            var box = document.getPage(index).getMediaBox();
            return new PageSize(box.getWidth(), box.getHeight(), document.getNumberOfPages());
        }
    }

    public record PageSize(double width, double height, int pageCount) {}
}
