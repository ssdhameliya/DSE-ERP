package org.example.server.eway;

import org.example.server.audit.AuditService;
import org.example.server.persistence.JpaNativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class EWayBillService {

    private final JpaNativeRepository jdbc;
    private final AuditService auditService;

    public EWayBillService(JpaNativeRepository jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<EWayBillDtos.ExistingEWayBillDto> listExistingBills() {
        return jdbc.query("""
            SELECT s.id, s.eway_bill_no, COALESCE(s.eway_bill_date, s.invoice_date::text),
                   s.invoice_no, s.invoice_date::text, COALESCE(p.name, 'Client'),
                   COALESCE(s.vehicle_number, 'NOT_ASSIGNED'), COALESCE(s.distance_km, 50),
                   COALESCE(s.total_amount, 0)
            FROM sales_header s
            LEFT JOIN party_master p ON p.id = s.customer_id
            WHERE s.eway_bill_no IS NOT NULL AND TRIM(s.eway_bill_no) <> ''
            ORDER BY s.id DESC
            LIMIT 300
            """, (rs, idx) -> new EWayBillDtos.ExistingEWayBillDto(
                rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getInt(8), rs.getBigDecimal(9),
                rs.getString(3), "GENERATED"
        ));
    }

    @Transactional(readOnly = true)
    public List<EWayBillDtos.EligibleSaleDto> listEligibleSales() {
        return jdbc.query("""
            SELECT s.id, s.invoice_no, s.invoice_date::text, COALESCE(p.name, 'Client'),
                   COALESCE(s.gstin, p.gstin, 'URP'), COALESCE(s.delivery_address, p.address, 'Surat'),
                   COALESCE(s.total_amount, 0)
            FROM sales_header s
            LEFT JOIN party_master p ON p.id = s.customer_id
            WHERE (s.eway_bill_no IS NULL OR TRIM(s.eway_bill_no) = '')
              AND UPPER(COALESCE(s.document_status, '')) <> 'DELETED'
            ORDER BY s.id DESC
            LIMIT 300
            """, (rs, idx) -> new EWayBillDtos.EligibleSaleDto(
                rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getBigDecimal(7)
        ));
    }

    public boolean generateEWayBill(Long saleId, Map<String, Object> req, String user) {
        String ewbNo = "24" + String.format(Locale.ROOT, "%010d", Math.abs((saleId * 1000033L + System.currentTimeMillis()) % 10000000000L));
        String today = LocalDate.now().toString();
        String vehicle = req != null && req.get("vehicleNo") != null ? String.valueOf(req.get("vehicleNo")).trim() : "";
        String transName = req != null && req.get("transporterName") != null ? String.valueOf(req.get("transporterName")).trim() : "";
        String transId = req != null && req.get("transporterId") != null ? String.valueOf(req.get("transporterId")).trim() : "";
        int dist = 50;
        if (req != null && req.get("distanceKm") != null) {
            try { dist = Integer.parseInt(String.valueOf(req.get("distanceKm"))); } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        }

        int rows = jdbc.update("""
            UPDATE sales_header
            SET eway_bill_no = ?, eway_bill_date = ?, vehicle_number = ?,
                transporter = ?, transporter_gstin = ?, distance_km = ?
            WHERE id = ?
            """, ewbNo, today, vehicle, transName, transId, dist, saleId);

        if (rows > 0) {
            auditService.log("EWAY_BILL", saleId, "GENERATED",
                    "Generated E-Way Bill " + ewbNo + " (Simulated/Internal Mode - Not Registered On NIC Portal) by " + (user != null ? user : "SYSTEM"));
            return true;
        }
        return false;
    }

    public boolean updateVehicle(Long id, Map<String, Object> req, String user) {
        String vehicle = req != null && req.get("vehicleNo") != null ? String.valueOf(req.get("vehicleNo")).trim() : "";
        int rows = jdbc.update("UPDATE sales_header SET vehicle_number = ? WHERE id = ?", vehicle, id);
        if (rows > 0) {
            auditService.log("EWAY_BILL", id, "VEHICLE_UPDATED",
                    "Updated vehicle to " + vehicle + " by " + (user != null ? user : "SYSTEM"));
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<EWayBillDtos.EWayBillSummaryDto> listEligibleInvoices() {
        return jdbc.query("""
            SELECT s.id, s.invoice_no, s.invoice_date, COALESCE(p.name, 'Client'), COALESCE(s.gstin, p.gstin),
                   s.total_amount, s.transporter_gstin, s.transporter, s.vehicle_number,
                   COALESCE(s.distance_km, 0), s.eway_bill_no, s.eway_bill_date
            FROM sales_header s
            LEFT JOIN party_master p ON p.id = s.customer_id
            ORDER BY s.invoice_date DESC, s.id DESC
            LIMIT 200
            """, (rs, idx) -> {
                String ewbNo = rs.getString(11);
                String status = (ewbNo != null && !ewbNo.isBlank()) ? "GENERATED" : "PENDING";
                return new EWayBillDtos.EWayBillSummaryDto(
                        rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getBigDecimal(6),
                        rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getInt(10), ewbNo, rs.getString(12), status
                );
        });
    }

    public boolean updateTransporter(Long invoiceId, EWayBillDtos.UpdateTransporterRequest req, String user) {
        int rows = jdbc.update("""
            UPDATE sales_header
            SET transporter_gstin = ?, transporter = ?, vehicle_number = ?,
                distance_km = ?, eway_bill_no = ?, eway_bill_date = ?
            WHERE id = ?
            """, req.transporterId(), req.transporterName(), req.vehicleNo(),
                req.distanceKm() != null ? req.distanceKm() : 0,
                req.ewayBillNo(), req.ewayBillDate(), invoiceId
        );
        if (rows > 0) {
            auditService.log("EWAY_BILL", invoiceId, "UPDATED",
                    "Updated transporter / vehicle for invoice " + invoiceId + " by " + (user != null ? user : "SYSTEM"));
            return true;
        }
        return false;
    }

    @Transactional
    public Map<String, Object> generateNicJson(Long invoiceId) {
        List<Map<String, Object>> invoices = jdbc.query("""
            SELECT s.invoice_no, s.invoice_date, s.subtotal, s.gst_amount, s.total_amount,
                   COALESCE(p.name, 'Client'), COALESCE(s.gstin, p.gstin), s.delivery_address,
                   s.transporter_gstin, s.vehicle_number, COALESCE(s.distance_km, 0)
            FROM sales_header s
            LEFT JOIN party_master p ON p.id = s.customer_id
            WHERE s.id = ?
            """, (rs, idx) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("docNo", rs.getString(1));
                map.put("docDate", formatDate(rs.getString(2)));
                map.put("subtotal", rs.getBigDecimal(3));
                map.put("gstAmount", rs.getBigDecimal(4));
                map.put("totalAmount", rs.getBigDecimal(5));
                map.put("toTrdName", rs.getString(6));
                map.put("toGstin", rs.getString(7));
                map.put("toAddr1", rs.getString(8) != null ? rs.getString(8) : "Client Address");
                map.put("transporterId", rs.getString(9));
                map.put("vehicleNo", rs.getString(10));
                map.put("distance", rs.getInt(11));
                return map;
        }, invoiceId);

        if (invoices.isEmpty()) return Map.of("error", "Invoice not found");
        Map<String, Object> inv = invoices.get(0);

        // Fetch company details
        String fromGstin = "24AAACD1234A1Z5";
        String fromTradeName = "DSE ENTERPRISE";
        String fromAddr = "Main Industrial Zone";
        try {
            List<Map<String, Object>> settings = jdbc.query(
                "SELECT setting_key, setting_value FROM application_setting WHERE setting_key IN ('company.gstin', 'company.name', 'company.address')",
                (rs, idx) -> Map.of("k", rs.getString(1), "v", rs.getString(2) != null ? rs.getString(2) : "")
            );
            for (Map<String, Object> s : settings) {
                String k = (String) s.get("k");
                String v = (String) s.get("v");
                if (v != null && !v.isBlank()) {
                    if ("company.gstin".equals(k)) fromGstin = v;
                    else if ("company.name".equals(k)) fromTradeName = v;
                    else if ("company.address".equals(k)) fromAddr = v;
                }
            }
        } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }

        List<Map<String, Object>> items = jdbc.query("""
            SELECT i.item_code, COALESCE(i.description, l.item_description_snapshot, i.item_code),
                   COALESCE(i.hsn, l.hsn_snapshot, '8481'),
                   l.quantity, l.rate, l.gst_percent, l.line_total
            FROM sales_line l
            LEFT JOIN item_master i ON i.item_code = l.item_code
            WHERE l.sales_id = ?
            """, (rs, idx) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("itemNo", idx + 1);
                item.put("productName", rs.getString(2) != null ? rs.getString(2) : rs.getString(1));
                item.put("productDesc", rs.getString(1));
                item.put("hsnCode", rs.getString(3));
                item.put("quantity", rs.getBigDecimal(4));
                item.put("qtyUnit", "NOS");
                item.put("taxableAmount", rs.getBigDecimal(7));
                item.put("gstRate", rs.getBigDecimal(6));
                return item;
        }, invoiceId);

        Map<String, Object> bill = new LinkedHashMap<>();
        bill.put("userGstin", fromGstin);
        bill.put("supplyType", "O");
        bill.put("subSupplyType", "1");
        bill.put("docType", "INV");
        bill.put("docNo", inv.get("docNo"));
        bill.put("docDate", inv.get("docDate"));
        bill.put("fromGstin", fromGstin);
        bill.put("fromTrdName", fromTradeName);
        bill.put("fromAddr1", fromAddr);
        bill.put("toGstin", inv.get("toGstin"));
        bill.put("toTrdName", inv.get("toTrdName"));
        bill.put("toAddr1", inv.get("toAddr1"));
        bill.put("totalValue", inv.get("subtotal"));
        bill.put("totInvValue", inv.get("totalAmount"));
        bill.put("transMode", "1"); // Road
        bill.put("transDistance", inv.get("distance"));
        bill.put("transporterId", inv.get("transporterId"));
        bill.put("vehicleNo", inv.get("vehicleNo"));
        bill.put("itemList", items);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", "1.0.04");
        payload.put("billLists", List.of(bill));

        auditService.log("EWAY_BILL", invoiceId, "JSON_EXPORTED",
                "Exported NIC E-Way Bill JSON for invoice " + inv.get("docNo"));

        return payload;
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return "01/01/2026";
        try {
            if (dateStr.length() >= 10 && dateStr.charAt(4) == '-') {
                String[] parts = dateStr.substring(0, 10).split("-");
                return parts[2] + "/" + parts[1] + "/" + parts[0];
            }
        } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        return dateStr;
    }
}
