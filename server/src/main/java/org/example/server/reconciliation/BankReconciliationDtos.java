package org.example.server.reconciliation;
import java.util.List;

public final class BankReconciliationDtos {
 private BankReconciliationDtos(){}
 public record ImportRow(Integer sourceRowNumber,String transactionTimestamp,String transactionDate,String valueDate,String description,String reference,double debit,double credit,double balance,String transactionFingerprint){}
 public record ImportRequest(String bankName,String bankAccount,String accountHolder,String statementFrom,String statementTo,String currency,Double openingBalance,Double closingBalance,String sourceFingerprint,String sourceFileName,String sourceCsv,String importedBy,List<ImportRow> rows){}
 public record ImportResult(BatchDto batch,int importedRows,int duplicateRows){}
 public record BatchDto(Long id,String bankName,String bankAccount,String accountHolder,String statementFrom,String statementTo,String currency,int transactionCount,double totalDebit,double totalCredit,int reconciledCount,double reconciliationPercent,String status,String sourceFileName,String importedAt){}
 public record SourceDto(String fileName,String fingerprint,String csvContent){}
 public record TransactionDto(Long id,Long importId,Integer sourceRowNumber,String transactionTimestamp,String transactionDate,String valueDate,String description,String reference,double debit,double credit,double balance,String status,String suggestedMatchType,Integer suggestedMatchId,Double suggestedConfidence,String matchLink,Integer financeEntryId,String linkedTargetType,Integer linkedTargetId,String linkedDocumentNo){}
 public record CandidateDto(String type,Integer id,String documentNo,String partyName,String documentDate,double totalAmount,double paidAmount,double outstanding,double confidence){}
 public record AllocationRequest(String targetType,Integer targetId,double amount){}
 public record MatchRequest(String user,List<AllocationRequest> allocations){}
 public record ExpenseRequest(String category,String accountName,String paymentMode,String notes,String billPath,String user){}
 public record IgnoreRequest(String note,String user){}
 public record NoteRequest(String note,String user){}
 public record OperationResult(boolean success,String message,String status,Integer financeEntryId){}
 public record AuditDto(Long id,String eventType,String detail,String previousStatus,String newStatus,String performedBy,String createdAt){}
 public record Metrics(int total,int unmatched,int suggested,int matched,int expenses,int ignored,int review,double totalCredits,double totalDebits,int reconciled,double reconciledPercent,String batchStatus){}
}
