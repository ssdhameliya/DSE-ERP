package org.example.api.master;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.ConfigManager;
import org.example.model.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Phase-2 REST client for customer/supplier, item and lookup/master data. */
public final class MasterApiClient {
 private final HttpClient http = org.example.api.ApiRuntime.HTTP;
 private final ObjectMapper json = org.example.api.ApiRuntime.JSON;
 private final String base;
 public MasterApiClient(){String b=ConfigManager.getDataApiBaseUrl(); while(b.endsWith("/"))b=b.substring(0,b.length()-1);base=b;}

 public List<Party> parties(String type){return get("/api/master/parties?type="+enc(type),new TypeReference<List<PartyDto>>(){}).stream().map(this::party).toList();}
 public List<Party> searchParties(String type,String query,int limit){return get("/api/master/parties/search?type="+enc(type)+"&q="+enc(query)+"&limit="+Math.max(1,Math.min(limit,100)),new TypeReference<List<PartyDto>>(){}).stream().map(this::party).toList();}
 public void saveParty(Party p){applyPartyIdentity(p,post("/api/master/parties",partyDto(p),PartyDto.class));}
 public void updateParty(Party p){applyPartyIdentity(p,put("/api/master/parties",partyDto(p),PartyDto.class));}
 public void deleteParty(int id){delete("/api/master/parties/"+id);}
 public void deleteParty(int id,long rowVersion){delete("/api/master/parties/"+id+"?rowVersion="+Math.max(0,rowVersion));}
 public boolean partyExists(String code){return get("/api/master/parties/exists?code="+enc(code),ExistsResponse.class).exists();}
 public String nextPartyCode(String type){return get("/api/master/parties/next-code?type="+enc(type),NextCodeResponse.class).code();}

 public List<Item> items(){return get("/api/master/items",new TypeReference<List<ItemDto>>(){}).stream().map(this::item).toList();}
 public List<Item> searchItems(String query,int limit){return get("/api/master/items/search?q="+enc(query)+"&limit="+Math.max(1,Math.min(limit,100)),new TypeReference<List<ItemDto>>(){}).stream().map(this::item).toList();}
 public SalesEntryBootstrap salesEntryBootstrap(){SalesEntryBootstrapDto d=get("/api/master/sales-entry-bootstrap",SalesEntryBootstrapDto.class);return new SalesEntryBootstrap(d.paymentTerms,d.chargeTypes,d.gstTypes,d.transporters==null?List.of():d.transporters.stream().map(this::lookup).toList(),d.customers==null?List.of():d.customers.stream().map(this::party).toList());}
 public void saveItem(Item i){applyItemIdentity(i,post("/api/master/items",itemDto(i),ItemDto.class));}
 public void updateItem(Item i){applyItemIdentity(i,put("/api/master/items",itemDto(i),ItemDto.class));}
 public void deleteItem(String code){delete("/api/master/items/"+encPath(code));}
 public void deleteItem(String code,long rowVersion){delete("/api/master/items/"+encPath(code)+"?rowVersion="+Math.max(0,rowVersion));}
 public boolean itemExists(String code){return get("/api/master/items/exists?code="+enc(code),ExistsResponse.class).exists();}
 public String nextItemCode(){return get("/api/master/items/next-code",NextCodeResponse.class).code();}
 public void saveItems(List<Item> rows){post("/api/master/items/bulk",rows.stream().map(this::itemDto).toList(),OperationResponse.class);}
 public ItemBulkDeleteValidation validateBulkDeleteItems(List<String> codes){return post("/api/master/items/bulk-delete/validate",new ItemBulkDeleteRequest(codes==null?List.of():List.copyOf(codes)),ItemBulkDeleteValidation.class);}
 public OperationResponse bulkDeleteItems(List<String> codes){return post("/api/master/items/bulk-delete",new ItemBulkDeleteRequest(codes==null?List.of():List.copyOf(codes)),OperationResponse.class);}

