package org.example.documentstudio.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.example.documentstudio.model.*;
import org.example.invoice.model.TaxInvoiceItem;

import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders Document Studio templates while keeping the uploaded PDF as the
 * protected background. v8.2.2 adds map-first text fitting, source-aware masks,
 * explicit repeated-page rules and dynamic unlimited charge tables.
 */
public final class PdfTemplateRenderer {
    private static final Pattern ERP_TOKEN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}" );
    private static final Map<PDDocument, Map<String, PDFont>> DOCUMENT_FONTS =
            Collections.synchronizedMap(new WeakHashMap<>());
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
        Path source = TemplateStorageService.sourcePdf(template);
        return renderFromSource(template, data, source, output);
    }

    /** Package-visible deterministic seam for renderer verification without changing the active ERP workspace. */
    static Path renderFromSource(DocumentTemplate template, TemplateData data, Path source, Path output) throws IOException {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(output, "output");
        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);

        List<TemplateElement> elements = template.getElements();
        try (PDDocument sourceDoc = Loader.loadPDF(source.toFile()); PDDocument targetDoc = new PDDocument()) {
            if (sourceDoc.getNumberOfPages() == 0) throw new IOException("Template PDF has no pages.");

            Map<Integer, FlowPlan> plans = new HashMap<>();
            for (int sourceIndex = 0; sourceIndex < sourceDoc.getNumberOfPages(); sourceIndex++) {
                final int page = sourceIndex;
                List<TemplateElement> pageElements = elements.stream().filter(e -> e.getPageIndex() == page).toList();
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
                List<TemplateElement> pageElements = elements.stream().filter(e -> e.getPageIndex() == page).toList();
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
        boolean transformable = e.getType() != ElementType.WHITEOUT;
        if (transformable) {
            cs.saveGraphicsState();
            if (e.getOpacity() < 0.999) {
                PDExtendedGraphicsState state = new PDExtendedGraphicsState();
                state.setNonStrokingAlphaConstant((float) e.getOpacity());
                state.setStrokingAlphaConstant((float) e.getOpacity());
                cs.setGraphicsStateParameters(state);
            }
            if (Math.abs(e.getRotation()) > 0.01) {
                float centerX = (float) (e.getX() + e.getWidth() / 2.0);
                float centerY = toPdfY(page, e.getY() + e.getHeight() / 2.0);
                cs.transform(Matrix.getTranslateInstance(centerX, centerY));
                cs.transform(Matrix.getRotateInstance(Math.toRadians(e.getRotation()), 0, 0));
                cs.transform(Matrix.getTranslateInstance(-centerX, -centerY));
            }
        }
        try {
            switch (e.getType()) {
                case TEXT -> drawText(doc, page, cs, e, e.getText());
                case FIELD -> drawText(doc, page, cs, e, formattedFieldValue(e,
                        fieldValue(data, e.getFieldKey(), pageNumber, totalPages)));
                case IMAGE -> drawImage(doc, page, cs, e, TemplateStorageService.resolveAsset(template, e.getImagePath()));
                case IMAGE_FIELD -> drawImage(doc, page, cs, e, data.image(e.getFieldKey()));
                case BARCODE -> drawGeneratedCode(doc, page, cs, e,
                        resolveTokens(e.getText(), data, pageNumber, totalPages), BarcodeFormat.CODE_128);
                case QR_CODE -> drawGeneratedCode(doc, page, cs, e,
                        resolveTokens(e.getText(), data, pageNumber, totalPages), BarcodeFormat.QR_CODE);
                case RECTANGLE -> drawRectangle(page, cs, e, false);
                case WHITEOUT -> drawRectangle(page, cs, e, true);
                case LINE -> drawLine(page, cs, e);
                case PATH -> drawPath(page, cs, e);
                case ITEM_TABLE -> drawItemTable(page, cs, e, tableItems == null ? List.of() : tableItems, data.gstType());
                case CHARGE_TABLE -> drawChargeTable(page, cs, e, tableCharges == null ? List.of() : tableCharges);
            }
        } finally {
            if (transformable) cs.restoreGraphicsState();
        }
    }

    private static String fieldValue(TemplateData data, String key, int pageNumber, int totalPages) {
        if ("document.pageNumber".equals(key)) return Integer.toString(pageNumber);
        if ("document.totalPages".equals(key)) return Integer.toString(totalPages);
        return data.value(key);
    }

    private static String resolveTokens(String template, TemplateData data, int pageNumber, int totalPages) {
        if (template == null || template.isBlank()) return "";
        Matcher matcher = ERP_TOKEN.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = fieldValue(data, matcher.group(1).trim(), pageNumber, totalPages);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static void drawGeneratedCode(PDDocument doc, PDPage page, PDPageContentStream cs,
                                          TemplateElement element, String value, BarcodeFormat format) throws IOException {
        if (value == null || value.isBlank()) return;
        int pixelWidth = Math.max(96, (int) Math.round(element.getWidth() * 3));
        int pixelHeight = Math.max(48, (int) Math.round(element.getHeight() * 3));
        if (format == BarcodeFormat.QR_CODE) pixelWidth = pixelHeight = Math.max(pixelWidth, pixelHeight);
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(value, format, pixelWidth, pixelHeight,
                    Map.of(EncodeHintType.MARGIN, format == BarcodeFormat.QR_CODE ? 1 : 4));
            java.awt.Color foreground = awtColor(element.getTextColor());
            java.awt.Color background = awtColor(element.getFillColor());
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < matrix.getHeight(); y++) for (int x = 0; x < matrix.getWidth(); x++)
                image.setRGB(x, y, matrix.get(x, y) ? foreground.getRGB() : background.getRGB());
            PDImageXObject pdfImage = LosslessFactory.createFromImage(doc, image);
            float boxX = (float) element.getX(), boxY = toPdfY(page, element.getY() + element.getHeight());
            float boxW = (float) element.getWidth(), boxH = (float) element.getHeight();
            if (format == BarcodeFormat.QR_CODE) {
                float side = Math.min(boxW, boxH);
                boxX += (boxW - side) / 2f; boxY += (boxH - side) / 2f; boxW = side; boxH = side;
            }
            cs.drawImage(pdfImage, boxX, boxY, boxW, boxH);
        } catch (com.google.zxing.WriterException error) {
            throw new IOException("Could not generate " + format + " for value: " + value, error);
        }
    }

    private static String formattedFieldValue(TemplateElement element, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank() && element.isHideWhenBlank()) return "";
        value = switch (element.getValueFormat()) {
            case "UPPERCASE" -> value.toUpperCase(Locale.ROOT);
            case "LOWERCASE" -> value.toLowerCase(Locale.ROOT);
            case "TITLE_CASE" -> titleCase(value);
            case "NUMBER" -> formattedNumber(value, false);
            case "CURRENCY" -> formattedNumber(value, true);
            case "DATE" -> formattedDate(value);
            default -> value;
        };
        return element.getValuePrefix() + value + element.getValueSuffix();
    }

    private static String titleCase(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean capitalize = true;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            result.appendCodePoint(capitalize ? Character.toTitleCase(codePoint) : Character.toLowerCase(codePoint));
            capitalize = Character.isWhitespace(codePoint) || codePoint == '-' || codePoint == '/';
        }
        return result.toString();
    }

    private static String formattedNumber(String value, boolean currency) {
        try {
            String normalized = value.replaceAll("[^0-9+\\-.]", "");
            if (normalized.isBlank()) return value;
            double number = Double.parseDouble(normalized);
            DecimalFormat format = (DecimalFormat) NumberFormat.getNumberInstance(Locale.ENGLISH);
            format.applyPattern(currency ? "#,##0.00" : "#,##0.###");
            return format.format(number);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static String formattedDate(String value) {
        for (DateTimeFormatter input : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/uuuu"), DateTimeFormatter.ofPattern("dd-MM-uuuu"))) {
            try { return LocalDate.parse(value, input).format(DateTimeFormatter.ofPattern("dd/MM/uuuu")); }
            catch (DateTimeParseException ignored) { }
        }
        return value;
    }

    private static void drawText(PDDocument doc, PDPage page, PDPageContentStream cs, TemplateElement e, String value) throws IOException {
        String text = safePdfText(value, false);
        if (text.isBlank()) return;
        PDFont font = fontFor(doc, e, text);
        if (font instanceof PDType1Font) text = safePdfText(text, true);
        float configuredSize = (float) e.getFontSize();
        float width = (float) Math.max(1, e.getWidth());
        float height = (float) Math.max(1, e.getHeight());
        float top = toPdfY(page, e.getY());
        String mode = e.getTextFit();

        if ("SHRINK".equals(mode) && !text.contains("\n")) {
            float size = shrinkToFit(text, font, configuredSize, width, height);
            drawSingleLine(cs, font, size, text, (float) e.getX(), top - size, width, e.getTextAlignment(), e.getTextColor());
            return;
        }
        if ("CLIP".equals(mode)) {
            cs.saveGraphicsState();
            try {
                cs.addRect((float) e.getX(), toPdfY(page, e.getY() + e.getHeight()), width, height);
                cs.clip();
                drawSingleLine(cs, font, configuredSize, text.replace('\n', ' '), (float) e.getX(), top - configuredSize, width, e.getTextAlignment(), e.getTextColor());
            } finally { cs.restoreGraphicsState(); }
            return;
        }
        if ("FIXED".equals(mode)) {
            drawSingleLine(cs, font, configuredSize, text.replace('\n', ' '), (float) e.getX(), top - configuredSize, width, e.getTextAlignment(), e.getTextColor());
            return;
        }

        float lineHeight = configuredSize * 1.22f;
        List<String> lines = wrap(text, font, configuredSize, width);
        setNonStroke(cs, e.getTextColor());
        float y = top - configuredSize;
        float bottom = top - height;
        for (String line : lines) {
            if (y < bottom) break;
            float x = alignedX(font, configuredSize, line, (float) e.getX(), width, e.getTextAlignment());
            cs.beginText(); cs.setFont(font, configuredSize); cs.newLineAtOffset(x, y); cs.showText(line); cs.endText();
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

    private static PDFont fontFor(PDDocument doc, TemplateElement e, String text) {
        if (text != null && text.codePoints().anyMatch(codePoint -> codePoint > 126)) {
            PDFont unicode = unicodeFont(doc, e.isBold(), e.isItalic(), text);
            if (unicode != null) return unicode;
        }
        return fontFor(e);
    }

    private static PDFont unicodeFont(PDDocument doc, boolean bold, boolean italic, String text) {
        Map<String, PDFont> fonts = DOCUMENT_FONTS.computeIfAbsent(doc, ignored -> new HashMap<>());
        for (Path candidate : unicodeFontCandidates(bold, italic)) {
            if (!Files.isRegularFile(candidate)) continue;
            String cacheKey = candidate.toAbsolutePath().normalize().toString();
            try (InputStream input = Files.newInputStream(candidate)) {
                PDFont loaded = fonts.get(cacheKey);
                if (loaded == null) {
                    loaded = PDType0Font.load(doc, input, true);
                    fonts.put(cacheKey, loaded);
                }
                if (supports(loaded, text)) return loaded;
            } catch (IOException ignored) {
                // Try the next platform font. Rendering still has a safe Standard-14 fallback.
            }
        }
        return null;
    }

    private static boolean supports(PDFont font, String text) {
        if (font == null || text == null) return false;
        try {
            return text.codePoints().filter(codePoint -> !Character.isISOControl(codePoint)).allMatch(codePoint -> {
                try { font.encode(new String(Character.toChars(codePoint))); return true; }
                catch (IOException | IllegalArgumentException ignored) { return false; }
            });
        } catch (Exception ignored) { return false; }
    }

    private static List<Path> unicodeFontCandidates(boolean bold, boolean italic) {
        if (bold) return List.of(
                Path.of("C:/Windows/Fonts/NirmalaB.ttf"),
                Path.of("C:/Windows/Fonts/ARIALUNI.TTF"),
                Path.of("C:/Windows/Fonts/arialbd.ttf"),
                Path.of("C:/Windows/Fonts/seguisb.ttf"),
                Path.of("/System/Library/Fonts/Supplemental/Arial Bold.ttf"),
                Path.of("/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf"),
                Path.of("/usr/share/fonts/truetype/noto/NotoSansDevanagari-Bold.ttf"),
                Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"));
        if (italic) return List.of(
                Path.of("C:/Windows/Fonts/Nirmala.ttf"),
                Path.of("C:/Windows/Fonts/ARIALUNI.TTF"),
                Path.of("C:/Windows/Fonts/ariali.ttf"),
                Path.of("C:/Windows/Fonts/segoeuii.ttf"),
                Path.of("/System/Library/Fonts/Supplemental/Arial Italic.ttf"),
                Path.of("/usr/share/fonts/truetype/liberation2/LiberationSans-Italic.ttf"),
                Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans-Oblique.ttf"));
        return List.of(
                Path.of("C:/Windows/Fonts/Nirmala.ttf"),
                Path.of("C:/Windows/Fonts/ARIALUNI.TTF"),
                Path.of("C:/Windows/Fonts/arial.ttf"),
                Path.of("C:/Windows/Fonts/segoeui.ttf"),
                Path.of("/System/Library/Fonts/Supplemental/Arial.ttf"),
                Path.of("/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"),
                Path.of("/usr/share/fonts/truetype/noto/NotoSansDevanagari-Regular.ttf"),
                Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
    }

    private static void drawRectangle(PDPage page, PDPageContentStream cs, TemplateElement e, boolean replacementMask) throws IOException {
        float x = (float) e.getX();
        float y = toPdfY(page, e.getY() + e.getHeight());
        float w = (float) e.getWidth();
        float h = (float) e.getHeight();
        // Replacement masks now carry a sampled source background color instead
        // of being forced white. Old saved masks still default to white.
        setNonStroke(cs, e.getFillColor());
        cs.addRect(x, y, w, h); cs.fill();
        if (!replacementMask && e.getStrokeWidth() > 0) {
            setStroke(cs, e.getStrokeColor()); cs.setLineWidth((float) e.getStrokeWidth());
            cs.addRect(x, y, w, h); cs.stroke();
        }
    }

    private static void drawLine(PDPage page, PDPageContentStream cs, TemplateElement e) throws IOException {
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
        if (e.isPathFilled()) setNonStroke(cs, e.getFillColor());
        if (e.isPathStroked()) { setStroke(cs, e.getStrokeColor()); cs.setLineWidth((float) Math.max(.5, e.getStrokeWidth())); }
        if (e.isPathFilled() && e.isPathStroked()) cs.fillAndStroke();
        else if (e.isPathFilled()) cs.fill();
        else if (e.isPathStroked()) cs.stroke();
    }

    private static float pathX(TemplateElement e, double normalized) { return (float) (e.getX() + normalized * e.getWidth()); }
    private static float pathY(PDPage page, TemplateElement e, double normalized) { return toPdfY(page, e.getY() + normalized * e.getHeight()); }

    private static void drawImage(PDDocument doc, PDPage page, PDPageContentStream cs, TemplateElement e, Path imagePath) throws IOException {
        if (imagePath == null || !Files.isRegularFile(imagePath)) return;
        PDImageXObject image = PDImageXObject.createFromFileByContent(imagePath.toFile(), doc);
        float boxX = (float) e.getX(), boxY = toPdfY(page, e.getY() + e.getHeight());
        float boxW = (float) e.getWidth(), boxH = (float) e.getHeight();
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
        if (columns.isEmpty()) columns = itemColumns(List.of("serial", "description", "qty", "rate", "amount"));
        drawTableScaffold(page, cs, e, columns);
        int count = Math.min(rowsPerPage(e), items.size());
        for (int r = 0; r < count; r++) drawItemRow(page, cs, e, columns, r, items.get(r), gstType);
    }

    private static void drawChargeTable(PDPage page, PDPageContentStream cs, TemplateElement e,
                                        List<TemplateCharge> charges) throws IOException {
        List<Column> columns = chargeColumns(e.getTableColumns());
        if (columns.isEmpty()) columns = chargeColumns(List.of("type", "amount", "gstPercent", "taxAmount", "total"));
        drawTableScaffold(page, cs, e, columns);
        int count = Math.min(rowsPerPage(e), charges.size());
        for (int r = 0; r < count; r++) drawChargeRow(page, cs, e, columns, r, charges.get(r));
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
        drawDataRow(page, cs, e, columns, row, key -> itemValue(key, item, gstType));
    }

    private static void drawChargeRow(PDPage page, PDPageContentStream cs, TemplateElement e, List<Column> columns,
                                      int row, TemplateCharge charge) throws IOException {
        drawDataRow(page, cs, e, columns, row, key -> chargeValue(key, charge));
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

    private static String itemValue(String key, TaxInvoiceItem item, String gstType) {
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

    private static String chargeValue(String key, TemplateCharge charge) {
        return switch (key) {
            case "type" -> charge.type();
            case "amount" -> money(charge.amount());
            case "taxable" -> charge.taxable() ? "Yes" : "No";
            case "gstPercent" -> number(charge.gstPercent()) + "%";
            case "taxAmount" -> money(charge.taxAmount());
            case "total" -> money(charge.total());
            default -> "";
        };
    }

    private static List<Column> itemColumns(List<String> keys) {
        List<Column> all = List.of(
                new Column("serial", "Sr", .55), new Column("hsn", "HSN", .85),
                new Column("description", "Description", 3.5), new Column("remarks", "Remarks", 1.6),
                new Column("qty", "Qty", .75), new Column("unit", "Unit", .7),
                new Column("rate", "Rate", 1.15), new Column("discount", "Disc %", .8),
                new Column("gst", "GST %", .8), new Column("taxable", "Taxable", 1.2),
                new Column("amount", "Amount", 1.3));
        return chooseColumns(all, keys);
    }

    private static List<Column> chargeColumns(List<String> keys) {
        List<Column> all = List.of(
                new Column("type", "Charge", 2.5), new Column("amount", "Amount", 1.2),
                new Column("taxable", "Taxable", .9), new Column("gstPercent", "GST %", .9),
                new Column("taxAmount", "Tax", 1.1), new Column("total", "Total", 1.25));
        return chooseColumns(all, keys);
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

    private static String safePdfText(String value) { return safePdfText(value, true); }

    private static String safePdfText(String value, boolean standardFontOnly) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
                out.append(codePoint == '\t' ? ' ' : (char) codePoint);
            } else if (Character.isISOControl(codePoint)) {
                out.append(' ');
            } else if (!standardFontOnly || (codePoint >= 32 && codePoint <= 126)) {
                out.appendCodePoint(codePoint);
            } else if (codePoint == '\u20b9') {
                out.append("Rs.");
            } else {
                out.append('?');
            }
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
