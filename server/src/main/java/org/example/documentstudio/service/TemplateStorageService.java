package org.example.documentstudio.service;

import org.example.documentstudio.model.DocumentTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/** Runtime-only server storage context for an extracted active PDF Studio snapshot. */
public final class TemplateStorageService {
    private static final ThreadLocal<Path> ROOT = new ThreadLocal<>();
    private TemplateStorageService() {}

    public static <T> T withRoot(Path root, Callable<T> work) throws Exception {
        Path previous = ROOT.get();
        ROOT.set(root == null ? null : root.toAbsolutePath().normalize());
        try { return work.call(); }
        finally { if (previous == null) ROOT.remove(); else ROOT.set(previous); }
    }

    public static Path sourcePdf(DocumentTemplate template) throws IOException {
        Path root = requireRoot();
        Path file = root.resolve(template.getSourceFile()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file))
            throw new IOException("Template source PDF is missing: " + file);
        return file;
    }

    public static Path resolveAsset(DocumentTemplate template, String relative) throws IOException {
        if (relative == null || relative.isBlank()) return null;
        Path root = requireRoot();
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root)) throw new IOException("Invalid template asset path.");
        return file;
    }

    private static Path requireRoot() throws IOException {
        Path root = ROOT.get();
        if (root == null) throw new IOException("No server PDF template context is active.");
        return root;
    }
}