 public Map<String,String> referenceFormats(){ReferenceFormatsResponse r=get("/api/master/reference-formats",ReferenceFormatsResponse.class);return r==null||r.formats()==null?Map.of():Map.copyOf(r.formats());}
 public List<Lookup> lookups(String type){return get("/api/master/lookups?type="+enc(type),new TypeReference<List<LookupDto>>(){}).stream().map(this::lookup).toList();}
 public List<String> lookupValues(String type){return get("/api/master/lookups/values?type="+enc(type),ValuesResponse.class).values();}
 public List<String> lookupValuesByCategoryCode(String code){return get("/api/master/lookups/values-by-category-code?code="+enc(code),ValuesResponse.class).values();}
 public List<Lookup> lookupsByCategoryCode(String code){return get("/api/master/lookups/by-category-code?code="+enc(code),new TypeReference<List<LookupDto>>(){}).stream().map(this::lookup).toList();}
 public void saveLookup(Lookup l){applyLookupIdentity(l,post("/api/master/lookups",lookupDto(l),LookupDto.class));}
 public void updateLookup(Lookup l){applyLookupIdentity(l,put("/api/master/lookups",lookupDto(l),LookupDto.class));}
 public void deleteLookup(int id){delete("/api/master/lookups/"+id);}
 public void deleteLookup(int id,long rowVersion){delete("/api/master/lookups/"+id+"?rowVersion="+Math.max(0,rowVersion));} 
 public LookupDto setLookupActive(int id,boolean active,long rowVersion){return put("/api/master/lookups/"+id+"/active?active="+active+"&rowVersion="+Math.max(0,rowVersion),null,LookupDto.class);}
 public String nextLookupCode(String type){return get("/api/master/lookups/next-code?type="+enc(type),NextCodeResponse.class).code();}
 public List<CategoryDto> categories(){return get("/api/master/categories",new TypeReference<List<CategoryDto>>(){});}
 public void addCategory(String name){postNoBody("/api/master/categories?name="+enc(name));}
 public CategoryDto upsertCategory(String code,String name,String description){return put("/api/master/categories/upsert",new CategoryUpsertRequest(code,name,description),CategoryDto.class);}
 public void renameCategory(String oldName,String newName,long rowVersion){put("/api/master/categories/rename?rowVersion="+Math.max(0,rowVersion),new RenameCategoryRequest(oldName,newName),CategoryDto.class);}
 public void deleteCategory(String name,long rowVersion){delete("/api/master/categories?name="+enc(name)+"&rowVersion="+Math.max(0,rowVersion));}
 public CategoryDto setCategoryActive(String name,boolean active,long rowVersion){return put("/api/master/categories/active?name="+enc(name)+"&active="+active+"&rowVersion="+Math.max(0,rowVersion),null,CategoryDto.class);}

 private void applyPartyIdentity(Party p,PartyDto d){if(p==null||d==null)return;p.setId(n(d.id));p.setRowVersion(d.rowVersion());}
 private void applyItemIdentity(Item i,ItemDto d){if(i==null||d==null)return;i.setId(n(d.id));i.setRowVersion(d.rowVersion());}
 private void applyLookupIdentity(Lookup l,LookupDto d){if(l==null||d==null)return;l.setId(n(d.id));l.setRowVersion(d.rowVersion());}
 private Party party(PartyDto d){Party p=new Party();p.setId(n(d.id));p.setPartyType(d.partyType);p.setPartyCode(d.partyCode);p.setName(d.name);p.setContactPerson(d.contactPerson);p.setPhone(d.phone);p.setEmail(d.email);p.setGstin(d.gstin);p.setAddress(d.address);p.setOpeningBalance(d.openingBalance);p.setActive(d.active);p.setRowVersion(d.rowVersion);return p;}
 private PartyDto partyDto(Party p){return new PartyDto(p.getId(),p.getPartyType(),p.getPartyCode(),p.getName(),p.getContactPerson(),p.getPhone(),p.getEmail(),p.getGstin(),p.getAddress(),p.getOpeningBalance(),p.isActive(),p.getRowVersion());}
 private Item item(ItemDto d){Item i=new Item();i.setId(n(d.id));i.setItemCode(d.itemCode);i.setDescription(d.description);i.setCategory(d.category);i.setBrand(d.brand);i.setMaterial(d.material);i.setSize(d.size);i.setUnit(d.unit);i.setHsn(d.hsn);i.setGst(d.gst);i.setDiscountPercent(d.discountPercent);i.setPurchasePrice(d.purchasePrice);i.setSellingPrice(d.sellingPrice);i.setOpeningStock(d.openingStock);i.setMinimumStock(d.minimumStock);i.setReservedStock(d.reservedStock);i.setLocation(d.location);i.setRemarks(d.remarks);i.setRowVersion(d.rowVersion);return i;}
 private ItemDto itemDto(Item i){return new ItemDto(i.getId(),i.getItemCode(),i.getDescription(),i.getCategory(),i.getBrand(),i.getMaterial(),i.getSize(),i.getUnit(),i.getHsn(),i.getGst(),i.getDiscountPercent(),i.getPurchasePrice(),i.getSellingPrice(),i.getOpeningStock(),i.getMinimumStock(),i.getReservedStock(),i.getLocation(),i.getRemarks(),true,i.getRowVersion());}
 private Lookup lookup(LookupDto d){Lookup l=new Lookup();l.setId(n(d.id));l.setLookupType(d.lookupType);l.setLookupCode(d.lookupCode);l.setLookupValue(d.lookupValue);l.setDescription(d.description);l.setDisplayOrder(d.displayOrder);l.setActive(d.active);l.setRowVersion(d.rowVersion);return l;}
 private LookupDto lookupDto(Lookup l){return new LookupDto(l.getId(),l.getLookupType(),l.getLookupCode(),l.getLookupValue(),l.getDescription(),l.getDisplayOrder(),l.isActive(),l.getRowVersion());}
 private int n(Integer v){return v==null?0:v;}

