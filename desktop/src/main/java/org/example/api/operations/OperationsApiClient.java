package org.example.api.operations;

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
import java.time.LocalDate;
import java.util.*;

/** Phase-3 REST client for sales, purchase and bank/expense operations. */
public final class OperationsApiClient {
    private final HttpClient http = org.example.api.ApiRuntime.HTTP;
    private final ObjectMapper json = org.example.api.ApiRuntime.JSON;
    private final String base;

    public OperationsApiClient() {
        String b = ConfigManager.getDataApiBaseUrl();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        base = b;
    }

    public void saveSale(Sales s){ post("/api/operations/sales", saleDto(s), SaleDto.class); }
    public void updateSale(Sales s){ put("/api/operations/sales", saleDto(s), SaleDto.class); }
    public List<Sales> sales(){ return get("/api/operations/sales", new TypeReference<List<SaleDto>>(){}).stream().map(this::sale).toList(); }
    public Sales sale(String invoice){ return sale(get("/api/operations/sales/by-invoice?invoiceNo="+enc(invoice), SaleDto.class)); }
    public boolean saleExists(String invoice){ return get("/api/operations/sales/exists?invoiceNo="+enc(invoice), ExistsResponse.class).exists(); }
    public void deleteSale(String invoice){ delete("/api/operations/sales?invoiceNo="+enc(invoice)); }
    public void cancelSale(String invoice){ postNoBody("/api/operations/sales/cancel?invoiceNo="+enc(invoice)); }
    public void markSaleEmail(int id){ postNoBody("/api/operations/sales/email-sent/"+id); }
    public String nextSaleInvoice(){ return get("/api/operations/sales/next-invoice", NextNumber.class).value(); }

    public void savePurchase(Purchase p){ post("/api/operations/purchases", purchaseDto(p), PurchaseDto.class); }
    public void updatePurchase(Purchase p){ put("/api/operations/purchases", purchaseDto(p), PurchaseDto.class); }
    public List<Purchase> purchases(){ return get("/api/operations/purchases", new TypeReference<List<PurchaseDto>>(){}).stream().map(this::purchase).toList(); }
    public Purchase purchase(String invoice){ return purchase(get("/api/operations/purchases/by-invoice?invoiceNo="+enc(invoice), PurchaseDto.class)); }
    public boolean purchaseExists(String invoice){ return get("/api/operations/purchases/exists?invoiceNo="+enc(invoice), ExistsResponse.class).exists(); }
    public void deletePurchase(String invoice){ delete("/api/operations/purchases?invoiceNo="+enc(invoice)); }
    public void markPurchaseEmail(int id){ postNoBody("/api/operations/purchases/email-sent/"+id); }
    public String nextPurchaseInvoice(){ return get("/api/operations/purchases/next-invoice", NextNumber.class).value(); }

    public List<FinanceEntry> finance(){ return get("/api/operations/finance", new TypeReference<List<FinanceEntry>>(){}); }
    public FinanceEntry saveFinance(FinanceEntry e){ return post("/api/operations/finance", e, FinanceEntry.class); }
    public FinanceEntry updateFinance(FinanceEntry e){ return put("/api/operations/finance", e, FinanceEntry.class); }
    public void deleteFinance(int id){ delete("/api/operations/finance/"+id); }
    public String nextVoucher(){ return get("/api/operations/finance/next-voucher", NextNumber.class).value(); }
    public FinanceMetrics financeMetrics(){ return get("/api/operations/finance/metrics", FinanceMetrics.class); }
    public List<StockHistoryEntry> stockHistory(String itemCode){ return get("/api/operations/stock/history?itemCode="+enc(itemCode), new TypeReference<List<StockHistoryEntry>>(){}); }
    public void adjustStock(StockAdjustmentRequest request){ post("/api/operations/stock/adjust", request, OperationResponse.class); }

