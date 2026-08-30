package org.example.server.returns;
import java.util.List;
public final class ReturnDtos{private ReturnDtos(){}
 public record Summary(String no,String date,String invoice,String party,double total,double refund,String reason,String status,String refundStatus,String email){}
 public record Line(String name,String code,double quantity,String unit,double rate,double tax,double amount,String reason){}
 public record Details(String no,String date,String invoice,String party,String type,String paymentTerms,String currency,String createdAt,String updatedAt,String attachment,String notes,double total,double refund,String status,String refundStatus,List<Line> lines){}
 public record CreateLine(String code,double quantity,double amount,String reason){}
 public record CreateRequest(String type,String invoiceNo,int partyId,String returnDate,List<CreateLine> lines){}
 public record Created(String returnNo){}
 public record Settlement(String invoiceNo,String status,double pendingAmount,double approvedReturnAmount,double settledAmount,String dueDate,String returnStatus,String refundStatus,double returnedQuantity,double originalQuantity){}
 public record UpdateRequest(String field,String value){}
 public record RefundRequest(double amount){}
 public record RefundCreateRequest(String date,double amount,String mode,String reference,String bankAccount,String refundedParty,String notes,String refundType,String createdBy){}
 public record RefundRow(int id,String date,String reference,String mode,String bankAccount,double amount,String refundedParty,String status,String notes,String attachment,String refundType){}
 public record RefundCreated(int id){}
 public record Metrics(double total,long count,double monthAmount,long monthCount,double approvedAmount,double refundAmount,double average){}public record Page(List<Summary> rows,int page,int size,long totalRows,int totalPages,Metrics metrics,List<String> parties){}public record Ok(boolean success,String message){}
}
