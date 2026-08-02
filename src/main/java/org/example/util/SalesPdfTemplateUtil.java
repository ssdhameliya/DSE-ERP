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
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.borders.Border;

import java.io.InputStream;
import java.sql.*;
import java.util.Properties;
import org.example.database.DatabaseManager;

public class SalesPdfTemplateUtil {

    public static void createSalesPdf(String dest, String logoPath, String invoiceNo, String docType) throws Exception {
        // Load config.properties from resources
        Properties config = new Properties();
        try (InputStream is = SalesPdfTemplateUtil.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) config.load(is);
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            // Fetch sales header
            PreparedStatement psHeader = conn.prepareStatement("SELECT * FROM sales_header WHERE TRIM(UPPER(invoice_no)) = TRIM(UPPER(?))");
            psHeader.setString(1, invoiceNo);
            ResultSet rsHeader = psHeader.executeQuery();
            if (!rsHeader.next()) throw new Exception(docType + " not found: " + invoiceNo);

            int headerId = rsHeader.getInt("id");
            String invoiceDate = rsHeader.getString("invoice_date");
            int customerId = rsHeader.getInt("customer_id");
            double subtotal = rsHeader.getDouble("subtotal");
            double gst = rsHeader.getDouble("gst_amount");
            double total = rsHeader.getDouble("total_amount");
            String remarks = rsHeader.getString("remarks");

            // Fetch customer details
            PreparedStatement psCust = conn.prepareStatement(
                "SELECT name, address, gstin, email, phone FROM party_master WHERE id = ?"
            );
            psCust.setInt(1, customerId);
            ResultSet rsCust = psCust.executeQuery();
            String custName = "", custAddress = "", custGstin = "", custEmail = "", custPhone = "";
            if (rsCust.next()) {
                custName = rsCust.getString("name");
                custAddress = rsCust.getString("address");
                custGstin = rsCust.getString("gstin");
                custEmail = rsCust.getString("email");
                custPhone = rsCust.getString("phone");
            }

            // Fetch line items
            PreparedStatement psItems = conn.prepareStatement(
                "SELECT item_code, quantity, rate, gst_percent, line_total FROM sales_line WHERE sales_id = ?"
            );
            psItems.setInt(1, headerId);
            ResultSet rsItems = psItems.executeQuery();

            // Create PDF
            PdfWriter writer = new PdfWriter(dest);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc);

            // Header block (logo + title + date + invoice no)
            Image logo = null;
            try {
                ImageData img = ImageDataFactory.create(logoPath);
                logo = new Image(img).setWidth(100);
            } catch (Exception e) {
                // If logo fails to load, continue without it
            }

            Paragraph title = new Paragraph(docType.toUpperCase() + " INVOICE")
                .setFontSize(22).setBold().setFontColor(new DeviceRgb(0, 153, 0))
                .setTextAlignment(TextAlignment.RIGHT);
            Paragraph datePara = new Paragraph("DATE: " + invoiceDate).setTextAlignment(TextAlignment.RIGHT);
            Paragraph invPara = new Paragraph("Invoice #: " + invoiceNo).setTextAlignment(TextAlignment.RIGHT);

            Table headerTable = new Table(UnitValue.createPercentArray(new float[]{2, 3}))
                .setWidth(UnitValue.createPercentValue(100));
            if (logo != null) {
                headerTable.addCell(new Cell().add(logo).setBorder(Border.NO_BORDER));
            } else {
                headerTable.addCell(new Cell().add(new Paragraph(" ")).setBorder(Border.NO_BORDER));
            }
            Cell right = new Cell().setBorder(Border.NO_BORDER);
            right.add(title);
            right.add(datePara);
            right.add(invPara);
            headerTable.addCell(right);
            doc.add(headerTable);

