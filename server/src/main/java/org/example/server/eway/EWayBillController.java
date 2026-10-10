package org.example.server.eway;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/eway-bill", "/api/eway-bills"})
public class EWayBillController {

    private final EWayBillService service;

    public EWayBillController(EWayBillService service) {
        this.service = service;
    }

    @GetMapping({"", "/"})
    public List<EWayBillDtos.ExistingEWayBillDto> listExistingBills() {
        return service.listExistingBills();
    }

    @GetMapping({"/eligible-sales", "/eligible"})
    public List<EWayBillDtos.EligibleSaleDto> listEligibleSales() {
        return service.listEligibleSales();
    }

    @PostMapping("/generate/{saleId}")
    public boolean generateEWayBill(
            @PathVariable Long saleId,
            @RequestBody(required = false) Map<String, Object> req,
            @RequestParam(required = false) String user) {
        return service.generateEWayBill(saleId, req, user);
    }

    @PutMapping("/{id}/update-vehicle")
    public boolean updateVehicle(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> req,
            @RequestParam(required = false) String user) {
        return service.updateVehicle(id, req, user);
    }

    @GetMapping({"/{id}/export-json", "/nic-json/{id}"})
    public Map<String, Object> generateNicJson(@PathVariable Long id) {
        return service.generateNicJson(id);
    }

    @PostMapping("/update/{invoiceId}")
    public boolean updateTransporter(
            @PathVariable Long invoiceId,
            @RequestBody EWayBillDtos.UpdateTransporterRequest req,
            @RequestParam(required = false) String user) {
        return service.updateTransporter(invoiceId, req, user);
    }
}
