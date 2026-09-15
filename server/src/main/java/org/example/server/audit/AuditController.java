package org.example.server.audit;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit){this.audit=audit;}
    @GetMapping("/record") public List<AuditDtos.EventRow> record(@RequestParam String type,@RequestParam long id){return audit.record(type,id);}
    @GetMapping("/global") public AuditDtos.GlobalPage global(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(defaultValue="") String module,@RequestParam(defaultValue="") String action,@RequestParam(defaultValue="") String user,@RequestParam(defaultValue="") String reference,@RequestParam(defaultValue="") String q){return audit.global(page,size,module,action,user,reference,q);}
}
