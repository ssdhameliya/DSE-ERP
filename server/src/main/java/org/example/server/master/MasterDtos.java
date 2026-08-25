package org.example.server.master;
import java.util.List;
public final class MasterDtos {
 private MasterDtos(){}
 public record OperationResponse(boolean success,String message){}
 public record ItemBulkDeleteRequest(List<String> itemCodes){}
 public record ItemDeleteIssue(String itemCode,String itemName,List<String> usages){}
 public record ItemBulkDeleteValidation(boolean valid,int requestedCount,List<ItemDeleteIssue> issues){}
 public record PartyDto(Integer id,String partyType,String partyCode,String name,String contactPerson,String phone,String email,String gstin,String address,double openingBalance,boolean active,long rowVersion){}
 public record ItemDto(Integer id,String itemCode,String description,String category,String brand,String material,String size,String unit,String hsn,double gst,double discountPercent,double purchasePrice,double sellingPrice,double openingStock,double minimumStock,double reservedStock,String location,String remarks,boolean active,long rowVersion){}
 public record LookupDto(Integer id,String lookupType,String lookupCode,String lookupValue,String description,int displayOrder,boolean active,long rowVersion){}
 public record CategoryDto(Integer id,String categoryCode,String categoryName,String description,int displayOrder,boolean active,long valueCount,long activeValueCount,long rowVersion){}
 public record RenameCategoryRequest(String oldName,String newName){}
 public record CategoryUpsertRequest(String code,String name,String description){}
 public record NextCodeResponse(String code){}
 public record ExistsResponse(boolean exists){}
 public record ValuesResponse(List<String> values){}
 public record ReferenceFormatsResponse(java.util.Map<String,String> formats){}
 public record SalesEntryBootstrap(List<String> paymentTerms,List<String> chargeTypes,List<String> gstTypes,List<LookupDto> transporters,List<PartyDto> customers){}
}
