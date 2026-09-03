package org.example.server.operations;
import java.util.List;
public final class OperationDtos {
 private OperationDtos(){}
 public record PartyDto(Integer id,String partyCode,String name,String email,String phone,String gstin,String address){}
 public record LineDto(String itemCode,String itemDescription,String itemCategory,String itemHsn,String itemUnit,String itemRemarks,double quantity,double rate,double discountPercent,double discountAmount,double gstPercent,double totalAmount){}
 public record ChargeDto(String chargeType,double amount,boolean taxable,double gstPercent){}
 public record SaleDto(Integer id,String invoiceNo,String invoiceDate,PartyDto customer,double subtotal,double discountAmount,double gstAmount,double totalAmount,String remarks,String createdAt,boolean emailSent,String dueDate,double paidAmount,String paymentStatus,boolean whatsappSent,String invoiceType,String salesperson,String source,String notes,String deliveryAddress,String paymentTerms,String transporter,String referenceNo,String poDate,String billingAddress,String gstType,String doorDelivery,String vehicleNumber,String contactPerson,String transportNote,String orderNo,String gstin,String billingGstin,String deliveryGstin,boolean sameAsBilling,String transporterGstin,String chargeType,double chargeAmount,String contactPersonMobile,String documentStatus,String attachmentPath,double quantity,List<ChargeDto> charges,List<LineDto> lines,long rowVersion){}
 public record PurchaseDto(Integer id,String invoiceNo,String invoiceDate,PartyDto supplier,double subtotal,double gstAmount,double totalAmount,String remarks,String createdAt,boolean emailSent,String dueDate,double paidAmount,String paymentStatus,String documentStatus,String warehouse,String paymentTerms,String currency,String referenceNo,String gstTreatment,String transporter,String lrAwbNo,String discountType,double discountAmount,String attachmentPath,String createdBy,String deliveryDate,String billingAddress,String deliveryAddress,String billingGstin,String deliveryGstin,String gstType,String transporterGstin,String vehicleNumber,String contactPerson,String contactPersonMobile,String notes,String orderNo,String poDate,boolean sameAsBilling,double quantity,List<ChargeDto> charges,List<LineDto> lines,long rowVersion){}
 public record FinanceDto(Integer id,String voucherNo,String voucherType,String voucherDate,Integer partyId,String category,String referenceNo,double amount,String paymentMode,String notes,String accountName,String billPath,boolean reconciled,Long statementTransactionId,String linkedTargetType,Integer linkedTargetId,String linkedDocumentNo,long rowVersion){}
 public record FinanceMetrics(double bankBalance,double credits,double debits,long bankEntries,long depositCount,long withdrawalCount,double expenseMonth,double expenseYear,long expenseEntries,String topExpenseCategory,double topExpenseAmount,long pendingReconcile,double pendingReconcileAmount){}
 public record StockHistoryDto(String date,String type,double quantity,String reason,String reference,String user){}
 public record StockAdjustmentRequest(String itemCode,String type,double quantity,String reason,String referenceNo,String createdBy){}
 public record NextNumber(String value){}
 public record RegisterTotals(long rows,double total,double paid,double balance){}
 public record MetricPoint(String label,double value){}
 public record SalesMetrics(double totalSales,long invoiceCount,double todaySales,long todayCount,double pendingBalance,long pendingCount,double overdueBalance,long overdueCount,double dueSoonBalance,long dueSoonCount,double emailRate,List<MetricPoint> dueBuckets,List<MetricPoint> topCustomers,List<MetricPoint> monthlySales){}
 public record PurchaseMetrics(double totalPurchases,long activeDocuments,long suppliers,double itemQuantity,double paidAmount){}
 public record SalePage(List<SaleDto> rows,int page,int size,long totalRows,int totalPages,RegisterTotals filteredTotals,SalesMetrics metrics,List<String> customers){}
 public record PurchasePage(List<PurchaseDto> rows,int page,int size,long totalRows,int totalPages,RegisterTotals filteredTotals,PurchaseMetrics metrics,List<String> suppliers){}
 public record FinancePage(List<FinanceDto> rows,int page,int size,long totalRows,int totalPages){}
 public record OperationResponse(boolean success,String message){}
}
