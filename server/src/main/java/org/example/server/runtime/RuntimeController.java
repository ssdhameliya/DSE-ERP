package org.example.server.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.example.shared.RuntimeContract;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lightweight health endpoint used by the JavaFX Phase-5 API runtime bootstrap. */
@RestController
@RequestMapping("/api/runtime")
public class RuntimeController {
    private final RuntimeService runtimeService;
    private final String version;
    private final String apiRevision;

    public RuntimeController(RuntimeService runtimeService, @Value("${dse.app.version:" + RuntimeContract.APP_VERSION + "}") String version, @Value("${dse.api.revision:spring-hibernate-jpa-v1}") String apiRevision) {
        this.runtimeService = runtimeService;
        this.version = version;
        this.apiRevision = apiRevision;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            boolean ready = runtimeService.databaseReady();
            result.put("ready", ready);
            result.put("service", RuntimeContract.SERVICE_NAME);
            result.put("version", version);
            result.put("apiRevision", apiRevision);
            result.put("database", "postgresql");
            result.put("message", ready ? "READY" : "Database health check failed");
        } catch (Exception exception) {
            result.put("ready", false);
            result.put("service", RuntimeContract.SERVICE_NAME);
            result.put("version", version);
            result.put("apiRevision", apiRevision);
            result.put("database", "postgresql");
            result.put("message", "Database unavailable: " + exception.getMessage());
        }
        return result;
    }
}
