package org.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves application resources consistently in packaged builds and IntelliJ/development runs.
 *
 * <p>The normal classpath remains authoritative. The filesystem fallback is deliberately limited
 * to Maven source-resource folders so an IntelliJ module whose resource root was not imported
 * correctly can still load the exact FXML/CSS/assets from this project. Packaged EXE/DMG builds
 * continue to use their bundled classpath resources.</p>
 */
public final class ResourceLocator {
    private static final int MAX_PARENT_SEARCH = 5;

    private ResourceLocator() {}

    public static URL require(String resource) {
        URL url = find(resource);
        if (url != null) return url;
        throw new IllegalStateException("Required application resource was not found: " + normalize(resource)
                + " | Working directory: " + Path.of("").toAbsolutePath().normalize()
                + " | Expected classpath resource or desktop/src/main/resources file.");
    }

    public static URL find(String resource) {
        String normalized = normalize(resource);
        if (normalized.isBlank()) return null;

        // 1) Normal packaged/Maven/IDE classpath.
        URL url = ResourceLocator.class.getResource("/" + normalized);
        if (url != null) return url;

        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            url = context.getResource(normalized);
            if (url != null) return url;
        }

        // 2) Explicit support/development override.
        String override = System.getProperty("dse.erp.resource.root", "").trim();
        if (!override.isBlank()) {
            url = fileUrl(Path.of(override).resolve(normalized));
            if (url != null) return url;
        }

        // 3) IntelliJ/development fallback. Search only standard Maven resource roots.
        for (Path root : developmentRoots()) {
            url = fileUrl(root.resolve(normalized));
            if (url != null) return url;
        }
        return null;
    }

    /** Returns a stream when the resource exists, otherwise null for optional assets. */
    public static InputStream open(String resource) throws IOException {
        URL url = find(resource);
        return url == null ? null : url.openStream();
    }

    private static List<Path> developmentRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        Path base = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < MAX_PARENT_SEARCH && base != null; i++, base = base.getParent()) {
            roots.add(base.resolve("desktop/src/main/resources").normalize());
            roots.add(base.resolve("src/main/resources").normalize());
        }
        return new ArrayList<>(roots);
    }

    private static URL fileUrl(Path candidate) {
        try {
            return Files.isRegularFile(candidate) ? candidate.toUri().toURL() : null;
        } catch (MalformedURLException ignored) {
            return null;
        }
    }

    private static String normalize(String resource) {
        if (resource == null) return "";
        String value = resource.trim().replace('\\', '/');
        while (value.startsWith("/")) value = value.substring(1);
        return value;
    }
}
