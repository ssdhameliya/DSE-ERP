package org.example.server.operations;
import java.util.List;
public final class OperationDtos {
 private OperationDtos(){}
 public record PartyDto(Integer id,String partyCode,String name,String email,String phone,String gstin,String address){}
 public record LineDto(String itemCode,String itemDescription,double quantity,double rate,double discountPercent,double discountAmount,double gstPercent,double totalAmount){}
 public record ChargeDto(String chargeType,double amount,boolean taxable,double gstPercent){}
 public record SaleDto(Integer id,String invoiceNo,String invoiceDate,PartyDto customer,double subtotal,double discountAmount,double gstAmount,double totalAmount,String remarks,String createdAt,boolean emailSent,String dueDate,double paidAmount,String paymentStatus,boolean whatsappSent,String invoiceType,String salesperson,String source,String notes,String deliveryAddress,String paymentTerms,String transporter,String referenceNo,String poDate,String billingAddress,String gstType,String doorDelivery,String vehicleNumber,String contactPerson,String transportNote,String orderNo,String gstin,String billingGstin,String deliveryGstin,boolean sameAsBilling,String transporterGstin,String chargeType,double chargeAmount,String contactPersonMobile,String documentStatus,double quantity,List<ChargeDto> charges,List<LineDto> lines){}
 public record PurchaseDto(Integer id,String invoiceNo,String invoiceDate,PartyDto supplier,double subtotal,double gstAmount,double totalAmount,String remarks,String createdAt,boolean emailSent,String dueDate,double paidAmount,String paymentStatus,String documentStatus,String warehouse,String paymentTerms,String currency,String referenceNo,String gstTreatment,String transporter,String lrAwbNo,String discountType,double discountAmount,String attachmentPath,String createdBy,String deliveryDate,double quantity,List<LineDto> lines){}
 public record FinanceDto(Integer id,String voucherNo,String voucherType,String voucherDate,Integer partyId,String category,String referenceNo,double amount,String paymentMode,String notes,String accountName,String billPath,boolean reconciled,Long statementTransactionId,String linkedTargetType,Integer linkedTargetId,String linkedDocumentNo){}
 public record FinanceMetrics(double bankBalance,double credits,double debits,long bankEntries,long depositCount,long withdrawalCount,double expenseMonth,double expenseYear,long expenseEntries,String topExpenseCategory,double topExpenseAmount,long pendingReconcile,double pendingReconcileAmount){}
 public record StockHistoryDto(String date,String type,double quantity,String reason,String reference,String user){}
 public record StockAdjustmentRequest(String itemCode,String type,double quantity,String reason,String referenceNo,String createdBy){}
 public record NextNumber(String value){}
 public record OperationResponse(boolean success,String message){}
}
