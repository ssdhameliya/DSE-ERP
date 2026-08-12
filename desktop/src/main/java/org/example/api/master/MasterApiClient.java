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
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 private final ObjectMapper json=new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
 private final String base;
 public MasterApiClient(){String b=ConfigManager.getDataApiBaseUrl(); while(b.endsWith("/"))b=b.substring(0,b.length()-1);base=b;}

 public List<Party> parties(String type){return get("/api/master/parties?type="+enc(type),new TypeReference<List<PartyDto>>(){}).stream().map(this::party).toList();}
 public void saveParty(Party p){post("/api/master/parties",partyDto(p),PartyDto.class);}
 public void updateParty(Party p){put("/api/master/parties",partyDto(p),PartyDto.class);}
 public void deleteParty(int id){delete("/api/master/parties/"+id);}
 public boolean partyExists(String code){return get("/api/master/parties/exists?code="+enc(code),ExistsResponse.class).exists();}
 public String nextPartyCode(String type){return get("/api/master/parties/next-code?type="+enc(type),NextCodeResponse.class).code();}

 public List<Item> items(){return get("/api/master/items",new TypeReference<List<ItemDto>>(){}).stream().map(this::item).toList();}
 public void saveItem(Item i){post("/api/master/items",itemDto(i),ItemDto.class);}
 public void updateItem(Item i){put("/api/master/items",itemDto(i),ItemDto.class);}
 public void deleteItem(String code){delete("/api/master/items/"+encPath(code));}
 public boolean itemExists(String code){return get("/api/master/items/exists?code="+enc(code),ExistsResponse.class).exists();}
 public String nextItemCode(){return get("/api/master/items/next-code",NextCodeResponse.class).code();}
 public void saveItems(List<Item> rows){post("/api/master/items/bulk",rows.stream().map(this::itemDto).toList(),OperationResponse.class);}

 public List<Lookup> lookups(String type){return get("/api/master/lookups?type="+enc(type),new TypeReference<List<LookupDto>>(){}).stream().map(this::lookup).toList();}
 public List<String> lookupValues(String type){return get("/api/master/lookups/values?type="+enc(type),ValuesResponse.class).values();}
 public List<String> lookupValuesByCategoryCode(String code){return get("/api/master/lookups/values-by-category-code?code="+enc(code),ValuesResponse.class).values();}
 public void saveLookup(Lookup l){post("/api/master/lookups",lookupDto(l),LookupDto.class);}
 public void updateLookup(Lookup l){put("/api/master/lookups",lookupDto(l),LookupDto.class);}
 public void deleteLookup(int id){delete("/api/master/lookups/"+id);}
 public String nextLookupCode(String type){return get("/api/master/lookups/next-code?type="+enc(type),NextCodeResponse.class).code();}
 public List<CategoryDto> categories(){return get("/api/master/categories",new TypeReference<List<CategoryDto>>(){});}
 public void addCategory(String name){postNoBody("/api/master/categories?name="+enc(name));}
 public CategoryDto upsertCategory(String code,String name,String description){return put("/api/master/categories/upsert",new CategoryUpsertRequest(code,name,description),CategoryDto.class);}
 public void renameCategory(String oldName,String newName){put("/api/master/categories/rename",new RenameCategoryRequest(oldName,newName),CategoryDto.class);}
 public void deleteCategory(String name){delete("/api/master/categories?name="+enc(name));}

 private Party party(PartyDto d){Party p=new Party();p.setId(n(d.id));p.setPartyType(d.partyType);p.setPartyCode(d.partyCode);p.setName(d.name);p.setContactPerson(d.contactPerson);p.setPhone(d.phone);p.setEmail(d.email);p.setGstin(d.gstin);p.setAddress(d.address);p.setOpeningBalance(d.openingBalance);p.setActive(d.active);return p;}
 private PartyDto partyDto(Party p){return new PartyDto(p.getId(),p.getPartyType(),p.getPartyCode(),p.getName(),p.getContactPerson(),p.getPhone(),p.getEmail(),p.getGstin(),p.getAddress(),p.getOpeningBalance(),p.isActive());}
 private Item item(ItemDto d){Item i=new Item();i.setId(n(d.id));i.setItemCode(d.itemCode);i.setDescription(d.description);i.setCategory(d.category);i.setBrand(d.brand);i.setMaterial(d.material);i.setSize(d.size);i.setUnit(d.unit);i.setHsn(d.hsn);i.setGst(d.gst);i.setDiscountPercent(d.discountPercent);i.setPurchasePrice(d.purchasePrice);i.setSellingPrice(d.sellingPrice);i.setOpeningStock(d.openingStock);i.setMinimumStock(d.minimumStock);i.setReservedStock(d.reservedStock);i.setLocation(d.location);i.setRemarks(d.remarks);return i;}
 private ItemDto itemDto(Item i){return new ItemDto(i.getId(),i.getItemCode(),i.getDescription(),i.getCategory(),i.getBrand(),i.getMaterial(),i.getSize(),i.getUnit(),i.getHsn(),i.getGst(),i.getDiscountPercent(),i.getPurchasePrice(),i.getSellingPrice(),i.getOpeningStock(),i.getMinimumStock(),i.getReservedStock(),i.getLocation(),i.getRemarks(),true);}
 private Lookup lookup(LookupDto d){Lookup l=new Lookup();l.setId(n(d.id));l.setLookupType(d.lookupType);l.setLookupCode(d.lookupCode);l.setLookupValue(d.lookupValue);l.setDescription(d.description);l.setDisplayOrder(d.displayOrder);l.setActive(d.active);return l;}
 private LookupDto lookupDto(Lookup l){return new LookupDto(l.getId(),l.getLookupType(),l.getLookupCode(),l.getLookupValue(),l.getDescription(),l.getDisplayOrder(),l.isActive());}
 private int n(Integer v){return v==null?0:v;}

 private <T>T get(String path,Class<T> c){return request("GET",path,null,c,null);} private <T>T get(String path,TypeReference<T> t){return request("GET",path,null,null,t);}
 private <T>T post(String path,Object b,Class<T> c){return request("POST",path,b,c,null);} private <T>T put(String path,Object b,Class<T> c){return request("PUT",path,b,c,null);}
 private void postNoBody(String path){request("POST",path,null,OperationResponse.class,null);} private void delete(String path){request("DELETE",path,null,OperationResponse.class,null);}
 private <T>T request(String method,String path,Object body,Class<T> cls,TypeReference<T> type){try{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15)).header("Accept","application/json"); if(body!=null){b.header("Content-Type","application/json");String payload=json.writeValueAsString(body);b.method(method,HttpRequest.BodyPublishers.ofString(payload));}else b.method(method,HttpRequest.BodyPublishers.noBody()); HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString()); if(r.statusCode()<200||r.statusCode()>=300)throw new IllegalStateException("Master API error ("+r.statusCode()+"): "+r.body()); return type!=null?json.readValue(r.body(),type):json.readValue(r.body(),cls);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Master API request interrupted",e);}catch(IOException|IllegalArgumentException e){throw new IllegalStateException("Cannot reach master-data server at "+base,e);}}
 private String enc(String v){return URLEncoder.encode(v==null?"":v,StandardCharsets.UTF_8);} private String encPath(String v){return enc(v).replace("+","%20");}
 public record PartyDto(Integer id,String partyType,String partyCode,String name,String contactPerson,String phone,String email,String gstin,String address,double openingBalance,boolean active){}
 public record ItemDto(Integer id,String itemCode,String description,String category,String brand,String material,String size,String unit,String hsn,double gst,double discountPercent,double purchasePrice,double sellingPrice,double openingStock,double minimumStock,double reservedStock,String location,String remarks,boolean active){}
 public record LookupDto(Integer id,String lookupType,String lookupCode,String lookupValue,String description,int displayOrder,boolean active){}
 public record CategoryDto(Integer id,String categoryCode,String categoryName,String description,int displayOrder,boolean active,long valueCount){}
 public record RenameCategoryRequest(String oldName,String newName){}
 public record CategoryUpsertRequest(String code,String name,String description){}
 public record NextCodeResponse(String code){} public record ExistsResponse(boolean exists){} public record ValuesResponse(List<String> values){} public record OperationResponse(boolean success,String message){}
}
