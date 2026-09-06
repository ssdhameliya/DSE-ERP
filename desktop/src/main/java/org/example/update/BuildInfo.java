package org.example.update;

import org.example.shared.RuntimeContract;
import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {
    private static final Properties PROPERTIES = load();
    private BuildInfo() {}
    public static String version() { return resolved("version", RuntimeContract.appVersion()); }
    public static String buildRevision() { return resolved("buildRevision", RuntimeContract.buildRevision()); }
    public static String buildTime() { return resolved("buildTime", RuntimeContract.buildTime()); }
    public static int databaseMigrationVersion() { return intValue("databaseMigrationVersion", 1); }
    public static int databaseMinCompatibleVersion() { return intValue("databaseMinCompatibleVersion", databaseMigrationVersion()); }
    public static int databaseMaxCompatibleVersion() { return intValue("databaseMaxCompatibleVersion", databaseMigrationVersion()); }
    public static String databaseCompatibilitySinceVersion() { return resolved("databaseCompatibilitySinceVersion", version()); }
    public static int workspaceSchemaVersion() { return intValue("workspaceSchemaVersion", 1); }
    private static int intValue(String key, int fallback) {
        try { return Integer.parseInt(PROPERTIES.getProperty(key, Integer.toString(fallback)).trim()); }
        catch (Exception ignored) { return fallback; }
    }
    private static String resolved(String key, String fallback) {
        String value=PROPERTIES.getProperty(key, "").trim();
        if (value.isBlank() || value.contains("${") || value.contains("@")) return fallback;
        return value;
    }
    private static Properties load() {
        Properties result = new Properties();
        try (InputStream in = org.example.util.ResourceLocator.open("/app-version.properties")) {
            if (in != null) result.load(in);
        } catch (Exception ignored) {}
        return result;
    }
}
