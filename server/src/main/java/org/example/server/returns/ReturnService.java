package org.example.server.returns;

import org.example.server.audit.AuditService;
import org.example.server.operations.BusinessOperationsService;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.util.BusinessClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ReturnService {
    private static final Set<String> RETURNABLE_SOURCE_BLOCKED = Set.of("DELETED", "CANCELLED", "REJECTED", "PENDING APPROVAL", "DRAFT");
    private static final Set<String> RETURN_DOCUMENT_TERMINAL = Set.of("REJECTED", "CANCELLED", "DELETED");

    private final JpaNativeRepository jdbc;
    private final BusinessOperationsService operations;
    private final AuditService audit;
    private final int settlementDays;

    public ReturnService(JpaNativeRepository jdbc,
                         BusinessOperationsService operations,
                         AuditService audit,
                         @Value("${dse.return.settlement-days:7}") int settlementDays) {
        this.jdbc = jdbc;
        this.operations = operations;
        this.audit = audit;
        this.settlementDays = Math.max(0, Math.min(settlementDays, 365));
    }

    @Transactional(readOnly = true)
    public List<ReturnDtos.Summary> summaries(String type) {
        return jdbc.query(
            returnSummaryCte() + " SELECT return_no,return_date,invoice_no,party,total,refund,reason,status,refund_status,email FROM s ORDER BY " + returnDateSql("return_date") + " DESC NULLS LAST,return_no DESC",
            (r, i) -> new ReturnDtos.Summary(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getDouble(5), r.getDouble(6), r.getString(7), r.getString(8), r.getString(9), r.getString(10)),
            normalizeType(type)
        );
    }

    @Transactional(readOnly = true)
    public ReturnDtos.Page page(String type, int page, int size, String q, String party, String status, String from, String to) {
        int safeSize = Math.max(10, Math.min(size, 200)), safePage = Math.max(0, page);
        List<Object> args = new ArrayList<>();
        args.add(normalizeType(type));
        StringBuilder filter = new StringBuilder(" WHERE 1=1");
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String partyFilter = party == null ? "" : party.trim();
        String statusFilter = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!query.isBlank()) { filter.append(" AND LOWER(CONCAT_WS(' ',s.return_no,s.invoice_no,s.party,s.reason)) LIKE ?"); args.add("%" + query + "%"); }
        if (!partyFilter.isBlank() && !partyFilter.toUpperCase(Locale.ROOT).startsWith("ALL")) { filter.append(" AND s.party=?"); args.add(partyFilter); }
        if (!statusFilter.isBlank() && !statusFilter.startsWith("ALL")) { filter.append(" AND UPPER(s.status)=?"); args.add(statusFilter); }
        LocalDate start = parseDate(from), end = parseDate(to);
        if (start != null) { filter.append(" AND ").append(returnDateSql("s.return_date")).append(">=?"); args.add(start); }
        if (end != null) { filter.append(" AND ").append(returnDateSql("s.return_date")).append("<=?"); args.add(end); }

        String cte = returnSummaryCte();
        Object[] baseArgs = args.toArray();
        long total = jdbc.queryForObject(cte + " SELECT COUNT(*) FROM s" + filter, Long.class, baseArgs);
        int pages = total == 0 ? 0 : (int) Math.ceil(total / (double) safeSize);
        if (pages > 0 && safePage >= pages) safePage = pages - 1;

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add((long) safePage * safeSize);
        List<ReturnDtos.Summary> rows = jdbc.query(
            cte + " SELECT return_no,return_date,invoice_no,party,total,refund,reason,status,refund_status,email FROM s" + filter + " ORDER BY " + returnDateSql("return_date") + " DESC NULLS LAST,return_no DESC LIMIT ? OFFSET ?",
            (r, i) -> new ReturnDtos.Summary(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getDouble(5), r.getDouble(6), r.getString(7), r.getString(8), r.getString(9), r.getString(10)),
            pageArgs.toArray()
        );
        List<String> parties = jdbc.query(cte + " SELECT DISTINCT party FROM s WHERE COALESCE(party,'')<>'' ORDER BY party LIMIT 40", (r, i) -> r.getString(1), normalizeType(type));
        List<Number[]> metricRows = jdbc.query(
            cte + " SELECT COALESCE(SUM(total),0),COUNT(*),COALESCE(SUM(CASE WHEN EXTRACT(YEAR FROM " + returnDateSql("return_date") + ")=EXTRACT(YEAR FROM CURRENT_DATE) AND EXTRACT(MONTH FROM " + returnDateSql("return_date") + ")=EXTRACT(MONTH FROM CURRENT_DATE) THEN total ELSE 0 END),0),COALESCE(SUM(CASE WHEN EXTRACT(YEAR FROM " + returnDateSql("return_date") + ")=EXTRACT(YEAR FROM CURRENT_DATE) AND EXTRACT(MONTH FROM " + returnDateSql("return_date") + ")=EXTRACT(MONTH FROM CURRENT_DATE) THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN UPPER(status)='APPROVED' THEN total ELSE 0 END),0),COALESCE(SUM(refund),0) FROM s" + filter,
            (r, i) -> new Number[]{(Number) r.getObject(1), (Number) r.getObject(2), (Number) r.getObject(3), (Number) r.getObject(4), (Number) r.getObject(5), (Number) r.getObject(6)},
            baseArgs
        );
        Number[] m = metricRows.isEmpty() ? new Number[]{0, 0, 0, 0, 0, 0} : metricRows.getFirst();
        double sum = n(m[0]), monthAmount = n(m[2]), approved = n(m[4]), refund = n(m[5]);
        long count = m[1] == null ? 0 : m[1].longValue(), monthCount = m[3] == null ? 0 : m[3].longValue();
        return new ReturnDtos.Page(rows, safePage, safeSize, total, pages, new ReturnDtos.Metrics(sum, count, monthAmount, monthCount, approved, refund, count == 0 ? 0 : sum / count), parties);
    }

    private String returnSummaryCte() {
        return "WITH s AS (" +
            "SELECT r.return_no,MAX(COALESCE(r.return_date,'')) return_date,MAX(COALESCE(r.invoice_no,'')) invoice_no," +
            "MAX(COALESCE(p.name,'')) party,SUM(COALESCE(r.amount,0)) total," +
            "COALESCE((SELECT SUM(rr.amount+COALESCE(rr.rounding_adjustment,0)) FROM return_refund rr WHERE rr.return_no=r.return_no),0) refund," +
            "MAX(COALESCE(r.reason,'')) reason,MAX(COALESCE(r.status,'PENDING APPROVAL')) status," +
            "CASE WHEN MAX(UPPER(COALESCE(r.status,'PENDING APPROVAL')))='PENDING APPROVAL' THEN 'WAITING APPROVAL' " +
            "WHEN MAX(UPPER(COALESCE(r.status,'PENDING APPROVAL'))) IN ('REJECTED','CANCELLED','DELETED') THEN 'N/A' " +
            "WHEN COALESCE((SELECT SUM(rr.amount+COALESCE(rr.rounding_adjustment,0)) FROM return_refund rr WHERE rr.return_no=r.return_no),0)>=SUM(r.amount) AND SUM(r.amount)>0 THEN 'REFUNDED' " +
            "WHEN COALESCE((SELECT SUM(rr.amount+COALESCE(rr.rounding_adjustment,0)) FROM return_refund rr WHERE rr.return_no=r.return_no),0)>0 THEN 'PARTIAL' ELSE 'PENDING' END refund_status," +
            "MAX(COALESCE(p.email,'')) email FROM return_register r LEFT JOIN party_master p ON p.id=r.party_id " +
            "WHERE r.return_type=? AND UPPER(COALESCE(r.status,'PENDING APPROVAL'))<>'DELETED' GROUP BY r.return_no)";
    }

    private static String returnDateSql(String c) {
        return "CASE WHEN COALESCE(" + c + ",'') ~ '^\\d{4}-\\d{2}-\\d{2}$' THEN TO_DATE(" + c + ",'YYYY-MM-DD') " +
            "WHEN COALESCE(" + c + ",'') ~ '^\\d{2}/\\d{2}/\\d{4}$' THEN TO_DATE(" + c + ",'DD/MM/YYYY') " +
            "WHEN COALESCE(" + c + ",'') ~ '^\\d{2}-\\d{2}-\\d{4}$' THEN TO_DATE(" + c + ",'DD-MM-YYYY') ELSE NULL END";
    }

    /** Quantities reserved by pending approval returns and consumed by approved returns. */
    @Transactional(readOnly = true)
    public Map<String, Double> returned(String type, String invoice) {
        Map<String, Double> out = new HashMap<>();
        jdbc.query("SELECT item_code,COALESCE(SUM(quantity),0) FROM return_register WHERE return_type=? AND invoice_no=? AND UPPER(COALESCE(status,'PENDING APPROVAL')) IN ('PENDING APPROVAL','APPROVED') GROUP BY item_code",
            r -> out.put(r.getString(1), r.getDouble(2)), normalizeType(type), invoice);
        return out;
    }

    @Transactional
    public ReturnDtos.Created create(ReturnDtos.CreateRequest d) {
        if (d == null || d.lines() == null || d.lines().isEmpty()) throw new IllegalArgumentException("Select at least one item to return.");
        String type = normalizeType(d.type());
        requireTypePermission(type, "EDIT");
        if (d.invoiceNo() == null || d.invoiceNo().isBlank()) throw new IllegalArgumentException("Invoice number is required.");
        LocalDate returnDate = parseDate(d.returnDate());
        if (returnDate == null) throw new IllegalArgumentException("Return date must be a valid date.");
        boolean sales = "SALES RETURN".equals(type);
        if (sales) requireActiveSaleForReturn(d.invoiceNo()); else requirePostedPurchaseForReturn(d.invoiceNo());
        requireNoOpenReturn(type, d.invoiceNo());
        int expectedParty = sourcePartyId(sales, d.invoiceNo());
        if (d.partyId() != expectedParty) throw new IllegalArgumentException("Return party must match the original invoice party.");

        String refKey = sales ? "REF_SALES_RETURN" : "REF_PURCHASE_RETURN", fallback = sales ? "SAL-RET-YYYY-XXXX" : "PUR-RET-YYYY-XXXX";
        String no = operations.nextConfiguredReference(refKey, fallback, () -> jdbc.query("SELECT DISTINCT return_no FROM return_register WHERE return_no IS NOT NULL", (r, i) -> r.getString(1)));
        String now = BusinessClock.nowUtcText();
        String actor = CurrentUser.require().username();

        for (var requested : d.lines()) {
            if (requested.code() == null || requested.code().isBlank()) throw new IllegalArgumentException("Return item code is required.");
            if (!Double.isFinite(requested.quantity()) || requested.quantity() <= 0) throw new IllegalArgumentException("Return quantity must be a finite number greater than zero.");
            double already = returnedQuantity(type, d.invoiceNo(), requested.code());
            List<OriginalLine> originals = originalLines(sales, d.invoiceNo(), requested.code());
            double invoiced = originals.stream().mapToDouble(OriginalLine::quantity).sum();
            if (invoiced <= 0) throw new IllegalArgumentException("Item " + requested.code() + " is not present on invoice " + d.invoiceNo() + ".");
            if (requested.quantity() > invoiced - already + .0001) throw new IllegalArgumentException("Return quantity exceeds the remaining invoiced quantity for " + requested.code() + ".");

            double toSkip = already, remaining = requested.quantity();
            for (OriginalLine original : originals) {
                if (remaining <= .0001) break;
                double available = original.quantity();
                if (toSkip >= available - .0001) { toSkip -= available; continue; }
                available -= Math.max(0, toSkip); toSkip = 0;
                double qty = Math.min(available, remaining);
                double amount = money((original.lineTotal() / original.quantity()) * qty);
                // v9.0.25: no stock/accounting movement until Admin approves the Return.
                jdbc.update("INSERT INTO return_register(return_no,return_type,return_date,invoice_no,party_id,item_code,quantity,amount,reason,status,refund_amount,refund_status,created_at,updated_at,source_line_id,approval_requested_by,approval_requested_at) VALUES(?,?,?,?,?,?,?,?,?,'PENDING APPROVAL',0,'WAITING APPROVAL',?,?,?,?,?)",
                    no, type, returnDate.toString(), d.invoiceNo(), expectedParty, requested.code(), qty, amount, requested.reason(), now, now, original.id(), actor, now);
                remaining -= qty;
            }
            if (remaining > .0001) throw new IllegalStateException("Could not allocate the requested return quantity to original invoice lines for " + requested.code() + ".");
        }
        audit.log(sales ? "SALES_RETURN" : "PURCHASE_RETURN", returnAuditId(no), "PENDING_APPROVAL", no + " • " + d.invoiceNo());
        return new ReturnDtos.Created(no);
    }

    @Transactional
    public void approve(String no) {
        requireReturnPermission(no, "APPROVE");
        String state = currentStateForUpdate(no);
        if (!"PENDING APPROVAL".equals(state)) throw new IllegalStateException("Only a Return waiting for approval can be approved.");
        ReturnOrigin origin = origin(no);
        boolean sales = "SALES RETURN".equalsIgnoreCase(origin.type());
        // Re-check the original at approval time so a stale pending Return cannot bypass the full-paid rule.
        if (sales) requireActiveSaleForReturn(origin.invoice()); else requirePostedPurchaseForReturn(origin.invoice());
        postReturnStock(no, sales);
        String now = BusinessClock.nowUtcText();
        LocalDate due = BusinessClock.today().plusDays(settlementDays);
        jdbc.update("UPDATE return_register SET status='APPROVED',refund_status='PENDING',approved_by=?,approved_at=?,rejection_reason=NULL,settlement_due_date=?,updated_at=? WHERE return_no=?",
            CurrentUser.require().username(), now, due, now, no);
        audit.log(returnEntityType(no), returnAuditId(no), "APPROVED", no + " • settlement due " + due);
    }

    @Transactional
    public void reject(String no, String reason) {
        requireReturnPermission(no, "APPROVE");
        String state = currentStateForUpdate(no);
        if (!"PENDING APPROVAL".equals(state)) throw new IllegalStateException("Only a Return waiting for approval can be rejected.");
        String why = reason == null || reason.isBlank() ? "Rejected by Admin" : reason.trim();
        String now = BusinessClock.nowUtcText();
        jdbc.update("UPDATE return_register SET status='REJECTED',refund_status='N/A',approved_by=NULL,approved_at=NULL,rejection_reason=?,settlement_due_date=NULL,updated_at=? WHERE return_no=?",
            why, now, no);
        audit.log(returnEntityType(no), returnAuditId(no), "REJECTED", no + " • " + why);
    }

    @Transactional(readOnly = true)
    public List<ReturnDtos.Settlement> settlements(String type) {
        String normalized = normalizeType(type);
        boolean salesReturn = "SALES RETURN".equals(normalized);
        String originalQtySql = salesReturn
            ? "COALESCE((SELECT SUM(COALESCE(sl.quantity,0)) FROM sales_line sl JOIN sales_header sh ON sh.id=sl.sales_id WHERE sh.invoice_no=a.invoice_no),0)"
            : "COALESCE((SELECT SUM(COALESCE(pl.quantity,0)) FROM purchase_line pl JOIN purchase_header ph ON ph.id=pl.purchase_id WHERE ph.invoice_no=a.invoice_no),0)";
        String unreturnedLinesSql = salesReturn
            ? "COALESCE((SELECT COUNT(*) FROM sales_line sl JOIN sales_header sh ON sh.id=sl.sales_id WHERE sh.invoice_no=a.invoice_no AND COALESCE((SELECT SUM(rr.quantity) FROM return_register rr WHERE rr.source_line_id=sl.id AND UPPER(COALESCE(rr.return_type,'')) IN ('SALE RETURN','SALES RETURN') AND UPPER(COALESCE(rr.status,''))='APPROVED'),0)+0.0001<COALESCE(sl.quantity,0)),0)"
            : "COALESCE((SELECT COUNT(*) FROM purchase_line pl JOIN purchase_header ph ON ph.id=pl.purchase_id WHERE ph.invoice_no=a.invoice_no AND COALESCE((SELECT SUM(rr.quantity) FROM return_register rr WHERE rr.source_line_id=pl.id AND UPPER(COALESCE(rr.return_type,''))='PURCHASE RETURN' AND UPPER(COALESCE(rr.status,''))='APPROVED'),0)+0.0001<COALESCE(pl.quantity,0)),0)";
        String sql = "SELECT a.invoice_no,a.approved_total,a.settled_total," +
            "CASE WHEN a.waiting_count>0 THEN 0 ELSE GREATEST(a.approved_total-a.settled_total,0) END pending_amount," +
            "CASE WHEN a.waiting_count>0 THEN 'RETURN APPROVAL PENDING' " +
            "WHEN a.approved_total>0 AND GREATEST(a.approved_total-a.settled_total,0)<=0.01 THEN 'RETURN PAID' " +
            "WHEN a.settled_total>0 THEN 'RETURN PARTIAL' WHEN a.approved_total>0 THEN 'RETURN PENDING' ELSE NULL END current_status,a.due_date," +
            "a.approved_qty," + originalQtySql + " original_qty," + unreturnedLinesSql + " unreturned_lines " +
            "FROM (SELECT x.invoice_no,SUM(CASE WHEN x.state='APPROVED' THEN x.return_total ELSE 0 END) approved_total," +
            "SUM(CASE WHEN x.state='APPROVED' THEN x.refunded ELSE 0 END) settled_total," +
            "SUM(CASE WHEN x.state='APPROVED' THEN x.return_qty ELSE 0 END) approved_qty," +
            "SUM(CASE WHEN x.state='PENDING APPROVAL' THEN 1 ELSE 0 END) waiting_count," +
            "MAX(CASE WHEN x.state='APPROVED' THEN x.due_date END) due_date FROM (" +
            "SELECT MAX(r.invoice_no) invoice_no,r.return_no,MAX(UPPER(COALESCE(r.status,'PENDING APPROVAL'))) state," +
            "SUM(COALESCE(r.amount,0)) return_total,SUM(COALESCE(r.quantity,0)) return_qty," +
            "COALESCE((SELECT SUM(rr.amount+COALESCE(rr.rounding_adjustment,0)) FROM return_refund rr WHERE rr.return_no=r.return_no),0) refunded," +
            "MAX(r.settlement_due_date) due_date FROM return_register r WHERE r.return_type=? AND UPPER(COALESCE(r.status,'PENDING APPROVAL')) IN ('PENDING APPROVAL','APPROVED') GROUP BY r.return_no" +
            ") x GROUP BY x.invoice_no) a WHERE a.waiting_count>0 OR a.approved_total>0 ORDER BY a.invoice_no";
        return jdbc.query(sql, (r, i) -> {
            double approved = r.getDouble(2), settled = r.getDouble(3), returnedQty = r.getDouble(7), originalQty = r.getDouble(8);
            long unreturnedLines = r.getLong(9);
            String current = r.getString(5);
            String returnStatus;
            if ("RETURN APPROVAL PENDING".equalsIgnoreCase(current)) returnStatus = "PENDING APPROVAL";
            else if (approved <= 0.01 || returnedQty <= 0.0001) returnStatus = "N/A";
            else if (originalQty > 0.0001 && unreturnedLines == 0) returnStatus = "FULLY RETURNED";
            else returnStatus = "PARTIALLY RETURNED";
            String refundStatus;
            if ("PENDING APPROVAL".equals(returnStatus)) refundStatus = "N/A";
            else if (approved <= 0.01) refundStatus = "N/A";
            else if (settled + 0.01 >= approved) refundStatus = "REFUNDED";
            else if (settled > 0.01) refundStatus = "PARTIAL";
            else refundStatus = "PENDING";
            return new ReturnDtos.Settlement(r.getString(1), current, r.getDouble(4), approved, settled,
                r.getObject(6) == null ? null : String.valueOf(r.getObject(6)), returnStatus, refundStatus, returnedQty, originalQty);
        }, normalized);
    }

    @Transactional(readOnly = true)
    public ReturnDtos.Details details(String no) {
        List<ReturnDtos.Line> lines = jdbc.query("SELECT COALESCE(NULLIF(sl.item_description_snapshot,''),NULLIF(pl.item_description_snapshot,''),im.description,r.item_code),r.item_code,r.quantity,COALESCE(NULLIF(sl.unit_snapshot,''),NULLIF(pl.unit_snapshot,''),im.unit,'Nos'),COALESCE(sl.rate,pl.rate,0),COALESCE(sl.gst_percent,pl.gst_percent,0),r.amount,COALESCE(r.reason,'') FROM return_register r LEFT JOIN item_master im ON im.item_code=r.item_code LEFT JOIN sales_line sl ON r.return_type='SALES RETURN' AND sl.id=r.source_line_id LEFT JOIN purchase_line pl ON r.return_type='PURCHASE RETURN' AND pl.id=r.source_line_id WHERE r.return_no=? ORDER BY r.id",
            (r, i) -> new ReturnDtos.Line(r.getString(1), r.getString(2), r.getDouble(3), r.getString(4), r.getDouble(5), r.getDouble(6), r.getDouble(7), r.getString(8)), no);
        if (lines.isEmpty()) throw new IllegalArgumentException("Return not found: " + no);
        Map<String, Object> h = jdbc.queryForMap("SELECT MAX(r.return_no) no,MAX(r.return_date) date,MAX(COALESCE(r.invoice_no,'')) invoice,MAX(COALESCE(pm.name,'')) party,MAX(r.return_type) type,MAX(COALESCE(ph.payment_terms,'')) terms,MAX(COALESCE(ph.currency,'INR - Indian Rupee')) currency,MAX(COALESCE(r.created_at::text,'')) created,MAX(COALESCE(r.updated_at::text,'')) updated,MAX(COALESCE(r.attachment_path,'')) attachment,MAX(COALESCE(r.notes,'')) notes,SUM(r.amount) total,MAX(COALESCE(r.status,'PENDING APPROVAL')) raw_status FROM return_register r LEFT JOIN party_master pm ON pm.id=r.party_id LEFT JOIN purchase_header ph ON ph.invoice_no=r.invoice_no WHERE r.return_no=?", no);
        double total = n(h.get("total")), refund = refundTotal(no);
        String raw = Objects.toString(h.get("raw_status"), "PENDING APPROVAL"), refundStatus = lifecycleRefundStatus(raw, total, refund);
        return new ReturnDtos.Details(no, Objects.toString(h.get("date"), ""), Objects.toString(h.get("invoice"), ""), Objects.toString(h.get("party"), ""), Objects.toString(h.get("type"), ""), Objects.toString(h.get("terms"), ""), Objects.toString(h.get("currency"), "INR - Indian Rupee"), Objects.toString(h.get("created"), ""), Objects.toString(h.get("updated"), ""), Objects.toString(h.get("attachment"), ""), Objects.toString(h.get("notes"), ""), total, refund, raw, refundStatus, lines);
    }

    @Transactional
    public void update(String no, String field, String value) {
        requireReturnPermission(no, "EDIT");
        String requestedField = field == null ? "" : field.trim().toLowerCase(Locale.ROOT);
        // Backward-compatible bridge for existing clients that already submit a Return
        // status update. The lifecycle action still goes through the protected approval
        // methods, so EDIT permission can never bypass APPROVE permission.
        if ("status".equals(requestedField)) {
            String requested = up(value);
            if ("APPROVED".equals(requested)) { approve(no); return; }
            if ("REJECTED".equals(requested)) { reject(no, "Rejected by Admin"); return; }
            throw new IllegalArgumentException("Return status is lifecycle-managed. Only APPROVED or REJECTED can be requested through this compatibility path.");
        }
        if (!Set.of("reason", "notes").contains(requestedField)) throw new IllegalArgumentException("Return status is lifecycle-managed and cannot be edited directly.");
        if (RETURN_DOCUMENT_TERMINAL.contains(currentStateForUpdate(no))) throw new IllegalStateException("Rejected, cancelled or deleted Returns cannot be edited.");
        jdbc.update("UPDATE return_register SET " + requestedField + "=?,updated_at=? WHERE return_no=?", value, BusinessClock.nowUtcText(), no);
        audit.log(returnEntityType(no), returnAuditId(no), "UPDATED", no + " • " + requestedField);
    }

    @Transactional
    public void refund(String no, double amount) {
        recordRefund(no, new ReturnDtos.RefundCreateRequest(BusinessClock.today().toString(), amount, "Legacy Refund", "", "", "", "Recorded from legacy refund action", "PARTIAL", CurrentUser.require().username()));
    }

    @Transactional(readOnly = true)
    public List<ReturnDtos.RefundRow> refunds(String no) {
        requireAccess(no);
        return jdbc.query("SELECT id,refund_date,COALESCE(reference_no,''),COALESCE(payment_mode,''),COALESCE(bank_account,''),amount,COALESCE(refunded_party,''),CASE WHEN UPPER(COALESCE(refund_type,''))='BANK_RECONCILIATION' THEN 'RECONCILED' ELSE 'RECORDED' END,COALESCE(notes,''),COALESCE(attachment_path,''),COALESCE(refund_type,'PARTIAL') FROM return_refund WHERE return_no=? ORDER BY refund_date DESC,id DESC",
            (r, i) -> new ReturnDtos.RefundRow(r.getInt(1), String.valueOf(r.getObject(2)), r.getString(3), r.getString(4), r.getString(5), r.getDouble(6), r.getString(7), r.getString(8), r.getString(9), r.getString(10), r.getString(11)), no);
    }

    @Transactional
    public int recordRefund(String no, ReturnDtos.RefundCreateRequest d) {
        requireReturnPermission(no, "EDIT");
        requireRefundEligible(no);
        if (d == null) throw new IllegalArgumentException("Refund details are required.");
        double total = total(no), already = refundTotal(no), remaining = Math.max(0, total - already);
        if (!Double.isFinite(d.amount()) || d.amount() <= 0 || d.amount() > remaining + .0001) throw new IllegalArgumentException("Refund amount must be greater than zero and no more than the remaining refund balance of " + String.format(Locale.ROOT, "%.2f", remaining) + ".");
        LocalDate date;
        try { date = LocalDate.parse(d.date()); } catch (Exception e) { throw new IllegalArgumentException("Refund date must use YYYY-MM-DD."); }
        String mode = d.mode() == null ? "" : d.mode().trim();
        if (mode.isBlank()) throw new IllegalArgumentException("Refund payment mode is required.");
        Integer id = jdbc.queryForObject("INSERT INTO return_refund(return_no,refund_date,amount,payment_mode,reference_no,bank_account,refunded_party,notes,refund_type,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?) RETURNING id", Integer.class,
            no, date, d.amount(), mode, clean(d.reference()), clean(d.bankAccount()), clean(d.refundedParty()), clean(d.notes()), clean(d.refundType()) == null ? "PARTIAL" : clean(d.refundType()), CurrentUser.require().username(), BusinessClock.nowUtcText());
        syncRefundState(no);
        if (id == null) throw new IllegalStateException("Refund id was not returned after saving.");
        audit.log(returnEntityType(no), returnAuditId(no), "REFUND_RECORDED", no + " • " + String.format(Locale.ROOT, "%.2f", d.amount()));
        return id;
    }

    /** Called by Bank Reconciliation too. Document lifecycle remains APPROVED; only settlement state changes. */
    @Transactional
    public void syncRefundState(String no) {
        double total = total(no), paid = refundTotal(no);
        String rs = settlementStatus(total, paid);
        String current = currentStateForUpdate(no);
        if (!"APPROVED".equals(current)) {
            if (paid > .0001) throw new IllegalStateException("Only an approved Return can contain refund settlements.");
            return;
        }
        jdbc.update("UPDATE return_register SET refund_amount=0,refund_status=?,updated_at=? WHERE return_no=?", rs, BusinessClock.nowUtcText(), no);
        if (paid > .0001) jdbc.update("UPDATE return_register SET refund_amount=? WHERE id=(SELECT MIN(id) FROM return_register WHERE return_no=?)", paid, no);
    }

    @Transactional
    public void delete(String no, boolean ignoredSalesFlag) {
        requireReturnPermission(no, "DELETE");
        assertUnrefunded(no);
        String state = currentStateForUpdate(no);
        if ("DELETED".equals(state)) return;
        ReturnOrigin origin = origin(no);
        if ("APPROVED".equals(state)) reverseApprovedStock(no, "SALES RETURN".equalsIgnoreCase(origin.type()));
        jdbc.update("UPDATE return_register SET status='DELETED',refund_status='N/A',settlement_due_date=NULL,updated_at=? WHERE return_no=?", BusinessClock.nowUtcText(), no);
        audit.log(returnEntityType(no), returnAuditId(no), "DELETED", no);
    }

    @Transactional
    public void cancel(String no, boolean ignoredSalesFlag) {
        requireReturnPermission(no, "EDIT");
        assertUnrefunded(no);
        String state = currentStateForUpdate(no);
        if ("DELETED".equals(state)) throw new IllegalStateException("Deleted Returns cannot be cancelled.");
        if ("CANCELLED".equals(state)) throw new IllegalStateException("This Return is already cancelled.");
        if ("REJECTED".equals(state)) throw new IllegalStateException("Rejected Returns are final. Delete the audit row only if policy allows it.");
        ReturnOrigin origin = origin(no);
        if ("APPROVED".equals(state)) reverseApprovedStock(no, "SALES RETURN".equalsIgnoreCase(origin.type()));
        jdbc.update("UPDATE return_register SET status='CANCELLED',refund_status='N/A',settlement_due_date=NULL,updated_at=? WHERE return_no=?", BusinessClock.nowUtcText(), no);
        audit.log(returnEntityType(no), returnAuditId(no), "CANCELLED", no);
    }

    private void requireNoOpenReturn(String type, String invoice) {
        Long waiting = jdbc.queryForObject("SELECT COUNT(DISTINCT return_no) FROM return_register WHERE return_type=? AND invoice_no=? AND UPPER(COALESCE(status,''))='PENDING APPROVAL'", Long.class, type, invoice);
        if (waiting != null && waiting > 0) throw new IllegalStateException("A Return for this document is already waiting for Admin approval.");
        Long unsettled = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT r.return_no,SUM(COALESCE(r.amount,0)) total,COALESCE((SELECT SUM(rr.amount+COALESCE(rr.rounding_adjustment,0)) FROM return_refund rr WHERE rr.return_no=r.return_no),0) refunded FROM return_register r WHERE r.return_type=? AND r.invoice_no=? AND UPPER(COALESCE(r.status,''))='APPROVED' GROUP BY r.return_no) x WHERE GREATEST(total-refunded,0)>0.01", Long.class, type, invoice);
        if (unsettled != null && unsettled > 0) throw new IllegalStateException("An approved Return for this document still has a refund/settlement balance. Complete that Return before creating another one.");
    }

    private void postReturnStock(String no, boolean sales) {
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT r.id,r.item_code,r.quantity,r.source_line_id,COALESCE(sl.unit_cost_snapshot,0) unit_cost FROM return_register r LEFT JOIN sales_line sl ON r.return_type='SALES RETURN' AND sl.id=r.source_line_id WHERE r.return_no=? ORDER BY r.id", no);
        for (Map<String, Object> line : lines) {
            String code = Objects.toString(line.get("item_code"), "");
            double q = n(line.get("quantity"));
            if (sales) operations.applyStockMovement(code, q, false, n(line.get("unit_cost")), "SALES_RETURN", ((Number) line.get("id")).intValue());
            else operations.applyStockDelta(code, -q, true);
        }
    }

    private void reverseApprovedStock(String no, boolean sales) {
        for (Map<String, Object> r : jdbc.queryForList("SELECT item_code,quantity FROM return_register WHERE return_no=?", no)) {
            double q = n(r.get("quantity"));
            String code = Objects.toString(r.get("item_code"), "");
            operations.applyStockDelta(code, sales ? -q : q, sales);
        }
    }

    private void assertUnrefunded(String no) {
        Long cnt = jdbc.queryForObject("SELECT COUNT(*) FROM return_register WHERE return_no=?", Long.class, no);
        if (cnt == null || cnt == 0) throw new IllegalStateException("Return " + no + " was not found.");
        if (refundTotal(no) > .0001) throw new IllegalStateException("A refunded or partially refunded Return cannot be deleted or cancelled because it would corrupt accounting history.");
    }

    private double total(String no) {
        Double d = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM return_register WHERE return_no=?", Double.class, no);
        if (d == null || d <= 0) throw new IllegalStateException("Return " + no + " was not found.");
        return d;
    }

    private double refundTotal(String no) {
        Double d = jdbc.queryForObject("SELECT COALESCE(SUM(amount+COALESCE(rounding_adjustment,0)),0) FROM return_refund WHERE return_no=?", Double.class, no);
        return d == null ? 0 : d;
    }

    private String currentStateForUpdate(String no) {
        List<String> rows = jdbc.query("SELECT COALESCE(status,'PENDING APPROVAL') FROM return_register WHERE return_no=? FOR UPDATE", (r, i) -> r.getString(1), no);
        if (rows.isEmpty()) throw new IllegalArgumentException("Return not found: " + no);
        return rows.stream().map(ReturnService::up).filter(x -> !x.isBlank()).findFirst().orElse("PENDING APPROVAL");
    }

    private double recordedPayments(String type, int id) {
        Double value = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE UPPER(document_type)=? AND document_id=?", Double.class, type, id);
        return value == null ? 0 : value;
    }

    private void requireActiveSaleForReturn(String invoice) {
        List<Object[]> rows = jdbc.query("SELECT id,COALESCE(document_status,''),COALESCE(approval_status,'APPROVED'),COALESCE(total_amount,0),COALESCE(paid_amount,0),COALESCE(payment_status,'') FROM sales_header WHERE invoice_no=? FOR UPDATE",
            (r, i) -> new Object[]{r.getInt(1), r.getString(2), r.getString(3), r.getDouble(4), r.getDouble(5), r.getString(6)}, invoice);
        if (rows.isEmpty()) throw new IllegalArgumentException("Sales invoice not found: " + invoice);
        Object[] row = rows.getFirst();
        String document = up(row[1]), approval = up(row[2]);
        if (!"APPROVED".equals(approval) || RETURNABLE_SOURCE_BLOCKED.contains(document)) throw new IllegalStateException("Sales Return is available only for an approved active Sale.");
        int id = ((Number) row[0]).intValue();
        double total = n(row[3]), cached = n(row[4]), recorded = recordedPayments("SALE", id), effective = Math.max(cached, recorded);
        String payment = up(row[5]);
        if (total <= .0001 || (effective + .0001 < total && !Set.of("PAID", "SETTLED").contains(payment))) throw new IllegalStateException("Sales Return is available only after the source Sale is fully paid. Pending, Partial and Over Due Sales cannot be returned.");
    }

    private void requirePostedPurchaseForReturn(String invoice) {
        List<Object[]> rows = jdbc.query("SELECT id,COALESCE(document_status,''),COALESCE(approval_status,'APPROVED'),COALESCE(inventory_posted,false),COALESCE(total_amount,0),COALESCE(paid_amount,0),COALESCE(payment_status,'') FROM purchase_header WHERE invoice_no=? FOR UPDATE",
            (r, i) -> new Object[]{r.getInt(1), r.getString(2), r.getString(3), Boolean.TRUE.equals(r.getObject(4)), r.getDouble(5), r.getDouble(6), r.getString(7)}, invoice);
        if (rows.isEmpty()) throw new IllegalArgumentException("Purchase invoice not found: " + invoice);
        Object[] row = rows.getFirst();
        String document = up(row[1]), approval = up(row[2]);
        boolean posted = Boolean.TRUE.equals(row[3]);
        if (!"APPROVED".equals(approval) || !posted || RETURNABLE_SOURCE_BLOCKED.contains(document)) throw new IllegalStateException("Purchase Return is available only for an approved active posted Purchase.");
        int id = ((Number) row[0]).intValue();
        double total = n(row[4]), cached = n(row[5]), recorded = recordedPayments("PURCHASE", id), effective = Math.max(cached, recorded);
        String payment = up(row[6]);
        if (total <= .0001 || (effective + .0001 < total && !Set.of("PAID", "SETTLED").contains(payment))) throw new IllegalStateException("Purchase Return is available only after the source Purchase is fully paid. Pending, Partial and Over Due Purchases cannot be returned.");
    }

    private void requireRefundEligible(String no) {
        String state = currentStateForUpdate(no);
        if (!"APPROVED".equals(state)) throw new IllegalStateException("Refund/settlement can be recorded only after Admin approves the Return.");
    }

    private double returnedQuantity(String type, String invoice, String code) {
        Double value = jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM return_register WHERE return_type=? AND invoice_no=? AND item_code=? AND UPPER(COALESCE(status,'PENDING APPROVAL')) IN ('PENDING APPROVAL','APPROVED')", Double.class, type, invoice, code);
        return value == null ? 0 : value;
    }

    private ReturnOrigin origin(String no) {
        Map<String, Object> row = jdbc.queryForMap("SELECT MAX(return_type) type,MAX(invoice_no) invoice FROM return_register WHERE return_no=?", no);
        String type = Objects.toString(row.get("type"), ""), invoice = Objects.toString(row.get("invoice"), "");
        if (type.isBlank() || invoice.isBlank()) throw new IllegalArgumentException("Return not found: " + no);
        return new ReturnOrigin(type, invoice);
    }

    private int sourcePartyId(boolean sales, String invoice) {
        Integer id = jdbc.queryForObject(sales ? "SELECT customer_id FROM sales_header WHERE invoice_no=?" : "SELECT supplier_id FROM purchase_header WHERE invoice_no=?", Integer.class, invoice);
        if (id == null || id <= 0) throw new IllegalArgumentException("Original invoice party was not found.");
        return id;
    }

    private List<OriginalLine> originalLines(boolean sales, String invoice, String code) {
        String sql = sales
            ? "SELECT l.id,l.quantity,l.line_total,COALESCE(l.unit_cost_snapshot,0) FROM sales_line l JOIN sales_header h ON h.id=l.sales_id WHERE h.invoice_no=? AND l.item_code=? ORDER BY l.id"
            : "SELECT l.id,l.quantity,l.line_total,CASE WHEN COALESCE(l.quantity,0)>0 THEN GREATEST(0,(COALESCE(l.quantity,0)*COALESCE(l.rate,0)-COALESCE(l.discount_amount,0))/l.quantity) ELSE 0 END FROM purchase_line l JOIN purchase_header h ON h.id=l.purchase_id WHERE h.invoice_no=? AND l.item_code=? ORDER BY l.id";
        return jdbc.query(sql, (r, i) -> new OriginalLine(r.getInt(1), r.getDouble(2), r.getDouble(3), r.getDouble(4)), invoice, code);
    }

    private static String settlementStatus(double total, double paid) {
        if (paid <= .0001) return "PENDING";
        if (paid + .0001 >= total) return "PAID";
        return "PARTIAL";
    }

    private static String lifecycleRefundStatus(String returnStatus, double total, double paid) {
        String state = up(returnStatus);
        if ("PENDING APPROVAL".equals(state)) return "WAITING APPROVAL";
        if (RETURN_DOCUMENT_TERMINAL.contains(state)) return "N/A";
        return "APPROVED".equals(state) ? settlementStatus(total, paid) : "N/A";
    }

    private String clean(String x) { return x == null || x.isBlank() ? null : x.trim(); }
    private static double money(double v) { return java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue(); }
    private static String up(Object x) { return Objects.toString(x, "").trim().toUpperCase(Locale.ROOT); }
    private double n(Object x) { return x instanceof Number v ? v.doubleValue() : 0; }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SALES RETURN", "PURCHASE RETURN").contains(normalized)) throw new IllegalArgumentException("Return type must be SALES RETURN or PURCHASE RETURN.");
        return normalized;
    }

    public void requireTypeAccess(String type) { requireTypePermission(type, "VIEW"); }
    private void requireTypePermission(String type, String action) { String n = normalizeType(type); CurrentUser.requirePermission(n.startsWith("PURCHASE") ? "PURCHASE." + action : "SALES." + action, action + " return"); }
    public void requireAccess(String no) { requireReturnPermission(no, "VIEW"); }
    private void requireReturnPermission(String no, String action) { CurrentUser.requirePermission(isSalesReturn(no) ? "SALES." + action : "PURCHASE." + action, action + " return"); }
    private String returnEntityType(String no) { return isSalesReturn(no) ? "SALES_RETURN" : "PURCHASE_RETURN"; }
    private Integer returnAuditId(String no) { return jdbc.queryForObject("SELECT MIN(id) FROM return_register WHERE return_no=?", Integer.class, no); }
    private boolean isSalesReturn(String no) { String type = jdbc.queryForObject("SELECT MAX(return_type) FROM return_register WHERE return_no=?", String.class, no); if (type == null || type.isBlank()) throw new IllegalArgumentException("Return not found: " + no); return "SALES RETURN".equalsIgnoreCase(type); }

    private static LocalDate parseDate(String v) {
        if (v == null || v.isBlank()) return null;
        String x = v.trim();
        for (DateTimeFormatter f : List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd/MM/uuuu"), DateTimeFormatter.ofPattern("dd-MM-uuuu"))) {
            try { return LocalDate.parse(x, f); } catch (Exception ignored) { }
        }
        return null;
    }

    private record OriginalLine(int id, double quantity, double lineTotal, double unitCost) { }
    private record ReturnOrigin(String type, String invoice) { }
}
