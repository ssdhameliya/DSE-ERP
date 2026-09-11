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
    private final String minimumSupportedDesktopVersion;
    private final String minimumSupportedAndroidVersion;
    private final String latestAndroidVersion;
    private final String minimumSupportedIosVersion;
    private final String latestIosVersion;
    private final String environment;

    public RuntimeController(RuntimeService runtimeService,
                             @Value("${dse.app.version:DEV}") String version,
                             @Value("${dse.api.revision:" + RuntimeContract.API_REVISION + "}") String apiRevision,
                             @Value("${dse.build.revision:DEV}") String buildRevision,
                             @Value("${dse.minimum-supported-desktop-version:}") String minimumSupportedDesktopVersion,
                             @Value("${dse.minimum-supported-android-version:}") String minimumSupportedAndroidVersion,
                             @Value("${dse.latest-android-version:}") String latestAndroidVersion,
                             @Value("${dse.minimum-supported-ios-version:}") String minimumSupportedIosVersion,
                             @Value("${dse.latest-ios-version:}") String latestIosVersion,
                             @Value("${dse.deployment.environment:LOCAL}") String environment) {
        this.runtimeService = runtimeService;
        this.version = version;
        this.apiRevision = apiRevision;
        this.buildRevision = buildRevision;
        this.minimumSupportedDesktopVersion = configuredOrDefault(minimumSupportedDesktopVersion, RuntimeContract.desktopCompatibilityBaseline());
        this.minimumSupportedAndroidVersion = configuredOrDefault(minimumSupportedAndroidVersion, RuntimeContract.androidCompatibilityBaseline());
        this.latestAndroidVersion = configuredOrDefault(latestAndroidVersion, RuntimeContract.androidLatestVersion());
        this.minimumSupportedIosVersion = configuredOrDefault(minimumSupportedIosVersion, RuntimeContract.iosCompatibilityBaseline());
        this.latestIosVersion = configuredOrDefault(latestIosVersion, RuntimeContract.iosLatestVersion());
        this.environment = normalizeEnvironment(environment);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            boolean ready = runtimeService.databaseReady();
            addContract(result);
            result.put("ready", ready);
            result.put("database", "postgresql");
            result.put("databaseName", runtimeService.databaseName());
            result.put("databaseTimeZone", runtimeService.databaseTimeZone());
            addBusinessTime(result);
            result.put("message", ready ? "READY" : "Database health check failed");
        } catch (Exception exception) {
            addContract(result);
            result.put("ready", false);
            result.put("database", "postgresql");
            result.put("databaseName", "unavailable");
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
        result.put("minimumSupportedDesktopVersion", minimumSupportedDesktopVersion);
        result.put("latestDesktopVersion", version);
        result.put("minimumSupportedAndroidVersion", minimumSupportedAndroidVersion);
        result.put("latestAndroidVersion", latestAndroidVersion);
        result.put("minimumSupportedIosVersion", minimumSupportedIosVersion);
        result.put("latestIosVersion", latestIosVersion);
        result.put("environment", environment);
    }

    private static String configuredOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalizeEnvironment(String value) {
        String env = value == null ? "LOCAL" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (env) { case "UAT", "PROD", "LOCAL" -> env; default -> "UNKNOWN"; };
    }

    private static void addBusinessTime(Map<String, Object> result) {
        result.put("businessZone", BusinessClock.zone().getId());
        result.put("businessDate", BusinessClock.today().toString());
        result.put("utcTime", BusinessClock.nowUtcText());
        result.put("dateFormat", BusinessClock.datePattern());
        result.put("timePolicy", "ISO_DATE_UTC_INSTANT");
    }
}