            // Bill To / Ship To blocks
            Table partyTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100));
            partyTable.addCell(new Cell().add(new Paragraph("BILL TO").setBold())
                .setBackgroundColor(new DeviceRgb(0, 153, 0)).setFontColor(ColorConstants.WHITE));
            partyTable.addCell(new Cell().add(new Paragraph("SHIP TO").setBold())
                .setBackgroundColor(new DeviceRgb(0, 153, 0)).setFontColor(ColorConstants.WHITE));

            partyTable.addCell(new Cell().add(new Paragraph(
                (custName == null ? "" : custName) + "\n" +
                    (custAddress == null ? "" : custAddress) +
                    (custGstin == null || custGstin.isEmpty() ? "" : "\nGSTIN: " + custGstin) +
                    (custEmail == null || custEmail.isEmpty() ? "" : "\nEmail: " + custEmail) +
                    (custPhone == null || custPhone.isEmpty() ? "" : "\nPhone: " + custPhone)
            )));
            partyTable.addCell(new Cell().add(new Paragraph(
                config.getProperty("shipto.name", "DS Engineers Pvt Ltd") + "\n" +
                    config.getProperty("shipto.address", "XYZ Street, Bengaluru") + "\n" +
                    "GSTIN: " + config.getProperty("shipto.gstin", "29ABCDE1234F1Z5") + "\n" +
                    "Email: " + config.getProperty("shipto.email", "info@dsengineers.com")
            )));
            doc.add(partyTable);

            // Requisitioner / Ship Via / F.O.B. / Shipping Terms
            Table reqTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100));
            String[] reqHeaders = {"REQUISITIONER", "SHIP VIA", "F.O.B.", "SHIPPING TERMS"};
            for (String h : reqHeaders) {
                reqTable.addCell(new Cell().add(new Paragraph(h).setBold().setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(new DeviceRgb(0, 153, 0)).setTextAlignment(TextAlignment.CENTER));
            }
            reqTable.addCell(new Cell().add(new Paragraph(config.getProperty("requisitioner", "-"))));
            reqTable.addCell(new Cell().add(new Paragraph(config.getProperty("shipvia", "Courier"))));
            reqTable.addCell(new Cell().add(new Paragraph(config.getProperty("fob", "Factory"))));
            reqTable.addCell(new Cell().add(new Paragraph(config.getProperty("shipping.terms", "Prepaid"))));
            doc.add(reqTable);

            // Items table
            Table itemTable = new Table(UnitValue.createPercentArray(new float[]{3, 4, 2, 2, 2}))
                .setWidth(UnitValue.createPercentValue(100));
            String[] headers = {"ITEM #", "DESCRIPTION", "QTY", "UNIT PRICE", "TOTAL"};
            for (String h : headers) {
                itemTable.addHeaderCell(new Cell().add(new Paragraph(h).setBold().setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(new DeviceRgb(0, 153, 0)).setTextAlignment(TextAlignment.CENTER)
                    .setBorder(new SolidBorder(ColorConstants.BLACK, 1)));
            }

            boolean alternate = false;
            while (rsItems.next()) {
                DeviceRgb rowColor = alternate ? new DeviceRgb(245, 245, 245) : (DeviceRgb) ColorConstants.WHITE;
                String itemCode = rsItems.getString("item_code");
                double qty = rsItems.getDouble("quantity");
                double rate = rsItems.getDouble("rate");
                double lineTotal = rsItems.getDouble("line_total");

                itemTable.addCell(new Cell().add(new Paragraph(itemCode == null ? "" : itemCode)).setBackgroundColor(rowColor));
                itemTable.addCell(new Cell().add(new Paragraph("Product")).setBackgroundColor(rowColor));
                itemTable.addCell(new Cell().add(new Paragraph(String.valueOf(qty)).setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(rowColor));
                itemTable.addCell(new Cell().add(new Paragraph("₹" + rate).setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(rowColor));
                itemTable.addCell(new Cell().add(new Paragraph("₹" + lineTotal).setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(rowColor));
                alternate = !alternate;
            }
            doc.add(itemTable);

            // Totals block
            Table totals = new Table(UnitValue.createPercentArray(new float[]{3, 2}))
                .setWidth(UnitValue.createPercentValue(50))
                .setTextAlignment(TextAlignment.RIGHT);
            totals.addCell(new Cell().add(new Paragraph("SUBTOTAL").setBold()));
            totals.addCell(new Cell().add(new Paragraph("₹" + subtotal)));
            totals.addCell(new Cell().add(new Paragraph("GST").setBold()));
            totals.addCell(new Cell().add(new Paragraph("₹" + gst)));
            totals.addCell(new Cell().add(new Paragraph("TOTAL").setBold()).setBackgroundColor(new DeviceRgb(255, 223, 0)));
            totals.addCell(new Cell().add(new Paragraph("₹" + total).setBold()).setBackgroundColor(new DeviceRgb(255, 223, 0)));
            doc.add(totals);

            // Remarks / Comments
            if (remarks != null && !remarks.trim().isEmpty()) {
                doc.add(new Paragraph("\nComments / Special Instructions:").setBold());
                doc.add(new Paragraph(remarks));
            } else {
                doc.add(new Paragraph("\nComments / Special Instructions: -"));
            }

            // Contact info and authorized signature
            doc.add(new Paragraph("\nIf you have any questions about this invoice, please contact Accounts - " +
                config.getProperty("accounts.phone", "+91-80-12345678") + " - " +
                config.getProperty("accounts.email", "accounts@dsengineers.com")));

            doc.add(new Paragraph("\n\nAuthorized Signature\n\n________________________"));

            // Watermark (applies to first page)
            if (logoPath != null && !logoPath.trim().isEmpty()) {
                try {
                    PdfPage firstPage = pdfDoc.getFirstPage();
                    PdfCanvas canvas = new PdfCanvas(firstPage.newContentStreamBefore(), firstPage.getResources(), pdfDoc);
                    PdfExtGState gs = new PdfExtGState().setFillOpacity(0.06f);
                    canvas.saveState();
                    canvas.setExtGState(gs);
                    ImageData watermark = ImageDataFactory.create(logoPath);
                    Rectangle pageSize = firstPage.getPageSize();
                    float w = pageSize.getWidth() / 2;
                    float h = pageSize.getHeight() / 2;
                    Rectangle rect = new Rectangle((pageSize.getWidth() - w) / 2, (pageSize.getHeight() - h) / 2, w, h);
                    canvas.addImageFittedIntoRectangle(watermark, rect, false);
                    canvas.restoreState();
                } catch (Exception e) {
                    // ignore watermark errors
                }
            }

            doc.close();
            pdfDoc.close();
        }
    }
}
