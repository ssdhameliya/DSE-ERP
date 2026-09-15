package org.example.api.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.ConfigManager;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class AuditApiClient {
    private final HttpClient http=org.example.api.ApiRuntime.HTTP;
    private final ObjectMapper json=org.example.api.ApiRuntime.JSON;
    private static String base(){String b=ConfigManager.getDataApiBaseUrl();while(b.endsWith("/"))b=b.substring(0,b.length()-1);return b;}
    public List<EventRow> record(String type,long id){return get("/api/audit/record?type="+enc(type)+"&id="+id,new TypeReference<List<EventRow>>(){});}
    public GlobalPage global(int page,int size,String module,String action,String user,String reference,String q){return get("/api/audit/global?page="+Math.max(0,page)+"&size="+Math.max(20,Math.min(size,200))+"&module="+enc(module)+"&action="+enc(action)+"&user="+enc(user)+"&reference="+enc(reference)+"&q="+enc(q),new TypeReference<GlobalPage>(){});}
    private <T>T get(String path,TypeReference<T> type){try{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base()+path)).timeout(Duration.ofSeconds(30)).header("Accept","application/json").GET();org.example.api.ApiSession.authorize(b);var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()<200||r.statusCode()>=300)throw new IllegalStateException("Audit API error ("+r.statusCode()+"): "+r.body());return json.readValue(r.body(),type);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}catch(IOException e){throw new IllegalStateException("Cannot reach audit API at "+base(),e);}}
    private static String enc(String v){return URLEncoder.encode(v==null?"":v,StandardCharsets.UTF_8);}
    public record ChangeRow(long id,String fieldName,String oldValue,String newValue){}
    public record EventRow(long id,String entityType,long entityId,String referenceNo,String action,String category,String detail,String createdBy,String createdAt,String source,String legacySource,List<ChangeRow> changes){}
    public record GlobalPage(List<EventRow> rows,long total,int page,int size,int totalPages,long businessChanges,long financialEvents,long documentEvents,long communications){}
}