    private SaleDto saleDto(Sales s){
        Party c=s.getCustomer(); PartyDto p=c==null?null:new PartyDto(c.getId(),c.getPartyCode(),c.getName(),c.getEmail(),c.getPhone(),c.getGstin(),c.getAddress());
        List<LineDto> lines=s.getLines()==null?List.of():s.getLines().stream().map(x->new LineDto(x.getItemCode(),x.getItemDescription(),x.getQuantity(),x.getRate(),x.getDiscountPercent(),x.getDiscountAmount(),x.getGstPercent(),x.getTotalAmount())).toList();
        List<ChargeDto> charges=s.getCharges().stream().map(x->new ChargeDto(x.getChargeType(),x.getAmount(),x.isTaxable(),x.getGstPercent())).toList();
        return new SaleDto(s.getId(),s.getInvoiceNo(),str(s.getInvoiceDate()),p,s.getSubtotal(),s.getDiscountAmount(),s.getGstAmount(),s.getTotalAmount(),s.getRemarks(),s.getCreatedAt(),s.isEmailSent(),str(s.getDueDate()),s.getPaidAmount(),s.getPaymentStatus(),s.isWhatsappSent(),s.getInvoiceType(),s.getSalesperson(),s.getSource(),s.getNotes(),s.getDeliveryAddress(),s.getPaymentTerms(),s.getTransporter(),s.getReferenceNo(),str(s.getPoDate()),s.getBillingAddress(),s.getGstType(),s.getDoorDelivery(),s.getVehicleNumber(),s.getContactPerson(),s.getTransportNote(),s.getOrderNo(),s.getGstin(),s.getBillingGstin(),s.getDeliveryGstin(),s.isSameAsBilling(),s.getTransporterGstin(),s.getChargeType(),s.getChargeAmount(),s.getContactPersonMobile(),s.getDocumentStatus(),s.getAttachmentPath(),s.getQuantity(),charges,lines);
    }
    private Sales sale(SaleDto d){
        Sales s=new Sales();s.setId(n(d.id));s.setInvoiceNo(d.invoiceNo);s.setInvoiceDate(date(d.invoiceDate));s.setCustomer(party(d.customer));s.setSubtotal(d.subtotal);s.setDiscountAmount(d.discountAmount);s.setGstAmount(d.gstAmount);s.setTotalAmount(d.totalAmount);s.setRemarks(d.remarks);s.setCreatedAt(d.createdAt);s.setEmailSent(d.emailSent);s.setDueDate(date(d.dueDate));s.setPaidAmount(d.paidAmount);s.setPaymentStatus(d.paymentStatus);s.setWhatsappSent(d.whatsappSent);s.setInvoiceType(d.invoiceType);s.setSalesperson(d.salesperson);s.setSource(d.source);s.setNotes(d.notes);s.setDeliveryAddress(d.deliveryAddress);s.setPaymentTerms(d.paymentTerms);s.setTransporter(d.transporter);s.setReferenceNo(d.referenceNo);s.setPoDate(date(d.poDate));s.setBillingAddress(d.billingAddress);s.setGstType(d.gstType);s.setDoorDelivery(d.doorDelivery);s.setVehicleNumber(d.vehicleNumber);s.setContactPerson(d.contactPerson);s.setTransportNote(d.transportNote);s.setOrderNo(d.orderNo);s.setGstin(d.gstin);s.setBillingGstin(d.billingGstin);s.setDeliveryGstin(d.deliveryGstin);s.setSameAsBilling(d.sameAsBilling);s.setTransporterGstin(d.transporterGstin);s.setChargeType(d.chargeType);s.setChargeAmount(d.chargeAmount);s.setCharges(d.charges==null?List.of():d.charges.stream().map(x->new SalesCharge(x.chargeType,x.amount,x.taxable,x.gstPercent)).toList());s.setContactPersonMobile(d.contactPersonMobile);s.setDocumentStatus(d.documentStatus);s.setAttachmentPath(d.attachmentPath);s.setQuantity(d.quantity);s.setLines(d.lines==null?new ArrayList<>():d.lines.stream().map(this::salesLine).toList());return s;
    }
    private PurchaseDto purchaseDto(Purchase p){
        Party c=p.getSupplier(); PartyDto party=c==null?null:new PartyDto(c.getId(),c.getPartyCode(),c.getName(),c.getEmail(),c.getPhone(),c.getGstin(),c.getAddress());
        List<LineDto> lines=p.getLines()==null?List.of():p.getLines().stream().map(x->new LineDto(x.getItemCode(),x.getItemDescription(),x.getQuantity(),x.getRate(),x.getDiscountPercent(),x.getDiscountAmount(),x.getGstPercent(),x.getTotalAmount())).toList();
        return new PurchaseDto(p.getId(),p.getInvoiceNo(),str(p.getInvoiceDate()),party,p.getSubtotal(),p.getGstAmount(),p.getTotalAmount(),p.getRemarks(),p.getCreatedAt(),p.isEmailSent(),str(p.getDueDate()),p.getPaidAmount(),p.getPaymentStatus(),p.getDocumentStatus(),p.getWarehouse(),p.getPaymentTerms(),p.getCurrency(),p.getReferenceNo(),p.getGstTreatment(),p.getTransporter(),p.getLrAwbNo(),p.getDiscountType(),p.getDiscountAmount(),p.getAttachmentPath(),p.getCreatedBy(),str(p.getDeliveryDate()),p.getQuantity(),lines);
    }
    private Purchase purchase(PurchaseDto d){
        Purchase p=new Purchase();p.setId(n(d.id));p.setInvoiceNo(d.invoiceNo);p.setInvoiceDate(date(d.invoiceDate));p.setSupplier(party(d.supplier));p.setSubtotal(d.subtotal);p.setGstAmount(d.gstAmount);p.setTotalAmount(d.totalAmount);p.setRemarks(d.remarks);p.setCreatedAt(d.createdAt);p.setEmailSent(d.emailSent);p.setDueDate(date(d.dueDate));p.setPaidAmount(d.paidAmount);p.setPaymentStatus(d.paymentStatus);p.setDocumentStatus(d.documentStatus);p.setWarehouse(d.warehouse);p.setPaymentTerms(d.paymentTerms);p.setCurrency(d.currency);p.setReferenceNo(d.referenceNo);p.setGstTreatment(d.gstTreatment);p.setTransporter(d.transporter);p.setLrAwbNo(d.lrAwbNo);p.setDiscountType(d.discountType);p.setDiscountAmount(d.discountAmount);p.setAttachmentPath(d.attachmentPath);p.setCreatedBy(d.createdBy);p.setDeliveryDate(date(d.deliveryDate));p.setQuantity(d.quantity);p.setLines(d.lines==null?new ArrayList<>():d.lines.stream().map(this::purchaseLine).toList());return p;
    }
    private Party party(PartyDto d){if(d==null)return null;Party p=new Party();p.setId(n(d.id));p.setPartyCode(d.partyCode);p.setName(d.name);p.setEmail(d.email);p.setPhone(d.phone);p.setGstin(d.gstin);p.setAddress(d.address);return p;}
    private SalesLine salesLine(LineDto d){SalesLine x=new SalesLine();x.setItemCode(d.itemCode);x.setItemDescription(d.itemDescription);x.setQuantity(d.quantity);x.setRate(d.rate);x.setDiscountPercent(d.discountPercent);x.setGstPercent(d.gstPercent);x.recalculate();return x;}
    private PurchaseLine purchaseLine(LineDto d){PurchaseLine x=new PurchaseLine();x.setItemCode(d.itemCode);x.setItemDescription(d.itemDescription);x.setQuantity(d.quantity);x.setRate(d.rate);x.setDiscountPercent(d.discountPercent);x.setGstPercent(d.gstPercent);x.calculateAmounts();return x;}

