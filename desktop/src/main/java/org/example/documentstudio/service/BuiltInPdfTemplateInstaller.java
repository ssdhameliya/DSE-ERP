package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.TemplateStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Installs the bundled Sales Invoice starter template.
 *
 * <p>All concrete source geometry is stored in the bundled template metadata resource, never in
 * Java constants.  The installer only copies the protected source PDF, loads that metadata, and
 * runs the same metadata normalization used for imported customer templates.</p>
 */
final class BuiltInPdfTemplateInstaller {
    static final String SALES_TEMPLATE_ID = "builtin-sales-invoice-jasvi-9-0-60";
    private static final String SOURCE_RESOURCE = "/documentstudio/defaults/sales-invoice-jasvi-runtime.pdf";
    private static final String TEMPLATE_RESOURCE = "/documentstudio/defaults/sales-invoice-jasvi-template.json";
    private static final int RELEASE_VERSION = 16;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final String SALES_DELETION_MARKER = ".builtin-sales-invoice-deleted";

    private BuiltInPdfTemplateInstaller() { }

    static void ensureInstalled(Path root) {
        try {
            if (isIntentionallyDeleted(root)) {
                removeLocalBuiltIn(root);
                return;
            }
            Path folder = root.resolve(SALES_TEMPLATE_ID);
            if (Files.isDirectory(folder)) {
                upgradeBuiltInIfNeeded(folder);
                return;
            }
            Path published = folder.resolve("published");
            Files.createDirectories(folder.resolve("assets"));
            Files.createDirectories(folder.resolve("history"));
            Files.createDirectories(published.resolve("assets"));
            copySource(folder);
            Files.copy(folder.resolve("source.pdf"), published.resolve("source.pdf"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(folder.resolve("original.pdf"), published.resolve("original.pdf"), StandardCopyOption.REPLACE_EXISTING);

            DocumentTemplate working = template(TemplateStatus.PUBLISHED, folder.resolve("source.pdf"));
            JSON.writeValue(folder.resolve("template.json").toFile(), working);
            DocumentTemplate pub = template(TemplateStatus.PUBLISHED, folder.resolve("source.pdf"));
            pub.setDefaultTemplate(false);
            pub.setRuntimeEnabled(false);
            JSON.writeValue(published.resolve("template.json").toFile(), pub);
            PdfStudioRemoteStore.publish(SALES_TEMPLATE_ID, folder);
        } catch (Exception error) {
            System.err.println("[PdfStudio] built-in Sales template install skipped: " + error.getMessage());
        }
    }

    static void markIntentionallyDeleted(Path root) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve(SALES_DELETION_MARKER), "deleted=" + Instant.now() + System.lineSeparator());
    }

    static boolean isIntentionallyDeleted(Path root) {
        return root != null && Files.isRegularFile(root.resolve(SALES_DELETION_MARKER));
    }

    static void enforceIntentionalDeletion(Path root) {
        if (!isIntentionallyDeleted(root)) return;
        try { removeLocalBuiltIn(root); }
        catch (Exception error) {
            System.err.println("[PdfStudio] deleted built-in Sales template cleanup skipped: " + error.getMessage());
        }
    }

