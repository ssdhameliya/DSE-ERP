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
    private static Properties load() {
        Properties result = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/app-version.properties")) {
            if (in != null) result.load(in);
        } catch (Exception ignored) {}
        return result;
    }
}
