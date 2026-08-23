package org.example.server.authority;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/authority/backups")
public class ServerBackupController {
    private final ServerBackupService service;

    public ServerBackupController(ServerBackupService service) { this.service = service; }

    @GetMapping public List<ServerBackupService.BackupFile> list() throws IOException { return service.list(); }
    @GetMapping("/metrics") public ServerBackupService.DatabaseMetrics metrics() { return service.metrics(); }
    @PostMapping public ServerBackupService.BackupFile create() throws IOException { return service.create("MANUAL"); }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ServerBackupService.BackupFile importBackup(@RequestParam String filename, @RequestBody byte[] data) throws IOException {
        return service.importBackup(filename, data);
    }

    @PostMapping("/{name}/validate")
    public ServerBackupService.Validation validate(@PathVariable String name) throws IOException { return service.validate(name); }

    @PostMapping("/{name}/restore/stage")
    public Message stageStored(@PathVariable String name) throws IOException { return new Message(service.stageStoredRestore(name)); }

    @DeleteMapping("/{name}")
    public Message delete(@PathVariable String name) throws IOException { service.deleteSafely(name); return new Message("Backup moved to server recycle storage."); }

    @GetMapping(value = "/{name}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> get(@PathVariable String name) throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8))
                .body(service.read(name));
    }

    @PostMapping(value = "/restore/stage", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Message stage(@RequestParam String filename, @RequestBody byte[] data) throws IOException {
        return new Message(service.stageRestore(filename, data));
    }

    public record Message(String message) {}
}
