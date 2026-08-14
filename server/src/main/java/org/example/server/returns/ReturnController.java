package org.example.server.returns;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/returns")
public class ReturnController {
    private final ReturnService service;

    public ReturnController(ReturnService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReturnDtos.Summary> list(@RequestParam String type) {
        service.requireTypeAccess(type);
        return service.summaries(type);
    }

    @GetMapping("/returned")
    public Map<String, Double> returned(@RequestParam String type, @RequestParam String invoice) {
        service.requireTypeAccess(type);
        return service.returned(type, invoice);
    }

    @PostMapping
    public ReturnDtos.Created create(@RequestBody ReturnDtos.CreateRequest request) {
        service.requireTypeAccess(request == null ? null : request.type());
        return service.create(request);
    }

    @GetMapping("/{no}")
    public ReturnDtos.Details details(@PathVariable String no) {
        service.requireAccess(no);
        return service.details(no);
    }

    @PutMapping("/{no}")
    public ReturnDtos.Ok update(@PathVariable String no, @RequestBody ReturnDtos.UpdateRequest request) {
        service.requireAccess(no);
        service.update(no, request.field(), request.value());
        return ok("Updated");
    }

    @PostMapping("/{no}/refund")
    public ReturnDtos.Ok refund(@PathVariable String no, @RequestBody ReturnDtos.RefundRequest request) {
        service.requireAccess(no);
        service.refund(no, request.amount());
        return ok("Recorded");
    }

    @PostMapping("/{no}/cancel")
    public ReturnDtos.Ok cancel(@PathVariable String no, @RequestParam boolean sales) {
        service.cancel(no, sales);
        return ok("Cancelled");
    }

    @DeleteMapping("/{no}")
    public ReturnDtos.Ok delete(@PathVariable String no, @RequestParam boolean sales) {
        service.delete(no, sales);
        return ok("Deleted");
    }

    private ReturnDtos.Ok ok(String message) {
        return new ReturnDtos.Ok(true, message);
    }
}
