package org.example.server.gst;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.server.persistence.JpaNativeRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class Gstr1Service {

    private final JpaNativeRepository jdbc;
    private final ObjectMapper objectMapper;

    @Autowired
    public Gstr1Service(JpaNativeRepository jdbc, ObjectProvider<ObjectMapper> mapperProvider) {
        this.jdbc = jdbc;
        this.objectMapper = mapperProvider != null && mapperProvider.getIfAvailable() != null
                ? mapperProvider.getIfAvailable()
                : new ObjectMapper();
    }

    public Gstr1Service(JpaNativeRepository jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public static record PeriodWindow(int month, int year, LocalDate startDate, LocalDate endDate, String fp) {}

    public PeriodWindow resolvePeriod(String period) {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int year = now.getYear();

        if (period != null && !period.isBlank() && !"All Periods".equalsIgnoreCase(period)) {
            String clean = period.replaceAll("[^0-9]", "");
            if (clean.length() == 6) {
                int firstTwo = Integer.parseInt(clean.substring(0, 2));
                if (firstTwo >= 1 && firstTwo <= 12) {
                    month = firstTwo;
                    year = Integer.parseInt(clean.substring(2, 6));
                } else {
                    year = Integer.parseInt(clean.substring(0, 4));
                    month = Integer.parseInt(clean.substring(4, 6));
                }
            } else if (period.contains("-")) {
                String[] p = period.split("-");
                if (p[0].trim().length() == 4) {
                    year = Integer.parseInt(p[0].trim());
                    month = Integer.parseInt(p[1].trim());
                } else {
                    month = Integer.parseInt(p[0].trim());
                    year = Integer.parseInt(p[1].trim());
                }
            }
        }

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        String fp = String.format("%02d%04d", month, year);
        return new PeriodWindow(month, year, start, end, fp);
    }

    private String getCompanyGstin() {
        try {
            String gstin = jdbc.queryForObject(
                    "SELECT setting_value FROM application_setting WHERE setting_key = 'company.gstin'",
                    String.class
            );
            if (gstin != null && !gstin.isBlank()) return gstin.trim();
        } catch (Exception ignored) {}
        return "24ABCDE1234F1Z5";
    }

    public Gstr1Dtos.Gstr1FullReturnDto generateGstr1(String returnPeriod) {
        PeriodWindow win = resolvePeriod(returnPeriod);
        String myGstin = getCompanyGstin();
        String homeState = myGstin.length() >= 2 ? myGstin.substring(0, 2) : "24";

        String startStr = win.startDate().toString();
        String endStr = win.endDate().toString();

        // 1. Fetch Sales and Lines for period
        List<Map<String, Object>> salesRows = jdbc.queryForList("""
            SELECT s.id, s.invoice_no, s.invoice_date, s.total_amount, s.gst_amount, s.subtotal,
                   s.gst_type, s.document_status,
                   COALESCE(NULLIF(s.customer_gstin_snapshot, ''), NULLIF(s.billing_gstin, ''), NULLIF(s.gstin, ''), p.gstin, '') as cust_gstin,
                   COALESCE(NULLIF(s.customer_name_snapshot, ''), p.name, 'Valued Customer') as cust_name,
                   COALESCE(NULLIF(s.delivery_address, ''), NULLIF(s.billing_address, ''), '') as addr
            FROM sales_header s
            LEFT JOIN party_master p ON p.id = s.customer_id
            WHERE UPPER(COALESCE(s.document_status, '')) NOT IN ('CANCELLED', 'DELETED')
              AND s.invoice_date BETWEEN ? AND ?
            ORDER BY s.invoice_date ASC, s.id ASC
            """, startStr, endStr);

        // Fetch sales lines
        List<Map<String, Object>> lineRows = jdbc.queryForList("""
            SELECT l.sales_id, l.item_code, l.quantity, l.rate, l.gst_percent, l.discount_amount,
                   l.line_total, l.item_description_snapshot, l.hsn_snapshot, l.unit_snapshot,
                   s.invoice_date, s.gst_type,
                   COALESCE(NULLIF(s.customer_gstin_snapshot, ''), NULLIF(s.billing_gstin, ''), NULLIF(s.gstin, ''), p.gstin, '') as cust_gstin
            FROM sales_line l
            JOIN sales_header s ON s.id = l.sales_id
            LEFT JOIN party_master p ON p.id = s.customer_id
            WHERE UPPER(COALESCE(s.document_status, '')) NOT IN ('CANCELLED', 'DELETED')
              AND s.invoice_date BETWEEN ? AND ?
            ORDER BY l.id ASC
            """, startStr, endStr);

        Map<Integer, List<Map<String, Object>>> linesBySalesId = new HashMap<>();
        for (Map<String, Object> lr : lineRows) {
            Integer sId = ((Number) lr.get("sales_id")).intValue();
            linesBySalesId.computeIfAbsent(sId, k -> new ArrayList<>()).add(lr);
        }

        BigDecimal grossTurnover = BigDecimal.ZERO;
        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalIgst = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;

        // Group into B2B and B2CS
        Map<String, List<Gstr1Dtos.Gstr1B2bInvoiceDto>> b2bMap = new LinkedHashMap<>();
        Map<String, String> partyNames = new HashMap<>();
        Map<String, Gstr1Dtos.Gstr1B2csDto> b2csGroup = new LinkedHashMap<>();

        for (Map<String, Object> s : salesRows) {
            Integer sId = ((Number) s.get("id")).intValue();
            String invNo = String.valueOf(s.get("invoice_no"));
            String invDateRaw = String.valueOf(s.get("invoice_date"));
            String idt = formatDateGst(invDateRaw);
            BigDecimal totalAmt = toBigDecimal(s.get("total_amount"));
            grossTurnover = grossTurnover.add(totalAmt);

            String cGstin = String.valueOf(s.get("cust_gstin")).trim().toUpperCase(Locale.ROOT);
            String custName = String.valueOf(s.get("cust_name")).trim();
            String gstType = s.get("gst_type") != null ? String.valueOf(s.get("gst_type")).toUpperCase() : "";

            List<Map<String, Object>> lines = linesBySalesId.getOrDefault(sId, List.of());
            String pos = cGstin.length() >= 2 ? cGstin.substring(0, 2) : homeState;
            boolean isInter = gstType.contains("INTER") || "IGST".equals(gstType) || (!pos.equals(homeState) && cGstin.length() == 15);

            // Item detail list for this invoice
            List<Gstr1Dtos.Gstr1ItemDetailDto> itmList = new ArrayList<>();
            if (!lines.isEmpty()) {
                int itemIdx = 1;
                for (Map<String, Object> l : lines) {
                    double qty = l.get("quantity") != null ? ((Number) l.get("quantity")).doubleValue() : 1.0;
                    double rate = l.get("rate") != null ? ((Number) l.get("rate")).doubleValue() : 0.0;
                    double disc = l.get("discount_amount") != null ? ((Number) l.get("discount_amount")).doubleValue() : 0.0;
                    double gstPct = l.get("gst_percent") != null ? ((Number) l.get("gst_percent")).doubleValue() : 18.0;
                    double lTotal = l.get("line_total") != null ? ((Number) l.get("line_total")).doubleValue() : 0.0;

                    BigDecimal txVal = BigDecimal.valueOf((qty * rate) - disc).setScale(2, RoundingMode.HALF_UP);
                    if (txVal.compareTo(BigDecimal.ZERO) <= 0) {
                        txVal = BigDecimal.valueOf(lTotal / (1.0 + (gstPct / 100.0))).setScale(2, RoundingMode.HALF_UP);
                    }
                    BigDecimal tax = BigDecimal.valueOf(lTotal).subtract(txVal).setScale(2, RoundingMode.HALF_UP);
                    if (tax.compareTo(BigDecimal.ZERO) < 0) tax = BigDecimal.ZERO;

                    BigDecimal rt = BigDecimal.valueOf(gstPct).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal iamt = isInter ? tax : BigDecimal.ZERO;
                    BigDecimal camt = !isInter ? tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    BigDecimal samt = !isInter ? tax.subtract(camt) : BigDecimal.ZERO;

                    totalTaxable = totalTaxable.add(txVal);
                    totalIgst = totalIgst.add(iamt);
                    totalCgst = totalCgst.add(camt);
                    totalSgst = totalSgst.add(samt);

                    itmList.add(new Gstr1Dtos.Gstr1ItemDetailDto(itemIdx++, txVal, rt, iamt, camt, samt, BigDecimal.ZERO));
                }
            } else {
                BigDecimal gstAmt = toBigDecimal(s.get("gst_amount"));
                BigDecimal sub = toBigDecimal(s.get("subtotal"));
                BigDecimal txVal = sub.compareTo(BigDecimal.ZERO) > 0 ? sub : totalAmt.subtract(gstAmt);
                BigDecimal rt = txVal.compareTo(BigDecimal.ZERO) > 0
                        ? gstAmt.multiply(BigDecimal.valueOf(100)).divide(txVal, 2, RoundingMode.HALF_UP)
                        : BigDecimal.valueOf(18.0);
                BigDecimal iamt = isInter ? gstAmt : BigDecimal.ZERO;
                BigDecimal camt = !isInter ? gstAmt.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal samt = !isInter ? gstAmt.subtract(camt) : BigDecimal.ZERO;

                totalTaxable = totalTaxable.add(txVal);
                totalIgst = totalIgst.add(iamt);
                totalCgst = totalCgst.add(camt);
                totalSgst = totalSgst.add(samt);

                itmList.add(new Gstr1Dtos.Gstr1ItemDetailDto(1, txVal, rt, iamt, camt, samt, BigDecimal.ZERO));
            }

            if (cGstin.length() == 15) {
                // Table 4: B2B
                partyNames.put(cGstin, custName);
                Gstr1Dtos.Gstr1B2bInvoiceDto invDto = new Gstr1Dtos.Gstr1B2bInvoiceDto(
                        invNo, idt, totalAmt, pos, "N", "R", itmList
                );
                b2bMap.computeIfAbsent(cGstin, k -> new ArrayList<>()).add(invDto);
            } else {
                // Table 7: B2CS
                for (Gstr1Dtos.Gstr1ItemDetailDto it : itmList) {
                    String b2csKey = pos + "_" + it.rt() + "_" + (isInter ? "INTER" : "INTRA");
                    Gstr1Dtos.Gstr1B2csDto existing = b2csGroup.get(b2csKey);
                    if (existing == null) {
                        b2csGroup.put(b2csKey, new Gstr1Dtos.Gstr1B2csDto(
                                isInter ? "INTER" : "INTRA", pos, it.txval(), it.rt(), it.iamt(), it.camt(), it.samt(), BigDecimal.ZERO
                        ));
                    } else {
                        b2csGroup.put(b2csKey, new Gstr1Dtos.Gstr1B2csDto(
                                existing.sply_ty(), existing.pos(),
                                existing.txval().add(it.txval()), existing.rt(),
                                existing.iamt().add(it.iamt()), existing.camt().add(it.camt()), existing.samt().add(it.samt()), BigDecimal.ZERO
                        ));
                    }
                }
            }
        }

        List<Gstr1Dtos.Gstr1B2bPartyDto> b2bList = new ArrayList<>();
        for (var entry : b2bMap.entrySet()) {
            b2bList.add(new Gstr1Dtos.Gstr1B2bPartyDto(entry.getKey(), partyNames.getOrDefault(entry.getKey(), "Valued Customer"), entry.getValue()));
        }

        List<Gstr1Dtos.Gstr1B2csDto> b2csList = new ArrayList<>(b2csGroup.values());

        // 2. Table 9B: CDNR (Registered Credit & Debit Notes)
        List<Gstr1Dtos.Gstr1CdnrPartyDto> cdnrList = new ArrayList<>();
        try {
            List<Map<String, Object>> cnRows = jdbc.queryForList("""
                SELECT id, credit_note_no, note_date, original_invoice_no, party_name, party_gstin,
                       taxable_amount, cgst_amount, sgst_amount, igst_amount, total_amount
                FROM credit_note_header
                WHERE note_date BETWEEN ? AND ? AND LENGTH(TRIM(COALESCE(party_gstin, ''))) = 15
                ORDER BY id ASC
                """, win.startDate(), win.endDate());

            Map<String, List<Gstr1Dtos.Gstr1CdnrNoteDto>> cnByParty = new LinkedHashMap<>();
            Map<String, String> cnPartyNames = new HashMap<>();

            for (Map<String, Object> cn : cnRows) {
                String ctin = String.valueOf(cn.get("party_gstin")).trim();
                cnPartyNames.put(ctin, String.valueOf(cn.get("party_name")));
                String nNo = String.valueOf(cn.get("credit_note_no"));
                String nDt = formatDateGst(String.valueOf(cn.get("note_date")));
                String origInv = cn.get("original_invoice_no") != null ? String.valueOf(cn.get("original_invoice_no")) : "INV-ORIG";
                BigDecimal totVal = toBigDecimal(cn.get("total_amount"));
                BigDecimal txVal = toBigDecimal(cn.get("taxable_amount"));
                BigDecimal cgst = toBigDecimal(cn.get("cgst_amount"));
                BigDecimal sgst = toBigDecimal(cn.get("sgst_amount"));
                BigDecimal igst = toBigDecimal(cn.get("igst_amount"));
                BigDecimal totalTax = cgst.add(sgst).add(igst);
                BigDecimal rt = txVal.compareTo(BigDecimal.ZERO) > 0 ? totalTax.multiply(BigDecimal.valueOf(100)).divide(txVal, 2, RoundingMode.HALF_UP) : BigDecimal.valueOf(18.0);

                List<Gstr1Dtos.Gstr1ItemDetailDto> itm = List.of(new Gstr1Dtos.Gstr1ItemDetailDto(1, txVal, rt, igst, cgst, sgst, BigDecimal.ZERO));
                String pos = ctin.length() >= 2 ? ctin.substring(0, 2) : homeState;
                cnByParty.computeIfAbsent(ctin, k -> new ArrayList<>()).add(new Gstr1Dtos.Gstr1CdnrNoteDto(
                        nNo, nDt, "C", origInv, nDt, totVal, pos, "N", itm
                ));
            }

            for (var entry : cnByParty.entrySet()) {
                cdnrList.add(new Gstr1Dtos.Gstr1CdnrPartyDto(entry.getKey(), cnPartyNames.getOrDefault(entry.getKey(), ""), entry.getValue()));
            }
        } catch (Exception ignored) {}

        // 3. Table 12: HSN Summary
        List<Gstr1Dtos.Gstr1HsnItemDto> hsnList = new ArrayList<>();
        Map<String, Gstr1Dtos.Gstr1HsnItemDto> hsnGroup = new LinkedHashMap<>();
        int hsnSeq = 1;

        for (Map<String, Object> lr : lineRows) {
            String hsn = lr.get("hsn_snapshot") != null && !String.valueOf(lr.get("hsn_snapshot")).isBlank()
                    ? String.valueOf(lr.get("hsn_snapshot")).trim() : "9983";
            String desc = lr.get("item_description_snapshot") != null ? String.valueOf(lr.get("item_description_snapshot")).trim() : "Item Supply";
            String uqc = lr.get("unit_snapshot") != null && !String.valueOf(lr.get("unit_snapshot")).isBlank()
                    ? String.valueOf(lr.get("unit_snapshot")).trim().toUpperCase() : "NOS";
            double gstPct = lr.get("gst_percent") != null ? ((Number) lr.get("gst_percent")).doubleValue() : 18.0;
            double qty = lr.get("quantity") != null ? ((Number) lr.get("quantity")).doubleValue() : 1.0;
            double rate = lr.get("rate") != null ? ((Number) lr.get("rate")).doubleValue() : 0.0;
            double disc = lr.get("discount_amount") != null ? ((Number) lr.get("discount_amount")).doubleValue() : 0.0;
            double lTotal = lr.get("line_total") != null ? ((Number) lr.get("line_total")).doubleValue() : 0.0;

            String cGstin = lr.get("cust_gstin") != null ? String.valueOf(lr.get("cust_gstin")).trim() : "";
            String gstType = lr.get("gst_type") != null ? String.valueOf(lr.get("gst_type")).toUpperCase() : "";
            boolean isInter = gstType.contains("INTER") || "IGST".equals(gstType) || (!cGstin.startsWith(homeState) && cGstin.length() == 15);

            BigDecimal txVal = BigDecimal.valueOf((qty * rate) - disc).setScale(2, RoundingMode.HALF_UP);
            if (txVal.compareTo(BigDecimal.ZERO) <= 0) {
                txVal = BigDecimal.valueOf(lTotal / (1.0 + (gstPct / 100.0))).setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal tax = BigDecimal.valueOf(lTotal).subtract(txVal).setScale(2, RoundingMode.HALF_UP);
            if (tax.compareTo(BigDecimal.ZERO) < 0) tax = BigDecimal.ZERO;

            BigDecimal iamt = isInter ? tax : BigDecimal.ZERO;
            BigDecimal camt = !isInter ? tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal samt = !isInter ? tax.subtract(camt) : BigDecimal.ZERO;

            String hsnKey = hsn + "_" + uqc + "_" + gstPct;
            Gstr1Dtos.Gstr1HsnItemDto existing = hsnGroup.get(hsnKey);
            if (existing == null) {
                hsnGroup.put(hsnKey, new Gstr1Dtos.Gstr1HsnItemDto(
                        hsnSeq++, hsn, desc, uqc, BigDecimal.valueOf(qty).setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(lTotal).setScale(2, RoundingMode.HALF_UP), txVal,
                        BigDecimal.valueOf(gstPct).setScale(2, RoundingMode.HALF_UP), iamt, camt, samt, BigDecimal.ZERO
                ));
            } else {
                hsnGroup.put(hsnKey, new Gstr1Dtos.Gstr1HsnItemDto(
                        existing.num(), existing.hsn_sc(), existing.desc(), existing.uqc(),
                        existing.qty().add(BigDecimal.valueOf(qty)),
                        existing.val().add(BigDecimal.valueOf(lTotal)),
                        existing.txval().add(txVal),
                        existing.rt(),
                        existing.iamt().add(iamt),
                        existing.camt().add(camt),
                        existing.samt().add(samt),
                        BigDecimal.ZERO
                ));
            }
        }
        hsnList.addAll(hsnGroup.values());

        // 4. Table 13: Documents Issued
        List<Gstr1Dtos.Gstr1DocSummaryDto> docSummary = new ArrayList<>();
        try {
            Map<String, Object> invStats = jdbc.queryForMap("""
                SELECT COALESCE(MIN(invoice_no), '-') as min_inv,
                       COALESCE(MAX(invoice_no), '-') as max_inv,
                       COUNT(*) as total_cnt,
                       SUM(CASE WHEN UPPER(COALESCE(document_status, '')) IN ('CANCELLED', 'DELETED') THEN 1 ELSE 0 END) as canc_cnt
                FROM sales_header
                WHERE invoice_date BETWEEN ? AND ?
                """, startStr, endStr);

            long invTot = ((Number) invStats.get("total_cnt")).longValue();
            long invCanc = ((Number) invStats.get("canc_cnt")).longValue();
            docSummary.add(new Gstr1Dtos.Gstr1DocSummaryDto(
                    1, "Invoices for Outward Supply",
                    String.valueOf(invStats.get("min_inv")), String.valueOf(invStats.get("max_inv")),
                    invTot, invCanc, invTot - invCanc
            ));
        } catch (Exception ignored) {}

        try {
            Map<String, Object> cnStats = jdbc.queryForMap("""
                SELECT COALESCE(MIN(credit_note_no), '-') as min_cn,
                       COALESCE(MAX(credit_note_no), '-') as max_cn,
                       COUNT(*) as total_cnt,
                       SUM(CASE WHEN UPPER(COALESCE(status, '')) = 'CANCELLED' THEN 1 ELSE 0 END) as canc_cnt
                FROM credit_note_header
                WHERE note_date BETWEEN ? AND ?
                """, win.startDate(), win.endDate());

            long cnTot = ((Number) cnStats.get("total_cnt")).longValue();
            long cnCanc = ((Number) cnStats.get("canc_cnt")).longValue();
            docSummary.add(new Gstr1Dtos.Gstr1DocSummaryDto(
                    4, "Credit Notes",
                    String.valueOf(cnStats.get("min_cn")), String.valueOf(cnStats.get("max_cn")),
                    cnTot, cnCanc, cnTot - cnCanc
            ));
        } catch (Exception ignored) {}

        BigDecimal totalTax = totalIgst.add(totalCgst).add(totalSgst);

        return new Gstr1Dtos.Gstr1FullReturnDto(
                myGstin, win.fp(), "GSTR1_v2.0", grossTurnover, totalTaxable,
                totalIgst, totalCgst, totalSgst, totalTax,
                b2bList, b2csList, cdnrList, hsnList, docSummary
        );
    }

    public ObjectNode generateGovernmentJson(String returnPeriod) {
        Gstr1Dtos.Gstr1FullReturnDto gstr1 = generateGstr1(returnPeriod);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("gstin", gstr1.gstin());
        root.put("fp", gstr1.fp());
        root.put("version", gstr1.version());
        root.put("hash", "hash");
        root.put("cur_gt", gstr1.grossTurnover().setScale(2, RoundingMode.HALF_UP));
        root.put("gt", gstr1.grossTurnover().setScale(2, RoundingMode.HALF_UP));

        // B2B Section
        ArrayNode b2bArray = root.putArray("b2b");
        for (Gstr1Dtos.Gstr1B2bPartyDto party : gstr1.b2b()) {
            ObjectNode pNode = b2bArray.addObject();
            pNode.put("ctin", party.ctin());
            ArrayNode invArray = pNode.putArray("inv");
            for (Gstr1Dtos.Gstr1B2bInvoiceDto inv : party.inv()) {
                ObjectNode iNode = invArray.addObject();
                iNode.put("inum", inv.inum());
                iNode.put("idt", inv.idt());
                iNode.put("val", inv.val().setScale(2, RoundingMode.HALF_UP));
                iNode.put("pos", inv.pos());
                iNode.put("rchrg", inv.rchrg());
                iNode.put("inv_typ", inv.inv_typ());

                ArrayNode itmArray = iNode.putArray("itms");
                for (Gstr1Dtos.Gstr1ItemDetailDto item : inv.itms()) {
                    ObjectNode itmObj = itmArray.addObject();
                    itmObj.put("num", item.num());
                    ObjectNode itmDet = itmObj.putObject("itm_det");
                    itmDet.put("txval", item.txval().setScale(2, RoundingMode.HALF_UP));
                    itmDet.put("rt", item.rt().setScale(2, RoundingMode.HALF_UP));
                    itmDet.put("iamt", item.iamt().setScale(2, RoundingMode.HALF_UP));
                    itmDet.put("camt", item.camt().setScale(2, RoundingMode.HALF_UP));
                    itmDet.put("samt", item.samt().setScale(2, RoundingMode.HALF_UP));
                    itmDet.put("csamt", item.csamt().setScale(2, RoundingMode.HALF_UP));
                }
            }
        }

        // B2CS Section
        ArrayNode b2csArray = root.putArray("b2cs");
        for (Gstr1Dtos.Gstr1B2csDto b2cs : gstr1.b2cs()) {
            ObjectNode bNode = b2csArray.addObject();
            bNode.put("sply_ty", b2cs.sply_ty());
            bNode.put("pos", b2cs.pos());
            bNode.put("txval", b2cs.txval().setScale(2, RoundingMode.HALF_UP));
            bNode.put("rt", b2cs.rt().setScale(2, RoundingMode.HALF_UP));
            bNode.put("iamt", b2cs.iamt().setScale(2, RoundingMode.HALF_UP));
            bNode.put("camt", b2cs.camt().setScale(2, RoundingMode.HALF_UP));
            bNode.put("samt", b2cs.samt().setScale(2, RoundingMode.HALF_UP));
            bNode.put("csamt", b2cs.csamt().setScale(2, RoundingMode.HALF_UP));
        }

        // CDNR Section
        ArrayNode cdnrArray = root.putArray("cdnr");
        for (Gstr1Dtos.Gstr1CdnrPartyDto cdnrParty : gstr1.cdnr()) {
            ObjectNode pNode = cdnrArray.addObject();
            pNode.put("ctin", cdnrParty.ctin());
            ArrayNode ntArray = pNode.putArray("nt");
            for (Gstr1Dtos.Gstr1CdnrNoteDto note : cdnrParty.nt()) {
                ObjectNode nNode = ntArray.addObject();
                nNode.put("nt_num", note.nt_num());
                nNode.put("nt_dt", note.nt_dt());
                nNode.put("ntty", note.ntty());
                nNode.put("inum", note.inum());
                nNode.put("idt", note.idt());
                nNode.put("val", note.val().setScale(2, RoundingMode.HALF_UP));
                nNode.put("pos", note.pos());
                nNode.put("rchrg", note.rchrg());

                ArrayNode itmArray = nNode.putArray("itms");
                for (Gstr1Dtos.Gstr1ItemDetailDto itm : note.itms()) {
                    ObjectNode itmObj = itmArray.addObject();
                    itmObj.put("num", itm.num());
                    ObjectNode itmDet = itmObj.putObject("itm_det");
                    itmDet.put("txval", itm.txval().setScale(2, RoundingMode.HALF_UP));
                    itmDet.put("rt", itm.rt().setScale(2, RoundingMode.HALF_UP));
                    itmDet.put("iamt", itm.iamt().setScale(2, RoundingMode.HALF_UP));
                    itmDet.put("camt", itm.camt().setScale(2, RoundingMode.HALF_UP));
                    itmDet.put("samt", itm.samt().setScale(2, RoundingMode.HALF_UP));
                    itmDet.put("csamt", itm.csamt().setScale(2, RoundingMode.HALF_UP));
                }
            }
        }

        // HSN Section
        ObjectNode hsnNode = root.putObject("hsn");
        ArrayNode hsnData = hsnNode.putArray("data");
        for (Gstr1Dtos.Gstr1HsnItemDto hsn : gstr1.hsn()) {
            ObjectNode item = hsnData.addObject();
            item.put("num", hsn.num());
            item.put("hsn_sc", hsn.hsn_sc());
            item.put("desc", hsn.desc());
            item.put("uqc", hsn.uqc());
            item.put("qty", hsn.qty().setScale(2, RoundingMode.HALF_UP));
            item.put("val", hsn.val().setScale(2, RoundingMode.HALF_UP));
            item.put("txval", hsn.txval().setScale(2, RoundingMode.HALF_UP));
            item.put("rt", hsn.rt().setScale(2, RoundingMode.HALF_UP));
            item.put("iamt", hsn.iamt().setScale(2, RoundingMode.HALF_UP));
            item.put("camt", hsn.camt().setScale(2, RoundingMode.HALF_UP));
            item.put("samt", hsn.samt().setScale(2, RoundingMode.HALF_UP));
            item.put("csamt", hsn.csamt().setScale(2, RoundingMode.HALF_UP));
        }

        // Documents Issued Section
        ObjectNode docIssue = root.putObject("doc_issue");
        ArrayNode docDet = docIssue.putArray("doc_det");
        for (Gstr1Dtos.Gstr1DocSummaryDto d : gstr1.documents()) {
            ObjectNode dNode = docDet.addObject();
            dNode.put("doc_num", d.doc_num());
            ArrayNode docs = dNode.putArray("docs");
            ObjectNode inner = docs.addObject();
            inner.put("num", 1);
            inner.put("from", d.from_serial());
            inner.put("to", d.to_serial());
            inner.put("totnum", d.total_count());
            inner.put("canc", d.cancelled_count());
            inner.put("net_issue", d.net_issued_count());
        }

        return root;
    }

    private static String formatDateGst(String raw) {
        if (raw == null || raw.isBlank()) return "01-10-2026";
        try {
            String s = raw.length() >= 10 ? raw.substring(0, 10) : raw;
            LocalDate d = LocalDate.parse(s);
            return d.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (Exception e) {
            return raw;
        }
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        try {
            return new BigDecimal(v.toString().trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
