package org.example.server.returns;
import java.util.List;import java.util.Map;
public final class ReturnDtos{private ReturnDtos(){}
 public record Summary(String no,String date,String invoice,String party,double total,double refund,String reason,String status,String refundStatus,String email){}
 public record Line(String name,String code,double quantity,String unit,double rate,double tax,double amount,String reason){}
 public record Details(String no,String date,String invoice,String party,String type,String paymentTerms,String currency,String createdAt,String updatedAt,String attachment,String notes,double total,double refund,String status,String refundStatus,List<Line> lines){}
 public record CreateLine(String code,double quantity,double amount,String reason){}
 public record CreateRequest(String type,String invoiceNo,int partyId,String returnDate,List<CreateLine> lines){}
 public record Created(String returnNo){} public record UpdateRequest(String field,String value){} public record RefundRequest(double amount){} public record Ok(boolean success,String message){}
}
