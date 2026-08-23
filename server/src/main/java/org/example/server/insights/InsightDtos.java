package org.example.server.insights;
import java.util.List;
public final class InsightDtos { private InsightDtos(){}
 public record DashboardSnapshot(String period,long products,long customers,long invoices,long purchases,long lowStock,double salesValue,double purchaseValue,double receivables,double payables,long openReceivables,long openPayables,double cash,long openReminders,long overdueReminders){}
 public record ActivityDto(String type,String number,String party,String date,double amount){}
 public record NotificationDto(long id,String title,String message,String severity,String category,boolean read,String targetFxml,String referenceNo,String moduleKey,Long recordId,String actionCode,long createdAt){}
 public record DashboardBundle(DashboardSnapshot snapshot,List<ActivityDto> recent,List<String> topCustomers,List<String> ageing,List<NotificationDto> activities){}
 public record ReportFilters(List<String> parties,List<String> items,List<String> salespeople){}
 public record PointDto(String label,double value){}
 public record ReportRow(String number,String date,String party,double amount,String status){}
 public record ReportBundle(double sales,double purchase,double profit,double receivables,double stock,long low,long customers,List<PointDto> customerPoints,List<PointDto> itemPoints,List<ReportRow> salesRows,List<ReportRow> purchaseRows,double salesPaid,double payables,double purchasesPaid,long items,long out){}
 public record ReminderDto(Long id,String title,String referenceNo,String dueDate,String priority,String notes,String status,String createdBy,String snoozedUntil){}
 public record NotificationCreate(String title,String message,String severity,String category,String targetFxml,String referenceNo,String moduleKey,Long recordId,String actionCode){
  public NotificationCreate(String title,String message,String severity,String category,String targetFxml,String referenceNo){this(title,message,severity,category,targetFxml,referenceNo,null,null,null);}
 }
 public record CountDto(long count){} public record ShellCounts(int notifications,int email,int whatsapp,int reminders){} public record Ok(boolean success,String message){}
}
