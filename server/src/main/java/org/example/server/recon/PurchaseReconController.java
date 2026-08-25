package org.example.server.recon;
import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/purchase-recon")
public class PurchaseReconController{
 private final PurchaseReconService service;public PurchaseReconController(PurchaseReconService service){this.service=service;}
 @GetMapping("/suppliers") public List<PurchaseReconDtos.SupplierDto> suppliers(){return service.suppliers();}
 @PostMapping("/suppliers") public PurchaseReconDtos.SupplierDto createSupplier(@RequestBody PurchaseReconDtos.SupplierSaveRequest r){return service.saveSupplier(r);}
 @PutMapping("/suppliers/{id}") public PurchaseReconDtos.SupplierDto updateSupplier(@PathVariable Integer id,@RequestBody PurchaseReconDtos.SupplierSaveRequest r){return service.saveSupplier(new PurchaseReconDtos.SupplierSaveRequest(id,r.legalName(),r.gstin(),r.pan(),r.contactPerson(),r.phone(),r.email(),r.notes(),r.status(),r.rowVersion()));}
 @GetMapping("/records") public List<PurchaseReconDtos.ReconDto> records(){return service.recons();}
 @GetMapping("/records/page") public PurchaseReconDtos.Page recordsPage(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status){return service.page(page,size,q,status);}
 @GetMapping("/records/{id}") public PurchaseReconDtos.ReconDto record(@PathVariable Integer id){return service.recon(id);}
 @PostMapping("/records") public PurchaseReconDtos.ReconDto create(@RequestBody PurchaseReconDtos.ReconSaveRequest r){return service.saveRecon(r);}
 @PutMapping("/records/{id}") public PurchaseReconDtos.ReconDto update(@PathVariable Integer id,@RequestBody PurchaseReconDtos.ReconSaveRequest r){return service.saveRecon(new PurchaseReconDtos.ReconSaveRequest(id,r.supplierId(),r.supplierInvoiceNo(),r.invoiceDate(),r.taxableValue(),r.cgst(),r.sgst(),r.igst(),r.otherAdjustment(),r.invoiceValue(),r.notes(),r.rowVersion()));}
 @GetMapping("/records/{id}/bank-links") public List<PurchaseReconDtos.BankLinkDto> bankLinks(@PathVariable Integer id){return service.bankLinks(id);}
 @GetMapping("/metrics") public PurchaseReconDtos.Metrics metrics(){return service.metrics();}
 @PostMapping("/imports") public PurchaseReconDtos.ImportResult importRows(@RequestBody PurchaseReconDtos.ImportRequest r){return service.importRows(r);}
}
