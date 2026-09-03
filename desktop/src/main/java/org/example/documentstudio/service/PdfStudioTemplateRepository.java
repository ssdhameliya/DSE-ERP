package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.example.config.WorkspaceManager;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateCategory;
import org.example.documentstudio.model.TemplateElement;
import org.example.documentstudio.model.TemplateStatus;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * PDF Studio 3 repository.
 *
 * <p>Design work is non-destructive and intentionally separated into three physical states:</p>
 * <ul>
 *   <li>working copy: source.pdf + template.json + assets/</li>
 *   <li>published/: validated candidate, still never used by production</li>
 *   <li>active/: immutable runtime snapshot copied only by explicit Mark Default</li>
 * </ul>
 *
 * <p>This means Save Draft, auto-save, Preview and Publish cannot change any current ERP PDF flow.
 * Runtime generation can see a Studio template only after an explicit activation.</p>
 */
public final class PdfStudioTemplateRepository {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final String META = "template.json";
    private static final String SOURCE = "source.pdf";
    private static final String ORIGINAL = "original.pdf";
    private static final String ASSETS = "assets";
    private static final String PUBLISHED = "published";
    private static final String ACTIVE = "active";
    private static final String HISTORY = "history";

    private PdfStudioTemplateRepository() {}

    public static Path root() throws IOException {
        Path root = WorkspaceManager.getTemplatesFolder().resolve("DocumentStudio").resolve("Pdf");
        Files.createDirectories(root);
        return root;
    }

