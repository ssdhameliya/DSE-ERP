package org.example.config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ConfigManager {
    private static final String DEFAULT_POSTGRES_URL = "jdbc:postgresql://localhost:5432/dse_erp";
    private static final Properties properties = new Properties();
    private static volatile String runtimeDbUrl;
    private static volatile String runtimeDbUsername;
    private static volatile String runtimeDbPassword;
    private static volatile String runtimeAuthApiBaseUrl;
    private static volatile String runtimeDataApiBaseUrl;
    private static volatile String runtimeInternalBridgeToken;

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
        String runtime = runtimeDbUrl;
        if (runtime != null && !runtime.isBlank()) return requirePostgresUrl(runtime);
        String configured = getConfiguredDbUrl();
        if (configured != null) return requirePostgresUrl(configured);
        String environment = getEnvironmentDbUrl();
        if (environment != null) return requirePostgresUrl(environment);
        return DEFAULT_POSTGRES_URL;
    }

    public static synchronized String getConfiguredDbUrl() {
        String value = properties.getProperty("db.url");
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String getEnvironmentDbUrl() {
        String value = System.getenv("DSE_DB_URL");
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String getDefaultPostgresUrl() {
        return DEFAULT_POSTGRES_URL;
    }

    private static String requirePostgresUrl(String url) {
        String value = url == null ? "" : url.trim();
        if (!value.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("DSE ERP 7.3.3 production runtime requires PostgreSQL. Invalid database URL: " + value);
        }
        return value;
    }

    public static String getDbUsername() {
        String runtime = runtimeDbUsername;
        return runtime != null ? runtime : get("db.username", System.getenv().getOrDefault("DSE_DB_USERNAME", "dse_erp_app"));
    }

    public static String getDbPassword() {
        String runtime = runtimeDbPassword;
        return runtime != null ? runtime : get("db.password", System.getenv().getOrDefault("DSE_DB_PASSWORD", ""));
    }

    public static String getSmtpEmail() { return get("smtp.email", "").trim(); }
    public static String getSmtpPassword() { return get("smtp.appPassword", "").replaceAll("\\s+", ""); }
    public static String getSmtpHost() {
        String configured = get("smtp.host", "").trim();
        if (!configured.isBlank()) return configured;
        String email = getSmtpEmail().toLowerCase();
        if (email.endsWith("@gmail.com") || email.endsWith("@googlemail.com")) return "smtp.gmail.com";
        if (email.endsWith("@outlook.com") || email.endsWith("@hotmail.com") || email.endsWith("@live.com")) return "smtp.office365.com";
        if (email.endsWith("@yahoo.com") || email.endsWith("@yahoo.in")) return "smtp.mail.yahoo.com";
        return "";
    }
    public static String getSmtpPort() {
        String value = get("smtp.port", "587").trim();
        return value.isBlank() ? "587" : value;
    }

    /** Applies credentials generated by the managed local PostgreSQL runtime without persisting secrets in config.properties. */
    public static synchronized void applyRuntimeDatabase(String url, String username, String password) {
        runtimeDbUrl = url;
        runtimeDbUsername = username;
        runtimeDbPassword = password;
    }

    public static synchronized void clearRuntimeDatabase() {
        runtimeDbUrl = null;
        runtimeDbUsername = null;
        runtimeDbPassword = null;
    }


    /** Authentication is always served by the local Spring backend. */
    public static String getAuthMode() { return "api"; }

    public static boolean isApiAuthenticationEnabled() { return true; }

    public static String getAuthApiBaseUrl() {
        String runtime = runtimeAuthApiBaseUrl;
        if (runtime != null && !runtime.isBlank()) return runtime;
        return get("auth.api.baseUrl", System.getenv().getOrDefault("DSE_AUTH_API_URL", "http://127.0.0.1:8080"));
    }

    public static synchronized void applyRuntimeApiBaseUrl(String baseUrl) {
        runtimeAuthApiBaseUrl = baseUrl;
        runtimeDataApiBaseUrl = baseUrl;
    }

    public static synchronized void clearRuntimeApiBaseUrl() {
        runtimeAuthApiBaseUrl = null;
        runtimeDataApiBaseUrl = null;
    }

    public static synchronized boolean hasExplicitApiBaseUrl() {
        return properties.getProperty("auth.api.baseUrl") != null
                || properties.getProperty("data.api.baseUrl") != null
                || System.getenv("DSE_AUTH_API_URL") != null
                || System.getenv("DSE_DATA_API_URL") != null;
    }

    /** Business data is always served by Spring; there is no desktop persistence mode. */
    public static String getDataMode() { return "api"; }

    public static boolean isApiDataEnabled() { return true; }

    public static String getDataApiBaseUrl() {
        String runtime = runtimeDataApiBaseUrl;
        if (runtime != null && !runtime.isBlank()) return runtime;
        return get("data.api.baseUrl", System.getenv().getOrDefault("DSE_DATA_API_URL", getAuthApiBaseUrl()));
    }


    public static synchronized void applyRuntimeInternalBridgeToken(String token) {
        runtimeInternalBridgeToken = token;
    }

    public static String getRuntimeInternalBridgeToken() {
        String token = runtimeInternalBridgeToken;
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DSE ERP internal Spring bridge token is not initialized");
        }
        return token;
    }

    public static boolean isPostgreSql() {
        return getDbUrl().startsWith("jdbc:postgresql:");
    }

    public static String getDatabaseDescription() {
        return getDbUrl();
    }

    /** Existing callers use this as the common ERP data root. */
    public static Path getConfigFolder() { return WorkspaceManager.getWorkspaceRoot(); }
    public static Path getConfigurationFolder() { return WorkspaceManager.getConfigurationFolder(); }
    public static Path getBackupFolder() { return WorkspaceManager.getBackupFolder(); }
    public static Path getPendingRestoreFile() { return WorkspaceManager.getTempFolder().resolve("restore-pending.pgbackup"); }
    public static Path getBackupTrashFolder() { return getBackupFolder().resolve(".trash"); }
}
