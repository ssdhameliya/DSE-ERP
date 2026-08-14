package org.example.server.setup;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/setup")
public class SetupController {
    private final SetupService service; public SetupController(SetupService service){this.service=service;}
    @GetMapping("/status") public SetupDtos.SetupStatus status(){ return service.status(); }
    @PostMapping("/bootstrap") public SetupDtos.BootstrapResponse bootstrap(@RequestBody SetupDtos.BootstrapRequest request){ return service.bootstrap(request); }
}
