package org.example.server.documents;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

import java.nio.file.Files;
import java.nio.file.Path;

/** Adds a non-destructive lifecycle watermark after either Standard or Studio rendering. */
final class PdfLifecycleWatermark {
    private PdfLifecycleWatermark() {}

    static void apply(Path pdf, String text) throws Exception {
        if (text == null || text.isBlank()) return;
        Path temp = Files.createTempFile(pdf.getParent(), "sales-watermark-", ".pdf");
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDExtendedGraphicsState alpha = new PDExtendedGraphicsState();
            alpha.setNonStrokingAlphaConstant(0.16f);
            for (PDPage page : document.getPages()) {
                float width = page.getMediaBox().getWidth();
                float height = page.getMediaBox().getHeight();
                float fontSize = Math.min(48f, Math.max(28f, width / 11f));
                float textWidth = font.getStringWidth(text) / 1000f * fontSize;
                try (PDPageContentStream cs = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.saveGraphicsState();
                    cs.setGraphicsStateParameters(alpha);
                    cs.setNonStrokingColor(110, 110, 110);
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.newLineAtOffset(Math.max(18f, (width - textWidth) / 2f), height * 0.52f);
                    cs.showText(text);
                    cs.endText();
                    cs.restoreGraphicsState();
                }
            }
            document.save(temp.toFile());
        }
        Files.move(temp, pdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
