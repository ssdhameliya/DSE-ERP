package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.example.config.WorkspaceManager;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateStatus;
import org.example.documentstudio.model.TemplateCategory;
import org.example.documentstudio.model.TemplateElement;

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
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final String META = "template.json";
    private static final String SOURCE = "source.pdf";
    private static final String ORIGINAL = "original.pdf";

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
                catch (Exception error) { logFailure("list", folder, error); }
            });
        } catch (Exception error) { logFailure("list-root", null, error); }
        result.sort(Comparator.comparing(DocumentTemplate::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    public static Optional<DocumentTemplate> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        try { return loadFromFolder(root().resolve(id)); }
        catch (Exception error) {
            logFailure("find:" + id, null, error);
            return Optional.empty();
        }
    }

    public static synchronized Optional<DocumentTemplate> defaultFor(DocumentType type) {
        List<DocumentTemplate> defaults = listAll().stream()
                .filter(t -> t.getDocumentType() == type)
                .filter(t -> t.getStatus() == TemplateStatus.ACTIVE)
                .filter(DocumentTemplate::isDefaultTemplate)
                .toList();
        if (defaults.isEmpty()) return Optional.empty();
        DocumentTemplate keeper = defaults.getFirst();
        // Older workspaces may contain duplicate defaults. Keep the most recently updated one
        // and self-heal the metadata so runtime selection is deterministic thereafter.
        for (int i = 1; i < defaults.size(); i++) {
            DocumentTemplate other = defaults.get(i);
            other.setDefaultTemplate(false);
            try { save(other); } catch (IOException error) { logFailure("repair-default:" + type, null, error); }
        }
        return Optional.of(keeper);
    }

    public static DocumentTemplate importPdf(Path sourcePdf, String name, DocumentType type) throws IOException {
        return importPdf(sourcePdf, name, type, "");
    }

    /**
     * Imports a PDF after validating credentials/permissions and creates an unencrypted private
     * workspace copy. The caller-supplied password is used only for this operation and is never persisted.
     */
    public static DocumentTemplate importPdf(Path sourcePdf, String name, DocumentType type, String password) throws IOException {
        if (sourcePdf == null || !Files.isRegularFile(sourcePdf)) throw new IOException("The selected PDF does not exist.");
        String lower = sourcePdf.getFileName().toString().toLowerCase();
        if (!lower.endsWith(".pdf")) throw new IOException("Only PDF templates are supported.");
        DocumentTemplate template = new DocumentTemplate();
        template.setId(UUID.randomUUID().toString());
        template.setName(name);
        template.setDocumentType(type);
        template.setCategory(type != null && type.isGeneral() ? TemplateCategory.GENERAL_PDF : TemplateCategory.ERP_TEMPLATE);
        template.setStatus(TemplateStatus.DRAFT);
        template.setSourceFile(SOURCE);
        Path folder = folder(template);
        try {
            Files.createDirectories(folder.resolve("assets"));
            // Keep an immutable byte-for-byte copy of what the user imported. The designer and
            // renderer use source.pdf, which may be normalized/decrypted for editing.
            Files.copy(sourcePdf, folder.resolve(ORIGINAL), StandardCopyOption.REPLACE_EXISTING);
            PdfImportSecurityService.normalizeForEditing(sourcePdf, folder.resolve(SOURCE), password);
            save(template);
            verifySavedTemplate(template.getId());
            return template;
        } catch (IOException | RuntimeException error) {
            try { deleteFolder(folder); } catch (Exception cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    public static DocumentTemplate createBlank(String name, DocumentType type) throws IOException {
        DocumentTemplate template = new DocumentTemplate();
        template.setId(UUID.randomUUID().toString());
        template.setName(name);
        template.setDocumentType(type);
        template.setCategory(type != null && type.isGeneral() ? TemplateCategory.GENERAL_PDF : TemplateCategory.ERP_TEMPLATE);
        template.setStatus(TemplateStatus.DRAFT);
        Path folder = folder(template);
        Files.createDirectories(folder.resolve("assets"));
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(folder.resolve(SOURCE).toFile());
        }
        save(template);
        verifySavedTemplate(template.getId());
        return template;
    }

    public static synchronized void save(DocumentTemplate template) throws IOException {
        if (template == null) throw new IllegalArgumentException("Template is required.");
        Path folder = folder(template);
        Files.createDirectories(folder.resolve("assets"));
        template.touch();
        writeMetadata(folder, template);
    }


    /** Convert a General PDF into an ERP template (or change its ERP document type). */
    public static synchronized void changeDocumentType(DocumentTemplate template, DocumentType type) throws IOException {
        if (template == null || type == null) throw new IOException("Template and document type are required.");
        DocumentType previous = template.getDocumentType();
        template.setDocumentType(type);
        template.setCategory(type.isGeneral() ? TemplateCategory.GENERAL_PDF : TemplateCategory.ERP_TEMPLATE);
        // A default belongs to its document type. Moving a template to another type must never
        // silently carry the live ERP default flag into the new flow.
        if (previous != type) template.setDefaultTemplate(false);
        save(template);
    }

    /** Append a new blank page matching the currently selected page size. */
    public static synchronized int appendBlankPage(DocumentTemplate template, int referencePage) throws IOException {
        Path source = sourcePdf(template);
        Path temp = folder(template).resolve("source-page-edit.tmp.pdf");
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(source.toFile())) {
            if (document.getNumberOfPages() == 0) document.addPage(new PDPage(PDRectangle.A4));
            int ref = Math.max(0, Math.min(referencePage, document.getNumberOfPages() - 1));
            PDRectangle box = document.getPage(ref).getMediaBox();
            document.addPage(new PDPage(new PDRectangle(box.getWidth(), box.getHeight())));
            document.save(temp.toFile());
        }
        Files.move(temp, source, StandardCopyOption.REPLACE_EXISTING);
        return pageCount(template);
    }

    public static synchronized int deletePage(DocumentTemplate template, int pageIndex) throws IOException {
        Path source = sourcePdf(template);
        Path temp = folder(template).resolve("source-page-edit.tmp.pdf");
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(source.toFile())) {
            if (document.getNumberOfPages() <= 1) throw new IOException("A document must contain at least one page.");
            int index = Math.max(0, Math.min(pageIndex, document.getNumberOfPages() - 1));
            document.removePage(index);
            document.save(temp.toFile());
        }
        Files.move(temp, source, StandardCopyOption.REPLACE_EXISTING);
        List<TemplateElement> adjusted = new ArrayList<>();
        for (TemplateElement element : template.getElements()) {
            if (element.getPageIndex() == pageIndex) continue;
            if (element.getPageIndex() > pageIndex) element.setPageIndex(element.getPageIndex() - 1);
            adjusted.add(element);
        }
        template.setElements(adjusted);
        save(template);
        return pageCount(template);
    }

    public static synchronized void rotatePage(DocumentTemplate template, int pageIndex, int degrees) throws IOException {
        Path source = sourcePdf(template);
        Path temp = folder(template).resolve("source-page-edit.tmp.pdf");
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(source.toFile())) {
            if (document.getNumberOfPages() == 0) throw new IOException("Document has no pages.");
            int index = Math.max(0, Math.min(pageIndex, document.getNumberOfPages() - 1));
            PDPage page = document.getPage(index);
            int rotation = ((page.getRotation() + degrees) % 360 + 360) % 360;
            page.setRotation(rotation);
            document.save(temp.toFile());
        }
        Files.move(temp, source, StandardCopyOption.REPLACE_EXISTING);
    }

    private static int pageCount(DocumentTemplate template) throws IOException {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(sourcePdf(template).toFile())) {
            return document.getNumberOfPages();
        }
    }

    public static synchronized void activateAndSetDefault(DocumentTemplate template) throws IOException {
        if (template == null) return;
        if (!DocumentFlowRegistry.isAutomatic(template.getDocumentType()))
            throw new IOException(template.getDocumentType().label() + " is design-only and cannot be an automatic ERP default yet.");
        validateBeforeActivation(template);
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


    /** A default must successfully render before it can replace a built-in business-document flow. */
    private static void validateBeforeActivation(DocumentTemplate template) throws IOException {
        Path test = folder(template).resolve(".activation-validation.pdf");
        try {
            PdfTemplateRenderer.renderSample(template, test);
            if (!Files.isRegularFile(test) || Files.size(test) < 100)
                throw new IOException("Template validation did not produce a valid PDF.");
        } catch (IOException error) {
            throw new IOException("Template validation failed. The existing built-in document remains active. " + error.getMessage(), error);
        } catch (Exception error) {
            throw new IOException("Template validation failed. The existing built-in document remains active. " + String.valueOf(error.getMessage()), error);
        } finally {
            try { Files.deleteIfExists(test); } catch (Exception ignored) { }
        }
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
        Path sourceOriginal = folder(source).resolve(ORIGINAL);
        if (Files.isRegularFile(sourceOriginal)) Files.copy(sourceOriginal, target.resolve(ORIGINAL), StandardCopyOption.REPLACE_EXISTING);
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

    /** Returns the immutable imported PDF when available; blank templates fall back to source.pdf. */
    public static Path originalPdf(DocumentTemplate template) throws IOException {
        Path original = folder(template).resolve(ORIGINAL);
        return Files.isRegularFile(original) ? original : sourcePdf(template);
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
        String raw = Files.readString(meta);
        DocumentTemplate template = JSON.readValue(raw, DocumentTemplate.class);
        boolean repair = raw.contains("\"erpConnected\"") || template.getCategory() == null
                || template.getSourceFile() == null || template.getSourceFile().isBlank();
        if (template.getId() == null || template.getId().isBlank()) {
            template.setId(folder.getFileName().toString());
            repair = true;
        }
        if (repair) {
            template.setCategory(template.getDocumentType().isGeneral() ? TemplateCategory.GENERAL_PDF : TemplateCategory.ERP_TEMPLATE);
            template.setSourceFile(SOURCE);
            writeMetadata(folder, template);
        }
        return Optional.of(template);
    }

    private static void writeMetadata(Path folder, DocumentTemplate template) throws IOException {
        Path temp = folder.resolve(META + ".tmp");
        JSON.writeValue(temp.toFile(), template);
        try {
            Files.move(temp, folder.resolve(META), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, folder.resolve(META), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static DocumentTemplate verifySavedTemplate(String id) throws IOException {
        return loadFromFolder(root().resolve(id)).orElseThrow(() -> new IOException("Template metadata could not be reloaded after saving."));
    }

    private static void deleteFolder(Path folder) throws IOException {
        if (folder == null || !Files.exists(folder)) return;
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void logFailure(String operation, Path path, Exception error) {
        String where = path == null ? "" : " [" + path + "]";
        System.err.println("[DocumentStudio] " + operation + where + " failed: " + error.getMessage());
        error.printStackTrace(System.err);
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
