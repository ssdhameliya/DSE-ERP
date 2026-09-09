package org.example.api.authority;

import org.example.api.ApiSession;
import org.example.config.ConfigManager;
import org.example.documentstudio.model.DocumentType;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Downloads the server-canonical business document used by both Desktop Shared Client and Mobile. */
public final class CanonicalDocumentClient {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base=ConfigManager.getDataApiBaseUrl().replaceAll("/+$","");

    public byte[] render(DocumentType type,String number,String format){
        if(type==null||number==null||number.isBlank())throw new IllegalArgumentException("Document type and number are required.");
        try{
            String path="/api/documents/render?type="+enc(type.name())+"&number="+enc(number)+"&format="+enc(format);
            HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(120)).header("Accept","application/octet-stream").GET();
            ApiSession.authorize(b);
            var r=http.send(b.build(),HttpResponse.BodyHandlers.ofByteArray());
            if(r.statusCode()<200||r.statusCode()>=300)throw new IllegalStateException("Canonical document API error ("+r.statusCode()+"): "+new String(r.body(),StandardCharsets.UTF_8));
            return r.body();
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Canonical document download was interrupted",e);}
        catch(Exception e){throw new IllegalStateException("Canonical document could not be downloaded from the company server",e);}
    }
    private static String enc(String value){return URLEncoder.encode(value==null?"":value,StandardCharsets.UTF_8);}
}
