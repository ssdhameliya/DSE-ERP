package org.example.shared;

import java.io.InputStream;
import java.util.Properties;

/** Runtime compatibility contract shared by desktop and server. */
public final class RuntimeContract {
    public static final String SERVICE_NAME = "dse-erp-server";
    public static final String HEALTH_PATH = "/api/runtime/health";
    public static final String API_REVISION = "spring-security-bearer-v5";
    /** Compile-time annotation fallback only; runtime identity comes from filtered build metadata. */
    public static final String APP_VERSION = "DEV";
    /** Compile-time annotation fallback only; runtime identity comes from filtered build metadata. */
    public static final String BUILD_REVISION = "DEV";
    public static final String BUILD_TIME = "";
    private static final Properties BUILD = loadBuild();
    private RuntimeContract() {}
    public static String appVersion() { return resolved("app.version", APP_VERSION); }
    public static String buildRevision() { return resolved("build.revision", BUILD_REVISION); }
    public static String buildTime() { return resolved("build.time", BUILD_TIME); }
    public static String desktopCompatibilityBaseline() { return resolved("desktop.compatibility.baseline", "10.0.1"); }
    public static String androidCompatibilityBaseline() { return resolved("android.compatibility.baseline", "1.2.3"); }
    public static String androidLatestVersion() { return resolved("android.latest.version", "1.2.3"); }
    public static String iosCompatibilityBaseline() { return resolved("ios.compatibility.baseline", "1.2.3"); }
    public static String iosLatestVersion() { return resolved("ios.latest.version", "1.2.3"); }
    private static String resolved(String key, String fallback) {
        String value = BUILD.getProperty(key, "").trim();
        if (value.isBlank() || value.contains("${") || value.contains("@")) return fallback;
        return value;
    }
    private static Properties loadBuild() {
        Properties p = new Properties();
        try (InputStream in = RuntimeContract.class.getResourceAsStream("/runtime-contract.properties")) {
            if (in != null) p.load(in);
        } catch (Exception ignored) {}
        return p;
    }
}
