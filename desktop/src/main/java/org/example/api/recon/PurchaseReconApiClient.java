package org.example.api.recon;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.ConfigManager;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class PurchaseReconApiClient {
    private final HttpClient http=org.example.api.ApiRuntime.HTTP;private final ObjectMapper json=org.example.api.ApiRuntime.JSON;
    public PurchaseReconApiClient(){}
    public List<SupplierDto> suppliers(){return searchSuppliers("",40);} public List<SupplierDto> searchSuppliers(String q,int limit){return get("/api/purchase-recon/suppliers?q="+enc(q)+"&limit="+Math.max(10,Math.min(limit,100)),new TypeReference<List<SupplierDto>>(){});} public SupplierDto supplier(int id){return get("/api/purchase-recon/suppliers/"+id,SupplierDto.class);} public void deleteSupplier(int id){request("DELETE","/api/purchase-recon/suppliers/"+id,null,Void.class,null);} public SupplierDto saveSupplier(SupplierSaveRequest r){return r.id()==null?post("/api/purchase-recon/suppliers",r,SupplierDto.class):put("/api/purchase-recon/suppliers/"+r.id(),r,SupplierDto.class);}
    public List<ReconDto> records(){return get("/api/purchase-recon/records",new TypeReference<List<ReconDto>>(){});} public Page page(int page,int size,String q,String status){return get("/api/purchase-recon/records/page?page="+Math.max(0,page)+"&size="+Math.max(10,Math.min(size,200))+"&q="+enc(q)+"&status="+enc(status),Page.class);} public ReconDto record(int id){return get("/api/purchase-recon/records/"+id,ReconDto.class);} public void deleteRecord(int id){request("DELETE","/api/purchase-recon/records/"+id,null,Void.class,null);} public ReconDto saveRecord(ReconSaveRequest r){return r.id()==null?post("/api/purchase-recon/records",r,ReconDto.class):put("/api/purchase-recon/records/"+r.id(),r,ReconDto.class);} public List<BankLinkDto> bankLinks(int id){return get("/api/purchase-recon/records/"+id+"/bank-links",new TypeReference<List<BankLinkDto>>(){});} public Metrics metrics(){return get("/api/purchase-recon/metrics",Metrics.class);} public ImportResult importRows(ImportRequest r){return post("/api/purchase-recon/imports",r,ImportResult.class);}
    private <T>T get(String p,Class<T>c){return request("GET",p,null,c,null);}private <T>T get(String p,TypeReference<T>t){return request("GET",p,null,null,t);}private <T>T post(String p,Object body,Class<T>c){return request("POST",p,body,c,null);}private <T>T put(String p,Object body,Class<T>c){return request("PUT",p,body,c,null);}
    private <T>T request(String method,String path,Object body,Class<T>clazz,TypeReference<T>type){try{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base()+path)).timeout(Duration.ofSeconds(45)).header("Accept","application/json");org.example.api.ApiSession.authorize(b);if(body!=null)b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body),StandardCharsets.UTF_8));else b.method(method,HttpRequest.BodyPublishers.noBody());var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));if(r.statusCode()==401){throw org.example.api.ApiSession.rejected("Purchase Recon request",r.body());}if(r.statusCode()/100!=2)throw new IllegalStateException(apiError(r.statusCode(),r.body()));if(clazz==Void.class)return null;return type!=null?json.readValue(r.body(),type):json.readValue(r.body(),clazz);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}catch(IOException e){throw new IllegalStateException("Cannot reach Purchase Recon API at "+base(),e);}}
    private String base(){String b=ConfigManager.getDataApiBaseUrl();while(b.endsWith("/"))b=b.substring(0,b.length()-1);return b;}
    private String enc(String value){return URLEncoder.encode(value==null?"":value,StandardCharsets.UTF_8);}
    private String apiError(int status,String body){try{JsonNode n=json.readTree(body);String m=n.path("message").asText("");if(!m.isBlank())return m;}catch(Exception ignored){}return "Purchase Recon API error ("+status+")";}
    public record SupplierDto(Integer id,String reference,String legalName,String gstin,String pan,String contactPerson,String phone,String email,String notes,String status,String source,long reconCount,String createdAt,String updatedAt,long rowVersion){@Override public String toString(){return legalName+(reference==null||reference.isBlank()?"":" • "+reference);}}
    public record SupplierSaveRequest(Integer id,String legalName,String gstin,String pan,String contactPerson,String phone,String email,String notes,String status,long rowVersion){}
    public record ReconDto(Integer id,String reference,Integer supplierId,String supplierReference,String supplierName,String supplierGstin,String supplierInvoiceNo,String invoiceDate,String financialYear,double taxableValue,double cgst,double sgst,double igst,double otherAdjustment,double invoiceValue,double linkedAmount,double balance,double taxDifference,boolean taxReviewRequired,String status,String source,Long importBatchId,Integer sourceRow,String notes,String createdAt,String updatedAt,List<BankLinkDto> bankLinks,long rowVersion){}
    public record ReconSaveRequest(Integer id,Integer supplierId,String supplierInvoiceNo,String invoiceDate,double taxableValue,double cgst,double sgst,double igst,double otherAdjustment,double invoiceValue,String notes,long rowVersion){}
    public record BankLinkDto(Long allocationId,Long statementTransactionId,String bankTransactionDate,String bankReference,double allocatedAmount,Integer financeEntryId,String financeVoucherNo,String createdAt){}
    public record ImportRow(Integer sourceRow,String supplierName,String supplierGstin,String supplierInvoiceNo,String invoiceDate,double taxableValue,double cgst,double sgst,double igst,double invoiceValue){}
    public record ImportRequest(String sourceFileName,String sourceFingerprint,String importNote,boolean dryRun,List<ImportRow> rows){}
    public record ImportRowResult(Integer sourceRow,String status,String supplierName,String supplierReference,String invoiceNo,String message,boolean warning){}
    public record ImportResult(int totalRows,int importedRows,int newSuppliers,int existingSuppliers,int duplicateRows,int warningRows,int ignoredRows,List<ImportRowResult> details){}
    public record Metrics(long total,long open,long partial,long reconciled,long review,double invoiceValue,double linkedValue,double outstandingValue){}
    public record Page(List<ReconDto> rows,int page,int size,long totalRows,int totalPages,Metrics metrics){}
}
