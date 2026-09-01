package org.example.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
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
        if (status == 404) return serverMessage.isBlank() ? "The requested ERP endpoint or record could not be found. Confirm that the desktop and server are the same DSE ERP version." : serverMessage;
        if (status == 409) return serverMessage.isBlank() ? "This operation conflicts with the latest ERP data. Reload and try again." : serverMessage;
        if (status >= 400 && status < 500) return serverMessage.isBlank() ? "Please review the entered information and try again." : serverMessage;
        String operation = area == null || area.isBlank() ? "this request" : area.trim();
        return "The ERP server could not complete " + operation + ". Please try again. If the problem continues, check the server log.";
    }

    /** Converts network/JSON failures into a precise message instead of reporting every failure as "server unreachable". */
    public static String transportMessage(String area, String baseUrl, Throwable failure) {
        String operation = area == null || area.isBlank() ? "ERP request" : area.trim();
        if (hasType(failure, "HttpTimeoutException") || hasType(failure, "TimeoutException"))
            return operation + " timed out while waiting for the ERP server at " + baseUrl + ".";
        if (hasType(failure, "ConnectException") || hasType(failure, "UnknownHostException") || hasType(failure, "NoRouteToHostException"))
            return "Cannot connect to the ERP server at " + baseUrl + ". Check that the current DSE ERP backend is running.";
        if (hasType(failure, "JsonProcessingException") || hasType(failure, "JsonMappingException")
                || hasType(failure, "MismatchedInputException") || hasType(failure, "InvalidDefinitionException"))
            return "The ERP request/response could not be converted safely. Confirm that desktop and server are the same application/build version and check desktop.log.";
        return operation + " failed while communicating with the ERP server at " + baseUrl + ".";
    }

    private static boolean hasType(Throwable failure, String simpleName) {
        Throwable current = failure;
        while (current != null) {
            String type = current.getClass().getName();
            if (type.endsWith("." + simpleName) || type.contains(simpleName)) return true;
            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
        }
        return false;
    }

    public static void logHttpFailure(String area, int status, String body) {
        String label = area == null || area.isBlank() ? "API request" : area;
        System.err.println(label + " failed with HTTP " + status + (body == null || body.isBlank() ? "" : ": " + body));
    }
}