    public static List<DocumentTemplate> listAll() {
        List<DocumentTemplate> result = new ArrayList<>();
        Path templateRoot;
        try { templateRoot = root(); PdfStudioRemoteStore.refresh(templateRoot); }
        catch (Exception error) { logFailure("server-refresh", null, error); try { templateRoot = root(); } catch (Exception fatal) { return result; } }
        BuiltInPdfTemplateInstaller.ensureInstalled(templateRoot);
        try (Stream<Path> folders = Files.list(templateRoot)) {
            folders.filter(Files::isDirectory).forEach(folder -> {
                try { loadWorking(folder).ifPresent(result::add); }
                catch (Exception error) { logFailure("list", folder, error); }
            });
        } catch (Exception error) { logFailure("list-root", null, error); }
        result.sort(Comparator.comparing(DocumentTemplate::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    public static Optional<DocumentTemplate> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        try { return loadWorking(root().resolve(id)); }
        catch (Exception error) { logFailure("find:" + id, null, error); return Optional.empty(); }
    }

    /** Runtime lookup: only an explicitly activated snapshot is eligible. */
    public static synchronized Optional<DocumentTemplate> defaultFor(DocumentType type) {
        if (type == null) return Optional.empty();
        List<DocumentTemplate> defaults = listAll().stream()
                .filter(t -> t.getDocumentType() == type)
                .filter(t -> t.getStatus() == TemplateStatus.ACTIVE)
                .filter(DocumentTemplate::isDefaultTemplate)
                .filter(DocumentTemplate::isRuntimeEnabled)
                .filter(t -> t.getActiveVersion() > 0)
                .toList();
        if (defaults.isEmpty()) return Optional.empty();

        DocumentTemplate keeper = defaults.getFirst();
        for (int i = 1; i < defaults.size(); i++) {
            DocumentTemplate duplicate = defaults.get(i);
            duplicate.setDefaultTemplate(false);
            duplicate.setRuntimeEnabled(false);
            if (duplicate.getPublishedVersion() > 0) duplicate.setStatus(TemplateStatus.PUBLISHED);
            try { writeWorkingAndMirror(duplicate); }
            catch (Exception error) { logFailure("repair-runtime-default:" + type, folderQuiet(duplicate), error); }
        }

        try {
            DocumentTemplate active = loadSnapshot(keeper.getId(), ACTIVE)
                    .orElseThrow(() -> new IOException("The active PDF Studio snapshot is missing."));
            active.setStorageVariant(ACTIVE);
            active.setDefaultTemplate(true);
            active.setRuntimeEnabled(true);
            active.setStatus(TemplateStatus.ACTIVE);
            active.setActiveVersion(keeper.getActiveVersion());
            active.setPublishedVersion(keeper.getPublishedVersion());
            return Optional.of(active);
        } catch (Exception error) {
            logFailure("runtime-default:" + type, folderQuiet(keeper), error);
            return Optional.empty();
        }
    }

    public static DocumentTemplate importPdf(Path sourcePdf, String name, DocumentType type) throws IOException {
        return importPdf(sourcePdf, name, type, "");
    }

    public static DocumentTemplate importPdf(Path sourcePdf, String name, DocumentType type, String password) throws IOException {
        if (sourcePdf == null || !Files.isRegularFile(sourcePdf)) throw new IOException("The selected PDF does not exist.");
        if (!sourcePdf.getFileName().toString().toLowerCase().endsWith(".pdf")) throw new IOException("Only PDF templates are supported.");
        DocumentTemplate template = fresh(name, type);
        template.setLayoutMode("STRICT_FIXED");
        Path folder = folder(template);
        try {
            Files.createDirectories(folder.resolve(ASSETS));
            Files.createDirectories(folder.resolve(HISTORY));
            // Byte-for-byte source retained forever as the protected source-of-truth.
            Files.copy(sourcePdf, folder.resolve(ORIGINAL), StandardCopyOption.REPLACE_EXISTING);
            // Editing operates on a private normalized copy only.
            PdfImportSecurityService.normalizeForEditing(sourcePdf, folder.resolve(SOURCE), password);
            writeWorkingAndMirror(template);
            verifySavedTemplate(template.getId());
            return template;
        } catch (IOException | RuntimeException error) {
            try { deleteTree(folder); } catch (Exception cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    public static DocumentTemplate createBlank(String name, DocumentType type) throws IOException {
        DocumentTemplate template = fresh(name, type);
        Path folder = folder(template);
        Files.createDirectories(folder.resolve(ASSETS));
        Files.createDirectories(folder.resolve(HISTORY));
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(folder.resolve(SOURCE).toFile());
        }
        // A blank template has no imported original; preserve its first source as original fidelity layer.
        Files.copy(folder.resolve(SOURCE), folder.resolve(ORIGINAL), StandardCopyOption.REPLACE_EXISTING);
        writeWorkingAndMirror(template);
        verifySavedTemplate(template.getId());
        return template;
    }

    private static DocumentTemplate fresh(String name, DocumentType type) {
        DocumentTemplate template = new DocumentTemplate();
        template.setStudioSchemaVersion(4);
        template.setDataContractVersion(1);
        template.setLayoutMode("FREEFORM");
        template.setId(UUID.randomUUID().toString());
        template.setName(name);
        template.setDocumentType(type == null ? DocumentType.GENERAL_PDF : type);
        template.setCategory(template.getDocumentType().isGeneral() ? TemplateCategory.GENERAL_PDF : TemplateCategory.ERP_TEMPLATE);
        template.setStatus(TemplateStatus.DRAFT);
        template.setDefaultTemplate(false);
        template.setRuntimeEnabled(false);
        template.setUnpublishedChanges(true);
        template.setPublishedVersion(0);
        template.setActiveVersion(0);
        template.setSourceFile(SOURCE);
        return template;
    }

    public static synchronized boolean migrateToStudioV3(DocumentTemplate template) throws IOException {
        if (template == null || template.getStudioSchemaVersion() >= 4) return false;
        template.setStudioSchemaVersion(4);
        template.setDataContractVersion(1);
        if (template.getLayoutMode() == null || template.getLayoutMode().isBlank()) template.setLayoutMode("STRICT_FIXED");
        template.setDefaultTemplate(false);
        template.setRuntimeEnabled(false);
        template.setStatus(TemplateStatus.DRAFT);
        template.setUnpublishedChanges(true);
        template.setPublishedVersion(0);
        template.setActiveVersion(0);
        writeWorkingAndMirror(template);
        return true;
    }

    public static synchronized void saveDraft(DocumentTemplate template) throws IOException {
        if (template == null) throw new IllegalArgumentException("Template is required.");
        ensureWorkingTemplate(template);
        template.setStudioSchemaVersion(4);
        template.setUnpublishedChanges(true);
        if (!template.isRuntimeEnabled() && template.getStatus() != TemplateStatus.ARCHIVED) template.setStatus(TemplateStatus.DRAFT);
        writeWorkingAndMirror(template);
    }

    /** Validate and create a candidate snapshot. Production remains untouched. */
    public static synchronized void publish(DocumentTemplate template) throws IOException {
        if (template == null) throw new IOException("Template is required.");
        ensureWorkingTemplate(template);
        validateRenderable(template, "publish-validation");

        int next = template.getPublishedVersion() <= 0 ? 1 : template.getPublishedVersion() + 1;
        DocumentTemplate snapshotMeta = deepCopy(template);
        snapshotMeta.setStudioSchemaVersion(4);
        snapshotMeta.setVersion(next);
        snapshotMeta.setPublishedVersion(next);
        snapshotMeta.setUnpublishedChanges(false);
        snapshotMeta.setPublishedAt(Instant.now().toString());
        snapshotMeta.setDefaultTemplate(false);
        snapshotMeta.setRuntimeEnabled(false);
        snapshotMeta.setStatus(TemplateStatus.PUBLISHED);
        snapshotMeta.setStorageVariant("");

        Path templateFolder = folder(template);
        replaceSnapshot(templateFolder, PUBLISHED, snapshotMeta);
        Path versionFolder = templateFolder.resolve(HISTORY).resolve(String.format("v%04d", next));
        replaceDirectory(templateFolder.resolve(PUBLISHED), versionFolder);

        template.setVersion(next);
        template.setPublishedVersion(next);
        template.setPublishedAt(snapshotMeta.getPublishedAt());
        template.setUnpublishedChanges(false);
        if (!template.isRuntimeEnabled()) template.setStatus(TemplateStatus.PUBLISHED);
        writeWorkingAndMirror(template);
    }

    /** Explicit activation is the only operation that can make Studio affect runtime generation. */
    public static synchronized void activateAndSetDefault(DocumentTemplate template) throws IOException {
        if (template == null) throw new IOException("Template is required.");
        ensureWorkingTemplate(template);
        if (!DocumentFlowRegistry.isAutomatic(template.getDocumentType()))
            throw new IOException(template.getDocumentType().label() + " is design-only and cannot be a runtime PDF default.");
        if (template.getPublishedVersion() <= 0 || !Files.isRegularFile(folder(template).resolve(PUBLISHED).resolve(META)))
            throw new IOException("Publish this template before marking it as the system default.");
        if (template.isUnpublishedChanges())
            throw new IOException("This template has draft changes. Publish the current design before marking it as default.");

        DocumentTemplate published = loadSnapshot(template.getId(), PUBLISHED)
                .orElseThrow(() -> new IOException("Published snapshot could not be loaded."));
        published.setStorageVariant(PUBLISHED);
        validateRenderable(published, "activation-validation");

        for (DocumentTemplate other : listAll()) {
            if (other.getDocumentType() == template.getDocumentType() && !other.getId().equals(template.getId())
                    && (other.isDefaultTemplate() || other.isRuntimeEnabled())) {
                other.setDefaultTemplate(false);
                other.setRuntimeEnabled(false);
                if (other.getPublishedVersion() > 0) other.setStatus(TemplateStatus.PUBLISHED);
                else other.setStatus(TemplateStatus.DRAFT);
                writeWorkingAndMirror(other);
            }
        }

        DocumentTemplate activeMeta = deepCopy(published);
        activeMeta.setStorageVariant("");
        activeMeta.setStatus(TemplateStatus.ACTIVE);
        activeMeta.setDefaultTemplate(true);
        activeMeta.setRuntimeEnabled(true);
        activeMeta.setActiveVersion(template.getPublishedVersion());
        activeMeta.setActivatedAt(Instant.now().toString());
        replaceSnapshot(folder(template), ACTIVE, activeMeta);

        template.setStatus(TemplateStatus.ACTIVE);
        template.setDefaultTemplate(true);
        template.setRuntimeEnabled(true);
        template.setActiveVersion(template.getPublishedVersion());
        template.setActivatedAt(activeMeta.getActivatedAt());
        writeWorkingAndMirror(template);
    }

    public static synchronized void setDefault(String id) throws IOException {
        activateAndSetDefault(find(id).orElseThrow(() -> new IOException("Template was not found.")));
    }

    public static synchronized void changeDocumentType(DocumentTemplate template, DocumentType type) throws IOException {
        if (template == null || type == null) throw new IOException("Template and document type are required.");
        if (template.getDocumentType() == type) return;
        template.setDocumentType(type);
        template.setCategory(type.isGeneral() ? TemplateCategory.GENERAL_PDF : TemplateCategory.ERP_TEMPLATE);
        template.setDefaultTemplate(false);
        template.setRuntimeEnabled(false);
        template.setActiveVersion(0);
        template.setPublishedVersion(0);
        template.setPublishedAt(null);
        template.setActivatedAt(null);
        template.setStatus(TemplateStatus.DRAFT);
        template.setUnpublishedChanges(true);
        deleteTree(folder(template).resolve(PUBLISHED));
        deleteTree(folder(template).resolve(ACTIVE));
        writeWorkingAndMirror(template);
    }

    public static synchronized int appendBlankPage(DocumentTemplate template, int referencePage) throws IOException {
        ensureWorkingTemplate(template);
        Path source = sourcePdf(template);
        Path temp = folder(template).resolve("source-page-edit.tmp.pdf");
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            if (document.getNumberOfPages() == 0) document.addPage(new PDPage(PDRectangle.A4));
            int ref = Math.max(0, Math.min(referencePage, document.getNumberOfPages() - 1));
            PDRectangle box = document.getPage(ref).getMediaBox();
            document.addPage(new PDPage(new PDRectangle(box.getWidth(), box.getHeight())));
            document.save(temp.toFile());
        }
        moveReplace(temp, source);
        saveDraft(template);
        return pageCount(template);
    }

    public static synchronized int deletePage(DocumentTemplate template, int pageIndex) throws IOException {
        ensureWorkingTemplate(template);
        Path source = sourcePdf(template);
        Path temp = folder(template).resolve("source-page-edit.tmp.pdf");
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            if (document.getNumberOfPages() <= 1) throw new IOException("A document must contain at least one page.");
            int index = Math.max(0, Math.min(pageIndex, document.getNumberOfPages() - 1));
            document.removePage(index);
            document.save(temp.toFile());
        }
        moveReplace(temp, source);
        List<TemplateElement> adjusted = new ArrayList<>();
        for (TemplateElement element : template.getElements()) {
            if (element.getPageIndex() == pageIndex) continue;
            if (element.getPageIndex() > pageIndex) element.setPageIndex(element.getPageIndex() - 1);
            adjusted.add(element);
        }
        template.setElements(adjusted);
        saveDraft(template);
        return pageCount(template);
    }

    public static synchronized void rotatePage(DocumentTemplate template, int pageIndex, int degrees) throws IOException {
        ensureWorkingTemplate(template);
        Path source = sourcePdf(template);
        Path temp = folder(template).resolve("source-page-edit.tmp.pdf");
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            if (document.getNumberOfPages() == 0) throw new IOException("Document has no pages.");
            int index = Math.max(0, Math.min(pageIndex, document.getNumberOfPages() - 1));
            PDPage page = document.getPage(index);
            page.setRotation(((page.getRotation() + degrees) % 360 + 360) % 360);
            document.save(temp.toFile());
        }
        moveReplace(temp, source);
        saveDraft(template);
    }

    public static synchronized DocumentTemplate duplicate(DocumentTemplate source) throws IOException {
        if (source == null) throw new IOException("Template is required.");
        DocumentTemplate copy = deepCopy(source);
        copy.setId(UUID.randomUUID().toString());
        copy.setName(source.getName() + " Copy");
        copy.setStudioSchemaVersion(4);
        copy.setStatus(TemplateStatus.DRAFT);
        copy.setDefaultTemplate(false);
        copy.setRuntimeEnabled(false);
        copy.setUnpublishedChanges(true);
        copy.setPublishedVersion(0);
        copy.setActiveVersion(0);
        copy.setPublishedAt(null);
        copy.setActivatedAt(null);
        copy.setVersion(1);
        copy.setCreatedAt(Instant.now().toString());
        copy.setUpdatedAt(Instant.now().toString());
        copy.setStorageVariant("");
        Path target = folder(copy);
        Files.createDirectories(target.resolve(ASSETS));
        Files.createDirectories(target.resolve(HISTORY));
        Files.copy(sourcePdf(source), target.resolve(SOURCE), StandardCopyOption.REPLACE_EXISTING);
        Path original = originalPdf(source);
        if (Files.isRegularFile(original)) Files.copy(original, target.resolve(ORIGINAL), StandardCopyOption.REPLACE_EXISTING);
        Path sourceAssets = contentFolder(source).resolve(ASSETS);
        if (Files.isDirectory(sourceAssets)) copyTree(sourceAssets, target.resolve(ASSETS));
        writeWorkingAndMirror(copy);
        return copy;
    }

    public static synchronized void archive(DocumentTemplate template) throws IOException {
        if (template == null) return;
        template.setStatus(TemplateStatus.ARCHIVED);
        template.setDefaultTemplate(false);
        template.setRuntimeEnabled(false);
        writeWorkingAndMirror(template);
    }

    public static synchronized void delete(DocumentTemplate template) throws IOException {
        if (template == null) return;
        PdfStudioRemoteStore.delete(template.getId());
        deleteTree(folder(template));
    }

    public static Path folder(DocumentTemplate template) throws IOException {
        if (template == null || template.getId() == null || template.getId().isBlank()) throw new IOException("Invalid template id.");
        return root().resolve(template.getId());
    }

    private static Path contentFolder(DocumentTemplate template) throws IOException {
        Path base = folder(template);
        String variant = template.getStorageVariant();
        if (variant.isBlank()) return base;
        if (!variant.equals(PUBLISHED) && !variant.equals(ACTIVE)) throw new IOException("Invalid PDF Studio storage variant.");
        return base.resolve(variant);
    }

    public static Path sourcePdf(DocumentTemplate template) throws IOException {
        Path file = contentFolder(template).resolve(template.getSourceFile());
        if (!Files.isRegularFile(file)) throw new IOException("Template source PDF is missing: " + file);
        return file;
    }

    public static Path originalPdf(DocumentTemplate template) throws IOException {
        Path original = contentFolder(template).resolve(ORIGINAL);
        return Files.isRegularFile(original) ? original : sourcePdf(template);
    }

    public static Path assetsFolder(DocumentTemplate template) throws IOException {
        ensureWorkingTemplate(template);
        Path assets = folder(template).resolve(ASSETS);
        Files.createDirectories(assets);
        return assets;
    }

    public static String importAsset(DocumentTemplate template, Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) throw new IOException("Selected image is missing.");
        String fileName = UUID.randomUUID() + "-" + source.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        Path target = assetsFolder(template).resolve(fileName);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        template.setUnpublishedChanges(true);
        return ASSETS + "/" + fileName;
    }

