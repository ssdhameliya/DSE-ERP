package org.example.api.customer360;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.api.ApiRuntime;
import org.example.config.ConfigManager;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class Customer360ApiClient {
    private final java.net.http.HttpClient http=ApiRuntime.HTTP;
    private final com.fasterxml.jackson.databind.ObjectMapper json=ApiRuntime.JSON;
    public Customer360ApiClient(){}
    private static String base(){String b=ConfigManager.getDataApiBaseUrl();while(b.endsWith("/"))b=b.substring(0,b.length()-1);return b;}

    public Summary summary(int id){return get("/api/customer-360/"+id+"/summary",Summary.class);}
    public List<ContactRow> contacts(int id){return get("/api/customer-360/"+id+"/contacts",new TypeReference<List<ContactRow>>(){});}
    public ContactRow saveContact(int id,ContactSave d){return post("/api/customer-360/"+id+"/contacts",d,ContactRow.class);}
    public void deleteContact(int id,long contactId,long rowVersion){delete("/api/customer-360/"+id+"/contacts/"+contactId+"?rowVersion="+rowVersion);}
    public List<QuotationRow> quotations(int id){return get("/api/customer-360/"+id+"/quotations",new TypeReference<List<QuotationRow>>(){});}
    public List<WorkflowRow> salesOrders(int id){return get("/api/customer-360/"+id+"/sales-orders",new TypeReference<List<WorkflowRow>>(){});}
    public List<InvoiceRow> directSales(int id){return get("/api/customer-360/"+id+"/direct-sales",new TypeReference<List<InvoiceRow>>(){});}
    public List<WorkflowRow> projects(int id){return get("/api/customer-360/"+id+"/projects",new TypeReference<List<WorkflowRow>>(){});}
    public List<InvoiceRow> invoices(int id){return get("/api/customer-360/"+id+"/invoices",new TypeReference<List<InvoiceRow>>(){});}
    public List<PaymentRow> payments(int id){return get("/api/customer-360/"+id+"/payments",new TypeReference<List<PaymentRow>>(){});}
    public List<NoteRow> notes(int id){return get("/api/customer-360/"+id+"/notes",new TypeReference<List<NoteRow>>(){});}
    public NoteRow saveNote(int id,NoteSave d){return post("/api/customer-360/"+id+"/notes",d,NoteRow.class);}
    public void deleteNote(int id,long noteId,long rowVersion){delete("/api/customer-360/"+id+"/notes/"+noteId+"?rowVersion="+rowVersion);}

    private <T>T get(String path,Class<T> type){return req("GET",path,null,type,null);}
    private <T>T get(String path,TypeReference<T> type){return req("GET",path,null,null,type);}
    private <T>T post(String path,Object body,Class<T> type){return req("POST",path,body,type,null);}
    private void delete(String path){req("DELETE",path,null,Ok.class,null);}
    private <T>T req(String method,String path,Object body,Class<T> cls,TypeReference<T> ref){
        String endpoint=base();
        try{
            HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(endpoint+path)).timeout(Duration.ofSeconds(30)).header("Accept","application/json");
            org.example.api.ApiSession.authorize(b);
            if(body!=null)b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            else b.method(method,HttpRequest.BodyPublishers.noBody());
            var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
            if(r.statusCode()<200||r.statusCode()>=300){ApiRuntime.logHttpFailure("Customer 360 operation",r.statusCode(),r.body());throw new IllegalStateException(ApiRuntime.userMessage("Customer 360 operation",r.statusCode(),r.body()));}
            return ref!=null?json.readValue(r.body(),ref):json.readValue(r.body(),cls);
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Customer 360 request interrupted",e);}
        catch(Exception e){if(e instanceof IllegalStateException x)throw x;throw new IllegalStateException(ApiRuntime.transportMessage("Customer 360 request",endpoint,e),e);}
    }

    public record Customer(int id,String code,String name,String contactPerson,String phone,String email,String gstin,String address,BigDecimal openingBalance,boolean active,long rowVersion){}
    public record Summary(Customer customer,BigDecimal outstandingReceivable,BigDecimal openQuotationValue,long openQuotationCount,BigDecimal openSalesOrderValue,long openSalesOrderCount,long activeProjectCount,BigDecimal totalSales,BigDecimal lastPaymentAmount,String lastPaymentDate,List<QuotationRow> recentQuotations,List<WorkflowRow> recentSalesOrders,List<InvoiceRow> recentInvoices){}
    public record QuotationRow(int id,String no,String date,String valid,String salesperson,BigDecimal amount,String status,String followUp){}
    public record WorkflowRow(int id,String documentType,String documentNo,String documentDate,String projectNo,String parentNo,String customerPoNo,String expectedDate,BigDecimal totalAmount,String status){}
    public record InvoiceRow(int id,String invoiceNo,String invoiceDate,String salesOrderNo,String projectNo,BigDecimal totalAmount,BigDecimal paidAmount,BigDecimal outstanding,String paymentStatus,String documentStatus){}
    public record PaymentRow(int id,String paymentDate,String referenceNo,String paymentMode,BigDecimal amount,String invoiceNo,String notes){}
    public record ContactRow(long id,int partyId,String name,String designation,String department,String mobile,String email,boolean primary,String notes,long rowVersion,String createdBy,String createdAt,String updatedBy,String updatedAt){}
    public record ContactSave(Long id,String name,String designation,String department,String mobile,String email,boolean primary,String notes,long rowVersion){}
    public record NoteRow(long id,int partyId,String note,String createdBy,String createdAt,String updatedBy,String updatedAt,long rowVersion){}
    public record NoteSave(Long id,String note,long rowVersion){}
    public record Ok(boolean success,String message){}
}
