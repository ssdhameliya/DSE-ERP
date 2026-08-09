package org.example.config;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ConfigManager {
    private static final String DEFAULT_POSTGRES_URL = "jdbc:postgresql://localhost:5432/dse_erp";
    private static final Properties properties = new Properties();

    private ConfigManager() {}

    public static synchronized void load() {
        if (!WorkspaceManager.isConfigured()) {
            throw new IllegalStateException("Workspace must be selected before loading configuration.");
        }
        Path configFolder = WorkspaceManager.getConfigurationFolder();
        Path configFile = configFolder.resolve("config.properties");
        try {
            Files.createDirectories(configFolder);
            properties.clear();
            if (Files.isRegularFile(configFile)) {
                try (InputStream input = Files.newInputStream(configFile)) {
                    properties.load(input);
                }
            } else {
                try (InputStream defaults = ConfigManager.class.getResourceAsStream("/config.properties")) {
                    if (defaults != null) properties.load(defaults);
                }
                properties.remove("smtp.appPassword");
                properties.remove("db.url");
                save();
            }
            System.out.println("Workspace   : " + WorkspaceManager.getWorkspaceRoot());
            System.out.println("Config File : " + configFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load ERP configuration", exception);
        }
    }

    public static synchronized void save() {
        Path configFolder = WorkspaceManager.getConfigurationFolder();
        Path configFile = configFolder.resolve("config.properties");
        try {
            Files.createDirectories(configFolder);
            try (OutputStream output = Files.newOutputStream(configFile)) {
                properties.store(output, "DSE ERP Configuration");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save ERP configuration", exception);
        }
    }

    public static synchronized String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static synchronized void set(String key, String value) {
        if (value == null) properties.remove(key); else properties.setProperty(key, value);
        save();
    }

    public static synchronized void setWithoutSaving(String key, String value) {
        if (value == null) properties.remove(key); else properties.setProperty(key, value);
    }

    public static synchronized void remove(String key) {
        properties.remove(key);
        save();
    }

    public static String getDbUrl() {
        Path legacyDatabase = WorkspaceManager.isConfigured()
                ? WorkspaceManager.getDatabaseFolder().resolve("JavaAppERP.db")
                : null;
        return resolveDbUrl(properties.getProperty("db.url"), System.getenv("DSE_DB_URL"), legacyDatabase);
    }

    static String resolveDbUrl(String configuredUrl, String environmentUrl, Path legacyDatabase) {
        if (configuredUrl != null && !configuredUrl.isBlank()) return configuredUrl.trim();
        if (environmentUrl != null && !environmentUrl.isBlank()) return environmentUrl.trim();
        if (legacyDatabase != null && Files.isRegularFile(legacyDatabase)) {
            return "jdbc:sqlite:" + legacyDatabase.toAbsolutePath().normalize();
        }
        return DEFAULT_POSTGRES_URL;
    }

    public static String getDbUsername() {
        return get("db.username", System.getenv().getOrDefault("DSE_DB_USERNAME", "dse_erp_app"));
    }

    public static String getDbPassword() {
        return get("db.password", System.getenv().getOrDefault("DSE_DB_PASSWORD", ""));
    }

    public static boolean isSqlite() {
        return getDbUrl().startsWith("jdbc:sqlite:");
    }

    public static boolean isPostgreSql() {
        return getDbUrl().startsWith("jdbc:postgresql:");
    }

    public static String getDatabaseDescription() {
        return isSqlite() ? getDatabasePath().toString() : getDbUrl();
    }

    public static Path getDatabasePath() {
        String url = getDbUrl();
        final String prefix = "jdbc:sqlite:";
        if (url == null || !url.startsWith(prefix)) {
            throw new IllegalStateException("A SQLite file path was requested while using: " + url);
        }
        String value = url.substring(prefix.length()).trim();
        if (value.isBlank() || value.equals(":memory:")) {
            throw new IllegalStateException("Backup & Restore requires a file-based SQLite database.");
        }
        try {
            Path path;
            if (value.startsWith("file:")) {
                String uriValue = value;
                int query = uriValue.indexOf('?');
                if (query >= 0) uriValue = uriValue.substring(0, query);
                path = Path.of(URI.create(uriValue));
            } else {
                int query = value.indexOf('?');
                if (query >= 0) value = value.substring(0, query);
                path = Path.of(value);
            }
            if (!path.isAbsolute()) path = WorkspaceManager.getWorkspaceRoot().resolve(path);
            return path.toAbsolutePath().normalize();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to resolve SQLite database path from: " + url, exception);
        }
    }

    /** Existing callers use this as the common ERP data root. */
    public static Path getConfigFolder() { return WorkspaceManager.getWorkspaceRoot(); }
    public static Path getConfigurationFolder() { return WorkspaceManager.getConfigurationFolder(); }
    public static Path getBackupFolder() { return WorkspaceManager.getBackupFolder(); }
    public static Path getPendingRestoreFile() { return WorkspaceManager.getTempFolder().resolve("restore-pending.db"); }
    public static Path getBackupTrashFolder() { return getBackupFolder().resolve(".trash"); }
}
