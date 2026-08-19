package org.example.server.insights;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.util.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class InsightsService {
 private static final String ACTIVE_SALES = "UPPER(COALESCE(document_status,'')) NOT IN ('DELETED','CANCELLED')";
 private static final String POSTED_PURCHASES = "UPPER(COALESCE(document_status,'')) NOT IN ('DELETED','CANCELLED','DRAFT')";
 private final JpaNativeRepository jdbc; public InsightsService(JpaNativeRepository jdbc){this.jdbc=jdbc;}

 // Dashboard sections are deliberately not wrapped in one database transaction.
 // A malformed historical value or one failing optional query must not leave the
 // PostgreSQL transaction aborted and prevent the remaining independent sections.
 public InsightDtos.DashboardBundle dashboard(String period){
   String selectedPeriod=period==null||period.isBlank()?"This Month":period;
   String cond=periodSql(selectedPeriod,"invoice_date");
   long[] master=dashboardSection("master totals",()->jdbc.query("""
     SELECT
       (SELECT COUNT(*) FROM item_master),
       (SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=COALESCE(minimum_stock,0)),
       (SELECT COUNT(*) FROM party_master WHERE party_type='CUSTOMER' AND COALESCE(is_active::text,'1') IN ('1','true','t'))
     """,(r,i)->new long[]{r.getLong(1),r.getLong(2),r.getLong(3)}).getFirst(),new long[]{0,0,0});
   double[] saleStats=dashboardSection("sales KPIs",()->jdbc.query("SELECT COUNT(*) FILTER (WHERE ("+cond+")), COALESCE(SUM(total_amount) FILTER (WHERE ("+cond+")),0), COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0), COUNT(*) FILTER (WHERE total_amount>COALESCE(paid_amount,0)), COALESCE(SUM(paid_amount),0) FROM sales_header WHERE "+ACTIVE_SALES,(r,i)->new double[]{r.getDouble(1),r.getDouble(2),r.getDouble(3),r.getDouble(4),r.getDouble(5)}).getFirst(),new double[]{0,0,0,0,0});
   double[] purchaseStats=dashboardSection("purchase KPIs",()->jdbc.query("SELECT COUNT(*) FILTER (WHERE ("+cond+")), COALESCE(SUM(total_amount) FILTER (WHERE ("+cond+")),0), COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0), COUNT(*) FILTER (WHERE total_amount>COALESCE(paid_amount,0)), COALESCE(SUM(paid_amount),0) FROM purchase_header WHERE "+POSTED_PURCHASES,(r,i)->new double[]{r.getDouble(1),r.getDouble(2),r.getDouble(3),r.getDouble(4),r.getDouble(5)}).getFirst(),new double[]{0,0,0,0,0});
   double expense=dashboardSection("expense total",()->n("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(voucher_type)='EXPENSE'"),0d);
   double cash=saleStats[4]-purchaseStats[4]-expense;
   String reminderDue=safeDateSql("due_date");
   long[] reminderStats=dashboardSection("reminder KPIs",()->jdbc.query("SELECT COUNT(*) FILTER (WHERE UPPER(COALESCE(status,'OPEN')) NOT IN ('COMPLETED','CANCELLED')), COUNT(*) FILTER (WHERE UPPER(COALESCE(status,'OPEN')) NOT IN ('COMPLETED','CANCELLED') AND "+reminderDue+" < ?)",(r,i)->new long[]{r.getLong(1),r.getLong(2)},BusinessClock.today()).getFirst(),new long[]{0,0});
   var snap=new InsightDtos.DashboardSnapshot(selectedPeriod,master[0],master[2],(long)saleStats[0],(long)purchaseStats[0],master[1],saleStats[1],purchaseStats[1],saleStats[2],purchaseStats[2],(long)saleStats[3],(long)purchaseStats[3],cash,reminderStats[0],reminderStats[1]);
   List<InsightDtos.ActivityDto> recent=dashboardSection("recent activity",()->jdbc.query("SELECT * FROM (SELECT 'Sale' type,s.invoice_no doc_no,p.name party,s.invoice_date doc_date,s.total_amount amount FROM sales_header s JOIN party_master p ON p.id=s.customer_id WHERE UPPER(COALESCE(s.document_status,'')) NOT IN ('DELETED','CANCELLED') UNION ALL SELECT 'Purchase',h.invoice_no,p.name,h.invoice_date,h.total_amount FROM purchase_header h JOIN party_master p ON p.id=h.supplier_id WHERE UPPER(COALESCE(h.document_status,'')) NOT IN ('DELETED','CANCELLED','DRAFT')) x ORDER BY doc_date DESC,doc_no DESC LIMIT 8",(rs,i)->new InsightDtos.ActivityDto(rs.getString(1),rs.getString(2),rs.getString(3),String.valueOf(rs.getObject(4)),rs.getDouble(5))),List.of());
   String pc=periodSql(selectedPeriod,"s.invoice_date");
   List<String> top=dashboardSection("top customers",()->jdbc.query("SELECT p.name,COALESCE(SUM(s.total_amount),0) amount FROM sales_header s JOIN party_master p ON p.id=s.customer_id WHERE UPPER(COALESCE(s.document_status,'')) NOT IN ('DELETED','CANCELLED') AND ("+pc+") GROUP BY p.id,p.name ORDER BY amount DESC LIMIT 5",(rs,i)->rs.getString(1)+"|"+rs.getDouble(2)),List.of());
   if(top.isEmpty())top=List.of("No customer sales for "+selectedPeriod.toLowerCase(Locale.ROOT));
   LocalDate today=BusinessClock.today();
   String dueExpr=safeDateSql("due_date");
   double[] buckets=dashboardSection("receivables ageing",()->jdbc.query("SELECT "
     +"COALESCE(SUM(total_amount-COALESCE(paid_amount,0)) FILTER (WHERE "+dueExpr+" < ?),0),"
     +"COALESCE(SUM(total_amount-COALESCE(paid_amount,0)) FILTER (WHERE "+dueExpr+" BETWEEN ? AND ?),0),"
     +"COALESCE(SUM(total_amount-COALESCE(paid_amount,0)) FILTER (WHERE "+dueExpr+" BETWEEN ? AND ?),0),"
     +"COALESCE(SUM(total_amount-COALESCE(paid_amount,0)) FILTER (WHERE "+dueExpr+" BETWEEN ? AND ?),0),"
     +"COALESCE(SUM(total_amount-COALESCE(paid_amount,0)) FILTER (WHERE "+dueExpr+" IS NULL OR "+dueExpr+" >= ?),0) "
     +"FROM sales_header WHERE UPPER(COALESCE(document_status,'')) NOT IN ('DELETED','CANCELLED') AND total_amount-COALESCE(paid_amount,0)>0",(r,i)->new double[]{r.getDouble(1),r.getDouble(2),r.getDouble(3),r.getDouble(4),r.getDouble(5)},today.minusDays(30),today.minusDays(30),today.minusDays(21),today.minusDays(20),today.minusDays(11),today.minusDays(10),today.minusDays(1),today).getFirst(),new double[]{0,0,0,0,0});
   List<String> ageing=List.of("Overdue (> 30 Days)|"+buckets[0],"21 - 30 Days|"+buckets[1],"11 - 20 Days|"+buckets[2],"1 - 10 Days|"+buckets[3],"Not Due|"+buckets[4]);
   List<InsightDtos.NotificationDto> activity=dashboardSection("notifications",()->notifications(5),List.of());
   return new InsightDtos.DashboardBundle(snap,recent,top,ageing,activity);
 }

 private String periodSql(String p,String c){LocalDate today=BusinessClock.today();LocalDate start=switch(p==null?"":p){case "This Month"->today.withDayOfMonth(1);case "This Quarter"->{int m=((today.getMonthValue()-1)/3)*3+1;yield LocalDate.of(today.getYear(),m,1);}case "This Year"->LocalDate.of(today.getYear(),1,1);default->null;};return start==null?"1=1":"("+safeDateSql(c)+")>=DATE '"+start+"'";}
 static String safeDateSql(String c){
   String text="NULLIF(TRIM(CAST("+c+" AS text)),'')";
   String slash="SPLIT_PART("+text+",'/',3)||'-'||SPLIT_PART("+text+",'/',2)||'-'||SPLIT_PART("+text+",'/',1)";
   String dash="SPLIT_PART("+text+",'-',3)||'-'||SPLIT_PART("+text+",'-',2)||'-'||SPLIT_PART("+text+",'-',1)";
   return "CASE WHEN "+text+" IS NULL THEN NULL "
     +"WHEN "+text+" ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' AND pg_input_is_valid("+text+",'date') THEN CAST("+text+" AS date) "
     +"WHEN "+text+" ~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}$' AND pg_input_is_valid("+slash+",'date') THEN CAST("+slash+" AS date) "
     +"WHEN "+text+" ~ '^[0-9]{2}-[0-9]{2}-[0-9]{4}$' AND pg_input_is_valid("+dash+",'date') THEN CAST("+dash+" AS date) "
     +"ELSE NULL END";
 }
 private <T> T dashboardSection(String section,java.util.function.Supplier<T> query,T fallback){try{return query.get();}catch(RuntimeException failure){System.err.println("[Dashboard] "+section+" unavailable: "+Objects.toString(failure.getMessage(),failure.getClass().getSimpleName()));return fallback;}}


 @Transactional(readOnly=true) public InsightDtos.ShellCounts shellCounts(){
   // One PostgreSQL round-trip replaces the four independent COUNT queries used
   // by earlier releases. This endpoint is intentionally cheap because the shell
   // refreshes it frequently for near-real-time badges.
   return jdbc.query("""
     SELECT
       (SELECT COUNT(*) FROM notifications WHERE COALESCE(is_read::text,'0') IN ('0','false','f')),
       (SELECT COUNT(*) FROM communication_log WHERE channel='EMAIL' AND COALESCE(is_read::text,'0') IN ('0','false','f')),
       (SELECT COUNT(*) FROM communication_log WHERE channel='WHATSAPP' AND COALESCE(is_read::text,'0') IN ('0','false','f')),
       (SELECT COUNT(*) FROM reminder_register WHERE UPPER(COALESCE(NULLIF(TRIM(status),''),'OPEN')) IN ('OPEN','SNOOZED'))
     """,(r,i)->new InsightDtos.ShellCounts(r.getInt(1),r.getInt(2),r.getInt(3),r.getInt(4))).getFirst();
 }
 @Transactional public void markCommunicationRead(String channel){
   jdbc.update("UPDATE communication_log SET is_read=1 WHERE channel=?",channel==null?"":channel.trim().toUpperCase(Locale.ROOT));
 }


 @Transactional(readOnly=true) public InsightDtos.ReportFilters reportFilters(){return new InsightDtos.ReportFilters(strings("SELECT name FROM party_master WHERE COALESCE(is_active::text,'1') IN ('1','true','t') ORDER BY name"),strings("SELECT description FROM item_master WHERE COALESCE(is_active::text,'1') IN ('1','true','t') ORDER BY description"),strings("SELECT DISTINCT salesperson FROM sales_header WHERE "+ACTIVE_SALES+" AND COALESCE(salesperson,'')<>'' ORDER BY salesperson"));}
 @Transactional(readOnly=true) public InsightDtos.ReportBundle report(String from,String to){
   Object[] a={LocalDate.parse(from),LocalDate.parse(to)}; String between=" BETWEEN ? AND ?";
   double sales=n("SELECT COALESCE(SUM(total_amount),0) FROM sales_header WHERE "+ACTIVE_SALES+" AND CAST(invoice_date AS DATE)"+between,a), purchase=n("SELECT COALESCE(SUM(total_amount),0) FROM purchase_header WHERE "+POSTED_PURCHASES+" AND CAST(invoice_date AS DATE)"+between,a); double profit=sales-purchase;
   double recv=n("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM sales_header WHERE "+ACTIVE_SALES+" AND CAST(invoice_date AS DATE)"+between,a),stock=n("SELECT COALESCE(SUM(opening_stock*purchase_price),0) FROM item_master");
   long low=l("SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=COALESCE(minimum_stock,0)"),customers=l("SELECT COUNT(*) FROM party_master WHERE party_type='CUSTOMER' AND COALESCE(is_active::text,'1') IN ('1','true','t')");
   List<InsightDtos.PointDto> cp=points("SELECT COALESCE(pm.name,'Unknown Customer'),SUM(sh.total_amount) amount FROM sales_header sh LEFT JOIN party_master pm ON pm.id=sh.customer_id WHERE UPPER(COALESCE(sh.document_status,'')) NOT IN ('DELETED','CANCELLED') AND CAST(sh.invoice_date AS DATE) BETWEEN ? AND ? GROUP BY pm.id,pm.name ORDER BY amount DESC LIMIT 5",a);
   List<InsightDtos.PointDto> ip=points("SELECT COALESCE(im.description,sl.item_code),SUM(sl.line_total) amount FROM sales_line sl JOIN sales_header sh ON sh.id=sl.sales_id LEFT JOIN item_master im ON im.item_code=sl.item_code WHERE UPPER(COALESCE(sh.document_status,'')) NOT IN ('DELETED','CANCELLED') AND CAST(sh.invoice_date AS DATE) BETWEEN ? AND ? GROUP BY sl.item_code,im.description ORDER BY amount DESC LIMIT 5",a);
   List<InsightDtos.ReportRow> sr=rows("SELECT sh.invoice_no,CAST(sh.invoice_date AS DATE),pm.name,sh.total_amount,COALESCE(sh.payment_status,'PENDING') FROM sales_header sh LEFT JOIN party_master pm ON pm.id=sh.customer_id WHERE UPPER(COALESCE(sh.document_status,'')) NOT IN ('DELETED','CANCELLED') AND CAST(sh.invoice_date AS DATE) BETWEEN ? AND ? ORDER BY sh.invoice_date DESC,sh.id DESC LIMIT 8",a);
   List<InsightDtos.ReportRow> pr=rows("SELECT ph.invoice_no,CAST(ph.invoice_date AS DATE),pm.name,ph.total_amount,COALESCE(ph.payment_status,'PENDING') FROM purchase_header ph LEFT JOIN party_master pm ON pm.id=ph.supplier_id WHERE UPPER(COALESCE(ph.document_status,'')) NOT IN ('DELETED','CANCELLED','DRAFT') AND CAST(ph.invoice_date AS DATE) BETWEEN ? AND ? ORDER BY ph.invoice_date DESC,ph.id DESC LIMIT 8",a);
   double sp=n("SELECT COALESCE(SUM(paid_amount),0) FROM sales_header WHERE "+ACTIVE_SALES+" AND CAST(invoice_date AS DATE) BETWEEN ? AND ?",a),pay=n("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM purchase_header WHERE "+POSTED_PURCHASES+" AND CAST(invoice_date AS DATE) BETWEEN ? AND ?",a),pp=n("SELECT COALESCE(SUM(paid_amount),0) FROM purchase_header WHERE "+POSTED_PURCHASES+" AND CAST(invoice_date AS DATE) BETWEEN ? AND ?",a);
   return new InsightDtos.ReportBundle(sales,purchase,profit,recv,stock,low,customers,cp,ip,sr,pr,sp,pay,pp,l("SELECT COUNT(*) FROM item_master"),l("SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=0"));
 }

 @Transactional(readOnly=true)
 public List<InsightDtos.ReminderDto> reminders(){
   return jdbc.query(
     "SELECT id,title,reference_no,due_date,priority,notes,status,created_by,snoozed_until " +
     "FROM reminder_register " +
     "ORDER BY CASE UPPER(COALESCE(NULLIF(TRIM(status),''),'OPEN')) WHEN 'OPEN' THEN 0 WHEN 'SNOOZED' THEN 1 WHEN 'COMPLETED' THEN 2 ELSE 3 END, " +
     safeDateSql("due_date") + " NULLS LAST, id DESC",
     (r,i)->toReminder(r)
   );
 }

 @Transactional(readOnly=true)
 public InsightDtos.ReminderDto reminder(long id){
   if(id<=0) throw new IllegalArgumentException("Reminder id must be greater than zero");
   List<InsightDtos.ReminderDto> matches=jdbc.query(
     "SELECT id,title,reference_no,due_date,priority,notes,status,created_by,snoozed_until FROM reminder_register WHERE id=?",
     (r,i)->toReminder(r), id
   );
   if(matches.isEmpty()) throw new IllegalArgumentException("Reminder was not found");
   return matches.getFirst();
 }

 @Transactional
 public InsightDtos.ReminderDto createReminder(InsightDtos.ReminderDto d){
   ReminderInput input=validateReminder(d);
   String now=BusinessClock.nowUtcText();
   String creator=CurrentUser.require().username();
   Long newId=jdbc.queryForObject(
     "INSERT INTO reminder_register(title,reference_no,due_date,priority,notes,status,created_by,created_at,updated_at) " +
     "VALUES(?,?,?,?,?,'OPEN',?,?,?) RETURNING id",
     Long.class,
     input.title(),input.reference(),input.dueDate(),input.priority(),input.notes(),creator,now,now
   );
   if(newId==null||newId<=0) throw new IllegalStateException("Reminder was created but no valid id was returned");
   return reminder(newId);
 }

 @Transactional
 public InsightDtos.ReminderDto updateReminder(long id,InsightDtos.ReminderDto d){
   if(id<=0) throw new IllegalArgumentException("Reminder id must be greater than zero");
   ReminderInput input=validateReminder(d);
   int changed=jdbc.update(
     "UPDATE reminder_register SET title=?,reference_no=?,due_date=?,priority=?,notes=?,updated_at=? WHERE id=?",
     input.title(),input.reference(),input.dueDate(),input.priority(),input.notes(),BusinessClock.nowUtcText(),id
   );
   if(changed!=1) throw new IllegalArgumentException("Reminder was not found or could not be updated");
   return reminder(id);
 }

 @Transactional
 public void setReminderStatus(long id,String status,String snoozedUntil){
   if(id<=0) throw new IllegalArgumentException("Reminder id must be greater than zero");
   String normalized=status==null?"OPEN":status.trim().toUpperCase(Locale.ROOT);
   if(!Set.of("OPEN","SNOOZED","COMPLETED").contains(normalized)) throw new IllegalArgumentException("Unsupported reminder status: "+normalized);
   String now=BusinessClock.nowUtcText();
   int changed;
   if("SNOOZED".equals(normalized)){
     String until=validatedReminderDate(snoozedUntil,"Snooze date");
     changed=jdbc.update("UPDATE reminder_register SET status='SNOOZED',snoozed_until=?,completed_at=NULL,updated_at=? WHERE id=?",until,now,id);
   }else if("COMPLETED".equals(normalized)){
     changed=jdbc.update("UPDATE reminder_register SET status='COMPLETED',completed_at=?,snoozed_until=NULL,updated_at=? WHERE id=?",now,now,id);
   }else{
     changed=jdbc.update("UPDATE reminder_register SET status='OPEN',completed_at=NULL,snoozed_until=NULL,updated_at=? WHERE id=?",now,id);
   }
   if(changed!=1) throw new IllegalArgumentException("Reminder was not found or could not be updated");
 }

 @Transactional
 public void deleteReminder(long id){
   if(id<=0) throw new IllegalArgumentException("Reminder id must be greater than zero");
   int changed=jdbc.update("DELETE FROM reminder_register WHERE id=?",id);
   if(changed!=1) throw new IllegalArgumentException("Reminder was not found or was already deleted");
 }

 private ReminderInput validateReminder(InsightDtos.ReminderDto d){
   if(d==null) throw new IllegalArgumentException("Reminder details are required");
   String title=d.title()==null?"":d.title().trim();
   if(title.isBlank()) throw new IllegalArgumentException("Reminder title is required");
   String due=validatedReminderDate(d.dueDate(),"Due date");
   String priority=d.priority()==null||d.priority().isBlank()?"NORMAL":d.priority().trim().toUpperCase(Locale.ROOT);
   if(!Set.of("LOW","NORMAL","HIGH","URGENT").contains(priority)) throw new IllegalArgumentException("Unsupported reminder priority: "+priority);
   String reference=d.referenceNo()==null?"":d.referenceNo().trim();
   String notes=d.notes()==null?"":d.notes().trim();
   return new ReminderInput(title,reference,due,priority,notes);
 }

 private InsightDtos.ReminderDto toReminder(JpaNativeRepository.NativeRow r) {
   // JpaNativeRepository.query(...) intentionally returns positional NativeRow data.
   // Alias lookups are empty on that path; using getString("title") was the 7.3.1
   // defect that produced id=0 / blank Reminder rows and caused update HTTP 400s.
   String status=Objects.toString(r.getString(7),"OPEN").trim();
   if(status.isBlank()) status="OPEN";
   String priority=Objects.toString(r.getString(5),"NORMAL").trim();
   if(priority.isBlank()) priority="NORMAL";
   String createdBy=Objects.toString(r.getString(8),"System").trim();
   if(createdBy.isBlank()) createdBy="System";
   return new InsightDtos.ReminderDto(
     r.getLong(1),
     Objects.toString(r.getString(2),""),
     Objects.toString(r.getString(3),""),
     Objects.toString(r.getString(4),""),
     priority.toUpperCase(Locale.ROOT),
     Objects.toString(r.getString(6),""),
     status.toUpperCase(Locale.ROOT),
     createdBy,
     Objects.toString(r.getString(9),"")
   );
 }

 private String validatedReminderDate(String value,String field){
   if(value==null||value.isBlank()) throw new IllegalArgumentException(field+" is required");
   try{return LocalDate.parse(value.trim()).toString();}
   catch(Exception ex){throw new IllegalArgumentException(field+" must be a valid date",ex);}
 }

 private record ReminderInput(String title,String reference,String dueDate,String priority,String notes){}

 @Transactional(readOnly=true) public List<InsightDtos.NotificationDto> notifications(int limit){return jdbc.query("SELECT id,title,message,severity,category,is_read,target_fxml,reference_no,created_at FROM notifications ORDER BY created_at DESC LIMIT ?",(r,i)->new InsightDtos.NotificationDto(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),readFlag(r.getObject(6)),r.getString(7),r.getString(8),r.getLong(9)),Math.max(1,limit));}
 @Transactional(readOnly=true) public long unreadCount(){return l("SELECT COUNT(*) FROM notifications WHERE COALESCE(is_read::text,'0') IN ('0','false','f')");}
 @Transactional public InsightDtos.NotificationDto createNotification(InsightDtos.NotificationCreate d){long now=System.currentTimeMillis();jdbc.update("INSERT INTO notifications(title,message,severity,category,is_read,target_fxml,reference_no,created_at) VALUES(?,?,?,?,0,?,?,?)",d.title(),d.message(),d.severity()==null?"INFO":d.severity(),d.category()==null?"GENERAL":d.category(),d.targetFxml(),d.referenceNo(),now);return notifications(1).getFirst();}
 @Transactional public void markRead(long id){jdbc.update("UPDATE notifications SET is_read=1 WHERE id=?",id);}@Transactional public void markAllRead(){jdbc.update("UPDATE notifications SET is_read=1");}@Transactional public void deleteNotification(long id){jdbc.update("DELETE FROM notifications WHERE id=?",id);}@Transactional public void clearNotifications(){jdbc.update("DELETE FROM notifications");}

 private boolean readFlag(Object v){if(v==null)return false;if(v instanceof Boolean b)return b;String s=String.valueOf(v).trim();return s.equals("1")||s.equalsIgnoreCase("true")||s.equalsIgnoreCase("t");}
 private List<String> strings(String q){return jdbc.query(q,(r,i)->r.getString(1));}
 private List<InsightDtos.PointDto> points(String q,Object... a){return jdbc.query(q,(r,i)->new InsightDtos.PointDto(Objects.toString(r.getObject(1),"â€”"),r.getDouble(2)),a);}
 private List<InsightDtos.ReportRow> rows(String q,Object... a){return jdbc.query(q,(r,i)->new InsightDtos.ReportRow(r.getString(1),String.valueOf(r.getObject(2)),Objects.toString(r.getObject(3),"â€”"),r.getDouble(4),r.getString(5)),a);}
 private double n(String q,Object... a){Double x=jdbc.queryForObject(q,Double.class,a);return x==null?0:x;} private long l(String q,Object... a){Long x=jdbc.queryForObject(q,Long.class,a);return x==null?0:x;}
}
