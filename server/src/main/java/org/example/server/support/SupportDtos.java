package org.example.server.support;
import java.util.List;
public final class SupportDtos { private SupportDtos(){}
 public record SavedView(String name,String data){} public record SavedViewSave(Integer userId,String screen,String name,String data){}
 public record PaymentRow(int id,String date,String reference,String mode,double amount,String notes,String receivedFrom,String attachment,String paymentType){} public record PaymentCreated(int id){}
 public record PaymentRequest(String documentType,int documentId,String date,double amount,String mode,String reference,String notes,String receivedFrom,String paymentType,String attachment,String createdBy){} public record PaymentUpdateRequest(String date,double amount,String mode,String reference,String notes,String receivedFrom){}
 public record CommunicationRow(int id,String entityType,int entityId,String documentLabel,String channel,String recipient,String subject,String status,String errorMessage,String createdBy,String createdAt){}
 public record CommunicationRequest(String entityType,int entityId,String channel,String recipient,String subject,String status,String errorMessage,String createdBy){}
 public record SearchRow(String module,String reference,String description,String detail,String targetFxml){}
 public record ReportSummary(double salesTotal,double purchaseTotal,double stockValue,List<List<String>> salesByCustomer,List<List<String>> purchasesBySupplier,List<List<String>> lowStock){}
 public record Setting(String key,String value){} public record BackupMeta(String fileName,String source,String integrityStatus,Integer schemaVersion,String applicationId,long fileSize){}
 public record AttachmentFile(String fileName,byte[] data){}
 public record AttachmentMeta(long id,String documentType,int documentId,String fileName,String createdBy,String createdAt){}
 public record Ok(boolean success,String message){} public record Bool(boolean value){} public record Text(String value){}
}