    public static Path resolveAsset(DocumentTemplate template, String relative) throws IOException {
        if (relative == null || relative.isBlank()) return null;
        Path base = contentFolder(template);
        Path candidate = base.resolve(relative).normalize();
        if (!candidate.startsWith(base)) throw new IOException("Invalid template asset path.");
        return candidate;
    }

    private static int pageCount(DocumentTemplate template) throws IOException {
        try (PDDocument document = Loader.loadPDF(sourcePdf(template).toFile())) { return document.getNumberOfPages(); }
    }

    private static void validateRenderable(DocumentTemplate template, String prefix) throws IOException {
        Path test = folder(template).resolve("." + prefix + ".pdf");
        try {
            PdfStudioRenderer.renderSample(template, test);
            if (!Files.isRegularFile(test) || Files.size(test) < 100) throw new IOException("Validation did not produce a valid PDF.");
            byte[] sig = new byte[4];
            try (var in = Files.newInputStream(test)) {
                if (in.read(sig) != 4 || sig[0] != '%' || sig[1] != 'P' || sig[2] != 'D' || sig[3] != 'F')
                    throw new IOException("Validation output is not a PDF.");
            }
        } catch (IOException error) {
            throw new IOException("PDF Studio validation failed. Existing document generation remains unchanged. " + rootMessage(error), error);
        } catch (Exception error) {
            throw new IOException("PDF Studio validation failed. Existing document generation remains unchanged. " + rootMessage(error), error);
        } finally {
            try { Files.deleteIfExists(test); } catch (Exception ignored) { }
        }
    }

