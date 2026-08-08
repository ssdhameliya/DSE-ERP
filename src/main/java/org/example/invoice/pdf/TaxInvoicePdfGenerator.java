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
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.layout.renderer.CellRenderer;
import com.itextpdf.layout.renderer.DrawContext;
import com.itextpdf.layout.renderer.IRenderer;
import org.example.config.ConfigManager;
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
    private static final DeviceRgb PALE_BLUE = new DeviceRgb(231, 240, 250);
    private static final DeviceRgb VERY_PALE_BLUE = new DeviceRgb(246, 249, 253);
    private static final DeviceRgb GREEN = new DeviceRgb(39, 158, 91);
    private static final DeviceRgb PALE_YELLOW = new DeviceRgb(255, 249, 218);
    private static final DeviceRgb GRID = new DeviceRgb(117, 153, 198);
    private static final DeviceRgb MUTED = new DeviceRgb(78, 90, 108);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final int FINAL_PAGE_ITEM_ROWS = 7;
    private static final int CONTENT_ONLY_PAGE_ROWS = 18;

    private TaxInvoicePdfGenerator() {
    }

    public static Path generate(TaxInvoiceDocument invoice, Path output) throws Exception {
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        try (PdfWriter writer = new PdfWriter(output.toString());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {
            doc.setMargins(8, 18, 8, 18);
            doc.setFontSize(7.2f);

            addJasviHeader(doc, invoice.company());
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

    /** Uses the approved full-width header artwork, not the legacy app logo. */
    private static void addJasviHeader(Document doc, CompanyProfile company) {
        Image header = classpathImage("/pdf/jasvi/company-header.png");
        if (header != null) {
            header.setWidth(UnitValue.createPercentValue(100));
            header.setMarginBottom(16);
            doc.add(header);
            return;
        }

        // Safe fallback for installations where the bundled artwork is missing.
        Table fallback = new Table(UnitValue.createPercentArray(new float[]{65, 35})).useAllAvailableWidth();
        fallback.addCell(noBorder().add(new Paragraph(company.name()).setBold().setFontSize(22).setFontColor(NAVY)));
        fallback.addCell(noBorder().setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph(company.email() + "\n" + company.phone()).setBold().setFontSize(7)));
        doc.add(fallback);
    }

    private static void addInvoiceTitleAndMeta(Document doc, TaxInvoiceDocument invoice) {
        Table title = new Table(1).useAllAvailableWidth();
        Table titleLine = new Table(UnitValue.createPercentArray(new float[]{20, 60, 20}))
                .useAllAvailableWidth();
        titleLine.addCell(noBorder().setBackgroundColor(NAVY));
        titleLine.addCell(noBorder().setBackgroundColor(NAVY)
                .setTextAlignment(TextAlignment.CENTER)
                .add(new Paragraph("TAX INVOICE").setBold().setFontSize(15)
                        .setFontColor(ColorConstants.WHITE).setPadding(5).setMargin(0)));
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
        table.addCell(metaText(leftValue, false));
        table.addCell(metaText(rightLabel, true));
        table.addCell(metaText(":", true));
        table.addCell(metaText(rightValue, false));
    }

    private static Cell metaText(String value, boolean bold) {
        Paragraph p = new Paragraph(value == null ? "" : value).setFontSize(6.6f).setMargin(0);
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
        card.add(new Paragraph(heading).setBold().setFontSize(7.5f).setFontColor(BLUE)
                .setMarginBottom(5));

        Cell content = noBorder().setPadding(0).setMinHeight(68);
        content.add(new Paragraph(party.name()).setBold().setFontSize(7.8f).setMarginBottom(2));
        if (!party.address().isBlank()) {
            content.add(new Paragraph(party.address()).setFontSize(6.8f).setFixedLeading(8.5f).setMarginBottom(2));
        }
        if (!party.contactPerson().isBlank()) {
            content.add(detailLine("Contact", party.contactPerson()));
        }
        if (!party.phone().isBlank()) {
            content.add(detailLine("Phone", party.phone()));
        }
        content.add(detailLine("GST-IN", dash(party.gstin())).setBold());
        card.add(content);
        return card;
    }

    private static Paragraph detailLine(String label, String value) {
        return new Paragraph(label + " : " + value).setFontSize(6.7f).setMargin(0).setFixedLeading(8.4f);
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
        values.addCell(sharedStripValue("CONTACT PERSON", invoice.contactPerson(), false));

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
                        .setFontSize(6.3f)
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
            table.addCell(itemCell(item.getDescription(), TextAlignment.LEFT));
            table.addCell(itemCell(number(item.getQuantity()), TextAlignment.CENTER));
            table.addCell(itemCell(money(item.getRate()), TextAlignment.RIGHT));
            table.addCell(itemCell(dash(item.getUnit()), TextAlignment.CENTER));
            table.addCell(itemCell(money(item.getGrossAmount()), TextAlignment.RIGHT));
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
        Table outer = new Table(UnitValue.createPercentArray(new float[]{57, 1.5f, 41.5f}))
                .useAllAvailableWidth().setKeepTogether(true).setMarginBottom(5);

        Cell left = roundedFilled(noBorder().setPadding(4), PALE_BLUE);
        left.add(bankDetails(invoice.company()));

        Cell right = roundedFilled(noBorder().setPadding(0), PALE_BLUE);
        right.add(totalsTable(invoice));

        outer.addCell(left);
        outer.addCell(noBorder());
        outer.addCell(right);
        doc.add(outer);

        Table words = new Table(UnitValue.createPercentArray(new float[]{22, 48, 17, 13}))
                .useAllAvailableWidth().setKeepTogether(true);
        words.addCell(new Cell().setFontColor(ColorConstants.WHITE).setBorder(Border.NO_BORDER).setPadding(4)
                .add(new Paragraph("AMOUNT IN WORDS").setBold().setFontSize(7.2f).setMargin(0)));
        words.addCell(new Cell().setBackgroundColor(ColorConstants.WHITE).setBorder(Border.NO_BORDER).setPadding(4)
                .add(new Paragraph(invoice.amountInWords()).setBold().setFontSize(7.2f).setMargin(0)));
        words.addCell(new Cell().setFontColor(ColorConstants.WHITE).setBorder(Border.NO_BORDER).setPadding(4)
                .add(new Paragraph("GRAND TOTAL").setBold().setFontSize(7.2f).setMargin(0)));
        words.addCell(new Cell().setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER).setPadding(4)
                .add(new Paragraph("INR " + money(invoice.totals().grandTotal()))
                        .setBold().setFontSize(7.7f).setMargin(0)));
        Cell wordsPanel = roundedFilled(noBorder().setPadding(0).add(words), GREEN);
        doc.add(new Table(1).useAllAvailableWidth().addCell(wordsPanel));
    }

    private static Table bankDetails(CompanyProfile company) {
        Table bank = new Table(UnitValue.createPercentArray(new float[]{29, 71})).useAllAvailableWidth();
        bank.setBorder(Border.NO_BORDER);
        bank.addCell(new Cell(1, 2).setFontColor(NAVY)
                .setBorder(Border.NO_BORDER).setPaddingTop(5).setPaddingLeft(6).setPaddingBottom(4)
                .add(new Paragraph("BANK DETAILS").setBold().setFontSize(7.6f).setMargin(0)));
        addBankRow(bank, "Supplier GST No.", company.gstin(), true);
        addBankRow(bank, "Bank Name", company.bankName(), false);
        addBankRow(bank, "Branch", company.bankBranch(), false);
        addBankRow(bank, "A/c No.", company.accountNumber(), false);
        addBankRow(bank, "IFSC Code", company.ifsc(), false);
        addBankRow(bank, "Account Type", company.accountType(), false);
        addBankRow(bank, "Payment Mode", company.paymentMode(), false);
        return bank;
    }

    private static void addBankRow(Table bank, String label, String value, boolean highlight) {
        bank.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setPaddingLeft(6).setPaddingTop(2.4f).setPaddingBottom(2.4f)
                .add(new Paragraph(label).setBold().setFontSize(6.5f).setMargin(0)));
        Paragraph valueText = new Paragraph(":  " + dash(value)).setFontSize(6.5f).setMargin(0);
        if (highlight) valueText.setBold().setFontColor(NAVY);
        bank.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setPaddingRight(6).setPaddingTop(2.4f).setPaddingBottom(2.4f).add(valueText));
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
        table.addCell(new Cell()
                .setBorder(new SolidBorder(GRID, .5f)).setPadding(3)
                .add(new Paragraph(label).setBold().setFontSize(6.7f).setMargin(0)));
        table.addCell(new Cell()
                .setTextAlignment(TextAlignment.RIGHT).setBorder(new SolidBorder(GRID, .5f)).setPadding(3)
                .add(new Paragraph("INR " + money(amount)).setBold().setFontSize(6.7f).setMargin(0)));
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
                .useAllAvailableWidth().setKeepTogether(true).setMarginTop(5);

        Cell terms = roundedFilled(new Cell().setPadding(7).setMinHeight(91)
                .setBorder(Border.NO_BORDER), PALE_YELLOW);
        terms.add(new Paragraph("TERMS & CONDITIONS").setBold().setFontColor(NAVY).setFontSize(7.8f).setMarginBottom(4));
        String text = invoice.company().terms();
        if (text.isBlank()) {
            text = "1. Goods once sold will not be taken back.\n"
                    + "2. Payment is due within the agreed credit period.\n"
                    + "3. Interest may apply on overdue balances.\n"
                    + "4. Subject to local jurisdiction only.";
        }
        terms.add(new Paragraph(text).setFontSize(6.5f).setFixedLeading(8.5f).setMargin(0));

        Cell signature = roundedFilled(new Cell().setPadding(6).setMinHeight(91).setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER), VERY_PALE_BLUE);
        signature.add(new Paragraph("For, " + invoice.company().name()).setBold().setFontColor(NAVY)
                .setFontSize(8.2f).setMarginBottom(3));
        Image signatureImage = configuredImage(ConfigManager.get("company.signaturePath", ""));
        if (signatureImage != null) {
            signatureImage.setMaxHeight(44).setMaxWidth(125).setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
            signature.add(signatureImage);
        } else {
            signature.add(new Paragraph("\n\n\n").setMargin(0));
        }
        signature.add(new Paragraph("AUTHORIZED SIGNATORY").setBold().setFontSize(7).setMarginTop(2).setMarginBottom(0));

        table.addCell(terms);
        table.addCell(signature);
        doc.add(table);
    }

    private static void addFooter(Document doc, CompanyProfile company) {
        Table stripes = new Table(UnitValue.createPercentArray(new float[]{74, 26})).useAllAvailableWidth();
        stripes.addCell(new Cell().setHeight(3).setBackgroundColor(NAVY).setBorder(Border.NO_BORDER));
        stripes.addCell(new Cell().setHeight(3).setBackgroundColor(BLUE).setBorder(Border.NO_BORDER));
        doc.add(stripes);

        doc.add(new Paragraph(company.address()).setTextAlignment(TextAlignment.CENTER)
                .setFontColor(MUTED).setBold().setFontSize(6.1f).setMarginTop(5).setMarginBottom(2));
    }

    private static Cell columnHeader(String text) {
        return new Cell().setBackgroundColor(NAVY).setFontColor(ColorConstants.WHITE)
                .setBorder(new SolidBorder(ColorConstants.WHITE, .35f)).setPadding(3.2f)
                .setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE)
                .add(new Paragraph(text).setBold().setFontSize(6.2f).setMargin(0));
    }

    private static Cell itemCell(String text, TextAlignment alignment) {
        return new Cell().setBorder(new SolidBorder(GRID, .45f)).setPadding(3.2f)
                .setTextAlignment(alignment).setVerticalAlignment(VerticalAlignment.MIDDLE)
                .add(new Paragraph(text == null ? "" : text).setFontSize(6.5f).setFixedLeading(8).setMargin(0));
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

    private static final class RoundedCellRenderer extends CellRenderer {
        private final Color fill;

        private RoundedCellRenderer(Cell modelElement, Color fill) {
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

    private static String stateFromAddress(String address) {
        if (address == null || address.isBlank()) return "-";
        String[] parts = address.split(",");
        return parts.length < 2 ? address.trim() : parts[parts.length - 2].trim();
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
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
