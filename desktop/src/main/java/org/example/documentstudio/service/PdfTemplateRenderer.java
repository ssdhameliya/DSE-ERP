package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.example.documentstudio.model.*;
import org.example.invoice.calculation.InvoiceTaxCalculator;
import org.example.invoice.model.InvoiceTotals;
import org.example.invoice.model.TaxInvoiceCharge;
import org.example.invoice.model.TaxInvoiceItem;
import org.example.shared.DocumentCalculationEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Renders Document Studio templates while keeping the uploaded PDF as the
 * protected background. v8.2.2 adds map-first text fitting, source-aware masks,
 * explicit repeated-page rules and dynamic unlimited charge tables.
 */
public final class PdfTemplateRenderer {
    private static final PDFont HELVETICA = font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont HELVETICA_BOLD = font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont HELVETICA_OBLIQUE = font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    private static final PDFont HELVETICA_BOLD_OBLIQUE = font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE);
    private static final PDFont TIMES = font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final PDFont TIMES_BOLD = font(Standard14Fonts.FontName.TIMES_BOLD);
    private static final PDFont TIMES_ITALIC = font(Standard14Fonts.FontName.TIMES_ITALIC);
    private static final PDFont TIMES_BOLD_ITALIC = font(Standard14Fonts.FontName.TIMES_BOLD_ITALIC);
    private static final PDFont COURIER = font(Standard14Fonts.FontName.COURIER);
    private static final PDFont COURIER_BOLD = font(Standard14Fonts.FontName.COURIER_BOLD);
    private static final PDFont COURIER_OBLIQUE = font(Standard14Fonts.FontName.COURIER_OBLIQUE);
    private static final PDFont COURIER_BOLD_OBLIQUE = font(Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE);

    private PdfTemplateRenderer() {}

    private static PDFont font(Standard14Fonts.FontName name) { return new PDType1Font(name); }

    public static Path renderPurchase(DocumentTemplate template, org.example.model.Purchase purchase, Path output) throws IOException {
        return render(template, TemplateDataFactory.fromPurchase(purchase), output);
    }

    public static Path renderSample(DocumentTemplate template, Path output) throws IOException {
        return render(template, TemplateDataFactory.sampleFor(template == null ? DocumentType.GENERAL_PDF : template.getDocumentType()), output);
    }

    public static Path render(DocumentTemplate template, TemplateData data, Path output) throws IOException {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(output, "output");
        data = enrichPdfData(data);
        Path source = TemplateStorageService.sourcePdf(template);
        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);

        List<TemplateElement> elements = template.getElements();
        try (PDDocument sourceDoc = Loader.loadPDF(source.toFile()); PDDocument targetDoc = new PDDocument()) {
            if (sourceDoc.getNumberOfPages() == 0) throw new IOException("Template PDF has no pages.");

            Map<Integer, FlowPlan> plans = new HashMap<>();
            for (int sourceIndex = 0; sourceIndex < sourceDoc.getNumberOfPages(); sourceIndex++) {
                final int page = sourceIndex;
                List<TemplateElement> pageElements = elements.stream().filter(e -> PdfStyleResolver.effectivelyVisible(template,e)).filter(e -> e.getPageIndex() == page).toList();
                plans.put(sourceIndex, FlowPlan.forPage(pageElements, data));
            }

            Map<Integer, List<Integer>> sourceToOutputPages = new HashMap<>();
            LayerUtility layer = new LayerUtility(targetDoc);
            for (int sourceIndex = 0; sourceIndex < sourceDoc.getNumberOfPages(); sourceIndex++) {
                int copies = plans.get(sourceIndex).totalCopies();
                List<Integer> mapped = new ArrayList<>();
                for (int copy = 0; copy < copies; copy++) {
                    PDPage sourcePage = sourceDoc.getPage(sourceIndex);
                    PDRectangle box = sourcePage.getMediaBox();
                    PDPage targetPage = new PDPage(new PDRectangle(box.getWidth(), box.getHeight()));
                    targetPage.setRotation(sourcePage.getRotation());
                    targetDoc.addPage(targetPage);
                    mapped.add(targetDoc.getNumberOfPages() - 1);
                    PDFormXObject form = layer.importPageAsForm(sourceDoc, sourcePage);
                    try (PDPageContentStream cs = new PDPageContentStream(targetDoc, targetPage,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        cs.saveGraphicsState();
                        cs.transform(Matrix.getTranslateInstance(-box.getLowerLeftX(), -box.getLowerLeftY()));
                        cs.drawForm(form);
                        cs.restoreGraphicsState();
                    }
                }
                sourceToOutputPages.put(sourceIndex, mapped);
            }

            int totalPages = targetDoc.getNumberOfPages();
            for (int sourceIndex = 0; sourceIndex < sourceDoc.getNumberOfPages(); sourceIndex++) {
                final int page = sourceIndex;
                List<TemplateElement> pageElements = elements.stream().filter(e -> PdfStyleResolver.effectivelyVisible(template,e)).filter(e -> e.getPageIndex() == page).toList();
                List<Integer> outputPages = sourceToOutputPages.getOrDefault(sourceIndex, List.of());
                FlowPlan plan = plans.get(sourceIndex);
                for (int part = 0; part < outputPages.size(); part++) {
                    int outputIndex = outputPages.get(part);
                    PDPage outputPage = targetDoc.getPage(outputIndex);
                    List<TaxInvoiceItem> itemChunk = plan.itemChunk(data.items(), part);
                    List<TemplateCharge> chargeChunk = plan.chargeChunk(data.charges(), part);
                    try (PDPageContentStream cs = new PDPageContentStream(targetDoc, outputPage,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        for (TemplateElement e : pageElements) {
                            if (e.getType() == ElementType.ITEM_TABLE) {
                                if (plan.drawItemTable(part)) drawElement(targetDoc, outputPage, cs, template, data, e, itemChunk, chargeChunk, outputIndex + 1, totalPages);
                                continue;
                            }
                            if (e.getType() == ElementType.CHARGE_TABLE) {
                                if (plan.drawChargeTable(part)) drawElement(targetDoc, outputPage, cs, template, data, e, itemChunk, chargeChunk, outputIndex + 1, totalPages);
                                continue;
                            }
                            if (shouldDraw(e, plan, part)) {
                                drawElement(targetDoc, outputPage, cs, template, data, e, itemChunk, chargeChunk, outputIndex + 1, totalPages);
                            }
                        }
                    }
                }
            }
            targetDoc.save(output.toFile());
        }
        if (Files.size(output) < 100) throw new IOException("Template renderer produced an invalid PDF.");
        return output;
    }

    private static boolean shouldDraw(TemplateElement e, FlowPlan plan, int part) {
        if (plan.totalCopies() <= 1) return true;
        return switch (e.getPageRule()) {
            case "FIRST", "FIXED" -> part == 0;
            case "EVERY" -> true;
            case "CONTINUATION" -> part > 0;
            case "LAST" -> part == plan.totalCopies() - 1;
            default -> legacyAutoRule(e, plan.primaryTable(), part, plan.totalCopies());
        };
    }

    private static boolean legacyAutoRule(TemplateElement e, TemplateElement table, int part, int copies) {
        if (table == null || copies <= 1) return true;
        boolean above = e.getY() + e.getHeight() <= table.getY() + 2;
        boolean below = e.getY() >= table.getY() + table.getHeight() - 2;
        if (above) return true;
        if (below) return part == copies - 1;
        return part == 0;
    }

    private static int rowsPerPage(TemplateElement table) {
        double usable = Math.max(table.getRowHeight(), table.getHeight() - Math.max(0, table.getHeaderHeight()));
        return Math.max(1, (int) Math.floor(usable / table.getRowHeight()));
    }

    private static int requiredPages(TemplateElement table, int count) {
        if (table == null || count <= 0) return 1;
        return Math.max(1, (int) Math.ceil(count / (double) rowsPerPage(table)));
    }

    private static void drawElement(PDDocument doc, PDPage page, PDPageContentStream cs,
                                    DocumentTemplate template, TemplateData data, TemplateElement e,
                                    List<TaxInvoiceItem> tableItems, List<TemplateCharge> tableCharges,
                                    int pageNumber, int totalPages) throws IOException {
        TemplateElement draw = PdfStyleResolver.effective(template, e);
        boolean transformable = draw.getType() != ElementType.WHITEOUT;
        if (transformable) {
            cs.saveGraphicsState();
            if (draw.getOpacity() < 0.999) {
                PDExtendedGraphicsState state = new PDExtendedGraphicsState();
                state.setNonStrokingAlphaConstant((float) draw.getOpacity());
                state.setStrokingAlphaConstant((float) draw.getOpacity());
                cs.setGraphicsStateParameters(state);
            }
            if (Math.abs(draw.getRotation()) > 0.01) {
                float centerX = (float) (draw.getX() + draw.getWidth() / 2.0);
                float centerY = toPdfY(page, draw.getY() + draw.getHeight() / 2.0);
                cs.transform(Matrix.getTranslateInstance(centerX, centerY));
                cs.transform(Matrix.getRotateInstance(Math.toRadians(draw.getRotation()), 0, 0));
                cs.transform(Matrix.getTranslateInstance(-centerX, -centerY));
            }
        }
        try {
            switch (draw.getType()) {
                case TEXT -> drawText(page, cs, draw, resolveExpression(draw.getText(), data, pageNumber, totalPages));
                case FIELD -> drawText(page, cs, draw, fieldValue(data, draw.getFieldKey(), pageNumber, totalPages));
                case IMAGE -> drawImage(doc, page, cs, draw, TemplateStorageService.resolveAsset(template, draw.getImagePath()));
                case IMAGE_FIELD -> drawImage(doc, page, cs, draw, data.image(draw.getFieldKey()));
                case RECTANGLE, BLOCK -> drawRectangle(page, cs, draw, false);
                case WHITEOUT -> drawRectangle(page, cs, draw, true);
                case LINE -> drawLine(page, cs, draw);
                case PATH -> drawPath(page, cs, draw);
                case ITEM_TABLE -> drawItemTable(page, cs, draw, tableItems == null ? List.of() : tableItems, data.gstType());
                case CHARGE_TABLE -> drawChargeTable(page, cs, draw, tableCharges == null ? List.of() : tableCharges, data.gstType());
            }
        } finally {
            if (transformable) cs.restoreGraphicsState();
        }
    }

    private static String fieldValue(TemplateData data, String key, int pageNumber, int totalPages) {
        if ("document.pageNumber".equals(key)) return Integer.toString(pageNumber);
        if ("document.totalPages".equals(key)) return Integer.toString(totalPages);
        if (key != null && key.startsWith("item.") && !data.items().isEmpty())
            return itemValue(key, data.items().getFirst(), data.gstType(), 1);
        if (key != null && key.startsWith("charge.") && !data.charges().isEmpty())
            return chargeValue(key, data.charges().getFirst(), data.gstType(), 1);
        return data.value(key);
    }

    /**
     * PDF-specific calculated values.  The PDF renderer uses the same shared invoice calculator
     * as the business-document flows but keeps these additions local to PDF Studio so Excel Studio
     * remains byte-for-byte independent of this editor redesign.
     */
    private static TemplateData enrichPdfData(TemplateData data) {
        if (data == null) return new TemplateData(Map.of(), Map.of(), List.of(), List.of(), "");
        Map<String,String> values = new LinkedHashMap<>(data.values());
        if (!data.items().isEmpty()) {
            try {
                List<TaxInvoiceCharge> charges = data.charges().stream()
                        .filter(Objects::nonNull)
                        .map(c -> new TaxInvoiceCharge(c.type(), c.amount(), c.taxable(), c.gstPercent()))
                        .toList();
                InvoiceTotals totals = InvoiceTaxCalculator.calculate(data.items(), charges, data.gstType());
                double chargeAmount = 0, chargeTax = 0, chargeTotal = 0;
                for (TemplateCharge charge : data.charges()) {
                    if (charge == null) continue;
                    DocumentCalculationEngine.ChargeResult result = DocumentCalculationEngine.charge(
                            charge.amount(), charge.taxable(), charge.gstPercent());
                    chargeAmount += result.amount();
                    chargeTax += result.taxAmount();
                    chargeTotal += result.totalAmount();
                }
                double tax = DocumentCalculationEngine.money(totals.cgst() + totals.sgst() + totals.igst());
                double preRound = DocumentCalculationEngine.money(totals.grandTotal() - totals.roundOff());
                double grossBeforeTax = DocumentCalculationEngine.money(
                        totals.basicAmount() - totals.discountAmount() + totals.chargesAmount());
                values.put("totals.cgstAmount", money(totals.cgst()));
                values.put("totals.sgstAmount", money(totals.sgst()));
                values.put("totals.igstAmount", money(totals.igst()));
                values.put("totals.gstAmount", money(tax));
                values.put("totals.chargesAmount", money(DocumentCalculationEngine.money(chargeAmount)));
                values.put("totals.chargeTaxAmount", money(DocumentCalculationEngine.money(chargeTax)));
                values.put("totals.chargesTotal", money(DocumentCalculationEngine.money(chargeTotal)));
                values.put("totals.grossBeforeTax", money(grossBeforeTax));
                values.put("totals.preRoundTotal", money(preRound));
                values.put("totals.roundOff", money(totals.roundOff()));
                values.put("totals.roundedGrandTotal", money(totals.grandTotal()));
            } catch (Exception ignored) {
                // Keep the original ERP values if a legacy/non-invoice TemplateData cannot be recalculated.
            }
        }
        return new TemplateData(values, data.images(), data.items(), data.charges(), data.gstType());
    }

    /** Resolve literal text mixed with {{erp.field}} expressions. */
    private static String resolveExpression(String text, TemplateData data, int pageNumber, int totalPages) {
        if (text == null || text.isBlank()) return text == null ? "" : text;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}")
                .matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String value = fieldValue(data, matcher.group(1), pageNumber, totalPages);
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static void drawText(PDPage page, PDPageContentStream cs, TemplateElement e, String value) throws IOException {
        String text = safePdfText(value);
        if (e.isFillEnabled() || e.isStrokeEnabled()) drawRectangle(page, cs, e, false);
        if (text.isBlank()) return;
        PDFont font = fontFor(e);
        float configuredSize = (float) e.getFontSize();
        float x = (float) (e.getX() + e.getPaddingLeft());
        float topY = (float) (e.getY() + e.getPaddingTop());
        float width = (float) Math.max(1, e.getWidth() - e.getPaddingLeft() - e.getPaddingRight());
        float height = (float) Math.max(1, e.getHeight() - e.getPaddingTop() - e.getPaddingBottom());
        float top = toPdfY(page, topY);
        String mode = e.getTextFit();

        if ("SHRINK".equals(mode) && !text.contains("\n")) {
            float size = shrinkToFit(text, font, configuredSize, width, height);
            drawSingleLine(cs, font, size, text, x, top - size, width, e.getTextAlignment(), e.getTextColor());
            return;
        }
        if ("CLIP".equals(mode)) {
            cs.saveGraphicsState();
            try {
                cs.addRect(x, toPdfY(page, topY + height), width, height);
                cs.clip();
                drawSingleLine(cs, font, configuredSize, text.replace('\n', ' '), x, top - configuredSize, width, e.getTextAlignment(), e.getTextColor());
            } finally { cs.restoreGraphicsState(); }
            return;
        }
        if ("FIXED".equals(mode)) {
            drawSingleLine(cs, font, configuredSize, text.replace('\n', ' '), x, top - configuredSize, width, e.getTextAlignment(), e.getTextColor());
            return;
        }

        float lineHeight = configuredSize * (float)Math.max(.5, e.getLineSpacing());
        List<String> lines = wrap(text, font, configuredSize, width);
        setNonStroke(cs, e.getTextColor());
        float y = top - configuredSize;
        float bottom = top - height;
        for (String line : lines) {
            if (y < bottom) break;
            float drawX = alignedX(font, configuredSize, line, x, width, e.getTextAlignment());
            cs.beginText(); cs.setFont(font, configuredSize); cs.newLineAtOffset(drawX, y); cs.showText(line); cs.endText();
            y -= lineHeight;
        }
    }

    private static float shrinkToFit(String text, PDFont font, float configured, float width, float height) throws IOException {
        float size = Math.max(5f, configured);
        float maxByHeight = Math.max(5f, height * .88f);
        size = Math.min(size, maxByHeight);
        float natural = textWidth(font, size, text);
        if (natural > width && natural > 0) size = Math.max(5f, size * width / natural);
        return size;
    }

    private static void drawSingleLine(PDPageContentStream cs, PDFont font, float size, String text,
                                       float x, float y, float width, String alignment, String color) throws IOException {
        setNonStroke(cs, color);
        float drawX = alignedX(font, size, text, x, width, alignment);
        cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(drawX, y); cs.showText(text); cs.endText();
    }

    private static float alignedX(PDFont font, float size, String text, float x, float width, String alignment) throws IOException {
        float tw = textWidth(font, size, text);
        if ("RIGHT".equals(alignment)) return x + Math.max(0, width - tw);
        if ("CENTER".equals(alignment)) return x + Math.max(0, (width - tw) / 2f);
        return x;
    }

    private static float textWidth(PDFont font, float size, String text) throws IOException {
        return font.getStringWidth(text == null ? "" : text) / 1000f * size;
    }

    private static PDFont fontFor(TemplateElement e) {
        String family = e.getFontFamily();
        boolean bold = e.isBold(), italic = e.isItalic();
        return switch (family) {
            case "TIMES" -> bold && italic ? TIMES_BOLD_ITALIC : bold ? TIMES_BOLD : italic ? TIMES_ITALIC : TIMES;
            case "COURIER" -> bold && italic ? COURIER_BOLD_OBLIQUE : bold ? COURIER_BOLD : italic ? COURIER_OBLIQUE : COURIER;
            default -> bold && italic ? HELVETICA_BOLD_OBLIQUE : bold ? HELVETICA_BOLD : italic ? HELVETICA_OBLIQUE : HELVETICA;
        };
    }

    private static void drawRectangle(PDPage page, PDPageContentStream cs, TemplateElement e, boolean replacementMask) throws IOException {
        float x = (float) e.getX();
        float y = toPdfY(page, e.getY() + e.getHeight());
        float w = (float) e.getWidth();
        float h = (float) e.getHeight();
        boolean fill = replacementMask || e.isFillEnabled();
        boolean stroke = !replacementMask && e.isStrokeEnabled() && e.getStrokeWidth() > 0;
        if (!fill && !stroke) return;
        if (fill) setNonStroke(cs, e.getFillColor());
        if (stroke) { setStroke(cs, e.getStrokeColor()); cs.setLineWidth((float)e.getStrokeWidth()); }
        float radius = (float)Math.min(Math.max(0, e.getBorderRadius()), Math.min(w,h)/2f);
        if (radius > .1f) roundedRect(cs, x, y, w, h, radius);
        else cs.addRect(x,y,w,h);
        if (fill && stroke) cs.fillAndStroke();
        else if (fill) cs.fill();
        else cs.stroke();
    }

    private static void roundedRect(PDPageContentStream cs, float x, float y, float w, float h, float r) throws IOException {
        float k = .55228475f, c = r * k;
        cs.moveTo(x+r,y);
        cs.lineTo(x+w-r,y); cs.curveTo(x+w-r+c,y,x+w,y+r-c,x+w,y+r);
        cs.lineTo(x+w,y+h-r); cs.curveTo(x+w,y+h-r+c,x+w-r+c,y+h,x+w-r,y+h);
        cs.lineTo(x+r,y+h); cs.curveTo(x+r-c,y+h,x,y+h-r+c,x,y+h-r);
        cs.lineTo(x,y+r); cs.curveTo(x,y+r-c,x+r-c,y,x+r,y);
        cs.closePath();
    }

    private static void drawLine(PDPage page, PDPageContentStream cs, TemplateElement e) throws IOException {
        if (!e.isStrokeEnabled()) return;
        setStroke(cs, e.getStrokeColor());
        cs.setLineWidth((float) Math.max(0.5, e.getStrokeWidth()));
        float x1 = (float) e.getX(), y1 = toPdfY(page, e.getY());
        float x2 = (float) (e.getX() + (e.getWidth() <= 1.001 ? 0 : e.getWidth()));
        float y2 = toPdfY(page, e.getY() + (e.getHeight() <= 1.001 ? 0 : e.getHeight()));
        cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke();
    }

    private static void drawPath(PDPage page, PDPageContentStream cs, TemplateElement e) throws IOException {
        if (e.getPathCommands() == null || e.getPathCommands().isEmpty()) return;
        for (PathCommand command : e.getPathCommands()) {
            switch (command.getType()) {
                case "M" -> cs.moveTo(pathX(e, command.getX1()), pathY(page, e, command.getY1()));
                case "L" -> cs.lineTo(pathX(e, command.getX1()), pathY(page, e, command.getY1()));
                case "C" -> cs.curveTo(pathX(e, command.getX1()), pathY(page, e, command.getY1()),
                        pathX(e, command.getX2()), pathY(page, e, command.getY2()),
                        pathX(e, command.getX3()), pathY(page, e, command.getY3()));
                case "Z" -> cs.closePath();
                default -> { }
            }
        }
        boolean fill = e.isFillEnabled() && e.isPathFilled();
        boolean stroke = e.isStrokeEnabled() && e.isPathStroked();
        if (fill) setNonStroke(cs, e.getFillColor());
        if (stroke) { setStroke(cs, e.getStrokeColor()); cs.setLineWidth((float) Math.max(.5, e.getStrokeWidth())); }
        if (fill && stroke) cs.fillAndStroke();
        else if (fill) cs.fill();
        else if (stroke) cs.stroke();
    }

    private static float pathX(TemplateElement e, double normalized) { return (float) (e.getX() + normalized * e.getWidth()); }
    private static float pathY(PDPage page, TemplateElement e, double normalized) { return toPdfY(page, e.getY() + normalized * e.getHeight()); }

    private static void drawImage(PDDocument doc, PDPage page, PDPageContentStream cs, TemplateElement e, Path imagePath) throws IOException {
        if (e.isFillEnabled() || e.isStrokeEnabled()) drawRectangle(page, cs, e, false);
        if (imagePath == null || !Files.isRegularFile(imagePath)) return;
        PDImageXObject image = PDImageXObject.createFromFileByContent(imagePath.toFile(), doc);
        float boxX = (float) (e.getX() + e.getPaddingLeft());
        float boxTop = (float) (e.getY() + e.getPaddingTop());
        float boxW = (float) Math.max(1, e.getWidth() - e.getPaddingLeft() - e.getPaddingRight());
        float boxH = (float) Math.max(1, e.getHeight() - e.getPaddingTop() - e.getPaddingBottom());
        float boxY = toPdfY(page, boxTop + boxH);
        float drawW = boxW, drawH = boxH;
        String fit = e.getImageFit();
        if (!"STRETCH".equals(fit) || e.isPreserveAspectRatio()) {
            float iw = Math.max(1, image.getWidth()), ih = Math.max(1, image.getHeight());
            float sx = boxW / iw, sy = boxH / ih;
            float factor = "FILL".equals(fit) ? Math.max(sx, sy) : Math.min(sx, sy);
            drawW = iw * factor; drawH = ih * factor;
        }
        float drawX = boxX + (boxW - drawW) / 2f, drawY = boxY + (boxH - drawH) / 2f;
        cs.saveGraphicsState();
        try {
            if ("FILL".equals(fit)) { cs.addRect(boxX, boxY, boxW, boxH); cs.clip(); }
            cs.drawImage(image, drawX, drawY, drawW, drawH);
        } finally { cs.restoreGraphicsState(); }
    }

    private static void drawItemTable(PDPage page, PDPageContentStream cs, TemplateElement e,
                                      List<TaxInvoiceItem> items, String gstType) throws IOException {
        List<Column> columns = itemColumns(e.getTableColumns());
        if (columns.isEmpty()) columns = itemColumns(List.of("serial", "descriptionWithRemarks", "quantity", "rate", "total"));
        drawTableScaffold(page, cs, e, columns);
        int count = Math.min(rowsPerPage(e), items.size());
        for (int r = 0; r < count; r++) drawItemRow(page, cs, e, columns, r, items.get(r), gstType);
    }

    private static void drawChargeTable(PDPage page, PDPageContentStream cs, TemplateElement e,
                                        List<TemplateCharge> charges, String gstType) throws IOException {
        List<Column> columns = chargeColumns(e.getTableColumns());
        if (columns.isEmpty()) columns = chargeColumns(List.of("type", "amount", "gstPercent", "taxAmount", "total"));
        drawTableScaffold(page, cs, e, columns);
        int count = Math.min(rowsPerPage(e), charges.size());
        for (int r = 0; r < count; r++) drawChargeRow(page, cs, e, columns, r, charges.get(r), gstType);
    }

    private static void drawTableScaffold(PDPage page, PDPageContentStream cs, TemplateElement e, List<Column> columns) throws IOException {
        if (e.isUseSourceTableDesign()) return;
        float x = (float) e.getX(), top = toPdfY(page, e.getY()), width = (float) e.getWidth();
        float headerH = (float) e.getHeaderHeight(), totalWeight = totalWeight(columns);
        setNonStroke(cs, "#EEF4FF"); cs.addRect(x, top - headerH, width, headerH); cs.fill();
        setStroke(cs, "#9FB3C8"); cs.setLineWidth(0.65f); cs.addRect(x, top - (float)e.getHeight(), width, (float)e.getHeight()); cs.stroke();
        float cursorX = x;
        for (Column column : columns) {
            float cw = width * (float) column.weight() / totalWeight;
            drawCellText(cs, HELVETICA_BOLD, 7.4f, column.label(), cursorX + 3, top - headerH + 7, cw - 6, Math.max(5, headerH - 5), "#24364B");
            cursorX += cw;
            if (cursorX < x + width - .5f) { cs.moveTo(cursorX, top); cs.lineTo(cursorX, top - (float)e.getHeight()); cs.stroke(); }
        }
        cs.moveTo(x, top - headerH); cs.lineTo(x + width, top - headerH); cs.stroke();
    }

    private static void drawItemRow(PDPage page, PDPageContentStream cs, TemplateElement e, List<Column> columns,
                                    int row, TaxInvoiceItem item, String gstType) throws IOException {
        drawDataRow(page, cs, e, columns, row, key -> itemValue(key, item, gstType, item.getSerialNo() > 0 ? item.getSerialNo() : row + 1));
    }

    private static void drawChargeRow(PDPage page, PDPageContentStream cs, TemplateElement e, List<Column> columns,
                                      int row, TemplateCharge charge, String gstType) throws IOException {
        drawDataRow(page, cs, e, columns, row, key -> chargeValue(key, charge, gstType, row + 1));
    }

    private static void drawDataRow(PDPage page, PDPageContentStream cs, TemplateElement e, List<Column> columns,
                                    int row, java.util.function.Function<String,String> value) throws IOException {
        float x = (float)e.getX(), top = toPdfY(page, e.getY()), width = (float)e.getWidth();
        float headerH = (float)Math.max(0, e.getHeaderHeight()), rowH = (float)e.getRowHeight();
        float rowTop = top - headerH - row * rowH, rowBottom = rowTop - rowH;
        float totalWeight = totalWeight(columns), cursorX = x;
        if (!e.isUseSourceTableDesign()) {
            setStroke(cs, "#9FB3C8"); cs.setLineWidth(.65f); cs.moveTo(x, rowBottom); cs.lineTo(x + width, rowBottom); cs.stroke();
        }
        PDFont font = fontFor(e);
        float fontSize = (float)Math.max(5, Math.min(e.getFontSize(), rowH * .58));
        for (Column column : columns) {
            float cw = width * (float)column.weight() / totalWeight;
            drawCellText(cs, font, fontSize, value.apply(column.key()), cursorX + 3, rowBottom + 2, Math.max(4, cw - 6), Math.max(4, rowH - 3), e.getTextColor());
            cursorX += cw;
        }
    }

    private static float totalWeight(List<Column> columns) { return (float)columns.stream().mapToDouble(Column::weight).sum(); }

    private static String itemValue(String key, TaxInvoiceItem item, String gstType, int serial) {
        if (item == null) return "";
        String k = key == null ? "" : key.replaceFirst("^item\\.", "");
        // Backward-compatible column aliases from legacy PDF templates.
        k = switch (k) { case "qty" -> "quantity"; case "discount" -> "discountPercent"; case "gst" -> "gstPercent"; case "amount" -> "total"; default -> k; };
        DocumentCalculationEngine.LineResult result = DocumentCalculationEngine.line(
                item.getQuantity(), item.getRate(), item.getDiscountPercent(), item.getGstPercent());
        TaxSplit split = taxSplit(item.getGstPercent(), result.taxAmount(), gstType);
        return switch (k) {
            case "serial" -> Integer.toString(serial);
            case "code" -> safe(item.getItemCode());
            case "hsn" -> safe(item.getHsn());
            case "description" -> safe(item.getDescription());
            case "descriptionWithRemarks" -> descriptionWithRemarks(item.getDescription(), item.getRemarks());
            case "remarks" -> safe(item.getRemarks());
            case "category" -> safe(item.getCategory());
            case "brand" -> safe(item.getBrand());
            case "material" -> safe(item.getMaterial());
            case "size" -> safe(item.getSize());
            case "quantity" -> number(item.getQuantity());
            case "unit" -> safe(item.getUnit());
            case "rate" -> money(item.getRate());
            case "discountPercent" -> number(item.getDiscountPercent());
            case "discountAmount" -> money(result.discountAmount());
            case "taxable" -> money(result.taxableAmount());
            case "gstPercent" -> number(item.getGstPercent());
            case "gstAmount" -> money(result.taxAmount());
            case "cgstPercent" -> number(split.cgstPercent());
            case "cgstAmount" -> money(split.cgstAmount());
            case "sgstPercent" -> number(split.sgstPercent());
            case "sgstAmount" -> money(split.sgstAmount());
            case "igstPercent" -> number(split.igstPercent());
            case "igstAmount" -> money(split.igstAmount());
            case "total" -> money(result.totalAmount());
            case "location" -> safe(item.getLocation());
            case "purchasePrice" -> money(item.getPurchasePrice());
            case "sellingPrice" -> money(item.getSellingPrice());
            case "availableStock" -> number(item.getAvailableStock());
            case "openingStock" -> number(item.getOpeningStock());
            case "minimumStock" -> number(item.getMinimumStock());
            case "reservedStock" -> number(item.getReservedStock());
            case "masterGstPercent" -> number(item.getMasterGstPercent());
            case "masterDiscountPercent" -> number(item.getMasterDiscountPercent());
            default -> "";
        };
    }

    private static String chargeValue(String key, TemplateCharge charge, String gstType, int serial) {
        if (charge == null) return "";
        String k = key == null ? "" : key.replaceFirst("^charge\\.", "");
        DocumentCalculationEngine.ChargeResult result = DocumentCalculationEngine.charge(
                charge.amount(), charge.taxable(), charge.gstPercent());
        TaxSplit split = taxSplit(charge.gstPercent(), result.taxAmount(), gstType);
        return switch (k) {
            case "serial" -> Integer.toString(serial);
            case "type" -> safe(charge.type());
            case "amount" -> money(result.amount());
            case "taxable" -> charge.taxable() ? "Yes" : "No";
            case "taxableAmount" -> money(result.taxableAmount());
            case "gstPercent" -> number(charge.taxable() ? charge.gstPercent() : 0);
            case "taxAmount" -> money(result.taxAmount());
            case "cgstPercent" -> number(split.cgstPercent());
            case "cgstAmount" -> money(split.cgstAmount());
            case "sgstPercent" -> number(split.sgstPercent());
            case "sgstAmount" -> money(split.sgstAmount());
            case "igstPercent" -> number(split.igstPercent());
            case "igstAmount" -> money(split.igstAmount());
            case "total" -> money(result.totalAmount());
            default -> "";
        };
    }

    private record TaxSplit(double cgstPercent, double cgstAmount, double sgstPercent, double sgstAmount, double igstPercent, double igstAmount) { }

    private static TaxSplit taxSplit(double gstPercent, double taxAmount, String gstType) {
        double rate = DocumentCalculationEngine.percent(gstPercent);
        double tax = DocumentCalculationEngine.money(taxAmount);
        if (DocumentCalculationEngine.taxMode(gstType) == DocumentCalculationEngine.TaxMode.IGST)
            return new TaxSplit(0, 0, 0, 0, rate, tax);
        double cgstRate = rate / 2d, sgstRate = rate - cgstRate;
        double cgst = DocumentCalculationEngine.money(tax / 2d), sgst = DocumentCalculationEngine.money(tax - cgst);
        return new TaxSplit(cgstRate, cgst, sgstRate, sgst, 0, 0);
    }

    private static String descriptionWithRemarks(String description, String remarks) {
        String d = safe(description), r = safe(remarks);
        if (r.isBlank()) return d;
        if (d.isBlank()) return r;
        return d + "\n" + r;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static List<Column> itemColumns(List<String> keys) {
        List<Column> all = List.of(
                new Column("serial", "Sr", .55), new Column("code", "Item Code", .95), new Column("hsn", "HSN", .85),
                new Column("description", "Description", 3.2), new Column("descriptionWithRemarks", "Description / Remarks", 3.6),
                new Column("remarks", "Remarks", 1.6), new Column("category", "Category", 1.0), new Column("brand", "Brand", 1.0),
                new Column("material", "Material", 1.0), new Column("size", "Size", .8),
                new Column("quantity", "Qty", .75), new Column("unit", "Unit", .7), new Column("rate", "Rate", 1.15),
                new Column("discountPercent", "Disc %", .8), new Column("discountAmount", "Discount", 1.0),
                new Column("taxable", "Taxable", 1.2), new Column("gstPercent", "GST %", .8), new Column("gstAmount", "GST", 1.0),
                new Column("cgstPercent", "CGST %", .8), new Column("cgstAmount", "CGST", 1.0),
                new Column("sgstPercent", "SGST %", .8), new Column("sgstAmount", "SGST", 1.0),
                new Column("igstPercent", "IGST %", .8), new Column("igstAmount", "IGST", 1.0), new Column("total", "Total", 1.3),
                new Column("location", "Location", 1.0), new Column("purchasePrice", "Purchase Price", 1.1),
                new Column("sellingPrice", "Selling Price", 1.1), new Column("availableStock", "Available", .9),
                new Column("openingStock", "Opening", .9), new Column("minimumStock", "Minimum", .9),
                new Column("reservedStock", "Reserved", .9), new Column("masterGstPercent", "Master GST %", .9),
                new Column("masterDiscountPercent", "Master Disc %", .9));
        return chooseColumnsWithAliases(all, keys, true);
    }

    private static List<Column> chargeColumns(List<String> keys) {
        List<Column> all = List.of(
                new Column("serial", "Sr", .55), new Column("type", "Charge", 2.5), new Column("amount", "Amount", 1.2),
                new Column("taxable", "Taxable", .9), new Column("taxableAmount", "Taxable Amount", 1.15),
                new Column("gstPercent", "GST %", .9), new Column("taxAmount", "Tax", 1.1),
                new Column("cgstPercent", "CGST %", .85), new Column("cgstAmount", "CGST", 1.0),
                new Column("sgstPercent", "SGST %", .85), new Column("sgstAmount", "SGST", 1.0),
                new Column("igstPercent", "IGST %", .85), new Column("igstAmount", "IGST", 1.0),
                new Column("total", "Total", 1.25));
        return chooseColumnsWithAliases(all, keys, false);
    }

    private static List<Column> chooseColumnsWithAliases(List<Column> all, List<String> keys, boolean item) {
        List<String> normalized = new ArrayList<>();
        for (String raw : keys == null ? List.<String>of() : keys) {
            if (raw == null) continue;
            String key = raw.trim().replaceFirst(item ? "^item\\." : "^charge\\.", "");
            if (item) key = switch (key) { case "qty" -> "quantity"; case "discount" -> "discountPercent"; case "gst" -> "gstPercent"; case "amount" -> "total"; default -> key; };
            if (!key.isBlank()) normalized.add(key);
        }
        return chooseColumns(all, normalized);
    }

    private static List<Column> chooseColumns(List<Column> all, List<String> keys) {
        Set<String> wanted = new LinkedHashSet<>(keys == null ? List.of() : keys);
        return all.stream().filter(c -> wanted.contains(c.key())).toList();
    }

    private static void drawCellText(PDPageContentStream cs, PDFont font, float fontSize,
                                     String text, float x, float y, float width, float height, String color) throws IOException {
        setNonStroke(cs, color);
        List<String> lines = wrap(safePdfText(text), font, fontSize, Math.max(5, width));
        float lineHeight = fontSize * 1.12f, cy = y + height - fontSize;
        for (String line : lines) {
            if (cy < y) break;
            cs.beginText(); cs.setFont(font, fontSize); cs.newLineAtOffset(x, cy); cs.showText(line); cs.endText();
            cy -= lineHeight;
        }
    }

    private static List<String> wrap(String text, PDFont font, float fontSize, float width) throws IOException {
        if (text == null || text.isEmpty()) return List.of("");
        List<String> result = new ArrayList<>();
        for (String paragraph : text.replace('\r', '\n').split("\\n")) {
            if (paragraph.isBlank()) { result.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (textWidth(font, fontSize, candidate) <= width || line.isEmpty()) {
                    line.setLength(0); line.append(candidate);
                } else {
                    result.add(line.toString()); line.setLength(0); line.append(word);
                }
            }
            if (!line.isEmpty()) result.add(line.toString());
        }
        return result;
    }

    private static float toPdfY(PDPage page, double topLeftY) { return page.getMediaBox().getHeight() - (float)topLeftY; }
    private static void setNonStroke(PDPageContentStream cs, String hex) throws IOException { cs.setNonStrokingColor(awtColor(hex)); }
    private static void setStroke(PDPageContentStream cs, String hex) throws IOException { cs.setStrokingColor(awtColor(hex)); }

    private static java.awt.Color awtColor(String hex) {
        String h = hex == null || !hex.matches("#[0-9a-fA-F]{6}") ? "#172033" : hex;
        return new java.awt.Color(Integer.parseInt(h.substring(1,3),16), Integer.parseInt(h.substring(3,5),16), Integer.parseInt(h.substring(5,7),16));
    }

    private static String safePdfText(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '\n' || c == '\r' || c == '\t') out.append(c == '\t' ? ' ' : c);
            else if (c >= 32 && c <= 126) out.append(c);
            else if (c == '\u20b9') out.append("Rs.");
            else out.append('?');
        }
        return out.toString();
    }

    private static String money(double value) { return String.format(Locale.ENGLISH, "%,.2f", value); }
    private static String number(double value) {
        if (Math.rint(value) == value) return Long.toString(Math.round(value));
        return String.format(Locale.ENGLISH, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private record Column(String key, String label, double weight) {}

    private record FlowPlan(TemplateElement itemTable, TemplateElement chargeTable,
                            int itemPages, int chargePages, int totalCopies, int chargeStartPart) {
        static FlowPlan forPage(List<TemplateElement> elements, TemplateData data) {
            TemplateElement item = elements.stream().filter(e -> e.getType() == ElementType.ITEM_TABLE).findFirst().orElse(null);
            TemplateElement charge = elements.stream().filter(e -> e.getType() == ElementType.CHARGE_TABLE).findFirst().orElse(null);
            int ip = item == null ? 0 : requiredPages(item, data.items().size());
            int cp = charge == null ? 0 : requiredPages(charge, data.charges().size());
            if (item == null && charge == null) return new FlowPlan(null, null, 0, 0, 1, 0);
            if (item != null && charge == null) return new FlowPlan(item, null, ip, 0, Math.max(1, ip), 0);
            if (item == null) return new FlowPlan(null, charge, 0, cp, Math.max(1, cp), 0);
            // When both regions share a source page, charges begin on the final
            // item page and continue on additional copies only if they overflow.
            int start = Math.max(0, ip - 1);
            int total = Math.max(1, ip + Math.max(0, cp - 1));
            return new FlowPlan(item, charge, ip, cp, total, start);
        }

        TemplateElement primaryTable() { return itemTable != null ? itemTable : chargeTable; }
        boolean drawItemTable(int part) { return itemTable != null && part < Math.max(1, itemPages); }
        boolean drawChargeTable(int part) {
            if (chargeTable == null) return false;
            int chargePart = part - chargeStartPart;
            return chargePart >= 0 && chargePart < Math.max(1, chargePages);
        }
        List<TaxInvoiceItem> itemChunk(List<TaxInvoiceItem> items, int part) {
            if (!drawItemTable(part) || items == null || items.isEmpty()) return List.of();
            int rows = rowsPerPage(itemTable), from = Math.min(items.size(), part * rows), to = Math.min(items.size(), from + rows);
            return items.subList(from, to);
        }
        List<TemplateCharge> chargeChunk(List<TemplateCharge> charges, int part) {
            if (!drawChargeTable(part) || charges == null || charges.isEmpty()) return List.of();
            int chargePart = part - chargeStartPart, rows = rowsPerPage(chargeTable);
            int from = Math.min(charges.size(), chargePart * rows), to = Math.min(charges.size(), from + rows);
            return charges.subList(from, to);
        }
    }
}
