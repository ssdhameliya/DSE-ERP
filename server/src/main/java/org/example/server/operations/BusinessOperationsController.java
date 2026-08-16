package org.example.server.operations;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/operations")
public class BusinessOperationsController {
 private final BusinessOperationsService s; public BusinessOperationsController(BusinessOperationsService s){this.s=s;}
 @GetMapping("/sales") public List<OperationDtos.SaleDto> sales(){return s.sales();}
 @GetMapping("/sales/by-invoice") public OperationDtos.SaleDto sale(@RequestParam String invoiceNo){return s.sale(invoiceNo);}
 @GetMapping("/sales/exists") public Map<String,Boolean> saleExists(@RequestParam String invoiceNo){return Map.of("exists",s.saleExists(invoiceNo));}
 @PostMapping("/sales") public OperationDtos.SaleDto saveSale(@RequestBody OperationDtos.SaleDto d){return s.saveSale(d);}
 @PutMapping("/sales") public OperationDtos.SaleDto updateSale(@RequestBody OperationDtos.SaleDto d){return s.updateSale(d);}
 @DeleteMapping("/sales") public OperationDtos.OperationResponse deleteSale(@RequestParam String invoiceNo){s.deleteSale(invoiceNo);return ok("Sale deleted");}
 @PostMapping("/sales/cancel") public OperationDtos.OperationResponse cancelSale(@RequestParam String invoiceNo){s.cancelSale(invoiceNo);return ok("Sale deleted");}
 @PostMapping("/sales/email-sent/{id}") public OperationDtos.OperationResponse saleEmail(@PathVariable int id){s.markSaleEmail(id);return ok("Updated");}
 @GetMapping("/sales/next-invoice") public OperationDtos.NextNumber nextSale(){return new OperationDtos.NextNumber(s.nextSalesInvoice());}

 @GetMapping("/purchases") public List<OperationDtos.PurchaseDto> purchases(){return s.purchases();}
 @GetMapping("/purchases/by-invoice") public OperationDtos.PurchaseDto purchase(@RequestParam String invoiceNo){return s.purchase(invoiceNo);}
 @GetMapping("/purchases/exists") public Map<String,Boolean> purchaseExists(@RequestParam String invoiceNo){return Map.of("exists",s.purchaseExists(invoiceNo));}
 @PostMapping("/purchases") public OperationDtos.PurchaseDto savePurchase(@RequestBody OperationDtos.PurchaseDto d){return s.savePurchase(d);}
 @PutMapping("/purchases") public OperationDtos.PurchaseDto updatePurchase(@RequestBody OperationDtos.PurchaseDto d){return s.updatePurchase(d);}
 @DeleteMapping("/purchases") public OperationDtos.OperationResponse deletePurchase(@RequestParam String invoiceNo){s.deletePurchase(invoiceNo);return ok("Purchase deleted");}
 @PostMapping("/purchases/email-sent/{id}") public OperationDtos.OperationResponse purchaseEmail(@PathVariable int id){s.markPurchaseEmail(id);return ok("Updated");}
 @GetMapping("/purchases/next-invoice") public OperationDtos.NextNumber nextPurchase(){return new OperationDtos.NextNumber(s.nextPurchaseInvoice());}

 @GetMapping("/finance") public List<OperationDtos.FinanceDto> finance(){return s.finance();}
 @PostMapping("/finance") public OperationDtos.FinanceDto saveFinance(@RequestBody OperationDtos.FinanceDto d){return s.saveFinance(d);}
 @PutMapping("/finance") public OperationDtos.FinanceDto updateFinance(@RequestBody OperationDtos.FinanceDto d){return s.updateFinance(d);}
 @DeleteMapping("/finance/{id}") public OperationDtos.OperationResponse deleteFinance(@PathVariable int id){s.deleteFinance(id);return ok("Finance entry deleted");}
 @GetMapping("/finance/next-voucher") public OperationDtos.NextNumber nextVoucher(){return new OperationDtos.NextNumber(s.nextVoucher());}
 @GetMapping("/finance/metrics") public OperationDtos.FinanceMetrics metrics(){return s.financeMetrics();}
 @GetMapping("/stock/history") public List<OperationDtos.StockHistoryDto> stockHistory(@RequestParam String itemCode){return s.stockHistory(itemCode);}
 @PostMapping("/stock/adjust") public OperationDtos.OperationResponse adjustStock(@RequestBody OperationDtos.StockAdjustmentRequest d){s.adjustStock(d);return ok("Stock adjusted");}
 private OperationDtos.OperationResponse ok(String m){return new OperationDtos.OperationResponse(true,m);}
}