    private static void removeLocalBuiltIn(Path root) throws IOException {
        Path folder = root.resolve(SALES_TEMPLATE_ID);
        if (!Files.exists(folder)) return;
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void upgradeBuiltInIfNeeded(Path folder) throws IOException {
        Path meta = folder.resolve("template.json");
        if (!Files.isRegularFile(meta)) return;
        DocumentTemplate current = JSON.readValue(meta.toFile(), DocumentTemplate.class);
        if (!SALES_TEMPLATE_ID.equals(current.getId()) || current.getVersion() >= RELEASE_VERSION) return;

        boolean wasDefault = current.isDefaultTemplate();
        boolean wasRuntime = current.isRuntimeEnabled();
        TemplateStatus oldStatus = current.getStatus();
        int oldActiveVersion = current.getActiveVersion();
        String oldActivatedAt = current.getActivatedAt();

        copySource(folder);
        DocumentTemplate upgraded = loadBundledTemplate(folder.resolve("source.pdf"));
        upgraded.setDefaultTemplate(wasDefault);
        upgraded.setRuntimeEnabled(wasRuntime);
        upgraded.setStatus(oldStatus);
        upgraded.setActiveVersion(oldActiveVersion > 0 ? RELEASE_VERSION : 0);
        upgraded.setActivatedAt(oldActivatedAt);
        upgraded.touch();
        JSON.writeValue(meta.toFile(), upgraded);

        upgradeSnapshot(folder.resolve("published"), false, false);
        upgradeSnapshot(folder.resolve("active"), wasDefault, wasRuntime);
        PdfStudioRemoteStore.publish(SALES_TEMPLATE_ID, folder);
    }

    private static void upgradeSnapshot(Path folder, boolean defaultTemplate, boolean runtimeEnabled) throws IOException {
        Path meta = folder.resolve("template.json");
        if (!Files.isRegularFile(meta)) return;
        Files.createDirectories(folder);
        Files.copy(folder.getParent().resolve("source.pdf"), folder.resolve("source.pdf"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(folder.getParent().resolve("original.pdf"), folder.resolve("original.pdf"), StandardCopyOption.REPLACE_EXISTING);
        DocumentTemplate snapshot = loadBundledTemplate(folder.resolve("source.pdf"));
        snapshot.setDefaultTemplate(defaultTemplate);
        snapshot.setRuntimeEnabled(runtimeEnabled);
        snapshot.setStatus(runtimeEnabled ? TemplateStatus.ACTIVE : TemplateStatus.PUBLISHED);
        snapshot.setActiveVersion(runtimeEnabled ? RELEASE_VERSION : 0);
        snapshot.setActivatedAt(runtimeEnabled ? Instant.now().toString() : null);
        JSON.writeValue(meta.toFile(), snapshot);
    }

    private static void copySource(Path folder) throws IOException {
        Files.createDirectories(folder);
        try (InputStream in = BuiltInPdfTemplateInstaller.class.getResourceAsStream(SOURCE_RESOURCE)) {
            if (in == null) throw new IOException("Built-in Sales Invoice PDF resource is missing.");
            Files.copy(in, folder.resolve("source.pdf"), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(folder.resolve("source.pdf"), folder.resolve("original.pdf"), StandardCopyOption.REPLACE_EXISTING);
        for (String snapshot : List.of("published", "active")) {
            Path target = folder.resolve(snapshot);
            if (!Files.isDirectory(target)) continue;
            Files.copy(folder.resolve("source.pdf"), target.resolve("source.pdf"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(folder.resolve("original.pdf"), target.resolve("original.pdf"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static DocumentTemplate template(TemplateStatus status, Path sourcePdf) throws IOException {
        DocumentTemplate t = loadBundledTemplate(sourcePdf);
        t.setStatus(status);
        t.setDefaultTemplate(status == TemplateStatus.ACTIVE);
        t.setRuntimeEnabled(status == TemplateStatus.ACTIVE);
        t.setUnpublishedChanges(false);
        t.setPublishedVersion(RELEASE_VERSION);
        t.setActiveVersion(status == TemplateStatus.ACTIVE ? RELEASE_VERSION : 0);
        String now = Instant.now().toString();
        t.setPublishedAt(now);
        t.setActivatedAt(status == TemplateStatus.ACTIVE ? now : null);
        return t;
    }

    private static DocumentTemplate loadBundledTemplate(Path sourcePdf) throws IOException {
        try (InputStream in = BuiltInPdfTemplateInstaller.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null) throw new IOException("Built-in Sales Invoice template metadata resource is missing.");
            DocumentTemplate t = JSON.readValue(in, DocumentTemplate.class);
            t.setId(SALES_TEMPLATE_ID);
            t.setVersion(RELEASE_VERSION);
            t.setPublishedVersion(RELEASE_VERSION);
            t.setLayoutMode("FLOW_FIXED");
            t.setSourceFile("source.pdf");
            PdfStudioTemplatePackageService.normalizeMappingMetadata(t, sourcePdf);
            return t;
        }
    }
}
