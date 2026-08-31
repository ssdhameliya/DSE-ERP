package org.example.service;

import javafx.scene.image.Image;
import org.example.config.ConfigManager;

import java.awt.Desktop;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Non-UI storage/preview operations for Settings branding assets.
 *
 * <p>Phase 6 keeps chooser/dialog/control updates in SettingsController and
 * moves file-system/configuration work here. This preserves the existing
 * atomic replacement and rollback behavior while shrinking the JavaFX
 * controller's responsibility.</p>
 */
public final class SettingsAssetService {
    private SettingsAssetService() { }

    public record Selection(Path path, BrandAssetPolicy.Inspection inspection) { }
    public record Stored(Path path, Image previewImage, BrandAssetPolicy.Inspection inspection) { }
    public record Preview(Image image, Path path, BrandAssetPolicy.Inspection inspection) { }

    public static Selection inspect(Path selectedPath, BrandAssetPolicy.Role role) throws Exception {
        Path normalized = selectedPath.toAbsolutePath().normalize();
        return new Selection(normalized, BrandAssetPolicy.inspect(normalized, role));
    }

    public static Stored store(
            String configKey,
            String baseName,
            BrandAssetPolicy.Role role,
            Selection selection,
            String previousConfiguredPath
    ) throws Exception {
        String extension = safeExtension(selection.path().getFileName().toString());
        Path assetsFolder = ConfigManager.getConfigurationFolder().resolve("assets");
        Files.createDirectories(assetsFolder);

        String revision = Long.toUnsignedString(System.nanoTime());
        Path destination = assetsFolder.resolve(baseName + "-" + revision + extension);
        Path temporary = Files.createTempFile(assetsFolder, "." + baseName + "-", ".uploading");
        boolean configCommitted = false;
        try {
            Files.copy(selection.path(), temporary, StandardCopyOption.REPLACE_EXISTING);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Image upload was superseded.");

            Image previewImage = loadPreviewImage(temporary, role);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Image upload was superseded.");
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination);
            }

            if (Thread.currentThread().isInterrupted()) {
                Files.deleteIfExists(destination);
                throw new InterruptedException("Image upload was superseded.");
            }
            try {
                ConfigManager.set(configKey, destination.toAbsolutePath().toString());
                String persisted = ConfigManager.get(configKey, "");
                if (!ConfigManager.isSharedClient() && !destination.toAbsolutePath().toString().equals(persisted)) {
                    throw new IllegalStateException("The saved image path could not be verified.");
                }
                configCommitted = true;
            } catch (Exception configError) {
                ConfigManager.setWithoutSaving(configKey, previousConfiguredPath);
                try { Files.deleteIfExists(destination); } catch (Exception ignored) { }
                throw configError;
            }

            removeOlderManagedAssetVersions(assetsFolder, baseName, destination);
            return new Stored(destination, previewImage, selection.inspection());
        } finally {
            try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
            if (!configCommitted) {
                try { Files.deleteIfExists(destination); } catch (Exception ignored) { }
            }
        }
    }

    public static Preview loadPreview(String configKey, BrandAssetPolicy.Role role) {
        String configuredPath = ConfigManager.get(configKey, "");
        if (configuredPath == null || configuredPath.isBlank()) return new Preview(null, null, null);
        try {
            Path path = Path.of(configuredPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) return new Preview(null, null, null);
            BrandAssetPolicy.Inspection inspection = BrandAssetPolicy.inspect(path, role);
            return new Preview(loadPreviewImage(path, role), path, inspection);
        } catch (Exception ignored) {
            return new Preview(null, null, null);
        }
    }

    public static void openConfigured(String configKey, String label) throws Exception {
        String configured = ConfigManager.get(configKey, "");
        if (configured == null || configured.isBlank()) throw new IllegalStateException("No " + label + " is attached.");
        Path path = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalStateException("The configured " + label + " is unavailable.");
        if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Preview is not supported on this computer.");
        Desktop.getDesktop().open(path.toFile());
    }

    public static void deleteConfiguredFile(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) return;
        try { Files.deleteIfExists(Path.of(configuredPath)); } catch (Exception ignored) { }
    }

    private static String safeExtension(String fileName) {
        String lowerName = fileName == null ? "" : fileName.toLowerCase();
        if (lowerName.endsWith(".jpg")) return ".jpg";
        if (lowerName.endsWith(".jpeg")) return ".jpeg";
        return ".png";
    }

    private static void removeOlderManagedAssetVersions(Path assetsFolder, String baseName, Path keep) {
        try (var files = Files.list(assetsFolder)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> isManagedAssetVersion(path.getFileName().toString(), baseName))
                    .filter(path -> keep == null || !path.toAbsolutePath().normalize().equals(keep.toAbsolutePath().normalize()))
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                    });
        } catch (Exception ignored) {
            // Cleanup is best-effort only after the new path is safely persisted.
        }
    }

    private static boolean isManagedAssetVersion(String fileName, String baseName) {
        if (fileName == null || baseName == null) return false;
        String lower = fileName.toLowerCase();
        String base = baseName.toLowerCase();
        boolean supported = lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        return supported && (lower.equals(base + ".png")
                || lower.equals(base + ".jpg")
                || lower.equals(base + ".jpeg")
                || lower.startsWith(base + "-"));
    }

    private static Image loadPreviewImage(Path path, BrandAssetPolicy.Role role) throws Exception {
        double requestedWidth = switch (role) {
            case APPLICATION_BANNER -> 1200.0;
            case APPLICATION_MARK -> 420.0;
            case COMPANY_LOGO -> 720.0;
            case SIGNATURE -> 720.0;
            case PAYMENT_QR -> 420.0;
        };
        double requestedHeight = switch (role) {
            case APPLICATION_BANNER -> 320.0;
            case APPLICATION_MARK -> 420.0;
            case COMPANY_LOGO -> 260.0;
            case SIGNATURE -> 260.0;
            case PAYMENT_QR -> 420.0;
        };

        try (InputStream input = Files.newInputStream(path)) {
            Image image = new Image(input, requestedWidth, requestedHeight, true, true);
            if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
                Throwable cause = image.getException();
                throw new IllegalArgumentException(
                        cause == null ? "The selected image could not be decoded." : cause.getMessage(),
                        cause);
            }
            return image;
        }
    }
}
