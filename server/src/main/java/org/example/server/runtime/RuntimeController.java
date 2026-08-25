package org.example.server.runtime;

import org.example.server.util.BusinessClock;
import org.example.shared.RuntimeContract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lightweight health endpoint used by the JavaFX API runtime bootstrap. */
@RestController
@RequestMapping("/api/runtime")
public class RuntimeController {
    private final RuntimeService runtimeService;
    private final String version;
    private final String apiRevision;
    private final String buildRevision;

    public RuntimeController(RuntimeService runtimeService,
                             @Value("${dse.app.version:" + RuntimeContract.APP_VERSION + "}") String version,
                             @Value("${dse.api.revision:" + RuntimeContract.API_REVISION + "}") String apiRevision,
                             @Value("${dse.build.revision:" + RuntimeContract.BUILD_REVISION + "}") String buildRevision) {
        this.runtimeService = runtimeService;
        this.version = version;
        this.apiRevision = apiRevision;
        this.buildRevision = buildRevision;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            boolean ready = runtimeService.databaseReady();
            addContract(result);
            result.put("ready", ready);
            result.put("database", "postgresql");
            result.put("databaseTimeZone", runtimeService.databaseTimeZone());
            addBusinessTime(result);
            result.put("message", ready ? "READY" : "Database health check failed");
        } catch (Exception exception) {
            addContract(result);
            result.put("ready", false);
            result.put("database", "postgresql");
            result.put("databaseTimeZone", "unavailable");
            addBusinessTime(result);
            result.put("message", "Database unavailable");
        }
        return result;
    }

    private void addContract(Map<String, Object> result) {
        result.put("service", RuntimeContract.SERVICE_NAME);
        result.put("version", version);
        result.put("apiRevision", apiRevision);
        result.put("buildRevision", buildRevision);
    }

    private static void addBusinessTime(Map<String, Object> result) {
        result.put("businessZone", BusinessClock.zone().getId());
        result.put("businessDate", BusinessClock.today().toString());
        result.put("utcTime", BusinessClock.nowUtcText());
        result.put("dateFormat", BusinessClock.datePattern());
        result.put("timePolicy", "ISO_DATE_UTC_INSTANT");
    }
}
