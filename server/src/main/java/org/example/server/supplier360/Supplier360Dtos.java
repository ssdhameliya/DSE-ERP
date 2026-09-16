package org.example.server.supplier360;
import java.math.BigDecimal;import java.util.List;
public final class Supplier360Dtos{private Supplier360Dtos(){}
 public record Supplier(int id,String code,String name,String contactPerson,String phone,String email,String gstin,String address,BigDecimal openingBalance,boolean active,long rowVersion){}
 public record PurchaseRow(int id,String invoiceNo,String invoiceDate,BigDecimal totalAmount,BigDecimal paidAmount,BigDecimal outstanding,String paymentStatus,String documentStatus){}
 public record PaymentRow(int id,String paymentDate,String referenceNo,String paymentMode,BigDecimal amount,String invoiceNo,String notes){}
 public record Summary(Supplier supplier,BigDecimal outstandingPayable,long purchaseCount,BigDecimal totalPurchases,BigDecimal lastPaymentAmount,String lastPaymentDate,List<PurchaseRow> recentPurchases,List<PaymentRow> recentPayments){}
 public record ContactRow(long id,int partyId,String name,String designation,String department,String mobile,String email,boolean primary,String notes,long rowVersion,String createdBy,String createdAt,String updatedBy,String updatedAt){}
 public record ContactSave(Long id,String name,String designation,String department,String mobile,String email,boolean primary,String notes,long rowVersion){}
 public record NoteRow(long id,int partyId,String note,String createdBy,String createdAt,String updatedBy,String updatedAt,long rowVersion){}
 public record NoteSave(Long id,String note,long rowVersion){} public record Ok(boolean success,String message){}
}
