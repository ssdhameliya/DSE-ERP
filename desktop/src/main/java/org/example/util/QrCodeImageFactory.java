package org.example.util;

import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.properties.HorizontalAlignment;
import javafx.scene.image.WritableImage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** Renders a standards-compliant QR code using PDF libraries already bundled with the desktop. */
public final class QrCodeImageFactory {
    private QrCodeImageFactory() {}

    public static javafx.scene.image.Image create(String value, int pixels) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("QR content is missing");
        int size = Math.max(180, pixels);
        try {
            ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
            try (PdfDocument pdf = new PdfDocument(new PdfWriter(pdfBytes));
                 Document doc = new Document(pdf, new PageSize(size, size))) {
                doc.setMargins(12, 12, 12, 12);
                BarcodeQRCode qr = new BarcodeQRCode(value);
                Image image = new Image(qr.createFormXObject(pdf))
                        .setWidth(size - 24).setHeight(size - 24)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                doc.add(image);
            }
            try (var pdf = Loader.loadPDF(pdfBytes.toByteArray())) {
                var buffered = new PDFRenderer(pdf).renderImageWithDPI(0, 120, ImageType.RGB);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(buffered, "png", png);
                return new javafx.scene.image.Image(new ByteArrayInputStream(png.toByteArray()), size, size, true, true);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to render authenticator QR code", exception);
        }
    }
}
