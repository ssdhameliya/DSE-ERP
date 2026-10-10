package org.example.server.barcode;

import org.example.server.audit.AuditService;
import org.example.server.persistence.JpaNativeRepository;
import org.example.shared.barcode.Code128Encoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class BarcodeService {

    private final JpaNativeRepository jdbc;
    private final AuditService auditService;

    public BarcodeService(JpaNativeRepository jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public BarcodeDtos.BarcodePreviewDto generateBarcodePreview(String text) {
        String safeText = (text != null && !text.isBlank()) ? text.trim() : "ITEM-001";
        String base64 = Code128Encoder.toBase64Png(safeText, 260, 80);
        return new BarcodeDtos.BarcodePreviewDto(safeText, "CODE_128", base64);
    }

    @Transactional(readOnly = true)
    public List<BarcodeDtos.DesktopBarcodeItemDto> listDesktopItems() {
        return jdbc.query("""
            SELECT id, item_code, COALESCE(description, item_code), COALESCE(category, 'General'),
                   item_code, COALESCE(selling_price, 0), COALESCE(opening_stock, 0)
            FROM item_master
            WHERE COALESCE(is_active::text, '1') IN ('1', 'true', 't')
            ORDER BY id ASC
            LIMIT 500
            """, (rs, idx) -> new BarcodeDtos.DesktopBarcodeItemDto(
                rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getBigDecimal(6),
                rs.getBigDecimal(7)
        ));
    }

    @Transactional(readOnly = true)
    public List<BarcodeDtos.LabelItemDto> searchItems(String query) {
        String filter = "%" + (query != null ? query.trim().toUpperCase() : "") + "%";
        return jdbc.query("""
            SELECT id, item_code, COALESCE(description, item_code), COALESCE(opening_stock, 0), COALESCE(selling_price, 0)
            FROM item_master
            WHERE COALESCE(is_active::text, '1') IN ('1', 'true', 't')
              AND (UPPER(item_code) LIKE ? OR UPPER(COALESCE(description, '')) LIKE ?)
            ORDER BY id ASC
            LIMIT 50
            """, (rs, idx) -> new BarcodeDtos.LabelItemDto(
                rs.getLong(1), rs.getString(2), rs.getString(3),
                "B-" + java.time.LocalDate.now().getYear(),
                java.time.LocalDate.now().plusYears(2).toString(),
                rs.getBigDecimal(5) != null ? rs.getBigDecimal(5) : BigDecimal.ZERO,
                1
        ), filter, filter);
    }

    public boolean logBarcodePrint(int totalLabels, String user) {
        auditService.log("BARCODE", 0, "PRINTED",
                "Printed " + totalLabels + " barcode labels by " + (user != null ? user : "SYSTEM"));
        return true;
    }
}