 private <T>T get(String path,Class<T> c){return request("GET",path,null,c,null);} private <T>T get(String path,TypeReference<T> t){return request("GET",path,null,null,t);}
 private <T>T post(String path,Object b,Class<T> c){return request("POST",path,b,c,null);} private <T>T put(String path,Object b,Class<T> c){return request("PUT",path,b,c,null);}
 private void postNoBody(String path){request("POST",path,null,OperationResponse.class,null);} private void delete(String path){request("DELETE",path,null,OperationResponse.class,null);}
 private <T>T request(String method,String path,Object body,Class<T> cls,TypeReference<T> type){try{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15)).header("Accept","application/json");org.example.api.ApiSession.authorize(b); if(body!=null){b.header("Content-Type","application/json");String payload=json.writeValueAsString(body);b.method(method,HttpRequest.BodyPublishers.ofString(payload));}else b.method(method,HttpRequest.BodyPublishers.noBody()); HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString()); if(r.statusCode()<200||r.statusCode()>=300)throw new IllegalStateException(apiMessage(r.statusCode(),r.body())); return type!=null?json.readValue(r.body(),type):json.readValue(r.body(),cls);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Master API request interrupted",e);}catch(IOException|IllegalArgumentException e){throw new IllegalStateException("Cannot reach master-data server at "+base,e);}}
 private String apiMessage(int status,String body){if(status==409){try{var n=json.readTree(body);String m=n.path("message").asText("");if(!m.isBlank())return m;}catch(Exception ignored){}return "This record was changed by another user. Reload the latest version and try again.";}return "Master API error ("+status+"): "+body;}
 private String enc(String v){return URLEncoder.encode(v==null?"":v,StandardCharsets.UTF_8);} private String encPath(String v){return enc(v).replace("+","%20");}
 public record PartyDto(Integer id,String partyType,String partyCode,String name,String contactPerson,String phone,String email,String gstin,String address,double openingBalance,boolean active,long rowVersion){}
 public record ItemDto(Integer id,String itemCode,String description,String category,String brand,String material,String size,String unit,String hsn,double gst,double discountPercent,double purchasePrice,double sellingPrice,double openingStock,double minimumStock,double reservedStock,String location,String remarks,boolean active,long rowVersion){}
 public record LookupDto(Integer id,String lookupType,String lookupCode,String lookupValue,String description,int displayOrder,boolean active,long rowVersion){}
 public record ReferenceFormatsResponse(Map<String,String> formats){}
 public record SalesEntryBootstrap(List<String> paymentTerms,List<String> chargeTypes,List<String> gstTypes,List<Lookup> transporters,List<Party> customers){}
 private record SalesEntryBootstrapDto(List<String> paymentTerms,List<String> chargeTypes,List<String> gstTypes,List<LookupDto> transporters,List<PartyDto> customers){}
 public record CategoryDto(Integer id,String categoryCode,String categoryName,String description,int displayOrder,boolean active,long valueCount,long activeValueCount,long rowVersion){}
 public record RenameCategoryRequest(String oldName,String newName){}
 public record CategoryUpsertRequest(String code,String name,String description){}
 public record ItemBulkDeleteRequest(List<String> itemCodes){}
 public record ItemDeleteIssue(String itemCode,String itemName,List<String> usages){}
 public record ItemBulkDeleteValidation(boolean valid,int requestedCount,List<ItemDeleteIssue> issues){}
 public record NextCodeResponse(String code){} public record ExistsResponse(boolean exists){} public record ValuesResponse(List<String> values){} public record OperationResponse(boolean success,String message){}
}
