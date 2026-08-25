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
                "WHERE status NOT IN ('ACCEPTED','REJECTED','DELETED') AND NULLIF(TRIM(valid_until),'') IS NOT NULL AND CAST(NULLIF(TRIM(valid_until),'') AS DATE) < ?",
                BusinessClock.today());
        return jdbc.query(
                "SELECT q.id,q.customer_id,q.quotation_no,CAST(q.quotation_date AS text),p.name," +
                "COALESCE(CAST(q.valid_until AS text),''),COALESCE(q.status,''),COALESCE(CAST(q.follow_up_date AS text),'')," +
                "COALESCE(q.converted_invoice_no,''),COALESCE(q.salesperson,''),COALESCE(q.created_by,'')," +
                "COALESCE(q.total_amount,0),COALESCE(p.phone,''),COALESCE(p.email,''),COALESCE(p.gstin,'')," +
                "COALESCE(q.source,''),COALESCE(q.remarks,''),COALESCE(q.discount_amount,0),COALESCE(q.attachment_path,'') " +
                "FROM quotation_header q JOIN party_master p ON p.id=q.customer_id " +
                "WHERE UPPER(COALESCE(q.status,''))<>'DELETED' ORDER BY q.quotation_date DESC,q.id DESC",
                (r, i) -> new QuotationDtos.QuoteDto(r.getInt(1), r.getInt(2), r.getString(3), r.getString(4),
                        r.getString(5), r.getString(6), r.getString(7), r.getString(8), r.getString(9),
                        r.getString(10), r.getString(11), r.getDouble(12), r.getString(13), r.getString(14),
                        r.getString(15), r.getString(16), r.getString(17), r.getDouble(18), r.getString(19)));
    }

    @Transactional
    public QuotationDtos.Page page(int page,int size,String q,String number,String customer,String status,String from,String to,String valid,String salesperson,String minAmount,String maxAmount,String followUp,String source){
        expireQuotations();int safeSize=Math.max(10,Math.min(size,200)),safePage=Math.max(0,page);List<Object> args=new ArrayList<>();StringBuilder where=new StringBuilder(" WHERE UPPER(COALESCE(q.status,''))<>'DELETED'");
        String query=trim(q),num=trim(number),cust=trim(customer),state=up(status),seller=trim(salesperson),follow=up(followUp),src=trim(source);LocalDate start=dateOrNull(from),end=dateOrNull(to),validDate=dateOrNull(valid);Double min=numberOrNull(minAmount),max=numberOrNull(maxAmount);
        if(query!=null){where.append(" AND LOWER(CONCAT_WS(' ',q.quotation_no,p.name,p.phone,p.email,p.gstin)) LIKE ?");args.add("%"+query.toLowerCase(Locale.ROOT)+"%");}if(num!=null){where.append(" AND LOWER(q.quotation_no) LIKE ?");args.add("%"+num.toLowerCase(Locale.ROOT)+"%");}if(cust!=null&&!cust.toUpperCase(Locale.ROOT).startsWith("ALL")){where.append(" AND p.name=?");args.add(cust);}if(state!=null&&!state.startsWith("ALL")){where.append(" AND UPPER(COALESCE(q.status,''))=?");args.add(state);}if(start!=null){where.append(" AND CAST(NULLIF(TRIM(q.quotation_date),'') AS DATE)>=?");args.add(start);}if(end!=null){where.append(" AND CAST(NULLIF(TRIM(q.quotation_date),'') AS DATE)<=?");args.add(end);}if(validDate!=null){where.append(" AND CAST(NULLIF(TRIM(q.valid_until),'') AS DATE)=?");args.add(validDate);}if(seller!=null&&!seller.toUpperCase(Locale.ROOT).startsWith("ALL")){where.append(" AND COALESCE(q.salesperson,'')=?");args.add(seller);}if(min!=null){where.append(" AND COALESCE(q.total_amount,0)>=?");args.add(min);}if(max!=null){where.append(" AND COALESCE(q.total_amount,0)<=?");args.add(max);}if(src!=null&&!src.toUpperCase(Locale.ROOT).startsWith("ALL")){where.append(" AND UPPER(COALESCE(q.source,''))=UPPER(?)");args.add(src);}if(follow!=null&&!follow.startsWith("ALL")){switch(follow){case "OVERDUE"->{where.append(" AND CAST(NULLIF(TRIM(q.follow_up_date),'') AS DATE)<CURRENT_DATE");}case "TODAY"->{where.append(" AND CAST(NULLIF(TRIM(q.follow_up_date),'') AS DATE)=CURRENT_DATE");}case "NEXT 7 DAYS"->{where.append(" AND CAST(NULLIF(TRIM(q.follow_up_date),'') AS DATE) BETWEEN CURRENT_DATE AND CURRENT_DATE+7");}case "NOT SET"->{where.append(" AND q.follow_up_date IS NULL");}default->{}}}
        String joins=" FROM quotation_header q JOIN party_master p ON p.id=q.customer_id";Object[] base=args.toArray();long total=jdbc.queryForObject("SELECT COUNT(*)"+joins+where,Long.class,base);Double filtered=jdbc.queryForObject("SELECT COALESCE(SUM(q.total_amount),0)"+joins+where,Double.class,base);int pages=total==0?0:(int)Math.ceil(total/(double)safeSize);if(pages>0&&safePage>=pages)safePage=pages-1;List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(safeSize);pageArgs.add((long)safePage*safeSize);
        List<QuotationDtos.QuoteDto> rows=jdbc.query(quoteSelect()+joins+where+" ORDER BY q.quotation_date DESC,q.id DESC LIMIT ? OFFSET ?",this::mapQuote,pageArgs.toArray());
        List<String> customers=jdbc.query("SELECT DISTINCT p.name"+joins+" WHERE UPPER(COALESCE(q.status,''))<>'DELETED' AND COALESCE(p.name,'')<>'' ORDER BY p.name",(r,i)->r.getString(1));List<String> salespersons=jdbc.query("SELECT DISTINCT q.salesperson FROM quotation_header q WHERE UPPER(COALESCE(q.status,''))<>'DELETED' AND COALESCE(q.salesperson,'')<>'' ORDER BY q.salesperson",(r,i)->r.getString(1));
        return new QuotationDtos.Page(rows,safePage,safeSize,total,pages,filtered==null?0:filtered,metrics(),customers,salespersons);
    }

    @Transactional
    public QuotationDtos.QuoteDto quote(int id){expireQuotations();List<QuotationDtos.QuoteDto> rows=jdbc.query(quoteSelect()+" FROM quotation_header q JOIN party_master p ON p.id=q.customer_id WHERE q.id=? AND UPPER(COALESCE(q.status,''))<>'DELETED'",this::mapQuote,id);if(rows.isEmpty())throw new IllegalArgumentException("Quotation not found.");return rows.getFirst();}

    private void expireQuotations(){jdbc.update("UPDATE quotation_header SET status='EXPIRED' WHERE status NOT IN ('ACCEPTED','REJECTED','DELETED') AND NULLIF(TRIM(valid_until),'') IS NOT NULL AND CAST(NULLIF(TRIM(valid_until),'') AS DATE) < ?",BusinessClock.today());}
    private String quoteSelect(){return "SELECT q.id,q.customer_id,q.quotation_no,CAST(q.quotation_date AS text),p.name,COALESCE(CAST(q.valid_until AS text),''),COALESCE(q.status,''),COALESCE(CAST(q.follow_up_date AS text),''),COALESCE(q.converted_invoice_no,''),COALESCE(q.salesperson,''),COALESCE(q.created_by,''),COALESCE(q.total_amount,0),COALESCE(p.phone,''),COALESCE(p.email,''),COALESCE(p.gstin,''),COALESCE(q.source,''),COALESCE(q.remarks,''),COALESCE(q.discount_amount,0),COALESCE(q.attachment_path,'')";}
    private QuotationDtos.QuoteDto mapQuote(org.example.server.persistence.JpaNativeRepository.NativeRow r,Integer i){return new QuotationDtos.QuoteDto(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getDouble(12),r.getString(13),r.getString(14),r.getString(15),r.getString(16),r.getString(17),r.getDouble(18),r.getString(19));}
    private QuotationDtos.Metrics metrics(){List<Number[]> m=jdbc.query("SELECT COALESCE(SUM(total_amount),0),COUNT(*),COALESCE(SUM(CASE WHEN status IN ('DRAFT','SENT') THEN total_amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN status IN ('DRAFT','SENT') THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN status='ACCEPTED' THEN total_amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN status='ACCEPTED' THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN status='EXPIRED' THEN total_amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN status='EXPIRED' THEN 1 ELSE 0 END),0) FROM quotation_header WHERE UPPER(COALESCE(status,''))<>'DELETED'",(r,i)->new Number[]{(Number)r.getObject(1),(Number)r.getObject(2),(Number)r.getObject(3),(Number)r.getObject(4),(Number)r.getObject(5),(Number)r.getObject(6),(Number)r.getObject(7),(Number)r.getObject(8)});Number[] x=m.getFirst();double total=n(x[0]),pending=n(x[2]),accepted=n(x[4]),expired=n(x[6]);long count=l(x[1]),pc=l(x[3]),ac=l(x[5]),ec=l(x[7]);List<QuotationDtos.MetricPoint> trend=jdbc.query("SELECT TO_CHAR(DATE_TRUNC('month',CAST(NULLIF(TRIM(quotation_date),'') AS DATE)),'YYYY-MM'),COALESCE(SUM(total_amount),0) FROM quotation_header WHERE UPPER(COALESCE(status,''))<>'DELETED' AND CAST(NULLIF(TRIM(quotation_date),'') AS DATE)>=DATE_TRUNC('month',CURRENT_DATE)-INTERVAL '7 months' GROUP BY DATE_TRUNC('month',CAST(NULLIF(TRIM(quotation_date),'') AS DATE)) ORDER BY DATE_TRUNC('month',CAST(NULLIF(TRIM(quotation_date),'') AS DATE))",(r,i)->new QuotationDtos.MetricPoint(r.getString(1),r.getDouble(2)));List<QuotationDtos.MetricPoint> statuses=jdbc.query("SELECT COALESCE(status,'DRAFT'),COUNT(*) FROM quotation_header WHERE UPPER(COALESCE(status,''))<>'DELETED' GROUP BY COALESCE(status,'DRAFT') ORDER BY 1",(r,i)->new QuotationDtos.MetricPoint(r.getString(1),r.getDouble(2)));return new QuotationDtos.Metrics(total,count,pending,pc,accepted,ac,expired,ec,count==0?0:ac*100d/count,count==0?0:total/count,trend,statuses);}
    private static String trim(String v){return v==null||v.isBlank()?null:v.trim();}private static String up(String v){String x=trim(v);return x==null?null:x.toUpperCase(Locale.ROOT);}private static Double numberOrNull(String v){try{return trim(v)==null?null:Double.parseDouble(v.replace(",","").replace("₹","").trim());}catch(Exception e){return null;}}private static LocalDate dateOrNull(String v){try{return trim(v)==null?null:LocalDate.parse(v);}catch(Exception e){return null;}}private static double n(Number v){return v==null?0:v.doubleValue();}private static long l(Number v){return v==null?0:v.longValue();}

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
        return quote(id);
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
        boolean admin = "ADMIN".equalsIgnoreCase(CurrentUser.require().role());
        String documentStatus = admin ? "PENDING" : "PENDING APPROVAL";
        String approvalStatus = admin ? "APPROVED" : "PENDING";
        String actor = CurrentUser.require().username();
        String now = BusinessClock.nowUtcText();
        Integer sid = jdbc.queryForObject(
                "INSERT INTO sales_header(invoice_no,invoice_date,customer_id,subtotal,gst_amount,total_amount,remarks,created_at," +
                "email_sent,due_date,paid_amount,payment_status,whatsapp_sent,invoice_type,salesperson,source,notes,document_status," +
                "requested_document_status,approval_status,approval_requested_by,approval_requested_at,approved_by,approved_at,inventory_posted) " +
                "VALUES(?,?,?,?,?,?,?,?,0,?,0,'PENDING',0,'TAX INVOICE',?,?,?,?,?,?,?,?,?,?,?) RETURNING id",
                Integer.class, invoice, today, q.get("customer_id"), q.get("subtotal"), q.get("gst_amount"), q.get("total_amount"),
                "Converted from " + q.get("quotation_no"), now, today.plusDays(30),
                q.get("salesperson"), q.get("source"), q.get("remarks"), documentStatus, "PENDING", approvalStatus,
                admin ? null : actor, admin ? null : now, admin ? actor : null, admin ? now : null, admin);
        for (var l : lines(id)) {
            if (!Double.isFinite(l.quantity()) || l.quantity() <= 0) {
                throw new IllegalArgumentException("Quotation quantity must be a finite number greater than zero.");
            }
            if (admin) operations.applyStockDelta(l.code(), -l.quantity(), true);
            jdbc.update("INSERT INTO sales_line(sales_id,item_code,quantity,rate,gst_percent,line_total) VALUES(?,?,?,?,?,?)",
                    sid, l.code(), l.quantity(), l.rate() * (1 - l.discount() / 100.0), l.gst(), l.total());
        }
        if (!admin) {
            jdbc.update("INSERT INTO notifications(title,message,severity,category,is_read,target_fxml,reference_no,module_key,record_id,action_code,created_at) VALUES(?,?,?,?,0,?,?,?,?,?,?)",
                    "Sales " + invoice + " • PENDING APPROVAL", invoice + " was created from quotation " + q.get("quotation_no") + " by " + actor + " and is waiting for Admin approval.",
                    "WARNING", "APPROVAL", "/fxml/pages/SalesList.fxml", invoice, "SALE", sid, "APPROVE", System.currentTimeMillis());
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
        return operations.nextConfiguredReference("REF_QUOTATION", "QT-YYYY-XXXX",
                () -> jdbc.query("SELECT quotation_no FROM quotation_header WHERE quotation_no IS NOT NULL", (r, i) -> r.getString(1)));
    }

    /** Quotation conversion uses the same Master Data Sales numbering as Create Sale. */
    private String nextSale() {
        return operations.nextSalesInvoice();
    }

    private LocalDate date(String v) { return v == null || v.isBlank() ? null : LocalDate.parse(v); }
}
