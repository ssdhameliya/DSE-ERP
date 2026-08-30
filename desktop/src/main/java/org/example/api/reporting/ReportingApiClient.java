package org.example.api.reporting;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.config.ConfigManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Client for the 9.0.40 server-owned reporting calculation engine. */
public final class ReportingApiClient {
    private final HttpClient http = org.example.api.ApiRuntime.HTTP;
    private final com.fasterxml.jackson.databind.ObjectMapper json = org.example.api.ApiRuntime.JSON;
    private final String base;

    public ReportingApiClient() {
        String b = ConfigManager.getDataApiBaseUrl();
        while (b.endsWith("/")) b = b.substring(0,b.length()-1);
        base = b;
    }

    public List<ReportDefinition> definitions() { return get("/api/reporting/definitions", new TypeReference<List<ReportDefinition>>(){}); }
    public ReportFilters filters() { return get("/api/reporting/filters", ReportFilters.class); }
    public ReportResult run(ReportRequest request) { return request("POST","/api/reporting/run",request,ReportResult.class,null); }

    private <T>T get(String path,Class<T> type){return request("GET",path,null,type,null);}
    private <T>T get(String path,TypeReference<T> type){return request("GET",path,null,null,type);}
    private <T>T request(String method,String path,Object body,Class<T> cls,TypeReference<T> type){
        try{
            HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(60)).header("Accept","application/json");
            org.example.api.ApiSession.authorize(b);
            if(body!=null)b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            else b.method(method,HttpRequest.BodyPublishers.noBody());
            HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
            if(r.statusCode()<200||r.statusCode()>=300){
                org.example.api.ApiRuntime.logHttpFailure("Reporting request",r.statusCode(),r.body());
                throw new IllegalStateException(org.example.api.ApiRuntime.userMessage("the reporting operation",r.statusCode(),r.body()));
            }
            return type!=null?json.readValue(r.body(),type):json.readValue(r.body(),cls);
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Reporting request interrupted",e);}
        catch(IOException|IllegalArgumentException e){throw new IllegalStateException("Cannot reach reporting server at "+base,e);}
    }

    public record ReportDefinition(String id,String category,String title,String description,List<String> groupByOptions,List<String> supportedFilters){}
    public record ReportFilters(List<String> parties,List<String> customers,List<String> suppliers,List<String> items,List<String> salespeople,List<String> documentStatuses,List<String> paymentStatuses,List<String> returnStatuses,List<String> gstRates,List<String> warehouses,List<String> bankStatuses){}
    public record ReportRequest(String reportId,String from,String to,String party,String item,String salesperson,String documentStatus,String paymentStatus,String returnStatus,String gstRate,String warehouse,String bankStatus,String search,String groupBy,String sortKey,String sortDirection,Double minAmount,Double maxAmount,Integer page,Integer size,List<String> visibleColumns){}
    public record ReportColumn(String key,String label,String type,boolean defaultVisible,boolean numeric,double preferredWidth){}
    public record ReportMetric(String key,String label,double value,String format,String note){}
    public record ReportRow(String rowKey,List<String> values,String groupKey,String targetFxml,Long targetId,String referenceNo){}
    public record ReportResult(String reportId,String title,String description,String periodFrom,String periodTo,List<ReportMetric> metrics,List<ReportColumn> columns,List<ReportRow> rows,long totalRows,int page,int size,int totalPages,List<String> groupByOptions,Map<String,String> appliedFilters,Map<String,String> totals,String generatedAt,String generatedBy){}
}
