package org.example.server.showcase;

import org.example.server.authority.ServerResourceService;
import org.example.server.persistence.JpaNativeRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@Service
public class ShowcaseService {
    public static final String SETTING_KEY = "SHOWCASE_CONTENT";
    public static final String MEDIA_RESOURCE_TYPE = "SHOWCASE_MEDIA";

    private final JpaNativeRepository db;
    private final ServerResourceService resourceService;
    private volatile String cachedJson = null;

    public ShowcaseService(JpaNativeRepository db, ServerResourceService resourceService) {
        this.db = db;
        this.resourceService = resourceService;
    }

    /**
     * High-speed in-memory cached read for public web visitors.
     */
    public String getShowcaseJson() {
        String current = cachedJson;
        if (current != null && !current.isBlank()) {
            return current;
        }
        return reloadFromDatabaseOrClasspath();
    }

    public synchronized String reloadFromDatabaseOrClasspath() {
        try {
            List<String> values = db.query(
                    "SELECT setting_value FROM application_setting WHERE setting_key = ?",
                    (rs, i) -> rs.getString(1),
                    SETTING_KEY
            );
            if (!values.isEmpty() && values.getFirst() != null && !values.getFirst().isBlank()) {
                cachedJson = values.getFirst();
                return cachedJson;
            }
        } catch (Exception ex) {
            // Database might not be initialized or accessible during early bootstrap
        }

        // Fallback to default classpath resource
        try {
            ClassPathResource res = new ClassPathResource("showcase/default-showcase.json");
            if (res.exists()) {
                try (InputStream in = res.getInputStream()) {
                    cachedJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    return cachedJson;
                }
            }
        } catch (Exception ignored) {
        }

        cachedJson = "{}";
        return cachedJson;
    }

    public synchronized void saveShowcaseJson(String json) {
        Objects.requireNonNull(json, "Showcase JSON cannot be null");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Showcase content cannot be blank");
        }

        db.update(
                "INSERT INTO application_setting (setting_key, setting_value, updated_at) " +
                        "VALUES (?, ?, CURRENT_TIMESTAMP) " +
                        "ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value, updated_at = CURRENT_TIMESTAMP",
                SETTING_KEY, json
        );

        // Update in-memory cache
        this.cachedJson = json;
    }

    public ServerResourceService.ResourceMeta saveMedia(String key, String fileName, String contentType, byte[] content) {
        return resourceService.put(MEDIA_RESOURCE_TYPE, key, fileName, contentType, content);
    }

    public ServerResourceService.ResourceFile getMedia(String key) {
        return resourceService.get(MEDIA_RESOURCE_TYPE, key);
    }

    public void deleteMedia(String key) {
        resourceService.delete(MEDIA_RESOURCE_TYPE, key);
    }
}
