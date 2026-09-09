package org.example.server.documents;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/documents")
public class CanonicalDocumentController {
    private final CanonicalDocumentService service;
    public CanonicalDocumentController(CanonicalDocumentService service){this.service=service;}

    @GetMapping("/render")
    public ResponseEntity<byte[]> render(@RequestParam String type,@RequestParam String number,@RequestParam(defaultValue="PDF") String format) throws Exception {
        var output=service.render(type,number,format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(output.contentType()))
                .header("Content-Disposition","attachment; filename*=UTF-8''"+URLEncoder.encode(output.fileName(),StandardCharsets.UTF_8).replace("+","%20"))
                .header("Cache-Control","no-store")
                .body(output.bytes());
    }
}
