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
import org.example.database.DatabaseManager;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.awt.BasicStroke;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private ProfessionalDocumentRenderer() {
    }

    /** Loads database data and writes the selected business document. */
    public static void render(Path output, Path logo, String number, Kind kind) throws Exception {
        Data data = switch (kind) {
            case SALES_INVOICE -> loadInvoice(number, true);
            case PURCHASE_INVOICE -> loadInvoice(number, false);
            case QUOTATION -> loadQuotation(number);
            case SALES_REFUND -> loadRefund(number, true);
            case PURCHASE_REFUND -> loadRefund(number, false);
        };

        normalizeTotals(data);
        Files.createDirectories(output.toAbsolutePath().getParent());
        logo = configuredDocumentLogo(logo);
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
            salesRefPair(info, "reference", "Order No.", data.purchaseOrder);
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
     * Resolves the Company & Billing logo saved by Settings. The method keeps the
     * caller-provided/bundled logo only as a safe fallback for older installations.
     */
    private static Path configuredDocumentLogo(Path fallback) {
        String configured = ConfigManager.get("company.logoPath", "").trim();
        if (!configured.isBlank()) {
            try {
                Path path = Path.of(configured).toAbsolutePath().normalize();
                if (Files.isRegularFile(path)) return path;
            } catch (Exception ignored) {
                // A stale setting must not prevent invoice generation.
            }
        }
        return fallback;
    }

    /** Adds a configured logo without allowing a missing asset to break rendering. */
    private static void addLogo(Cell cell, Path logo, Color accent, float width, float height) {
        if (logo != null && Files.isRegularFile(logo)) {
            try {
                Image image = new Image(ImageDataFactory.create(logo.toAbsolutePath().toString()));
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

    private static Cell refCard(String title, String body, Color accent) {
        Cell card = new Cell().setBorder(new SolidBorder(PDF_LINE, .7f))
            .setPadding(0).setMinHeight(105);
        card.add(new Paragraph(title).setBold().setFontSize(8).setFontColor(ColorConstants.WHITE)
            .setBackgroundColor(accent).setPadding(4).setMargin(0));
        card.add(new Paragraph(body).setFontSize(7.1f).setMultipliedLeading(1.25f).setPadding(7).setMargin(0));
        return card;
    }

    private static Table referenceGstStrip(Color accent) {
        Table strip = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1})).useAllAvailableWidth();
        strip.setMarginTop(4);
        strip.addCell(stripCell("GST Treatment", "Business - Regular", accent));
        strip.addCell(stripCell("Place of Supply", config("company.state", "Not configured"), accent));
        strip.addCell(stripCell("Reverse Charge", "No", accent));
        return strip;
    }

    private static Cell stripCell(String key, String value, Color accent) {
        return new Cell().add(new Paragraph(key + "  :  " + value).setFontSize(7).setMargin(0))
            .setBorder(new SolidBorder(PDF_LINE, .6f)).setPadding(4);
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
        BufferedImage source = ImageIO.read(path.toFile());
        if (source == null) {
            throw new IllegalArgumentException("Unsupported configured image: " + path);
        }
        BufferedImage selected = scaleImage(source, maxDimension, false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(selected, "png", output);
        return ImageDataFactory.create(output.toByteArray());
    }

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
        try (InputStream stream = ProfessionalDocumentRenderer.class
                .getResourceAsStream("/pdf/icons/" + iconName)) {
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
    private static Table buildHeader(Data data, Path logo, Color accent) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{1.2f, 3.8f, 2.8f}))
            .useAllAvailableWidth();
        table.setBorder(Border.NO_BORDER);

        Cell logoCell = plainCell();
        if (logo != null && Files.isRegularFile(logo)) {
            try {
                logoCell.add(new Image(ImageDataFactory.create(logo.toAbsolutePath().toString()))
                    .setAutoScale(true).setMaxWidth(72).setMaxHeight(72));
            } catch (Exception ignored) {
                // Company text remains available if a configured logo is invalid.
            }
        }
        logoCell.add(new Paragraph("DSE ERP 2.0").setBold().setFontColor(accent).setFontSize(11));
        table.addCell(logoCell);

        Cell company = plainCell()
            .add(new Paragraph(config("company.name", "DSE INFOTECH PVT LTD"))
                .setBold().setFontSize(20).setFontColor(INK))
            .add(new Paragraph(config("company.tagline", "Business Solution - Simplified"))
                .setBold().setFontColor(accent).setFontSize(10))
            .add(new Paragraph(config("company.address", "Configure company address in Settings"))
                .setFontSize(9))
            .add(new Paragraph(config("company.phone", "") + "  |  " +
                config("company.email", "") + "  |  " + config("company.website", ""))
                .setFontSize(8))
            .add(new Paragraph("GSTIN: " + present(config("company.gstin", "")) +
                "   |   PAN: " + present(config("company.pan", "")))
                .setBold().setFontSize(8));
        table.addCell(company);

        Cell identity = new Cell().setBorder(new SolidBorder(accent, 1)).setPadding(0);
        identity.add(new Paragraph(data.title)
            .setBold().setFontSize(17).setFontColor(ColorConstants.WHITE)
            .setBackgroundColor(accent).setTextAlignment(TextAlignment.CENTER).setPadding(9));
        Table pairs = new Table(UnitValue.createPercentArray(new float[]{1.25f, 1.7f}))
            .useAllAvailableWidth();
        addPair(pairs, data.numberLabel, data.number);
        addPair(pairs, data.dateLabel, data.date);
        addPair(pairs,
            data.refund ? "Original Invoice" : data.quotation ? "Valid Until" : "Due Date",
            data.refund ? data.originalNumber : data.dueDate);
        addPair(pairs,
            data.refund ? "Original Date" : "Place of Supply",
            data.refund ? data.originalDate : config("company.state", "Not configured"));
        identity.add(pairs);
        table.addCell(identity);
        return table;
    }

    /** Bill-to, ship-to and context-specific identity cards. */
    private static Table buildPartyCards(Data data, Color accent, Kind kind) {
        Table cards = new Table(UnitValue.createPercentArray(new float[]{1.15f, 1.15f, 1f}))
            .useAllAvailableWidth();
        cards.setHorizontalBorderSpacing(7);

        if (kind == Kind.PURCHASE_INVOICE) {
            cards.addCell(card("SUPPLIER / VENDOR", partyText(data), accent));
            cards.addCell(card("DELIVER TO", companyShipText(), accent));
            cards.addCell(card("PURCHASE DETAILS",
                "Purchase No.: " + present(data.number) +
                    "\nPayment Terms: " + present(data.paymentTerms) +
                    "\nTransport: " + present(data.transporter) +
                    "\nReference: " + present(data.reference), accent));
        } else if (data.refund) {
            cards.addCell(card(kind == Kind.SALES_REFUND ? "CUSTOMER DETAILS" : "SUPPLIER DETAILS",
                partyText(data), accent));
            cards.addCell(card("REFUND DETAILS",
                "Reason: " + present(data.reason) +
                    "\nRefund Against: " + present(data.originalNumber) +
                    "\nRefund Status: " + present(data.status) +
                    "\nReference: " + present(data.number), accent));
            cards.addCell(card("BANK DETAILS (FOR REFUND)", bankText(), accent));
        } else {
            cards.addCell(card("BILL TO", partyText(data), accent));
            cards.addCell(card("SHIP TO", present(data.shipTo), accent));
            cards.addCell(card(data.quotation ? "QUOTATION DETAILS" : "TAX / GST DETAILS",
                contextDetails(data), accent));
        }
        return cards;
    }

    private static String partyText(Data data) {
        return present(data.partyName) + "\n" + present(data.partyAddress) +
            "\nGSTIN: " + present(data.partyGstin) +
            "\nPhone: " + present(data.partyPhone);
    }

    private static String contextDetails(Data data) {
        String first = data.quotation
            ? "Quotation No.: " + present(data.number) + "\nValid Until: " + present(data.dueDate)
            : "GSTIN: " + present(data.partyGstin) +
                "\nPlace of Supply: " + config("company.state", "Not configured");
        return first + "\nSales Person: " + present(data.salesperson) +
            "\nPayment Terms: " + present(data.paymentTerms) +
            "\nTransport: " + present(data.transporter) +
            "\nReference: " + present(data.reference);
    }

    /** Line-item table shared across invoices, quotations and refund notes. */
    private static Table buildItems(Data data, Color accent) {
        float[] widths = {.45f, 1.05f, 1.8f, .8f, .55f, .55f, .8f, .8f, .55f, .85f, .9f};
        Table table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
        String[] headers = {
            "SR.", "ITEM CODE", "DESCRIPTION", "HSN / SAC", "UOM", "QTY",
            "RATE (\u20B9)", "TAXABLE (\u20B9)", "GST %", "GST AMT (\u20B9)", "TOTAL (\u20B9)"
        };
        for (String header : headers) {
            table.addHeaderCell(new Cell().add(new Paragraph(header).setBold().setFontSize(7))
                .setFontColor(ColorConstants.WHITE).setBackgroundColor(accent)
                .setTextAlignment(TextAlignment.CENTER).setPadding(5)
                .setBorder(new SolidBorder(ColorConstants.WHITE, .35f)));
        }

        int rowNumber = 1;
        for (Line line : data.lines) {
            double taxable = line.quantity * line.rate - line.discount;
            double gst = taxable * line.gst / 100;
            double total = taxable + gst;
            String[] values = {
                String.valueOf(rowNumber++), line.code, line.description, present(line.hsn),
                present(line.unit), quantity(line.quantity), money(line.rate), money(taxable),
                quantity(line.gst) + "%", money(gst), money(total)
            };
            for (int column = 0; column < values.length; column++) {
                table.addCell(new Cell().add(new Paragraph(values[column]).setFontSize(7.5f))
                    .setTextAlignment(column >= 5 ? TextAlignment.RIGHT : TextAlignment.LEFT)
                    .setPadding(5).setBackgroundColor(rowNumber % 2 == 0 ? PALE : ColorConstants.WHITE)
                    .setBorder(new SolidBorder(LINE, .45f)));
            }
        }
        return table;
    }

    /** Amount in words, bank data and totals. */
    private static Table buildSummary(Data data, Color accent) {
        Table outer = new Table(UnitValue.createPercentArray(new float[]{1.55f, 1f}))
            .useAllAvailableWidth();
        Cell left = plainCell();
        left.add(labelBox("AMOUNT IN WORDS", accent));
        left.add(new Paragraph(amountWords(data.total) + " Only").setFontSize(9).setPaddingTop(5));
        if (!data.refund) {
            left.add(spacer(8));
            left.add(labelBox("BANK DETAILS", accent));
            left.add(new Paragraph(bankText()).setFontSize(8.5f));
        }
        outer.addCell(left);

        Table totals = new Table(UnitValue.createPercentArray(new float[]{1.05f, 1.15f}))
            .useAllAvailableWidth();
        addTotal(totals, "Total Taxable Amount", data.subtotal, false, accent);
        addTotal(totals, "Total GST Amount", data.gst, false, accent);
        addTotal(totals, "Round Off", data.total - data.subtotal - data.gst, false, accent);
        addTotal(totals, data.refund ? "REFUND AMOUNT" : "GRAND TOTAL", data.total, true, accent);
        outer.addCell(new Cell().add(totals).setBorder(Border.NO_BORDER));
        return outer;
    }

    /** Terms and configured signature image. */
    private static Table buildTerms(Data data, Color accent) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{2.3f, 1}))
            .useAllAvailableWidth();
        Cell terms = new Cell().setBorder(new SolidBorder(LINE, .7f)).setPadding(8);
        terms.add(new Paragraph(data.refund ? "REFUND TERMS & CONDITIONS" : "TERMS & CONDITIONS")
            .setBold().setFontColor(accent).setFontSize(9));
        String defaultTerms = data.refund
            ? "1. This is a system generated refund note.\n2. Refund will be processed after verification.\n3. All disputes are subject to local jurisdiction."
            : "1. Goods once sold will not be taken back.\n2. Payment is due within the agreed period.\n3. All disputes are subject to local jurisdiction.";
        terms.add(new Paragraph(configuredOr("company.terms", defaultTerms)).setFontSize(8));
        table.addCell(terms);

        Cell signature = new Cell().setBorder(new SolidBorder(LINE, .7f)).setPadding(8)
            .setTextAlignment(TextAlignment.CENTER);
        signature.add(new Paragraph("For " + config("company.name", "DSE INFOTECH PVT LTD"))
            .setBold().setFontSize(8));
        Path signaturePath = configuredAsset("company.signaturePath");
        if (signaturePath != null) {
            try {
                signature.add(new Image(configuredAssetImageData(signaturePath, 360))
                    .setAutoScale(true).setMaxHeight(44).setMaxWidth(115)
                    .setHorizontalAlignment(HorizontalAlignment.CENTER));
            } catch (Exception ignored) {
                signature.add(new Paragraph("\n\n"));
            }
        } else {
            signature.add(new Paragraph("\n\n"));
        }
        signature.add(new Paragraph("Authorized Signatory")
            .setBold().setFontColor(accent).setFontSize(9));
        table.addCell(signature);
        return table;
    }

    /** Uploaded UPI QR image, or generated UPI QR when no image was uploaded. */
    private static Table buildFooter(Data data, Color accent, PdfDocument pdf) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{.8f, 2.2f, 1.25f}))
            .useAllAvailableWidth();
        Cell qrCell = plainCell();
        if (!data.refund) {
            qrCell.add(new Paragraph("QR CODE").setBold().setFontColor(accent)
                .setFontSize(7).setMarginBottom(2));
            Path qrPath = configuredAsset("payment.qrImagePath");
            if (qrPath != null) {
                try {
                    qrCell.add(new Image(configuredQrImageData(qrPath))
                        .setAutoScale(true).setMaxWidth(58).setMaxHeight(58));
                } catch (Exception ignored) {
                    addGeneratedQr(qrCell, data, pdf);
                }
            } else {
                addGeneratedQr(qrCell, data, pdf);
            }
        }
        table.addCell(qrCell);

        String documentName = data.refund ? "refund note" : data.quotation ? "quotation" : "invoice";
        table.addCell(plainCell().add(new Paragraph(
                "Thank you for your business!\nThis is a computer generated " +
                    documentName + ".\nNo signature is required.")
            .setFontSize(8).setFontColor(accent)));
        table.addCell(plainCell().add(new Paragraph(
                "Powered by DSE ERP 2.0\n" + config("company.email", "") +
                    "\n" + config("company.phone", ""))
            .setFontSize(8).setTextAlignment(TextAlignment.RIGHT)));
        return table;
    }

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

    /** Loads a sales or purchase invoice and its item lines. */
    private static Data loadInvoice(String number, boolean sales) throws Exception {
        String header = sales ? "sales_header" : "purchase_header";
        String line = sales ? "sales_line" : "purchase_line";
        String foreignKey = sales ? "sales_id" : "purchase_id";
        String partyKey = sales ? "customer_id" : "supplier_id";
        String sql = "SELECT h.*,pm.party_code,pm.name,pm.address,pm.gstin,pm.phone,pm.email " +
            "FROM " + header + " h LEFT JOIN party_master pm ON pm.id=h." + partyKey +
            " WHERE TRIM(UPPER(h.invoice_no))=TRIM(UPPER(?))";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, number);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException((sales ? "Sales" : "Purchase") +
                        " not found: " + number);
                }
                Data data = new Data();
                data.title = "TAX INVOICE";
                data.numberLabel = "Invoice No.";
                data.dateLabel = "Invoice Date";
                data.number = result.getString("invoice_no");
                data.date = result.getString("invoice_date");
                data.dueDate = safeColumn(result, "due_date");
                data.poDate = safeColumn(result, "po_date");
                populateParty(data, result);
                if (sales) {
                    data.partyAddress = firstNonBlank(safeColumn(result, "billing_address"), data.partyAddress);
                }
                data.subtotal = result.getDouble("subtotal");
                data.gst = result.getDouble("gst_amount");
                data.total = result.getDouble("total_amount");
                data.salesperson = safeColumn(result, "salesperson");
                data.paymentTerms = safeColumn(result, "payment_terms");
                data.transporter = safeColumn(result, "transporter");
                data.gstType = safeColumn(result, "gst_type");
                data.doorDelivery = safeColumn(result, "door_delivery");
                data.vehicleNumber = safeColumn(result, "vehicle_number");
                data.contactPerson = safeColumn(result, "contact_person");
                data.transportNote = safeColumn(result, "transport_note");
                data.chargeType = safeColumn(result, "charge_type");
                data.chargeAmount = safeDoubleColumn(result, "charge_amount");
                data.reference = safeColumn(result, "reference_no");
                data.purchaseOrder = sales
                    ? safeColumn(result, "order_no")
                    : firstNonBlank(safeColumn(result, "purchase_order_no"),
                        firstNonBlank(safeColumn(result, "po_number"), safeColumn(result, "purchase_order")));
                if (sales) data.partyGstin = firstNonBlank(safeColumn(result, "gstin"), data.partyGstin);
                data.shipTo = sales
                    ? firstNonBlank(safeColumn(result, "delivery_address"), data.partyAddress)
                    : companyShipText();
                loadLines(connection, data, line, foreignKey, result.getInt("id"));
                return data;
            }
        }
    }

    /** Loads a quotation into the same branded model used by invoices. */
    private static Data loadQuotation(String number) throws Exception {
        String sql = "SELECT q.*,pm.party_code,pm.name,pm.address,pm.gstin,pm.phone,pm.email " +
            "FROM quotation_header q LEFT JOIN party_master pm ON pm.id=q.customer_id " +
            "WHERE TRIM(UPPER(q.quotation_no))=TRIM(UPPER(?))";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, number);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Quotation not found: " + number);
                Data data = new Data();
                data.quotation = true;
                data.title = "QUOTATION";
                data.numberLabel = "Quotation No.";
                data.dateLabel = "Quotation Date";
                data.number = result.getString("quotation_no");
                data.date = result.getString("quotation_date");
                data.dueDate = safeColumn(result, "valid_until");
                populateParty(data, result);
                data.subtotal = result.getDouble("subtotal");
                data.gst = result.getDouble("gst_amount");
                data.total = result.getDouble("total_amount");
                data.salesperson = safeColumn(result, "salesperson");
                data.paymentTerms = "As agreed";
                data.reference = safeColumn(result, "source");
                data.shipTo = data.partyAddress;
                loadLines(connection, data, "quotation_line", "quotation_id", result.getInt("id"));
                return data;
            }
        }
    }

    /** Loads a sales/purchase return and derives its refund note details. */
    private static Data loadRefund(String number, boolean sales) throws Exception {
        String sql = "SELECT r.*,pm.party_code,pm.name,pm.address,pm.gstin,pm.phone " +
            "FROM return_register r LEFT JOIN party_master pm ON pm.id=r.party_id " +
            "WHERE TRIM(UPPER(r.return_no))=TRIM(UPPER(?)) AND UPPER(r.return_type) LIKE ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, number);
            statement.setString(2, sales ? "SALES%" : "PURCHASE%");
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Refund record not found: " + number);
                Data data = new Data();
                data.refund = true;
                data.title = sales ? "SALES REFUND NOTE" : "PURCHASE REFUND NOTE";
                data.numberLabel = "Refund Note No.";
                data.dateLabel = "Refund Note Date";
                data.number = result.getString("return_no");
                data.date = result.getString("return_date");
                data.originalNumber = safeColumn(result, "invoice_no");
                data.originalDate = originalDate(connection, data.originalNumber, sales);
                populateParty(data, result);
                data.reason = safeColumn(result, "reason");
                data.status = safeColumn(result, "refund_status");
                data.total = result.getDouble("amount");
                String itemCode = safeColumn(result, "item_code");
                double quantity = result.getDouble("quantity");
                Line item = loadReturnLine(connection, itemCode, quantity, data.total, sales,
                    data.originalNumber);
                data.lines.add(item);
                data.gst = (quantity * item.rate - item.discount) * item.gst / 100;
                data.subtotal = data.total - data.gst;
                return data;
            }
        }
    }

    private static void populateParty(Data data, ResultSet result) {
        data.partyCode = safeColumn(result, "party_code");
        data.partyName = safeColumn(result, "name");
        data.partyAddress = safeColumn(result, "address");
        data.partyGstin = safeColumn(result, "gstin");
        data.partyPhone = safeColumn(result, "phone");
        data.partyEmail = safeColumn(result, "email");
    }

    private static void loadLines(Connection connection, Data data, String table,
                                  String foreignKey, int id) throws SQLException {
        String sql = "SELECT l.*,im.description,im.hsn,im.unit FROM " + table +
            " l LEFT JOIN item_master im ON im.item_code=l.item_code WHERE l." +
            foreignKey + "=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Line item = new Line();
                    item.code = result.getString("item_code");
                    item.description = present(result.getString("description"));
                    item.hsn = safeColumn(result, "hsn");
                    item.unit = safeColumn(result, "unit");
                    item.quantity = result.getDouble("quantity");
                    item.rate = result.getDouble("rate");
                    item.gst = result.getDouble("gst_percent");
                    double taxable = item.quantity * item.rate;
                    item.discount = Math.max(0,
                        taxable - result.getDouble("line_total") / (1 + item.gst / 100));
                    data.lines.add(item);
                }
            }
        }
    }

    private static Line loadReturnLine(Connection connection, String code, double quantity,
                                       double amount, boolean sales, String invoiceNo)
        throws SQLException {
        Line item = new Line();
        item.code = code;
        item.quantity = quantity;
        String lineTable = sales ? "sales_line" : "purchase_line";
        String headerTable = sales ? "sales_header" : "purchase_header";
        String foreignKey = sales ? "sales_id" : "purchase_id";
        String sql = "SELECT im.description,im.hsn,im.unit,l.rate,l.gst_percent " +
            "FROM item_master im LEFT JOIN " + lineTable + " l ON l.item_code=im.item_code " +
            "LEFT JOIN " + headerTable + " h ON h.id=l." + foreignKey +
            " AND h.invoice_no=? WHERE im.item_code=? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, invoiceNo);
            statement.setString(2, code);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    item.description = present(result.getString(1));
                    item.hsn = present(result.getString(2));
                    item.unit = present(result.getString(3));
                    item.rate = result.getDouble(4);
                    item.gst = result.getDouble(5);
                }
            }
        }
        if (item.rate == 0 && quantity > 0) item.rate = amount / quantity / (1 + item.gst / 100);
        return item;
    }

    private static String originalDate(Connection connection, String number, boolean sales)
        throws SQLException {
        String sql = "SELECT invoice_date FROM " +
            (sales ? "sales_header" : "purchase_header") + " WHERE invoice_no=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, number);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : "";
            }
        }
    }

    private static void normalizeTotals(Data data) {
        if (data.lines.isEmpty()) return;
        double taxable = 0;
        double gst = 0;
        for (Line line : data.lines) {
            double base = line.quantity * line.rate - line.discount;
            taxable += base;
            gst += base * line.gst / 100;
        }
        data.subtotal = taxable;
        data.gst = gst;
        data.total = taxable + gst;
    }

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
        try (InputStream stream = ProfessionalDocumentRenderer.class
                .getResourceAsStream("/pdf/icons/" + iconName)) {
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
        try (InputStream stream = ProfessionalDocumentRenderer.class
                .getResourceAsStream(resourcePath)) {
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

    private static Paragraph labelBox(String value, Color accent) {
        return new Paragraph(value).setBold().setFontColor(ColorConstants.WHITE)
            .setBackgroundColor(accent).setPadding(4).setFontSize(8).setWidth(150);
    }

    private static void addPair(Table table, String key, String value) {
        table.addCell(plainCell().add(new Paragraph(key).setBold().setFontSize(8)));
        table.addCell(plainCell().add(new Paragraph(present(value)).setFontSize(8)));
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
    private static String shipToText(Data data) {
        return present(data.partyName) + "\n" +
            present(firstNonBlank(data.shipTo, data.partyAddress)) + "\n" +
            "Phone: " + present(data.partyPhone);
    }

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

    private static String present(String value) {
        return value == null || value.isBlank() ? "Not provided" : value;
    }

    /** Returns the first non-blank value without replacing it with display text. */
    private static String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : Objects.toString(fallback, "");
    }

    private static String safeColumn(ResultSet result, String name) {
        try {
            return Objects.toString(result.getString(name), "");
        } catch (SQLException ignored) {
            return "";
        }
    }

    private static double safeDoubleColumn(ResultSet result, String name) {
        try {
            return result.getDouble(name);
        } catch (SQLException ignored) {
            return 0;
        }
    }

    private static String money(double value) {
        return MONEY.format(value);
    }

    private static String quantity(double value) {
        return value == Math.rint(value)
            ? String.valueOf((long) value)
            : new DecimalFormat("0.###").format(value);
    }

    private static String url(String value) {
        return value.replace(" ", "%20");
    }

    private static String amountWords(double amount) {
        long value = Math.round(amount);
        return value == 0 ? "Zero Rupees" : words(value) + " Rupees";
    }

    private static String words(long value) {
        String[] ones = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven",
            "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen",
            "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty",
            "Seventy", "Eighty", "Ninety"};
        if (value < 20) return ones[(int) value];
        if (value < 100) return tens[(int) value / 10] +
            (value % 10 == 0 ? "" : " " + ones[(int) value % 10]);
        if (value < 1_000) return words(value / 100) + " Hundred" +
            (value % 100 == 0 ? "" : " " + words(value % 100));
        if (value < 100_000) return words(value / 1_000) + " Thousand" +
            (value % 1_000 == 0 ? "" : " " + words(value % 1_000));
        if (value < 10_000_000) return words(value / 100_000) + " Lakh" +
            (value % 100_000 == 0 ? "" : " " + words(value % 100_000));
        return words(value / 10_000_000) + " Crore" +
            (value % 10_000_000 == 0 ? "" : " " + words(value % 10_000_000));
    }

    private static final class Data {
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
        String partyPhone = "";
        String partyEmail = "";
        String salesperson = "";
        String paymentTerms = "";
        String transporter = "";
        String gstType = "";
        String doorDelivery = "";
        String vehicleNumber = "";
        String contactPerson = "";
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
        List<Line> lines = new ArrayList<>();
    }

    private static final class Line {
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
