package org.example.server.audit;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.util.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class AuditService {
    private final JpaNativeRepository jdbc;

    public AuditService(JpaNativeRepository jdbc) { this.jdbc = jdbc; }

    /**
     * Compatibility entry point used across the ERP. It keeps the historical activity_log
     * populated while also writing the authoritative centralized audit trail.
     */
    public void log(String entityType, Number entityId, String action, String detail) {
        logChanges(entityType, entityId, action, detail, List.of());
    }

    public void logChange(String entityType, Number entityId, String action, String detail,
                          String fieldName, Object oldValue, Object newValue) {
        logChanges(entityType, entityId, action, detail,
                List.of(new Change(fieldName, text(oldValue), text(newValue))));
    }

    public void logChanges(String entityType, Number entityId, String action, String detail, List<Change> changes) {
        String type = normalize(entityType), act = normalize(action), actor = CurrentUser.require().username();
        Long activityId = entityId == null ? null : entityId.longValue();
        long id = activityId == null ? 0L : activityId;
        String now = BusinessClock.nowUtcText();
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES(?,?,?,?,?,?)",
                type,activityId,act,safe(detail),actor,now);
        Long eventId = jdbc.queryForObject(
                "INSERT INTO audit_event(entity_type,entity_id,reference_no,action,category,detail,created_by,created_at,source) VALUES(?,?,?,?,?,?,?,?,?) RETURNING id",
                Long.class,type,id,id>0?reference(type,id):null,act,category(act),safe(detail),actor,now,"SERVER");
        if (eventId == null) throw new IllegalStateException("Audit event was not created.");
        if (changes != null) for (Change change : changes) {
            if (change == null || safe(change.fieldName()).isBlank() || Objects.equals(safe(change.oldValue()), safe(change.newValue()))) continue;
            jdbc.update("INSERT INTO audit_change(audit_event_id,field_name,old_value,new_value) VALUES(?,?,?,?)",
                    eventId,safe(change.fieldName()),nullable(change.oldValue()),nullable(change.newValue()));
        }
    }

    @Transactional(readOnly = true)
    public List<AuditDtos.EventRow> record(String entityType,long entityId) {
        CurrentUser.requirePermission("AUDIT.VIEW","View audit trail");
        String type=normalize(entityType);
        if ("CUSTOMER".equals(type) || "SUPPLIER".equals(type))
            return load("WHERE e.entity_type IN ('PARTY',?) AND e.entity_id=? ORDER BY e.created_at DESC,e.id DESC LIMIT 1000", type,entityId);
        return load("WHERE e.entity_type=? AND e.entity_id=? ORDER BY e.created_at DESC,e.id DESC LIMIT 1000", type,entityId);
    }

    @Transactional(readOnly = true)
    public AuditDtos.GlobalPage global(int page,int size,String module,String action,String user,String reference,String q) {
        CurrentUser.requirePermission("AUDIT.GLOBAL","View global audit trail");
        int safeSize=Math.max(20,Math.min(size,200)),safePage=Math.max(0,page);
        List<Object> args=new ArrayList<>(); StringBuilder where=new StringBuilder(" WHERE 1=1");
        add(where,args," AND e.entity_type=?",normalizeOptional(module));
        add(where,args," AND e.action=?",normalizeOptional(action));
        if(!blank(user)){where.append(" AND LOWER(e.created_by)=LOWER(?)");args.add(user.trim());}
        if(!blank(reference)){where.append(" AND LOWER(COALESCE(e.reference_no,'')) LIKE ?");args.add("%"+reference.trim().toLowerCase(Locale.ROOT)+"%");}
        if(!blank(q)){where.append(" AND LOWER(CONCAT_WS(' ',e.entity_type,e.reference_no,e.action,e.category,e.detail,e.created_by)) LIKE ?");args.add("%"+q.trim().toLowerCase(Locale.ROOT)+"%");}
        long total=Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event e"+where,Long.class,args.toArray())).orElse(0L);
        int pages=total==0?0:(int)Math.ceil(total/(double)safeSize); if(pages>0&&safePage>=pages)safePage=pages-1;
        List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(safeSize);pageArgs.add((long)safePage*safeSize);
        List<AuditDtos.EventRow> rows=load(where+" ORDER BY e.created_at DESC,e.id DESC LIMIT ? OFFSET ?",pageArgs.toArray());
        long changes=countCategory(where,args,"BUSINESS_CHANGE")+countCategory(where,args,"LIFECYCLE");
        long financial=countCategory(where,args,"FINANCIAL"), docs=countCategory(where,args,"DOCUMENT"), comm=countCategory(where,args,"COMMUNICATION");
        return new AuditDtos.GlobalPage(rows,total,safePage,safeSize,pages,changes,financial,docs,comm);
    }

    private long countCategory(StringBuilder where,List<Object> args,String cat){
        List<Object> a=new ArrayList<>(args);String extra=where+" AND e.category=?";a.add(cat);
        return Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event e"+extra,Long.class,a.toArray())).orElse(0L);
    }

    private List<AuditDtos.EventRow> load(String suffix,Object...args){
        List<Base> base=jdbc.query("SELECT e.id,e.entity_type,e.entity_id,COALESCE(e.reference_no,''),e.action,e.category,COALESCE(e.detail,''),e.created_by,e.created_at,COALESCE(e.source,'SERVER'),COALESCE(e.legacy_source,'') FROM audit_event e "+suffix,
                (r,i)->new Base(r.getLong(1),r.getString(2),r.getLong(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11)),args);
        List<AuditDtos.EventRow> out=new ArrayList<>(base.size());
        for(Base e:base){List<AuditDtos.ChangeRow> changes=jdbc.query("SELECT id,field_name,COALESCE(old_value,''),COALESCE(new_value,'') FROM audit_change WHERE audit_event_id=? ORDER BY id",(r,i)->new AuditDtos.ChangeRow(r.getLong(1),r.getString(2),r.getString(3),r.getString(4)),e.id());out.add(new AuditDtos.EventRow(e.id(),e.type(),e.entityId(),e.reference(),e.action(),e.category(),e.detail(),e.user(),e.at(),e.source(),e.legacy(),changes));}
        return List.copyOf(out);
    }

    private String reference(String type,long id){
        try{return switch(type){
            case "SALE"->jdbc.queryForObject("SELECT invoice_no FROM sales_header WHERE id=?",String.class,id);
            case "PURCHASE"->jdbc.queryForObject("SELECT invoice_no FROM purchase_header WHERE id=?",String.class,id);
            case "QUOTATION"->jdbc.queryForObject("SELECT quotation_no FROM quotation_header WHERE id=?",String.class,id);
            case "SALES_RETURN","PURCHASE_RETURN"->jdbc.queryForObject("SELECT return_no FROM return_register WHERE id=?",String.class,id);
            case "PARTY","CUSTOMER","SUPPLIER"->jdbc.queryForObject("SELECT party_code FROM party_master WHERE id=?",String.class,id);
            case "ITEM"->jdbc.queryForObject("SELECT item_code FROM item_master WHERE id=?",String.class,id);
            case "FINANCE"->jdbc.queryForObject("SELECT voucher_no FROM finance_register WHERE id=?",String.class,id);
            case "PURCHASE_RECON"->jdbc.queryForObject("SELECT recon_ref FROM purchase_recon WHERE id=?",String.class,id);
            case "RECON_SUPPLIER"->jdbc.queryForObject("SELECT recon_supplier_ref FROM recon_supplier WHERE id=?",String.class,id);
            case "MASTER_LOOKUP"->jdbc.queryForObject("SELECT CONCAT(lookup_type,':',lookup_code) FROM lookup_master WHERE id=?",String.class,id);
            case "MASTER_CATEGORY"->jdbc.queryForObject("SELECT category_code FROM master_category WHERE id=?",String.class,id);
            default->null;
        };}catch(Exception ignored){return null;}
    }
    private static String category(String a){String x=normalize(a);if(x.contains("PAYMENT")||x.contains("REFUND")||x.contains("RECON"))return "FINANCIAL";if(x.contains("EMAIL")||x.contains("WHATSAPP")||x.contains("COMMUNICATION"))return "COMMUNICATION";if(x.contains("PDF")||x.contains("EXCEL")||x.contains("ATTACH")||x.contains("DOCUMENT"))return "DOCUMENT";if(Set.of("APPROVED","REJECTED","CANCELLED","DELETED","PENDING_APPROVAL","CONVERTED","DUPLICATED").contains(x))return "LIFECYCLE";return "BUSINESS_CHANGE";}
    private static void add(StringBuilder w,List<Object>a,String clause,String value){if(value!=null){w.append(clause);a.add(value);}}
    private static String normalizeOptional(String v){return blank(v)||"ALL".equalsIgnoreCase(v.trim())?null:normalize(v);}
    private static String normalize(String value){return value==null?"":value.trim().toUpperCase(Locale.ROOT).replace(' ','_');}
    private static String safe(String value){String text=value==null?"":value.trim();return text.length()<=2000?text:text.substring(0,2000);}
    private static String nullable(String v){return v==null?null:safe(v);}
    private static String text(Object v){return v==null?null:String.valueOf(v);}
    private static boolean blank(String v){return v==null||v.isBlank();}
    public record Change(String fieldName,String oldValue,String newValue){}
    private record Base(long id,String type,long entityId,String reference,String action,String category,String detail,String user,String at,String source,String legacy){}
}
