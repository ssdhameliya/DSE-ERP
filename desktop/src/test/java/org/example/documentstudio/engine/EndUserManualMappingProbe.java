package org.example.documentstudio.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.documentstudio.model.*;
import org.example.documentstudio.service.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class EndUserManualMappingProbe {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void inspectUserTemplateAndSourcePdf() throws Exception {
        Path templateDir = Path.of("D:/DSE Production/Templates/DocumentStudio/Pdf/579a7787-fa32-4e3f-a2c8-5945be4ac7e5");
        if (!Files.isDirectory(templateDir)) {
            System.out.println("Template directory does not exist: " + templateDir);
            return;
        }

        Path templateJson = templateDir.resolve("template.json");
        Path sourcePdf = templateDir.resolve("source.pdf");
        DocumentTemplate userTemplate = JSON.readValue(templateJson.toFile(), DocumentTemplate.class);
        System.out.println("=== 1. ALL ELEMENTS IN USER TEMPLATE (" + userTemplate.getElements().size() + ") ===");
        for (int i = 0; i < userTemplate.getElements().size(); i++) {
            TemplateElement el = userTemplate.getElements().get(i);
            System.out.printf("[%d] type=%s, fieldKey=%s, x=%.1f, y=%.1f, w=%.1f, h=%.1f, text='%s', mode=%s, repGroup='%s', repKey='%s'\n",
                    i, el.getType(), el.getFieldKey(), el.getX(), el.getY(), el.getWidth(), el.getHeight(),
                    el.getText(), el.getSourceReplacementMode(), el.getReplacementGroupId(), el.getReplacementSourceKey());
        }

        System.out.println("\n=== 2. SOURCE PDF INSPECTION ===");
        try (PDDocument doc = Loader.loadPDF(sourcePdf.toFile())) {
            System.out.println("Pages: " + doc.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                System.out.println("--- Page " + p + " Text ---");
                System.out.println(stripper.getText(doc));
            }
        }

        System.out.println("\n=== 3. TEXT REGIONS EXTRACTED BY STUDIO ===");
        List<PdfTextRegion> textRegions = PdfTextExtractionService.extract(sourcePdf, 0);
        System.out.println("Total detected text regions on Page 1: " + textRegions.size());
        for (int i = 0; i < Math.min(25, textRegions.size()); i++) {
            PdfTextRegion r = textRegions.get(i);
            System.out.printf("  [%d] x=%.1f, y=%.1f, w=%.1f, h=%.1f, text='%s', font='%s', size=%.1f\n",
                    i, r.x(), r.y(), r.width(), r.height(), r.text(), r.fontName(), r.fontSize());
        }

        System.out.println("\n=== 4. AUTO-MAPPING ANALYSIS ===");
        var analysis = PdfAutoMappingService.analyze(DocumentType.SALES_INVOICE, textRegions, null);
        System.out.println("Analyzed mappings: " + analysis.mappings().size());
        for (var c : analysis.mappings()) {
            System.out.printf("  Mapping field='%s' (score=%.2f) on text='%s' at (%.1f, %.1f)\n",
                    c.fieldKey(), c.confidence(), c.region().text(), c.region().x(), c.region().y());
        }

        System.out.println("\n=== 5. TABLE DETECTION ANALYSIS ===");
        var tableOpt = PdfSourceTableDetectionService.detectItemTable(sourcePdf, 0);
        if (tableOpt.isPresent()) {
            var d = tableOpt.get();
            var t = d.table();
            System.out.printf("Detected Table: x=%.1f, y=%.1f, w=%.1f, h=%.1f, columns=%d\n",
                    t.getX(), t.getY(), t.getWidth(), t.getHeight(), t.getTableColumns().size());
        } else {
            System.out.println("NO Table detected on Page 1 by PdfSourceTableDetectionService!");
        }

        System.out.println("\n=== 6. VECTOR REGIONS ON PAGE 1 ===");
        List<PdfImageExtractionService.VectorRegion> vectors = PdfImageExtractionService.extractVectors(sourcePdf, 0);
        System.out.println("Total vectors on page 0: " + vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            var v = vectors.get(i);
            System.out.printf("  [%d] kind=%s, x=%.1f, y=%.1f, w=%.1f, h=%.1f, prims=%d\n",
                    i, v.kind(), v.x(), v.y(), v.width(), v.height(), v.primitives().size());
        }
    }

    @Test
    void compareVisualPixels() throws Exception {
        File f1 = new File("D:/JavaProject/DSE-ERP-Final/desktop/target/pdf-comparison-evidence/STANDARD-SALES-5-page-1.png");
        File f2 = new File("D:/JavaProject/DSE-ERP-Final/desktop/target/pdf-comparison-evidence/STUDIO-SALES-5-page-1.png");
        if (!f1.exists() || !f2.exists()) {
            System.out.println("Images do not exist for comparison");
            return;
        }

        java.awt.image.BufferedImage img1 = javax.imageio.ImageIO.read(f1);
        java.awt.image.BufferedImage img2 = javax.imageio.ImageIO.read(f2);

        int w = Math.min(img1.getWidth(), img2.getWidth());
        int h = Math.min(img1.getHeight(), img2.getHeight());
        long diffPixels = 0;
        long totalPixels = (long) w * h;

        java.awt.image.BufferedImage diffImg = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb1 = img1.getRGB(x, y);
                int rgb2 = img2.getRGB(x, y);

                int r1 = (rgb1 >> 16) & 0xff, g1 = (rgb1 >> 8) & 0xff, b1 = rgb1 & 0xff;
                int r2 = (rgb2 >> 16) & 0xff, g2 = (rgb2 >> 8) & 0xff, b2 = rgb2 & 0xff;

                int diff = Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
                if (diff > 40) {
                    diffPixels++;
                    diffImg.setRGB(x, y, 0xff0000); // Red highlight for visual differences
                } else {
                    diffImg.setRGB(x, y, 0xf0f0f0); // Light gray for identical regions
                }
            }
        }

        File diffOut = new File("D:/JavaProject/DSE-ERP-Final/desktop/target/pdf-comparison-evidence/DIFF-SALES-5-page-1.png");
        javax.imageio.ImageIO.write(diffImg, "PNG", diffOut);

        double matchPercentage = 100.0 - ((double) diffPixels / totalPixels * 100.0);
        System.out.println("=== VISUAL PIXEL COMPARISON RESULTS ===");
        System.out.printf("Image Resolution: %d x %d (%d total pixels)\n", w, h, totalPixels);
        System.out.printf("Identical / Matching Pixels: %d (%.2f%%)\n", (totalPixels - diffPixels), matchPercentage);
        System.out.printf("Differing Pixels: %d (%.2f%%)\n", diffPixels, (100.0 - matchPercentage));
        System.out.println("Diff Image saved to: " + diffOut.getAbsolutePath());
    }

    @Test
    void renderUserTemplate() throws Exception {
        Path templateDir = Path.of("D:/DSE Production/Templates/DocumentStudio/Pdf/579a7787-fa32-4e3f-a2c8-5945be4ac7e5");
        if (!Files.isDirectory(templateDir)) {
            System.out.println("Template directory does not exist: " + templateDir);
            return;
        }

        DocumentTemplate template = JSON.readValue(templateDir.resolve("template.json").toFile(), DocumentTemplate.class);

        try (var scope = org.example.config.WorkspaceTestSupport.useTransientWorkspace(Path.of("target/test-ws").toAbsolutePath())) {
            org.example.config.ConfigManager.load();
            org.example.config.ConfigManager.setWithoutSaving("company.name", "Jasvi Industries");
            org.example.config.ConfigManager.setWithoutSaving("company.address", "H 52 Darshan Villa society, Bihand Darthi School, Near Gopal, New naroda, ahmedabad -382346 - Gujarat");
            org.example.config.ConfigManager.setWithoutSaving("company.gstin", "BEEPD4909N12345");
            org.example.config.ConfigManager.setWithoutSaving("payment.bankName", "State Bank of India");
            org.example.config.ConfigManager.setWithoutSaving("payment.branch", "Nikol");
            org.example.config.ConfigManager.setWithoutSaving("payment.accountNumber", "20104492473");
            org.example.config.ConfigManager.setWithoutSaving("payment.ifsc", "SBIN0000001");
            org.example.config.ConfigManager.setWithoutSaving("payment.accountType", "Savings");
            org.example.config.ConfigManager.setWithoutSaving("company.terms", "(1) All Prices are Nett-Godown.\n(2) Our responsibility ceases as soon as the goods leaves our godown.\n(3) Intrest @ 24% p.a. will be charged, if the payment not paidin due time.\n(4) Payments to be made by payess A/c Chque / DD / RTGS / NEFT Only");

            Path targetDir = org.example.config.WorkspaceManager.getTemplatesFolder()
                    .resolve("DocumentStudio").resolve("Pdf").resolve(template.getId());
            Files.createDirectories(targetDir);
            for (Path f : Files.list(templateDir).toList()) {
                if (Files.isRegularFile(f)) {
                    Files.copy(f, targetDir.resolve(f.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }

            List<TemplateElement> cleanedElements = new ArrayList<>();
            for (TemplateElement el : template.getElements()) {
                // Filter out bad/duplicate header mappings from old tests
                if ("document.poDate".equals(el.getFieldKey()) && el.getY() > 175) continue;
                if ("document.poNumber".equals(el.getFieldKey()) && el.getY() > 175) continue;
                if ("document.date".equals(el.getFieldKey()) && el.getY() < 170) continue;
                if ("document.orderNumber".equals(el.getFieldKey()) && el.getY() < 160) continue;
                if ("document.poNumber".equals(el.getFieldKey()) && el.getY() < 155) continue; // old accidental mapping at y=150
                if (el.getType() == ElementType.ITEM_TABLE) {
                    el.setUseSourceTableDesign(true);
                }
                cleanedElements.add(el);
            }
            template.setElements(cleanedElements);

            TemplateData data = TemplateDataFactory.sampleFor(template.getDocumentType());
            Path outPdf = Path.of("target/user-template-rendered.pdf");
            PdfStudioRenderer.render(template, data, outPdf);
            System.out.println("Rendered sample with PdfStudioRenderer successfully to: " + outPdf.toAbsolutePath());

            try (PDDocument doc = Loader.loadPDF(outPdf.toFile())) {
                org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
                for (int i = 0; i < doc.getNumberOfPages(); i++) {
                    java.awt.image.BufferedImage img = renderer.renderImageWithDPI(i, 150);
                    File pngOut = new File("C:/Users/JATIN DHAMELIYA/.gemini/antigravity-ide/brain/f6631471-5687-4712-af37-4878fd7e8d00/user_template_page_" + (i + 1) + ".png");
                    javax.imageio.ImageIO.write(img, "PNG", pngOut);
                    System.out.println("Saved rendered page " + (i + 1) + " to: " + pngOut.getAbsolutePath());
                }
            }
        }

        try (PDDocument doc = Loader.loadPDF(templateDir.resolve("original.pdf").toFile())) {
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                java.awt.image.BufferedImage img = renderer.renderImageWithDPI(i, 150);
                File pngOut = new File("C:/Users/JATIN DHAMELIYA/.gemini/antigravity-ide/brain/f6631471-5687-4712-af37-4878fd7e8d00/original_template_page_" + (i + 1) + ".png");
                javax.imageio.ImageIO.write(img, "PNG", pngOut);
                System.out.println("Saved original page " + (i + 1) + " to: " + pngOut.getAbsolutePath());
            }
        }

        try (PDDocument doc = Loader.loadPDF(templateDir.resolve("source.pdf").toFile())) {
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                java.awt.image.BufferedImage img = renderer.renderImageWithDPI(i, 150);
                File pngOut = new File("C:/Users/JATIN DHAMELIYA/.gemini/antigravity-ide/brain/f6631471-5687-4712-af37-4878fd7e8d00/source_template_page_" + (i + 1) + ".png");
                javax.imageio.ImageIO.write(img, "PNG", pngOut);
                System.out.println("Saved source page " + (i + 1) + " to: " + pngOut.getAbsolutePath());
            }

            // Diagnostic: Test suppressing sourceDoc and see what happens to the sourceDoc!
            PdfSourceTextSuppressionService.suppress(doc, template.getElements());
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                java.awt.image.BufferedImage img = renderer.renderImageWithDPI(i, 150);
                File pngOut = new File("C:/Users/JATIN DHAMELIYA/.gemini/antigravity-ide/brain/f6631471-5687-4712-af37-4878fd7e8d00/suppressed_source_page_" + (i + 1) + ".png");
                javax.imageio.ImageIO.write(img, "PNG", pngOut);
                System.out.println("Saved suppressed source page " + (i + 1) + " to: " + pngOut.getAbsolutePath());
            }
        }
    }
}

