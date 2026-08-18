package org.example.server.quotation;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.util.BusinessClock;
import org.example.server.operations.BusinessOperationsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class QuotationService {
    private final JpaNativeRepository jdbc;
    private final BusinessOperationsService operations;

    public QuotationService(JpaNativeRepository jdbc, BusinessOperationsService operations) {
        this.jdbc = jdbc;
        this.operations = operations;
    }

    @Transactional
    public List<QuotationDtos.QuoteDto> list() {
        jdbc.update(
                "UPDATE quotation_header SET status='EXPIRED' " +
                "WHERE status NOT IN ('ACCEPTED','REJECTED','DELETED') AND valid_until IS NOT NULL AND CAST(valid_until AS DATE) < ?",
                BusinessClock.today());
        return jdbc.query(
                "SELECT q.id,q.customer_id,q.quotation_no,CAST(q.quotation_date AS text),p.name," +
                "COALESCE(CAST(q.valid_until AS text),''),COALESCE(q.status,''),COALESCE(CAST(q.follow_up_date AS text),'')," +
                "COALESCE(q.converted_invoice_no,''),COALESCE(q.salesperson,''),COALESCE(q.created_by,'')," +
                "COALESCE(q.total_amount,0),COALESCE(p.phone,''),COALESCE(p.email,''),COALESCE(p.gstin,'')," +
                "COALESCE(q.source,''),COALESCE(q.remarks,''),COALESCE(q.discount_amount,0) " +
                "FROM quotation_header q JOIN party_master p ON p.id=q.customer_id " +
                "WHERE UPPER(COALESCE(q.status,''))<>'DELETED' ORDER BY q.quotation_date DESC,q.id DESC",
                (r, i) -> new QuotationDtos.QuoteDto(r.getInt(1), r.getInt(2), r.getString(3), r.getString(4),
                        r.getString(5), r.getString(6), r.getString(7), r.getString(8), r.getString(9),
                        r.getString(10), r.getString(11), r.getDouble(12), r.getString(13), r.getString(14),
                        r.getString(15), r.getString(16), r.getString(17), r.getDouble(18)));
    }

    @Transactional(readOnly = true)
    public List<QuotationDtos.LineDto> lines(int id) {
        return jdbc.query(
                "SELECT l.item_code,COALESCE(i.description,l.item_code),l.quantity,l.rate,l.gst_percent," +
                "COALESCE(l.discount_percent,0),l.line_total FROM quotation_line l " +
                "LEFT JOIN item_master i ON i.item_code=l.item_code WHERE l.quotation_id=? ORDER BY l.id",
                (r, i) -> new QuotationDtos.LineDto(r.getString(1), r.getString(2), r.getDouble(3), r.getDouble(4),
                        r.getDouble(5), r.getDouble(6), r.getDouble(7)), id);
    }

    @Transactional
    public QuotationDtos.QuoteDto save(QuotationDtos.SaveRequest d) {
        int id;
        if (d.id() == null) {
            String no = nextNo();
            Integer x = jdbc.queryForObject(
                    "INSERT INTO quotation_header(quotation_no,quotation_date,valid_until,customer_id,subtotal," +
                    "discount_amount,gst_amount,total_amount,status,remarks,follow_up_date,salesperson,source,created_by,created_at) " +
                    "VALUES(?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?) RETURNING id",
                    Integer.class, no, date(d.date()), date(d.valid()), d.customerId(), d.subtotal(),
                    d.discountAmount(), d.gstAmount(), d.total(), d.remarks(), date(d.followUp()),
                    d.salesperson(), d.source(), CurrentUser.require().username(), BusinessClock.nowUtcText());
            id = x == null ? 0 : x;
        } else {
            id = d.id();
            Map<String, Object> existing = jdbc.queryForMap(
                    "SELECT COALESCE(converted_invoice_no,'') converted,COALESCE(status,'DRAFT') status FROM quotation_header WHERE id=? FOR UPDATE",
                    id);
            String existingStatus = Objects.toString(existing.get("status"), "DRAFT").trim().toUpperCase(Locale.ROOT);
            if ("DELETED".equals(existingStatus)) {
                throw new IllegalStateException("Deleted quotations are read-only.");
            }
            if (!Objects.toString(existing.get("converted"), "").isBlank()) {
                throw new IllegalStateException("Converted quotations are read-only. Duplicate the quotation if a new revision is required.");
            }
            jdbc.update(
                    "UPDATE quotation_header SET quotation_date=?,valid_until=?,customer_id=?,subtotal=?,discount_amount=?," +
                    "gst_amount=?,total_amount=?,remarks=?,follow_up_date=?,salesperson=?,source=? WHERE id=?",
                    date(d.date()), date(d.valid()), d.customerId(), d.subtotal(), d.discountAmount(), d.gstAmount(),
                    d.total(), d.remarks(), date(d.followUp()), d.salesperson(), d.source(), id);
            jdbc.update("DELETE FROM quotation_line WHERE quotation_id=?", id);
        }
        if (d.lines() != null) {
            for (var l : d.lines()) {
                jdbc.update("INSERT INTO quotation_line(quotation_id,item_code,quantity,rate,gst_percent,discount_percent,line_total) VALUES(?,?,?,?,?,?,?)",
                        id, l.code(), l.quantity(), l.rate(), l.gst(), l.discount(), l.total());
            }
        }
        int savedId = id;
        return list().stream().filter(q -> q.id() == savedId).findFirst().orElseThrow();
    }

    @Transactional
    public void notes(int id, String v) {
        requireEditable(id);
        jdbc.update("UPDATE quotation_header SET remarks=? WHERE id=?", v, id);
    }

    @Transactional
    public void markSent(int id, String ch) {
        String col = "WHATSAPP".equalsIgnoreCase(ch) ? "whatsapp_sent" : "email_sent";
        jdbc.update("UPDATE quotation_header SET " + col + "=1,status=CASE WHEN status='DRAFT' THEN 'SENT' ELSE status END WHERE id=?", id);
    }

    @Transactional
    public void followUp(int id, QuotationDtos.FollowUp d) {
        requireEditable(id);
        var q = jdbc.queryForMap("SELECT quotation_no,customer_id FROM quotation_header WHERE id=?", id);
        String customer = jdbc.queryForObject("SELECT name FROM party_master WHERE id=?", String.class, q.get("customer_id"));
        String followUp = date(d.date()) == null ? null : date(d.date()).toString();
        jdbc.update("UPDATE quotation_header SET follow_up_date=? WHERE id=?", followUp, id);
        jdbc.update("INSERT INTO reminder_register(title,reference_no,due_date,priority,notes,status,created_by,created_at,updated_at) VALUES(?,?,?,?,?,'OPEN',?,?,?)",
                "Quotation follow-up: " + customer, q.get("quotation_no"), followUp, "NORMAL", d.notes(),
                CurrentUser.require().username(), BusinessClock.nowUtcText(), BusinessClock.nowUtcText());
    }

    @Transactional
    public String convert(int id, String ignoredUser) {
        Map<String, Object> q = jdbc.queryForMap("SELECT * FROM quotation_header WHERE id=? FOR UPDATE", id);
        String existing = Objects.toString(q.get("converted_invoice_no"), "");
        if (!existing.isBlank()) return existing;
        String status = Objects.toString(q.get("status"), "DRAFT").trim().toUpperCase(Locale.ROOT);
        if (Set.of("REJECTED", "EXPIRED", "DELETED").contains(status)) {
            throw new IllegalStateException("A " + status.toLowerCase(Locale.ROOT) + " quotation cannot be converted to a Sale.");
        }

        LocalDate today = BusinessClock.today();
        String invoice = nextSale();
        Integer sid = jdbc.queryForObject(
                "INSERT INTO sales_header(invoice_no,invoice_date,customer_id,subtotal,gst_amount,total_amount,remarks,created_at," +
                "email_sent,due_date,paid_amount,payment_status,whatsapp_sent,invoice_type,salesperson,source,notes) " +
                "VALUES(?,?,?,?,?,?,?,?,0,?,0,'PENDING',0,'TAX INVOICE',?,?,?) RETURNING id",
                Integer.class, invoice, today, q.get("customer_id"), q.get("subtotal"), q.get("gst_amount"), q.get("total_amount"),
                "Converted from " + q.get("quotation_no"), BusinessClock.nowUtcText(), today.plusDays(30),
                q.get("salesperson"), q.get("source"), q.get("remarks"));
        for (var l : lines(id)) {
            if (!Double.isFinite(l.quantity()) || l.quantity() <= 0) {
                throw new IllegalArgumentException("Quotation quantity must be a finite number greater than zero.");
            }
            if (jdbc.update("UPDATE item_master SET opening_stock=COALESCE(opening_stock,0)-? WHERE item_code=? AND COALESCE(opening_stock,0)>=?",
                    l.quantity(), l.code(), l.quantity()) != 1) {
                throw new IllegalStateException("Insufficient stock for " + l.code() + ". Refresh and try again.");
            }
            jdbc.update("INSERT INTO sales_line(sales_id,item_code,quantity,rate,gst_percent,line_total) VALUES(?,?,?,?,?,?)",
                    sid, l.code(), l.quantity(), l.rate() * (1 - l.discount() / 100.0), l.gst(), l.total());
        }
        jdbc.update("UPDATE quotation_header SET status='ACCEPTED',converted_invoice_no=? WHERE id=?", invoice, id);
        activity(id, "CONVERTED", invoice, ignoredUser);
        return invoice;
    }

    @Transactional
    public String duplicate(int id, String ignoredUser) {
        Map<String, Object> q = jdbc.queryForMap("SELECT * FROM quotation_header WHERE id=?", id);
        String no = nextNo();
        LocalDate today = BusinessClock.today();
        Integer nid = jdbc.queryForObject(
                "INSERT INTO quotation_header(quotation_no,quotation_date,valid_until,customer_id,subtotal,discount_amount,gst_amount," +
                "total_amount,status,remarks,follow_up_date,salesperson,source,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?) RETURNING id",
                Integer.class, no, today, today.plusDays(30), q.get("customer_id"), q.get("subtotal"), q.get("discount_amount"),
                q.get("gst_amount"), q.get("total_amount"), q.get("remarks"), today.plusDays(7), q.get("salesperson"),
                q.get("source"), CurrentUser.require().username(), BusinessClock.nowUtcText());
        for (var l : lines(id)) {
            jdbc.update("INSERT INTO quotation_line(quotation_id,item_code,quantity,rate,gst_percent,discount_percent,line_total) VALUES(?,?,?,?,?,?,?)",
                    nid, l.code(), l.quantity(), l.rate(), l.gst(), l.discount(), l.total());
        }
        return no;
    }

    @Transactional
    public void delete(int id) {
        Map<String, Object> current = jdbc.queryForMap(
                "SELECT COALESCE(status,'DRAFT') status,COALESCE(converted_invoice_no,'') converted FROM quotation_header WHERE id=? FOR UPDATE",
                id);
        String status = Objects.toString(current.get("status"), "DRAFT").trim().toUpperCase(Locale.ROOT);
        if ("DELETED".equals(status)) return;
        if (!Objects.toString(current.get("converted"), "").isBlank()) {
            throw new IllegalStateException("A converted quotation cannot be deleted because it is linked to a Sales invoice.");
        }
        jdbc.update("UPDATE quotation_header SET status='DELETED' WHERE id=?", id);
        activity(id, "DELETED", "Quotation soft-deleted", null);
    }


    private void requireEditable(int id) {
        Map<String, Object> state = jdbc.queryForMap(
                "SELECT COALESCE(status,'DRAFT') status,COALESCE(converted_invoice_no,'') converted FROM quotation_header WHERE id=? FOR UPDATE",
                id);
        String status = Objects.toString(state.get("status"), "DRAFT").trim().toUpperCase(Locale.ROOT);
        if ("DELETED".equals(status)) throw new IllegalStateException("Deleted quotations are read-only.");
        if (!Objects.toString(state.get("converted"), "").isBlank())
            throw new IllegalStateException("Converted quotations are read-only. Duplicate the quotation if a new revision is required.");
    }

    private void activity(int id, String action, String detail, String ignoredUser) {
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('QUOTATION',?,?,?,?,?)",
                id, action, detail, CurrentUser.require().username(), BusinessClock.nowUtcText());
    }

    private String nextNo() {
        List<String> existing = jdbc.query("SELECT quotation_no FROM quotation_header WHERE quotation_no IS NOT NULL", (r, i) -> r.getString(1));
        return operations.nextConfiguredReference("REF_QUOTATION", "QT-YYYY-XXXX", existing);
    }

    /** Quotation conversion uses the same Master Data Sales numbering as Create Sale. */
    private String nextSale() {
        return operations.nextSalesInvoice();
    }

    private LocalDate date(String v) { return v == null || v.isBlank() ? null : LocalDate.parse(v); }
}
