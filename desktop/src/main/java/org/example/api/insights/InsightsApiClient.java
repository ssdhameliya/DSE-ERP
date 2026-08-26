package org.example.api.insights;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.ConfigManager;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Phase-4 REST client for dashboard, reporting, reminders and notifications. */
public final class InsightsApiClient {
    private final HttpClient http = org.example.api.ApiRuntime.HTTP;
    private final ObjectMapper json = org.example.api.ApiRuntime.JSON;
    private final String base;
    public InsightsApiClient(){String b=ConfigManager.getDataApiBaseUrl();while(b.endsWith("/"))b=b.substring(0,b.length()-1);base=b;}

    public DashboardBundle dashboard(String period){return get("/api/insights/dashboard?period="+enc(period),DashboardBundle.class);}
    public ShellCounts shellCounts(){return get("/api/insights/shell-counts",ShellCounts.class);}
    public void markCommunicationRead(String channel){postNoBody("/api/insights/communication/read?channel="+enc(channel));org.example.util.ShellIndicatorBus.publish();}
    public ReportFilters reportFilters(){return get("/api/insights/reports/filters",ReportFilters.class);}
    public ReportBundle report(String from,String to,String reportType,String party,String item,String salesperson){return get("/api/insights/reports?from="+enc(from)+"&to="+enc(to)+"&reportType="+enc(reportType)+"&party="+enc(party)+"&item="+enc(item)+"&salesperson="+enc(salesperson),ReportBundle.class);}

    public List<ReminderDto> reminders(){return get("/api/insights/reminders",new TypeReference<List<ReminderDto>>(){});}
    public ReminderDto saveReminder(ReminderDto d){ReminderDto value=post("/api/insights/reminders",d,ReminderDto.class);org.example.util.ShellIndicatorBus.publish();return value;}
    public ReminderDto updateReminder(ReminderDto d){ReminderDto value=put("/api/insights/reminders/"+d.id(),d,ReminderDto.class);org.example.util.ShellIndicatorBus.publish();return value;}
    public void reminderStatus(long id,String status,String snoozedUntil){postNoBody("/api/insights/reminders/"+id+"/status?status="+enc(status)+(snoozedUntil==null?"":"&snoozedUntil="+enc(snoozedUntil)));org.example.util.ShellIndicatorBus.publish();}
    public void deleteReminder(long id){delete("/api/insights/reminders/"+id);org.example.util.ShellIndicatorBus.publish();}

    public List<NotificationDto> notifications(int limit){return get("/api/insights/notifications?limit="+Math.max(1,limit),new TypeReference<List<NotificationDto>>(){});}
    public long unreadCount(){return get("/api/insights/notifications/unread-count",CountDto.class).count();}
    public void createNotification(NotificationCreate d){post("/api/insights/notifications",d,NotificationDto.class);org.example.util.ShellIndicatorBus.publish();}
    public void markRead(long id){postNoBody("/api/insights/notifications/"+id+"/read");org.example.util.ShellIndicatorBus.publish();} public void markUnread(long id){postNoBody("/api/insights/notifications/"+id+"/unread");org.example.util.ShellIndicatorBus.publish();}
    public void markAllRead(){postNoBody("/api/insights/notifications/read-all");org.example.util.ShellIndicatorBus.publish();}
    public void deleteNotification(long id){delete("/api/insights/notifications/"+id);org.example.util.ShellIndicatorBus.publish();}
    public void clearNotifications(){delete("/api/insights/notifications");org.example.util.ShellIndicatorBus.publish();}

    private <T>T get(String p,Class<T> c){return request("GET",p,null,c,null);} private <T>T get(String p,TypeReference<T> t){return request("GET",p,null,null,t);}
    private <T>T post(String p,Object b,Class<T> c){return request("POST",p,b,c,null);} private <T>T put(String p,Object b,Class<T> c){return request("PUT",p,b,c,null);}
    private void postNoBody(String p){request("POST",p,null,Ok.class,null);} private void delete(String p){request("DELETE",p,null,Ok.class,null);}
    private <T>T request(String method,String path,Object body,Class<T> cls,TypeReference<T> type){try{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Accept","application/json");org.example.api.ApiSession.authorize(b);if(body!=null){b.header("Content-Type","application/json");b.method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));}else b.method(method,HttpRequest.BodyPublishers.noBody());HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()<200||r.statusCode()>=300){org.example.api.ApiRuntime.logHttpFailure("Insights request",r.statusCode(),r.body());throw new IllegalStateException(org.example.api.ApiRuntime.userMessage("the requested dashboard or communication operation",r.statusCode(),r.body()));}return type!=null?json.readValue(r.body(),type):json.readValue(r.body(),cls);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Insights API request interrupted",e);}catch(IOException|IllegalArgumentException e){throw new IllegalStateException("Cannot reach insights server at "+base,e);}}

    private String apiErrorMessage(int status,String body){
        try{
            var node=json.readTree(body==null?"":body);
            String message=node!=null&&node.hasNonNull("message")?node.get("message").asText():"";
            if(message!=null&&!message.isBlank()) return message+" (HTTP "+status+")";
        }catch(Exception ignored){}
        return "Insights service request failed (HTTP "+status+")";
    }
    private String enc(String v){return URLEncoder.encode(v==null?"":v,StandardCharsets.UTF_8);}

    public record DashboardSnapshot(String period,long products,long customers,long invoices,long purchases,long lowStock,double salesValue,double purchaseValue,double receivables,double payables,long openReceivables,long openPayables,double cash,long openReminders,long overdueReminders){}
    public record ActivityDto(String type,String number,String party,String date,double amount){}
    public record DashboardBundle(DashboardSnapshot snapshot,List<ActivityDto> recent,List<String> topCustomers,List<String> ageing,List<NotificationDto> activities){}
    public record ReportFilters(List<String> parties,List<String> items,List<String> salespeople){}
    public record PointDto(String label,double value){}
    public record ReportRow(String number,String date,String party,double amount,String status){}
    public record ReportBundle(double sales,double purchase,double profit,double receivables,double stock,long low,long customers,List<PointDto> customerPoints,List<PointDto> itemPoints,List<ReportRow> salesRows,List<ReportRow> purchaseRows,double salesPaid,double payables,double purchasesPaid,long items,long out,long salesCount,long purchaseCount,double averageSale){}
    public record ReminderDto(Long id,String title,String referenceNo,String dueDate,String priority,String notes,String status,String createdBy,String snoozedUntil){}
    public record NotificationDto(long id,String title,String message,String severity,String category,boolean read,String targetFxml,String referenceNo,String moduleKey,Long recordId,String actionCode,long createdAt){}
    public record NotificationCreate(String title,String message,String severity,String category,String targetFxml,String referenceNo,String moduleKey,Long recordId,String actionCode){
        public NotificationCreate(String title,String message,String severity,String category,String targetFxml,String referenceNo){this(title,message,severity,category,targetFxml,referenceNo,null,null,null);}
    }
    public record CountDto(long count){} public record ShellCounts(int notifications,int email,int whatsapp,int reminders){} public record Ok(boolean success,String message){}
}
