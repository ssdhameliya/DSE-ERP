package org.example.util;

import org.example.api.support.SupportApiClient;
import org.example.config.WorkspaceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One managed path for materialising server attachments before desktop preview.
 * Server authorization/ownership remains authoritative; this class only handles
 * the temporary local preview copy returned by an already-authorized API call.
 */
public final class AttachmentPreviewSupport {
    private AttachmentPreviewSupport() { }

    public static Path materialize(SupportApiClient.DownloadedAttachment download, String fallbackName) throws IOException {
        if (download == null || download.data() == null || download.data().length == 0) return null;
        return materialize(download.data(), download.fileName(), fallbackName);
    }

    public static Path materializeRequired(SupportApiClient.DownloadedAttachment download, String fallbackName) throws IOException {
        Path path = materialize(download, fallbackName);
        if (path == null) throw new IOException("No attachment file is stored.");
        return path;
    }

    public static Path materialize(byte[] data, String fileName, String fallbackName) throws IOException {
        if (data == null || data.length == 0) return null;
        Path folder = WorkspaceManager.getTempFolder().resolve("AttachmentPreview");
        Files.createDirectories(folder);
        String name = sanitize(fileName, fallbackName);
        Path target = folder.resolve(System.currentTimeMillis() + "-" + name);
        Files.write(target, data);
        target.toFile().deleteOnExit();
        return target;
    }

    public static String sanitize(String fileName, String fallbackName) {
        String fallback = fallbackName == null || fallbackName.isBlank() ? "attachment" : fallbackName;
        String raw = fileName == null || fileName.isBlank() ? fallback : fileName;
        String safe = raw.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? fallback : safe;
    }

    public static String countLabel(int count) {
        return count <= 0 ? "No attachments" : count + " attachment" + (count == 1 ? "" : "s");
    }
}
