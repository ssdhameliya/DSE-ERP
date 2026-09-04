package org.example.util;

import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import org.example.config.ConfigManager;
import org.example.api.operations.OperationsApiClient;
import org.example.api.quotation.QuotationApiClient;
import org.example.api.returns.ReturnApiClient;
import org.example.api.master.MasterApiClient;
import org.example.model.Sales;
import org.example.model.SalesLine;
import org.example.model.Purchase;
import org.example.model.PurchaseLine;
import org.example.model.Party;
import org.example.model.Item;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.awt.BasicStroke;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates every customer and supplier PDF from one branded A4 template.
 *
 * <p>Preview, download, email and WhatsApp actions all call this renderer. That
 * guarantees that a document cannot change appearance merely because the user
 * selected a different action.</p>
 */
public final class ProfessionalDocumentRenderer {
    public enum Kind {
        SALES_INVOICE,
        PURCHASE_INVOICE,
        QUOTATION,
        SALES_REFUND,
        PURCHASE_REFUND
    }

    private static final DeviceRgb BLUE = new DeviceRgb(4, 47, 111);
    private static final DeviceRgb RED = new DeviceRgb(183, 0, 0);
    private static final DeviceRgb INK = new DeviceRgb(12, 25, 52);
    private static final DeviceRgb LINE = new DeviceRgb(170, 180, 196);
    private static final DeviceRgb PDF_LINE = new DeviceRgb(177, 194, 222);
    private static final DeviceRgb PALE = new DeviceRgb(247, 249, 252);
    private static final DeviceRgb JASVI_NAVY = new DeviceRgb(25, 58, 116);
    private static final DeviceRgb JASVI_BLUE = new DeviceRgb(49, 109, 179);
    private static final DeviceRgb JASVI_PALE_BLUE = new DeviceRgb(237, 244, 253);
    private static final DeviceRgb JASVI_GREEN = new DeviceRgb(42, 145, 91);
    private static final DeviceRgb JASVI_PALE_YELLOW = new DeviceRgb(255, 249, 221);
    private static final DeviceRgb JASVI_TEXT = new DeviceRgb(24, 37, 59);
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final ConcurrentHashMap<AssetCacheKey, byte[]> ASSET_IMAGE_CACHE = new ConcurrentHashMap<>();

    private ProfessionalDocumentRenderer() {
    }

