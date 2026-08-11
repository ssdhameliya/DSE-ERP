package org.example.api.setup;

import org.example.config.ConfigManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** First-run setup is persisted by Spring; JavaFX never writes setup data directly to PostgreSQL. */
public final class SetupApiClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    public void bootstrap(String companyName, String phone, String companyEmail, String gstin, String address,
                          String adminName, String adminUsername, String adminEmail, String adminPassword) {
        String json = "{" +
                "\"companyName\":\"" + esc(companyName) + "\"," +
                "\"phone\":\"" + esc(phone) + "\"," +
                "\"companyEmail\":\"" + esc(companyEmail) + "\"," +
                "\"gstin\":\"" + esc(gstin) + "\"," +
                "\"address\":\"" + esc(address) + "\"," +
                "\"adminName\":\"" + esc(adminName) + "\"," +
                "\"adminUsername\":\"" + esc(adminUsername) + "\"," +
                "\"adminEmail\":\"" + esc(adminEmail) + "\"," +
                "\"adminPassword\":\"" + esc(adminPassword) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(ConfigManager.getDataApiBaseUrl()+"/api/setup/bootstrap"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200 || response.statusCode()>=300) throw new IllegalStateException("Setup API failed ("+response.statusCode()+"): "+response.body());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Setup interrupted",e); }
        catch (Exception e) { if(e instanceof IllegalStateException ise) throw ise; throw new IllegalStateException("Unable to complete setup through Spring API",e); }
    }
    private static String esc(String value){ if(value==null)return ""; return value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r"); }
}
