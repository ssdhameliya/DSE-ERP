package org.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

/**
 * Canonical Phase 3 semantic presentation registry.
 *
 * <p>The reviewed 9.0.41 FXML inventory is exact-mapped in
 * {@code /ui/semantic-registry.properties}.  Runtime controls therefore share
 * one business vocabulary for field captions, TableView headers and KPI cards.
 * The registry owns icon identity and colour family; controllers continue to
 * own data, actions and business behaviour.</p>
 */
public final class UiSemanticRegistry {
    private static final String RESOURCE = "/ui/semantic-registry.properties";
    private static final Properties VALUES = load();

    private UiSemanticRegistry() {}

    public static String fieldSemantic(String text) {
        return lookup("field", text);
    }

    public static String headerSemantic(String text) {
        return lookup("header", text);
    }

    public static String kpiSemantic(String text) {
        return lookup("kpi", text);
    }

    public static String iconLiteral(String semantic) {
        String normalized = normalizeSemantic(semantic);
        String value = VALUES.getProperty("semantic." + normalized + ".icon");
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String colour(String semantic) {
        String normalized = normalizeSemantic(semantic);
        String value = VALUES.getProperty("semantic." + normalized + ".colour");
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static boolean hasSemantic(String semantic) {
        return iconLiteral(semantic) != null && colour(semantic) != null;
    }

    /** Stable key shared with the Phase 3 static audit. */
    public static String textKey(String text) {
        if (text == null) return "";
        String value = text.toLowerCase(Locale.ROOT).trim()
            .replace("&amp;", " and ")
            .replace("&", " and ")
            .replace("₹", " amount ")
            .replace("↔", " link ")
            .replace("→", " to ")
            .replace("←", " from ");
        value = value.replaceAll("[*:]+$", "").trim();
        value = value.replaceAll("[^a-z0-9]+", " ");
        value = value.replaceAll("\\s+", " ").trim();
        return value;
    }

    public static String normalizeSemantic(String semantic) {
        if (semantic == null) return "unknown";
        String value = semantic.toLowerCase(Locale.ROOT).trim();
        return value.isBlank() ? "unknown" : value.replace('_', '-').replace(' ', '-');
    }

    private static String lookup(String namespace, String text) {
        String key = textKey(text);
        if (key.isBlank()) return null;
        String semantic = VALUES.getProperty(namespace + "." + key.replace(' ', '.'));
        if (semantic == null || semantic.isBlank()) return null;
        semantic = normalizeSemantic(semantic);
        return hasSemantic(semantic) ? semantic : null;
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream stream = UiSemanticRegistry.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Semantic UI registry not found: " + RESOURCE);
            }
            properties.load(stream);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load semantic UI registry: " + RESOURCE, e);
        }
    }
}
