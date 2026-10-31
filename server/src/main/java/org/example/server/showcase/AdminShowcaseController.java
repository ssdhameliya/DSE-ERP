package org.example.server.showcase;

import jakarta.servlet.http.HttpServletRequest;
import org.example.server.authority.ServerResourceService;
import org.example.server.web.BoundedUpload;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/showcase")
public class AdminShowcaseController {
    private static final long MAX_IMAGE_BYTES = 15L * 1024 * 1024; // 15MB limit
    private final ShowcaseService showcaseService;

    public AdminShowcaseController(ShowcaseService showcaseService) {
        this.showcaseService = showcaseService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getShowcaseForAdmin() {
        return ResponseEntity.ok(showcaseService.getShowcaseJson());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateShowcase(@RequestBody String jsonPayload) {
        showcaseService.saveShowcaseJson(jsonPayload);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Showcase content published successfully."
        ));
    }

    @PostMapping(value = "/media", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> uploadMedia(
            @RequestParam(defaultValue = "machine.jpg") String filename,
            @RequestParam(defaultValue = "image/jpeg") String contentType,
            HttpServletRequest request
    ) throws IOException {
        // Enforce strict file extension and MIME type validation
        String cleanName = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String lower = cleanName.toLowerCase();
        if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg") && !lower.endsWith(".png") && !lower.endsWith(".webp")) {
            throw new IllegalArgumentException("Unsupported image format. Allowed formats: JPG, PNG, WEBP.");
        }

        byte[] content = BoundedUpload.read(request, MAX_IMAGE_BYTES, "Showcase image");
        String resourceKey = "mach_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        ServerResourceService.ResourceMeta meta = showcaseService.saveMedia(resourceKey, cleanName, contentType, content);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "key", resourceKey,
                "fileName", cleanName,
                "url", "/api/public/showcase/media/" + resourceKey,
                "checksum", meta.checksum()
        ));
    }

    @DeleteMapping("/media/{key}")
    public ResponseEntity<Map<String, Object>> deleteMedia(@PathVariable String key) {
        showcaseService.deleteMedia(key);
        return ResponseEntity.ok(Map.of("success", true, "message", "Media deleted"));
    }
}
