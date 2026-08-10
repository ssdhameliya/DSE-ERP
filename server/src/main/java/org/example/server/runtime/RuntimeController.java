package org.example.server.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lightweight health endpoint used by the JavaFX Phase-5 API runtime bootstrap. */
@RestController
@RequestMapping("/api/runtime")
public class RuntimeController {
    private final JdbcTemplate jdbc;
    private final String version;
    private final String apiRevision;

    public RuntimeController(JdbcTemplate jdbc, @Value("${dse.app.version:5.0.6}") String version, @Value("${dse.api.revision:bank-reconciliation-v1}") String apiRevision) {
        this.jdbc = jdbc;
        this.version = version;
        this.apiRevision = apiRevision;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            boolean ready = one != null && one == 1;
            result.put("ready", ready);
            result.put("service", "dse-erp-server");
            result.put("version", version);
            result.put("apiRevision", apiRevision);
            result.put("database", "postgresql");
            result.put("message", ready ? "READY" : "Database health check failed");
        } catch (Exception exception) {
            result.put("ready", false);
            result.put("service", "dse-erp-server");
            result.put("version", version);
            result.put("apiRevision", apiRevision);
            result.put("database", "postgresql");
            result.put("message", "Database unavailable: " + exception.getMessage());
        }
        return result;
    }
}
