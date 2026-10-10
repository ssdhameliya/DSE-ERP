package org.example.server.aging;

import org.example.server.audit.AuditService;
import org.example.server.persistence.JpaNativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AgingAnalysisService {

    private final JpaNativeRepository jdbc;
    private final AuditService auditService;

    public AgingAnalysisService(JpaNativeRepository jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public AgingAnalysisDtos.AgingSummaryDto getReceivablesAging(LocalDate asOfDate) {
        LocalDate asOf = asOfDate != null ? asOfDate : LocalDate.now();

        List<Map<String, Object>> rows = jdbc.query("""
            SELECT s.customer_id, COALESCE(p.name, 'Client'), p.gstin, p.phone,
                   s.invoice_date, (s.total_amount - COALESCE(s.paid_amount, 0)) as balance
            FROM sales_header s
            LEFT JOIN party_master p ON p.id = s.customer_id
            WHERE (s.total_amount - COALESCE(s.paid_amount, 0)) > 0
              AND UPPER(COALESCE(s.document_status, '')) <> 'DELETED'
            """, (rs, idx) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("partyId", rs.getLong(1));
                map.put("partyName", rs.getString(2));
                map.put("gstin", rs.getString(3));
                map.put("phone", rs.getString(4));
                map.put("invoiceDate", rs.getString(5));
                map.put("balance", rs.getBigDecimal(6));
                return map;
            });

        return buildAgingSummary(asOf, "RECEIVABLES", rows);
    }

    public AgingAnalysisDtos.AgingSummaryDto getPayablesAging(LocalDate asOfDate) {
        LocalDate asOf = asOfDate != null ? asOfDate : LocalDate.now();

        List<Map<String, Object>> rows = jdbc.query("""
            SELECT p.supplier_id, COALESCE(party.name, 'Supplier'), party.gstin, party.phone,
                   p.invoice_date, (p.total_amount - COALESCE(p.paid_amount, 0)) as balance
            FROM purchase_header p
            LEFT JOIN party_master party ON party.id = p.supplier_id
            WHERE (p.total_amount - COALESCE(p.paid_amount, 0)) > 0
              AND UPPER(COALESCE(p.document_status, '')) <> 'DELETED'
            """, (rs, idx) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("partyId", rs.getLong(1));
                map.put("partyName", rs.getString(2));
                map.put("gstin", rs.getString(3));
                map.put("phone", rs.getString(4));
                map.put("invoiceDate", rs.getString(5));
                map.put("balance", rs.getBigDecimal(6));
                return map;
            });

        return buildAgingSummary(asOf, "PAYABLES", rows);
    }

    public AgingAnalysisDtos.DesktopAgingReport getDesktopAgingReport(String type, LocalDate asOfDate) {
        LocalDate asOf = asOfDate != null ? asOfDate : LocalDate.now();
        boolean isDebtor = !"CREDITOR".equalsIgnoreCase(type) && !"PAYABLES".equalsIgnoreCase(type);
        AgingAnalysisDtos.AgingSummaryDto summary = isDebtor ? getReceivablesAging(asOf) : getPayablesAging(asOf);

        BigDecimal b0_30 = BigDecimal.ZERO;
        BigDecimal b31_60 = BigDecimal.ZERO;
        BigDecimal b61_90 = BigDecimal.ZERO;
        BigDecimal b90Plus = BigDecimal.ZERO;
        List<AgingAnalysisDtos.DesktopAgingRow> rows = new ArrayList<>();

        if (summary.parties() != null) {
            for (AgingAnalysisDtos.PartyAgingDto p : summary.parties()) {
                b0_30 = b0_30.add(p.bucket0To30());
                b31_60 = b31_60.add(p.bucket31To60());
                b61_90 = b61_90.add(p.bucket61To90());
                b90Plus = b90Plus.add(p.bucket90Plus());
                rows.add(new AgingAnalysisDtos.DesktopAgingRow(
                        p.partyId(), p.partyName(), p.phone(), p.totalDue(),
                        p.bucket0To30(), p.bucket31To60(), p.bucket61To90(), p.bucket90Plus(),
                        p.riskLevel()
                ));
            }
        }

        return new AgingAnalysisDtos.DesktopAgingReport(
                asOf.toString(),
                isDebtor ? "DEBTOR" : "CREDITOR",
                rows.size(),
                summary.totalOutstanding(),
                b0_30, b31_60, b61_90, b90Plus,
                rows
        );
    }

    @Transactional
    public boolean sendPaymentReminder(Long partyId, String user) {
        try {
            String name = jdbc.queryForObject("SELECT name FROM party_master WHERE id = ?", String.class, partyId);
            auditService.log("AGING_ANALYSIS", partyId, "REMINDER_SENT",
                    "Sent payment reminder to " + name + " by " + (user != null ? user : "SYSTEM"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private AgingAnalysisDtos.AgingSummaryDto buildAgingSummary(LocalDate asOf, String type, List<Map<String, Object>> rows) {
        Map<Long, PartyBuckets> bucketsByParty = new LinkedHashMap<>();

        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;

        for (Map<String, Object> row : rows) {
            Long partyId = (Long) row.get("partyId");
            String partyName = (String) row.get("partyName");
            String gstin = (String) row.get("gstin");
            String phone = (String) row.get("phone");
            String dateStr = (String) row.get("invoiceDate");
            BigDecimal balance = (BigDecimal) row.get("balance");

            if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) continue;

            LocalDate invDate = parseDate(dateStr);
            long days = invDate != null ? ChronoUnit.DAYS.between(invDate, asOf) : 0;
            if (days < 0) days = 0;

            PartyBuckets b = bucketsByParty.computeIfAbsent(partyId, k -> new PartyBuckets(partyId, partyName, type, gstin, phone));
            b.addInvoice(days, balance);

            totalOutstanding = totalOutstanding.add(balance);
            if (days > 30) {
                totalOverdue = totalOverdue.add(balance);
            }
        }

        List<AgingAnalysisDtos.PartyAgingDto> partyList = new ArrayList<>();
        for (PartyBuckets pb : bucketsByParty.values()) {
            partyList.add(pb.toDto());
        }

        partyList.sort((a, b) -> b.totalDue().compareTo(a.totalDue()));

        return new AgingAnalysisDtos.AgingSummaryDto(
                asOf, type, totalOutstanding, totalOverdue, partyList.size(), partyList
        );
    }

    private LocalDate parseDate(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            if (str.length() >= 10) {
                return LocalDate.parse(str.substring(0, 10));
            }
        } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        try {
            return LocalDate.parse(str, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        return null;
    }

    private static class PartyBuckets {
        final Long partyId;
        final String partyName;
        final String partyType;
        final String gstin;
        final String phone;
        BigDecimal b0_30 = BigDecimal.ZERO;
        BigDecimal b31_60 = BigDecimal.ZERO;
        BigDecimal b61_90 = BigDecimal.ZERO;
        BigDecimal b90Plus = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        PartyBuckets(Long partyId, String partyName, String partyType, String gstin, String phone) {
            this.partyId = partyId;
            this.partyName = partyName;
            this.partyType = partyType;
            this.gstin = gstin;
            this.phone = phone;
        }

        void addInvoice(long days, BigDecimal amount) {
            total = total.add(amount);
            if (days <= 30) {
                b0_30 = b0_30.add(amount);
            } else if (days <= 60) {
                b31_60 = b31_60.add(amount);
            } else if (days <= 90) {
                b61_90 = b61_90.add(amount);
            } else {
                b90Plus = b90Plus.add(amount);
            }
        }

        AgingAnalysisDtos.PartyAgingDto toDto() {
            String risk = "LOW";
            if (b90Plus.compareTo(BigDecimal.ZERO) > 0) {
                risk = "HIGH";
            } else if (b61_90.compareTo(BigDecimal.ZERO) > 0) {
                risk = "MEDIUM";
            }
            return new AgingAnalysisDtos.PartyAgingDto(
                    partyId, partyName, partyType, gstin, phone,
                    b0_30, b31_60, b61_90, b90Plus, total, risk
            );
        }
    }
}
