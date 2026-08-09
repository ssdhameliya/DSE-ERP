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
import org.example.invoice.model.TaxInvoiceItem;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    // Fixed page-grid heights used by the deterministic pagination engine.
    // These are table-region heights (header + rendered item rows + filler rows).
    private static final float FIRST_FINAL_ITEM_REGION_HEIGHT = 193f;
    private static final float FIRST_CONTENT_ITEM_REGION_HEIGHT = 430f;
    private static final float CONTINUATION_FINAL_ITEM_REGION_HEIGHT = 500f;
    private static final float CONTINUATION_CONTENT_ITEM_REGION_HEIGHT = 744f;
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
    private static final float FONT_ITEM_TITLE = 6.9f;
    private static final float FONT_ITEM_REMARK = 6.35f;
    private static final float FONT_TOTAL = 6.45f;
    private static final float FONT_TERMS = 6.9f;
    private static final float CONTENT_WIDTH_PERCENT = 100f;
    private static final float STANDARD_SECTION_GAP = 5f;
    private static final float HEADER_TO_TITLE_GAP = 12f;
    private static final float FOOTER_RESERVED_BOTTOM = 31f;
    private static final float FOOTER_BAR_Y = 4f;
    private static final float FOOTER_ADDRESS_Y = 13f;
    private static final float FOOTER_SEPARATOR_Y = 27f;
    private static final float FOOTER_DARK_PERCENT = 48f;
    private static final float FOOTER_BLUE_PERCENT = 52f;

    private TaxInvoicePdfGenerator() {
    }

    public static Path generate(TaxInvoiceDocument invoice, Path output) throws Exception {
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        try (PdfWriter writer = new PdfWriter(output.toString());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {
            doc.setMargins(8, 24, FOOTER_RESERVED_BOTTOM, 24);
            configureTypography(doc);
            doc.setFontSize(7.0f);

            addCompanyHeader(doc, invoice.company());
            addInvoiceTitleAndMeta(doc, invoice);
            addAddressCards(doc, invoice);
            addTransportStrip(doc, invoice);
            addPaginatedItems(doc, invoice);
            addFinancialSection(doc, invoice);
            addTermsAndSignature(doc, invoice);
            addFooter(doc, invoice.company());
        }
        return output;
    }

    /**
     * Renders the approved invoice header from Settings. A wide uploaded artwork is
     * contained in the fixed header box; a normal logo is composed with company text.
     * Image dimensions can never grow the page layout.
     */
    private static void addCompanyHeader(Document doc, CompanyProfile company) {
        final float headerHeight = 82f;
        Image logo = configuredImage(company.logoPath());

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
                "ORDER NO", dash(invoice.orderNo())));
        metaCards.addCell(noBorder());
        metaCards.addCell(metaCard(
                "INVOICE DATE", formatDate(invoice.invoiceDate()),
                "PO DATE", formatDate(invoice.poDate())));
        doc.add(metaCards);
    }

    private static Cell metaCard(String label1, String value1, String label2, String value2) {
        Cell card = roundedFilled(new Cell().setPadding(7).setBorder(Border.NO_BORDER), PALE_BLUE);
        Table values = new Table(UnitValue.createPercentArray(new float[]{32, 5, 63})).useAllAvailableWidth();
        addSingleMetaRow(values, label1, value1);
        addSingleMetaRow(values, label2, value2);
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
        return noBorder().setPaddingTop(1.2f).setPaddingBottom(1.2f).setPaddingLeft(1).setPaddingRight(1).add(p);
    }

    private static void addAddressCards(Document doc, TaxInvoiceDocument invoice) {
        Table addresses = new Table(UnitValue.createPercentArray(new float[]{49, 2, 49}))
                .useAllAvailableWidth().setMarginBottom(STANDARD_SECTION_GAP);
        addresses.addCell(addressCard("BILLING ADDRESS", invoice.billing()));
        addresses.addCell(noBorder());
        addresses.addCell(addressCard("DELIVERY ADDRESS", invoice.delivery()));
        doc.add(addresses);
    }

    private static Cell addressCard(String heading, InvoiceParty party) {
        Cell card = roundedFilled(new Cell().setPadding(7).setBorder(Border.NO_BORDER), PALE_BLUE);
        card.add(new Paragraph(heading).setBold().setFontSize(FONT_SECTION).setFontColor(NAVY)
                .setMarginBottom(5));

        Cell content = noBorder().setPadding(0).setMinHeight(60);
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
        // Keep transport and contact data semantically separate. The sale already
        // stores Door Delivery and Vehicle Number independently, so the PDF must
        // not merge those values into the transporter name.
        Table strip = new Table(UnitValue.createPercentArray(new float[]{49, 2, 49}))
                .useAllAvailableWidth().setMarginTop(0).setMarginBottom(STANDARD_SECTION_GAP);
        strip.addCell(transportInfoCard(invoice));
        strip.addCell(noBorder());
        strip.addCell(contactInfoCard(invoice));
        doc.add(strip);
    }

    private static Cell transportInfoCard(TaxInvoiceDocument invoice) {
        Cell card = roundedFilled(new Cell().setPaddingTop(3).setPaddingBottom(3)
                .setPaddingLeft(7).setPaddingRight(7).setBorder(Border.NO_BORDER), PALE_BLUE);
        card.add(infoLine("TRANSPORTER", invoice.transporter()));
        Table secondLine = new Table(UnitValue.createPercentArray(new float[]{46, 54})).useAllAvailableWidth();
        secondLine.addCell(noBorder().add(infoLine("DELIVERY METHOD", yesNo(invoice.doorDelivery()))));
        secondLine.addCell(noBorder().add(infoLine("VEHICLE NUMBER", invoice.vehicleNumber())));
        card.add(secondLine);
        return card;
    }

    private static Cell contactInfoCard(TaxInvoiceDocument invoice) {
        Cell card = roundedFilled(new Cell().setPaddingTop(3).setPaddingBottom(3)
                .setPaddingLeft(7).setPaddingRight(7).setBorder(Border.NO_BORDER), PALE_BLUE);
        card.add(infoLine("CONTACT NAME", invoice.contactPerson()));
        card.add(infoLine("CONTACT NUMBER", formatIndianPhone(invoice.contactPersonMobile())));
        return card;
    }

    private static Paragraph infoLine(String label, String value) {
        return new Paragraph(label + " : " + dash(value))
                .setBold().setFontSize(FONT_BODY_SMALL).setFixedLeading(8.2f).setMargin(0);
    }

    /**
     * Deterministic 4.0.7 pagination. Row fitting is based on iText's actual
     * rendered table height, so long Product Description / Item Master Remark
     * rows are never treated as a fixed-count row. Closing blocks remain on the
     * final page and are anchored by a fixed-height item region filled with blank
     * grid rows when necessary.
     */
    private static void addPaginatedItems(Document doc, TaxInvoiceDocument invoice) {
        List<TaxInvoiceItem> items = new ArrayList<>(invoice.items());
        if (items.isEmpty()) return;

        // One-page invoices keep the approved fixed item region so every closing
        // block lands in the same place regardless of how many blank rows remain.
        if (fitsItems(doc, items, FIRST_FINAL_ITEM_REGION_HEIGHT)) {
            addItemsTable(doc, items, FIRST_FINAL_ITEM_REGION_HEIGHT);
            return;
        }

        int offset = 0;
        boolean firstPage = true;
        while (offset < items.size()) {
            List<TaxInvoiceItem> remaining = items.subList(offset, items.size());
            float finalCapacity = CONTINUATION_FINAL_ITEM_REGION_HEIGHT;

            // The first continuation page that can contain every remaining item
            // together with the fixed closing area becomes the final page.
            if (!firstPage && fitsItems(doc, remaining, finalCapacity)) {
                addItemsTable(doc, remaining, finalCapacity);
                return;
            }

            float contentCapacity = firstPage
                    ? FIRST_CONTENT_ITEM_REGION_HEIGHT
                    : CONTINUATION_CONTENT_ITEM_REGION_HEIGHT;

            // Fill every non-final page to its measured rendered-height capacity.
            // There is deliberately no balancing between pages: blank grid rows
            // belong only on the final page. Keep at least one complete item for
            // the final page so the closing page is never an empty item template.
            int fit = maxFittingCount(doc, remaining, contentCapacity);
            if (remaining.size() > 1) fit = Math.min(fit, remaining.size() - 1);
            fit = Math.max(1, fit);

            addContentItemsTable(doc, remaining.subList(0, fit));
            offset += fit;
            doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            addContinuationHeading(doc, invoice);
            firstPage = false;
        }
    }

    private static int maxFittingCount(Document doc, List<TaxInvoiceItem> items, float capacity) {
        int best = 0;
        for (int count = 1; count <= items.size(); count++) {
            if (measureItemsTableHeight(doc, items.subList(0, count)) <= capacity - LAYOUT_SAFETY) {
                best = count;
            } else {
                break;
            }
        }
        return best;
    }

    private static boolean fitsItems(Document doc, List<TaxInvoiceItem> items, float capacity) {
        return measureItemsTableHeight(doc, items) <= capacity - LAYOUT_SAFETY;
    }

    private static float measureItemsTableHeight(Document doc, List<TaxInvoiceItem> items) {
        Table table = buildItemsTable(items);
        IRenderer renderer = table.createRendererSubTree();
        renderer.setParent(doc.getRenderer());
        float contentWidth = PageSize.A4.getWidth() - doc.getLeftMargin() - doc.getRightMargin();
        LayoutResult result = renderer.layout(new LayoutContext(
                new LayoutArea(1, new Rectangle(0, 0, contentWidth, PageSize.A4.getHeight()))));
        if (result.getOccupiedArea() == null) return Float.MAX_VALUE;
        return result.getOccupiedArea().getBBox().getHeight();
    }

    private static void addContinuationHeading(Document doc, TaxInvoiceDocument invoice) {
        Table heading = new Table(UnitValue.createPercentArray(new float[]{65, 35}))
                .useAllAvailableWidth().setMarginBottom(6);
        heading.addCell(noBorder().add(new Paragraph("TAX INVOICE - CONTINUED")
                .setBold().setFontColor(NAVY).setFontSize(10).setMargin(0)));
        heading.addCell(noBorder().setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph(invoice.invoiceNo()).setBold().setFontColor(BLUE)
                        .setFontSize(9).setMargin(0)));
        doc.add(heading);
    }

    /** Renders a content-only page without artificial blank filler rows. */
    private static void addContentItemsTable(Document doc, List<TaxInvoiceItem> items) {
        Table table = buildItemsTable(items);
        Cell itemPanel = rounded(new Cell().setPadding(0).setBorder(Border.NO_BORDER).add(table));
        doc.add(new Table(1).useAllAvailableWidth().setMarginBottom(STANDARD_SECTION_GAP).addCell(itemPanel));
    }

    private static void addItemsTable(Document doc, List<TaxInvoiceItem> items, float targetHeight) {
        Table table = buildItemsTable(items);
        // Fill the unused item region with blank grid rows until the actual
        // rendered table height reaches the page template target. The final
        // closing sections therefore begin at a stable Y position.
        int guard = 0;
        while (guard++ < 80) {
            float measured = measureTableHeight(doc, table);
            float remaining = targetHeight - measured;
            if (remaining <= 1.2f) break;
            addFillerRow(table, Math.min(FILLER_ROW_HEIGHT, remaining));
        }

        Cell itemPanel = rounded(new Cell().setPadding(0).setBorder(Border.NO_BORDER).add(table));
        doc.add(new Table(1).useAllAvailableWidth().setMarginBottom(STANDARD_SECTION_GAP).addCell(itemPanel));
    }

    private static Table buildItemsTable(List<TaxInvoiceItem> items) {
        float[] widths = {7, 14, 39, 9, 12, 8, 14};
        Table table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
        String[] headers = {"SR. NO.", "HSN CODE", "PRODUCT DESCRIPTION", "QTY", "UNIT RATE", "UNIT", "AMOUNT (INR)"};
        for (String header : headers) table.addHeaderCell(columnHeader(header));
        for (TaxInvoiceItem item : items) {
            table.addCell(itemCell(String.valueOf(item.getSerialNo()), TextAlignment.CENTER));
            table.addCell(itemCell(dash(item.getHsn()), TextAlignment.CENTER));
            table.addCell(itemDescriptionCell(item));
            table.addCell(itemCell(number(item.getQuantity()), TextAlignment.CENTER));
            table.addCell(itemCell(rupees(item.getRate()), TextAlignment.RIGHT));
            table.addCell(itemCell(dash(item.getUnit()), TextAlignment.CENTER));
            table.addCell(itemCell(rupees(item.getGrossAmount()), TextAlignment.RIGHT));
        }
        return table;
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

    private static void addFinancialSection(Document doc, TaxInvoiceDocument invoice) {
        Table outer = new Table(UnitValue.createPercentArray(new float[]{49, 2, 49}))
                .useAllAvailableWidth().setKeepTogether(true).setMarginBottom(STANDARD_SECTION_GAP);

        Cell left = roundedFilled(noBorder().setPadding(4), PALE_BLUE);
        left.add(bankDetails(invoice.company()));

        Cell right = roundedFilled(noBorder().setPadding(0), PALE_BLUE);
        right.add(totalsTable(invoice));

        outer.addCell(left);
        outer.addCell(noBorder());
        outer.addCell(right);
        doc.add(outer);

        // Match the approved invoice: Amount in Words and Grand Total are two
        // independent bordered blocks, aligned to the same split as the bank
        // and calculation panels above.
        Table closing = new Table(UnitValue.createPercentArray(new float[]{49, 2, 49}))
                .useAllAvailableWidth().setKeepTogether(true).setMarginBottom(STANDARD_SECTION_GAP);

        Table words = new Table(UnitValue.createPercentArray(new float[]{12, 88})).useAllAvailableWidth();
        words.addCell(noBorder().setFontColor(NAVY).setPaddingLeft(6).setPaddingTop(4).setPaddingBottom(4)
                .add(new Paragraph("INR :").setBold().setFontSize(7.1f).setMargin(0)));
        words.addCell(noBorder().setPaddingLeft(1).setPaddingRight(5).setPaddingTop(4).setPaddingBottom(4)
                .add(new Paragraph(stripInrPrefix(invoice.amountInWords())).setFontSize(6.9f).setMargin(0)));
        closing.addCell(roundedFilled(noBorder().setPadding(0).add(words), GREEN));
        closing.addCell(noBorder());

        Table grand = new Table(UnitValue.createPercentArray(new float[]{67, 33})).useAllAvailableWidth();
        grand.addCell(noBorder().setFontColor(NAVY).setPaddingLeft(7).setPaddingTop(4).setPaddingBottom(4)
                .add(new Paragraph("G R A N D   T O T A L").setBold().setFontSize(7.2f).setMargin(0)));
        grand.addCell(noBorder().setFontColor(NAVY).setTextAlignment(TextAlignment.RIGHT)
                .setPaddingRight(6).setPaddingTop(4).setPaddingBottom(4)
                .add(new Paragraph(rupees(invoice.totals().grandTotal())).setBold().setFontSize(8.1f).setMargin(0)));
        closing.addCell(roundedFilled(noBorder().setPadding(0).add(grand), GREEN));
        doc.add(closing);
    }

    private static Table bankDetails(CompanyProfile company) {
        Table bank = new Table(UnitValue.createPercentArray(new float[]{29, 71})).useAllAvailableWidth();
        bank.setBorder(Border.NO_BORDER);
        addBankRow(bank, "Supplier GST NO", company.gstin(), true);
        addBankRow(bank, "BANK NAME", company.bankName(), false);
        addBankRow(bank, "BRANCH", company.bankBranch(), false);
        addBankRow(bank, "A/c NO", company.accountNumber(), false);
        addBankRow(bank, "IFSC CODE", company.ifsc(), false);
        addBankRow(bank, "ACCOUNT TYPE", company.accountType(), false);
        addBankRow(bank, "PAYMENT MODE", company.paymentMode(), false);
        return bank;
    }

    private static void addBankRow(Table bank, String label, String value, boolean highlight) {
        bank.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setPaddingLeft(6).setPaddingTop(1.35f).setPaddingBottom(1.35f)
                .add(new Paragraph(label).setBold().setFontSize(FONT_BODY_SMALL).setMargin(0)));
        Paragraph valueText = new Paragraph(":  " + dash(value)).setFontSize(FONT_BODY_SMALL).setMargin(0);
        if (highlight) valueText.setBold().setFontColor(NAVY);
        bank.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setPaddingRight(6).setPaddingTop(1.35f).setPaddingBottom(1.35f).add(valueText));
    }

    private static Table totalsTable(TaxInvoiceDocument invoice) {
        InvoiceTotals totals = invoice.totals();
        Table table = new Table(UnitValue.createPercentArray(new float[]{64, 36})).useAllAvailableWidth();
        table.setBorder(Border.NO_BORDER);
        addTotalRow(table, "BASIC AMOUNT", totals.basicAmount());
        addTotalRow(table, "DISCOUNT @ NIL", totals.discountAmount());
        addTotalRow(table, "FREIGHT CHARGES @ EXTRA", totals.freightCharges());
        addTotalRow(table, "GROSS TOTAL", totals.grossTotal());

        double gstRate = invoice.items().stream().mapToDouble(TaxInvoiceItem::getGstPercent).max().orElse(0);
        addTotalRow(table, "CGST @ " + percent(gstRate / 2), totals.cgst());
        addTotalRow(table, "SGST @ " + percent(gstRate / 2), totals.sgst());
        addTotalRow(table, "IGST @ " + percent(gstRate), totals.igst());
        addTotalRow(table, "ROUND OFF", totals.roundOff());
        return table;
    }

    private static void addTotalRow(Table table, String label, double amount) {
        boolean strong = "BASIC AMOUNT".equals(label) || "GROSS TOTAL".equals(label);
        Cell labelCell = new Cell().setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(GRID, .28f))
                .setPaddingLeft(5).setPaddingRight(3).setPaddingTop(1.65f).setPaddingBottom(1.65f);
        Paragraph labelText = new Paragraph(label).setFontSize(FONT_TOTAL).setMargin(0);
        if (strong) labelText.setBold();
        labelCell.add(labelText);

        Cell amountCell = new Cell().setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(GRID, .28f))
                .setTextAlignment(TextAlignment.RIGHT)
                .setPaddingLeft(3).setPaddingRight(5).setPaddingTop(1.65f).setPaddingBottom(1.65f);
        Paragraph amountText = new Paragraph(zeroAsDashRupees(amount)).setFontSize(FONT_TOTAL).setMargin(0);
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
                .add(new Paragraph("₹ " + money(amount)).setBold().setFontSize(grand ? 8.4f : 6.7f).setMargin(0)));
    }

    private static void addTermsAndSignature(Document doc, TaxInvoiceDocument invoice) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{49, 2, 49}))
                .useAllAvailableWidth().setKeepTogether(true).setMarginTop(0);

        Cell terms = roundedFilled(new Cell().setPadding(7).setMinHeight(84)
                .setBorder(Border.NO_BORDER), PALE_YELLOW);
        terms.add(new Paragraph("TERMS & CONDITIONS").setBold().setFontColor(NAVY).setFontSize(FONT_SECTION).setMarginBottom(5));
        String text = invoice.company().terms();
        terms.add(new Paragraph(text).setFontSize(FONT_TERMS).setFixedLeading(10.4f).setMargin(0));

        Cell signature = roundedFilled(new Cell().setPadding(6).setMinHeight(84).setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER), VERY_PALE_BLUE);
        signature.add(new Paragraph("For, " + invoice.company().name()).setBold().setFontColor(NAVY)
                .setFontSize(8.8f).setMarginBottom(3));
        Image signatureImage = configuredImage(invoice.company().signaturePath());
        if (signatureImage != null) {
            signatureImage.scaleToFit(120f, 42f);
            signatureImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
            signature.add(signatureImage);
        } else {
            signature.add(new Paragraph("\n\n\n").setMargin(0));
        }
        signature.add(new Paragraph("AUTHORIZED SIGNATORY").setBold().setFontSize(6.2f).setMarginTop(2).setMarginBottom(0));

        table.addCell(terms);
        table.addCell(noBorder());
        table.addCell(signature);
        doc.add(table);
    }

    private static void addFooter(Document doc, CompanyProfile company) {
        // 4.0.7 final polish: the footer is anchored to the physical bottom of
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

        Paragraph address = new Paragraph(company.address()).setTextAlignment(TextAlignment.LEFT)
                .setFontColor(NAVY).setBold().setFontSize(FONT_BODY_SMALL).setFixedLeading(7.2f)
                .setMargin(0);
        address.setFixedPosition(pageNo, left + 8f, FOOTER_ADDRESS_Y, contentWidth - 16f);
        doc.add(address);

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
        Cell cell = new Cell().setBorder(new SolidBorder(GRID, .45f)).setPaddingTop(2.3f).setPaddingBottom(2.3f).setPaddingLeft(3).setPaddingRight(3)
                .setTextAlignment(TextAlignment.LEFT).setVerticalAlignment(VerticalAlignment.TOP);
        cell.add(new Paragraph(item.getDescription()).setBold().setFontSize(FONT_ITEM_TITLE)
                .setFixedLeading(8.3f).setMargin(0));
        if (!item.getRemarks().isBlank()) {
            cell.add(new Paragraph(item.getRemarks()).setFontSize(FONT_ITEM_REMARK)
                    .setFixedLeading(7.8f).setMarginTop(1).setMarginBottom(0));
        }
        return cell;
    }

    private static Cell itemCell(String text, TextAlignment alignment) {
        return new Cell().setBorder(new SolidBorder(GRID, .45f)).setPaddingTop(2.3f).setPaddingBottom(2.3f).setPaddingLeft(3).setPaddingRight(3)
                .setTextAlignment(alignment).setVerticalAlignment(VerticalAlignment.MIDDLE)
                .add(new Paragraph(text == null ? "" : text).setFontSize(FONT_BODY_SMALL).setFixedLeading(8.0f).setMargin(0));
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

    private static Image classpathImage(String resource) {
        try (InputStream input = TaxInvoicePdfGenerator.class.getResourceAsStream(resource)) {
            return input == null ? null : new Image(ImageDataFactory.create(input.readAllBytes()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Image configuredImage(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) return null;
        try {
            Path path = Path.of(configuredPath).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? new Image(ImageDataFactory.create(path.toString())) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

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

    private static String zeroAsDashPlain(double value) {
        return Math.abs(value) < 0.0000001 ? "-" : money(value);
    }

    private static String stateFromAddress(String address) {
        if (address == null || address.isBlank()) return "-";
        String[] parts = address.split(",");
        return parts.length < 2 ? address.trim() : parts[parts.length - 2].trim();
    }

    private static String yesNo(String value) {
        if (value == null || value.isBlank()) return "No";
        String text = value.trim();
        if (text.equalsIgnoreCase("yes") || text.equalsIgnoreCase("y") || text.equalsIgnoreCase("true") || text.equals("1")) return "Yes";
        if (text.equalsIgnoreCase("no") || text.equalsIgnoreCase("n") || text.equalsIgnoreCase("false") || text.equals("0")) return "No";
        return text;
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

    private static String rupees(double value) {
        return "₹ " + money(value);
    }

    private static String zeroAsDashRupees(double value) {
        return Math.abs(value) < 0.0000001 ? "-" : rupees(value);
    }

    private static String money(double value) {
        return String.format(Locale.of("en", "IN"), "%,.2f", value);
    }

    private static String zeroAsDash(double value) {
        return Math.abs(value) < 0.0000001 ? "-" : "INR " + money(value);
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0000001) return String.format(Locale.ROOT, "%.0f", value);
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String percent(double value) {
        return number(value) + "%";
    }
}
