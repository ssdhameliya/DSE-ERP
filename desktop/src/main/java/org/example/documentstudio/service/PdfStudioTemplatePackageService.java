package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.TemplateStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Portable PDF Studio bundle: source PDF + complete mapping JSON + template assets. */
public final class PdfStudioTemplatePackageService {
    public static final String EXTENSION = ".dsetemplate";
    private static final String MANIFEST = "package.json";
    private static final String META = "template.json";
    private static final String SOURCE = "source.pdf";
    private static final String ORIGINAL = "original.pdf";
    private static final String ASSETS = "assets";
    private static final int PACKAGE_VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private PdfStudioTemplatePackageService() { }

    public record PackageManifest(int packageVersion, int studioSchemaVersion, int dataContractVersion,
                                  String templateName, String documentType, String exportedAt) { }

    public static Path exportPackage(DocumentTemplate template, Path output) throws IOException {
        if (template == null) throw new IOException("Template is required.");
        if (output == null) throw new IOException("Export destination is required.");
        Path folder = PdfStudioTemplateRepository.folder(template);
        Path meta = folder.resolve(META);
        Path source = folder.resolve(SOURCE);
        if (!Files.isRegularFile(meta) || !Files.isRegularFile(source))
            throw new IOException("Template source or mapping metadata is missing.");
        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);

        PackageManifest manifest = new PackageManifest(PACKAGE_VERSION, template.getStudioSchemaVersion(),
                template.getDataContractVersion(), template.getName(), template.getDocumentType().name(), Instant.now().toString());
        try (OutputStream raw = Files.newOutputStream(output); ZipOutputStream zip = new ZipOutputStream(raw)) {
            putBytes(zip, MANIFEST, JSON.writeValueAsBytes(manifest));
            putFile(zip, META, meta);
            putFile(zip, SOURCE, source);
            Path original = folder.resolve(ORIGINAL);
            if (Files.isRegularFile(original)) putFile(zip, ORIGINAL, original);
            Path assets = folder.resolve(ASSETS);
            if (Files.isDirectory(assets)) {
                try (Stream<Path> walk = Files.walk(assets)) {
                    for (Path file : walk.filter(Files::isRegularFile).toList()) {
                        String name = ASSETS + "/" + assets.relativize(file).toString().replace('\\','/');
                        putFile(zip, name, file);
                    }
                }
            }
        }
        return output;
    }

    public static DocumentTemplate importPackage(Path packageFile) throws IOException {
        if (packageFile == null || !Files.isRegularFile(packageFile)) throw new IOException("Template package does not exist.");
        Path temp = Files.createTempDirectory("dse-pdf-template-import-");
        try {
            extractSafely(packageFile, temp);
            Path manifestFile = temp.resolve(MANIFEST);
            Path metaFile = temp.resolve(META);
            Path sourceFile = temp.resolve(SOURCE);
            if (!Files.isRegularFile(manifestFile) || !Files.isRegularFile(metaFile) || !Files.isRegularFile(sourceFile))
                throw new IOException("Invalid DSE template package. package.json, template.json and source.pdf are required.");
            PackageManifest manifest = JSON.readValue(manifestFile.toFile(), PackageManifest.class);
            if (manifest.packageVersion() != PACKAGE_VERSION)
                throw new IOException("Unsupported DSE template package version: " + manifest.packageVersion());
            DocumentTemplate imported = JSON.readValue(metaFile.toFile(), DocumentTemplate.class);
            if (imported.getStudioSchemaVersion() > 4)
                throw new IOException("This template requires a newer PDF Studio schema (" + imported.getStudioSchemaVersion() + ").");

            DocumentTemplate created = PdfStudioTemplateRepository.importPdf(sourceFile,
                    imported.getName(), imported.getDocumentType());
            created.setCategory(imported.getCategory());
            created.setStudioSchemaVersion(imported.getStudioSchemaVersion());
            created.setDataContractVersion(imported.getDataContractVersion());
            created.setLayoutMode(imported.getLayoutMode());
            created.setVersion(Math.max(1, imported.getVersion()));
            created.setElements(imported.getElements());
            created.setDefaultTemplate(false);
            created.setRuntimeEnabled(false);
            created.setStatus(TemplateStatus.DRAFT);
            created.setUnpublishedChanges(true);
            created.setPublishedVersion(0);
            created.setActiveVersion(0);
            created.setPublishedAt(null);
            created.setActivatedAt(null);

            Path target = PdfStudioTemplateRepository.folder(created);
            Path importedOriginal = temp.resolve(ORIGINAL);
            if (Files.isRegularFile(importedOriginal))
                Files.copy(importedOriginal, target.resolve(ORIGINAL), StandardCopyOption.REPLACE_EXISTING);
            Path importedAssets = temp.resolve(ASSETS);
            if (Files.isDirectory(importedAssets)) copyTree(importedAssets, target.resolve(ASSETS));
            PdfStudioTemplateRepository.saveDraft(created);
            return created;
        } finally {
            deleteTree(temp);
        }
    }

    private static void extractSafely(Path zipFile, Path destination) throws IOException {
        try (InputStream raw = Files.newInputStream(zipFile); ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) throw new IOException("Unsafe template package path: " + entry.getName());
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void putBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
    }
    private static void putFile(ZipOutputStream zip, String name, Path file) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); Files.copy(file, zip); zip.closeEntry();
    }
    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path dest = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(dest);
                else { Files.createDirectories(dest.getParent()); Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
    }
    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (Exception ignored) { }
    }
}
