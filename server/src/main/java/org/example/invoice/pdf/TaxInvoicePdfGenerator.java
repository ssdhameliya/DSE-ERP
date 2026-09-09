package org.example.invoice.pdf;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.font.FontProvider;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.layout.layout.LayoutArea;
import com.itextpdf.layout.layout.LayoutContext;
import com.itextpdf.layout.layout.LayoutResult;
import com.itextpdf.layout.renderer.CellRenderer;
import com.itextpdf.layout.renderer.DrawContext;
import com.itextpdf.layout.renderer.IRenderer;
import org.example.invoice.model.CompanyProfile;
import org.example.invoice.model.InvoiceParty;
import org.example.invoice.model.InvoiceTotals;
import org.example.invoice.model.TaxInvoiceDocument;
import org.example.invoice.model.TaxInvoiceCharge;
import org.example.invoice.model.TaxInvoiceItem;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates the JASVI Industries sales tax invoice.
 *
 * <p>This renderer intentionally owns the complete document layout. It does
 * not delegate any section to the legacy ERP PDF renderer, which prevents old
 * invoice panels from leaking into the approved JASVI design.</p>
 */
public final class TaxInvoicePdfGenerator {
    private static final DeviceRgb NAVY = new DeviceRgb(30, 67, 123);
    private static final DeviceRgb BLUE = new DeviceRgb(55, 117, 188);
    private static final DeviceRgb PALE_BLUE = new DeviceRgb(238, 244, 251);
    private static final DeviceRgb VERY_PALE_BLUE = new DeviceRgb(248, 250, 253);
    private static final DeviceRgb GREEN = new DeviceRgb(223, 245, 227);
    private static final DeviceRgb PALE_YELLOW = new DeviceRgb(255, 247, 220);
    private static final DeviceRgb GRID = new DeviceRgb(117, 153, 198);
    private static final DeviceRgb MUTED = new DeviceRgb(78, 90, 108);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    // Item pagination is derived from iText's live remaining page area. Final-page
    // capacity is solved against the measured closing stack so the item frame ends
    // exactly one standard gap above Bank/Calculation, regardless of optional content.
    private static final float FILLER_ROW_HEIGHT = 18f;
    private static final float LAYOUT_SAFETY = 2.0f;

    // Approved JASVI invoice typography/geometry tokens. Keep all visual
    // measurements here so every section follows one coherent design system.
    private static final float FONT_META = 7.0f;
    private static final float FONT_SECTION = 7.8f;
    private static final float FONT_PARTY = 8.0f;
    private static final float FONT_BODY = 6.8f;
    private static final float FONT_BODY_SMALL = 6.45f;
    private static final float FONT_TABLE_HEADER = 6.9f;
    private static final float FONT_ITEM_TITLE = FONT_BODY_SMALL;
    private static final float FONT_ITEM_REMARK = 6.35f;
    private static final float FONT_TOTAL = 6.45f;
    private static final float FONT_TERMS = 6.9f;
    private static final float CONTENT_WIDTH_PERCENT = 100f;
    private static final float STANDARD_SECTION_GAP = 5f;
    private static final float COMPACT_VERTICAL_PADDING = 1f;
    // Lower closing stack uses one explicit visible gap. The final item-region heights
    // are reduced accordingly so Item -> Bank and Terms -> Footer keep the same spacing
    // without pushing the closing stack to a second page.
    private static final float LOWER_SECTION_GAP = STANDARD_SECTION_GAP;
    private static final float HEADER_TO_TITLE_GAP = STANDARD_SECTION_GAP;
    private static final float FOOTER_RESERVED_BOTTOM = 31f;
    private static final float FOOTER_BAR_Y = 3.5f;
    private static final float FOOTER_ADDRESS_Y = 17.5f;
    private static final float FOOTER_SEPARATOR_Y = 28.5f;
    private static final float FOOTER_DARK_PERCENT = 48f;
    private static final float FOOTER_BLUE_PERCENT = 52f;
    private static final float FOOTER_PAGE_NUMBER_WIDTH = 68f;
    private static final int MAX_ITEMS_PER_PAGE = 20;
    private static final float SIGNATURE_MAX_WIDTH = 174f;
    private static final float SIGNATURE_MAX_HEIGHT = 60f;
    private static final int SIGNATURE_TRIM_PADDING_PX = 3;
    private static final ConcurrentHashMap<AssetCacheKey, byte[]> ASSET_IMAGE_CACHE = new ConcurrentHashMap<>();

    /**
     * FULL is the official customer/export PDF. BODY_ONLY is the Sales Register
     * "Sale Invoice" variant: company header/footer artwork is suppressed while
     * their original layout space remains reserved, so TAX INVOICE through the
     * closing Terms/Signature stack keeps identical coordinates.
     */
    public enum Presentation { FULL, BODY_ONLY }

    private TaxInvoicePdfGenerator() {
    }

    public static Path generate(TaxInvoiceDocument invoice, Path output) throws Exception {
        return generate(invoice, output, Presentation.FULL);
    }

    /**
     * Shared dynamic Sales layout contract consumed by both the Standard Sales renderer
     * and PDF Studio. The plan is measured by the same iText tables and live content
     * used by the Standard renderer, so Studio never has to guess row heights, page
     * splits, or closing-stack dimensions from hard-coded template coordinates.
     */
    public record SalesLayoutPage(int fromIndex, int toIndex, boolean finalPage) {
        public int itemCount() { return Math.max(0, toIndex - fromIndex); }
    }

    public record SalesLayoutPlan(
            List<SalesLayoutPage> pages,
            float standardRowMinHeight,
            float physicalRowMinHeight,
            float financialHeight,
            float closingHeight,
            float termsHeight,
            float financialY,
            float closingY,
            float termsY,
            float firstIntermediateCapacity,
            float firstFinalCapacity) {
        public int totalPages() { return pages == null ? 0 : pages.size(); }
    }

    public static SalesLayoutPlan layoutPlan(TaxInvoiceDocument invoice) throws Exception {
        validateCustomerFacingRemarks(invoice);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(bytes);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {
            doc.setMargins(8, 24, FOOTER_RESERVED_BOTTOM, 24);
            configureTypography(doc);
            doc.setFontSize(7.0f);

            addCompanyHeader(doc, invoice.company(), true);
            addInvoiceTitleAndMeta(doc, invoice);
            addAddressCards(doc, invoice);
            addTransportStrip(doc, invoice);

            List<TaxInvoiceItem> items = new ArrayList<>(invoice.items());
            ClosingGeometry closing = closingGeometry(doc, invoice);
            float financialHeight = measureTableHeight(doc, buildFinancialTable(invoice));
            float closingHeight = measureTableHeight(doc, buildClosingTotalsTable(invoice));
            float termsHeight = measureTableHeight(doc, buildTermsAndSignatureTable(invoice));
            float initialFinalCapacity = finalItemCapacity(doc, invoice);
            float initialIntermediateCapacity = intermediateItemCapacity(doc);

            List<SalesLayoutPage> pages = new ArrayList<>();
            if (items.isEmpty()) {
                return new SalesLayoutPlan(List.of(), 0f, FILLER_ROW_HEIGHT, financialHeight, closingHeight, termsHeight,
                        closing.financialY(), closing.closingY(), closing.termsY(),
                        initialIntermediateCapacity, initialFinalCapacity);
            }

            if (items.size() <= MAX_ITEMS_PER_PAGE && fitsItems(doc, items, initialFinalCapacity)) {
                pages.add(new SalesLayoutPage(0, items.size(), true));
                return new SalesLayoutPlan(List.copyOf(pages), 0f, FILLER_ROW_HEIGHT, financialHeight, closingHeight, termsHeight,
                        closing.financialY(), closing.closingY(), closing.termsY(),
                        initialIntermediateCapacity, initialFinalCapacity);
            }

            int firstCount = Math.min(MAX_ITEMS_PER_PAGE,
                    Math.max(1, maxFittingCount(doc, items, initialIntermediateCapacity)));
            float standardRowMinHeight = expandedRowMinHeight(doc, items.subList(0, firstCount), initialIntermediateCapacity);
            float headerOnlyHeight = measureItemsTableHeight(doc, List.of(), standardRowMinHeight);
            float firstTableHeight = measureItemsTableHeight(doc, items.subList(0, firstCount), standardRowMinHeight);
            float physicalRowMinHeight = Math.max(FILLER_ROW_HEIGHT,
                    (firstTableHeight - headerOnlyHeight) / Math.max(1, firstCount));

            int offset = 0;
            while (offset < items.size()) {
                List<TaxInvoiceItem> remaining = items.subList(offset, items.size());
                float finalCapacity = finalItemCapacity(doc, invoice);
                if (remaining.size() <= MAX_ITEMS_PER_PAGE
                        && fitsItems(doc, remaining, finalCapacity, standardRowMinHeight)) {
                    pages.add(new SalesLayoutPage(offset, items.size(), true));
                    break;
                }

                float pageCapacity = intermediateItemCapacity(doc);
                int physicalFit = maxFittingCount(doc, remaining, pageCapacity, standardRowMinHeight);
                int fit = Math.min(MAX_ITEMS_PER_PAGE, physicalFit);
                if (remaining.size() <= MAX_ITEMS_PER_PAGE && remaining.size() > 1) {
                    fit = Math.min(fit, remaining.size() - 1);
                }
                fit = Math.max(1, fit);
                pages.add(new SalesLayoutPage(offset, offset + fit, false));
                offset += fit;

                doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                addCompanyHeader(doc, invoice.company(), true);
                addInvoiceTitleAndMeta(doc, invoice);
                addAddressCards(doc, invoice);
                addTransportStrip(doc, invoice);
            }

            return new SalesLayoutPlan(List.copyOf(pages), standardRowMinHeight, physicalRowMinHeight, financialHeight, closingHeight, termsHeight,
                    closing.financialY(), closing.closingY(), closing.termsY(),
                    initialIntermediateCapacity, initialFinalCapacity);
        }
    }