    private static Optional<DocumentTemplate> loadWorking(Path folder) throws IOException {
        Path meta = folder.resolve(META);
        if (!Files.isRegularFile(meta)) return Optional.empty();
        DocumentTemplate template = JSON.readValue(meta.toFile(), DocumentTemplate.class);
        template.setStorageVariant("");
        boolean repair = false;
        if (template.getId() == null || template.getId().isBlank()) { template.setId(folder.getFileName().toString()); repair = true; }
        if (template.getStudioSchemaVersion() < 4) {
            template.setStudioSchemaVersion(4);
            template.setDataContractVersion(1);
            if (template.getLayoutMode() == null || template.getLayoutMode().isBlank()) template.setLayoutMode("STRICT_FIXED");
            template.setDefaultTemplate(false);
            template.setRuntimeEnabled(false);
            template.setStatus(TemplateStatus.DRAFT);
            template.setUnpublishedChanges(true);
            template.setPublishedVersion(0);
            template.setActiveVersion(0);
            repair = true;
        }
        if (template.getCategory() == null) { template.setCategory(template.getDocumentType().isGeneral() ? TemplateCategory.GENERAL_PDF : TemplateCategory.ERP_TEMPLATE); repair = true; }
        if (repair) writeMetadata(folder, template);
        return Optional.of(template);
    }

