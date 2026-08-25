package org.example.server.returns;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/returns")
public class ReturnController {
    private final ReturnService service;
    public ReturnController(ReturnService service) { this.service = service; }

    @GetMapping public List<ReturnDtos.Summary> list(@RequestParam String type) { service.requireTypeAccess(type); return service.summaries(type); }
    @GetMapping("/page") public ReturnDtos.Page page(@RequestParam String type,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String party,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String to){service.requireTypeAccess(type);return service.page(type,page,size,q,party,status,from,to);}
    @GetMapping("/returned") public Map<String,Double> returned(@RequestParam String type,@RequestParam String invoice){service.requireTypeAccess(type);return service.returned(type,invoice);}
    @PostMapping public ReturnDtos.Created create(@RequestBody ReturnDtos.CreateRequest request){service.requireTypeAccess(request==null?null:request.type());return service.create(request);}
    @GetMapping("/{no}") public ReturnDtos.Details details(@PathVariable String no){service.requireAccess(no);return service.details(no);}
    @PutMapping("/{no}") public ReturnDtos.Ok update(@PathVariable String no,@RequestBody ReturnDtos.UpdateRequest request){service.requireAccess(no);service.update(no,request.field(),request.value());return ok("Updated");}

    /** Legacy amount-only endpoint retained for compatibility; new UI uses the auditable refund ledger endpoint below. */
    @PostMapping("/{no}/refund") public ReturnDtos.Ok refund(@PathVariable String no,@RequestBody ReturnDtos.RefundRequest request){service.requireAccess(no);service.refund(no,request.amount());return ok("Recorded");}
    @GetMapping("/{no}/refunds") public List<ReturnDtos.RefundRow> refunds(@PathVariable String no){service.requireAccess(no);return service.refunds(no);}
    @PostMapping("/{no}/refunds") public ReturnDtos.RefundCreated recordRefund(@PathVariable String no,@RequestBody ReturnDtos.RefundCreateRequest request){service.requireAccess(no);return new ReturnDtos.RefundCreated(service.recordRefund(no,request));}

    @PostMapping("/{no}/cancel") public ReturnDtos.Ok cancel(@PathVariable String no,@RequestParam boolean sales){service.cancel(no,sales);return ok("Cancelled");}
    @DeleteMapping("/{no}") public ReturnDtos.Ok delete(@PathVariable String no,@RequestParam boolean sales){service.delete(no,sales);return ok("Deleted");}
    private ReturnDtos.Ok ok(String message){return new ReturnDtos.Ok(true,message);}
}