    public static Path generate(TaxInvoiceDocument invoice, Path output, Presentation presentation) throws Exception {
        validateCustomerFacingRemarks(invoice);
        Presentation mode = presentation == null ? Presentation.FULL : presentation;
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        try (PdfWriter writer = new PdfWriter(output.toString());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {
            doc.setMargins(8, 24, FOOTER_RESERVED_BOTTOM, 24);
            configureTypography(doc);
            doc.setFontSize(7.0f);

            addCompanyHeader(doc, invoice.company(), mode == Presentation.FULL);
            addInvoiceTitleAndMeta(doc, invoice);
            addAddressCards(doc, invoice);
            addTransportStrip(doc, invoice);
            addPaginatedItems(doc, invoice, mode);
            addFixedClosingStack(doc, invoice);
            if (mode == Presentation.FULL) {
                addFooter(doc, invoice.company());
                addPageNumbers(doc);
            }
        }
        return output;
    }

    /**
     * Renders the approved invoice header from Settings. A wide uploaded artwork is
     * contained in the fixed header box; a normal logo is composed with company text.
     * Image dimensions can never grow the page layout.
     */
    private static void addCompanyHeader(Document doc, CompanyProfile company, boolean visible) {
        final float headerHeight = 82f;
        Image logo = configuredImage(company.logoPath());

        if (!visible) {
            addCompanyHeaderSpacer(doc, logo, headerHeight);
            return;
        }

        if (logo != null && logo.getImageWidth() > logo.getImageHeight() * 3.2f) {
            // The uploaded full-width header artwork must share the exact same
            // left/right guides as every invoice block below it. Force the
            // content width while preserving its aspect ratio.
            float contentWidth = PageSize.A4.getWidth() - 48f;
            float scale = contentWidth / logo.getImageWidth();
            logo.scaleAbsolute(contentWidth, logo.getImageHeight() * scale);
            logo.setHorizontalAlignment(HorizontalAlignment.LEFT);
            logo.setMarginLeft(0);
            logo.setMarginRight(0);
            logo.setMarginBottom(HEADER_TO_TITLE_GAP);
            doc.add(logo);
            return;
        }

        Table header = new Table(UnitValue.createPercentArray(new float[]{24, 76}))
                .useAllAvailableWidth().setHeight(headerHeight);
        Cell logoCell = noBorder().setVerticalAlignment(VerticalAlignment.MIDDLE).setTextAlignment(TextAlignment.CENTER);
        if (logo != null) {
            logo.scaleToFit(118f, 72f);
            logo.setHorizontalAlignment(HorizontalAlignment.CENTER);
            logoCell.add(logo);
        }
        header.addCell(logoCell);

        Cell brand = noBorder().setVerticalAlignment(VerticalAlignment.MIDDLE).setPaddingLeft(2);
        String certificate = company.certificationText();
        if (!certificate.isBlank()) {
            brand.add(new Paragraph(certificate).setTextAlignment(TextAlignment.RIGHT)
                    .setBackgroundColor(BLUE).setFontColor(ColorConstants.WHITE).setBold()
                    .setFontSize(6.2f).setPaddingTop(2).setPaddingBottom(2).setPaddingRight(7)
                    .setMarginBottom(8));
        }
        brand.add(new Paragraph(dash(company.name())).setBold().setFontColor(NAVY)
                .setFontSize(24f).setCharacterSpacing(1.1f).setMargin(0).setMarginBottom(4));
        String contacts = joinNonBlank("  |  ", company.email(), company.alternateEmail(), company.phone());
        brand.add(new Paragraph(contacts).setBold().setFontColor(NAVY).setFontSize(6.8f)
                .setBorderTop(new SolidBorder(NAVY, .8f)).setPaddingTop(4).setMargin(0));
        header.addCell(brand);
        doc.add(header);
        doc.add(new Table(1).useAllAvailableWidth().setMarginTop(2).setMarginBottom(HEADER_TO_TITLE_GAP)
                .addCell(new Cell().setHeight(1).setBorder(Border.NO_BORDER).setBackgroundColor(GRID)));
    }

    /**
     * Reserves exactly the same header footprint as the visible company header.
     * This is used only by the Sales Register body-only PDF so removing branding
     * never moves TAX INVOICE or any subsequent section.
     */
    private static void addCompanyHeaderSpacer(Document doc, Image logo, float normalHeaderHeight) {
        if (logo != null && logo.getImageWidth() > logo.getImageHeight() * 3.2f) {
            float contentWidth = PageSize.A4.getWidth() - 48f;
            float scaledHeight = logo.getImageHeight() * (contentWidth / logo.getImageWidth());
            Table spacer = new Table(1).useAllAvailableWidth().setHeight(scaledHeight)
                    .setMarginBottom(HEADER_TO_TITLE_GAP);
            spacer.addCell(noBorder());
            doc.add(spacer);
            return;
        }

        Table header = new Table(1).useAllAvailableWidth().setHeight(normalHeaderHeight);
        header.addCell(noBorder());
        doc.add(header);
        // Mirror the visible header's 2pt top margin, 1pt separator height and
        // HEADER_TO_TITLE_GAP bottom margin without drawing any branding.
        Table separatorSpacer = new Table(1).useAllAvailableWidth()
                .setMarginTop(2).setMarginBottom(HEADER_TO_TITLE_GAP);
        separatorSpacer.addCell(noBorder().setHeight(1));
        doc.add(separatorSpacer);
    }

    private static void addInvoiceTitleAndMeta(Document doc, TaxInvoiceDocument invoice) {
        // 4.0.7: title follows the exact same left/right content guides as all
        // primary invoice blocks. No centered percentage inset.
        Table title = new Table(1).useAllAvailableWidth();
        Table titleLine = new Table(UnitValue.createPercentArray(new float[]{20, 60, 20}))
                .useAllAvailableWidth();
        titleLine.addCell(noBorder().setBackgroundColor(NAVY));
        titleLine.addCell(noBorder().setBackgroundColor(NAVY)
                .setTextAlignment(TextAlignment.CENTER)
                .add(new Paragraph("TAX INVOICE").setBold().setFontSize(13.5f)
                        .setFontColor(ColorConstants.WHITE).setPaddingTop(4).setPaddingBottom(4).setMargin(0)));
        titleLine.addCell(noBorder().setBackgroundColor(NAVY)
                .setTextAlignment(TextAlignment.RIGHT).setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPaddingRight(7)
                .add(new Paragraph("(ORIGINAL FOR BUYER)").setBold().setFontSize(6.6f)
                        .setFontColor(ColorConstants.WHITE).setMargin(0)));
        Cell titleCell = rounded(new Cell().setPadding(0).setBorder(Border.NO_BORDER));
        titleCell.add(titleLine);
        title.addCell(titleCell);
        doc.add(title);

        // Invoice/order details and date details are two independent cards,
        // matching the Billing/Delivery card rhythm and padding.
        Table metaCards = new Table(UnitValue.createPercentArray(new float[]{49, 2, 49}))
                .useAllAvailableWidth().setMarginTop(STANDARD_SECTION_GAP).setMarginBottom(STANDARD_SECTION_GAP);
        metaCards.addCell(metaCard(
                "INVOICE NO", invoice.invoiceNo(),
                "PO NO", invoice.orderNo().isBlank() ? "NA" : invoice.orderNo()));
        metaCards.addCell(noBorder());
        metaCards.addCell(metaCard(
                "INVOICE DATE", formatDate(invoice.invoiceDate()),
                invoice.poDate() == null ? "" : "PO DATE", invoice.poDate() == null ? "" : formatDate(invoice.poDate())));
        doc.add(metaCards);
    }

    private static Cell metaCard(String label1, String value1, String label2, String value2) {
        Cell card = roundedFilled(new Cell()
                .setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING)
                .setPaddingLeft(6).setPaddingRight(6).setBorder(Border.NO_BORDER), PALE_BLUE);
        Table values = new Table(UnitValue.createPercentArray(new float[]{32, 5, 63})).useAllAvailableWidth();
        addSingleMetaRow(values, label1, value1);
        if (label2 != null && !label2.isBlank() && value2 != null && !value2.isBlank()) addSingleMetaRow(values, label2, value2);
        card.add(values);
        return card;
    }

