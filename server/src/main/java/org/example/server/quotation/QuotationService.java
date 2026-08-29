package org.example.server.quotation;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.util.BusinessClock;
import org.example.server.operations.BusinessOperationsService;
import org.example.server.master.MasterDataService;
import org.example.shared.DocumentCalculationEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class QuotationService {
    private static final String QUOTATION_SOURCE_CODE="QUOTATION_SOURCE";
    private final JpaNativeRepository jdbc;
    private final BusinessOperationsService operations;
    private final MasterDataService masterData;

    public QuotationService(JpaNativeRepository jdbc, BusinessOperationsService operations, MasterDataService masterData) {
        this.jdbc = jdbc;
        this.operations = operations;
        this.masterData = masterData;
    }

    @Transactional
    public List<QuotationDtos.QuoteDto> list() {
        CurrentUser.requirePermission("QUOTATION.VIEW","View quotations");
        jdbc.update(
                "UPDATE quotation_header SET status='EXPIRED' " +
                "WHERE status NOT IN ('ACCEPTED','REJECTED','DELETED') AND NULLIF(TRIM(valid_until),'') IS NOT NULL AND dse_safe_date(valid_until) < ?",
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
        CurrentUser.requirePermission("QUOTATION.VIEW","View quotations");expireQuotations();int safeSize=Math.max(10,Math.min(size,200)),safePage=Math.max(0,page);List<Object> args=new ArrayList<>();StringBuilder where=new StringBuilder(" WHERE UPPER(COALESCE(q.status,''))<>'DELETED'");
        String query=trim(q),num=trim(number),cust=trim(customer),state=up(status),seller=trim(salesperson),follow=up(followUp),src=trim(source);LocalDate start=dateOrNull(from),end=dateOrNull(to),validDate=dateOrNull(valid);Double min=numberOrNull(minAmount),max=numberOrNull(maxAmount);
        if(query!=null){where.append(" AND LOWER(CONCAT_WS(' ',q.quotation_no,p.name,p.phone,p.email,p.gstin)) LIKE ?");args.add("%"+query.toLowerCase(Locale.ROOT)+"%");}if(num!=null){where.append(" AND LOWER(q.quotation_no) LIKE ?");args.add("%"+num.toLowerCase(Locale.ROOT)+"%");}if(cust!=null&&!cust.toUpperCase(Locale.ROOT).startsWith("ALL")){where.append(" AND p.name=?");args.add(cust);}if(state!=null&&!state.startsWith("ALL")){where.append(" AND UPPER(COALESCE(q.status,''))=?");args.add(state);}if(start!=null){where.append(" AND dse_safe_date(q.quotation_date)>=?");args.add(start);}if(end!=null){where.append(" AND dse_safe_date(q.quotation_date)<=?");args.add(end);}if(validDate!=null){where.append(" AND dse_safe_date(q.valid_until)=?");args.add(validDate);}if(seller!=null&&!seller.toUpperCase(Locale.ROOT).startsWith("ALL")){where.append(" AND COALESCE(q.salesperson,'')=?");args.add(seller);}if(min!=null){where.append(" AND COALESCE(q.total_amount,0)>=?");args.add(min);}if(max!=null){where.append(" AND COALESCE(q.total_amount,0)<=?");args.add(max);}if(src!=null&&!src.toUpperCase(Locale.ROOT).startsWith("ALL")){where.append(" AND UPPER(COALESCE(q.source,''))=UPPER(?)");args.add(src);}if(follow!=null&&!follow.startsWith("ALL")){switch(follow){case "OVERDUE"->{where.append(" AND dse_safe_date(q.follow_up_date)<CURRENT_DATE");}case "TODAY"->{where.append(" AND dse_safe_date(q.follow_up_date)=CURRENT_DATE");}case "NEXT 7 DAYS"->{where.append(" AND dse_safe_date(q.follow_up_date) BETWEEN CURRENT_DATE AND CURRENT_DATE+7");}case "NOT SET"->{where.append(" AND q.follow_up_date IS NULL");}default->{}}}
        String joins=" FROM quotation_header q JOIN party_master p ON p.id=q.customer_id";Object[] base=args.toArray();long total=jdbc.queryForObject("SELECT COUNT(*)"+joins+where,Long.class,base);Double filtered=jdbc.queryForObject("SELECT COALESCE(SUM(q.total_amount),0)"+joins+where,Double.class,base);int pages=total==0?0:(int)Math.ceil(total/(double)safeSize);if(pages>0&&safePage>=pages)safePage=pages-1;List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(safeSize);pageArgs.add((long)safePage*safeSize);
        List<QuotationDtos.QuoteDto> rows=jdbc.query(quoteSelect()+joins+where+" ORDER BY q.quotation_date DESC,q.id DESC LIMIT ? OFFSET ?",this::mapQuote,pageArgs.toArray());
        List<String> customers=jdbc.query("SELECT DISTINCT p.name"+joins+" WHERE UPPER(COALESCE(q.status,''))<>'DELETED' AND COALESCE(p.name,'')<>'' ORDER BY p.name LIMIT 40",(r,i)->r.getString(1));List<String> salespersons=jdbc.query("SELECT DISTINCT q.salesperson FROM quotation_header q WHERE UPPER(COALESCE(q.status,''))<>'DELETED' AND COALESCE(q.salesperson,'')<>'' ORDER BY q.salesperson",(r,i)->r.getString(1));
        return new QuotationDtos.Page(rows,safePage,safeSize,total,pages,filtered==null?0:filtered,metrics(joins,where.toString(),base),customers,salespersons);
    }

    @Transactional
    public QuotationDtos.QuoteDto quote(int id){CurrentUser.requirePermission("QUOTATION.VIEW","View quotation");expireQuotations();List<QuotationDtos.QuoteDto> rows=jdbc.query(quoteSelect()+" FROM quotation_header q JOIN party_master p ON p.id=q.customer_id WHERE q.id=? AND UPPER(COALESCE(q.status,''))<>'DELETED'",this::mapQuote,id);if(rows.isEmpty())throw new IllegalArgumentException("Quotation not found.");return rows.getFirst();}

    private void expireQuotations(){jdbc.update("UPDATE quotation_header SET status='EXPIRED' WHERE status NOT IN ('ACCEPTED','REJECTED','DELETED') AND NULLIF(TRIM(valid_until),'') IS NOT NULL AND dse_safe_date(valid_until) < ?",BusinessClock.today());}
    private String quoteSelect(){return "SELECT q.id,q.customer_id,q.quotation_no,CAST(q.quotation_date AS text),p.name,COALESCE(CAST(q.valid_until AS text),''),COALESCE(q.status,''),COALESCE(CAST(q.follow_up_date AS text),''),COALESCE(q.converted_invoice_no,''),COALESCE(q.salesperson,''),COALESCE(q.created_by,''),COALESCE(q.total_amount,0),COALESCE(p.phone,''),COALESCE(p.email,''),COALESCE(p.gstin,''),COALESCE(q.source,''),COALESCE(q.remarks,''),COALESCE(q.discount_amount,0),COALESCE(q.attachment_path,'')";}
    private QuotationDtos.QuoteDto mapQuote(org.example.server.persistence.JpaNativeRepository.NativeRow r,Integer i){return new QuotationDtos.QuoteDto(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getDouble(12),r.getString(13),r.getString(14),r.getString(15),r.getString(16),r.getString(17),r.getDouble(18),r.getString(19));}
    private QuotationDtos.Metrics metrics(String joins,String where,Object[] args){
        String state="COALESCE(NULLIF(UPPER(TRIM(q.status)),''),'DRAFT')";
        List<Number[]> m=jdbc.query("SELECT COALESCE(SUM(q.total_amount),0),COUNT(*),COALESCE(SUM(CASE WHEN "+state+" IN ('DRAFT','SENT') THEN q.total_amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN "+state+" IN ('DRAFT','SENT') THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN "+state+"='ACCEPTED' THEN q.total_amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN "+state+"='ACCEPTED' THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN "+state+"='EXPIRED' THEN q.total_amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN "+state+"='EXPIRED' THEN 1 ELSE 0 END),0)"+joins+where,(r,i)->new Number[]{(Number)r.getObject(1),(Number)r.getObject(2),(Number)r.getObject(3),(Number)r.getObject(4),(Number)r.getObject(5),(Number)r.getObject(6),(Number)r.getObject(7),(Number)r.getObject(8)},args);
        Number[] x=m.getFirst();double total=n(x[0]),pending=n(x[2]),accepted=n(x[4]),expired=n(x[6]);long count=l(x[1]),pc=l(x[3]),ac=l(x[5]),ec=l(x[7]);
        List<QuotationDtos.MetricPoint> trend=jdbc.query("SELECT TO_CHAR(DATE_TRUNC('month',dse_safe_date(q.quotation_date)),'YYYY-MM'),COALESCE(SUM(q.total_amount),0)"+joins+where+" AND dse_safe_date(q.quotation_date)>=DATE_TRUNC('month',CURRENT_DATE)-INTERVAL '7 months' GROUP BY DATE_TRUNC('month',dse_safe_date(q.quotation_date)) ORDER BY DATE_TRUNC('month',dse_safe_date(q.quotation_date))",(r,i)->new QuotationDtos.MetricPoint(r.getString(1),r.getDouble(2)),args);
        List<QuotationDtos.MetricPoint> statuses=jdbc.query("SELECT "+state+",COUNT(*)"+joins+where+" GROUP BY "+state+" ORDER BY 1",(r,i)->new QuotationDtos.MetricPoint(r.getString(1),r.getDouble(2)),args);
        return new QuotationDtos.Metrics(total,count,pending,pc,accepted,ac,expired,ec,count==0?0:ac*100d/count,count==0?0:total/count,trend,statuses);
    }
    private static String trim(String v){return v==null||v.isBlank()?null:v.trim();}private static String up(String v){String x=trim(v);return x==null?null:x.toUpperCase(Locale.ROOT);}private static Double numberOrNull(String v){try{return trim(v)==null?null:Double.parseDouble(v.replace(",","").replace("₹","").trim());}catch(Exception e){return null;}}private static LocalDate dateOrNull(String v){try{return trim(v)==null?null:LocalDate.parse(v);}catch(Exception e){return null;}}private static double n(Number v){return v==null?0:v.doubleValue();}private static long l(Number v){return v==null?0:v.longValue();}

    @Transactional
    public QuotationDtos.EditorBootstrapDto editorBootstrap(Integer id) {
        if (!(CurrentUser.hasPermission("QUOTATION.VIEW") || CurrentUser.hasPermission("QUOTATION.CREATE") || CurrentUser.hasPermission("QUOTATION.EDIT")))
            throw new SecurityException("Quotation editor requires Quotation access");
        QuotationDtos.QuoteDto quote = null;
        List<QuotationDtos.LineDto> lines = List.of();
        int selectedCustomerId = 0;
        if (id != null) {
            quote = quote(id);
            selectedCustomerId = quote.customerId();
            lines = loadLines(id);
        }
        int keepCustomerId = selectedCustomerId;
        List<QuotationDtos.CustomerChoiceDto> customers = jdbc.query(
                "SELECT id,COALESCE(party_code,''),COALESCE(name,'') FROM party_master " +
                "WHERE UPPER(COALESCE(party_type,''))='CUSTOMER' AND (COALESCE(is_active,1)<>0 OR id=?) " +
                "ORDER BY LOWER(COALESCE(name,'')),LOWER(COALESCE(party_code,''))",
                (r,i)->new QuotationDtos.CustomerChoiceDto(r.getInt(1),r.getString(2),r.getString(3)), keepCustomerId);
        return new QuotationDtos.EditorBootstrapDto(List.copyOf(customers), activeQuotationSources(), quote, List.copyOf(lines));
    }

    @Transactional(readOnly = true)
    public List<QuotationDtos.LineDto> lines(int id) {
        CurrentUser.requirePermission("QUOTATION.VIEW","View quotation");
        return loadLines(id);
    }

    private List<QuotationDtos.LineDto> loadLines(int id) {
        return jdbc.query(
                "SELECT l.item_code,COALESCE(NULLIF(TRIM(l.item_description_snapshot),''),i.description,l.item_code),l.quantity,l.rate,l.gst_percent," +
                "COALESCE(l.discount_percent,0),l.line_total FROM quotation_line l " +
                "LEFT JOIN item_master i ON i.item_code=l.item_code WHERE l.quotation_id=? ORDER BY l.id",
                (r, i) -> new QuotationDtos.LineDto(r.getString(1), r.getString(2), r.getDouble(3), r.getDouble(4),
                        r.getDouble(5), r.getDouble(6), r.getDouble(7)), id);
    }

    @Transactional
    public List<String> sourceChoices(){
        if(!(CurrentUser.hasPermission("QUOTATION.VIEW")||CurrentUser.hasPermission("QUOTATION.CREATE")||CurrentUser.hasPermission("QUOTATION.EDIT")))
            throw new SecurityException("Quotation Source requires Quotation access");
        return activeQuotationSources();
    }

    /** Backward-compatible quotation endpoint; values come from the same generic Master service used by desktop Master-backed fields. */
    private List<String> activeQuotationSources(){
        return masterData.valuesByCategoryCode(QUOTATION_SOURCE_CODE).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v->!v.isBlank())
                .distinct()
                .toList();
    }

    @Transactional(readOnly=true)
    public List<QuotationDtos.ItemChoiceDto> itemChoices(String query,int limit){
        if(!(CurrentUser.hasPermission("QUOTATION.CREATE")||CurrentUser.hasPermission("QUOTATION.EDIT")))
            throw new SecurityException("Quotation item search requires QUOTATION.CREATE or QUOTATION.EDIT permission");
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);int safeLimit=Math.max(1,Math.min(limit,50));
        String pattern="%"+q+"%";
        return jdbc.query("SELECT COALESCE(item_code,''),COALESCE(description,''),COALESCE(remarks,''),COALESCE(hsn,''),COALESCE(selling_price,0),COALESCE(gst,0),COALESCE(discount_percent,0) FROM item_master WHERE COALESCE(is_active,1)<>0 AND (?='' OR LOWER(CONCAT_WS(' ',COALESCE(item_code,''),COALESCE(description,''),COALESCE(remarks,''),COALESCE(hsn,''))) LIKE ?) ORDER BY COALESCE(description,''),COALESCE(item_code,'') LIMIT ?",
            (r,i)->new QuotationDtos.ItemChoiceDto(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getDouble(5),r.getDouble(6),r.getDouble(7)),q,pattern,safeLimit);
    }

    @Transactional
    public QuotationDtos.QuoteDto save(QuotationDtos.SaveRequest d) {
        if(d==null)throw new IllegalArgumentException("Quotation data is required");
        boolean create=d.id()==null;
        CurrentUser.requirePermission("QUOTATION.VIEW","Quotation access");
        CurrentUser.requirePermission(create?"QUOTATION.CREATE":"QUOTATION.EDIT",create?"Create quotation":"Edit quotation");
        QuoteCalculation calc=calculate(d.lines());
        LocalDate quotationDate=requireDate(d.date(),"Quotation date");
        LocalDate validUntil=requireDate(d.valid(),"Valid until");
        if(validUntil.isBefore(quotationDate))throw new IllegalArgumentException("Quotation valid-until date cannot be before quotation date.");
        String source=requireQuotationSource(d.source());
        int id;
        if(create){requireCustomerReference(d.customerId(),true);requireActiveQuotationItems(d.lines(),Set.of());}
        if (create) {
            String no = nextNo();
            Integer x = jdbc.queryForObject(
                    "INSERT INTO quotation_header(quotation_no,quotation_date,valid_until,customer_id,subtotal," +
                    "discount_amount,gst_amount,total_amount,status,remarks,follow_up_date,salesperson,source,created_by,created_at) " +
                    "VALUES(?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?) RETURNING id",
                    Integer.class, no, quotationDate, validUntil, d.customerId(), calc.subtotal(),
                    calc.discount(), calc.tax(), calc.total(), d.remarks(), date(d.followUp()),
                    d.salesperson(), source, CurrentUser.require().username(), BusinessClock.nowUtcText());
            id = x == null ? 0 : x;
        } else {
            id = d.id();
            Map<String, Object> existing = jdbc.queryForMap(
                    "SELECT COALESCE(converted_invoice_no,'') converted,COALESCE(status,'DRAFT') status,customer_id FROM quotation_header WHERE id=? FOR UPDATE",
                    id);
            String existingStatus = Objects.toString(existing.get("status"), "DRAFT").trim().toUpperCase(Locale.ROOT);
            if ("DELETED".equals(existingStatus)) throw new IllegalStateException("Deleted quotations are read-only.");
            if (!Objects.toString(existing.get("converted"), "").isBlank())
                throw new IllegalStateException("Converted quotations are read-only. Duplicate the quotation if a new revision is required.");
            int existingCustomer=((Number)existing.get("customer_id")).intValue();
            requireCustomerReference(d.customerId(),existingCustomer!=d.customerId());
            requireActiveQuotationItems(d.lines(),quotationItemCodes(id));
            jdbc.update(
                    "UPDATE quotation_header SET quotation_date=?,valid_until=?,customer_id=?,subtotal=?,discount_amount=?," +
                    "gst_amount=?,total_amount=?,remarks=?,follow_up_date=?,salesperson=?,source=? WHERE id=?",
                    quotationDate, validUntil, d.customerId(), calc.subtotal(), calc.discount(), calc.tax(),
                    calc.total(), d.remarks(), date(d.followUp()), d.salesperson(), source, id);
            jdbc.update("DELETE FROM quotation_line WHERE quotation_id=?", id);
        }
        for (var l : calc.lines()) {
            jdbc.update("INSERT INTO quotation_line(quotation_id,item_code,quantity,rate,gst_percent,discount_percent,line_total,item_description_snapshot) VALUES(?,?,?,?,?,?,?,?)",
                    id,l.code(),l.quantity(),l.rate(),l.gst(),l.discount(),l.total(),l.description());
        }
        return quote(id);
    }

    private void requireCustomerReference(int customerId,boolean requireActive){
        List<int[]> rows=jdbc.query("SELECT CASE WHEN UPPER(COALESCE(party_type,''))='CUSTOMER' THEN 1 ELSE 0 END,CASE WHEN COALESCE(is_active,1)<>0 THEN 1 ELSE 0 END FROM party_master WHERE id=?",(r,i)->new int[]{r.getInt(1),r.getInt(2)},customerId);
        if(rows.isEmpty()||rows.getFirst()[0]==0)throw new IllegalArgumentException("Quotation customer must reference a CUSTOMER Master record.");
        if(requireActive&&rows.getFirst()[1]==0)throw new IllegalArgumentException("Quotation customer is inactive. Reactivate it in Customer Master before using it on a new quotation or Sale.");
    }

    private Set<String> quotationItemCodes(int quotationId){return new HashSet<>(jdbc.query("SELECT DISTINCT UPPER(TRIM(item_code)) FROM quotation_line WHERE quotation_id=? AND COALESCE(item_code,'')<>''",(r,i)->r.getString(1),quotationId));}

    private void requireActiveQuotationItems(List<QuotationDtos.LineDto> lines,Set<String> historicalCodes){
        Set<String> checked=new HashSet<>();Set<String> historical=historicalCodes==null?Set.of():historicalCodes;
        for(var line:lines==null?List.<QuotationDtos.LineDto>of():lines){if(line==null||line.code()==null||line.code().isBlank())continue;String code=line.code().trim().toUpperCase(Locale.ROOT);if(!checked.add(code)||historical.contains(code))continue;Long count=jdbc.queryForObject("SELECT COUNT(*) FROM item_master WHERE UPPER(TRIM(item_code))=? AND COALESCE(is_active,1)<>0",Long.class,code);if(count==null||count==0)throw new IllegalArgumentException("Quotation item "+line.code()+" is inactive or missing. Reactivate it in Item Master before using it on a new or changed quotation.");}
    }

    @Transactional
    public void notes(int id, String v) {
        CurrentUser.requirePermission("QUOTATION.EDIT","Edit quotation notes");requireEditable(id);
        jdbc.update("UPDATE quotation_header SET remarks=? WHERE id=?", v, id);
    }

    @Transactional
    public void markSent(int id, String ch) {
        CurrentUser.requirePermission("QUOTATION.EDIT","Update quotation delivery status");requireEditable(id);
        String col = "WHATSAPP".equalsIgnoreCase(ch) ? "whatsapp_sent" : "email_sent";
        jdbc.update("UPDATE quotation_header SET " + col + "=1,status=CASE WHEN status='DRAFT' THEN 'SENT' ELSE status END WHERE id=?", id);
    }

    @Transactional
    public void followUp(int id, QuotationDtos.FollowUp d) {
        CurrentUser.requirePermission("QUOTATION.EDIT","Update quotation follow-up");requireEditable(id);
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
        CurrentUser.requirePermission("QUOTATION.EDIT","Convert quotation");CurrentUser.requirePermission("SALES.CREATE","Create Sale from quotation");
        Map<String, Object> q = jdbc.queryForMap("SELECT * FROM quotation_header WHERE id=? FOR UPDATE", id);
        String existing = Objects.toString(q.get("converted_invoice_no"), "");
        if (!existing.isBlank()) return existing;
        String status = Objects.toString(q.get("status"), "DRAFT").trim().toUpperCase(Locale.ROOT);
        if (Set.of("REJECTED", "EXPIRED", "DELETED").contains(status)) {
            throw new IllegalStateException("A " + status.toLowerCase(Locale.ROOT) + " quotation cannot be converted to a Sale.");
        }

        requireCustomerReference(((Number)q.get("customer_id")).intValue(),true);
        List<QuotationDtos.LineDto> conversionLines=loadLines(id);
        requireActiveQuotationItems(conversionLines,Set.of());
        LocalDate today = BusinessClock.today();
        String invoice = nextSale();
        boolean admin = "ADMIN".equalsIgnoreCase(CurrentUser.require().role());
        String documentStatus = admin ? "PENDING" : "PENDING APPROVAL";
        String approvalStatus = admin ? "APPROVED" : "PENDING";
        String actor = CurrentUser.require().username();
        String now = BusinessClock.nowUtcText();
        Integer sid = jdbc.queryForObject(
                "INSERT INTO sales_header(invoice_no,invoice_date,customer_id,subtotal,discount_amount,gst_amount,total_amount,remarks,created_at," +
                "email_sent,due_date,paid_amount,payment_status,whatsapp_sent,invoice_type,salesperson,source,notes,document_status," +
                "requested_document_status,approval_status,approval_requested_by,approval_requested_at,approved_by,approved_at,inventory_posted) " +
                "VALUES(?,?,?,?,?,?,?,?,?,0,?,0,'PENDING',0,'TAX INVOICE',?,?,?,?,?,?,?,?,?,?,?) RETURNING id",
                Integer.class, invoice, today, q.get("customer_id"), q.get("subtotal"), q.get("discount_amount"), q.get("gst_amount"), q.get("total_amount"),
                "Converted from " + q.get("quotation_no"), now, today.plusDays(30),
                q.get("salesperson"), q.get("source"), q.get("remarks"), documentStatus, "PENDING", approvalStatus,
                admin ? null : actor, admin ? null : now, admin ? actor : null, admin ? now : null, admin);
        jdbc.update("UPDATE sales_header h SET customer_name_snapshot=p.name,customer_email_snapshot=p.email,customer_phone_snapshot=p.phone,customer_gstin_snapshot=p.gstin,customer_address_snapshot=p.address FROM party_master p WHERE h.id=? AND p.id=h.customer_id",sid);
        for (var l : conversionLines) {
            if (!Double.isFinite(l.quantity()) || l.quantity() <= 0) {
                throw new IllegalArgumentException("Quotation quantity must be a finite number greater than zero.");
            }
            Double unitCost=jdbc.queryForObject("SELECT COALESCE((SELECT average_unit_cost FROM inventory_cost_state WHERE item_code=?),(SELECT purchase_price FROM item_master WHERE item_code=?),0)",Double.class,l.code(),l.code());
            if (admin) operations.applyStockMovement(l.code(), -l.quantity(), true, unitCost==null?0:unitCost, "SALE", sid);
            DocumentCalculationEngine.LineResult converted=DocumentCalculationEngine.line(l.quantity(),l.rate(),l.discount(),l.gst());
            jdbc.update("INSERT INTO sales_line(sales_id,item_code,quantity,rate,discount_percent,discount_amount,gst_percent,line_total,item_description_snapshot,hsn_snapshot,unit_snapshot,item_remarks_snapshot,unit_cost_snapshot) SELECT ?,?,?,?,?,?,?,?,description,hsn,unit,remarks,? FROM item_master WHERE item_code=?",
                    sid,l.code(),l.quantity(),l.rate(),l.discount(),converted.discountAmount(),l.gst(),converted.totalAmount(),unitCost==null?0:unitCost,l.code());
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
        CurrentUser.requirePermission("QUOTATION.CREATE","Duplicate quotation");
        Map<String, Object> q = jdbc.queryForMap("SELECT * FROM quotation_header WHERE id=?", id);
        requireCustomerReference(((Number)q.get("customer_id")).intValue(),true);
        List<QuotationDtos.LineDto> duplicateLines=loadLines(id);
        requireActiveQuotationItems(duplicateLines,Set.of());
        String no = nextNo();
        LocalDate today = BusinessClock.today();
        Integer nid = jdbc.queryForObject(
                "INSERT INTO quotation_header(quotation_no,quotation_date,valid_until,customer_id,subtotal,discount_amount,gst_amount," +
                "total_amount,status,remarks,follow_up_date,salesperson,source,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?) RETURNING id",
                Integer.class, no, today, today.plusDays(30), q.get("customer_id"), q.get("subtotal"), q.get("discount_amount"),
                q.get("gst_amount"), q.get("total_amount"), q.get("remarks"), today.plusDays(7), q.get("salesperson"),
                q.get("source"), CurrentUser.require().username(), BusinessClock.nowUtcText());
        for (var l : duplicateLines) {
            jdbc.update("INSERT INTO quotation_line(quotation_id,item_code,quantity,rate,gst_percent,discount_percent,line_total,item_description_snapshot) VALUES(?,?,?,?,?,?,?,?)",
                    nid, l.code(), l.quantity(), l.rate(), l.gst(), l.discount(), l.total(), l.description());
        }
        return no;
    }

    @Transactional
    public void delete(int id) {
        CurrentUser.requirePermission("QUOTATION.DELETE","Delete quotation");
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

    private QuoteCalculation calculate(List<QuotationDtos.LineDto> requested){
        if(requested==null||requested.isEmpty())throw new IllegalArgumentException("Quotation must contain at least one item line.");
        List<QuotationDtos.LineDto> normalized=new ArrayList<>();double subtotal=0,discount=0,tax=0,total=0;
        for(var l:requested){if(l==null||l.code()==null||l.code().isBlank())throw new IllegalArgumentException("Quotation item code is required.");
            if(!Double.isFinite(l.quantity())||l.quantity()<=0)throw new IllegalArgumentException("Quotation quantity must be greater than zero.");
            if(!Double.isFinite(l.rate())||l.rate()<0)throw new IllegalArgumentException("Quotation rate must be non-negative.");
            DocumentCalculationEngine.LineResult r=DocumentCalculationEngine.line(l.quantity(),l.rate(),l.discount(),l.gst());
            subtotal+=r.taxableAmount();discount+=r.discountAmount();tax+=r.taxAmount();total+=r.totalAmount();
            normalized.add(new QuotationDtos.LineDto(l.code(),l.description(),l.quantity(),l.rate(),l.gst(),l.discount(),r.totalAmount()));}
        return new QuoteCalculation(DocumentCalculationEngine.money(subtotal),DocumentCalculationEngine.money(discount),DocumentCalculationEngine.money(tax),DocumentCalculationEngine.money(total),List.copyOf(normalized));
    }
    private static LocalDate requireDate(String v,String field){try{if(v==null||v.isBlank())throw new Exception();return LocalDate.parse(v.trim());}catch(Exception ex){throw new IllegalArgumentException(field+" must be a valid YYYY-MM-DD date");}}
    private record QuoteCalculation(double subtotal,double discount,double tax,double total,List<QuotationDtos.LineDto> lines){}

    private String requireQuotationSource(String value){
        String source=trim(value);
        if(source==null)throw new IllegalArgumentException("Quotation Source is required and must be selected from Master Data.");
        String canonical=activeQuotationSources().stream().filter(v->v.equalsIgnoreCase(source)).findFirst().orElse(null);
        if(canonical==null)throw new IllegalArgumentException("Quotation Source '"+source+"' is not an active QUOTATION SOURCE Master value.");
        return canonical;
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
