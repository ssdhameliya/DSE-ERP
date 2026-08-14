package org.example.update;

import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {
    private static final Properties PROPERTIES = load();
    private BuildInfo() {}
    public static String version() { return PROPERTIES.getProperty("version", UpdateService.DEFAULT_VERSION); }
    public static int databaseMigrationVersion() {
        try { return Integer.parseInt(PROPERTIES.getProperty("databaseMigrationVersion", "1")); }
        catch (NumberFormatException ignored) { return 1; }
    }
    public static int databaseMinCompatibleVersion() {
        try { return Integer.parseInt(PROPERTIES.getProperty("databaseMinCompatibleVersion", String.valueOf(databaseMigrationVersion()))); }
        catch (NumberFormatException ignored) { return databaseMigrationVersion(); }
    }
    public static int databaseMaxCompatibleVersion() {
        try { return Integer.parseInt(PROPERTIES.getProperty("databaseMaxCompatibleVersion", String.valueOf(databaseMigrationVersion()))); }
        catch (NumberFormatException ignored) { return databaseMigrationVersion(); }
    }
    public static int workspaceSchemaVersion() {
        try { return Integer.parseInt(PROPERTIES.getProperty("workspaceSchemaVersion", "1")); }
        catch (NumberFormatException ignored) { return 1; }
    }
    private static Properties load() {
        Properties result = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/app-version.properties")) {
            if (in != null) result.load(in);
        } catch (Exception ignored) {}
        return result;
    }
}
