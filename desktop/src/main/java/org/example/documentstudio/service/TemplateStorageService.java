package org.example.documentstudio.service;

import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Compatibility facade retained so existing generation entry points do not need to change.
 * All PDF Studio persistence and lifecycle behavior is owned by PdfStudioTemplateRepository.
 */
public final class TemplateStorageService {
    private TemplateStorageService() {}

    public static Path root() throws IOException { return PdfStudioTemplateRepository.root(); }
    public static List<DocumentTemplate> listAll() { return PdfStudioTemplateRepository.listAll(); }
    public static Optional<DocumentTemplate> find(String id) { return PdfStudioTemplateRepository.find(id); }
    public static Optional<DocumentTemplate> defaultFor(DocumentType type) { return PdfStudioTemplateRepository.defaultFor(type); }
    public static DocumentTemplate importPdf(Path sourcePdf, String name, DocumentType type) throws IOException { return PdfStudioTemplateRepository.importPdf(sourcePdf, name, type); }
    public static DocumentTemplate importPdf(Path sourcePdf, String name, DocumentType type, String password) throws IOException { return PdfStudioTemplateRepository.importPdf(sourcePdf, name, type, password); }
    public static DocumentTemplate createBlank(String name, DocumentType type) throws IOException { return PdfStudioTemplateRepository.createBlank(name, type); }
    public static boolean migrateToStudioV2(DocumentTemplate template) throws IOException { return PdfStudioTemplateRepository.migrateToStudioV3(template); }
    public static boolean migrateToStudioV3(DocumentTemplate template) throws IOException { return PdfStudioTemplateRepository.migrateToStudioV3(template); }
    public static void save(DocumentTemplate template) throws IOException { PdfStudioTemplateRepository.saveDraft(template); }
    public static void saveDraft(DocumentTemplate template) throws IOException { PdfStudioTemplateRepository.saveDraft(template); }
    public static void publish(DocumentTemplate template) throws IOException { PdfStudioTemplateRepository.publish(template); }
    public static void changeDocumentType(DocumentTemplate template, DocumentType type) throws IOException { PdfStudioTemplateRepository.changeDocumentType(template, type); }
    public static int appendBlankPage(DocumentTemplate template, int referencePage) throws IOException { return PdfStudioTemplateRepository.appendBlankPage(template, referencePage); }
    public static int deletePage(DocumentTemplate template, int pageIndex) throws IOException { return PdfStudioTemplateRepository.deletePage(template, pageIndex); }
    public static void rotatePage(DocumentTemplate template, int pageIndex, int degrees) throws IOException { PdfStudioTemplateRepository.rotatePage(template, pageIndex, degrees); }
    public static void activateAndSetDefault(DocumentTemplate template) throws IOException { PdfStudioTemplateRepository.activateAndSetDefault(template); }
    public static void setDefault(String id) throws IOException { PdfStudioTemplateRepository.setDefault(id); }
    public static DocumentTemplate duplicate(DocumentTemplate source) throws IOException { return PdfStudioTemplateRepository.duplicate(source); }
    public static void archive(DocumentTemplate template) throws IOException { PdfStudioTemplateRepository.archive(template); }
    public static void delete(DocumentTemplate template) throws IOException { PdfStudioTemplateRepository.delete(template); }
    public static Path folder(DocumentTemplate template) throws IOException { return PdfStudioTemplateRepository.folder(template); }
    public static Path sourcePdf(DocumentTemplate template) throws IOException { return PdfStudioTemplateRepository.sourcePdf(template); }
    public static Path originalPdf(DocumentTemplate template) throws IOException { return PdfStudioTemplateRepository.originalPdf(template); }
    public static Path assetsFolder(DocumentTemplate template) throws IOException { return PdfStudioTemplateRepository.assetsFolder(template); }
    public static String importAsset(DocumentTemplate template, Path source) throws IOException { return PdfStudioTemplateRepository.importAsset(template, source); }
    public static Path resolveAsset(DocumentTemplate template, String relative) throws IOException { return PdfStudioTemplateRepository.resolveAsset(template, relative); }
}