    private <T>T get(String path,Class<T> c){return request("GET",path,null,c,null);} private <T>T get(String path,TypeReference<T> t){return request("GET",path,null,null,t);}
    private <T>T post(String path,Object b,Class<T> c){return request("POST",path,b,c,null);} private <T>T put(String path,Object b,Class<T> c){return request("PUT",path,b,c,null);}
    private void postNoBody(String path){request("POST",path,null,OperationResponse.class,null);} private void delete(String path){request("DELETE",path,null,OperationResponse.class,null);}
    private <T>T request(String method,String path,Object body,Class<T> cls,TypeReference<T> type){try{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(25)).header("Accept","application/json");org.example.api.ApiSession.authorize(b);if(body!=null){b.header("Content-Type","application/json");b.method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));}else b.method(method,HttpRequest.BodyPublishers.noBody());HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()<200||r.statusCode()>=300)throw new IllegalStateException("Operations API error ("+r.statusCode()+"): "+r.body());return type!=null?json.readValue(r.body(),type):json.readValue(r.body(),cls);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Operations API request interrupted",e);}catch(IOException|IllegalArgumentException e){throw new IllegalStateException("Cannot reach operations server at "+base,e);}}
    private String enc(String v){return URLEncoder.encode(v==null?"":v, StandardCharsets.UTF_8);} private static String str(LocalDate d){return d==null?null:d.toString();} private static LocalDate date(String s){if(s==null||s.isBlank())return null;return LocalDate.parse(s.length()>=10?s.substring(0,10):s);} private static int n(Integer i){return i==null?0:i;}

    public record PartyDto(Integer id,String partyCode,String name,String email,String phone,String gstin,String address){}
    public record LineDto(String itemCode,String itemDescription,double quantity,double rate,double discountPercent,double discountAmount,double gstPercent,double totalAmount){}
    public record ChargeDto(String chargeType,double amount,boolean taxable,double gstPercent){}
    public record SaleDto(Integer id,String invoiceNo,String invoiceDate,PartyDto customer,double subtotal,double discountAmount,double gstAmount,double totalAmount,String remarks,String createdAt,boolean emailSent,String dueDate,double paidAmount,String paymentStatus,boolean whatsappSent,String invoiceType,String salesperson,String source,String notes,String deliveryAddress,String paymentTerms,String transporter,String referenceNo,String poDate,String billingAddress,String gstType,String doorDelivery,String vehicleNumber,String contactPerson,String transportNote,String orderNo,String gstin,String billingGstin,String deliveryGstin,boolean sameAsBilling,String transporterGstin,String chargeType,double chargeAmount,String contactPersonMobile,String documentStatus,String attachmentPath,double quantity,List<ChargeDto> charges,List<LineDto> lines){}
    public record PurchaseDto(Integer id,String invoiceNo,String invoiceDate,PartyDto supplier,double subtotal,double gstAmount,double totalAmount,String remarks,String createdAt,boolean emailSent,String dueDate,double paidAmount,String paymentStatus,String documentStatus,String warehouse,String paymentTerms,String currency,String referenceNo,String gstTreatment,String transporter,String lrAwbNo,String discountType,double discountAmount,String attachmentPath,String createdBy,String deliveryDate,double quantity,List<LineDto> lines){}
    public record FinanceEntry(Integer id,String voucherNo,String voucherType,String voucherDate,Integer partyId,String category,String referenceNo,double amount,String paymentMode,String notes,String accountName,String billPath,boolean reconciled,Long statementTransactionId,String linkedTargetType,Integer linkedTargetId,String linkedDocumentNo){
        public FinanceEntry(Integer id,String voucherNo,String voucherType,String voucherDate,Integer partyId,String category,String referenceNo,double amount,String paymentMode,String notes,String accountName,String billPath,boolean reconciled){this(id,voucherNo,voucherType,voucherDate,partyId,category,referenceNo,amount,paymentMode,notes,accountName,billPath,reconciled,null,null,null,null);}
        public FinanceEntry(Integer id,String voucherNo,String voucherType,String voucherDate,String category,String referenceNo,double amount,String paymentMode,String notes,String accountName,String billPath,boolean reconciled){this(id,voucherNo,voucherType,voucherDate,null,category,referenceNo,amount,paymentMode,notes,accountName,billPath,reconciled);}
    }
    public record FinanceMetrics(double bankBalance,double credits,double debits,long bankEntries,long depositCount,long withdrawalCount,double expenseMonth,double expenseYear,long expenseEntries,String topExpenseCategory,double topExpenseAmount,long pendingReconcile,double pendingReconcileAmount){
        public double deposits(){return credits;}
        public double withdrawals(){return debits;}
        public double pendingAmount(){return pendingReconcileAmount;}
        public double monthExpenses(){return expenseMonth;}
        public double yearExpenses(){return expenseYear;}
        public long monthExpenseCount(){return expenseEntries;}
    }
    public record StockHistoryEntry(String date,String type,double quantity,String reason,String reference,String user){}
    public record StockAdjustmentRequest(String itemCode,String type,double quantity,String reason,String referenceNo,String createdBy){}
    public record NextNumber(String value){} public record ExistsResponse(boolean exists){} public record OperationResponse(boolean success,String message){}
}
