package org.example.config;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ConfigManager {

    private static final Path CONFIG_FOLDER_PATH = resolveAppDataFolder();
    private static final Path CONFIG_FILE_PATH = CONFIG_FOLDER_PATH.resolve("config.properties");
    private static final Properties properties = new Properties();

    private ConfigManager() {}

    public static synchronized void load() {
        try {
            Files.createDirectories(CONFIG_FOLDER_PATH);

            File file = CONFIG_FILE_PATH.toFile();
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    properties.clear();
                    properties.load(fis);
                }
            } else {
                try (InputStream defaults = ConfigManager.class.getResourceAsStream("/config.properties")) {
                    if (defaults != null) properties.load(defaults);
                }
                properties.remove("smtp.appPassword");
                properties.remove("db.url");
                save();
            }

            System.out.println("Config File : " + file.getAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load ERP configuration", e);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(CONFIG_FOLDER_PATH);
            try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE_PATH.toFile())) {
                properties.store(fos, "JavaApp ERP Configuration");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save ERP configuration", e);
        }
    }

    public static synchronized String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static synchronized void set(String key, String value) {
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
        save();
    }

    public static synchronized void remove(String key) {
        properties.remove(key);
        save();
    }

    public static String getDbUrl() {
        return get("db.url", "jdbc:sqlite:" + CONFIG_FOLDER_PATH.resolve("JavaAppERP.db"));
    }

    /**
     * Resolves the actual file used by the configured SQLite JDBC URL.
     * This keeps Backup & Restore aligned with DatabaseManager even when db.url is customized.
     */
    public static Path getDatabasePath() {
        String url = getDbUrl();
        final String prefix = "jdbc:sqlite:";
        if (url == null || !url.startsWith(prefix)) {
            throw new IllegalStateException("Only SQLite database URLs are supported: " + url);
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

            if (!path.isAbsolute()) {
                path = Path.of(System.getProperty("user.dir")).resolve(path);
            }
            return path.toAbsolutePath().normalize();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to resolve SQLite database path from: " + url, exception);
        }
    }

    public static Path getConfigFolder() {
        return CONFIG_FOLDER_PATH;
    }

    public static Path getBackupFolder() {
        return CONFIG_FOLDER_PATH.resolve("Backups");
    }

    public static Path getPendingRestoreFile() {
        return CONFIG_FOLDER_PATH.resolve("restore-pending.db");
    }

    public static Path getBackupTrashFolder() {
        return getBackupFolder().resolve(".trash");
    }

    private static Path resolveAppDataFolder() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".dse-erp")
                : Path.of(appData, "DSE ERP");
        return base.toAbsolutePath().normalize();
    }
}
