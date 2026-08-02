package org.example.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces consistent management reports from the live SQLite data.
 */
public final class BusinessReportService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu");
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    public void exportPdf(Path file, LocalDate from, LocalDate to) throws IOException {
        ReportData data = load(from, to);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream c = new PDPageContentStream(doc, page)) {
                float y = 800;
                header(c, y, from, to);
                y -= 92;
                y = section(c, "Executive Summary", y);
                y = metric(c, "Sales", currency(data.salesTotal), y);
                y = metric(c, "Purchases", currency(data.purchaseTotal), y);
                y = metric(c, "Inventory Value", currency(data.stockValue), y);
                y = metric(c, "Low-stock items", String.valueOf(data.lowStock.size()), y);
                y -= 12;
                y = section(c, "Sales by Customer", y);
                y = table(c, y, new String[]{"Customer", "Invoices", "Sales"}, data.salesByCustomer, new float[]{260, 100, 120});
                y -= 12;
                y = section(c, "Low-stock Attention", y);
                table(c, y, new String[]{"Item", "Available", "Minimum", "Unit"}, data.lowStock, new float[]{250, 90, 90, 70});
                footer(c);
            }
            doc.save(file.toFile());
        }
    }

    public void exportExcel(Path file, LocalDate from, LocalDate to) throws IOException {
        ReportData data = load(from, to);
        try (Workbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            CellStyle title = style(wb, IndexedColors.DARK_BLUE, true, IndexedColors.WHITE);
            CellStyle header = style(wb, IndexedColors.BLUE_GREY, true, IndexedColors.WHITE);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("₹#,##0.00"));
            Sheet summary = wb.createSheet("Summary");
            summary.setColumnWidth(0, 26 * 256);
            summary.setColumnWidth(1, 20 * 256);
            row(summary, 0, title, "Business Performance Report");
            row(summary, 1, null, "Period", from.format(DATE) + " - " + to.format(DATE));
            row(summary, 3, header, "Metric", "Value");
            metricRow(summary, 4, "Sales", data.salesTotal, money);
            metricRow(summary, 5, "Purchases", data.purchaseTotal, money);
            metricRow(summary, 6, "Inventory Value", data.stockValue, money);
            metricRow(summary, 7, "Low-stock Items", data.lowStock.size(), null);
            tableSheet(wb, "Sales by Customer", new String[]{"Customer", "Invoices", "Sales"}, data.salesByCustomer, header, money);
            tableSheet(wb, "Purchases by Supplier", new String[]{"Supplier", "Invoices", "Purchases"}, data.purchasesBySupplier, header, money);
            tableSheet(wb, "Low Stock", new String[]{"Item", "Available", "Minimum", "Unit"}, data.lowStock, header, null);
            wb.write(out);
        }
    }

    private ReportData load(LocalDate from, LocalDate to) {
        String start = from.toString(), end = to.toString();
        try (Connection c = DatabaseManager.getConnection()) {
            return new ReportData(value(c, "SELECT COALESCE(SUM(total_amount),0) FROM sales_header WHERE invoice_date BETWEEN ? AND ?", start, end), value(c, "SELECT COALESCE(SUM(total_amount),0) FROM purchase_header WHERE invoice_date BETWEEN ? AND ?", start, end), value(c, "SELECT COALESCE(SUM(opening_stock*purchase_price),0) FROM item_master"), rows(c, "SELECT p.name, COUNT(*), SUM(s.total_amount) FROM sales_header s JOIN party_master p ON p.id=s.customer_id WHERE s.invoice_date BETWEEN ? AND ? GROUP BY p.name ORDER BY 3 DESC LIMIT 12", start, end), rows(c, "SELECT p.name, COUNT(*), SUM(h.total_amount) FROM purchase_header h JOIN party_master p ON p.id=h.supplier_id WHERE h.invoice_date BETWEEN ? AND ? GROUP BY p.name ORDER BY 3 DESC LIMIT 12", start, end), rows(c, "SELECT description, opening_stock, minimum_stock, unit FROM item_master WHERE COALESCE(opening_stock,0)<=COALESCE(minimum_stock,0) ORDER BY description"));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not prepare report data", e);
        }
    }

    private static double value(Connection c, String sql, String... params) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            bind(p, params);
            ResultSet r = p.executeQuery();
            return r.next() ? r.getDouble(1) : 0;
        }
    }

    private static List<Object[]> rows(Connection c, String sql, String... params) throws SQLException {
        List<Object[]> results = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement(sql)) {
            bind(p, params);
            ResultSet r = p.executeQuery();
            int count = r.getMetaData().getColumnCount();
            while (r.next()) {
                Object[] row = new Object[count];
                for (int i = 0; i < count; i++) row[i] = r.getObject(i + 1);
                results.add(row);
            }
        }
        return results;
    }

    private static void bind(PreparedStatement p, String[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) p.setString(i + 1, params[i]);
    }

    private static String currency(double amount) {
        return String.format("Rs. %,.2f", amount);
    }

    private static void header(PDPageContentStream c, float y, LocalDate from, LocalDate to) throws IOException {
        c.setNonStrokingColor(new Color(20, 54, 92));
        c.addRect(0, 760, 595, 82);
        c.fill();
        text(c, BOLD, 20, Color.WHITE, 42, 800, ConfigManager.get("company.name", "JavaApp ERP"));
        text(c, REGULAR, 10, Color.WHITE, 42, 781, "Business Performance Report | " + from.format(DATE) + " to " + to.format(DATE));
    }

    private static float section(PDPageContentStream c, String value, float y) throws IOException {
        text(c, BOLD, 13, new Color(20, 54, 92), 42, y, value);
        c.setStrokingColor(new Color(180, 195, 210));
        c.moveTo(42, y - 5);
        c.lineTo(553, y - 5);
        c.stroke();
        return y - 26;
    }

    private static float metric(PDPageContentStream c, String label, String value, float y) throws IOException {
        text(c, REGULAR, 10, Color.DARK_GRAY, 52, y, label);
        text(c, BOLD, 10, new Color(20, 54, 92), 430, y, value);
        return y - 18;
    }

    private static float table(PDPageContentStream c, float y, String[] heads, List<Object[]> data, float[] widths) throws IOException {
        float x = 42;
        c.setNonStrokingColor(new Color(56, 92, 137));
        c.addRect(x, y - 14, sum(widths), 18);
        c.fill();
        for (int i = 0; i < heads.length; i++) {
            text(c, BOLD, 8, Color.WHITE, x + 4, y - 9, heads[i]);
            x += widths[i];
        }
        y -= 28;
        if (data.isEmpty()) {
            text(c, REGULAR, 9, Color.GRAY, 46, y, "No records for this period.");
            return y - 18;
        }
        for (Object[] row : data) {
            x = 42;
            for (int i = 0; i < row.length; i++) {
                String v = row[i] == null ? "" : (row[i] instanceof Number n ? (i == row.length - 1 ? currency(n.doubleValue()) : String.format("%,.2f", n.doubleValue())) : row[i].toString());
                text(c, REGULAR, 8, Color.DARK_GRAY, x + 4, y, trim(v, (int) (widths[i] / 5)));
                x += widths[i];
            }
            y -= 16;
        }
        return y;
    }

    private static float sum(float[] values) {
        float total = 0;
        for (float value : values) total += value;
        return total;
    }

    private static String trim(String value, int max) {
        return value.length() > max ? value.substring(0, Math.max(0, max - 3)) + "..." : value;
    }

    private static void text(PDPageContentStream c, PDType1Font font, float size, Color color, float x, float y, String value) throws IOException {
        c.beginText();
        c.setFont(font, size);
        c.setNonStrokingColor(color);
        c.newLineAtOffset(x, y);
        c.showText(value.replaceAll("[^\\x20-\\x7E]", "?"));
        c.endText();
    }

    private static void footer(PDPageContentStream c) throws IOException {
        text(c, REGULAR, 8, Color.GRAY, 42, 28, "Generated " + LocalDate.now().format(DATE) + " | Confidential business report");
    }

    private static CellStyle style(Workbook wb, IndexedColors fill, boolean bold, IndexedColors fontColor) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(fill.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font f = wb.createFont();
        f.setBold(bold);
        f.setColor(fontColor.getIndex());
        s.setFont(f);
        return s;
    }

    private static void row(Sheet sheet, int index, CellStyle style, String... values) {
        Row r = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            Cell c = r.createCell(i);
            c.setCellValue(values[i]);
            if (style != null) c.setCellStyle(style);
        }
    }

    private static void metricRow(Sheet s, int i, String label, double value, CellStyle style) {
        Row r = s.createRow(i);
        r.createCell(0).setCellValue(label);
        Cell c = r.createCell(1);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    private static void tableSheet(Workbook wb, String name, String[] headers, List<Object[]> data, CellStyle header, CellStyle money) {
        Sheet s = wb.createSheet(name);
        s.createFreezePane(0, 1);
        row(s, 0, header, headers);
        for (int r = 0; r < data.size(); r++) {
            Row row = s.createRow(r + 1);
            for (int col = 0; col < data.get(r).length; col++) {
                Object value = data.get(r)[col];
                Cell cell = row.createCell(col);
                if (value instanceof Number n) cell.setCellValue(n.doubleValue());
                else cell.setCellValue(value == null ? "" : value.toString());
                if (money != null && col == data.get(r).length - 1) cell.setCellStyle(money);
                s.setColumnWidth(col, 24 * 256);
            }
        }
        s.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, data.size()), 0, headers.length - 1));
    }

    private record ReportData(double salesTotal, double purchaseTotal, double stockValue,
                              List<Object[]> salesByCustomer, List<Object[]> purchasesBySupplier,
                              List<Object[]> lowStock) {
    }
}
