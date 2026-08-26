package org.example.server.operations;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/operations")
public class BusinessOperationsController {
 private final BusinessOperationsService s; public BusinessOperationsController(BusinessOperationsService s){this.s=s;}
 @GetMapping("/sales") public List<OperationDtos.SaleDto> sales(){return s.sales();}
 @GetMapping("/sales/page") public OperationDtos.SalePage salesPage(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String invoice,@RequestParam(defaultValue="") String customer,@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String to,@RequestParam(defaultValue="") String paymentStatus,@RequestParam(defaultValue="") String due,@RequestParam(defaultValue="") String mail,@RequestParam(defaultValue="") String whatsapp,@RequestParam(defaultValue="") String invoiceType,@RequestParam(defaultValue="") String documentStatus,@RequestParam(required=false) Double minAmount,@RequestParam(required=false) Double maxAmount){return s.salesPage(page,size,q,invoice,customer,from,to,paymentStatus,due,mail,whatsapp,invoiceType,documentStatus,minAmount,maxAmount);}
 @GetMapping("/sales/by-invoice") public OperationDtos.SaleDto sale(@RequestParam String invoiceNo){return s.sale(invoiceNo);}
 @GetMapping("/sales/exists") public Map<String,Boolean> saleExists(@RequestParam String invoiceNo){return Map.of("exists",s.saleExists(invoiceNo));}
 @PostMapping("/sales") public OperationDtos.SaleDto saveSale(@RequestBody OperationDtos.SaleDto d){return s.saveSale(d);}
 @PutMapping("/sales") public OperationDtos.SaleDto updateSale(@RequestBody OperationDtos.SaleDto d){return s.updateSale(d);}
 @DeleteMapping("/sales") public OperationDtos.OperationResponse deleteSale(@RequestParam String invoiceNo){s.deleteSale(invoiceNo);return ok("Sale deleted");}
 @PostMapping("/sales/cancel") public OperationDtos.OperationResponse cancelSale(@RequestParam String invoiceNo){s.cancelSale(invoiceNo);return ok("Sale cancelled");}
 @PostMapping("/sales/approve") public OperationDtos.OperationResponse approveSale(@RequestParam String invoiceNo){s.approveSale(invoiceNo);return ok("Sale approved");}
 @PostMapping("/sales/reject") public OperationDtos.OperationResponse rejectSale(@RequestParam String invoiceNo,@RequestParam(required=false) String reason){s.rejectSale(invoiceNo,reason);return ok("Sale rejected");}
 @PostMapping("/sales/email-sent/{id}") public OperationDtos.OperationResponse saleEmail(@PathVariable int id){s.markSaleEmail(id);return ok("Updated");}
 @GetMapping("/sales/next-invoice") public OperationDtos.NextNumber nextSale(){return new OperationDtos.NextNumber(s.previewSalesInvoice());}

 @GetMapping("/purchases") public List<OperationDtos.PurchaseDto> purchases(){return s.purchases();}
 @GetMapping("/purchases/page") public OperationDtos.PurchasePage purchasesPage(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String supplier,@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String to,@RequestParam(defaultValue="") String paymentStatus,@RequestParam(defaultValue="") String mail,@RequestParam(defaultValue="") String documentStatus){return s.purchasesPage(page,size,q,supplier,from,to,paymentStatus,mail,documentStatus);}
 @GetMapping("/purchases/by-invoice") public OperationDtos.PurchaseDto purchase(@RequestParam String invoiceNo){return s.purchase(invoiceNo);}
 @GetMapping("/purchases/exists") public Map<String,Boolean> purchaseExists(@RequestParam String invoiceNo){return Map.of("exists",s.purchaseExists(invoiceNo));}
 @PostMapping("/purchases") public OperationDtos.PurchaseDto savePurchase(@RequestBody OperationDtos.PurchaseDto d){return s.savePurchase(d);}
 @PutMapping("/purchases") public OperationDtos.PurchaseDto updatePurchase(@RequestBody OperationDtos.PurchaseDto d){return s.updatePurchase(d);}
 @DeleteMapping("/purchases") public OperationDtos.OperationResponse deletePurchase(@RequestParam String invoiceNo){s.deletePurchase(invoiceNo);return ok("Purchase deleted");}
 @PostMapping("/purchases/cancel") public OperationDtos.OperationResponse cancelPurchase(@RequestParam String invoiceNo){s.cancelPurchase(invoiceNo);return ok("Purchase cancelled");}
 @PostMapping("/purchases/approve") public OperationDtos.OperationResponse approvePurchase(@RequestParam String invoiceNo){s.approvePurchase(invoiceNo);return ok("Purchase approved");}
 @PostMapping("/purchases/reject") public OperationDtos.OperationResponse rejectPurchase(@RequestParam String invoiceNo,@RequestParam(required=false) String reason){s.rejectPurchase(invoiceNo,reason);return ok("Purchase rejected");}
 @PostMapping("/purchases/email-sent/{id}") public OperationDtos.OperationResponse purchaseEmail(@PathVariable int id){s.markPurchaseEmail(id);return ok("Updated");}
 @GetMapping("/purchases/next-invoice") public OperationDtos.NextNumber nextPurchase(){return new OperationDtos.NextNumber(s.previewPurchaseInvoice());}

 @GetMapping("/finance") public List<OperationDtos.FinanceDto> finance(){return s.finance();}
 @GetMapping("/finance/page") public OperationDtos.FinancePage financePage(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,@RequestParam(defaultValue="") String mode,@RequestParam(defaultValue="") String period,@RequestParam(defaultValue="") String type,@RequestParam(defaultValue="") String q){return s.financePage(page,size,mode,period,type,q);}
 @GetMapping("/finance/{id}") public OperationDtos.FinanceDto finance(@PathVariable int id){return s.finance(id);}
 @PostMapping("/finance") public OperationDtos.FinanceDto saveFinance(@RequestBody OperationDtos.FinanceDto d){return s.saveFinance(d);}
 @PutMapping("/finance") public OperationDtos.FinanceDto updateFinance(@RequestBody OperationDtos.FinanceDto d){return s.updateFinance(d);}
 @DeleteMapping("/finance/{id}") public OperationDtos.OperationResponse deleteFinance(@PathVariable int id,@RequestParam(defaultValue="-1") long rowVersion){s.deleteFinance(id,rowVersion);return ok("Finance entry deleted");}
 @GetMapping("/finance/next-voucher") public OperationDtos.NextNumber nextVoucher(){return new OperationDtos.NextNumber(s.previewVoucher());}
 @GetMapping("/finance/metrics") public OperationDtos.FinanceMetrics metrics(){return s.financeMetrics();}
 @GetMapping("/stock/history") public List<OperationDtos.StockHistoryDto> stockHistory(@RequestParam String itemCode){return s.stockHistory(itemCode);}
 @PostMapping("/stock/adjust") public OperationDtos.OperationResponse adjustStock(@RequestBody OperationDtos.StockAdjustmentRequest d){s.adjustStock(d);return ok("Stock adjusted");}
 private OperationDtos.OperationResponse ok(String m){return new OperationDtos.OperationResponse(true,m);}
}
