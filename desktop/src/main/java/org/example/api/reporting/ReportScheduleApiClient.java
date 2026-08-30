package org.example.api.reporting;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.config.ConfigManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;

/** Client for persistent server-owned Scheduled Reports. */
public final class ReportScheduleApiClient {
    private final HttpClient http = org.example.api.ApiRuntime.HTTP;
    private final com.fasterxml.jackson.databind.ObjectMapper json = org.example.api.ApiRuntime.JSON;
    private final String base;

    public ReportScheduleApiClient() {
        String b = ConfigManager.getDataApiBaseUrl(); while (b.endsWith("/")) b = b.substring(0,b.length()-1);
        base = b + "/api/reporting/schedules";
    }

    public SchedulePage page() { return request("GET", base, null, SchedulePage.class, null); }
    public List<SavedReportOption> savedReports() { return request("GET", base + "/saved-reports", null, null, new TypeReference<List<SavedReportOption>>(){}); }
    public ScheduleRow create(ScheduleRequest value) { return request("POST", base, value, ScheduleRow.class, null); }
    public ScheduleRow update(long id, ScheduleRequest value) { return request("PUT", base + "/" + id, value, ScheduleRow.class, null); }
    public Result run(long id) { return request("POST", base + "/" + id + "/run", null, Result.class, null); }
    public Result pause(long id) { return request("POST", base + "/" + id + "/pause", null, Result.class, null); }
    public Result resume(long id) { return request("POST", base + "/" + id + "/resume", null, Result.class, null); }
    public ScheduleRow duplicate(long id) { return request("POST", base + "/" + id + "/duplicate", null, ScheduleRow.class, null); }
    public Result delete(long id) { return request("DELETE", base + "/" + id, null, Result.class, null); }
    public List<RunHistory> history(long id) { return request("GET", base + "/" + id + "/history", null, null, new TypeReference<List<RunHistory>>(){}); }

    private <T>T request(String method,String uri,Object body,Class<T> cls,TypeReference<T> type){
        try{
            HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofMinutes(4)).header("Accept","application/json");
            org.example.api.ApiSession.authorize(b);
            if(body!=null)b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            else b.method(method,HttpRequest.BodyPublishers.noBody());
            HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
            if(r.statusCode()<200||r.statusCode()>=300){
                org.example.api.ApiRuntime.logHttpFailure("Scheduled reporting request",r.statusCode(),r.body());
                throw new IllegalStateException(org.example.api.ApiRuntime.userMessage("the scheduled reporting operation",r.statusCode(),r.body()));
            }
            return type!=null?json.readValue(r.body(),type):json.readValue(r.body(),cls);
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Scheduled reporting request interrupted",e);}
        catch(IOException|IllegalArgumentException e){throw new IllegalStateException("Cannot reach scheduled reporting service",e);}
    }

    public record ScheduleRow(long id,String name,String savedReport,String reportTitle,String datePreset,String frequency,Integer dayOfWeek,Integer dayOfMonth,Integer monthOfYear,String time,String format,String delivery,String recipients,String nextRun,String lastRun,String status,String lastStatus,String lastError){}
    public record ScheduleRequest(String name,String savedReport,String frequency,Integer dayOfWeek,Integer dayOfMonth,Integer monthOfYear,String time,String format,String delivery,String recipients){}
    public record ScheduleSummary(long activeSchedules,String nextRun,String nextSchedule,long reportsThisMonth,long failuresLast30Days){}
    public record SavedReportOption(String name,String reportId,String title,String datePreset){ @Override public String toString(){return name+(title==null||title.isBlank()?"":"  —  "+title);} }
    public record RunHistory(long id,long scheduleId,String startedAt,String finishedAt,String status,String reportTitle,String format,String delivery,long rowCount,String artifacts,String error,String triggeredBy){}
    public record SchedulePage(List<ScheduleRow> schedules,ScheduleSummary summary){}
    public record Result(boolean success,String message){}
}
