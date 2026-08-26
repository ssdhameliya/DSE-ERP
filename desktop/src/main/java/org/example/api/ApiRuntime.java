package org.example.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;

/** Shared immutable HTTP/JSON runtime used by desktop REST clients. */
public final class ApiRuntime {
    private ApiRuntime() {}

    public static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .version(HttpClient.Version.HTTP_1_1)
        .build();

    public static final ObjectMapper JSON = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Converts an HTTP error payload into a user-facing ERP message. Raw JSON,
     * stack traces and Java exception class names belong in logs, not dialogs.
     */
    public static String userMessage(String area, int status, String body) {
        String serverMessage = "";
        try {
            var node = JSON.readTree(body == null ? "" : body);
            if (node != null && node.hasNonNull("message")) serverMessage = node.get("message").asText("").trim();
        } catch (Exception ignored) {}

        if (status == 401) return "Your session has expired. Please sign in again.";
        if (status == 403) return serverMessage.isBlank() ? "You do not have permission to perform this action." : serverMessage;
        if (status == 404) return serverMessage.isBlank() ? "The requested ERP record could not be found." : serverMessage;
        if (status == 409) return serverMessage.isBlank() ? "This operation conflicts with the latest ERP data. Reload and try again." : serverMessage;
        if (status >= 400 && status < 500) return serverMessage.isBlank() ? "Please review the entered information and try again." : serverMessage;
        String operation = area == null || area.isBlank() ? "this request" : area.trim();
        return "The ERP server could not complete " + operation + ". Please try again. If the problem continues, check the server log.";
    }

    public static void logHttpFailure(String area, int status, String body) {
        String label = area == null || area.isBlank() ? "API request" : area;
        System.err.println(label + " failed with HTTP " + status + (body == null || body.isBlank() ? "" : ": " + body));
    }
}
