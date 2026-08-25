package org.example.server.recon;
import java.util.List;
public final class PurchaseReconDtos{
 private PurchaseReconDtos(){}
 public record SupplierDto(Integer id,String reference,String legalName,String gstin,String pan,String contactPerson,String phone,String email,String notes,String status,String source,long reconCount,String createdAt,String updatedAt,long rowVersion){}
 public record SupplierSaveRequest(Integer id,String legalName,String gstin,String pan,String contactPerson,String phone,String email,String notes,String status,long rowVersion){}
 public record ReconDto(Integer id,String reference,Integer supplierId,String supplierReference,String supplierName,String supplierGstin,String supplierInvoiceNo,String invoiceDate,String financialYear,double taxableValue,double cgst,double sgst,double igst,double otherAdjustment,double invoiceValue,double linkedAmount,double balance,double taxDifference,boolean taxReviewRequired,String status,String source,Long importBatchId,String sourceSheet,Integer sourceRow,String notes,String createdAt,String updatedAt,List<BankLinkDto> bankLinks,long rowVersion){}
 public record ReconSaveRequest(Integer id,Integer supplierId,String supplierInvoiceNo,String invoiceDate,double taxableValue,double cgst,double sgst,double igst,double otherAdjustment,double invoiceValue,String notes,long rowVersion){}
 public record BankLinkDto(Long allocationId,Long statementTransactionId,String bankTransactionDate,String bankReference,double allocatedAmount,Integer financeEntryId,String financeVoucherNo,String createdAt){}
 public record ImportRow(String sourceSheet,Integer sourceRow,String supplierName,String supplierGstin,String supplierInvoiceNo,String invoiceDate,double taxableValue,double cgst,double sgst,double igst,double invoiceValue){}
 public record ImportRequest(String sourceFileName,String sourceFingerprint,String importNote,boolean dryRun,List<ImportRow> rows){}
 public record ImportRowResult(String sourceSheet,Integer sourceRow,String status,String action,String supplierName,String supplierReference,String invoiceNo,String message,boolean warning){}
 public record ImportResult(int totalRows,int importedRows,int updatedRows,int alreadyCurrentRows,int newSuppliers,int existingSuppliers,int duplicateRows,int conflictRows,int warningRows,int ignoredRows,List<ImportRowResult> details){}
 public record Metrics(long total,long open,long partial,long reconciled,long review,double invoiceValue,double linkedValue,double outstandingValue){}
 public record Page(List<ReconDto> rows,int page,int size,long totalRows,int totalPages,Metrics metrics){}
 public record Ok(boolean success,String message){}
}
