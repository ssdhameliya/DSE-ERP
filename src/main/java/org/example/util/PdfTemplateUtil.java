package org.example.util;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.borders.Border;

import java.io.InputStream;
import java.sql.*;
import java.util.Properties;
import org.example.database.DatabaseManager;

public class PdfTemplateUtil {

    /**
     * Create a premium Purchase Order PDF.
     *
     * @param dest     output PDF path
     * @param logoPath path to logo image used for header and watermark
     * @param invoiceNo purchase order number
     * @param docType  document type label (e.g., "PURCHASE")
     * @throws Exception on error
     */
    public static void createDocument(String dest, String logoPath, String invoiceNo, String docType) throws Exception {
        // Load configuration from classpath resources/config.properties
        Properties config = new Properties();
        try (InputStream is = PdfTemplateUtil.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                config.load(is);
            } // if null, we'll use fallback defaults below
        }

        // Read ship-to and other values from properties (classpath)
        String shipToName = config.getProperty("shipto.name", "DS Engineers Pvt Ltd");
        String shipToAddress = config.getProperty("shipto.address", "XYZ Street, Bengaluru");
        String shipToEmail = config.getProperty("shipto.email", "info@dsengineers.com");
        String shipToGstin = config.getProperty("shipto.gstin", "29ABCDE1234F1Z5");

        String requisitioner = config.getProperty("requisitioner", "John Doe");
        String shipVia = config.getProperty("shipvia", "Courier");
        String fob = config.getProperty("fob", "Factory");
        String shippingTerms = config.getProperty("shipping.terms", "Prepaid");
        String shippingValue = config.getProperty("shipping.value", "-");
        String otherValue = config.getProperty("other.value", "-");
        String contactInfo = config.getProperty("contact.info", "[Name, Phone #, Email]");

