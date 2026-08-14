package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.example.config.WorkspaceManager;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateStatus;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** File-backed template repository stored under Workspace/Templates/DocumentStudio. */
public final class TemplateStorageService {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String META = "template.json";
    private static final String SOURCE = "source.pdf";

    private TemplateStorageService() {}

    public static Path root() throws IOException {
        Path root = WorkspaceManager.getTemplatesFolder().resolve("DocumentStudio");
        Files.createDirectories(root);
        return root;
    }

    public static List<DocumentTemplate> listAll() {
        List<DocumentTemplate> result = new ArrayList<>();
        try (Stream<Path> folders = Files.list(root())) {
            folders.filter(Files::isDirectory).forEach(folder -> {
                try { loadFromFolder(folder).ifPresent(result::add); }
                catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
        result.sort(Comparator.comparing(DocumentTemplate::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    public static Optional<DocumentTemplate> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        try { return loadFromFolder(root().resolve(id)); }
        catch (Exception ignored) { return Optional.empty(); }
    }

    public static Optional<DocumentTemplate> defaultFor(DocumentType type) {
        return listAll().stream()
                .filter(t -> t.getDocumentType() == type)
                .filter(t -> t.getStatus() == TemplateStatus.ACTIVE)
                .filter(DocumentTemplate::isDefaultTemplate)
                .findFirst();
    }

    public static DocumentTemplate importPdf(Path sourcePdf, String name, DocumentType type) throws IOException {
        if (sourcePdf == null || !Files.isRegularFile(sourcePdf)) throw new IOException("The selected PDF does not exist.");
        String lower = sourcePdf.getFileName().toString().toLowerCase();
        if (!lower.endsWith(".pdf")) throw new IOException("Only PDF templates are supported.");
        DocumentTemplate template = new DocumentTemplate();
        template.setId(UUID.randomUUID().toString());
        template.setName(name);
        template.setDocumentType(type);
        template.setStatus(TemplateStatus.DRAFT);
        template.setSourceFile(SOURCE);
        Path folder = folder(template);
        Files.createDirectories(folder.resolve("assets"));
        Files.copy(sourcePdf, folder.resolve(SOURCE), StandardCopyOption.REPLACE_EXISTING);
        save(template);
        return template;
    }

    public static DocumentTemplate createBlank(String name, DocumentType type) throws IOException {
        DocumentTemplate template = new DocumentTemplate();
        template.setId(UUID.randomUUID().toString());
        template.setName(name);
        template.setDocumentType(type);
        template.setStatus(TemplateStatus.DRAFT);
        Path folder = folder(template);
        Files.createDirectories(folder.resolve("assets"));
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(folder.resolve(SOURCE).toFile());
        }
        save(template);
        return template;
    }

    public static synchronized void save(DocumentTemplate template) throws IOException {
        if (template == null) throw new IllegalArgumentException("Template is required.");
        Path folder = folder(template);
        Files.createDirectories(folder.resolve("assets"));
        template.touch();
        Path temp = folder.resolve(META + ".tmp");
        JSON.writeValue(temp.toFile(), template);
        try {
            Files.move(temp, folder.resolve(META), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, folder.resolve(META), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static synchronized void activateAndSetDefault(DocumentTemplate template) throws IOException {
        if (template == null) return;
        for (DocumentTemplate other : listAll()) {
            if (other.getDocumentType() == template.getDocumentType() && other.isDefaultTemplate()
                    && !other.getId().equals(template.getId())) {
                other.setDefaultTemplate(false);
                save(other);
            }
        }
        template.setStatus(TemplateStatus.ACTIVE);
        template.setDefaultTemplate(true);
        save(template);
    }

    public static synchronized void setDefault(String id) throws IOException {
        DocumentTemplate template = find(id).orElseThrow(() -> new IOException("Template was not found."));
        activateAndSetDefault(template);
    }

    public static synchronized DocumentTemplate duplicate(DocumentTemplate source) throws IOException {
        if (source == null) throw new IOException("Template is required.");
        DocumentTemplate copy = JSON.readValue(JSON.writeValueAsBytes(source), DocumentTemplate.class);
        copy.setId(UUID.randomUUID().toString());
        copy.setName(source.getName() + " Copy");
        copy.setDefaultTemplate(false);
        copy.setStatus(TemplateStatus.DRAFT);
        copy.setVersion(source.getVersion() + 1);
        copy.setCreatedAt(Instant.now().toString());
        copy.setUpdatedAt(Instant.now().toString());
        Path target = folder(copy);
        Files.createDirectories(target.resolve("assets"));
        Files.copy(sourcePdf(source), target.resolve(SOURCE), StandardCopyOption.REPLACE_EXISTING);
        Path sourceAssets = folder(source).resolve("assets");
        if (Files.isDirectory(sourceAssets)) copyTree(sourceAssets, target.resolve("assets"));
        save(copy);
        return copy;
    }

    public static synchronized void archive(DocumentTemplate template) throws IOException {
        if (template == null) return;
        template.setStatus(TemplateStatus.ARCHIVED);
        template.setDefaultTemplate(false);
        save(template);
    }

    public static synchronized void delete(DocumentTemplate template) throws IOException {
        if (template == null) return;
        Path folder = folder(template);
        if (!Files.exists(folder)) return;
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    public static Path folder(DocumentTemplate template) throws IOException {
        if (template == null || template.getId() == null || template.getId().isBlank()) throw new IOException("Invalid template id.");
        return root().resolve(template.getId());
    }

    public static Path sourcePdf(DocumentTemplate template) throws IOException {
        Path file = folder(template).resolve(template.getSourceFile());
        if (!Files.isRegularFile(file)) throw new IOException("Template source PDF is missing: " + file);
        return file;
    }

    public static Path assetsFolder(DocumentTemplate template) throws IOException {
        Path folder = folder(template).resolve("assets");
        Files.createDirectories(folder);
        return folder;
    }

    public static String importAsset(DocumentTemplate template, Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) throw new IOException("Selected image is missing.");
        String fileName = UUID.randomUUID() + "-" + source.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        Path target = assetsFolder(template).resolve(fileName);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return "assets/" + fileName;
    }

    public static Path resolveAsset(DocumentTemplate template, String relative) throws IOException {
        if (relative == null || relative.isBlank()) return null;
        Path candidate = folder(template).resolve(relative).normalize();
        if (!candidate.startsWith(folder(template))) throw new IOException("Invalid template asset path.");
        return candidate;
    }

    private static Optional<DocumentTemplate> loadFromFolder(Path folder) throws IOException {
        Path meta = folder.resolve(META);
        if (!Files.isRegularFile(meta)) return Optional.empty();
        DocumentTemplate template = JSON.readValue(meta.toFile(), DocumentTemplate.class);
        return Optional.of(template);
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path relative = source.relativize(path);
                Path dest = target.resolve(relative);
                if (Files.isDirectory(path)) Files.createDirectories(dest);
                else Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
