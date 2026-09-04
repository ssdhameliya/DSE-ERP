package org.example.server.support;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/storage")
public class StorageController {
    private final StorageMaintenanceService storage;
    public StorageController(StorageMaintenanceService storage) { this.storage = storage; }

    @GetMapping("/status")
    public StorageDtos.Status status() { return storage.status(); }

    @PostMapping("/cleanup")
    public StorageDtos.CleanupResult cleanup(@RequestParam(defaultValue = "false") boolean dryRun) {
        return storage.cleanup(dryRun);
    }
}
