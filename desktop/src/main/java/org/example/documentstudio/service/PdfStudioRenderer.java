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
import org.example.invoice.calculation.AmountInWordsConverter;
import org.example.invoice.pdf.TaxInvoicePdfGenerator;
import org.example.invoice.model.CompanyProfile;
import org.example.invoice.model.InvoiceParty;
import org.example.invoice.model.TaxInvoiceDocument;
import org.example.invoice.model.InvoiceTotals;
import org.example.invoice.model.TaxInvoiceCharge;
import org.example.invoice.model.TaxInvoiceItem;
import org.example.shared.DocumentCalculationEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Renders Document Studio templates while keeping the uploaded PDF as the
 * protected background. v8.2.2 adds map-first text fitting, source-aware masks,
 * explicit repeated-page rules and dynamic unlimited charge tables.
 */
public final class PdfStudioRenderer {
    /*
     * A PDFont owns COS objects that are adopted by the first PDDocument using it.
     * Reusing static PDFont instances across invoice files can leave the next PDF
     * with dangling font resources. Keep a fresh cache per rendering thread and
     * clear it at the beginning of every render invocation.
     */
    private static final ThreadLocal<EnumMap<Standard14Fonts.FontName, PDFont>> RENDER_FONTS =
            ThreadLocal.withInitial(() -> new EnumMap<>(Standard14Fonts.FontName.class));

    private PdfStudioRenderer() {}