    private static void addSingleMetaRow(Table table, String label, String value) {
        table.addCell(metaText(label, true));
        table.addCell(metaText(":", true));
        table.addCell(metaText(value, false));
    }

    private static Cell metaText(String value, boolean bold) {
        Paragraph p = new Paragraph(value == null ? "" : value).setFontSize(FONT_META).setMargin(0);
        if (bold) p.setBold();
        return noBorder().setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING)
                .setPaddingLeft(1).setPaddingRight(1).add(p);
    }

    private static void addAddressCards(Document doc, TaxInvoiceDocument invoice) {
        // 7.3.8: Billing and Delivery are always shown as independent audit-friendly
        // cards, even when "Same as Billing" produced identical party values.
        Table addresses = new Table(UnitValue.createPercentArray(new float[]{49, 2, 49}))
                .useAllAvailableWidth().setMarginBottom(STANDARD_SECTION_GAP);
        addresses.addCell(addressCard("BILLING ADDRESS", invoice.billing()));
        addresses.addCell(noBorder());
        addresses.addCell(addressCard("DELIVERY ADDRESS", invoice.delivery()));
        doc.add(addresses);
    }

    private static Cell addressCard(String heading, InvoiceParty party) {
        Cell card = roundedFilled(new Cell()
                .setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING)
                .setPaddingLeft(6).setPaddingRight(6).setBorder(Border.NO_BORDER), PALE_BLUE);
        card.add(new Paragraph(heading).setBold().setFontSize(FONT_SECTION).setFontColor(NAVY)
                .setMarginBottom(5));

        Cell content = noBorder().setPadding(0);
        content.add(new Paragraph(party.name()).setBold().setFontSize(FONT_PARTY).setMarginBottom(3));
        if (!party.address().isBlank()) {
            content.add(new Paragraph(party.address()).setFontSize(FONT_BODY).setFixedLeading(8.4f).setMarginBottom(2));
        }
        content.add(detailLine("GST-IN", dash(party.gstin())).setBold());
        card.add(content);
        return card;
    }

    private static Paragraph detailLine(String label, String value) {
        return new Paragraph(label + " : " + value).setFontSize(FONT_BODY).setMargin(0).setFixedLeading(8.4f);
    }

    private static void addTransportStrip(Document doc, TaxInvoiceDocument invoice) {
        if (!hasTransportDetails(invoice)) return;

        // One full-width single-line transport card. No reserved outer 5pt gutters:
        // every available point belongs to the visible facts. Column shares are
        // proportional to content length, so longer facts (usually Contact Details)
        // automatically receive more width while short facts stay compact.
        String contact = joinNonBlank(" / ", invoice.contactPerson(), formatIndianPhone(invoice.contactPersonMobile()));
        List<String> facts = new ArrayList<>();
        addIfNotBlank(facts, labelled("TRANSPORTER", invoice.transporter()));
        addIfNotBlank(facts, labelled("GSTIN", invoice.transporterGstin()));
        addIfNotBlank(facts, labelled("VEHICLE", invoice.vehicleNumber()));
        addIfNotBlank(facts, contact.isBlank() ? "" : "CONTACT DETAILS : " + contact);

        float[] widths = transportFactWidths(facts);
        float transportFontSize = transportFontSize(facts);
        Table content = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth().setMargin(0);
        for (int i = 0; i < facts.size(); i++) {
            Cell fact = noBorder()
                    .setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING)
                    .setPaddingLeft(i == 0 ? 0 : 2).setPaddingRight(i == facts.size() - 1 ? 0 : 2)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .add(new Paragraph(facts.get(i))
                            .setBold().setFontSize(transportFontSize).setFixedLeading(transportFontSize + 1.2f).setMargin(0));
            content.addCell(fact);
        }

        Cell contentCard = noBorder().setBackgroundColor(PALE_BLUE)
                .setPadding(0)
                .add(content);

        Table strip = new Table(1).useAllAvailableWidth().setMarginTop(0).setMarginBottom(STANDARD_SECTION_GAP);
        strip.addCell(roundedFilled(new Cell().setPadding(0).setBorder(Border.NO_BORDER).add(contentCard), PALE_BLUE));
        doc.add(strip);
    }

    private static float[] transportFactWidths(List<String> facts) {
        float[] widths = new float[facts.size()];
        for (int i = 0; i < facts.size(); i++) {
            String fact = facts.get(i);
            // Character-weighted sizing is intentionally simple and deterministic.
            // It gives long values more room while preserving a compact minimum share.
            widths[i] = Math.max(10f, fact == null ? 10f : fact.length() + 4f);
        }
        return widths;
    }

    private static float transportFontSize(List<String> facts) {
        int totalChars = 0;
        for (String fact : facts) totalChars += fact == null ? 0 : fact.length();
        // Keep the standard small-body size for normal invoices and only compress
        // very dense transport rows enough to protect the single-line contract.
        if (totalChars > 120) return 5.65f;
        if (totalChars > 100) return 5.9f;
        if (totalChars > 82) return 6.15f;
        return FONT_BODY_SMALL;
    }

    private static void addIfNotBlank(List<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value);
    }

    private static boolean hasTransportDetails(TaxInvoiceDocument invoice) {
        return !joinNonBlank("", invoice.transporter(), invoice.transporterGstin(), invoice.vehicleNumber(), invoice.contactPerson(), invoice.contactPersonMobile()).isBlank();
    }

    private static String labelled(String label, String value) { return value == null || value.isBlank() ? "" : label + " : " + value.trim(); }

    /**
     * 7.1.9 multi-page pagination contract.
     *
     * Every physical page repeats the approved full invoice header stack
     * (company/header, invoice meta, addresses and transporter). Non-final pages
     * contain up to 20 real item rows only; those real rows are expanded uniformly
     * to consume the available item region and finish one approved section gap above
     * that page's footer. Artificial blank grid rows are never added to an
     * intermediate page. Only the final page may use blank grid rows, and only to
     * consume the flexible item area above the Bank/Calculation closing stack.
     */
    private static void addPaginatedItems(Document doc, TaxInvoiceDocument invoice, Presentation presentation) {
        List<TaxInvoiceItem> items = new ArrayList<>(invoice.items());
        if (items.isEmpty()) return;

        // Single-page invoices keep the approved natural/filler-row behavior unchanged.
        float firstFinalCapacity = finalItemCapacity(doc, invoice);
        if (items.size() <= MAX_ITEMS_PER_PAGE && fitsItems(doc, items, firstFinalCapacity)) {
            addItemsTable(doc, items, firstFinalCapacity);
            return;
        }

        // Multi-page invoices establish ONE real-row height from page 1 and reuse it
        // on every later page. The final page therefore has fewer total rows, never
        // narrower/compressed rows; its unused row slots are filled with same-height
        // blank rows above the measured closing stack.
        float intermediateCapacity = intermediateItemCapacity(doc);
        int firstCount = Math.min(MAX_ITEMS_PER_PAGE,
                Math.max(1, maxFittingCount(doc, items, intermediateCapacity)));
        float standardRowMinHeight = expandedRowMinHeight(doc, items.subList(0, firstCount), intermediateCapacity);

        int offset = 0;
        while (offset < items.size()) {
            List<TaxInvoiceItem> remaining = items.subList(offset, items.size());
            float finalCapacity = finalItemCapacity(doc, invoice);

            if (remaining.size() <= MAX_ITEMS_PER_PAGE
                    && fitsItems(doc, remaining, finalCapacity, standardRowMinHeight)) {
                addItemsTable(doc, remaining, finalCapacity, standardRowMinHeight);
                return;
            }

            float pageCapacity = intermediateItemCapacity(doc);
            int physicalFit = maxFittingCount(doc, remaining, pageCapacity, standardRowMinHeight);
            int fit = Math.min(MAX_ITEMS_PER_PAGE, physicalFit);
            if (remaining.size() <= MAX_ITEMS_PER_PAGE && remaining.size() > 1) {
                fit = Math.min(fit, remaining.size() - 1);
            }
            fit = Math.max(1, fit);

            addExpandedContentItemsTable(doc, remaining.subList(0, fit), pageCapacity, standardRowMinHeight);
            offset += fit;

            if (presentation == Presentation.FULL) addFooter(doc, invoice.company());
            doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            addCompanyHeader(doc, invoice.company(), presentation == Presentation.FULL);
            addInvoiceTitleAndMeta(doc, invoice);
            addAddressCards(doc, invoice);
            addTransportStrip(doc, invoice);
        }
    }

    /**
     * Available height for real item rows on a non-final page. Normal document flow
     * already stops above the fixed footer reserve; keep one additional standard 5pt
     * visible gap between the item frame and footer separator.
     */
    private static float intermediateItemCapacity(Document doc) {
        return Math.max(0f, currentFlowCapacity(doc) - STANDARD_SECTION_GAP);
    }

    /**
     * Renders a non-final page using real rows only. The rows themselves are expanded
     * uniformly so the table consumes the complete available item region; no blank
     * filler rows are introduced on intermediate pages.
     */
    private static float expandedRowMinHeight(Document doc, List<TaxInvoiceItem> items, float targetHeight) {
        float low = 0f;
        float high = 48f;
        for (int i = 0; i < 18; i++) {
            float mid = (low + high) / 2f;
            Table candidate = buildItemsTable(items, mid);
            float measured = measureTableHeight(doc, roundedItemsSection(candidate));
            if (measured <= targetHeight) low = mid;
            else high = mid;
        }
        return low;
    }

    private static void addExpandedContentItemsTable(Document doc, List<TaxInvoiceItem> items,
                                                     float targetHeight, float standardRowMinHeight) {
        Table section = roundedItemsSection(buildItemsTable(items, standardRowMinHeight));
        section.setMarginBottom(0);
        doc.add(section);
    }

    /**
     * Returns the exact live height still available to normal document flow on the
     * current page. This replaces the old first/continuation magic-number capacities.
     */
    private static float currentFlowCapacity(Document doc) {
        LayoutArea area = doc.getRenderer().getCurrentArea();
        if (area == null || area.getBBox() == null) return 0f;
        return Math.max(0f, area.getBBox().getHeight() - LAYOUT_SAFETY);
    }

    /**
     * Calculates final-page item capacity from physical page coordinates. The top of
     * Bank/Calculation is measured from the footer upward; the item frame is allowed
     * to consume every point above it except the one approved 5pt section gap.
     */
    private static float finalItemCapacity(Document doc, TaxInvoiceDocument invoice) {
        LayoutArea area = doc.getRenderer().getCurrentArea();
        if (area == null || area.getBBox() == null) return 0f;

        float currentTopY = area.getBBox().getTop();
        float financialTopY = closingGeometry(doc, invoice).financialTopY();
        return Math.max(0f, currentTopY - financialTopY - STANDARD_SECTION_GAP);
    }

    private static ClosingGeometry closingGeometry(Document doc, TaxInvoiceDocument invoice) {
        float financialHeight = measureTableHeight(doc, buildFinancialTable(invoice));
        float closingHeight = measureTableHeight(doc, buildClosingTotalsTable(invoice));
        float termsHeight = measureTableHeight(doc, buildTermsAndSignatureTable(invoice));

        float termsY = FOOTER_SEPARATOR_Y + STANDARD_SECTION_GAP;
        float closingY = termsY + termsHeight + STANDARD_SECTION_GAP;
        float financialY = closingY + closingHeight + STANDARD_SECTION_GAP;
        return new ClosingGeometry(financialY, financialHeight, closingY, termsY);
    }

    private record ClosingGeometry(float financialY, float financialHeight, float closingY, float termsY) {
        float financialTopY() {
            return financialY + financialHeight;
        }
    }

    private static void validateCustomerFacingRemarks(TaxInvoiceDocument invoice) {
        for (TaxInvoiceItem item : invoice.items()) {
            if (item.getRemarks() == null || item.getRemarks().isBlank()) {
                throw new IllegalArgumentException("Item Master remark is required for invoice PDF (item " + item.getSerialNo() + ").");
            }
        }
    }

    private static int maxFittingCount(Document doc, List<TaxInvoiceItem> items, float capacity) {
        return maxFittingCount(doc, items, capacity, 0f);
    }

    private static int maxFittingCount(Document doc, List<TaxInvoiceItem> items, float capacity, float rowMinHeight) {
        int best = 0;
        for (int count = 1; count <= items.size(); count++) {
            if (measureItemsTableHeight(doc, items.subList(0, count), rowMinHeight) <= capacity - LAYOUT_SAFETY) {
                best = count;
            } else {
                break;
            }
        }
        return best;
    }

    private static boolean fitsItems(Document doc, List<TaxInvoiceItem> items, float capacity) {
        return fitsItems(doc, items, capacity, 0f);
    }

    private static boolean fitsItems(Document doc, List<TaxInvoiceItem> items, float capacity, float rowMinHeight) {
        return measureItemsTableHeight(doc, items, rowMinHeight) <= capacity - LAYOUT_SAFETY;
    }

    private static float measureItemsTableHeight(Document doc, List<TaxInvoiceItem> items) {
        return measureItemsTableHeight(doc, items, 0f);
    }

    private static float measureItemsTableHeight(Document doc, List<TaxInvoiceItem> items, float rowMinHeight) {
        Table table = buildItemsTable(items, rowMinHeight);
        IRenderer renderer = table.createRendererSubTree();
        renderer.setParent(doc.getRenderer());
        float contentWidth = PageSize.A4.getWidth() - doc.getLeftMargin() - doc.getRightMargin();
        LayoutResult result = renderer.layout(new LayoutContext(
                new LayoutArea(1, new Rectangle(0, 0, contentWidth, PageSize.A4.getHeight()))));
        if (result.getOccupiedArea() == null) return Float.MAX_VALUE;
        return result.getOccupiedArea().getBBox().getHeight();
    }

    private static void addItemsTable(Document doc, List<TaxInvoiceItem> items, float targetHeight) {
        addItemsTable(doc, items, targetHeight, 0f);
    }

    private static void addItemsTable(Document doc, List<TaxInvoiceItem> items,
                                      float targetHeight, float standardRowMinHeight) {
        Table table = buildItemsTable(items, standardRowMinHeight);
        // Final-page filler rows use the SAME row height as real multi-page rows.
        // Therefore the final page has fewer total rows, not compressed rows.
        float fillerHeight = standardRowMinHeight > 0f ? standardRowMinHeight : FILLER_ROW_HEIGHT;
        int guard = 0;
        while (guard++ < 80) {
            Table section = roundedItemsSection(table);
            float measured = measureTableHeight(doc, section);
            float remaining = targetHeight - measured;
            if (remaining <= 1.2f) break;
            if (remaining + 0.6f < fillerHeight) break;
            addFillerRow(table, fillerHeight);
        }

        Table section = roundedItemsSection(table);
        section.setMarginBottom(0);
        doc.add(section);
    }

    private static Table roundedItemsSection(Table items) {
        Table section = new Table(1).useAllAvailableWidth().setMargin(0);
        section.addCell(roundedFilled(new Cell().setPadding(0).setBorder(Border.NO_BORDER).add(items), ColorConstants.WHITE));
        return section;
    }

    private static Table buildItemsTable(List<TaxInvoiceItem> items) {
        return buildItemsTable(items, 0f);
    }

    private static Table buildItemsTable(List<TaxInvoiceItem> items, float rowMinHeight) {
        // Size every non-description column from the actual header/data it must render.
        // PRODUCT DESCRIPTION deliberately receives every remaining point of table width.
        float[] widths = dynamicItemColumnWidths(items);
        Table table = new Table(UnitValue.createPointArray(widths)).useAllAvailableWidth();
        String[] headers = {"SR. NO.", "HSN CODE", "PRODUCT DESCRIPTION", "QTY", "UNIT RATE", "UNIT", "AMOUNT (INR)"};
        for (String header : headers) table.addHeaderCell(columnHeader(header));
        for (TaxInvoiceItem item : items) {
            table.addCell(itemCell(String.valueOf(item.getSerialNo()), TextAlignment.CENTER, rowMinHeight));
            table.addCell(itemCell(dash(item.getHsn()), TextAlignment.CENTER, rowMinHeight));
            table.addCell(itemDescriptionCell(item, rowMinHeight));
            table.addCell(itemCell(number(item.getQuantity()), TextAlignment.CENTER, rowMinHeight));
            table.addCell(itemCell(money(item.getRate()), TextAlignment.RIGHT, rowMinHeight));
            table.addCell(itemCell(dash(item.getUnit()), TextAlignment.CENTER, rowMinHeight));
            table.addCell(itemCell(money(item.getGrossAmount()), TextAlignment.RIGHT, rowMinHeight));
        }
        return table;
    }

    /**
     * Content-aware item-table geometry. Non-description columns consume only the
     * width required by their header or widest value (within protective bounds).
     * The Product Description column is the sole flexible column and receives all
     * remaining table width.
     */
    private static float[] dynamicItemColumnWidths(List<TaxInvoiceItem> items) {
        final float tableWidth = PageSize.A4.getWidth() - 48f; // document left/right margins are 24pt
        final float cellSafety = 9f;

        float serial = textWidthEstimate("SR. NO.", FONT_TABLE_HEADER) + cellSafety;
        float hsn = textWidthEstimate("HSN CODE", FONT_TABLE_HEADER) + cellSafety;
        float qty = textWidthEstimate("QTY", FONT_TABLE_HEADER) + cellSafety;
        float rate = textWidthEstimate("UNIT RATE", FONT_TABLE_HEADER) + cellSafety;
        float unit = textWidthEstimate("UNIT", FONT_TABLE_HEADER) + cellSafety;
        float amount = textWidthEstimate("AMOUNT (INR)", FONT_TABLE_HEADER) + cellSafety;

        if (items != null) {
            for (TaxInvoiceItem item : items) {
                if (item == null) continue;
                serial = Math.max(serial, textWidthEstimate(String.valueOf(item.getSerialNo()), FONT_BODY_SMALL) + cellSafety);
                hsn = Math.max(hsn, textWidthEstimate(dash(item.getHsn()), FONT_BODY_SMALL) + cellSafety);
                qty = Math.max(qty, textWidthEstimate(number(item.getQuantity()), FONT_BODY_SMALL) + cellSafety);
                rate = Math.max(rate, textWidthEstimate(money(item.getRate()), FONT_BODY_SMALL) + cellSafety);
                unit = Math.max(unit, textWidthEstimate(dash(item.getUnit()), FONT_BODY_SMALL) + cellSafety);
                amount = Math.max(amount, textWidthEstimate(money(item.getGrossAmount()), FONT_BODY_SMALL) + cellSafety);
            }
        }

        serial = clamp(serial, 34f, 46f);
        hsn = clamp(hsn, 48f, 78f);
        qty = clamp(qty, 30f, 58f);
        rate = clamp(rate, 48f, 78f);
        unit = clamp(unit, 31f, 58f);
        amount = clamp(amount, 60f, 96f);

        final float minDescription = 180f;
        float fixed = serial + hsn + qty + rate + unit + amount;
        if (tableWidth - fixed < minDescription) {
            // Protect Description from being crushed by an unusually large numeric value.
            float targetFixed = tableWidth - minDescription;
            float scale = targetFixed / fixed;
            serial *= scale;
            hsn *= scale;
            qty *= scale;
            rate *= scale;
            unit *= scale;
            amount *= scale;
            fixed = serial + hsn + qty + rate + unit + amount;
        }

        float description = Math.max(minDescription, tableWidth - fixed);
        return new float[]{serial, hsn, description, qty, rate, unit, amount};
    }

    private static float textWidthEstimate(String value, float fontSize) {
        if (value == null || value.isBlank()) return 0f;
        float units = 0f;
        for (char ch : value.toCharArray()) {
            if (Character.isWhitespace(ch)) units += 0.28f;
            else if ("ilI1.,:'|".indexOf(ch) >= 0) units += 0.28f;
            else if ("MW@#%&".indexOf(ch) >= 0) units += 0.82f;
            else if (Character.isUpperCase(ch)) units += 0.62f;
            else units += 0.52f;
        }
        return units * fontSize;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float measureTableHeight(Document doc, Table table) {
        IRenderer renderer = table.createRendererSubTree();
        renderer.setParent(doc.getRenderer());
        float contentWidth = PageSize.A4.getWidth() - doc.getLeftMargin() - doc.getRightMargin();
        LayoutResult result = renderer.layout(new LayoutContext(
                new LayoutArea(1, new Rectangle(0, 0, contentWidth, PageSize.A4.getHeight()))));
        if (result.getOccupiedArea() == null) return 0f;
        return result.getOccupiedArea().getBBox().getHeight();
    }

    private static void addFillerRow(Table table, float height) {
        float rowHeight = Math.max(3f, height);
        for (int column = 0; column < 7; column++) {
            table.addCell(new Cell().setBorder(new SolidBorder(GRID, .45f))
                    .setPadding(0).setHeight(rowHeight)
                    .add(new Paragraph("").setMargin(0)));
        }
    }

    /**
     * Places the complete closing stack on the final page from the footer upward.
     * This removes the mixed positioning model that previously left a variable gap
     * between Terms/Signature and the fixed footer. Every lower section now uses
     * the same LOWER_SECTION_GAP between every lower closing section.
     */
    private static void addFixedClosingStack(Document doc, TaxInvoiceDocument invoice) {
        int pageNo = doc.getPdfDocument().getNumberOfPages();
        float contentWidth = PageSize.A4.getWidth() - doc.getLeftMargin() - doc.getRightMargin();
        float left = doc.getLeftMargin();

        Table financial = buildFinancialTable(invoice);
        Table closing = buildClosingTotalsTable(invoice);
        Table terms = buildTermsAndSignatureTable(invoice);

        // Use the exact same geometry contract used by finalItemCapacity(). This
        // guarantees Item frame bottom -> Bank/Calculation top = 5pt by construction.
        ClosingGeometry geometry = closingGeometry(doc, invoice);

        terms.setFixedPosition(pageNo, left, geometry.termsY(), contentWidth);
        closing.setFixedPosition(pageNo, left, geometry.closingY(), contentWidth);
        financial.setFixedPosition(pageNo, left, geometry.financialY(), contentWidth);

        doc.add(financial);
        doc.add(closing);
        doc.add(terms);
    }

    private static Table buildFinancialTable(TaxInvoiceDocument invoice) {
        Table outer = new Table(UnitValue.createPercentArray(new float[]{65, 2, 33}))
                .useAllAvailableWidth().setKeepTogether(true).setMargin(0);

        Cell left = roundedFilled(new Cell()
                .setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING)
                .setPaddingLeft(4).setPaddingRight(4).setBorder(Border.NO_BORDER), ColorConstants.WHITE);
        left.add(bankDetails(invoice));

        Cell right = roundedFilled(new Cell().setPadding(0).setBorder(Border.NO_BORDER), ColorConstants.WHITE);
        right.add(totalsTable(invoice));

        outer.addCell(left);
        outer.addCell(noBorder());
        outer.addCell(right);
        return outer;
    }

    private static Table buildClosingTotalsTable(TaxInvoiceDocument invoice) {
        Table closing = new Table(UnitValue.createPercentArray(new float[]{65, 2, 33}))
                .useAllAvailableWidth().setKeepTogether(true).setMargin(0);

        Table words = new Table(UnitValue.createPercentArray(new float[]{12, 88})).useAllAvailableWidth();
        words.addCell(noBorder().setFontColor(NAVY).setPaddingLeft(6)
                .setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING)
                .add(new Paragraph("INR :").setBold().setFontSize(7.1f).setMargin(0)));
        words.addCell(noBorder().setPaddingLeft(1).setPaddingRight(5)
                .setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING)
                .add(new Paragraph(stripInrPrefix(invoice.amountInWords())).setFontSize(6.9f).setMargin(0)));
        closing.addCell(roundedFilled(noBorder().setPadding(0).add(words), GREEN));
        closing.addCell(noBorder());

        // The 33% Grand Total card uses its full width: label at the left edge,
        // amount at the far right edge. No centered nested wrapper is used.
        Table grand = new Table(UnitValue.createPercentArray(new float[]{62, 38})).useAllAvailableWidth();
        grand.addCell(noBorder().setFontColor(NAVY).setTextAlignment(TextAlignment.LEFT)
                .setPaddingLeft(6).setPaddingRight(3)
                .setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING)
                .add(new Paragraph("G R A N D   T O T A L").setBold().setFontSize(7.2f).setMargin(0)));
        grand.addCell(noBorder().setFontColor(NAVY).setTextAlignment(TextAlignment.RIGHT)
                .setPaddingLeft(3).setPaddingRight(6)
                .setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING)
                .add(new Paragraph(money(invoice.totals().grandTotal())).setBold().setFontSize(8.1f).setMargin(0)));
        closing.addCell(roundedFilled(noBorder().setPadding(0).add(grand), GREEN));
        return closing;
    }

    private static Table bankDetails(TaxInvoiceDocument invoice) {
        CompanyProfile company = invoice.company();
        Table bank = new Table(UnitValue.createPercentArray(new float[]{29, 71})).useAllAvailableWidth();
        bank.setBorder(Border.NO_BORDER);
        addBankRowIfPresent(bank, "Supplier GST NO", company.gstin(), true);
        addBankRowIfPresent(bank, "BANK NAME", company.bankName(), false);
        addBankRowIfPresent(bank, "BRANCH", company.bankBranch(), false);
        addBankRowIfPresent(bank, "A/c NO", company.accountNumber(), false);
        addBankRowIfPresent(bank, "IFSC CODE", company.ifsc(), false);
        addBankRowIfPresent(bank, "ACCOUNT TYPE", company.accountType(), false);
        addBankRowIfPresent(bank, "PAYMENT MODE", company.paymentMode(), false);
        addBankRow(bank, "PAYMENT TERMS", paymentTermsDisplay(invoice), true);
        return bank;
    }

    private static void addBankRowIfPresent(Table bank, String label, String value, boolean highlight) {
        if (value != null && !value.isBlank()) addBankRow(bank,label,value,highlight);
    }

    private static void addBankRow(Table bank, String label, String value, boolean highlight) {
        bank.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setPaddingLeft(6).setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING)
                .add(new Paragraph(label).setBold().setFontSize(FONT_BODY_SMALL).setMargin(0)));
        Paragraph valueText = new Paragraph(":  " + dash(value)).setFontSize(FONT_BODY_SMALL).setMargin(0);
        if (highlight) valueText.setBold().setFontColor(NAVY);
        bank.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setPaddingRight(6).setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING).add(valueText));
    }

    private static Table totalsTable(TaxInvoiceDocument invoice) {
        InvoiceTotals totals = invoice.totals();
        Table table = new Table(UnitValue.createPercentArray(new float[]{64, 36})).useAllAvailableWidth();
        table.setBorder(Border.NO_BORDER);
        addTotalRow(table, "BASIC AMOUNT", totals.basicAmount());
        if (totals.discountAmount() > .004) addTotalRow(table, "DISCOUNT", totals.discountAmount());
        for (TaxInvoiceCharge charge : invoice.charges()) addTotalRow(table, charge.name().toUpperCase(Locale.ROOT), charge.amount());
        addTotalRow(table, "TAXABLE AMOUNT", totals.taxableAmount());

        List<Double> rates = new ArrayList<>();
        invoice.items().stream().mapToDouble(TaxInvoiceItem::getGstPercent).filter(rate->rate>0).forEach(rates::add);
        invoice.charges().stream().filter(TaxInvoiceCharge::taxable).mapToDouble(TaxInvoiceCharge::gstPercent).filter(rate->rate>0).forEach(rates::add);
        long distinctRates = rates.stream().map(rate->Math.round(rate*100d)).distinct().count();
        double gstRate = rates.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        boolean igstMode = invoice.gstType().toUpperCase(Locale.ROOT).contains("IGST") || invoice.gstType().toUpperCase(Locale.ROOT).contains("INTER");
        String rateText = distinctRates == 1 ? " @ " + percent(igstMode ? gstRate : gstRate/2d) : "";
        if (igstMode) addTotalRow(table, "IGST" + rateText, totals.igst());
        else {
            addTotalRow(table, "CGST" + rateText, totals.cgst());
            addTotalRow(table, "SGST" + rateText, totals.sgst());
        }
        addTotalRow(table, "ROUND OFF", totals.roundOff());
        return table;
    }

    private static void addTotalRow(Table table, String label, double amount) {
        boolean strong = "BASIC AMOUNT".equals(label) || "TAXABLE AMOUNT".equals(label);
        Cell labelCell = new Cell().setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(GRID, .28f))
                .setPaddingLeft(5).setPaddingRight(3).setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING);
        Paragraph labelText = new Paragraph(label).setFontSize(FONT_TOTAL).setMargin(0);
        if (strong) labelText.setBold();
        labelCell.add(labelText);

        Cell amountCell = new Cell().setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(GRID, .28f))
                .setTextAlignment(TextAlignment.RIGHT)
                .setPaddingLeft(3).setPaddingRight(5).setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING);
        Paragraph amountText = new Paragraph(zeroAsDashAmount(amount)).setFontSize(FONT_TOTAL).setMargin(0);
        if (strong) amountText.setBold();
        amountCell.add(amountText);
        table.addCell(labelCell);
        table.addCell(amountCell);
    }

    private static void addTotalRow(Table table, String label, double amount, boolean grand) {
        DeviceRgb fill = grand ? GREEN : VERY_PALE_BLUE;
        com.itextpdf.kernel.colors.Color text = grand ? ColorConstants.WHITE : ColorConstants.BLACK;
        table.addCell(new Cell().setBackgroundColor(fill).setFontColor(text)
                .setBorder(new SolidBorder(GRID, .5f)).setPadding(grand ? 4 : 3)
                .add(new Paragraph(label).setBold().setFontSize(grand ? 8 : 6.7f).setMargin(0)));
        table.addCell(new Cell().setBackgroundColor(fill).setFontColor(text)
                .setTextAlignment(TextAlignment.RIGHT).setBorder(new SolidBorder(GRID, .5f)).setPadding(grand ? 4 : 3)
                .add(new Paragraph(money(amount)).setBold().setFontSize(grand ? 8.4f : 6.7f).setMargin(0)));
    }

    private static Table buildTermsAndSignatureTable(TaxInvoiceDocument invoice) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{65, 2, 33}))
                .useAllAvailableWidth().setKeepTogether(true).setMargin(0);

        // Restore the original dynamic Terms & Conditions card. The table row itself
        // keeps Terms and Signature height-synchronised; no fixed height is introduced.
        Cell terms = roundedFilled(new Cell()
                .setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING)
                .setPaddingLeft(7).setPaddingRight(7).setBorder(Border.NO_BORDER), ColorConstants.WHITE);
        terms.add(new Paragraph("TERMS & CONDITIONS").setBold().setFontColor(NAVY).setFontSize(FONT_SECTION).setMarginBottom(5));
        String text = invoice.company().terms();
        if (text != null && !text.isBlank()) {
            terms.add(new Paragraph(text).setFontSize(FONT_TERMS).setFixedLeading(10.4f).setMargin(0));
        }
        table.addCell(terms);
        table.addCell(noBorder());

        // 7.3.8: Let the signature use the complete 33% card instead of the old
        // 4/40/4 inner table. Blank/transparent canvas around the source artwork is
        // trimmed in-memory only; the stored Settings asset is never changed.
        Cell signatureOuter = roundedFilled(new Cell()
                .setPaddingTop(COMPACT_VERTICAL_PADDING).setPaddingBottom(COMPACT_VERTICAL_PADDING)
                .setPaddingLeft(3).setPaddingRight(3).setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE),
                ColorConstants.WHITE);
        signatureOuter.add(new Paragraph("For, " + invoice.company().name()).setBold().setFontColor(NAVY)
                .setFontSize(8.8f).setTextAlignment(TextAlignment.CENTER).setMarginBottom(3));
        Image signatureImage = configuredSignatureImage(invoice.company().signaturePath());
        if (signatureImage != null) {
            signatureImage.scaleToFit(SIGNATURE_MAX_WIDTH, SIGNATURE_MAX_HEIGHT);
            signatureImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
            signatureOuter.add(signatureImage);
        } else {
            signatureOuter.add(new Paragraph(" ").setFontSize(20f).setMargin(0));
        }
        signatureOuter.add(new Paragraph("AUTHORIZED SIGNATORY").setBold().setFontSize(6.2f)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(2).setMarginBottom(0));
        table.addCell(signatureOuter);
        return table;
    }

    private static String paymentTermsDisplay(TaxInvoiceDocument invoice) {
        if (invoice.paymentTerms() != null && !invoice.paymentTerms().isBlank()) {
            return invoice.paymentTerms().trim();
        }
        if (invoice.invoiceDate() != null && invoice.poDate() != null) {
            long days = ChronoUnit.DAYS.between(invoice.invoiceDate(), invoice.poDate());
            if (days == 0) return "Due on Receipt";
            if (days > 0) return days + (days == 1 ? " Day" : " Days");
        }
        return "NA";
    }

    /**
     * Adds a compact page indicator only for multi-page invoices. The indicator is
     * overlaid inside the existing bottom-right blue footer bar after all pages have
     * been created, so the already-centered footer address is never moved or resized.
     */
    private static void addPageNumbers(Document doc) {
        int totalPages = doc.getPdfDocument().getNumberOfPages();
        if (totalPages <= 1) return;

        float pageRight = PageSize.A4.getWidth() - doc.getRightMargin();
        float left = pageRight - FOOTER_PAGE_NUMBER_WIDTH;

        for (int page = 1; page <= totalPages; page++) {
            Table pageBand = new Table(1).setWidth(FOOTER_PAGE_NUMBER_WIDTH).setHeight(7f);
            pageBand.addCell(noBorder()
                    .setPadding(0)
                    .setPaddingRight(2f)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .add(new Paragraph("Page " + page + " of " + totalPages)
                            .setTextAlignment(TextAlignment.RIGHT)
                            .setFontColor(ColorConstants.WHITE)
                            .setBold()
                            .setFontSize(4.8f)
                            .setFixedLeading(5.2f)
                            .setMargin(0)));
            pageBand.setFixedPosition(page, left, FOOTER_BAR_Y, FOOTER_PAGE_NUMBER_WIDTH);
            doc.add(pageBand);
        }
    }

    private static void addFooter(Document doc, CompanyProfile company) {
        // 5.0.4 footer polish: keep the footer geometry fixed and only protect the address text from overlap.
        // The footer remains anchored to the physical bottom of
        // the final A4 page instead of participating in normal document flow.
        // This keeps the same bottom position for single-page and multi-page
        // invoices while preserving a small printer-safe margin below the bar.
        int pageNo = doc.getPdfDocument().getNumberOfPages();
        float contentWidth = PageSize.A4.getWidth() - doc.getLeftMargin() - doc.getRightMargin();
        float left = doc.getLeftMargin();

        Table separator = new Table(1).useAllAvailableWidth();
        separator.addCell(new Cell().setHeight(1.2f).setBackgroundColor(BLUE).setBorder(Border.NO_BORDER));
        separator.setFixedPosition(pageNo, left, FOOTER_SEPARATOR_Y, contentWidth);
        doc.add(separator);

        // Keep the footer text inside its own fixed-height safe band. The band is shifted
        // upward inside the unchanged footer frame so the address cannot touch the
        // upper separator or the lower two-colour bar on Windows/macOS font metrics.
        Table addressBand = new Table(1).useAllAvailableWidth().setHeight(10.5f);
        addressBand.addCell(noBorder().setPaddingLeft(8f).setPaddingRight(8f)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .add(new Paragraph("Address : " + company.address()).setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(NAVY).setBold().setFontSize(5.8f).setFixedLeading(6.4f)
                        .setMargin(0)));
        addressBand.setFixedPosition(pageNo, left, FOOTER_ADDRESS_Y, contentWidth);
        doc.add(addressBand);

        Table stripes = new Table(UnitValue.createPercentArray(
                new float[]{FOOTER_DARK_PERCENT, FOOTER_BLUE_PERCENT}))
                .useAllAvailableWidth();
        stripes.addCell(new Cell().setHeight(7f).setBackgroundColor(NAVY).setBorder(Border.NO_BORDER));
        stripes.addCell(new Cell().setHeight(7f).setBackgroundColor(BLUE).setBorder(Border.NO_BORDER));
        stripes.setFixedPosition(pageNo, left, FOOTER_BAR_Y, contentWidth);
        doc.add(stripes);
    }

    private static Cell columnHeader(String text) {
        return new Cell().setBackgroundColor(NAVY).setFontColor(ColorConstants.WHITE)
                .setBorder(new SolidBorder(ColorConstants.WHITE, .35f)).setPaddingTop(3.2f).setPaddingBottom(3.2f).setPaddingLeft(2).setPaddingRight(2)
                .setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE)
                .add(new Paragraph(text).setBold().setFontSize(FONT_TABLE_HEADER).setMargin(0));
    }

    private static Cell itemDescriptionCell(TaxInvoiceItem item) {
        return itemDescriptionCell(item, 0f);
    }

    private static Cell itemDescriptionCell(TaxInvoiceItem item, float rowMinHeight) {
        Cell cell = new Cell().setBorder(new SolidBorder(GRID, .45f)).setPaddingTop(2.3f).setPaddingBottom(2.3f).setPaddingLeft(3).setPaddingRight(3)
                .setTextAlignment(TextAlignment.LEFT).setVerticalAlignment(VerticalAlignment.TOP);
        if (rowMinHeight > 0f) cell.setMinHeight(rowMinHeight);
        cell.add(new Paragraph(item.getRemarks()).setFontSize(FONT_ITEM_TITLE)
                .setFixedLeading(8.0f).setMargin(0));
        return cell;
    }

    private static Cell itemCell(String text, TextAlignment alignment) {
        return itemCell(text, alignment, 0f);
    }

    private static Cell itemCell(String text, TextAlignment alignment, float rowMinHeight) {
        Cell cell = new Cell().setBorder(new SolidBorder(GRID, .45f)).setPaddingTop(2.3f).setPaddingBottom(2.3f).setPaddingLeft(3).setPaddingRight(3)
                .setTextAlignment(alignment).setVerticalAlignment(VerticalAlignment.MIDDLE);
        if (rowMinHeight > 0f) cell.setMinHeight(rowMinHeight);
        return cell.add(new Paragraph(text == null ? "" : text).setFontSize(FONT_BODY_SMALL).setFixedLeading(8.0f).setMargin(0));
    }

    /**
     * Uses a Unicode font family that is visually close to the approved JASVI PDF.
     * Arial/Liberation Sans is preferred because it is visually closest to the approved
     * reference. DejaVu Sans remains the Unicode fallback for the rupee glyph. Fonts are loaded from the operating system, so no application font
     * resource is required and the rupee glyph remains available in generated PDFs.
     */
    private static void configureTypography(Document doc) {
        FontProvider provider = new FontProvider();
        String family = null;
        String[][] candidates = new String[][]{
                {"C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/arialbd.ttf", "Arial"},
                {"/System/Library/Fonts/Supplemental/Arial.ttf", "/System/Library/Fonts/Supplemental/Arial Bold.ttf", "Arial"},
                {"/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf", "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf", "Liberation Sans"},
                {"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "DejaVu Sans"},
                {"C:/Windows/Fonts/DejaVuSans.ttf", "C:/Windows/Fonts/DejaVuSans-Bold.ttf", "DejaVu Sans"}
        };
        for (String[] candidate : candidates) {
            try {
                if (Files.isRegularFile(Path.of(candidate[0]))) {
                    provider.addFont(candidate[0]);
                    if (Files.isRegularFile(Path.of(candidate[1]))) provider.addFont(candidate[1]);
                    family = candidate[2];
                    break;
                }
            } catch (Exception ignored) {
                // Try the next platform font.
            }
        }
        if (family == null) {
            provider.addStandardPdfFonts();
            family = "Helvetica";
        }
        doc.setFontProvider(provider);
        doc.setFontFamily(family);
    }

    private static Cell noBorder() {
        return new Cell().setBorder(Border.NO_BORDER).setPadding(0);
    }

    /** Adds the softly rounded outer card used by the approved JASVI design. */
    private static Cell rounded(Cell cell) {
        cell.setNextRenderer(new RoundedCellRenderer(cell, null));
        return cell;
    }

    private static Cell roundedFilled(Cell cell, Color fill) {
        cell.setNextRenderer(new RoundedCellRenderer(cell, fill));
        return cell;
    }

    private static class RoundedCellRenderer extends CellRenderer {
        private final Color fill;

        protected RoundedCellRenderer(Cell modelElement, Color fill) {
            super(modelElement);
            this.fill = fill;
        }

        @Override
        public IRenderer getNextRenderer() {
            return new RoundedCellRenderer((Cell) getModelElement(), fill);
        }

        @Override
        public void drawBackground(DrawContext drawContext) {
            if (fill == null) return;
            Rectangle box = getOccupiedAreaBBox();
            drawContext.getCanvas().saveState()
                    .setFillColor(fill)
                    .roundRectangle(box.getX(), box.getY(), box.getWidth(), box.getHeight(), 5f)
                    .fill()
                    .restoreState();
        }

        @Override
        public void drawBorder(DrawContext drawContext) {
            Rectangle box = getOccupiedAreaBBox();
            PdfCanvas canvas = drawContext.getCanvas();
            canvas.saveState()
                    .setStrokeColor(GRID)
                    .setLineWidth(.65f)
                    .roundRectangle(box.getX(), box.getY(), box.getWidth(), box.getHeight(), 5f)
                    .stroke()
                    .restoreState();
        }
    }


    private static Image configuredSignatureImage(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) return null;
        try {
            Path path = Path.of(configuredPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) return null;
            return new Image(ImageDataFactory.create(cachedSignatureBytes(path)));
        } catch (Exception ignored) {
            // A signature image must never make invoice generation fail.
            return configuredImage(configuredPath);
        }
    }

    private static byte[] cachedSignatureBytes(Path path) throws Exception {
        AssetCacheKey key = assetCacheKey(path, "signature-trim");
        byte[] cached = ASSET_IMAGE_CACHE.get(key);
        if (cached != null) return cached;

        BufferedImage source = ImageIO.read(path.toFile());
        byte[] prepared;
        if (source == null) {
            prepared = Files.readAllBytes(path);
        } else {
            int minX = source.getWidth();
            int minY = source.getHeight();
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int argb = source.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xff;
                    int red = (argb >>> 16) & 0xff;
                    int green = (argb >>> 8) & 0xff;
                    int blue = argb & 0xff;
                    boolean transparent = alpha <= 12;
                    boolean nearWhite = red >= 248 && green >= 248 && blue >= 248;
                    if (!transparent && !nearWhite) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }

            if (maxX < minX || maxY < minY) {
                prepared = Files.readAllBytes(path);
            } else {
                minX = Math.max(0, minX - SIGNATURE_TRIM_PADDING_PX);
                minY = Math.max(0, minY - SIGNATURE_TRIM_PADDING_PX);
                maxX = Math.min(source.getWidth() - 1, maxX + SIGNATURE_TRIM_PADDING_PX);
                maxY = Math.min(source.getHeight() - 1, maxY + SIGNATURE_TRIM_PADDING_PX);

                BufferedImage cropped = source.getSubimage(
                        minX, minY, maxX - minX + 1, maxY - minY + 1);
                try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    if (ImageIO.write(cropped, "png", bytes)) prepared = bytes.toByteArray();
                    else prepared = Files.readAllBytes(path);
                }
            }
        }

        cacheAssetBytes(key, prepared);
        return prepared;
    }

    private static Image configuredImage(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) return null;
        try {
            Path path = Path.of(configuredPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) return null;
            AssetCacheKey key = assetCacheKey(path, "raw");
            byte[] bytes = ASSET_IMAGE_CACHE.get(key);
            if (bytes == null) {
                bytes = Files.readAllBytes(path);
                cacheAssetBytes(key, bytes);
            }
            return new Image(ImageDataFactory.create(bytes));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static AssetCacheKey assetCacheKey(Path path, String variant) throws Exception {
        Path normalized = path.toAbsolutePath().normalize();
        return new AssetCacheKey(normalized.toString(), Files.size(normalized),
                Files.getLastModifiedTime(normalized).toMillis(), variant);
    }

    private static void cacheAssetBytes(AssetCacheKey key, byte[] bytes) {
        ASSET_IMAGE_CACHE.keySet().removeIf(existing ->
                existing.path().equals(key.path())
                        && existing.variant().equals(key.variant())
                        && !existing.equals(key));
        ASSET_IMAGE_CACHE.put(key, bytes);
    }

    private record AssetCacheKey(String path, long size, long modified, String variant) { }

    private static String formatDate(java.time.LocalDate value) {
        return value == null ? "-" : value.format(DATE);
    }

    private static String joinNonBlank(String separator, String... values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            if (!out.isEmpty()) out.append(separator);
            out.append(value.trim());
        }
        return out.toString();
    }

    private static String stripInrPrefix(String value) {
        if (value == null) return "";
        String text = value.trim();
        return text.regionMatches(true, 0, "INR :", 0, 5) ? text.substring(5).trim() : text;
    }




    private static String formatIndianPhone(String value) {
        if (value == null || value.isBlank()) return "";
        String text = value.trim();
        if (text.startsWith("+")) return text;
        String digits = text.replaceAll("\\D", "");
        if (digits.length() == 10) return "+91 " + digits;
        if (digits.length() == 12 && digits.startsWith("91")) return "+" + digits.substring(0, 2) + " " + digits.substring(2);
        return text;
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static String zeroAsDashAmount(double value) {
        return Math.abs(value) < 0.0000001 ? "-" : money(value);
    }

    private static String money(double value) {
        return String.format(Locale.of("en", "IN"), "%,.2f", value);
    }


    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0000001) return String.format(Locale.ROOT, "%.0f", value);
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String percent(double value) {
        return number(value) + "%";
    }
}
