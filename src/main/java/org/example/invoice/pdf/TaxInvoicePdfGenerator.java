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
    private static final int FINAL_PAGE_ITEM_ROWS = 6;
    private static final int CONTENT_ONLY_PAGE_ROWS = 18;

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
    private static final float TITLE_WIDTH_PERCENT = 96.2f;
    private static final float FOOTER_DARK_PERCENT = 48f;
    private static final float FOOTER_BLUE_PERCENT = 52f;

    private TaxInvoicePdfGenerator() {
    }

    public static Path generate(TaxInvoiceDocument invoice, Path output) throws Exception {
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        try (PdfWriter writer = new PdfWriter(output.toString());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {
            doc.setMargins(8, 24, 8, 24);
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
            logo.setMarginBottom(27f);
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
        doc.add(new Table(1).useAllAvailableWidth().setMarginTop(2).setMarginBottom(28)
                .addCell(new Cell().setHeight(1).setBorder(Border.NO_BORDER).setBackgroundColor(GRID)));
    }

    private static void addInvoiceTitleAndMeta(Document doc, TaxInvoiceDocument invoice) {
        Table title = new Table(1).setWidth(UnitValue.createPercentValue(TITLE_WIDTH_PERCENT))
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
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

        Cell metaCell = roundedFilled(new Cell().setPadding(6).setBorder(Border.NO_BORDER), PALE_BLUE);
        metaCell.setNextRenderer(new MetaCellRenderer(metaCell, PALE_BLUE));
        Table meta = new Table(UnitValue.createPercentArray(new float[]{16, 3, 31, 16, 3, 31})).useAllAvailableWidth();
        addMetaPair(meta, "INVOICE NO", invoice.invoiceNo(), "INVOICE DATE", formatDate(invoice.invoiceDate()));
        addMetaPair(meta, "ORDER NO", dash(invoice.orderNo()), "PO DATE", formatDate(invoice.poDate()));
        metaCell.add(meta);
        doc.add(new Table(1).useAllAvailableWidth().setMarginTop(5).setMarginBottom(7).addCell(metaCell));
    }

    private static void addMetaPair(Table table, String leftLabel, String leftValue,
                                    String rightLabel, String rightValue) {
        table.addCell(metaText(leftLabel, true));
        table.addCell(metaText(":", true));
        table.addCell(metaText(leftValue, false).setPaddingRight(7));
        table.addCell(metaText(rightLabel, true).setPaddingLeft(7));
        table.addCell(metaText(":", true));
        table.addCell(metaText(rightValue, false));
    }

    private static Cell metaText(String value, boolean bold) {
        Paragraph p = new Paragraph(value == null ? "" : value).setFontSize(FONT_META).setMargin(0);
        if (bold) p.setBold();
        return noBorder().setPadding(1).add(p);
    }

    private static void addAddressCards(Document doc, TaxInvoiceDocument invoice) {
        Table addresses = new Table(UnitValue.createPercentArray(new float[]{49, 2, 49}))
                .useAllAvailableWidth().setMarginBottom(4);
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
        String transporter = dash(invoice.transporter());
        if (!invoice.vehicleNumber().isBlank()) {
            transporter += " - " + invoice.vehicleNumber();
        }

        // The reference invoice presents both values inside one continuous
        // strip. The outer cell owns the shared border/background, while the
        // inner table contributes only the single centre divider.
        Table values = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .useAllAvailableWidth();
        values.addCell(sharedStripValue("TRANSPORTER", transporter, true));
        String contact = dash(invoice.contactPerson());
        if (!invoice.contactPersonMobile().isBlank()) {
            contact += " - " + invoice.contactPersonMobile();
        }
        values.addCell(sharedStripValue("CONTACT PERSON", contact, false));

        Cell sharedPanel = roundedFilled(new Cell()
                .setPadding(1)
                .setBorder(Border.NO_BORDER)
                .add(values), PALE_BLUE);

        Table strip = new Table(1)
                .useAllAvailableWidth()
                .setMarginTop(1)
                .setMarginBottom(6);
        strip.addCell(sharedPanel);
        doc.add(strip);
    }

    private static Cell sharedStripValue(String label, String value, boolean addCentreDivider) {
        Cell cell = new Cell()
                .setPaddingTop(3)
                .setPaddingBottom(3)
                .setPaddingLeft(5)
                .setPaddingRight(5)
                .setBorder(Border.NO_BORDER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .add(new Paragraph(label + " : " + dash(value))
                        .setBold()
                        .setFontSize(FONT_BODY)
                        .setMargin(0));
        if (addCentreDivider) {
            cell.setBorderRight(new SolidBorder(GRID, .65f));
        }
        return cell;
    }

    /**
     * Keeps the financial and authorization blocks at the bottom of the final
     * page. Item-only pages are emitted first; the last page always contains a
     * seven-row item area (real rows plus blank rows) followed by the fixed
     * invoice closing blocks. Every continuation page repeats the table header.
     */
    private static void addPaginatedItems(Document doc, TaxInvoiceDocument invoice) {
        List<TaxInvoiceItem> items = new ArrayList<>(invoice.items());
        if (items.size() <= FINAL_PAGE_ITEM_ROWS) {
            addItemsTable(doc, items, invoice, FINAL_PAGE_ITEM_ROWS - items.size());
            return;
        }

        int offset = 0;
        while (items.size() - offset > FINAL_PAGE_ITEM_ROWS) {
            int rowsBeforeFinalPage = items.size() - offset - FINAL_PAGE_ITEM_ROWS;
            int pageRows = Math.min(CONTENT_ONLY_PAGE_ROWS, rowsBeforeFinalPage);
            addItemsTable(doc, items.subList(offset, offset + pageRows), invoice, 0);
            offset += pageRows;
            doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            addContinuationHeading(doc, invoice);
        }

        List<TaxInvoiceItem> finalItems = items.subList(offset, items.size());
        addItemsTable(doc, finalItems, invoice, FINAL_PAGE_ITEM_ROWS - finalItems.size());
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

    private static void addItemsTable(Document doc, List<TaxInvoiceItem> items,
                                      TaxInvoiceDocument invoice, int fillerRows) {
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
        for (int i = 0; i < fillerRows; i++) {
            table.addCell(itemCell("", TextAlignment.CENTER).setMinHeight(18));
            table.addCell(itemCell("", TextAlignment.CENTER));
            table.addCell(itemCell("", TextAlignment.LEFT));
            table.addCell(itemCell("", TextAlignment.CENTER));
            table.addCell(itemCell("", TextAlignment.RIGHT));
            table.addCell(itemCell("", TextAlignment.CENTER));
            table.addCell(itemCell("", TextAlignment.RIGHT));
        }
        Cell itemPanel = rounded(new Cell().setPadding(0).setBorder(Border.NO_BORDER).add(table));
        doc.add(new Table(1).useAllAvailableWidth().setMarginBottom(5).addCell(itemPanel));
    }

    private static void addFinancialSection(Document doc, TaxInvoiceDocument invoice) {
        Table outer = new Table(UnitValue.createPercentArray(new float[]{55, 1.5f, 43.5f}))
                .useAllAvailableWidth().setKeepTogether(true).setMarginBottom(5);

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
        Table closing = new Table(UnitValue.createPercentArray(new float[]{55, 1.5f, 43.5f}))
                .useAllAvailableWidth().setKeepTogether(true);

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
        Table table = new Table(UnitValue.createPercentArray(new float[]{62, 38}))
                .useAllAvailableWidth().setKeepTogether(true).setMarginTop(4);

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
        table.addCell(signature);
        doc.add(table);
    }

    private static void addFooter(Document doc, CompanyProfile company) {
        // Footer geometry uses the same document content guides as every main
        // invoice block. No percentage inset is allowed here; this was the
        // source of the visible left/right mismatch in the previous build.
        Table separator = new Table(1).useAllAvailableWidth().setMarginTop(3);
        separator.addCell(new Cell().setHeight(1).setBackgroundColor(BLUE).setBorder(Border.NO_BORDER));
        doc.add(separator);

        doc.add(new Paragraph(company.address()).setTextAlignment(TextAlignment.LEFT)
                .setFontColor(NAVY).setBold().setFontSize(FONT_BODY_SMALL)
                .setMarginLeft(8).setMarginRight(8).setMarginTop(4).setMarginBottom(4));

        Table stripes = new Table(UnitValue.createPercentArray(
                new float[]{FOOTER_DARK_PERCENT, FOOTER_BLUE_PERCENT}))
                .useAllAvailableWidth();
        stripes.addCell(new Cell().setHeight(6).setBackgroundColor(NAVY).setBorder(Border.NO_BORDER));
        stripes.addCell(new Cell().setHeight(6).setBackgroundColor(BLUE).setBorder(Border.NO_BORDER));
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

    /** Draws the metadata centre divider as one continuous top-to-bottom line. */
    private static final class MetaCellRenderer extends RoundedCellRenderer {
        private MetaCellRenderer(Cell modelElement, Color fill) {
            super(modelElement, fill);
        }

        @Override
        public IRenderer getNextRenderer() {
            return new MetaCellRenderer((Cell) getModelElement(), PALE_BLUE);
        }

        @Override
        public void drawBorder(DrawContext drawContext) {
            super.drawBorder(drawContext);
            Rectangle box = getOccupiedAreaBBox();
            float x = box.getX() + box.getWidth() / 2f;
            drawContext.getCanvas().saveState()
                    .setStrokeColor(GRID)
                    .setLineWidth(.75f)
                    .moveTo(x, box.getY())
                    .lineTo(x, box.getY() + box.getHeight())
                    .stroke()
                    .restoreState();
        }
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