    private static PDFont font(Standard14Fonts.FontName name) {
        return RENDER_FONTS.get().computeIfAbsent(name, PDType1Font::new);
    }

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
        RENDER_FONTS.get().clear();
        data = ErpDocumentJsonService.normalize(template.getDocumentType(), enrichPdfData(data));
        Path source = TemplateStorageService.sourcePdf(template);
        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);

        List<TemplateElement> elements = template.getElements();
        try (PDDocument sourceDoc = Loader.loadPDF(source.toFile()); PDDocument targetDoc = new PDDocument()) {
            if (sourceDoc.getNumberOfPages() == 0) throw new IOException("Template PDF has no pages.");

            TaxInvoicePdfGenerator.SalesLayoutPlan salesLayout = null;
            if (template.getDocumentType() == DocumentType.SALES_INVOICE && !"MAPPED_FIXED".equals(template.getLayoutMode())) {
                try {
                    salesLayout = TaxInvoicePdfGenerator.layoutPlan(toSalesLayoutDocument(data));
                } catch (Exception ex) {
                    throw new IOException("Unable to calculate shared Standard Sales layout plan.", ex);
                }
            }

            Map<Integer, FlowPlan> plans = new HashMap<>();
            for (int sourceIndex = 0; sourceIndex < sourceDoc.getNumberOfPages(); sourceIndex++) {
                final int page = sourceIndex;
                List<TemplateElement> pageElements = elements.stream().filter(e -> PdfStyleResolver.effectivelyVisible(template,e)).filter(e -> e.getPageIndex() == page).toList();
                plans.put(sourceIndex, FlowPlan.forPage(pageElements, data, salesLayout));
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
                    // PDFBox Standard-14 font objects must not be reused across page resource
                    // dictionaries. Reusing one PDType1Font on multiple pages can corrupt the
                    // first page /Font references when the document is saved. Keep a tiny
                    // per-page cache instead: the page still reuses Helvetica/Bold internally,
                    // while every page owns valid font resource dictionaries.
                    RENDER_FONTS.get().clear();
                    int outputIndex = outputPages.get(part);
                    PDPage outputPage = targetDoc.getPage(outputIndex);
                    List<TaxInvoiceItem> itemChunk = plan.itemChunk(data.items(), part);
                    List<TemplateCharge> chargeChunk = plan.chargeChunk(data.charges(), part);
                    try (PDPageContentStream cs = new PDPageContentStream(targetDoc, outputPage,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        boolean sharedSalesLayout = template.getDocumentType() == DocumentType.SALES_INVOICE && salesLayout != null;
                        if (sharedSalesLayout) {
                            prepareDynamicSalesPage(outputPage, cs, plan, part, salesLayout);
                        }
                        for (TemplateElement e : pageElements) {
                            if (sharedSalesLayout && isLegacyFixedSalesClosingElement(e)) continue;
                            if (e.getType() == ElementType.ITEM_TABLE) {
                                if (plan.drawItemTable(part)) {
                                    TemplateElement liveTable = sharedSalesLayout ? sharedSalesTableElement(e, plan, part, salesLayout) : e;
                                    drawElement(targetDoc, outputPage, cs, template, data, liveTable, itemChunk, chargeChunk, outputIndex + 1, totalPages, salesLayout);
                                }
                                continue;
                            }
                            if (e.getType() == ElementType.CHARGE_TABLE) {
                                if (plan.drawChargeTable(part)) drawElement(targetDoc, outputPage, cs, template, data, e, itemChunk, chargeChunk, outputIndex + 1, totalPages, salesLayout);
                                continue;
                            }
                            if (shouldDraw(e, plan, part)) {
                                drawElement(targetDoc, outputPage, cs, template, data, e, itemChunk, chargeChunk, outputIndex + 1, totalPages, salesLayout);
                            }
                        }
                        if (sharedSalesLayout && part == plan.totalCopies() - 1) {
                            drawDynamicSalesClosing(targetDoc, outputPage, cs, data, salesLayout, plan);
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
        // Explicit page rules must also be honored for a one-page document.
        // In particular, INTERMEDIATE means "all pages except the last"; for a
        // single-page invoice there is no intermediate page.  The old early return
        // drew INTERMEDIATE whiteouts on one-page invoices and erased the complete
        // bank/totals/terms/signature closing stack.
        return switch (e.getPageRule()) {
            case "FIRST", "FIXED" -> part == 0;
            case "EVERY" -> true;
            case "CONTINUATION" -> part > 0;
            case "INTERMEDIATE" -> part < plan.totalCopies() - 1;
            case "LAST" -> part == plan.totalCopies() - 1;
            case "MULTI" -> plan.totalCopies() > 1;
            default -> plan.totalCopies() <= 1 || legacyAutoRule(e, plan.primaryTable(), part, plan.totalCopies());
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
                                    int pageNumber, int totalPages,
                                    TaxInvoicePdfGenerator.SalesLayoutPlan salesLayout) throws IOException {
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
                case ITEM_TABLE -> drawItemTable(page, cs, draw, tableItems == null ? List.of() : tableItems, data.gstType(), pageNumber, totalPages,
                        salesLayout == null ? Math.max(18.0, draw.getRowHeight()) : Math.max(18.0, salesLayout.standardRowMinHeight()));
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
                values.put("totals.basicAmount", money(totals.basicAmount()));
                values.put("totals.discountAmount", money(totals.discountAmount()));
                values.put("totals.taxableAmount", money(totals.taxableAmount()));
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
            case "TIMES" -> font(bold && italic ? Standard14Fonts.FontName.TIMES_BOLD_ITALIC
                    : bold ? Standard14Fonts.FontName.TIMES_BOLD
                    : italic ? Standard14Fonts.FontName.TIMES_ITALIC
                    : Standard14Fonts.FontName.TIMES_ROMAN);
            case "COURIER" -> font(bold && italic ? Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE
                    : bold ? Standard14Fonts.FontName.COURIER_BOLD
                    : italic ? Standard14Fonts.FontName.COURIER_OBLIQUE
                    : Standard14Fonts.FontName.COURIER);
            default -> font(bold && italic ? Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE
                    : bold ? Standard14Fonts.FontName.HELVETICA_BOLD
                    : italic ? Standard14Fonts.FontName.HELVETICA_OBLIQUE
                    : Standard14Fonts.FontName.HELVETICA);
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
                                      List<TaxInvoiceItem> items, String gstType, int pageNumber, int totalPages,
                                      double fillerRowHeight) throws IOException {
        List<Column> columns = itemColumns(e.getTableColumns());
        if (columns.isEmpty()) columns = itemColumns(List.of("serial", "descriptionWithRemarks", "quantity", "rate", "total"));

        TemplateElement effective = e;
        int count = items == null ? 0 : items.size();
        if (e.isUseSourceTableDesign()) {
            effective = e.copy();
            rebuildSourceSalesGridDynamic(page, cs, effective, count, pageNumber == totalPages, fillerRowHeight);
        }

        drawTableScaffold(page, cs, effective, columns);
        for (int r = 0; r < count; r++) drawItemRow(page, cs, effective, columns, r, items.get(r), gstType);
    }

    private static void rebuildSourceSalesGrid(PDPage page, PDPageContentStream cs, TemplateElement e, double bottomTopLeftY) throws IOException {
        float x = (float)e.getX();
        float width = (float)e.getWidth();
        float bodyTopY = (float)(e.getY() + e.getHeaderHeight());
        float bottomY = (float)bottomTopLeftY;
        float pdfBottom = toPdfY(page, bottomY);
        float pdfTop = toPdfY(page, bodyTopY);

        // Erase only inside the old table-body strokes, then redraw the Standard-blue scaffold.
        setNonStroke(cs, "#FFFFFF");
        cs.addRect(x + 0.7f, pdfBottom + 0.7f, width - 1.4f, Math.max(1f, pdfTop - pdfBottom - 1.4f));
        cs.fill();
        setStroke(cs, "#7FA4D3");
        cs.setLineWidth(0.45f);

        List<Float> widths = columnWidths(e, itemColumns(e.getTableColumns()), width);
        float cursor = x;
        cs.moveTo(x, pdfTop); cs.lineTo(x + width, pdfTop); cs.stroke();
        for (float w : widths) {
            cs.moveTo(cursor, pdfTop); cs.lineTo(cursor, pdfBottom); cs.stroke();
            cursor += w;
        }
        cs.moveTo(x + width, pdfTop); cs.lineTo(x + width, pdfBottom); cs.stroke();

        double rh = e.getRowHeight();
        for (double y = bodyTopY; y < bottomY - 0.5; y += rh) {
            float py = toPdfY(page, Math.min(bottomY, y));
            cs.moveTo(x, py); cs.lineTo(x + width, py); cs.stroke();
        }
        cs.moveTo(x, pdfBottom); cs.lineTo(x + width, pdfBottom); cs.stroke();
    }


    private static void rebuildSourceSalesGridDynamic(PDPage page, PDPageContentStream cs, TemplateElement e,
                                                      int realRows, boolean finalPage, double fillerRowHeight) throws IOException {
        float x = (float)e.getX();
        float width = (float)e.getWidth();
        double bodyTop = e.getY() + e.getHeaderHeight();
        double cursorY = bodyTop;
        List<Double> lines = new ArrayList<>();
        lines.add(bodyTop);
        for (int i = 0; i < realRows; i++) {
            cursorY += e.getRowHeight();
            lines.add(cursorY);
        }
        if (finalPage) {
            double targetBottom = e.getY() + e.getHeight();
            while (cursorY + fillerRowHeight <= targetBottom + 0.6) {
                cursorY += fillerRowHeight;
                lines.add(cursorY);
            }
        }
        float pdfTop = toPdfY(page, bodyTop);
        float pdfBottom = toPdfY(page, cursorY);

        setNonStroke(cs, "#FFFFFF");
        cs.addRect(x + 0.7f, pdfBottom + 0.7f, width - 1.4f, Math.max(1f, pdfTop - pdfBottom - 1.4f));
        cs.fill();
        setStroke(cs, "#7FA4D3");
        cs.setLineWidth(0.45f);
        List<Float> widths = columnWidths(e, itemColumns(e.getTableColumns()), width);
        float colX = x;
        for (float w : widths) {
            cs.moveTo(colX, pdfTop); cs.lineTo(colX, pdfBottom); cs.stroke();
            colX += w;
        }
        cs.moveTo(x + width, pdfTop); cs.lineTo(x + width, pdfBottom); cs.stroke();
        for (double y : lines) {
            float py = toPdfY(page, y);
            cs.moveTo(x, py); cs.lineTo(x + width, py); cs.stroke();
        }
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
        float headerH = (float) e.getHeaderHeight();
        List<Float> widths = columnWidths(e, columns, width);
        setNonStroke(cs, "#EEF4FF"); cs.addRect(x, top - headerH, width, headerH); cs.fill();
        setStroke(cs, "#9FB3C8"); cs.setLineWidth(0.65f); cs.addRect(x, top - (float)e.getHeight(), width, (float)e.getHeight()); cs.stroke();
        float cursorX = x;
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            float cw = widths.get(i);
            drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 7.4f, column.label(), cursorX + 3, top - headerH + 7, cw - 6, Math.max(5, headerH - 5), "#24364B");
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
        List<Float> widths = columnWidths(e, columns, width);
        float cursorX = x;
        if (!e.isUseSourceTableDesign()) {
            setStroke(cs, "#9FB3C8"); cs.setLineWidth(.65f); cs.moveTo(x, rowBottom); cs.lineTo(x + width, rowBottom); cs.stroke();
        }
        PDFont font = fontFor(e);
        float fontSize = (float)Math.max(5, Math.min(e.getFontSize(), rowH * .58));
        List<String> alignments = e.getTableColumnAlignments();
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            float cw = widths.get(i);
            String alignment = alignments != null && i < alignments.size() ? alignments.get(i) : "LEFT";
            drawCellTextAligned(cs, font, fontSize, value.apply(column.key()), cursorX + 3, rowBottom + 2,
                    Math.max(4, cw - 6), Math.max(4, rowH - 3), e.getTextColor(), alignment);
            cursorX += cw;
        }
    }

    private static float totalWeight(List<Column> columns) { return (float)columns.stream().mapToDouble(Column::weight).sum(); }

    /** Exact PDF-template column widths win when supplied; legacy templates keep semantic proportional widths. */
    private static List<Float> columnWidths(TemplateElement element, List<Column> columns, float totalWidth) {
        List<Double> exact = element == null ? List.of() : element.getTableColumnWidths();
        if (exact != null && exact.size() == columns.size() && exact.stream().allMatch(v -> v != null && v > 0)) {
            double supplied = exact.stream().mapToDouble(Double::doubleValue).sum();
            if (supplied > 0) {
                double scale = totalWidth / supplied;
                return exact.stream().map(v -> (float)(v * scale)).toList();
            }
        }
        float totalWeight = totalWeight(columns);
        return columns.stream().map(column -> totalWidth * (float)column.weight() / totalWeight).toList();
    }

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
            case "grossAmount" -> money(result.grossAmount());
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
                new Column("igstPercent", "IGST %", .8), new Column("igstAmount", "IGST", 1.0), new Column("grossAmount", "Amount", 1.3), new Column("total", "Total", 1.3),
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
        // Preserve the template's explicit column order. Filtering the catalogue order
        // silently swapped fields such as rate/unit when a source PDF used a different
        // order from the generic catalogue.
        Map<String, Column> byKey = new LinkedHashMap<>();
        for (Column column : all) byKey.put(column.key(), column);
        List<Column> selected = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String key : keys == null ? List.<String>of() : keys) {
            Column column = byKey.get(key);
            if (column != null && seen.add(column.key())) selected.add(column);
        }
        return selected;
    }

    private static void drawCellText(PDPageContentStream cs, PDFont font, float fontSize,
                                     String text, float x, float y, float width, float height, String color) throws IOException {
        drawCellTextAligned(cs, font, fontSize, text, x, y, width, height, color, "LEFT");
    }

    private static void drawCellTextAligned(PDPageContentStream cs, PDFont font, float fontSize,
                                            String text, float x, float y, float width, float height,
                                            String color, String alignment) throws IOException {
        setNonStroke(cs, color);
        List<String> lines = wrap(safePdfText(text), font, fontSize, Math.max(5, width));
        float lineHeight = fontSize * 1.12f, cy = y + height - fontSize;
        for (String line : lines) {
            if (cy < y) break;
            float drawX = alignedX(font, fontSize, line, x, width, alignment == null ? "LEFT" : alignment.toUpperCase(Locale.ROOT));
            cs.beginText(); cs.setFont(font, fontSize); cs.newLineAtOffset(drawX, cy); cs.showText(line); cs.endText();
            cy -= lineHeight;
        }
    }

    private static void drawSingleLineCentered(PDPageContentStream cs, PDFont font, float fontSize,
                                               String text, float x, float bottomY, float width, float height,
                                               String color, String alignment) throws IOException {
        String line = safePdfText(text == null ? "" : text);
        setNonStroke(cs, color);
        String effectiveAlignment = alignment == null ? "LEFT" : alignment.toUpperCase(Locale.ROOT);
        float drawX = alignedX(font, fontSize, line, x, width, effectiveAlignment);
        float ascent = fontSize;
        float descent = 0f;
        if (font.getFontDescriptor() != null) {
            float fdAscent = font.getFontDescriptor().getAscent();
            float fdDescent = font.getFontDescriptor().getDescent();
            if (fdAscent != 0f) ascent = fdAscent / 1000f * fontSize;
            descent = fdDescent / 1000f * fontSize;
        }
        float glyphHeight = Math.max(fontSize * 0.75f, ascent - descent);
        float baseline = bottomY + Math.max(0f, (height - glyphHeight) / 2f) - descent;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(drawX, baseline);
        cs.showText(line);
        cs.endText();
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


    private static TaxInvoiceDocument toSalesLayoutDocument(TemplateData data) {
        List<TaxInvoiceCharge> charges = data.charges().stream()
                .map(c -> new TaxInvoiceCharge(c.type(), c.amount(), c.taxable(), c.gstPercent()))
                .toList();
        InvoiceTotals totals = InvoiceTaxCalculator.calculate(data.items(), charges, data.gstType());
        CompanyProfile company = new CompanyProfile(
                data.value("company.name"), data.value("company.address"), data.value("company.gstin"),
                data.value("company.email"), data.value("company.alternateEmail"), data.value("company.phone"),
                data.value("payment.bankName"), data.value("payment.branch"), data.value("payment.accountNumber"),
                data.value("payment.ifsc"), data.value("payment.accountType"), data.value("payment.mode"),
                data.value("company.terms"), pathText(data.image("company.logo")), pathText(data.image("company.signature")),
                data.value("company.certificationText"));
        InvoiceParty billing = new InvoiceParty(data.value("party.name"), data.value("party.billingAddress"),
                data.value("party.billingGstin"), data.value("party.contactPerson"), data.value("party.contact"));
        InvoiceParty delivery = new InvoiceParty(data.value("party.name"), data.value("party.deliveryAddress"),
                data.value("party.deliveryGstin"), data.value("party.contactPerson"), data.value("party.contact"));
        String words = data.value("totals.amountInWords");
        if (words.isBlank()) words = "INR : " + AmountInWordsConverter.indianRupees(totals.grandTotal());
        return new TaxInvoiceDocument(company,
                data.value("document.number"), parseDate(data.value("document.date")),
                data.value("document.poNumber"), parseDate(data.value("document.poDate")), data.value("document.paymentTerms"),
                billing, delivery, data.value("transport.name"), data.value("transport.gstin"), data.value("transport.vehicleNumber"),
                data.value("transport.contactPerson"), data.value("transport.contact"), data.items(), data.gstType(), charges, totals, words);
    }

    private static String pathText(Path path) { return path == null ? "" : path.toString(); }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return LocalDate.now();
        for (DateTimeFormatter f : List.of(DateTimeFormatter.ofPattern("dd/MM/yyyy"), DateTimeFormatter.ofPattern("dd-MM-yyyy"), DateTimeFormatter.ISO_LOCAL_DATE)) {
            try { return LocalDate.parse(value.trim(), f); } catch (Exception ignored) { }
        }
        return LocalDate.now();
    }

    private static boolean isLegacyFixedSalesClosingElement(TemplateElement e) {
        if (e == null) return false;
        boolean closingRule = "LAST".equals(e.getPageRule()) || "INTERMEDIATE".equals(e.getPageRule());
        return closingRule && e.getY() >= 600.0 && e.getY() < 812.0;
    }

    private static TemplateElement sharedSalesTableElement(TemplateElement source, FlowPlan flow, int part,
                                                            TaxInvoicePdfGenerator.SalesLayoutPlan layout) {
        TemplateElement table = source.copy();
        boolean finalPage = part == flow.totalCopies() - 1;
        double capacity;
        if (finalPage) {
            double pageHeight = 841.8898; // A4 points; source templates retain A4 geometry.
            double financialTopTopLeft = pageHeight - (layout.financialY() + layout.financialHeight());
            capacity = financialTopTopLeft - table.getY() - 5.0;
        } else {
            capacity = layout.firstIntermediateCapacity();
        }
        table.setHeight(Math.max(table.getHeaderHeight() + 20.0, capacity));
        table.setRowHeight(Math.max(18.0, layout.physicalRowMinHeight()));
        return table;
    }

    private static void prepareDynamicSalesPage(PDPage page, PDPageContentStream cs, FlowPlan flow, int part,
                                                TaxInvoicePdfGenerator.SalesLayoutPlan layout) throws IOException {
        // Clear the fixed source closing artwork. The actual closing stack is rebuilt from
        // measured Standard-Sales geometry on the final page; intermediate pages reuse this
        // region for real item rows.
        float top = 270.15f;
        float bottom = 808.0f;
        setNonStroke(cs, "#FFFFFF");
        float pyBottom = toPdfY(page, bottom);
        float pyTop = toPdfY(page, top);
        cs.addRect(23.8f, pyBottom, 547.4f, Math.max(1f, pyTop - pyBottom));
        cs.fill();
    }

    private static void drawDynamicSalesClosing(PDDocument doc, PDPage page, PDPageContentStream cs,
                                                TemplateData data, TaxInvoicePdfGenerator.SalesLayoutPlan layout,
                                                FlowPlan flow) throws IOException {
        final float left = flow != null && flow.itemTable() != null ? (float)flow.itemTable().getX() : 24f;
        final float width = flow != null && flow.itemTable() != null ? (float)flow.itemTable().getWidth() : 547f;
        final float leftW = width * .65f;
        final float gapW = width * .02f;
        final float rightW = width * .33f;
        final String stroke = "#7599C6";
        final String navy = "#1E437B";

        float financialH = layout.financialHeight();
        float financialTop = page.getMediaBox().getHeight() - (layout.financialY() + financialH);
        float financialBottomPdf = toPdfY(page, financialTop + financialH);
        drawRoundedCard(cs, left, financialBottomPdf, leftW, financialH, 5f, "#FFFFFF", stroke);
        drawRoundedCard(cs, left + leftW + gapW, financialBottomPdf, rightW, financialH, 5f, "#FFFFFF", stroke);

        List<String[]> bankRows = new ArrayList<>();
        addIfValue(bankRows, "Supplier GST NO", data.value("company.gstin"));
        addIfValue(bankRows, "BANK NAME", data.value("payment.bankName"));
        addIfValue(bankRows, "BRANCH", data.value("payment.branch"));
        addIfValue(bankRows, "A/c NO", data.value("payment.accountNumber"));
        addIfValue(bankRows, "IFSC CODE", data.value("payment.ifsc"));
        addIfValue(bankRows, "ACCOUNT TYPE", data.value("payment.accountType"));
        addIfValue(bankRows, "PAYMENT MODE", data.value("payment.mode"));
        bankRows.add(new String[]{"PAYMENT TERMS", blankAs(data.value("document.paymentTerms"), "NA")});
        List<String[]> totals = dynamicTotalsRows(data);
        // Match Standard Sales: both sides keep their natural compact row rhythm and
        // begin at the top of the financial card. The card itself grows to the taller
        // side, but the shorter side must NOT stretch its rows to fill that height.
        int naturalFinancialRows = Math.max(1, Math.max(bankRows.size(), totals.size()));
        float naturalRowH = financialH / naturalFinancialRows;
        float bankRowH = naturalRowH;
        for (int i = 0; i < bankRows.size(); i++) {
            float topY = financialTop + i * bankRowH;
            String[] row = bankRows.get(i);
            drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 6.1f, row[0], left + 6f,
                    toPdfY(page, topY + bankRowH - 2.0f), leftW * .31f - 8f, bankRowH - 2f, "#000000");
            drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA), 6.1f, ":  " + row[1], left + leftW * .31f,
                    toPdfY(page, topY + bankRowH - 2.0f), leftW * .69f - 7f, bankRowH - 2f,
                    (i == 0 || i == bankRows.size() - 1) ? navy : "#000000");
        }

        float calcX = left + leftW + gapW;
        float calcRowH = naturalRowH;
        setStroke(cs, stroke); cs.setLineWidth(.35f);
        for (int i = 1; i < totals.size(); i++) {
            float y = toPdfY(page, financialTop + i * calcRowH);
            cs.moveTo(calcX, y); cs.lineTo(calcX + rightW, y); cs.stroke();
        }
        for (int i = 0; i < totals.size(); i++) {
            float topY = financialTop + i * calcRowH;
            String[] row = totals.get(i);
            PDFont lf = (i == 0 || row[0].startsWith("TAXABLE")) ? font(Standard14Fonts.FontName.HELVETICA_BOLD) : font(Standard14Fonts.FontName.HELVETICA);
            float rowBottom = toPdfY(page, topY + calcRowH);
            // Calculation rows are a single continuous row: label uses the full left edge,
            // amount uses the full right edge, and both share the same vertically-centred baseline.
            // Do not use drawCellText here: that generic helper intentionally top-aligns wrapped text.
            drawSingleLineCentered(cs, lf, 6.0f, row[0], calcX + 4f, rowBottom, rightW - 8f, calcRowH, "#000000", "LEFT");
            drawSingleLineCentered(cs, lf, 6.0f, row[1], calcX + 4f, rowBottom, rightW - 8f, calcRowH, "#000000", "RIGHT");
        }

        float closingH = layout.closingHeight();
        float closingTop = page.getMediaBox().getHeight() - (layout.closingY() + closingH);
        float closingBottomPdf = toPdfY(page, closingTop + closingH);
        drawRoundedCard(cs, left, closingBottomPdf, leftW, closingH, 5f, "#DFF5E3", stroke);
        drawRoundedCard(cs, calcX, closingBottomPdf, rightW, closingH, 5f, "#DFF5E3", stroke);
        drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 7.0f, "INR :", left + 6f,
                closingBottomPdf + 2f, 38f, closingH - 3f, navy);
        drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA), 6.8f,
                blankAs(data.value("totals.amountInWordsText"), data.value("totals.amountInWords")), left + 45f,
                closingBottomPdf + 2f, leftW - 50f, closingH - 3f, "#000000");
        // Grand Total follows the same continuous-row rule as Calculation: full left label,
        // full right amount, both optically centred between the top/bottom card rules.
        drawSingleLineCentered(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 7.0f, "G R A N D   T O T A L",
                calcX + 5f, closingBottomPdf, rightW - 10f, closingH, navy, "LEFT");
        drawSingleLineCentered(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 8.0f, data.value("totals.roundedGrandTotal"),
                calcX + 5f, closingBottomPdf, rightW - 10f, closingH, navy, "RIGHT");

        float termsH = layout.termsHeight();
        float termsTop = page.getMediaBox().getHeight() - (layout.termsY() + termsH);
        float termsBottomPdf = toPdfY(page, termsTop + termsH);
        drawRoundedCard(cs, left, termsBottomPdf, leftW, termsH, 5f, "#FFFFFF", stroke);
        drawRoundedCard(cs, calcX, termsBottomPdf, rightW, termsH, 5f, "#FFFFFF", stroke);
        drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 7.6f, "TERMS & CONDITIONS", left + 7f,
                toPdfY(page, termsTop + 12f), leftW - 14f, 10f, navy);
        drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA), 6.7f, data.value("company.terms"), left + 7f,
                termsBottomPdf + 5f, leftW - 14f, Math.max(10f, termsH - 18f), "#000000");
        drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 8.5f, "For, " + data.value("company.name"), calcX + 6f,
                toPdfY(page, termsTop + 13f), rightW - 12f, 10f, navy);
        Path signature = data.image("company.signature");
        if (signature != null && Files.isRegularFile(signature)) {
            PDImageXObject image = PDImageXObject.createFromFileByContent(signature.toFile(), doc);
            float maxW = rightW - 18f, maxH = Math.max(8f, termsH - 31f);
            float scale = Math.min(maxW / Math.max(1f, image.getWidth()), maxH / Math.max(1f, image.getHeight()));
            float iw = image.getWidth() * scale, ih = image.getHeight() * scale;
            cs.drawImage(image, calcX + (rightW - iw) / 2f, termsBottomPdf + 13f, iw, ih);
        }
        drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 6.1f, "AUTHORIZED SIGNATORY", calcX + 8f,
                termsBottomPdf + 2f, rightW - 16f, 9f, "#000000");
    }

    private static void drawRoundedCard(PDPageContentStream cs, float x, float y, float w, float h, float radius,
                                        String fill, String stroke) throws IOException {
        setNonStroke(cs, fill); setStroke(cs, stroke); cs.setLineWidth(.55f);
        roundedRect(cs, x, y, w, h, radius); cs.fillAndStroke();
    }

    private static void addIfValue(List<String[]> rows, String label, String value) {
        if (value != null && !value.isBlank()) rows.add(new String[]{label, value.trim()});
    }

    private static String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? (fallback == null ? "" : fallback) : value;
    }

    private static List<String[]> dynamicTotalsRows(TemplateData data) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"BASIC AMOUNT", data.value("totals.basicAmount")});
        if (!isZeroMoney(data.value("totals.discountAmount"))) rows.add(new String[]{"DISCOUNT", data.value("totals.discountAmount")});
        for (TemplateCharge charge : data.charges()) {
            rows.add(new String[]{charge.type().toUpperCase(Locale.ROOT), money(charge.amount())});
        }
        rows.add(new String[]{"TAXABLE AMOUNT", data.value("totals.taxableAmount")});
        if (data.gstType().toUpperCase(Locale.ROOT).contains("IGST") || data.gstType().toUpperCase(Locale.ROOT).contains("INTER")) {
            rows.add(new String[]{blankAs(data.value("tax.primaryLabel"), "IGST"), data.value("totals.igstAmount")});
        } else {
            rows.add(new String[]{blankAs(data.value("tax.primaryLabel"), "CGST"), data.value("totals.cgstAmount")});
            rows.add(new String[]{blankAs(data.value("tax.secondaryLabel"), "SGST"), data.value("totals.sgstAmount")});
        }
        rows.add(new String[]{"ROUND OFF", blankAs(data.value("totals.roundOff"), "-")});
        return rows;
    }

    private static boolean isZeroMoney(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) return true;
        try { return Math.abs(Double.parseDouble(value.replace(",", "").trim())) < .004; }
        catch (Exception ignored) { return false; }
    }

    private record Column(String key, String label, double weight) {}

    private record FlowPlan(TemplateElement itemTable, TemplateElement chargeTable,
                            int itemPages, int chargePages, int totalCopies, int chargeStartPart,
                            TaxInvoicePdfGenerator.SalesLayoutPlan salesLayout) {
        static FlowPlan forPage(List<TemplateElement> elements, TemplateData data,
                                TaxInvoicePdfGenerator.SalesLayoutPlan salesLayout) {
            TemplateElement item = elements.stream().filter(e -> e.getType() == ElementType.ITEM_TABLE).findFirst().orElse(null);
            TemplateElement charge = elements.stream().filter(e -> e.getType() == ElementType.CHARGE_TABLE).findFirst().orElse(null);
            if (item != null && salesLayout != null && salesLayout.totalPages() > 0) {
                return new FlowPlan(item, charge, salesLayout.totalPages(), 0,
                        salesLayout.totalPages(), 0, salesLayout);
            }
            int ip = item == null ? 0 : requiredPages(item, data.items().size());
            int cp = charge == null ? 0 : requiredPages(charge, data.charges().size());
            if (item == null && charge == null) return new FlowPlan(null, null, 0, 0, 1, 0, null);
            if (item != null && charge == null) return new FlowPlan(item, null, ip, 0, Math.max(1, ip), 0, null);
            if (item == null) return new FlowPlan(null, charge, 0, cp, Math.max(1, cp), 0, null);
            int start = Math.max(0, ip - 1);
            int total = Math.max(1, ip + Math.max(0, cp - 1));
            return new FlowPlan(item, charge, ip, cp, total, start, null);
        }

        TemplateElement primaryTable() { return itemTable != null ? itemTable : chargeTable; }
        boolean drawItemTable(int part) { return itemTable != null && part < Math.max(1, itemPages); }
        boolean drawChargeTable(int part) {
            if (salesLayout != null) return false;
            if (chargeTable == null) return false;
            int chargePart = part - chargeStartPart;
            return chargePart >= 0 && chargePart < Math.max(1, chargePages);
        }
        List<TaxInvoiceItem> itemChunk(List<TaxInvoiceItem> items, int part) {
            if (!drawItemTable(part) || items == null || items.isEmpty()) return List.of();
            if (salesLayout != null && part < salesLayout.pages().size()) {
                var page = salesLayout.pages().get(part);
                int from = Math.min(items.size(), page.fromIndex());
                int to = Math.min(items.size(), page.toIndex());
                return items.subList(from, to);
            }
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
