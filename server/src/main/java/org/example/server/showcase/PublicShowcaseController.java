package org.example.server.showcase;

import org.example.server.authority.ServerResourceService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/public/showcase")
public class PublicShowcaseController {
    private final ShowcaseService showcaseService;

    public PublicShowcaseController(ShowcaseService showcaseService) {
        this.showcaseService = showcaseService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getShowcase() {
        String json = showcaseService.getShowcaseJson();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(json);
    }

    @GetMapping(value = "/media/{key}")
    public ResponseEntity<byte[]> getMedia(@PathVariable String key) {
        try {
            ServerResourceService.ResourceFile file = showcaseService.getMedia(key);
            MediaType mediaType = MediaType.parseMediaType(file.contentType());
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                    .header("X-Resource-SHA256", file.checksum())
                    .body(file.content());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping(value = "/rfq", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> submitRfq(@RequestBody Map<String, Object> rfqPayload) {
        // Log technical specification receipt for sales engineering team
        System.getLogger("org.example.server.showcase")
                .log(System.Logger.Level.INFO, "Public RFQ received from: " + rfqPayload.get("email"));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "RFQ Received! Our engineering team will review your drawings and deliver a formal quotation within 24 hours."
        ));
    }
}