        // Database connection
        try (Connection conn = DatabaseManager.getConnection()) {

            // Fetch header
            String headerSql = "SELECT * FROM purchase_header WHERE TRIM(UPPER(invoice_no)) = TRIM(UPPER(?))";

            try (PreparedStatement ps = conn.prepareStatement(headerSql)) {
                ps.setString(1, invoiceNo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new Exception(docType + " not found: " + invoiceNo);
                    }

                    int headerId = rs.getInt("id");
                    String invoiceDate = rs.getString("invoice_date");
                    int supplierId = rs.getInt("supplier_id");
                    double subtotal = rs.getDouble("subtotal");
                    double gst = rs.getDouble("gst_amount");
                    double total = rs.getDouble("total_amount");
                    String remarks = rs.getString("remarks");

                    // Fetch supplier details
                    String supplierName = "";
                    String supplierAddress = "";
                    String supplierGSTIN = "";
                    String supplierEmail = "";
                    String supplierSql = "SELECT name, address, gstin, email FROM party_master WHERE id = ?";
                    try (PreparedStatement psSupplier = conn.prepareStatement(supplierSql)) {
                        psSupplier.setInt(1, supplierId);
                        try (ResultSet rsSupplier = psSupplier.executeQuery()) {
                            if (rsSupplier.next()) {
                                supplierName = rsSupplier.getString("name");
                                supplierAddress = rsSupplier.getString("address");
                                supplierGSTIN = rsSupplier.getString("gstin");
                                supplierEmail = rsSupplier.getString("email");
                            }
                        }
                    }

                    // Fetch line items
                    String itemsSql = "SELECT item_code, quantity, rate, gst_percent, line_total FROM purchase_line WHERE purchase_id = ?";
                    try (PreparedStatement psItems = conn.prepareStatement(itemsSql)) {
                        psItems.setInt(1, headerId);
                        try (ResultSet rsItems = psItems.executeQuery()) {

                            // Create PDF writer and document
                            PdfWriter writer = new PdfWriter(dest);
                            PdfDocument pdfDoc = new PdfDocument(writer);
                            Document doc = new Document(pdfDoc);

                            // --- Add content (header, tables, items) ---
                            // Header block: logo + title
                            Image logo = new Image(ImageDataFactory.create(logoPath)).setWidth(100);
                            Paragraph poTitle = new Paragraph(docType.toUpperCase() + " ORDER")
                                .setFontSize(22).setBold()
                                .setFontColor(new DeviceRgb(0, 153, 0))
                                .setTextAlignment(TextAlignment.RIGHT);
                            Paragraph dateField = new Paragraph("DATE: " + invoiceDate).setFontSize(12).setTextAlignment(TextAlignment.RIGHT);
                            Paragraph poNumberField = new Paragraph("PO #: " + invoiceNo).setFontSize(12).setTextAlignment(TextAlignment.RIGHT);

                            Table headerTable = new Table(UnitValue.createPercentArray(new float[]{2, 3}))
                                .setWidth(UnitValue.createPercentValue(100));
                            headerTable.addCell(new Cell().add(logo).setBorder(Border.NO_BORDER));
                            Cell rightBlock = new Cell().setBorder(Border.NO_BORDER);
                            rightBlock.add(poTitle);
                            rightBlock.add(dateField);
                            rightBlock.add(poNumberField);
                            headerTable.addCell(rightBlock);
                            doc.add(headerTable);

                            // Vendor & Ship To sections (values from DB and config)
                            Table partyTable = new Table(UnitValue.createPercentArray(new float[]{1,1}))
                                .setWidth(UnitValue.createPercentValue(100));
                            partyTable.addCell(new Cell().add(new Paragraph("VENDOR").setBold())
                                .setBackgroundColor(new DeviceRgb(0,153,0)).setFontColor(ColorConstants.WHITE));
                            partyTable.addCell(new Cell().add(new Paragraph("SHIP TO").setBold())
                                .setBackgroundColor(new DeviceRgb(0,153,0)).setFontColor(ColorConstants.WHITE));

                            partyTable.addCell(new Cell().add(new Paragraph(
                                supplierName + "\n" + supplierAddress + "\nGSTIN: " + supplierGSTIN + "\nEmail: " + supplierEmail)));
                            partyTable.addCell(new Cell().add(new Paragraph(
                                shipToName + "\n" + shipToAddress + "\nEmail: " + shipToEmail + "\nGSTIN: " + shipToGstin)));
                            doc.add(partyTable);

                            // Requisitioner / Ship Via / F.O.B. / Shipping Terms
                            Table reqTable = new Table(UnitValue.createPercentArray(new float[]{1,1,1,1}))
                                .setWidth(UnitValue.createPercentValue(100));
                            String[] reqHeaders = {"REQUISITIONER", "SHIP VIA", "F.O.B.", "SHIPPING TERMS"};
                            for (String h : reqHeaders) {
                                reqTable.addCell(new Cell().add(new Paragraph(h).setBold().setFontColor(ColorConstants.WHITE))
                                    .setBackgroundColor(new DeviceRgb(0,153,0)).setTextAlignment(TextAlignment.CENTER));
                            }
                            reqTable.addCell(new Cell().add(new Paragraph(requisitioner)));
                            reqTable.addCell(new Cell().add(new Paragraph(shipVia)));
                            reqTable.addCell(new Cell().add(new Paragraph(fob)));
                            reqTable.addCell(new Cell().add(new Paragraph(shippingTerms)));
                            doc.add(reqTable);

                            // Item table
                            Table itemTable = new Table(UnitValue.createPercentArray(new float[]{3,4,2,2,2}))
                                .setWidth(UnitValue.createPercentValue(100));
                            String[] headers = {"ITEM #", "DESCRIPTION", "QTY", "UNIT PRICE", "TOTAL"};
                            for (String h : headers) {
                                itemTable.addHeaderCell(new Cell().add(new Paragraph(h).setBold().setFontColor(ColorConstants.WHITE))
                                    .setBackgroundColor(new DeviceRgb(0,153,0)).setTextAlignment(TextAlignment.CENTER)
                                    .setBorder(new SolidBorder(ColorConstants.BLACK, 1)));
                            }

                            boolean alternate = false;
                            while (rsItems.next()) {
                                DeviceRgb rowColor = alternate ? new DeviceRgb(245, 245, 245) : (DeviceRgb) ColorConstants.WHITE;
                                itemTable.addCell(new Cell().add(new Paragraph(rsItems.getString("item_code"))).setBackgroundColor(rowColor));
                                itemTable.addCell(new Cell().add(new Paragraph("Product description")).setBackgroundColor(rowColor));
                                itemTable.addCell(new Cell().add(new Paragraph(String.valueOf(rsItems.getDouble("quantity")))
                                    .setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(rowColor));
                                itemTable.addCell(new Cell().add(new Paragraph("₹" + rsItems.getDouble("rate"))
                                    .setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(rowColor));
                                itemTable.addCell(new Cell().add(new Paragraph("₹" + rsItems.getDouble("line_total"))
                                    .setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(rowColor));
                                alternate = !alternate;
                            }
                            doc.add(itemTable);

                            // Summary section
                            Table totals = new Table(UnitValue.createPercentArray(new float[]{3,2}))
                                .setWidth(UnitValue.createPercentValue(50))
                                .setTextAlignment(TextAlignment.RIGHT);
                            totals.addCell(new Cell().add(new Paragraph("SUBTOTAL").setBold()));
                            totals.addCell(new Cell().add(new Paragraph("₹" + subtotal)));
                            totals.addCell(new Cell().add(new Paragraph("TAX").setBold()));
                            totals.addCell(new Cell().add(new Paragraph("₹" + gst)));
                            totals.addCell(new Cell().add(new Paragraph("SHIPPING").setBold()));
                            totals.addCell(new Cell().add(new Paragraph(shippingValue)));
                            totals.addCell(new Cell().add(new Paragraph("OTHER").setBold()));
                            totals.addCell(new Cell().add(new Paragraph(otherValue)));
                            totals.addCell(new Cell().add(new Paragraph("TOTAL").setBold())
                                .setBackgroundColor(new DeviceRgb(255, 223, 0)));
                            totals.addCell(new Cell().add(new Paragraph("₹" + total).setBold())
                                .setBackgroundColor(new DeviceRgb(255, 223, 0)));
                            doc.add(totals);

                            // Comments / Footer
                            doc.add(new Paragraph("\nComments / Special Instructions: " + (remarks == null ? "" : remarks)));
                            doc.add(new Paragraph("\nIf you have any questions about this purchase order, please contact " + contactInfo));
                            doc.add(new Paragraph("\nAuthorized Signature"));

                            // --- Add watermark behind content on the first page ---
                            // Ensure first page exists
                            PdfPage firstPage = pdfDoc.getFirstPage();
                            if (firstPage == null) {
                                // Force creation of a page by adding an empty paragraph (shouldn't normally be needed)
                                doc.add(new Paragraph(""));
                                firstPage = pdfDoc.getFirstPage();
                            }
                            // Create a canvas that writes to the page's "before" content stream so watermark is behind layout
                            PdfCanvas canvas = new PdfCanvas(firstPage.newContentStreamBefore(), firstPage.getResources(), pdfDoc);
                            PdfExtGState gs1 = new PdfExtGState().setFillOpacity(0.08f); // adjust opacity here
                            canvas.saveState();
                            canvas.setExtGState(gs1);
                            ImageData watermarkImg = ImageDataFactory.create(logoPath);
                            // Adjust rectangle to position watermark as desired
                            Rectangle rect = new Rectangle(100, 200, 400, 400);
                            canvas.addImageFittedIntoRectangle(watermarkImg, rect, false);
                            canvas.restoreState();

                            // Close document and resources
                            doc.close();
                            pdfDoc.close();
                        }
                    }
                }
            }
        }
    }
}
