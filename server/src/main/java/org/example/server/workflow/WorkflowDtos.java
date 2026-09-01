package org.example.server.workflow; import java.math.BigDecimal; import java.time.LocalDate; import java.util.List;
public final class WorkflowDtos { private WorkflowDtos(){}
 public record Line(Integer id,Integer lineNo,String itemCode,String description,BigDecimal quantity,BigDecimal rate,BigDecimal amount){}
 public record Document(Integer id,String documentType,String documentNo,LocalDate documentDate,String projectNo,String parentNo,String partyName,Integer partyId,String customerPoNo,LocalDate expectedDate,String status,BigDecimal totalAmount,String notes,long rowVersion,List<Line> lines){}
 public record NextNumber(String value){}
 public record StockAvailability(String itemCode,String description,BigDecimal onHand,BigDecimal reserved,BigDecimal currentOrderReserved,BigDecimal freeToPromise){}
 public record ProjectProfitability(String projectNo,BigDecimal salesOrdered,BigDecimal purchaseCommitted,BigDecimal salesInvoiced,BigDecimal purchaseInvoiced,BigDecimal grossProfit,BigDecimal grossMarginPercent){}
}
