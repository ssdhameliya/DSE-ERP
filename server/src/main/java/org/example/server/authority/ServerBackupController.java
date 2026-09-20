package org.example.server.authority;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/authority/backups")
public class ServerBackupController {
    private final ServerBackupService service;

    public ServerBackupController(ServerBackupService service) { this.service = service; }

    @GetMapping public List<ServerBackupService.BackupFile> list() throws IOException { return service.list(); }
    @GetMapping("/metrics") public ServerBackupService.DatabaseMetrics metrics() { return service.metrics(); }
    @PostMapping public ServerBackupService.BackupFile create() throws IOException { return service.create("MANUAL"); }

    @PostMapping(value = "/recovery-package", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> recoveryPackage() throws IOException {
        ServerBackupService.RecoveryPackage recovery = service.createRecoveryPackage();
        StreamingResponseBody stream = output -> {
            try (InputStream in=Files.newInputStream(recovery.file())) { in.transferTo(output); }
            finally { Files.deleteIfExists(recovery.file()); }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(recovery.filename(), StandardCharsets.UTF_8))
                .header("X-DSE-Recovery-Database-SHA256", recovery.databaseSha256())
                .header("X-DSE-Recovery-Environment", recovery.environment())
                .header("X-DSE-Recovery-Version", recovery.applicationVersion())
                .contentLength(recovery.size())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(stream);
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ServerBackupService.BackupFile importBackup(@RequestParam String filename, HttpServletRequest request) throws IOException {
        return service.importBackup(filename, request.getInputStream());
    }

    @PostMapping("/{name}/validate")
    public ServerBackupService.Validation validate(@PathVariable String name) throws IOException { return service.validate(name); }

    @PostMapping("/{name}/restore/stage")
    public Message stageStored(@PathVariable String name) throws IOException { return new Message(service.stageStoredRestore(name)); }

    @DeleteMapping("/{name}")
    public Message delete(@PathVariable String name) throws IOException { service.deleteSafely(name); return new Message("Backup moved to server recycle storage."); }

    @GetMapping(value = "/{name}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> get(@PathVariable String name) throws IOException {
        Path file=service.readablePath(name);long size=Files.size(file);
        StreamingResponseBody stream=output->{try(InputStream in=Files.newInputStream(file)){in.transferTo(output);}};
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8))
                .contentLength(size)
                .body(stream);
    }

    @PostMapping(value = "/restore/stage", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Message stage(@RequestParam String filename, HttpServletRequest request) throws IOException {
        return new Message(service.stageRestore(filename, request.getInputStream()));
    }

    public record Message(String message) {}
}
