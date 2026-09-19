package org.example.backup;

import org.example.config.ConfigManager;
import org.example.util.UiDiagnostics;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Portable workstation preferences layered onto the server-created recovery package.
 * Deployment identity, server URLs, database credentials and other machine-specific values
 * are deliberately excluded so a recovery cannot silently reconnect a LOCAL copy to UAT/PROD.
 */
public final class PortableRecoverySettings {
    public static final String SETTINGS_PREFIX = "settings/";
    public static final String SETTINGS_FILE = SETTINGS_PREFIX + "portable.properties";
    private static final Set<String> PORTABLE_KEYS = Set.of(
            "application.displayName", "application.tagline", "application.startingText",
            "update.checkAtStartup", "update.downloadInBackground"
    );
    private static final Map<String,String> ASSET_KEYS = Map.of(
            "application.brandImagePath", "application-brand",
            "application.markImagePath", "application-mark"
    );

    private PortableRecoverySettings() { }

    public static void augment(Path packageFile) throws IOException {
        if (packageFile == null || !Files.isRegularFile(packageFile)) return;
        Properties portable = captureProperties();
        Path temp = Files.createTempFile(packageFile.getParent(), ".dse-recovery-", ".zip");
        try (ZipFile source = new ZipFile(packageFile.toFile());
             ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
            Properties manifest = new Properties();
            ZipEntry sourceManifest = source.getEntry("manifest.properties");
            if (sourceManifest != null) {
                try (InputStream in = source.getInputStream(sourceManifest)) { manifest.load(in); }
            }

            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\','/');
                if (name.equals("manifest.properties") || name.startsWith(SETTINGS_PREFIX)) continue;
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                if (!entry.isDirectory()) try (InputStream in = source.getInputStream(entry)) { in.transferTo(out); }
                out.closeEntry();
            }

            for (var asset : ASSET_KEYS.entrySet()) {
                String configured = ConfigManager.get(asset.getKey(), "").trim();
                if (configured.isBlank()) continue;
                try {
                    Path file = Path.of(configured).toAbsolutePath().normalize();
                    if (!Files.isRegularFile(file)) continue;
                    String ext = extension(file.getFileName().toString());
                    String entryName = SETTINGS_PREFIX + "assets/" + asset.getValue() + ext;
                    putFile(out, file, entryName);
                    portable.setProperty(asset.getKey(), entryName);
                } catch (Exception ignored) { }
            }
            portable.setProperty("ui.diagnostics.enabled", Boolean.toString(UiDiagnostics.isEnabled()));
            ByteArrayOutputStream props = new ByteArrayOutputStream();
            portable.store(props, "DSE ERP portable recovery settings - no deployment/database credentials");
            putBytes(out, SETTINGS_FILE, props.toByteArray());

            manifest.setProperty("portable.settings", "true");
            manifest.setProperty("portable.settings.file", SETTINGS_FILE);
            ByteArrayOutputStream manifestBytes = new ByteArrayOutputStream();
            manifest.store(manifestBytes, "DSE ERP recovery package");
            putBytes(out, "manifest.properties", manifestBytes.toByteArray());
        }
        try {
            Files.move(temp, packageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, packageFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Properties captureProperties() throws IOException {
        Properties raw = new Properties();
        Path config = ConfigManager.getConfigurationFolder().resolve("config.properties");
        if (Files.isRegularFile(config)) try (InputStream in = Files.newInputStream(config)) { raw.load(in); }
        Properties portable = new Properties();
        for (String key : raw.stringPropertyNames()) {
            if (PORTABLE_KEYS.contains(key) || key.startsWith("shortcut.")) {
                portable.setProperty(key, raw.getProperty(key, ""));
            }
        }
        for (String key : PORTABLE_KEYS) {
            if (!portable.containsKey(key)) portable.setProperty(key, ConfigManager.get(key, ""));
        }
        return portable;
    }

    public static void applyStaged(Path stagedSettings, Path workspace) throws IOException {
        if (stagedSettings == null || !Files.isDirectory(stagedSettings)) return;
        Path propertiesFile = stagedSettings.resolve("portable.properties");
        if (!Files.isRegularFile(propertiesFile)) return;
        Properties portable = new Properties();
        try (InputStream in = Files.newInputStream(propertiesFile)) { portable.load(in); }

        for (String key : portable.stringPropertyNames()) {
            if (PORTABLE_KEYS.contains(key) || key.startsWith("shortcut.")) {
                ConfigManager.setWithoutSaving(key, portable.getProperty(key, ""));
            }
        }

        Path assetsTarget = ConfigManager.getConfigurationFolder().resolve("assets");
        Files.createDirectories(assetsTarget);
        for (var asset : ASSET_KEYS.entrySet()) {
            String entry = portable.getProperty(asset.getKey(), "").replace('\\','/');
            if (!entry.startsWith(SETTINGS_PREFIX + "assets/")) continue;
            Path source = stagedSettings.resolve("assets").resolve(Path.of(entry).getFileName().toString()).normalize();
            if (!source.startsWith(stagedSettings.resolve("assets").normalize()) || !Files.isRegularFile(source)) continue;
            Path target = assetsTarget.resolve(source.getFileName().toString()).normalize();
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            ConfigManager.setWithoutSaving(asset.getKey(), target.toAbsolutePath().toString());
        }
        ConfigManager.save();
        UiDiagnostics.setEnabled(Boolean.parseBoolean(portable.getProperty("ui.diagnostics.enabled", Boolean.toString(UiDiagnostics.isEnabled()))));
    }

    private static String extension(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg")) return ".jpg";
        if (lower.endsWith(".jpeg")) return ".jpeg";
        return ".png";
    }

    private static void putFile(ZipOutputStream out, Path file, String name) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(Files.getLastModifiedTime(file).toMillis());
        out.putNextEntry(entry);
        Files.copy(file, out);
        out.closeEntry();
    }

    private static void putBytes(ZipOutputStream out, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        out.putNextEntry(entry);
        out.write(bytes);
        out.closeEntry();
    }
}
