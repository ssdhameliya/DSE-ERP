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
import org.apache.pdfbox.util.Matrix;
import org.example.documentstudio.model.*;
import org.example.invoice.model.TaxInvoiceItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Renders a saved Document Studio template by preserving the imported PDF as
 * the background and drawing user-created objects on top of it.
 *
 * <p>Template coordinates are stored in PDF points measured from the top-left,
 * which keeps the editor independent of monitor DPI and JavaFX zoom level.</p>
 */
public final class PdfTemplateRenderer {
    private static final PDFont FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private PdfTemplateRenderer() {}

    public static Path renderPurchase(DocumentTemplate template, org.example.model.Purchase purchase, Path output) throws IOException {
        return render(template, TemplateDataFactory.fromPurchase(purchase), output);
    }

    public static Path renderSample(DocumentTemplate template, Path output) throws IOException {
        return render(template, TemplateDataFactory.samplePurchase(), output);
    }

    public static Path render(DocumentTemplate template, TemplateData data, Path output) throws IOException {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(output, "output");
        Path source = TemplateStorageService.sourcePdf(template);
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());

        List<TemplateElement> elements = template.getElements();
        TemplateElement table = elements.stream()
                .filter(e -> e.getType() == ElementType.ITEM_TABLE)
                .findFirst().orElse(null);

