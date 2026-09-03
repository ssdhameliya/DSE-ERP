package org.example.server.reporting;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.server.persistence.JpaNativeRepository;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static org.example.server.reporting.ReportingDtos.*;

/**
 * Headless report renderer used by the server scheduler. It consumes the exact
 * same ReportingService ReportResult as the interactive JavaFX viewer.
 */
@Service
public final class ScheduledReportExportService {
    private static final DeviceRgb NAVY = new DeviceRgb(15,45,77);
    private static final DeviceRgb BLUE = new DeviceRgb(32,105,210);
    private static final DeviceRgb PALE = new DeviceRgb(239,245,252);
    private static final NumberFormat INR = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    private final JpaNativeRepository db;

    public ScheduledReportExportService(JpaNativeRepository db) {
        this.db = db;
    }

    public void pdf(Path target, ReportResult result, Set<String> visibleKeys) throws IOException {
        Objects.requireNonNull(result, "result");
        Files.createDirectories(target.toAbsolutePath().getParent());
        List<Integer> positions = positions(result, visibleKeys);
        PageSize pageSize = positions.size() <= 6 ? PageSize.A4 : PageSize.A4.rotate();
        Path tmp = Files.createTempFile(target.toAbsolutePath().getParent(), "dse-scheduled-report-", ".pdf");
        try {
            try (PdfDocument pdf = new PdfDocument(new PdfWriter(tmp.toFile()));
                 Document doc = new Document(pdf, pageSize)) {
                doc.setMargins(30, 28, 34, 28);
                addHeader(doc, result);
                addFilters(doc, result);
                addSummary(doc, result);
                addTable(doc, result, positions);
            }
            stampFooter(tmp, target, result);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public void excel(Path target, ReportResult result, Set<String> visibleKeys) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        List<Integer> positions = positions(result, visibleKeys);
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle title = wb.createCellStyle();
            Font titleFont = wb.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short)16); title.setFont(titleFont);
            CellStyle heading = wb.createCellStyle();
            Font hf = wb.createFont(); hf.setBold(true); heading.setFont(hf);
            heading.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()); heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle money = wb.createCellStyle(); money.setDataFormat(wb.createDataFormat().getFormat("₹ #,##0.00"));
            CellStyle number = wb.createCellStyle(); number.setDataFormat(wb.createDataFormat().getFormat("#,##0.####"));

            Sheet summary = wb.createSheet("Summary"); int row = 0;
            Row r = summary.createRow(row++); org.apache.poi.ss.usermodel.Cell tc = r.createCell(0); tc.setCellValue(result.title()); tc.setCellStyle(title);
            summary.createRow(row++).createCell(0).setCellValue("Period: " + result.periodFrom() + " to " + result.periodTo());
            summary.createRow(row++).createCell(0).setCellValue("Generated: " + result.generatedAt() + " by " + result.generatedBy());
            row++;
            Row mh = summary.createRow(row++); mh.createCell(0).setCellValue("Metric"); mh.createCell(1).setCellValue("Value"); mh.getCell(0).setCellStyle(heading); mh.getCell(1).setCellStyle(heading);
            for (ReportMetric metric : safe(result.metrics())) {
                Row mr = summary.createRow(row++); mr.createCell(0).setCellValue(metric.label());
                org.apache.poi.ss.usermodel.Cell value = mr.createCell(1);
                if ("COUNT".equals(metric.format())) value.setCellValue((long)metric.value());
                else { value.setCellValue(metric.value()); value.setCellStyle("MONEY".equals(metric.format()) ? money : number); }
            }
            row++;
            Row fh = summary.createRow(row++); fh.createCell(0).setCellValue("Applied Filters"); fh.getCell(0).setCellStyle(heading);
            if (result.appliedFilters() != null) for (var entry : result.appliedFilters().entrySet()) {
                Row fr = summary.createRow(row++); fr.createCell(0).setCellValue(entry.getKey()); fr.createCell(1).setCellValue(entry.getValue());
            }
            summary.autoSizeColumn(0); summary.autoSizeColumn(1);

            Sheet details = wb.createSheet("Details");
            Row header = details.createRow(0);
            for (int j = 0; j < positions.size(); j++) {
                org.apache.poi.ss.usermodel.Cell c = header.createCell(j); c.setCellValue(result.columns().get(positions.get(j)).label()); c.setCellStyle(heading);
            }
            int dr = 1;
            for (ReportRow rr : safe(result.rows())) {
                Row er = details.createRow(dr++);
                for (int j = 0; j < positions.size(); j++) {
                    int p = positions.get(j); ReportColumn col = result.columns().get(p);
                    String raw = rr.values().size() > p ? rr.values().get(p) : "";
                    org.apache.poi.ss.usermodel.Cell c = er.createCell(j);
                    if (col.numeric()) {
                        try { c.setCellValue(Double.parseDouble(raw)); c.setCellStyle("MONEY".equals(col.type()) ? money : number); }
                        catch (Exception ex) { c.setCellValue(raw); }
                    } else c.setCellValue(raw);
                }
            }
            details.createFreezePane(0, 1);
            if (!positions.isEmpty()) details.setAutoFilter(new CellRangeAddress(0, Math.max(0, dr - 1), 0, positions.size() - 1));
            for (int j = 0; j < positions.size(); j++) details.setColumnWidth(j, Math.min(60 * 256, Math.max(12 * 256, (int)result.columns().get(positions.get(j)).preferredWidth() * 42)));
            try (OutputStream out = Files.newOutputStream(target)) { wb.write(out); }
        }
    }

    public void csv(Path target, ReportResult result, Set<String> visibleKeys) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        List<Integer> positions = positions(result, visibleKeys);
        try (BufferedWriter out = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            for (int j = 0; j < positions.size(); j++) { if (j > 0) out.write(','); out.write(csv(result.columns().get(positions.get(j)).label())); }
            out.newLine();
            for (ReportRow row : safe(result.rows())) {
                for (int j = 0; j < positions.size(); j++) { if (j > 0) out.write(','); int p = positions.get(j); out.write(csv(row.values().size() > p ? row.values().get(p) : "")); }
                out.newLine();
            }
        }
    }

    private void addHeader(Document doc, ReportResult result) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
        Cell company = new Cell().setBorder(null).setPadding(0);
        company.add(new Paragraph(setting("company.name", "DSE ERP")).setBold().setFontSize(14).setFontColor(NAVY));
        String address = setting("company.address", "").trim(); if (!address.isBlank()) company.add(new Paragraph(address).setFontSize(7));
        String gst = setting("company.gstin", "").trim(); if (!gst.isBlank()) company.add(new Paragraph("GSTIN: " + gst).setFontSize(7).setBold());
        String contact = join(setting("company.phone", ""), setting("company.email", ""), setting("company.website", ""));
        if (!contact.isBlank()) company.add(new Paragraph(contact).setFontSize(7));
        header.addCell(company);

        Cell report = new Cell().setBorder(null).setTextAlignment(TextAlignment.RIGHT).setPadding(0);
        report.add(new Paragraph(result.title().toUpperCase(Locale.ROOT)).setBold().setFontSize(15).setFontColor(BLUE));
        report.add(new Paragraph(result.periodFrom() + " to " + result.periodTo()).setFontSize(8));
        report.add(new Paragraph("Generated " + result.generatedAt()).setFontSize(7));
        report.add(new Paragraph("By " + result.generatedBy()).setFontSize(7));
        header.addCell(report); doc.add(header);
        doc.add(new Paragraph(result.description()).setFontSize(8).setFontColor(new DeviceRgb(70,85,100)).setMarginTop(5).setMarginBottom(6));
    }

    private static void addFilters(Document doc, ReportResult result) {
        if (result.appliedFilters() == null || result.appliedFilters().isEmpty()) return;
        StringBuilder text = new StringBuilder("Filters: "); boolean first = true;
        for (var entry : result.appliedFilters().entrySet()) { if (!first) text.append("  |  "); first = false; text.append(entry.getKey()).append(": ").append(entry.getValue()); }
        doc.add(new Paragraph(text.toString()).setFontSize(7.5f).setBackgroundColor(PALE).setPadding(5).setMarginBottom(7));
    }

    private static void addSummary(Document doc, ReportResult result) {
        if (result.metrics() == null || result.metrics().isEmpty()) return;
        int count = Math.min(6, result.metrics().size());
        Table table = new Table(UnitValue.createPercentArray(count)).useAllAvailableWidth().setMarginBottom(8);
        for (int i = 0; i < count; i++) {
            ReportMetric metric = result.metrics().get(i);
            Cell cell = new Cell().setPadding(5).setBackgroundColor(PALE).setBorder(new SolidBorder(new DeviceRgb(205,218,234), 0.75f));
            cell.add(new Paragraph(metric.label()).setFontSize(6.5f).setFontColor(new DeviceRgb(85,100,118)));
            cell.add(new Paragraph(formatMetric(metric)).setBold().setFontSize(9).setFontColor(NAVY)); table.addCell(cell);
        }
        doc.add(table);
    }

    private static void addTable(Document doc, ReportResult result, List<Integer> positions) {
        if (positions.isEmpty()) { doc.add(new Paragraph("No visible columns selected.")); return; }
        float[] widths = new float[positions.size()]; for (int i = 0; i < positions.size(); i++) widths[i] = (float)Math.max(60, result.columns().get(positions.get(i)).preferredWidth());
        Table table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth().setFontSize(6.8f);
        for (int p : positions) {
            ReportColumn col = result.columns().get(p);
            table.addHeaderCell(new Cell().add(new Paragraph(col.label()).setBold()).setBackgroundColor(NAVY).setFontColor(ColorConstants.WHITE).setPadding(4).setTextAlignment(col.numeric() ? TextAlignment.RIGHT : TextAlignment.LEFT));
        }
        String group = null;
        for (ReportRow row : safe(result.rows())) {
            if (row.groupKey() != null && !row.groupKey().isBlank() && !Objects.equals(group, row.groupKey())) {
                group = row.groupKey(); table.addCell(new Cell(1, positions.size()).add(new Paragraph(group).setBold()).setBackgroundColor(PALE).setFontColor(NAVY).setPadding(4));
            }
            for (int p : positions) {
                ReportColumn col = result.columns().get(p); String raw = row.values().size() > p ? row.values().get(p) : "";
                table.addCell(new Cell().add(new Paragraph(formatValue(col, raw))).setPadding(3.5f).setTextAlignment(col.numeric() ? TextAlignment.RIGHT : TextAlignment.LEFT));
            }
        }
        if (safe(result.rows()).isEmpty()) table.addCell(new Cell(1, positions.size()).add(new Paragraph("No transactions found for the selected criteria.")).setTextAlignment(TextAlignment.CENTER).setPadding(15));
        doc.add(table);
    }

    private static void stampFooter(Path source, Path target, ReportResult result) throws IOException {
        try (PdfDocument pdf = new PdfDocument(new PdfReader(source.toFile()), new PdfWriter(target.toFile()))) {
            int pages = pdf.getNumberOfPages();
            for (int i = 1; i <= pages; i++) {
                var page = pdf.getPage(i);
                PdfCanvas canvasData = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdf);
                try (Canvas canvas = new Canvas(canvasData, page.getPageSize())) {
                    String left = "DSE ERP | " + result.title() + " | Generated " + result.generatedAt();
                    canvas.showTextAligned(new Paragraph(left).setFontSize(6.5f).setFontColor(new DeviceRgb(90,100,112)),
                            page.getPageSize().getLeft()+28, page.getPageSize().getBottom()+15, TextAlignment.LEFT);
                    canvas.showTextAligned(new Paragraph("Page " + i + " of " + pages).setFontSize(6.5f).setFontColor(new DeviceRgb(90,100,112)),
                            page.getPageSize().getRight()-28, page.getPageSize().getBottom()+15, TextAlignment.RIGHT);
                }
            }
        }
    }

    private String setting(String key, String fallback) {
        try {
            String value = db.queryForObject("SELECT setting_value FROM application_setting WHERE setting_key=?", String.class, key);
            return value == null ? fallback : value;
        } catch (Exception ignored) { return fallback == null ? "" : fallback; }
    }

    private static List<Integer> positions(ReportResult result, Set<String> visible) {
        List<Integer> positions = new ArrayList<>(); Set<String> keys = visible == null ? Set.of() : new LinkedHashSet<>(visible);
        for (int i = 0; i < result.columns().size(); i++) {
            ReportColumn c = result.columns().get(i); if (keys.isEmpty() ? c.defaultVisible() : keys.contains(c.key())) positions.add(i);
        }
        return positions;
    }

    private static String formatMetric(ReportMetric metric) {
        return switch (metric.format() == null ? "" : metric.format()) {
            case "MONEY" -> INR.format(metric.value());
            case "PERCENT" -> String.format("%,.2f%%", metric.value());
            case "COUNT" -> String.format("%,.0f", metric.value());
            default -> String.format("%,.4f", metric.value()).replaceAll("\\.?0+$", "");
        };
    }

    private static String formatValue(ReportColumn column, String raw) {
        if (raw == null) return ""; if (!column.numeric()) return raw;
        try {
            double value = Double.parseDouble(raw);
            if ("MONEY".equals(column.type())) return INR.format(value);
            if ("PERCENT".equals(column.type())) return String.format("%,.2f%%", value);
            return String.format("%,.4f", value).replaceAll("\\.?0+$", "");
        } catch (Exception ignored) { return raw; }
    }

    private static String csv(String value) {
        String text = spreadsheetSafe(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) return "\"" + text.replace("\"", "\"\"") + "\"";
        return text;
    }

    private static String spreadsheetSafe(String value) {
        String text = value == null ? "" : value; String trimmed = text.stripLeading(); if (trimmed.isEmpty()) return text;
        char c = trimmed.charAt(0); boolean numericNegative = c == '-' && trimmed.matches("-\\d+(?:\\.\\d+)?");
        return c == '=' || c == '+' || c == '@' || (c == '-' && !numericNegative) ? "'" + text : text;
    }

    private static String join(String... values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) if (value != null && !value.isBlank()) { if (!out.isEmpty()) out.append("  |  "); out.append(value.trim()); }
        return out.toString();
    }

    private static <T> List<T> safe(List<T> value) { return value == null ? List.of() : value; }
}
