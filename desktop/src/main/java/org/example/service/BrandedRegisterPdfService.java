package org.example.service;

import org.example.util.BusinessClock;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import org.example.config.ConfigManager;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/** Creates consistent company-branded PDF register exports. */
public final class BrandedRegisterPdfService {
    private static final DeviceRgb BLUE = new DeviceRgb(5, 79, 180);
    private static final DeviceRgb PALE_BLUE = new DeviceRgb(235, 244, 255);

    private BrandedRegisterPdfService() {}

    public static void export(Path target, String title, String[] headings,
                              List<String[]> rows, float[] widths) throws IOException {
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(target.toFile()));
             Document document = new Document(pdf, PageSize.A4.rotate())) {
            document.setMargins(28, 28, 28, 28);
            Table banner = new Table(new float[]{3, 2}).useAllAvailableWidth();
            banner.addCell(headerCell(ConfigManager.get("company.name", "DSE Engineers") +
                "\n" + ConfigManager.get("company.tagline", "ERP Solutions"), 16));
            banner.addCell(headerCell(title.toUpperCase() + "\nGenerated " + BusinessClock.today(), 15)
                .setTextAlignment(TextAlignment.RIGHT));
            document.add(banner);
            document.add(new Paragraph(ConfigManager.get("company.address", "") + "  |  " +
                ConfigManager.get("company.phone", "") + "  |  " +
                ConfigManager.get("company.email", "")).setFontSize(8).setFontColor(BLUE));

            Table table = new Table(widths).useAllAvailableWidth();
            for (String heading : headings) {
                table.addHeaderCell(new Cell().add(new Paragraph(heading).setBold())
                    .setBackgroundColor(BLUE).setFontColor(ColorConstants.WHITE)
                    .setPadding(6).setTextAlignment(TextAlignment.CENTER));
            }
            boolean shaded = false;
            for (String[] row : rows) {
                for (String value : row) {
                    Cell cell = new Cell().add(new Paragraph(value == null ? "" : value).setFontSize(8))
                        .setPadding(5);
                    if (shaded) cell.setBackgroundColor(PALE_BLUE);
                    table.addCell(cell);
                }
                shaded = !shaded;
            }
            if (rows.isEmpty()) {
                table.addCell(new Cell(1, headings.length)
                    .add(new Paragraph("No records available for the selected filters."))
                    .setTextAlignment(TextAlignment.CENTER).setPadding(16));
            }
            document.add(table);
            document.add(new Paragraph("Powered by DSE ERP 2.0").setFontSize(8)
                .setFontColor(BLUE).setTextAlignment(TextAlignment.RIGHT));
        }
    }

    private static Cell headerCell(String value, float size) {
        return new Cell().add(new Paragraph(value).setBold().setFontSize(size))
            .setBackgroundColor(BLUE).setFontColor(ColorConstants.WHITE)
            .setPadding(12).setBorder(null);
    }
}
