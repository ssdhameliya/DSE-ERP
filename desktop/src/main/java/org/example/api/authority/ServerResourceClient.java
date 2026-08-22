package org.example.api.authority;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class ServerResourceClient {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json=new ObjectMapper();
    private final String base=ConfigManager.getDataApiBaseUrl().replaceAll("/+$","");
    public List<ResourceMeta> list(String type){try{var r=send("GET",path(type,null),null,"application/json");return json.readValue(r.body(),new TypeReference<>(){});}catch(Exception e){throw failure(e);}}
    public byte[] get(String type,String key){try{return sendBytes("GET",path(type,key),null).body();}catch(Exception e){throw failure(e);}}
    public void put(String type,String key,String fileName,byte[] content){try{String p=path(type,key)+"?filename="+enc(fileName)+"&contentType="+enc("application/zip");sendBytes("PUT",p,content);}catch(Exception e){throw failure(e);}}
    public void delete(String type,String key){try{sendBytes("DELETE",path(type,key),null);}catch(Exception e){throw failure(e);}}
    private HttpResponse<String> send(String method,String p,byte[] body,String accept)throws Exception{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+p)).timeout(Duration.ofSeconds(90)).header("Accept",accept);ApiSession.authorize(b);b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());requireOk(r.statusCode(),r.body());return r;}
    private HttpResponse<byte[]> sendBytes(String method,String p,byte[] body)throws Exception{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+p)).timeout(Duration.ofSeconds(90)).header("Accept","application/octet-stream");ApiSession.authorize(b);if(body!=null)b.header("Content-Type","application/octet-stream");b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));var r=http.send(b.build(),HttpResponse.BodyHandlers.ofByteArray());requireOk(r.statusCode(),r.body()==null?"":new String(r.body(),StandardCharsets.UTF_8));return r;}
    private static void requireOk(int status,String body){if(status<200||status>=300)throw new IllegalStateException("Server resource API error ("+status+"): "+body);}
    private static String path(String type,String key){return "/api/authority/resources/"+enc(type)+(key==null?"":"/"+enc(key));}
    private static String enc(String v){return URLEncoder.encode(v==null?"":v,StandardCharsets.UTF_8);}
    private static IllegalStateException failure(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();return new IllegalStateException("Cannot synchronize server-owned templates/assets",e);}
    public record ResourceMeta(String key,String fileName,String contentType,String checksum,String updatedAt,long size){}
}