    /** Loads database data and writes the selected business document. */
    public static void render(Path output, Path logo, String number, Kind kind) throws Exception {
        Data data = ProfessionalDocumentDataLoader.load(number, kind);

        Files.createDirectories(output.toAbsolutePath().getParent());
        logo = configuredDocumentLogo(logo);

        // Sales invoices use the approved JASVI Industries composition.  The
        // database-loading and action-routing code remains shared, so preview,
        // download, email and WhatsApp always receive this same PDF.
        if (kind == Kind.SALES_INVOICE) {
            renderJasviSalesInvoice(output, logo, data);
            return;
        }
        Color accent = BLUE;

        try (PdfWriter writer = new PdfWriter(output.toFile());
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf, PageSize.A4)) {
            document.setMargins(8, 12, 10, 12);
            applyUnicodeFont(document);
            document.add(referenceTopRule(accent));
            document.add(referenceHeader(data, logo, accent, kind));
            document.add(spacer(3));
            document.add(referencePartyArea(data, accent, kind));
            // The approved purchase layout moves directly from party/details cards
            // to the item table; the former GST/place-of-supply strip is omitted.
            document.add(spacer(4));
            document.add(referenceItems(data, accent, kind));
            Table lowerArea = referenceLowerArea(data, accent, kind, pdf);
            // Allow the summary area to use the remaining first-page space instead
            // of moving the entire lower section to a second page. The payment block
            // itself remains compact and grouped inside the lower-area table.
            lowerArea.setKeepTogether(false);
            document.add(lowerArea);
            document.add(spacer(4));
            document.add(referenceFooterBand(accent, kind));
            document.flush();
            addWatermarkAndPages(pdf, data.title, accent);
        }
    }

    /**
     * Renders the approved JASVI Industries sales invoice.
     *
     * <p>The item table is a normal flowing iText table: it contains exactly
     * one body row per database line, repeats its header after a page break and
     * grows onto additional pages without inserting decorative blank rows.</p>
     */
    private static void renderJasviSalesInvoice(Path output, Path fallbackLogo, Data data)
        throws Exception {
        try (PdfWriter writer = new PdfWriter(output.toFile());
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf, PageSize.A4)) {
            document.setMargins(12, 16, 14, 16);
            applyUnicodeFont(document);

            document.add(jasviHeader(fallbackLogo));
            document.add(jasviInvoiceIdentity(data));
            document.add(jasviAddressCards(data));
            document.add(jasviTransportStrip(data));
            document.add(jasviItemTable(data));
            document.add(jasviSummaryArea(data, pdf).setKeepTogether(true));
            document.add(jasviTermsAndSignature().setKeepTogether(true));
            document.add(jasviFooter().setKeepTogether(true));
            document.flush();
            addJasviPageNumbers(pdf);
        }
    }

    /** Uses the supplied JASVI banner as the primary document identity. */
    private static Table jasviHeader(Path fallbackLogo) {
        Table header = new Table(1).useAllAvailableWidth().setMarginBottom(5);
        Cell cell = new Cell().setBorder(Border.NO_BORDER).setPadding(0)
            .setTextAlignment(TextAlignment.CENTER);
        try (InputStream stream = ResourceLocator.open("/pdf/jasvi/company-header.png")) {
            if (stream != null) {
                cell.add(new Image(ImageDataFactory.create(stream.readAllBytes()))
                    .setAutoScale(true).setMaxHeight(92)
                    .setHorizontalAlignment(HorizontalAlignment.CENTER));
            } else if (fallbackLogo != null && Files.isRegularFile(fallbackLogo)) {
                cell.add(new Image(ImageDataFactory.create(fallbackLogo.toString()))
                    .setAutoScale(true).setMaxHeight(72)
                    .setHorizontalAlignment(HorizontalAlignment.CENTER));
            }
        } catch (Exception ignored) {
            cell.add(new Paragraph(config("company.name", "JASVI INDUSTRIES"))
                .setBold().setFontSize(25).setFontColor(JASVI_NAVY));
        }
        header.addCell(cell);
        return header;
    }

    /** Blue title band and compact invoice metadata matching the reference. */
    private static Table jasviInvoiceIdentity(Data data) {
        Table outer = new Table(UnitValue.createPercentArray(new float[]{47, 53}))
            .useAllAvailableWidth().setMarginBottom(6);
        outer.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(0));

        Cell right = new Cell().setPadding(0).setBorder(new SolidBorder(JASVI_BLUE, .8f));
        right.add(new Paragraph("TAX INVOICE")
            .setBackgroundColor(JASVI_NAVY).setFontColor(ColorConstants.WHITE)
            .setTextAlignment(TextAlignment.CENTER).setBold().setFontSize(15)
            .setPadding(5).setMargin(0));
        right.add(new Paragraph("ORIGINAL FOR BUYER")
            .setBackgroundColor(JASVI_BLUE).setFontColor(ColorConstants.WHITE)
            .setTextAlignment(TextAlignment.CENTER).setBold().setFontSize(6.8f)
            .setPadding(2).setMargin(0));

        Table metadata = new Table(UnitValue.createPercentArray(new float[]{48, 52}))
            .useAllAvailableWidth().setBackgroundColor(JASVI_PALE_BLUE);
        jasviMeta(metadata, "Invoice No.", data.number);
        jasviMeta(metadata, "Invoice Date", displayDate(data.date));
        jasviMeta(metadata, "Due Date", displayDate(data.dueDate));
        jasviMeta(metadata, "Payment Terms", present(data.paymentTerms));
        jasviMeta(metadata, "Reference", present(data.reference));
        jasviMeta(metadata, "Currency", "INR");
        right.add(metadata);
        outer.addCell(right);
        return outer;
    }

    private static void jasviMeta(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setBold().setFontSize(7.2f))
            .setBorder(Border.NO_BORDER).setPadding(2.5f).setPaddingLeft(7));
        table.addCell(new Cell().add(new Paragraph(":  " + present(value)).setFontSize(7.2f))
            .setBorder(Border.NO_BORDER).setPadding(2.5f));
    }

    /** Equal billing and delivery cards populated from the selected customer. */
    private static Table jasviAddressCards(Data data) {
        Table cards = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
            .useAllAvailableWidth().setMarginBottom(5);
        cards.addCell(jasviAddressCard("BILLING ADDRESS", data.partyName,
            data.partyAddress, data.partyPhone, data.partyEmail, data.billingGstin));
        cards.addCell(jasviAddressCard("DELIVERY ADDRESS", data.partyName,
            data.shipTo, data.partyPhone, data.partyEmail, data.deliveryGstin));
        return cards;
    }

    private static Cell jasviAddressCard(String title, String name, String address,
                                         String phone, String email, String gstin) {
        Cell card = new Cell().setPadding(0).setBorder(new SolidBorder(JASVI_BLUE, .7f));
        card.add(new Paragraph(title).setBold().setFontColor(ColorConstants.WHITE)
            .setBackgroundColor(JASVI_NAVY).setFontSize(8).setPadding(4).setMargin(0));
        Cell content = new Cell().setBorder(Border.NO_BORDER).setPadding(6);
        content.add(new Paragraph(present(name)).setBold().setFontSize(8.2f).setMarginBottom(2));
        content.add(new Paragraph(present(address)).setFontSize(7.2f)
            .setMultipliedLeading(1.25f).setMarginBottom(3));
        content.add(jasviInline("Phone", phone));
        content.add(jasviInline("Email", email));
        content.add(jasviInline("GSTIN", gstin));
        Table wrapper = new Table(1).useAllAvailableWidth();
        wrapper.addCell(content);
        card.add(wrapper);
        return card;
    }

    private static Paragraph jasviInline(String label, String value) {
        return new Paragraph().add(new Text(label + ": ").setBold().setFontColor(JASVI_NAVY))
            .add(new Text(present(value))).setFontSize(6.9f).setMargin(0);
    }

    /** Compact logistics strip directly above the line-item table. */
    private static Table jasviTransportStrip(Data data) {
        Table strip = new Table(UnitValue.createPercentArray(new float[]{24, 24, 26, 26}))
            .useAllAvailableWidth().setMarginBottom(5);
        jasviStripCell(strip, "TRANSPORTER", data.transporter);
        jasviStripCell(strip, "GSTIN", data.transporterGstin);
        jasviStripCell(strip, "CONTACT NAME", data.contactPerson);
        jasviStripCell(strip, "NUMBER", data.contactPersonMobile);
        return strip;
    }

    private static void jasviStripCell(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph()
                .add(new Text(label + ": ").setBold().setFontColor(JASVI_NAVY))
                .add(new Text(pdfValue(value))).setFontSize(6.4f).setMargin(0))
            .setPadding(5)
            .setBorder(new SolidBorder(JASVI_BLUE, .5f)));
    }

    /** Dynamic item table with no placeholder rows. */
    private static Table jasviItemTable(Data data) {
        Table table = new Table(UnitValue.createPercentArray(
            new float[]{5, 12, 31, 8, 12, 9, 10, 13})).useAllAvailableWidth();
        table.setMarginBottom(6);
        String[] headers = {"SR.", "HSN CODE", "PRODUCT DESCRIPTION", "QTY",
            "UNIT RATE", "UNIT", "GST %", "AMOUNT (INR)"};
        for (String header : headers) {
            table.addHeaderCell(new Cell().add(new Paragraph(header).setBold().setFontSize(6.4f))
                .setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setFontColor(ColorConstants.WHITE).setBackgroundColor(JASVI_NAVY)
                .setPadding(4).setBorder(new SolidBorder(ColorConstants.WHITE, .35f)));
        }
        int index = 1;
        for (Line line : data.lines) {
            double base = line.quantity * line.rate - line.discount;
            double total = base + base * line.gst / 100;
            jasviItemCell(table, String.valueOf(index++), TextAlignment.CENTER);
            jasviItemCell(table, present(line.hsn), TextAlignment.CENTER);
            jasviItemCell(table, present(line.description), TextAlignment.LEFT);
            jasviItemCell(table, quantity(line.quantity), TextAlignment.RIGHT);
            jasviItemCell(table, money(line.rate), TextAlignment.RIGHT);
            jasviItemCell(table, present(line.unit), TextAlignment.CENTER);
            jasviItemCell(table, quantity(line.gst) + "%", TextAlignment.CENTER);
            jasviItemCell(table, money(total), TextAlignment.RIGHT);
        }
        return table;
    }

    private static void jasviItemCell(Table table, String value, TextAlignment alignment) {
        table.addCell(new Cell().add(new Paragraph(value).setFontSize(6.7f).setMargin(0))
            .setTextAlignment(alignment).setVerticalAlignment(VerticalAlignment.MIDDLE)
            .setBackgroundColor(JASVI_PALE_BLUE).setPadding(4)
            .setBorder(new SolidBorder(ColorConstants.WHITE, .45f)));
    }

    /** Tax, bank, QR and grand-total area used on the final page. */
    private static Table jasviSummaryArea(Data data, PdfDocument pdf) {
        Table outer = new Table(UnitValue.createPercentArray(new float[]{58, 42}))
            .useAllAvailableWidth().setMarginBottom(6);
        Cell left = new Cell().setBorder(Border.NO_BORDER).setPaddingRight(6);
        left.add(jasviAmountWords(data));
        left.add(jasviBankAndQr(data, pdf));
        outer.addCell(left);

        Cell right = new Cell().setBorder(Border.NO_BORDER).setPadding(0);
        Table totals = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
            .useAllAvailableWidth();
        jasviTotal(totals, "Taxable Amount", data.subtotal, false);
        double gstRate = data.subtotal == 0 ? 0 : data.gst / data.subtotal * 100;
        boolean interstate = isInterstate(data);
        if (interstate) {
            jasviTotal(totals, "IGST (" + quantity(gstRate) + "%)", data.gst, false);
        } else {
            jasviTotal(totals, "CGST (" + quantity(gstRate / 2) + "%)", data.gst / 2, false);
            jasviTotal(totals, "SGST (" + quantity(gstRate / 2) + "%)", data.gst / 2, false);
        }
        jasviTotal(totals, "Round Off", 0.00, false);
        jasviTotal(totals, "GRAND TOTAL", data.total, true);
        right.add(totals);
        outer.addCell(right);
        return outer;
    }

    private static Table jasviAmountWords(Data data) {
        Table box = new Table(1).useAllAvailableWidth().setMarginBottom(5);
        box.addCell(new Cell().add(new Paragraph("AMOUNT IN WORDS").setBold().setFontSize(7.2f))
            .setFontColor(ColorConstants.WHITE).setBackgroundColor(JASVI_GREEN)
            .setPadding(3).setBorder(Border.NO_BORDER));
        box.addCell(new Cell().add(new Paragraph(amountWords(data.total) + " Only")
                .setBold().setFontSize(7).setMargin(0))
            .setPadding(5).setBorder(new SolidBorder(JASVI_GREEN, .6f)));
        return box;
    }

    private static Table jasviBankAndQr(Data data, PdfDocument pdf) {
        Table box = new Table(UnitValue.createPercentArray(new float[]{62, 38}))
            .useAllAvailableWidth();
        Cell bank = new Cell().setPadding(5).setBorder(new SolidBorder(JASVI_BLUE, .6f));
        bank.add(new Paragraph("BANK DETAILS").setBold().setFontColor(JASVI_NAVY)
            .setFontSize(7.2f).setMarginBottom(3));
        bank.add(new Paragraph(bankText()).setFontSize(6.6f).setMultipliedLeading(1.2f));
        box.addCell(bank);
        Cell qr = new Cell().setPadding(3).setTextAlignment(TextAlignment.CENTER)
            .setVerticalAlignment(VerticalAlignment.MIDDLE)
            .setBorder(new SolidBorder(JASVI_BLUE, .6f));
        qr.add(new Paragraph("SCAN TO PAY").setBold().setFontColor(JASVI_NAVY)
            .setFontSize(6.8f).setMargin(0));
        addConfiguredQr(qr, data, pdf);
        box.addCell(qr);
        return box;
    }

    private static void jasviTotal(Table table, String label, double amount, boolean grand) {
        Cell left = new Cell().add(new Paragraph(label).setBold().setFontSize(grand ? 8.5f : 7.2f))
            .setPadding(5).setBorder(new SolidBorder(JASVI_BLUE, .5f));
        Cell right = new Cell().add(new Paragraph("\u20B9 " + money(amount))
                .setBold().setFontSize(grand ? 9 : 7.2f))
            .setTextAlignment(TextAlignment.RIGHT).setPadding(5)
            .setBorder(new SolidBorder(JASVI_BLUE, .5f));
        if (grand) {
            left.setBackgroundColor(JASVI_GREEN).setFontColor(ColorConstants.WHITE);
            right.setBackgroundColor(JASVI_GREEN).setFontColor(ColorConstants.WHITE);
        }
        table.addCell(left);
        table.addCell(right);
    }

    private static boolean isInterstate(Data data) {
        String companyState = config("company.state", "").toLowerCase(Locale.ROOT);
        String party = (data.partyAddress + " " + data.shipTo).toLowerCase(Locale.ROOT);
        return !companyState.isBlank() && !party.isBlank() && !party.contains(companyState);
    }

    /** Reference terms panel and configured signature. */
    private static Table jasviTermsAndSignature() {
        Table outer = new Table(UnitValue.createPercentArray(new float[]{62, 38}))
            .useAllAvailableWidth().setMarginBottom(6);
        Cell terms = new Cell().setPadding(6).setBackgroundColor(JASVI_PALE_YELLOW)
            .setBorder(new SolidBorder(JASVI_BLUE, .6f));
        terms.add(new Paragraph("TERMS & CONDITIONS").setBold().setFontColor(JASVI_NAVY)
            .setFontSize(7.5f).setMarginBottom(3));
        terms.add(new Paragraph(config("invoice.terms",
            "1. Goods once sold will not be taken back.\n" +
                "2. Payment is due within the agreed credit period.\n" +
                "3. Interest may apply on overdue balances.\n" +
                "4. Subject to local jurisdiction only."))
            .setFontSize(6.3f).setMultipliedLeading(1.25f).setMargin(0));
        outer.addCell(terms);

        Cell sign = new Cell().setPadding(5).setTextAlignment(TextAlignment.CENTER)
            .setVerticalAlignment(VerticalAlignment.MIDDLE)
            .setBorder(new SolidBorder(JASVI_BLUE, .6f));
        sign.add(new Paragraph("FOR " + config("company.name", "JASVI INDUSTRIES"))
            .setBold().setFontColor(JASVI_NAVY).setFontSize(6.8f).setMarginBottom(2));
        Path signature = configuredAsset("company.signaturePath");
        if (signature != null) {
            try {
                sign.add(new Image(configuredAssetImageData(signature, 360))
                    .setAutoScale(true).setMaxHeight(35).setMaxWidth(95)
                    .setHorizontalAlignment(HorizontalAlignment.CENTER));
            } catch (Exception ignored) {
                // The document remains valid when an optional signature is unreadable.
            }
        }
        sign.add(new Paragraph("Authorized Signatory").setBold().setFontSize(6.8f).setMargin(0));
        outer.addCell(sign);
        return outer;
    }

    private static Table jasviFooter() {
        Table footer = new Table(1).useAllAvailableWidth();
        footer.addCell(new Cell().add(new Paragraph(config("company.address", ""))
                .setTextAlignment(TextAlignment.CENTER).setFontSize(6.4f).setMargin(0))
            .setPadding(3).setBorder(Border.NO_BORDER));
        footer.addCell(new Cell().setHeight(3).setPadding(0).setBorder(Border.NO_BORDER)
            .setBackgroundColor(JASVI_NAVY));
        footer.addCell(new Cell().setHeight(2).setPadding(0).setBorder(Border.NO_BORDER)
            .setBackgroundColor(JASVI_BLUE));
        return footer;
    }

    private static String displayDate(String value) {
        if (value == null || value.isBlank()) return "Not provided";
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(value.substring(0, 10));
            return date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));
        } catch (Exception ignored) {
            return value;
        }
    }

    private static void addJasviPageNumbers(PdfDocument pdf) {
        int total = pdf.getNumberOfPages();
        for (int page = 1; page <= total; page++) {
            PdfPage pdfPage = pdf.getPage(page);
            PdfCanvas canvas = new PdfCanvas(pdfPage.newContentStreamAfter(),
                pdfPage.getResources(), pdf);
            try {
                canvas.beginText().setFontAndSize(PdfFontFactory.createFont(), 6.5f)
                    .moveText(PageSize.A4.getWidth() - 54, 7)
                    .showText("Page " + page + " of " + total).endText();
            } catch (Exception ignored) {
                // Page numbering is decorative; invoice creation must never fail for it.
            } finally {
                canvas.release();
            }
        }
    }

    /** Thin top rule and tab are distinctive elements of the approved A4 design. */
    private static Table referenceTopRule(Color accent) {
        Table rule = new Table(UnitValue.createPercentArray(new float[]{43, 14, 43})).useAllAvailableWidth();
        rule.setBorder(Border.NO_BORDER).setMarginBottom(2);
        rule.addCell(new Cell().setHeight(2).setPadding(0).setBorder(Border.NO_BORDER).setBackgroundColor(accent));
        rule.addCell(new Cell().setHeight(5).setPadding(0).setBorder(Border.NO_BORDER).setBackgroundColor(accent));
        rule.addCell(new Cell().setHeight(2).setPadding(0).setBorder(Border.NO_BORDER).setBackgroundColor(accent));
        return rule;
    }

    /** Company identity at left and event-specific document identity at right. */
    private static Table referenceHeader(Data data, Path logo, Color accent, Kind kind) {
        Table outer = new Table(UnitValue.createPercentArray(new float[]{58, 42})).useAllAvailableWidth();
        outer.setBorder(Border.NO_BORDER);
        // Every document now shares the approved Sales Invoice identity block:
        // configured logo first, followed by aligned company contact and GSTIN rows.
        outer.addCell(salesBrandCell(logo, accent));

        String heading = switch (kind) {
            case SALES_INVOICE -> "TAX INVOICE";
            case PURCHASE_INVOICE -> "TAX INVOICE";
            case QUOTATION -> "QUOTATION";
            case SALES_REFUND -> "SALES RETURN / CREDIT NOTE";
            case PURCHASE_REFUND -> "PURCHASE RETURN NOTE";
        };
        Cell identity = new Cell().setBorder(Border.NO_BORDER).setPadding(4).setMinHeight(98)
            .setTextAlignment(TextAlignment.RIGHT);
        identity.add(new Paragraph(heading).setBold().setFontSize(kind == Kind.SALES_REFUND ? 14 : 17)
            .setFontColor(INK).setMargin(0));
        identity.add(new Paragraph(present(data.number)).setBold().setFontSize(9)
            .setFontColor(ColorConstants.WHITE).setBackgroundColor(accent).setPadding(4).setMarginLeft(95));
        Table info = kind == Kind.SALES_INVOICE
            ? new Table(UnitValue.createPercentArray(new float[]{8, 40, 5, 47}))
                // Keep all six rows directly beneath the right-aligned invoice badge.
                .setWidth(UnitValue.createPercentValue(59))
                .setHorizontalAlignment(HorizontalAlignment.RIGHT)
                .setMarginTop(3)
            : new Table(UnitValue.createPercentArray(new float[]{8, 39, 5, 48}))
                // Match the approved Sales Invoice: metadata begins beneath the
                // left edge of the blue document-number badge and ends at its right edge.
                .setWidth(UnitValue.createPercentValue(59))
                .setHorizontalAlignment(HorizontalAlignment.RIGHT)
                .setMarginTop(4);
        if (data.refund) {
            documentRefPair(info, "calendar", "Return Date", data.date);
            documentRefPair(info, "document", "Original Invoice", data.originalNumber);
            documentRefPair(info, "calendar", "Invoice Date", data.originalDate);
            if (kind == Kind.SALES_REFUND) {
                documentRefPair(info, "reference", "Payment / Refund Mode", data.status);
                documentRefPair(info, "document", "Delivery / Pickup Ref.", data.reference);
            } else {
                documentRefPair(info, "document", "Payment Mode", data.status);
            }
        } else if (kind == Kind.SALES_INVOICE) {
            // The invoice number is already displayed in the blue badge above.
            // These rows intentionally mirror the approved icon/label/value grid.
            salesRefPair(info, "calendar", "Invoice Date", data.date);
            salesRefPair(info, "calendar", "PO Date", data.poDate);
            salesRefPair(info, "reference", "PO No.", data.purchaseOrder);
            salesRefPair(info, "reference", "GST Type", data.gstType);
            salesRefPair(info, "user", "Sales Person", data.salesperson);
            salesRefPair(info, "currency", "Currency", "INR");
        } else {
            if (kind == Kind.PURCHASE_INVOICE) {
                documentRefPair(info, "calendar", "Purchase Date", data.date);
                documentRefPair(info, "calendar", "Invoice Date", firstNonBlank(data.originalDate, data.date));
                documentRefPair(info, "clock", "Due Date", data.dueDate);
            } else {
                documentRefPair(info, "calendar", "Date", data.date);
                documentRefPair(info, "clock", "Valid Until", data.dueDate);
            }
        }
        identity.add(info);
        outer.addCell(identity);
        return outer;
    }

    /** Compact icon-led metadata row shared by every approved non-sales template. */
    private static void documentRefPair(Table table, String iconType, String key, String value) {
        Cell icon = new Cell().setBorder(Border.NO_BORDER).setPadding(1)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
        addMetadataIcon(icon, iconType);
        table.addCell(icon);
        table.addCell(salesMetaText(key, true, TextAlignment.LEFT));
        table.addCell(salesMetaText(":", true, TextAlignment.CENTER));
        table.addCell(salesMetaText(present(value), false, TextAlignment.LEFT));
    }

    /** Approved Sales layout stacks the company identity beneath the upper-left logo. */
    private static Cell salesBrandCell(Path logo, Color accent) {
        Cell brand = plainCell().setPaddingLeft(5).setPaddingTop(2).setPaddingRight(16);
        addLogo(brand, logo, accent, 150, 58);
        addPdfIconLine(brand, "company.png",
            config("company.name", "DSE ERP SOLUTIONS PVT. LTD."), accent, true);
        addPdfIconLine(brand, "location.png",
            config("company.address", "Configure company address in Settings"), accent, false);
        addPdfIconLine(brand, "phone.png", config("company.phone", ""), accent, false);
        addPdfIconLine(brand, "email.png", config("company.email", ""), accent, false);
        addPdfIconLine(brand, "website.png", config("company.website", ""), accent, false);
        addAlignedGstinLine(brand, config("company.gstin", ""), accent, true);
        return brand;
    }

    /**
     * Resolves the optional Company & Billing logo saved by Settings.
     * Application branding is intentionally never used as an invoice fallback.
     */
    private static Path configuredDocumentLogo(Path ignoredFallback) {
        String configured = ConfigManager.get("company.logoPath", "").trim();
        if (configured.isBlank()) return null;
        try {
            Path path = Path.of(configured).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? path : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Adds a configured logo without allowing a missing asset to break rendering. */
    private static void addLogo(Cell cell, Path logo, Color accent, float width, float height) {
        if (logo != null && Files.isRegularFile(logo)) {
            try {
                Image image = new Image(cachedRawAssetImageData(logo));
                image.scaleToFit(width, height);
                cell.add(image);
                return;
            } catch (Exception ignored) {
                // The text fallback keeps the invoice usable when the logo is corrupt.
            }
        }
        cell.add(new Paragraph("DSE").setBold().setFontSize(22).setFontColor(accent));
    }

    private static void refPair(Table table, String key, String value) {
        table.addCell(refText(key, true, TextAlignment.LEFT));
        table.addCell(refText(":", true, TextAlignment.CENTER));
        table.addCell(refText(present(value), false, TextAlignment.LEFT));
    }

    /** Icon-led identity row used by the approved Sales Invoice header. */
    private static void salesRefPair(Table table, String iconType, String key, String value) {
        Cell icon = new Cell().setBorder(Border.NO_BORDER).setPadding(1)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
        addMetadataIcon(icon, iconType);
        table.addCell(icon);
        table.addCell(salesMetaText(key, true, TextAlignment.LEFT));
        table.addCell(salesMetaText(":", true, TextAlignment.CENTER));
        table.addCell(salesMetaText(present(value), false, TextAlignment.LEFT));
    }

    /** Keeps every Sales Invoice metadata row on one crisp visual baseline. */
    private static Cell salesMetaText(String value, boolean bold, TextAlignment alignment) {
        Paragraph paragraph = new Paragraph(value).setFontSize(7.2f)
            .setMargin(0).setMultipliedLeading(1);
        if (bold) paragraph.setBold();
        return new Cell().add(paragraph).setBorder(Border.NO_BORDER)
            .setPaddingTop(1).setPaddingBottom(1).setPaddingLeft(1).setPaddingRight(1)
            .setTextAlignment(alignment).setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    /** Draws compact monochrome metadata icons without embedded labels or emoji fonts. */
    private static void addMetadataIcon(Cell cell, String type) {
        try {
            int size = 64;
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new java.awt.Color(4, 94, 214));
            graphics.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));

            switch (type) {
                case "document" -> {
                    graphics.drawRoundRect(14, 7, 36, 50, 5, 5);
                    graphics.drawLine(22, 24, 42, 24);
                    graphics.drawLine(22, 34, 42, 34);
                    graphics.drawLine(22, 44, 36, 44);
                }
                case "calendar" -> {
                    graphics.drawRoundRect(8, 14, 48, 42, 6, 6);
                    graphics.drawLine(9, 27, 55, 27);
                    graphics.drawLine(20, 8, 20, 20);
                    graphics.drawLine(44, 8, 44, 20);
                    graphics.fillOval(18, 35, 7, 7);
                    graphics.fillOval(30, 35, 7, 7);
                    graphics.fillOval(42, 35, 7, 7);
                }
                case "clock" -> {
                    graphics.drawOval(7, 7, 50, 50);
                    graphics.drawLine(32, 17, 32, 33);
                    graphics.drawLine(32, 33, 44, 40);
                }
                case "reference" -> {
                    graphics.drawOval(7, 22, 30, 20);
                    graphics.drawOval(27, 22, 30, 20);
                    graphics.drawLine(25, 32, 39, 32);
                }
                case "user" -> {
                    graphics.drawOval(22, 8, 20, 20);
                    graphics.drawArc(12, 29, 40, 29, 0, 180);
                    graphics.drawLine(12, 43, 12, 54);
                    graphics.drawLine(52, 43, 52, 54);
                }
                case "currency" -> {
                    graphics.drawRoundRect(7, 13, 50, 38, 7, 7);
                    graphics.drawLine(8, 25, 56, 25);
                    graphics.drawLine(19, 38, 45, 38);
                }
                default -> graphics.fillOval(20, 20, 24, 24);
            }
            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            cell.add(new Image(ImageDataFactory.create(output.toByteArray()))
                .setWidth(8).setHeight(8).setHorizontalAlignment(HorizontalAlignment.CENTER));
        } catch (Exception ignored) {
            // Metadata text remains fully readable if icon drawing is unavailable.
        }
    }

    private static Cell refText(String value, boolean bold, TextAlignment alignment) {
        Paragraph p = new Paragraph(value).setFontSize(7.2f).setMargin(0);
        if (bold) p.setBold();
        return new Cell().add(p).setBorder(Border.NO_BORDER).setPadding(2).setTextAlignment(alignment);
    }

    /** Bill/ship/tax cards, or the dedicated party and return cards for returns. */
    private static Table referencePartyArea(Data data, Color accent, Kind kind) {
        boolean returns = data.refund;
        float[] widths = returns ? new float[]{1, 1} : new float[]{1, 1, 1};
        Table cards = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
        cards.setHorizontalBorderSpacing(6);
        if (returns) {
            cards.addCell(documentPartyCard(kind == Kind.SALES_REFUND ? "CUSTOMER DETAILS" : "VENDOR DETAILS",
                "bill-to.png", data, accent));
            cards.addCell(returnDetailsCard(data, kind, accent));
        } else if (kind == Kind.PURCHASE_INVOICE) {
            cards.addCell(documentPartyCard("SUPPLIER / VENDOR", "bill-to.png", data, accent));
            cards.addCell(companyDeliveryCard("DELIVER TO", accent));
            cards.addCell(documentDetailsCard("PURCHASE DETAILS", new String[][]{
                {"Purchase No.", data.number}, {"Purchase Date", data.date},
                {"Invoice No.", data.reference}, {"Invoice Date", firstNonBlank(data.originalDate, data.date)},
                {"Payment Terms", data.paymentTerms}, {"Delivery Terms", data.transporter}, {"Currency", "INR"}
            }, accent));
        } else if (kind == Kind.SALES_INVOICE) {
            cards.addCell(salesPartyCard("BILL TO", "bill-to.png", data, false, accent));
            cards.addCell(salesPartyCard("SHIP TO", "ship-to.png", data, true, accent));
            cards.addCell(salesGstCard(data, accent));
        } else {
            cards.addCell(documentPartyCard("BILL TO", "bill-to.png", data, accent));
            cards.addCell(documentShipCard("SHIP TO", data, accent));
            cards.addCell(documentDetailsCard("QUOTATION DETAILS", new String[][]{
                {"Quotation No.", data.number}, {"Quotation Date", data.date},
                {"Valid Until", data.dueDate}, {"Sales Person", data.salesperson},
                {"Payment Terms", data.paymentTerms}, {"Delivery Terms", data.transporter}, {"Currency", "INR"}
            }, accent));
        }
        return cards;
    }

    /** Contact card with the same icon, title ribbon and aligned GST details as the supplied PDFs. */
    private static Cell documentPartyCard(String title, String iconName, Data data, Color accent) {
        Cell card = new Cell().setBorder(new SolidBorder(PDF_LINE, .7f)).setPadding(0).setMinHeight(108);
        card.add(documentCardTitle(title, accent));
        Table body = new Table(UnitValue.createPercentArray(new float[]{18, 82})).useAllAvailableWidth().setMarginTop(4);
        Cell icon = plainCell().setPadding(5).setVerticalAlignment(VerticalAlignment.TOP);
        addResourceIcon(icon, iconName, 24, 24);
        body.addCell(icon);
        Cell details = plainCell().setPadding(3);
        details.add(new Paragraph(present(data.partyName)).setBold().setFontSize(7.3f).setMargin(0));
        details.add(new Paragraph(present(data.partyAddress)).setFontSize(6.6f).setMultipliedLeading(1.18f).setMarginTop(2));
        addPdfIconLine(details, "phone.png", data.partyPhone, accent, false);
        addPdfIconLine(details, "email.png", data.partyEmail, accent, false);
        addAlignedGstinLine(details, data.partyGstin, accent, false);
        body.addCell(details);
        card.add(body);
        return card;
    }

    private static Cell documentShipCard(String title, Data data, Color accent) {
        Cell card = new Cell().setBorder(new SolidBorder(PDF_LINE, .7f)).setPadding(0).setMinHeight(108);
        card.add(documentCardTitle(title, accent));
        Table body = new Table(UnitValue.createPercentArray(new float[]{18, 82})).useAllAvailableWidth().setMarginTop(4);
        Cell icon = plainCell().setPadding(5); addResourceIcon(icon, "ship-to.png", 24, 24); body.addCell(icon);
        Cell details = plainCell().setPadding(3);
        details.add(new Paragraph(present(data.partyName) + " - Warehouse").setBold().setFontSize(7.2f).setMargin(0));
        details.add(new Paragraph(present(firstNonBlank(data.shipTo, data.partyAddress))).setFontSize(6.6f)
            .setMultipliedLeading(1.18f).setMarginTop(3));
        addPdfIconLine(details, "phone.png", data.partyPhone, accent, false);
        addPdfIconLine(details, "email.png", data.partyEmail, accent, false);
        body.addCell(details); card.add(body); return card;
    }

    private static Cell companyDeliveryCard(String title, Color accent) {
        Cell card = new Cell().setBorder(new SolidBorder(PDF_LINE, .7f)).setPadding(0).setMinHeight(108);
        card.add(documentCardTitle(title, accent));
        Table body = new Table(UnitValue.createPercentArray(new float[]{18, 82})).useAllAvailableWidth().setMarginTop(4);
        Cell icon = plainCell().setPadding(5); addResourceIcon(icon, "ship-to.png", 24, 24); body.addCell(icon);
        Cell details = plainCell().setPadding(3);
        details.add(new Paragraph(config("company.name", "DSE ERP SOLUTIONS PVT. LTD.") + " - Warehouse")
            .setBold().setFontSize(7.1f).setMargin(0));
        details.add(new Paragraph(present(config("company.shipTo", config("company.address", ""))))
            .setFontSize(6.6f).setMultipliedLeading(1.18f).setMarginTop(3));
        addPdfIconLine(details, "phone.png", config("company.phone", ""), accent, false);
        addPdfIconLine(details, "email.png", config("company.email", ""), accent, false);
        body.addCell(details); card.add(body); return card;
    }

    private static Cell documentDetailsCard(String title, String[][] rows, Color accent) {
        Cell card = new Cell().setBorder(new SolidBorder(PDF_LINE, .7f)).setPadding(7).setMinHeight(108);
        card.add(new Paragraph(title).setBold().setFontSize(7.5f).setFontColor(accent).setMarginTop(0).setMarginBottom(4));
        Table details = new Table(UnitValue.createPercentArray(new float[]{40, 5, 55})).useAllAvailableWidth();
        for (String[] row : rows) refPair(details, row[0], row[1]);
        card.add(details); return card;
    }

    private static Cell returnDetailsCard(Data data, Kind kind, Color accent) {
        String[][] rows = kind == Kind.SALES_REFUND
            ? new String[][]{{"Return Note No.", data.number}, {"Return Date", data.date},
                {"Original Invoice", data.originalNumber}, {"Invoice Date", data.originalDate},
                {"Payment / Refund Mode", data.status},
                {"Settlement / Reference", firstNonBlank(data.reference, data.status)}}
            : new String[][]{{"Return Note No.", data.number}, {"Return Date", data.date},
                {"Original Invoice", data.originalNumber}, {"Invoice Date", data.originalDate},
                {"Payment Mode", data.status},
                {"Settlement / Reference", firstNonBlank(data.reference, data.status)}};
        return documentDetailsCard(kind == Kind.SALES_REFUND ? "RETURN DETAILS" : "RETURN / SETTLEMENT DETAILS",
            rows, accent);
    }

    private static Table documentCardTitle(String title, Color accent) {
        Table band = new Table(UnitValue.createPercentArray(new float[]{58, 42})).useAllAvailableWidth();
        band.addCell(new Cell().add(new Paragraph(title).setBold().setFontSize(7.3f)
                .setFontColor(ColorConstants.WHITE).setMargin(0))
            .setBackgroundColor(accent).setBorder(Border.NO_BORDER).setPadding(4));
        band.addCell(plainCell().setPadding(0));
        return band;
    }

    /** Bill/Ship card matching the approved Sales Invoice card structure. */
    private static Cell salesPartyCard(String title, String iconName, Data data,
                                       boolean shipping, Color accent) {
        Cell card = new Cell().setBorder(new SolidBorder(PDF_LINE, .7f))
            .setPadding(0).setMinHeight(112);

        Table titleBand = new Table(UnitValue.createPercentArray(new float[]{36, 64}))
            .useAllAvailableWidth();
        titleBand.addCell(new Cell().add(new Paragraph(title).setBold().setFontSize(7.4f)
                .setFontColor(ColorConstants.WHITE).setMargin(0))
            .setBackgroundColor(accent).setBorder(Border.NO_BORDER).setPadding(4));
        titleBand.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(0));
        card.add(titleBand);

        Table body = new Table(UnitValue.createPercentArray(new float[]{18, 82}))
            .useAllAvailableWidth().setMarginTop(5);
        Cell icon = new Cell().setBorder(Border.NO_BORDER).setPadding(5)
            .setVerticalAlignment(VerticalAlignment.TOP);
        addResourceIcon(icon, iconName, 25, 25);
        body.addCell(icon);

        Cell details = new Cell().setBorder(Border.NO_BORDER).setPadding(3);
        details.add(new Paragraph(present(data.partyName)).setBold().setFontSize(7.4f).setMargin(0));
        String address = shipping ? firstNonBlank(data.shipTo, data.partyAddress) : data.partyAddress;
        details.add(new Paragraph(present(address)).setFontSize(6.6f).setMultipliedLeading(1.18f)
            .setMarginTop(2).setMarginBottom(2));
        addPdfIconLine(details, "phone.png", data.partyPhone, accent, false);
        // Both Bill To and Ship To show the party's complete contact information.
        addPdfIconLine(details, "email.png", data.partyEmail, accent, false);
        addAlignedGstinLine(details, data.partyGstin, accent, false);
        body.addCell(details);
        card.add(body);
        return card;
    }

    /** Tax card uses fixed label/colon/value columns so every GST field aligns. */
    private static Cell salesGstCard(Data data, Color accent) {
        Cell card = new Cell().setBorder(new SolidBorder(PDF_LINE, .7f))
            .setPadding(7).setMinHeight(112);
        card.add(new Paragraph("TAX / GST DETAILS").setBold().setFontSize(7.4f)
            .setFontColor(accent).setMarginTop(0).setMarginBottom(5));
        Table details = new Table(UnitValue.createPercentArray(new float[]{39, 5, 56}))
            .useAllAvailableWidth();
        refPair(details, "GSTIN", data.partyGstin);
        refPair(details, "State Code", config("company.stateCode", config("company.state", "Not configured")));
        refPair(details, "Place of Supply", config("company.state", "Not configured"));
        refPair(details, "Reverse Charge", "No");
        refPair(details, "Reference", data.reference);
        card.add(details);
        return card;
    }




    /** Event-specific item table matching the column set in each supplied PDF. */
    private static Table referenceItems(Data data, Color accent, Kind kind) {
        String[] headers;
        float[] widths;
        if (data.refund) {
            headers = new String[]{"SR.", "ITEM CODE", "ITEM DESCRIPTION", "QTY RETURNED", "RATE (INR)", "DISCOUNT", "TAX %", "TAX AMOUNT", "RETURN AMOUNT", "RETURN REASON"};
            widths = new float[]{.35f,.8f,1.75f,.7f,.8f,.7f,.55f,.8f,.9f,1.2f};
        } else if (kind == Kind.QUOTATION) {
            headers = new String[]{"#", "ITEM CODE", "DESCRIPTION", "UNIT", "QTY", "RATE (INR)", "TAX (%)", "AMOUNT (INR)"};
            widths = new float[]{.35f,.8f,2.1f,.55f,.5f,.75f,.55f,.9f};
        } else if (kind == Kind.PURCHASE_INVOICE) {
            headers = new String[]{"#", "ITEM CODE", "DESCRIPTION", "QTY", "RATE (INR)", "DISCOUNT", "TAX (%)", "AMOUNT (INR)"};
            widths = new float[]{.35f,.9f,2.3f,.65f,.9f,.75f,.65f,1.1f};
        } else {
            headers = new String[]{"#", "ITEM CODE", "DESCRIPTION", "QTY", "RATE (INR)", "DISCOUNT", "TAX (%)", "AMOUNT (INR)"};
            widths = new float[]{.3f,.85f,2.25f,.55f,.78f,.68f,.58f,1.05f};
        }
        Table table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
        // The table grows with its real rows. iText splits it across pages and
        // automatically repeats the header on every continuation page.
        table.setKeepTogether(false);
        for (String header : headers) table.addHeaderCell(new Cell().add(new Paragraph(header).setBold().setFontSize(6.2f))
            .setFontColor(ColorConstants.WHITE).setBackgroundColor(accent).setTextAlignment(TextAlignment.CENTER)
            .setVerticalAlignment(VerticalAlignment.MIDDLE).setPadding(3).setBorder(new SolidBorder(ColorConstants.WHITE,.3f)));
        int index = 1;
        for (Line line : data.lines) {
            double taxable = line.quantity * line.rate - line.discount;
            double tax = taxable * line.gst / 100;
            List<String> values = new ArrayList<>();
            values.add(String.valueOf(index++)); values.add(line.code); values.add(line.description);
            if (data.refund) {
                values.add(quantity(line.quantity)); values.add(money(line.rate));
                values.add(money(line.discount)); values.add(quantity(line.gst) + "%"); values.add(money(tax));
                values.add(money(taxable + tax)); values.add(present(data.reason));
            } else {
                if (kind != Kind.SALES_INVOICE && kind != Kind.PURCHASE_INVOICE) values.add(present(line.unit));
                values.add(quantity(line.quantity)); values.add(money(line.rate));
                if (kind != Kind.QUOTATION) values.add(money(line.discount));
                values.add(quantity(line.gst) + "%"); values.add(money(taxable + tax));
            }
            for (int c = 0; c < values.size(); c++) table.addCell(new Cell()
                .add(new Paragraph(values.get(c)).setFontSize(6.4f).setMargin(0))
                .setPaddingTop(2).setPaddingBottom(2).setPaddingLeft(3).setPaddingRight(3)
                .setMinHeight(18).setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(c >= values.size()-5 && c != 2 ? TextAlignment.RIGHT : TextAlignment.LEFT)
                .setBorder(new SolidBorder(kind == Kind.SALES_INVOICE ? PDF_LINE : new DeviceRgb(190,198,211),.45f)));
        }

        // Keep a consistent item-section footprint for short documents. These are
        // deliberately blank cells (not fake item records), so the bank/UPI/signature
        // block remains near the bottom while users still get visible writing space.
        int minimumVisibleRows = data.refund ? 3 : (kind == Kind.QUOTATION ? 5 : 4);
        int blankRows = Math.max(0, minimumVisibleRows - data.lines.size());
        for (int row = 0; row < blankRows; row++) {
            for (int c = 0; c < headers.length; c++) {
                table.addCell(new Cell().add(new Paragraph(" ").setFontSize(6.2f).setMargin(0))
                    .setMinHeight(18).setPadding(2)
                    .setBorder(new SolidBorder(kind == Kind.SALES_INVOICE ? PDF_LINE
                        : new DeviceRgb(190,198,211), .45f)));
            }
        }
        return table;
    }

    /** Amount, terms, bank/QR/signature and return-specific summaries. */
    private static Table referenceLowerArea(Data data, Color accent, Kind kind, PdfDocument pdf) {
        if (data.refund) return referenceReturnLowerArea(data, accent, kind);
        // Keep a deliberate gutter between the left narrative blocks and the
        // right totals table so their borders never touch, while preserving the
        // same outer left/right alignment as the item table.
        Table outer = new Table(UnitValue.createPercentArray(new float[]{55, 2, 43}))
            .useAllAvailableWidth().setMarginTop(5);
        Cell left = plainCell().setPaddingRight(0).setPaddingTop(0);
        left.add(refIconSection("amount-words.png", "AMOUNT IN WORDS",
            amountWords(data.total) + " Only", accent, false));
        if (data.refund) {
            left.add(refSection("RETURN REASON SUMMARY", present(data.reason) + "\nRemarks: " + present(data.reason), accent));
            left.add(refSection("REMARKS", "Goods returned against " + present(data.originalNumber) + ". Settlement is subject to verification.", accent));
        } else {
            String termsTitle = kind == Kind.PURCHASE_INVOICE
                ? "PURCHASE TERMS & CONDITIONS" : "TERMS & CONDITIONS";
            String terms = configuredOr("company.terms",
                "• This is a computer generated document.\n• Payment is due within the agreed period.\n" +
                "• Goods are subject to inspection and applicable tax.\n• All disputes are subject to local jurisdiction.");
            left.add(refIconSection("terms.png", termsTitle, terms, accent, true));
            if (kind == Kind.PURCHASE_INVOICE) {
                left.add(refIconSection("terms.png", "NOTES", present(data.reason), accent, false));
            }
        }
        outer.addCell(left);
        outer.addCell(plainCell());
        Table totals = new Table(UnitValue.createPercentArray(new float[]{58,42})).useAllAvailableWidth();
        addTotal(totals, "Sub Total", data.subtotal, false, accent);
        addTotal(totals, "Taxable Amount", data.subtotal, false, accent);
        double componentRate = data.subtotal == 0 ? 0 : data.gst / data.subtotal * 50;
        String cgst = "CGST (" + quantity(componentRate) + "%)";
        String sgst = "SGST (" + quantity(componentRate) + "%)";
        addTotal(totals, cgst, data.gst/2, false, accent);
        addTotal(totals, sgst, data.gst/2, false, accent);
        if (kind == Kind.SALES_INVOICE && (data.chargeAmount > .009 || !data.chargeType.isBlank())) {
            addTotal(totals, data.chargeType.isBlank() ? "Charges" : data.chargeType, data.chargeAmount, false, accent);
        }
        addTotal(totals, data.refund ? "RETURN / REFUND AMOUNT" : kind == Kind.PURCHASE_INVOICE ? "TOTAL AMOUNT" : "GRAND TOTAL", data.total, true, accent);
        Cell right = new Cell().add(totals).setBorder(Border.NO_BORDER)
            .setPaddingTop(0).setPaddingBottom(0).setPaddingLeft(0).setPaddingRight(0);
        if (data.refund) right.add(refSection("REFUND / DEBIT NOTE STATUS", present(data.status), accent));
        outer.addCell(right);

        // Push the payment/branding strip toward the physical page bottom for
        // short invoices. As item count grows the spacer collapses, allowing the
        // strip to flow naturally to the next page only when it genuinely must.
        float bottomGap = Math.max(0f, 66f - Math.max(0, data.lines.size() - 1) * 18f);
        Cell bottomSpacer = new Cell(1, 3).setBorder(Border.NO_BORDER)
            .setPadding(0).setHeight(bottomGap);
        outer.addCell(bottomSpacer);

        Cell full = new Cell(1,3).setBorder(new SolidBorder(PDF_LINE,.7f))
            .setPadding(3).setMinHeight(52).setKeepTogether(true);
        Table payment = new Table(UnitValue.createPercentArray(new float[]{42,20,38})).useAllAvailableWidth();
        Cell bank = plainCell();
        bank.setBorderRight(new SolidBorder(PDF_LINE, .65f));
        bank.add(sectionIconTitle("bank.png", "BANK DETAILS", accent));
        bank.add(new Paragraph(bankText()).setFontSize(5.8f).setMultipliedLeading(1.05f)
            .setMarginTop(1).setMarginBottom(0));
        payment.addCell(bank);
        Cell qr = plainCell().setTextAlignment(TextAlignment.CENTER)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
        qr.setBorderRight(new SolidBorder(PDF_LINE, .65f));
        qr.add(new Paragraph("QR CODE").setBold().setFontColor(accent)
            .setFontSize(6.3f).setMargin(0));
        qr.add(new Paragraph("Scan to make payment").setFontSize(5.1f)
            .setFontColor(INK).setMarginTop(0).setMarginBottom(0));
        if (!data.refund) addConfiguredQr(qr, data, pdf);
        payment.addCell(qr);
        Cell sign = plainCell().setTextAlignment(TextAlignment.CENTER)
            .add(new Paragraph("FOR " + config("company.name", "DSE ERP SOLUTIONS PVT. LTD.")).setBold().setFontColor(accent).setFontSize(6.5f));
        Path sig = configuredAsset("company.signaturePath");
        if (sig != null) try {
            var signatureData = configuredAssetImageData(sig, 360);
            // The Settings screen permits either a handwritten signature or a
            // square company stamp. Preserve its aspect ratio and never replace
            // the user's configured asset merely because it is square.
            sign.add(new Image(signatureData).setAutoScale(true).setMaxHeight(28).setMaxWidth(70)
                .setHorizontalAlignment(HorizontalAlignment.CENTER));
        } catch (Exception ignored) {}
        sign.add(new Paragraph(data.refund ? "Approved / Authorized Signatory" : "Authorized Signatory")
            .setBold().setFontSize(6.4f).setMarginTop(0).setMarginBottom(0));
        payment.addCell(sign); full.add(payment); outer.addCell(full);
        return outer;
    }

    /** Return-specific summary, status and approval blocks from the supplied designs. */
    private static Table referenceReturnLowerArea(Data data, Color accent, Kind kind) {
        Table outer = new Table(UnitValue.createPercentArray(new float[]{52, 48})).useAllAvailableWidth();
        Cell left = plainCell().setPaddingRight(6);
        left.add(refIconSection("amount-words.png", "RETURN REASON SUMMARY",
            present(data.reason) + "\nReturned line items: " + data.lines.size(), accent, true));
        left.add(refIconSection("terms.png", "REMARKS",
            "Goods returned against " + present(data.originalNumber) +
                ". Settlement is subject to inspection and approval.", accent, true));
        outer.addCell(left);

        Cell right = plainCell();
        Table totals = new Table(UnitValue.createPercentArray(new float[]{60,40})).useAllAvailableWidth();
        addTotal(totals, "Sub Total (Before Tax)", data.subtotal, false, accent);
        double componentRate = data.subtotal == 0 ? 0 : data.gst / data.subtotal * 50;
        addTotal(totals, "CGST (" + quantity(componentRate) + "%)", data.gst / 2, false, accent);
        addTotal(totals, "SGST (" + quantity(componentRate) + "%)", data.gst / 2, false, accent);
        addTotal(totals, "Total Tax Amount", data.gst, false, accent);
        addTotal(totals, "TOTAL RETURN AMOUNT (INR)", data.total, true, accent);
        right.add(totals);
        right.add(refIconSection("amount-words.png",
            kind == Kind.SALES_REFUND ? "CREDIT NOTE STATUS" : "REFUND / DEBIT NOTE STATUS",
            present(data.status), accent, false));
        outer.addCell(right);

        Cell approvals = new Cell(1, 2).setBorder(new SolidBorder(PDF_LINE, .7f)).setPadding(6);
        Table approvalTable = new Table(UnitValue.createPercentArray(
            kind == Kind.SALES_REFUND ? new float[]{1,1,1} : new float[]{1,1,1,1})).useAllAvailableWidth();
        approvalTable.addCell(approvalCell("PREPARED BY", accent));
        if (kind == Kind.PURCHASE_REFUND) {
            approvalTable.addCell(approvalCell("RECEIVED BY (WAREHOUSE)", accent));
        }
        approvalTable.addCell(approvalCell("APPROVED BY", accent));
        approvalTable.addCell(approvalCell("AUTHORIZED SIGNATURE", accent));
        approvals.add(approvalTable);
        outer.addCell(approvals);
        return outer;
    }

    private static Cell approvalCell(String title, Color accent) {
        Cell cell = new Cell().setBorder(Border.NO_BORDER)
            .setBorderRight(new SolidBorder(PDF_LINE, .5f)).setPadding(5);
        cell.add(new Paragraph(title).setBold().setFontColor(accent).setFontSize(6.8f).setMargin(0));
        cell.add(new Paragraph(
            "Name: __________________\nDesignation: ____________\nDate: __________________\nSignature: ______________")
            .setFontSize(6.2f).setMultipliedLeading(1.5f).setMarginTop(4));
        return cell;
    }

    /** Sales lower-section box with the reference icon positioned beside its title. */
    private static Table refIconSection(String iconName, String title, String body,
                                         Color accent, boolean terms) {
        Table box = new Table(1).useAllAvailableWidth().setMarginBottom(3);
        box.addCell(new Cell().add(sectionIconTitle(iconName, title, accent))
            .setPadding(2.5f).setBorder(new SolidBorder(PDF_LINE, .6f)));
        box.addCell(new Cell().add(new Paragraph(body).setFontSize(6.8f)
                .setMultipliedLeading(1.2f).setMargin(0))
            .setPadding(4).setMinHeight(terms ? 42 : 22)
            .setBorder(new SolidBorder(PDF_LINE, .6f)));
        return box;
    }

    /** Creates a compact PDF-safe icon and section-title row. */
    private static Table sectionIconTitle(String iconName, String title, Color accent) {
        Table row = new Table(UnitValue.createPercentArray(new float[]{8, 92})).useAllAvailableWidth();
        Cell icon = new Cell().setBorder(Border.NO_BORDER).setPadding(0)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
        addResourceIcon(icon, iconName, 9, 9);
        row.addCell(icon);
        row.addCell(new Cell().add(new Paragraph(title).setBold().setFontColor(accent)
                .setFontSize(7).setMargin(0))
            .setBorder(Border.NO_BORDER).setPadding(0)
            .setVerticalAlignment(VerticalAlignment.MIDDLE));
        return row;
    }

    private static Table refSection(String title, String body, Color accent) {
        Table box = new Table(1).useAllAvailableWidth().setMarginBottom(3);
        box.addCell(new Cell().add(new Paragraph(title).setBold().setFontColor(accent).setFontSize(7).setMargin(0))
            .setPadding(3).setBorder(new SolidBorder(PDF_LINE,.6f)));
        box.addCell(new Cell().add(new Paragraph(body).setFontSize(6.8f).setMultipliedLeading(1.2f).setMargin(0))
            .setPadding(5).setMinHeight(title.contains("TERMS") ? 58 : 28)
            .setBorder(new SolidBorder(PDF_LINE,.6f)));
        return box;
    }

    private static void addConfiguredQr(Cell cell, Data data, PdfDocument pdf) {
        Path qrPath = configuredAsset("payment.qrImagePath");
        if (qrPath != null) try {
            cell.add(new Image(configuredQrImageData(qrPath)).setAutoScale(true)
                .setMaxWidth(38).setMaxHeight(38)
                .setHorizontalAlignment(HorizontalAlignment.CENTER));
            return;
        } catch (Exception ignored) {}
        addGeneratedQr(cell, data, pdf);
    }

    /**
     * Reads the exact payment image selected in Settings.
     *
     * <p>A bank/UPI QR may be uploaded as either a cropped square or a phone
     * screenshot. For a portrait/landscape screenshot we extract its central QR
     * panel before inserting it into the document. This keeps the QR large and
     * scannable without silently falling back to a generated code.</p>
     */
    private static ImageData configuredQrImageData(Path qrPath) throws Exception {
        BufferedImage source = ImageIO.read(qrPath.toFile());
        if (source == null) {
            throw new IllegalArgumentException("Unsupported QR image: " + qrPath);
        }

        int width = source.getWidth();
        int height = source.getHeight();
        double ratio = (double) width / Math.max(1, height);
        BufferedImage selected = source;

        if (ratio < .85d) {
            // Google Pay and similar portrait exports place the QR in the upper
            // middle of the screenshot. Crop that square while retaining its
            // required quiet border.
            int side = Math.max(1, Math.min(width, (int) Math.round(width * .78d)));
            int x = Math.max(0, (width - side) / 2);
            int centerY = (int) Math.round(height * .43d);
            int y = Math.max(0, Math.min(height - side, centerY - side / 2));
            selected = source.getSubimage(x, y, side, side);
        } else if (ratio > 1.15d) {
            int side = Math.max(1, Math.min(height, (int) Math.round(height * .78d)));
            int centerX = (int) Math.round(width * .50d);
            int x = Math.max(0, Math.min(width - side, centerX - side / 2));
            int y = Math.max(0, (height - side) / 2);
            selected = source.getSubimage(x, y, side, side);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // A 600-pixel QR remains crisp and scannable in the small PDF panel,
        // while avoiding multi-megabyte documents from phone screenshots.
        selected = scaleImage(selected, 600, true);
        ImageIO.write(selected, "png", output);
        return ImageDataFactory.create(output.toByteArray());
    }

    /** Loads a configured image and bounds its embedded PDF resolution. */
    private static ImageData configuredAssetImageData(Path path, int maxDimension)
        throws Exception {
        AssetCacheKey key = assetCacheKey(path, "scaled-" + maxDimension);
        byte[] cached = ASSET_IMAGE_CACHE.get(key);
        if (cached == null) {
            BufferedImage source = ImageIO.read(path.toFile());
            if (source == null) {
                throw new IllegalArgumentException("Unsupported configured image: " + path);
            }
            BufferedImage selected = scaleImage(source, maxDimension, false);
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                ImageIO.write(selected, "png", output);
                cached = output.toByteArray();
            }
            cacheAssetBytes(key, cached);
        }
        return ImageDataFactory.create(cached);
    }

    private static ImageData cachedRawAssetImageData(Path path) throws Exception {
        AssetCacheKey key = assetCacheKey(path, "raw");
        byte[] cached = ASSET_IMAGE_CACHE.get(key);
        if (cached == null) {
            cached = Files.readAllBytes(path);
            cacheAssetBytes(key, cached);
        }
        return ImageDataFactory.create(cached);
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

    /** Preserves aspect ratio while preparing an image for compact PDF embedding. */
    private static BufferedImage scaleImage(BufferedImage source, int maxDimension,
                                             boolean preserveQrEdges) {
        int largest = Math.max(source.getWidth(), source.getHeight());
        if (largest <= maxDimension) {
            return source;
        }
        double scale = (double) maxDimension / largest;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            preserveQrEdges ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }

    private static Table referenceFooterBand(Color accent, Kind kind) {
        Table footer = new Table(UnitValue.createPercentArray(new float[]{1,1,1,1})).useAllAvailableWidth();
        footer.setBackgroundColor(accent);
        String[] values = {config("company.phone", ""), config("company.email", ""),
            config("company.website", ""), config("company.state", "India")};
        String[] icons = {"phone.png", "email.png", "website.png", "location.png"};
        for (int index = 0; index < values.length; index++) {
            footer.addCell(footerIconCell(icons[index], values[index]));
        }
        return footer;
    }

    /** Footer contact cell used by the approved Sales Invoice prototype. */
    private static Cell footerIconCell(String iconName, String value) {
        Cell container = new Cell().setBorder(Border.NO_BORDER).setPadding(2)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
        Table row = new Table(UnitValue.createPercentArray(new float[]{12, 88})).useAllAvailableWidth();
        Cell icon = new Cell().setBorder(Border.NO_BORDER).setPadding(0);
        try (InputStream stream = ResourceLocator.open("/pdf/icons/" + iconName)) {
            if (stream != null) {
                icon.add(new Image(ImageDataFactory.create(stream.readAllBytes()))
                    .setWidth(7).setHeight(7).setHorizontalAlignment(HorizontalAlignment.RIGHT));
            }
        } catch (Exception ignored) {}
        row.addCell(icon);
        row.addCell(new Cell().add(new Paragraph(value).setFontSize(6.2f).setMargin(0))
            .setFontColor(ColorConstants.WHITE).setBorder(Border.NO_BORDER).setPadding(0)
            .setTextAlignment(TextAlignment.CENTER));
        container.add(row);
        return container;
    }

    private static void applyUnicodeFont(Document document) throws Exception {
        Path font = Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts", "arial.ttf");
        if (Files.isRegularFile(font)) {
            document.setFont(PdfFontFactory.createFont(font.toString(), PdfEncodings.IDENTITY_H));
        }
    }

    /** Company identity and document identity header. */

    /** Bill-to, ship-to and context-specific identity cards. */



    /** Line-item table shared across invoices, quotations and refund notes. */

    /** Amount in words, bank data and totals. */

    /** Terms and configured signature image. */

    /** Uploaded UPI QR image, or generated UPI QR when no image was uploaded. */

    private static void addGeneratedQr(Cell cell, Data data, PdfDocument pdf) {
        String upi = config("payment.upiId", "");
        if (upi.isBlank()) return;
        try {
            BarcodeQRCode code = new BarcodeQRCode(
                "upi://pay?pa=" + upi +
                    "&pn=" + url(config("payment.accountHolder", config("company.name", "DSE ERP"))) +
                    "&am=" + String.format(Locale.US, "%.2f", data.total) + "&cu=INR");
            cell.add(new Image(code.createFormXObject(pdf))
                .setWidth(54).setHeight(54)
                .setHorizontalAlignment(HorizontalAlignment.CENTER));
        } catch (Exception ignored) {
            // Bank details remain visible if QR generation is unavailable.
        }
    }

    private static void addWatermarkAndPages(PdfDocument pdf, String title, Color accent)
        throws Exception {
        for (int pageNumber = 1; pageNumber <= pdf.getNumberOfPages(); pageNumber++) {
            PdfPage page = pdf.getPage(pageNumber);
            PdfCanvas watermark = new PdfCanvas(
                page.newContentStreamBefore(), page.getResources(), pdf);
            watermark.saveState();
            watermark.setExtGState(new PdfExtGState().setFillOpacity(.045f));
            watermark.setFillColor(accent);
            watermark.beginText().setFontAndSize(PdfFontFactory.createFont(), 52)
                .setTextMatrix(1, 0, .25f, 1, 105, 360)
                .showText(title).endText();
            watermark.restoreState();

            PdfCanvas footer = new PdfCanvas(page);
            footer.beginText().setFontAndSize(PdfFontFactory.createFont(), 7)
                .moveText(280, 10)
                .showText("Page " + pageNumber + " of " + pdf.getNumberOfPages())
                .endText();
        }
    }

    /** Loads a sales or purchase invoice through the Spring operations API. */
    /** Loads a quotation into the same branded model used by invoices. */
    /** Loads a sales/purchase return and derives its refund note details. */
    private static Cell card(String title, String body, Color accent) {
        Cell cell = new Cell().setBorder(new SolidBorder(LINE, .7f)).setPadding(0);
        cell.add(new Paragraph(title).setBold().setFontColor(ColorConstants.WHITE)
            .setBackgroundColor(accent).setPadding(6).setFontSize(9));
        cell.add(new Paragraph(body).setPadding(7).setFontSize(8.5f).setMultipliedLeading(1.35f));
        return cell;
    }

    private static Cell plainCell() {
        return new Cell().setBorder(Border.NO_BORDER).setPadding(4);
    }

    /** Adds a compact company-contact row using a bundled, PDF-safe PNG icon. */
    private static void addPdfIconLine(Cell container, String iconName, String value,
                                       Color accent, boolean bold) {
        if (value == null || value.isBlank()) return;
        Table line = new Table(UnitValue.createPercentArray(new float[]{11, 89}))
            .useAllAvailableWidth().setMarginTop(1);
        Cell iconCell = new Cell().setBorder(Border.NO_BORDER).setPadding(0)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
        try (InputStream stream = ResourceLocator.open("/pdf/icons/" + iconName)) {
            if (stream != null) {
                iconCell.add(new Image(ImageDataFactory.create(stream.readAllBytes()))
                    .setWidth(8).setHeight(8).setHorizontalAlignment(HorizontalAlignment.CENTER));
            }
        } catch (Exception ignored) {
            // Contact text remains readable if a packaged icon is unavailable.
        }
        Paragraph text = new Paragraph(value).setFontSize(6.8f)
            .setFontColor(bold ? accent : INK).setMargin(0).setMultipliedLeading(1.1f);
        if (bold) text.setBold();
        line.addCell(iconCell);
        line.addCell(new Cell().add(text).setBorder(Border.NO_BORDER).setPadding(0)
            .setVerticalAlignment(VerticalAlignment.MIDDLE));
        container.add(line);
    }

    /** Keeps GSTIN as one uninterrupted inline label/value, matching the reference. */
    private static void addAlignedGstinLine(Cell container, String value, Color accent,
                                            boolean alignWithCompanyIcons) {
        Paragraph gstin = new Paragraph()
            .add(new Text("GSTIN: ").setBold().setFontColor(accent))
            .add(new Text(present(value)).setBold().setFontColor(INK))
            .setFontSize(6.6f).setMarginTop(2).setMarginBottom(0)
            .setMultipliedLeading(1);
        if (alignWithCompanyIcons) {
            // Align the label with the visible contact icons (not their full grid cell).
            gstin.setMarginLeft(13);
        }
        container.add(gstin);
    }

    /** Adds one packaged PNG icon to a cell; missing icons never suppress text. */
    private static void addResourceIcon(Cell cell, String iconName, float maxWidth,
                                        float maxHeight) {
        String resourcePath = iconName.startsWith("/")
            ? iconName
            : "/pdf/icons/" + iconName;
        try (InputStream stream = ResourceLocator.open(resourcePath)) {
            if (stream != null) {
                cell.add(new Image(ImageDataFactory.create(stream.readAllBytes()))
                    .setAutoScale(true).setMaxWidth(maxWidth).setMaxHeight(maxHeight)
                    .setHorizontalAlignment(HorizontalAlignment.CENTER));
            }
        } catch (Exception ignored) {
            // The surrounding heading or party data remains visible without the icon.
        }
    }

    private static Paragraph spacer(float height) {
        return new Paragraph(" ").setFontSize(height).setMargin(0);
    }



    private static void addTotal(Table table, String label, double amount,
                                 boolean grand, Color accent) {
        Cell left = new Cell().add(new Paragraph(label).setBold().setFontSize(grand ? 9 : 8))
            .setPadding(4).setBorder(new SolidBorder(PDF_LINE, .55f));
        Cell right = new Cell().add(new Paragraph("\u20B9 " + money(amount))
                .setBold().setFontSize(grand ? 9 : 8))
            .setTextAlignment(TextAlignment.RIGHT).setPadding(4)
            .setBorder(new SolidBorder(PDF_LINE, .55f));
        if (grand) {
            left.setBackgroundColor(accent).setFontColor(ColorConstants.WHITE);
            right.setBackgroundColor(accent).setFontColor(ColorConstants.WHITE);
        }
        table.addCell(left);
        table.addCell(right);
    }

    private static String bankText() {
        return "Bank Name: " + present(config("payment.bankName", "")) +
            "\nA/c No.: " + present(config("payment.accountNumber", "")) +
            "\nIFSC Code: " + present(config("payment.ifsc", "")) +
            "\nBranch: " + present(config("payment.branch", "")) +
            "\nAccount Holder: " + present(config("payment.accountHolder", ""));
    }

    private static String companyShipText() {
        return config("company.shipAddress",
            config("company.address", "Company delivery address not configured"));
    }

    /** Builds a complete Ship To card, falling back to the billing address. */

    private static Path configuredAsset(String key) {
        String value = config(key, "");
        if (value.isBlank()) return null;
        try {
            Path path = Path.of(value).toAbsolutePath();
            return Files.isRegularFile(path) ? path : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String config(String key, String fallback) {
        return ConfigManager.get(key, fallback);
    }

    /** Returns the configured text, or the supplied business-safe default when blank. */
    private static String configuredOr(String key, String fallback) {
        String value = config(key, "");
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String present(String value) { return ProfessionalDocumentFormatSupport.present(value); }


    private static String pdfValue(String value) { return ProfessionalDocumentFormatSupport.pdfValue(value); }


    /** Returns the first non-blank value without replacing it with display text. */
    private static String firstNonBlank(String first, String fallback) { return ProfessionalDocumentFormatSupport.firstNonBlank(first, fallback); }


    private static String firstNonBlank(String first, String second, String fallback) { return ProfessionalDocumentFormatSupport.firstNonBlank(first, second, fallback); }


    private static String money(double value) { return ProfessionalDocumentFormatSupport.money(value); }


    private static String quantity(double value) { return ProfessionalDocumentFormatSupport.quantity(value); }


    private static String url(String value) { return ProfessionalDocumentFormatSupport.url(value); }


    private static String amountWords(double amount) { return ProfessionalDocumentFormatSupport.amountWords(amount); }



    static final class Data {
        String title;
        String numberLabel;
        String dateLabel;
        String number;
        String date;
        String dueDate = "";
        String poDate = "";
        String originalNumber = "";
        String originalDate = "";
        String partyCode = "";
        String partyName = "";
        String partyAddress = "";
        String partyGstin = "";
        String billingGstin = "";
        String deliveryGstin = "";
        String partyPhone = "";
        String partyEmail = "";
        String salesperson = "";
        String paymentTerms = "";
        String transporter = "";
        String transporterGstin = "";
        String gstType = "";
        String doorDelivery = "";
        String vehicleNumber = "";
        String contactPerson = "";
        String contactPersonMobile = "";
        String transportNote = "";
        String chargeType = "";
        double chargeAmount;
        String reference = "";
        String purchaseOrder = "";
        String shipTo = "";
        String reason = "";
        String status = "";
        double subtotal;
        double gst;
        double total;
        boolean refund;
        boolean quotation;
        boolean sameAsBilling = true;
        List<Line> lines = new ArrayList<>();
    }

    static final class Line {
        String code = "";
        String description = "";
        String hsn = "";
        String unit = "Nos";
        double quantity;
        double rate;
        double gst;
        double discount;
    }
}