    private static Optional<DocumentTemplate> loadSnapshot(String id, String variant) throws IOException {
        Path snapshot = root().resolve(id).resolve(variant);
        Path meta = snapshot.resolve(META);
        if (!Files.isRegularFile(meta)) return Optional.empty();
        DocumentTemplate template = JSON.readValue(meta.toFile(), DocumentTemplate.class);
        template.setStorageVariant(variant);
        return Optional.of(template);
    }

    private static void replaceSnapshot(Path templateFolder, String name, DocumentTemplate snapshotMeta) throws IOException {
        Path temp = Files.createTempDirectory(templateFolder, "." + name + "-");
        boolean moved = false;
        try {
            Files.copy(templateFolder.resolve(SOURCE), temp.resolve(SOURCE), StandardCopyOption.REPLACE_EXISTING);
            if (Files.isRegularFile(templateFolder.resolve(ORIGINAL)))
                Files.copy(templateFolder.resolve(ORIGINAL), temp.resolve(ORIGINAL), StandardCopyOption.REPLACE_EXISTING);
            if (Files.isDirectory(templateFolder.resolve(ASSETS))) copyTree(templateFolder.resolve(ASSETS), temp.resolve(ASSETS));
            else Files.createDirectories(temp.resolve(ASSETS));
            writeMetadata(temp, snapshotMeta);
            Path target = templateFolder.resolve(name);
            deleteTree(target);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target); }
            moved = true;
        } finally {
            if (!moved) deleteTree(temp);
        }
    }

    private static void replaceDirectory(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempDirectory(parent, ".history-");
        boolean moved = false;
        try {
            copyTree(source, temp);
            deleteTree(target);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target); }
            moved = true;
        } finally { if (!moved) deleteTree(temp); }
    }

    private static void writeWorkingAndMirror(DocumentTemplate template) throws IOException {
        Path folder = folder(template);
        Files.createDirectories(folder.resolve(ASSETS));
        Files.createDirectories(folder.resolve(HISTORY));
        template.touch();
        template.setStorageVariant("");
        writeMetadata(folder, template);
        PdfStudioRemoteStore.publish(template.getId(), folder);
    }

    private static void writeMetadata(Path folder, DocumentTemplate template) throws IOException {
        Files.createDirectories(folder);
        Path temp = folder.resolve(META + ".tmp");
        JSON.writeValue(temp.toFile(), template);
        moveReplace(temp, folder.resolve(META));
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static DocumentTemplate deepCopy(DocumentTemplate template) throws IOException {
        DocumentTemplate copy = JSON.readValue(JSON.writeValueAsBytes(template), DocumentTemplate.class);
        copy.setStorageVariant("");
        return copy;
    }

    private static DocumentTemplate verifySavedTemplate(String id) throws IOException {
        return loadWorking(root().resolve(id)).orElseThrow(() -> new IOException("Template metadata could not be reloaded after saving."));
    }

    private static void ensureWorkingTemplate(DocumentTemplate template) throws IOException {
        if (template == null) throw new IOException("Template is required.");
        if (!template.getStorageVariant().isBlank()) throw new IOException("Published and active snapshots are read-only.");
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

    private static void deleteTree(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    private static Path folderQuiet(DocumentTemplate template) {
        try { return folder(template); } catch (Exception ignored) { return null; }
    }

    private static void logFailure(String operation, Path path, Exception error) {
        String where = path == null ? "" : " [" + path + "]";
        System.err.println("[PdfStudio] " + operation + where + " failed: " + rootMessage(error));
    }

    private static String rootMessage(Throwable error) {
        Throwable t = error;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }
}
