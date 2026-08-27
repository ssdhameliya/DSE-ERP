package org.example.config;

import org.example.shared.SecretValueCodec;

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
    private static volatile String runtimeBusinessZone;
    private static volatile String runtimeBusinessDateFormat;

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
                try (InputStream defaults = org.example.util.ResourceLocator.open("/config.properties")) {
                    if (defaults != null) properties.load(defaults);
                }
                properties.remove("smtp.appPassword");
                properties.remove("db.url");
                save();
            }
            String smtpSecret=properties.getProperty("smtp.appPassword");
            if(smtpSecret!=null&&!smtpSecret.isBlank()&&!SecretValueCodec.isEncrypted(smtpSecret)){properties.setProperty("smtp.appPassword",SecretValueCodec.encrypt(smtpSecret.replaceAll("\\s+","")));save();}
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
        if (shouldUseServerSetting(key)) {
            try { String remote=new org.example.api.support.SupportApiClient().setting(key, defaultValue);return SharedAssetBridge.isAssetKey(key)?SharedAssetBridge.resolve(key,remote):remote; }
            catch (org.example.api.ApiSession.AuthenticationRequiredException ignored) { }
        }
        String value=properties.getProperty(key, defaultValue);
        return "smtp.appPassword".equals(key)?SecretValueCodec.decrypt(value):value;
    }

    public static synchronized void set(String key, String value) {
        if (shouldUseServerSetting(key) && org.example.api.ApiSession.token() != null) {
            String remote=SharedAssetBridge.isAssetKey(key)?SharedAssetBridge.publish(key,value):value;
            new org.example.api.support.SupportApiClient().setSetting(key, remote == null ? "" : remote);
        }
        if (value == null) properties.remove(key); else properties.setProperty(key, "smtp.appPassword".equals(key)?SecretValueCodec.encrypt(value.replaceAll("\\s+","")):value);
        save();
    }

    private static boolean shouldUseServerSetting(String key) {
        if (key != null && key.equals("payment.bankMatchRoundingTolerance") && org.example.api.ApiSession.token() != null) return true;
        if (getConfiguredDeploymentMode() != DeploymentMode.SHARED_CLIENT
                || key == null || org.example.api.ApiSession.token() == null) return false;
        return key.startsWith("company.") || key.startsWith("payment.") || key.startsWith("invoice.")
                || key.startsWith("business.") || key.startsWith("tax.") || key.startsWith("reference.")
                || key.equals("date.format") || key.equals("timezone.business");
    }

    public static synchronized void setWithoutSaving(String key, String value) {
        if (shouldUseServerSetting(key)) {
            String remote=SharedAssetBridge.isAssetKey(key)?SharedAssetBridge.publish(key,value):value;
            new org.example.api.support.SupportApiClient().setSetting(key, remote == null ? "" : remote);
        }
        if (value == null) properties.remove(key); else properties.setProperty(key, "smtp.appPassword".equals(key)?SecretValueCodec.encrypt(value.replaceAll("\\s+","")):value);
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
            throw new IllegalStateException("DSE ERP 9.0.22 production runtime requires PostgreSQL. Invalid database URL: " + value);
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
        return getCanonicalApiBaseUrl();
    }

    /** Raw configured/runtime endpoint used before login and by runtime bootstrap. */
    public static String getDataApiBaseUrlUnbound() {
        return getCanonicalApiBaseUrlUnbound();
    }

    private static synchronized String getCanonicalApiBaseUrl() {
        String sessionBase = org.example.api.ApiSession.boundApiBaseUrl();
        if (sessionBase != null && !sessionBase.isBlank()) return sessionBase;
        return getCanonicalApiBaseUrlUnbound();
    }

    private static synchronized String getCanonicalApiBaseUrlUnbound() {
        String runtimeAuth = normalizeApiUrl(runtimeAuthApiBaseUrl);
        String runtimeData = normalizeApiUrl(runtimeDataApiBaseUrl);
        if (!runtimeAuth.isBlank() || !runtimeData.isBlank()) {
            if (!runtimeAuth.isBlank() && !runtimeData.isBlank() && !runtimeAuth.equalsIgnoreCase(runtimeData)) {
                throw new IllegalStateException("Authentication and business data must use the same DSE ERP server. Runtime endpoints differ: " + runtimeAuth + " vs " + runtimeData);
            }
            return !runtimeData.isBlank() ? runtimeData : runtimeAuth;
        }
        if (isSharedClient()) {
            String server = normalizeApiUrl(getConfiguredServerUrl());
            if (server.isBlank()) throw new IllegalStateException("Shared-client mode requires a company server address.");
            return server;
        }

        String envAuth = normalizeApiUrl(System.getenv("DSE_AUTH_API_URL"));
        String envData = normalizeApiUrl(System.getenv("DSE_DATA_API_URL"));
        String configuredAuth = normalizeApiUrl(properties.getProperty("auth.api.baseUrl"));
        String configuredData = normalizeApiUrl(properties.getProperty("data.api.baseUrl"));
        String auth = !envAuth.isBlank() ? envAuth : configuredAuth;
        String data = !envData.isBlank() ? envData : configuredData;
        if (!auth.isBlank() && !data.isBlank() && !auth.equalsIgnoreCase(data)) {
            throw new IllegalStateException("DSE ERP uses one Spring server for authentication and business data. Remove the conflicting auth.api.baseUrl/data.api.baseUrl configuration: " + auth + " vs " + data);
        }
        if (!data.isBlank()) return data;
        if (!auth.isBlank()) return auth;
        return "http://127.0.0.1:8080";
    }

    private static String normalizeApiUrl(String value) {
        if (value == null) return "";
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
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

    public static DeploymentMode getDeploymentMode() {
        return getConfiguredDeploymentMode();
    }

    /**
     * Reads the deployment mode without using the server-aware settings path.
     * Deployment mode decides whether that path may be used, so calling get()
     * here would recursively re-enter shouldUseServerSetting().
     */
    private static DeploymentMode getConfiguredDeploymentMode() {
        String environment = System.getenv("DSE_DEPLOYMENT_MODE");
        return DeploymentMode.parse(environment == null || environment.isBlank()
                ? properties.getProperty("deployment.mode", "LOCAL") : environment);
    }

    public static boolean isSharedClient() { return getDeploymentMode() == DeploymentMode.SHARED_CLIENT; }

    public static synchronized void applyServerBusinessPolicy(String zone,String dateFormat){
        runtimeBusinessZone=zone==null?null:zone.trim();
        runtimeBusinessDateFormat=dateFormat==null?null:dateFormat.trim();
    }
    public static String runtimeBusinessZone(){return runtimeBusinessZone;}
    public static String runtimeBusinessDateFormat(){return runtimeBusinessDateFormat;}

    public static String getConfiguredServerUrl() {
        String environment = System.getenv("DSE_SERVER_URL");
        String value = environment == null || environment.isBlank()
                ? get("server.baseUrl", "") : environment;
        return value == null ? "" : value.trim().replaceAll("/+$", "");
    }

    /** Business data is always served by Spring; there is no desktop persistence mode. */
    public static String getDataMode() { return "api"; }

    public static boolean isApiDataEnabled() { return true; }

    public static String getDataApiBaseUrl() {
        return getCanonicalApiBaseUrl();
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
