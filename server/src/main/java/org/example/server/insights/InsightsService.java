package org.example.server.insights;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class InsightsService {
 private final JpaNativeRepository jdbc; public InsightsService(JpaNativeRepository jdbc){this.jdbc=jdbc;}

 @Transactional(readOnly=true) public InsightDtos.DashboardBundle dashboard(String period){
   String cond=periodSql(period,"invoice_date");
   long products=l("SELECT COUNT(*) FROM item_master"), customers=l("SELECT COUNT(*) FROM party_master WHERE party_type='CUSTOMER' AND COALESCE(is_active::text,'1') IN ('1','true','t')");
   long invoices=l("SELECT COUNT(*) FROM sales_header WHERE "+cond), purchases=l("SELECT COUNT(*) FROM purchase_header WHERE "+cond), low=l("SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=COALESCE(minimum_stock,0)");
   double sales=n("SELECT COALESCE(SUM(total_amount),0) FROM sales_header WHERE "+cond), purchase=n("SELECT COALESCE(SUM(total_amount),0) FROM purchase_header WHERE "+cond);
   double recv=n("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM sales_header"), pay=n("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM purchase_header");
   long openRecv=l("SELECT COUNT(*) FROM sales_header WHERE total_amount>COALESCE(paid_amount,0)"), openPay=l("SELECT COUNT(*) FROM purchase_header WHERE total_amount>COALESCE(paid_amount,0)");
   double cash=n("SELECT COALESCE(SUM(paid_amount),0) FROM sales_header")-n("SELECT COALESCE(SUM(paid_amount),0) FROM purchase_header")-n("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(voucher_type)='EXPENSE'");
   long openRem=l("SELECT COUNT(*) FROM reminder_register WHERE UPPER(COALESCE(status,'OPEN')) NOT IN ('COMPLETED','CANCELLED')");
   long overdue=l("SELECT COUNT(*) FROM reminder_register WHERE UPPER(COALESCE(status,'OPEN')) NOT IN ('COMPLETED','CANCELLED') AND due_date IS NOT NULL AND CAST(due_date AS DATE)<CURRENT_DATE");
   var snap=new InsightDtos.DashboardSnapshot(period,products,customers,invoices,purchases,low,sales,purchase,recv,pay,openRecv,openPay,cash,openRem,overdue);
   List<InsightDtos.ActivityDto> recent=jdbc.query("SELECT * FROM (SELECT 'Sale' type,s.invoice_no doc_no,p.name party,s.invoice_date doc_date,s.total_amount amount FROM sales_header s JOIN party_master p ON p.id=s.customer_id UNION ALL SELECT 'Purchase',h.invoice_no,p.name,h.invoice_date,h.total_amount FROM purchase_header h JOIN party_master p ON p.id=h.supplier_id) x ORDER BY doc_date DESC,doc_no DESC LIMIT 8",(rs,i)->new InsightDtos.ActivityDto(rs.getString("type"),rs.getString("doc_no"),rs.getString("party"),String.valueOf(rs.getObject("doc_date")),rs.getDouble("amount")));
   String pc=periodSql(period,"s.invoice_date");
   List<String> top=jdbc.query("SELECT p.name,COALESCE(SUM(s.total_amount),0) amount FROM sales_header s JOIN party_master p ON p.id=s.customer_id WHERE "+pc+" GROUP BY p.id,p.name ORDER BY amount DESC LIMIT 5",(rs,i)->rs.getString(1)+"|"+rs.getDouble(2));
   if(top.isEmpty())top=List.of("No customer sales for "+period.toLowerCase(Locale.ROOT));
   List<String> ageing=List.of(age("Overdue (> 30 Days)","CAST(due_date AS DATE) < CURRENT_DATE - INTERVAL '30 days'"),age("21 - 30 Days","CAST(due_date AS DATE) BETWEEN CURRENT_DATE - INTERVAL '30 days' AND CURRENT_DATE - INTERVAL '21 days'"),age("11 - 20 Days","CAST(due_date AS DATE) BETWEEN CURRENT_DATE - INTERVAL '20 days' AND CURRENT_DATE - INTERVAL '11 days'"),age("1 - 10 Days","CAST(due_date AS DATE) BETWEEN CURRENT_DATE - INTERVAL '10 days' AND CURRENT_DATE - INTERVAL '1 day'"),age("Not Due","due_date IS NULL OR CAST(due_date AS DATE) >= CURRENT_DATE"));
   return new InsightDtos.DashboardBundle(snap,recent,top,ageing,notifications(5));
 }
 private String age(String label,String cond){return label+"|"+n("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM sales_header WHERE total_amount-COALESCE(paid_amount,0)>0 AND ("+cond+")");}
 private String periodSql(String p,String c){return switch(p==null?"":p){case "This Month"->"CAST("+c+" AS DATE)>=date_trunc('month',CURRENT_DATE)::date";case "This Quarter"->"CAST("+c+" AS DATE)>=date_trunc('quarter',CURRENT_DATE)::date";case "This Year"->"CAST("+c+" AS DATE)>=date_trunc('year',CURRENT_DATE)::date";default->"1=1";};}


 @Transactional(readOnly=true) public InsightDtos.ShellCounts shellCounts(){
   int notifications=(int)unreadCount();
   int email=(int)l("SELECT COUNT(*) FROM communication_log WHERE channel='EMAIL' AND COALESCE(is_read::text,'0') IN ('0','false','f')");
   int whatsapp=(int)l("SELECT COUNT(*) FROM communication_log WHERE channel='WHATSAPP' AND COALESCE(is_read::text,'0') IN ('0','false','f')");
   int reminders=(int)l("SELECT COUNT(*) FROM reminder_register WHERE status IN ('OPEN','SNOOZED')");
   return new InsightDtos.ShellCounts(notifications,email,whatsapp,reminders);
 }
 @Transactional public void markCommunicationRead(String channel){
   jdbc.update("UPDATE communication_log SET is_read=1 WHERE channel=?",channel==null?"":channel.trim().toUpperCase(Locale.ROOT));
 }


 @Transactional(readOnly=true) public InsightDtos.ReportFilters reportFilters(){return new InsightDtos.ReportFilters(strings("SELECT name FROM party_master WHERE COALESCE(is_active::text,'1') IN ('1','true','t') ORDER BY name"),strings("SELECT description FROM item_master WHERE COALESCE(is_active::text,'1') IN ('1','true','t') ORDER BY description"),strings("SELECT DISTINCT salesperson FROM sales_header WHERE COALESCE(salesperson,'')<>'' ORDER BY salesperson"));}
 @Transactional(readOnly=true) public InsightDtos.ReportBundle report(String from,String to){
   Object[] a={LocalDate.parse(from),LocalDate.parse(to)}; String between=" BETWEEN ? AND ?";
   double sales=n("SELECT COALESCE(SUM(total_amount),0) FROM sales_header WHERE CAST(invoice_date AS DATE)"+between,a), purchase=n("SELECT COALESCE(SUM(total_amount),0) FROM purchase_header WHERE CAST(invoice_date AS DATE)"+between,a); double profit=sales-purchase;
   double recv=n("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM sales_header WHERE CAST(invoice_date AS DATE)"+between,a),stock=n("SELECT COALESCE(SUM(opening_stock*purchase_price),0) FROM item_master");
   long low=l("SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=COALESCE(minimum_stock,0)"),customers=l("SELECT COUNT(*) FROM party_master WHERE party_type='CUSTOMER' AND COALESCE(is_active::text,'1') IN ('1','true','t')");
   List<InsightDtos.PointDto> cp=points("SELECT COALESCE(pm.name,'Unknown Customer'),SUM(sh.total_amount) amount FROM sales_header sh LEFT JOIN party_master pm ON pm.id=sh.customer_id WHERE CAST(sh.invoice_date AS DATE) BETWEEN ? AND ? GROUP BY pm.id,pm.name ORDER BY amount DESC LIMIT 5",a);
   List<InsightDtos.PointDto> ip=points("SELECT COALESCE(im.description,sl.item_code),SUM(sl.line_total) amount FROM sales_line sl JOIN sales_header sh ON sh.id=sl.sales_id LEFT JOIN item_master im ON im.item_code=sl.item_code WHERE CAST(sh.invoice_date AS DATE) BETWEEN ? AND ? GROUP BY sl.item_code,im.description ORDER BY amount DESC LIMIT 5",a);
   List<InsightDtos.ReportRow> sr=rows("SELECT sh.invoice_no,CAST(sh.invoice_date AS DATE),pm.name,sh.total_amount,COALESCE(sh.payment_status,'PENDING') FROM sales_header sh LEFT JOIN party_master pm ON pm.id=sh.customer_id WHERE CAST(sh.invoice_date AS DATE) BETWEEN ? AND ? ORDER BY sh.invoice_date DESC,sh.id DESC LIMIT 8",a);
   List<InsightDtos.ReportRow> pr=rows("SELECT ph.invoice_no,CAST(ph.invoice_date AS DATE),pm.name,ph.total_amount,COALESCE(ph.payment_status,'PENDING') FROM purchase_header ph LEFT JOIN party_master pm ON pm.id=ph.supplier_id WHERE CAST(ph.invoice_date AS DATE) BETWEEN ? AND ? ORDER BY ph.invoice_date DESC,ph.id DESC LIMIT 8",a);
   double sp=n("SELECT COALESCE(SUM(paid_amount),0) FROM sales_header WHERE CAST(invoice_date AS DATE) BETWEEN ? AND ?",a),pay=n("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM purchase_header WHERE CAST(invoice_date AS DATE) BETWEEN ? AND ?",a),pp=n("SELECT COALESCE(SUM(paid_amount),0) FROM purchase_header WHERE CAST(invoice_date AS DATE) BETWEEN ? AND ?",a);
   return new InsightDtos.ReportBundle(sales,purchase,profit,recv,stock,low,customers,cp,ip,sr,pr,sp,pay,pp,l("SELECT COUNT(*) FROM item_master"),l("SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=0"));
 }

 @Transactional(readOnly=true) public List<InsightDtos.ReminderDto> reminders(){return jdbc.query("SELECT id,title,reference_no,due_date,priority,notes,status,created_by,snoozed_until FROM reminder_register ORDER BY CASE UPPER(COALESCE(status,'OPEN')) WHEN 'OPEN' THEN 0 WHEN 'SNOOZED' THEN 1 ELSE 2 END,CAST(due_date AS DATE),id DESC",(r,i)->new InsightDtos.ReminderDto(r.getInt("id"),r.getString("title"),r.getString("reference_no"),r.getString("due_date"),r.getString("priority"),r.getString("notes"),r.getString("status"),r.getString("created_by"),r.getString("snoozed_until")));}
 @Transactional public InsightDtos.ReminderDto saveReminder(InsightDtos.ReminderDto d,boolean update){String due=validatedReminderDate(d.dueDate(),"Due date");String title=d.title()==null?"":d.title().trim();if(title.isBlank())throw new IllegalArgumentException("Reminder title is required");String priority=d.priority()==null||d.priority().isBlank()?"NORMAL":d.priority().trim().toUpperCase(Locale.ROOT);String reference=d.referenceNo()==null?"":d.referenceNo().trim();String notes=d.notes()==null?"":d.notes();String now=java.time.LocalDateTime.now().toString();if(update){if(d.id()==null||d.id()<=0)throw new IllegalArgumentException("Reminder id is required for update");jdbc.update("UPDATE reminder_register SET title=?,reference_no=?,due_date=?,priority=?,notes=?,updated_at=? WHERE id=?",title,reference,due,priority,notes,now,d.id());return reminders().stream().filter(x->Objects.equals(x.id(),d.id())).findFirst().orElseThrow(()->new IllegalStateException("Reminder was not found after update"));}Integer newId=jdbc.queryForObject("INSERT INTO reminder_register(title,reference_no,due_date,priority,notes,status,created_by,updated_at) VALUES(?,?,?,?,?,'OPEN',?,?) RETURNING id",Integer.class,title,reference,due,priority,notes,CurrentUser.require().username(),now);return reminders().stream().filter(x->Objects.equals(x.id(),newId)).findFirst().orElseThrow(()->new IllegalStateException("Reminder was not found after creation"));}
 @Transactional public void setReminderStatus(int id,String status,String snoozedUntil){String normalized=status==null?"OPEN":status.trim().toUpperCase(Locale.ROOT);String now=java.time.LocalDateTime.now().toString();if("SNOOZED".equals(normalized)){String until=validatedReminderDate(snoozedUntil,"Snooze date");jdbc.update("UPDATE reminder_register SET status='SNOOZED',snoozed_until=?,completed_at=NULL,updated_at=? WHERE id=?",until,now,id);}else if("COMPLETED".equals(normalized)){jdbc.update("UPDATE reminder_register SET status='COMPLETED',completed_at=?,snoozed_until=NULL,updated_at=? WHERE id=?",now,now,id);}else{jdbc.update("UPDATE reminder_register SET status=?,completed_at=NULL,snoozed_until=NULL,updated_at=? WHERE id=?",normalized,now,id);}}
 @Transactional public void deleteReminder(int id){jdbc.update("DELETE FROM reminder_register WHERE id=?",id);}
 private String validatedReminderDate(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");LocalDate date=LocalDate.parse(value.trim());return date.toString();}

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