        try (PDDocument sourceDoc = Loader.loadPDF(source.toFile());
             PDDocument targetDoc = new PDDocument()) {
            if (sourceDoc.getNumberOfPages() == 0) throw new IOException("Template PDF has no pages.");

            int tablePage = table == null ? -1 : Math.min(table.getPageIndex(), sourceDoc.getNumberOfPages() - 1);
            int tableCopies = table == null ? 1 : requiredTablePages(table, data.items());
            Map<Integer, List<Integer>> sourceToOutputPages = new HashMap<>();
            LayerUtility layer = new LayerUtility(targetDoc);

            for (int sourceIndex = 0; sourceIndex < sourceDoc.getNumberOfPages(); sourceIndex++) {
                int copies = sourceIndex == tablePage ? tableCopies : 1;
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
                        // Imported forms preserve the original media-box geometry. The
                        // translation handles uncommon PDFs whose lower-left is not 0,0.
                        cs.transform(Matrix.getTranslateInstance(-box.getLowerLeftX(), -box.getLowerLeftY()));
                        cs.drawForm(form);
                        cs.restoreGraphicsState();
                    }
                }
                sourceToOutputPages.put(sourceIndex, mapped);
            }

            for (int sourceIndex = 0; sourceIndex < sourceDoc.getNumberOfPages(); sourceIndex++) {
                final int currentSourceIndex = sourceIndex;
                List<TemplateElement> pageElements = elements.stream()
                        .filter(e -> e.getPageIndex() == currentSourceIndex)
                        .toList();
                List<Integer> outputPages = sourceToOutputPages.getOrDefault(sourceIndex, List.of());
                if (sourceIndex == tablePage && table != null && outputPages.size() > 1) {
                    renderRepeatedTablePage(targetDoc, template, data, table, pageElements, outputPages);
                } else {
                    for (Integer outputIndex : outputPages) {
                        PDPage page = targetDoc.getPage(outputIndex);
                        try (PDPageContentStream cs = new PDPageContentStream(targetDoc, page,
                                PDPageContentStream.AppendMode.APPEND, true, true)) {
                            for (TemplateElement element : pageElements) drawElement(targetDoc, page, cs, template, data, element, data.items());
                        }
                    }
                }
            }

            targetDoc.save(output.toFile());
        }
        if (Files.size(output) < 100) throw new IOException("Template renderer produced an invalid PDF.");
        return output;
    }

    private static void renderRepeatedTablePage(PDDocument doc, DocumentTemplate template, TemplateData data,
                                                TemplateElement table, List<TemplateElement> elements,
                                                List<Integer> outputPages) throws IOException {
        int rowsPerPage = rowsPerPage(table);
        for (int pagePart = 0; pagePart < outputPages.size(); pagePart++) {
            int from = Math.min(data.items().size(), pagePart * rowsPerPage);
            int to = Math.min(data.items().size(), from + rowsPerPage);
            List<TaxInvoiceItem> chunk = data.items().subList(from, to);
            PDPage page = doc.getPage(outputPages.get(pagePart));
            try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                for (TemplateElement element : elements) {
                    if (element.getType() == ElementType.ITEM_TABLE) {
                        drawElement(doc, page, cs, template, data, element, chunk);
                        continue;
                    }
                    boolean aboveTable = element.getY() + element.getHeight() <= table.getY() + 2;
                    boolean belowTable = element.getY() >= table.getY() + table.getHeight() - 2;
                    if (aboveTable || (!belowTable && pagePart == 0)) {
                        drawElement(doc, page, cs, template, data, element, chunk);
                    } else if (belowTable && pagePart == outputPages.size() - 1) {
                        drawElement(doc, page, cs, template, data, element, chunk);
                    }
                }
            }
        }
    }

    private static int requiredTablePages(TemplateElement table, List<TaxInvoiceItem> items) {
        if (items == null || items.isEmpty()) return 1;
        int rows = rowsPerPage(table);
        return Math.max(1, (int) Math.ceil(items.size() / (double) rows));
    }

    private static int rowsPerPage(TemplateElement table) {
        double usable = Math.max(table.getRowHeight(), table.getHeight() - table.getHeaderHeight());
        return Math.max(1, (int) Math.floor(usable / table.getRowHeight()));
    }

    private static void drawElement(PDDocument doc, PDPage page, PDPageContentStream cs,
                                    DocumentTemplate template, TemplateData data,
                                    TemplateElement e, List<TaxInvoiceItem> tableItems) throws IOException {
        switch (e.getType()) {
            case TEXT -> drawText(page, cs, e, e.getText());
            case FIELD -> drawText(page, cs, e, data.value(e.getFieldKey()));
            case IMAGE -> drawImage(doc, page, cs, e, TemplateStorageService.resolveAsset(template, e.getImagePath()));
            case IMAGE_FIELD -> drawImage(doc, page, cs, e, data.image(e.getFieldKey()));
            case RECTANGLE -> drawRectangle(page, cs, e, false);
            case WHITEOUT -> drawRectangle(page, cs, e, true);
            case LINE -> drawLine(page, cs, e);
            case ITEM_TABLE -> drawItemTable(page, cs, e, tableItems == null ? List.of() : tableItems, data.gstType());
        }
    }

    private static void drawText(PDPage page, PDPageContentStream cs, TemplateElement e, String value) throws IOException {
        String text = safePdfText(value);
        if (text.isBlank()) return;
        PDFont font = e.isBold() ? FONT_BOLD : FONT;
        float fontSize = (float) e.getFontSize();
        float x = (float) e.getX();
        float top = toPdfY(page, e.getY());
        float lineHeight = fontSize * 1.22f;
        List<String> lines = wrap(text, font, fontSize, (float) e.getWidth());
        setNonStroke(cs, e.getTextColor());
        float y = top - fontSize;
        float bottom = top - (float) e.getHeight();
        for (String line : lines) {
            if (y < bottom) break;
            cs.beginText();
            cs.setFont(font, fontSize);
            cs.newLineAtOffset(x, y);
            cs.showText(line);
            cs.endText();
            y -= lineHeight;
        }
    }

    private static void drawRectangle(PDPage page, PDPageContentStream cs, TemplateElement e, boolean whiteout) throws IOException {
        float x = (float) e.getX();
        float y = toPdfY(page, e.getY() + e.getHeight());
        float w = (float) e.getWidth();
        float h = (float) e.getHeight();
        setNonStroke(cs, whiteout ? "#FFFFFF" : e.getFillColor());
        cs.addRect(x, y, w, h);
        cs.fill();
        if (!whiteout && e.getStrokeWidth() > 0) {
            setStroke(cs, e.getStrokeColor());
            cs.setLineWidth((float) e.getStrokeWidth());
            cs.addRect(x, y, w, h);
            cs.stroke();
        }
    }

    private static void drawLine(PDPage page, PDPageContentStream cs, TemplateElement e) throws IOException {
        setStroke(cs, e.getStrokeColor());
        cs.setLineWidth((float) Math.max(0.5, e.getStrokeWidth()));
        float x1 = (float) e.getX();
        float y1 = toPdfY(page, e.getY());
        float x2 = (float) (e.getX() + e.getWidth());
        float y2 = toPdfY(page, e.getY() + e.getHeight());
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private static void drawImage(PDDocument doc, PDPage page, PDPageContentStream cs,
                                  TemplateElement e, Path imagePath) throws IOException {
        if (imagePath == null || !Files.isRegularFile(imagePath)) return;
        PDImageXObject image = PDImageXObject.createFromFileByContent(imagePath.toFile(), doc);
        float x = (float) e.getX();
        float y = toPdfY(page, e.getY() + e.getHeight());
        cs.drawImage(image, x, y, (float) e.getWidth(), (float) e.getHeight());
    }

    private static void drawItemTable(PDPage page, PDPageContentStream cs, TemplateElement e,
                                      List<TaxInvoiceItem> items, String gstType) throws IOException {
        List<Column> columns = columns(e.getTableColumns());
        if (columns.isEmpty()) columns = columns(List.of("serial", "description", "qty", "rate", "amount"));
        float x = (float) e.getX();
        float top = toPdfY(page, e.getY());
        float width = (float) e.getWidth();
        float headerH = (float) e.getHeaderHeight();
        float rowH = (float) e.getRowHeight();
        float totalWeight = (float) columns.stream().mapToDouble(Column::weight).sum();

        setNonStroke(cs, "#EEF4FF");
        cs.addRect(x, top - headerH, width, headerH);
        cs.fill();
        setStroke(cs, "#9FB3C8");
        cs.setLineWidth(0.65f);
        cs.addRect(x, top - (float) e.getHeight(), width, (float) e.getHeight());
        cs.stroke();

        float cursorX = x;
        for (Column column : columns) {
            float cw = width * (float) column.weight() / totalWeight;
            drawCellText(cs, FONT_BOLD, 7.4f, column.label(), cursorX + 3, top - headerH + 7, cw - 6, headerH - 5, "#24364B");
            cursorX += cw;
            if (cursorX < x + width - 0.5f) {
                cs.moveTo(cursorX, top);
                cs.lineTo(cursorX, top - (float) e.getHeight());
                cs.stroke();
            }
        }
        cs.moveTo(x, top - headerH);
        cs.lineTo(x + width, top - headerH);
        cs.stroke();

        int maxRows = rowsPerPage(e);
        int rowCount = Math.min(maxRows, items.size());
        for (int r = 0; r < rowCount; r++) {
            float rowTop = top - headerH - r * rowH;
            float rowBottom = rowTop - rowH;
            cs.moveTo(x, rowBottom);
            cs.lineTo(x + width, rowBottom);
            cs.stroke();
            TaxInvoiceItem item = items.get(r);
            cursorX = x;
            for (Column column : columns) {
                float cw = width * (float) column.weight() / totalWeight;
                String value = columnValue(column.key(), item, gstType);
                drawCellText(cs, FONT, 7.2f, value, cursorX + 3, rowBottom + 4, cw - 6, rowH - 5, "#172033");
                cursorX += cw;
            }
        }
    }

    private static void drawCellText(PDPageContentStream cs, PDFont font, float fontSize,
                                     String text, float x, float y, float width, float height,
                                     String color) throws IOException {
        setNonStroke(cs, color);
        List<String> lines = wrap(safePdfText(text), font, fontSize, Math.max(5, width));
        float lineHeight = fontSize * 1.15f;
        float cy = y + height - fontSize;
        for (String line : lines) {
            if (cy < y) break;
            cs.beginText(); cs.setFont(font, fontSize); cs.newLineAtOffset(x, cy); cs.showText(line); cs.endText();
            cy -= lineHeight;
        }
    }

    private static String columnValue(String key, TaxInvoiceItem item, String gstType) {
        return switch (key) {
            case "serial" -> Integer.toString(item.getSerialNo());
            case "hsn" -> item.getHsn();
            case "description" -> item.getDescription();
            case "remarks" -> item.getRemarks();
            case "qty" -> number(item.getQuantity());
            case "unit" -> item.getUnit();
            case "rate" -> money(item.getRate());
            case "discount" -> number(item.getDiscountPercent()) + "%";
            case "gst" -> number(item.getGstPercent()) + "%";
            case "taxable" -> money(item.getTaxableAmount());
            case "amount" -> money(item.getTotalAmount());
            default -> "";
        };
    }

    private static List<Column> columns(List<String> keys) {
        List<Column> all = List.of(
                new Column("serial", "Sr", 0.55), new Column("hsn", "HSN", 0.85),
                new Column("description", "Description", 3.5), new Column("remarks", "Remarks", 1.6),
                new Column("qty", "Qty", 0.75), new Column("unit", "Unit", 0.7),
                new Column("rate", "Rate", 1.15), new Column("discount", "Disc %", 0.8),
                new Column("gst", "GST %", 0.8), new Column("taxable", "Taxable", 1.2),
                new Column("amount", "Amount", 1.3));
        Set<String> wanted = new LinkedHashSet<>(keys == null ? List.of() : keys);
        return all.stream().filter(c -> wanted.contains(c.key())).toList();
    }

    private static List<String> wrap(String text, PDFont font, float fontSize, float width) throws IOException {
        if (text == null || text.isEmpty()) return List.of("");
        List<String> result = new ArrayList<>();
        for (String paragraph : text.replace('\r', '\n').split("\\n")) {
            if (paragraph.isBlank()) { result.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                float candidateWidth = font.getStringWidth(candidate) / 1000f * fontSize;
                if (candidateWidth <= width || line.isEmpty()) {
                    line.setLength(0); line.append(candidate);
                } else {
                    result.add(line.toString()); line.setLength(0); line.append(word);
                }
            }
            if (!line.isEmpty()) result.add(line.toString());
        }
        return result;
    }

    private static float toPdfY(PDPage page, double topLeftY) {
        return page.getMediaBox().getHeight() - (float) topLeftY;
    }

    private static void setNonStroke(PDPageContentStream cs, String hex) throws IOException {
        cs.setNonStrokingColor(awtColor(hex));
    }

    private static void setStroke(PDPageContentStream cs, String hex) throws IOException {
        cs.setStrokingColor(awtColor(hex));
    }

    private static java.awt.Color awtColor(String hex) {
        String h = hex == null || !hex.matches("#[0-9a-fA-F]{6}") ? "#172033" : hex;
        return new java.awt.Color(Integer.parseInt(h.substring(1,3),16),
                Integer.parseInt(h.substring(3,5),16), Integer.parseInt(h.substring(5,7),16));
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
}
