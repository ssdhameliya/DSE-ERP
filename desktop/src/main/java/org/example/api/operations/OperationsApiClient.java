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

    public void saveSale(Sales s){ applySaleIdentity(s, post("/api/operations/sales", saleDto(s), SaleDto.class)); }
    public void updateSale(Sales s){ applySaleIdentity(s, put("/api/operations/sales", saleDto(s), SaleDto.class)); }
    public List<Sales> sales(){ return get("/api/operations/sales", new TypeReference<List<SaleDto>>(){}).stream().map(this::sale).toList(); }
    public SalesPage salesPage(int page,int size,String q,String invoice,String customer,LocalDate from,LocalDate to,String paymentStatus,String due,String mail,String whatsapp,String invoiceType,String documentStatus,Double minAmount,Double maxAmount){
        String path="/api/operations/sales/page?page="+Math.max(0,page)+"&size="+Math.max(10,Math.min(size,200))+"&q="+enc(q)+"&invoice="+enc(invoice)+"&customer="+enc(customer)+"&from="+enc(str(from))+"&to="+enc(str(to))+"&paymentStatus="+enc(paymentStatus)+"&due="+enc(due)+"&mail="+enc(mail)+"&whatsapp="+enc(whatsapp)+"&invoiceType="+enc(invoiceType)+"&documentStatus="+enc(documentStatus);
        if(minAmount!=null)path+="&minAmount="+URLEncoder.encode(String.valueOf(minAmount),StandardCharsets.UTF_8);if(maxAmount!=null)path+="&maxAmount="+URLEncoder.encode(String.valueOf(maxAmount),StandardCharsets.UTF_8);
        SalePageDto d=get(path,SalePageDto.class);return new SalesPage(d.rows()==null?List.of():d.rows().stream().map(this::sale).toList(),d.page(),d.size(),d.totalRows(),d.totalPages(),d.filteredTotals(),d.metrics(),d.customers()==null?List.of():d.customers());
    }
    public Sales sale(String invoice){ return sale(get("/api/operations/sales/by-invoice?invoiceNo="+enc(invoice), SaleDto.class)); }
    public boolean saleExists(String invoice){ return get("/api/operations/sales/exists?invoiceNo="+enc(invoice), ExistsResponse.class).exists(); }
    public void deleteSale(String invoice){ delete("/api/operations/sales?invoiceNo="+enc(invoice)); }
    public void cancelSale(String invoice){ postNoBody("/api/operations/sales/cancel?invoiceNo="+enc(invoice)); }
    public void approveSale(String invoice){ postNoBody("/api/operations/sales/approve?invoiceNo="+enc(invoice)); }
    public void rejectSale(String invoice,String reason){ postNoBody("/api/operations/sales/reject?invoiceNo="+enc(invoice)+(reason==null||reason.isBlank()?"":"&reason="+enc(reason))); }
    public void markSaleEmail(int id){ postNoBody("/api/operations/sales/email-sent/"+id); }
    public String nextSaleInvoice(){ return get("/api/operations/sales/next-invoice", NextNumber.class).value(); }

    public void savePurchase(Purchase p){ applyPurchaseIdentity(p, post("/api/operations/purchases", purchaseDto(p), PurchaseDto.class)); }
    public void updatePurchase(Purchase p){ applyPurchaseIdentity(p, put("/api/operations/purchases", purchaseDto(p), PurchaseDto.class)); }
    public List<Purchase> purchases(){ return get("/api/operations/purchases", new TypeReference<List<PurchaseDto>>(){}).stream().map(this::purchase).toList(); }
    public PurchasePage purchasesPage(int page,int size,String q,String supplier,LocalDate from,LocalDate to,String paymentStatus,String due,String mail,String documentStatus){PurchasePageDto d=get("/api/operations/purchases/page?page="+Math.max(0,page)+"&size="+Math.max(10,Math.min(size,200))+"&q="+enc(q)+"&supplier="+enc(supplier)+"&from="+enc(str(from))+"&to="+enc(str(to))+"&paymentStatus="+enc(paymentStatus)+"&due="+enc(due)+"&mail="+enc(mail)+"&documentStatus="+enc(documentStatus),PurchasePageDto.class);return new PurchasePage(d.rows()==null?List.of():d.rows().stream().map(this::purchase).toList(),d.page(),d.size(),d.totalRows(),d.totalPages(),d.filteredTotals(),d.metrics(),d.suppliers()==null?List.of():d.suppliers());}
    public Purchase purchase(String invoice){ return purchase(get("/api/operations/purchases/by-invoice?invoiceNo="+enc(invoice), PurchaseDto.class)); }
    public boolean purchaseExists(String invoice){ return get("/api/operations/purchases/exists?invoiceNo="+enc(invoice), ExistsResponse.class).exists(); }
    public void deletePurchase(String invoice){ delete("/api/operations/purchases?invoiceNo="+enc(invoice)); }
    public void cancelPurchase(String invoice){ postNoBody("/api/operations/purchases/cancel?invoiceNo="+enc(invoice)); }
    public void approvePurchase(String invoice){ postNoBody("/api/operations/purchases/approve?invoiceNo="+enc(invoice)); }
    public void rejectPurchase(String invoice,String reason){ postNoBody("/api/operations/purchases/reject?invoiceNo="+enc(invoice)+(reason==null||reason.isBlank()?"":"&reason="+enc(reason))); }
    public void markPurchaseEmail(int id){ postNoBody("/api/operations/purchases/email-sent/"+id); }
    public String nextPurchaseInvoice(){ return get("/api/operations/purchases/next-invoice", NextNumber.class).value(); }

    public List<FinanceEntry> finance(){ return get("/api/operations/finance", new TypeReference<List<FinanceEntry>>(){}); }
    public FinancePage financePage(int page,int size,String mode,String period,String type,String q,LocalDate from,LocalDate to){return get("/api/operations/finance/page?page="+Math.max(0,page)+"&size="+Math.max(10,Math.min(size,200))+"&mode="+enc(mode)+"&period="+enc(period)+"&type="+enc(type)+"&q="+enc(q)+"&from="+enc(str(from))+"&to="+enc(str(to)),FinancePage.class);}
    public FinanceEntry finance(int id){return get("/api/operations/finance/"+id,FinanceEntry.class);}
    public FinanceEntry saveFinance(FinanceEntry e){ return post("/api/operations/finance", e, FinanceEntry.class); }
    public FinanceEntry updateFinance(FinanceEntry e){ return put("/api/operations/finance", e, FinanceEntry.class); }
    public void deleteFinance(int id,long rowVersion){ delete("/api/operations/finance/"+id+"?rowVersion="+Math.max(0,rowVersion)); }
    public String nextVoucher(){ return get("/api/operations/finance/next-voucher", NextNumber.class).value(); }
    public FinanceMetrics financeMetrics(){ return get("/api/operations/finance/metrics", FinanceMetrics.class); }
    public List<StockHistoryEntry> stockHistory(String itemCode){ return get("/api/operations/stock/history?itemCode="+enc(itemCode), new TypeReference<List<StockHistoryEntry>>(){}); }
    public void adjustStock(StockAdjustmentRequest request){ post("/api/operations/stock/adjust", request, OperationResponse.class); }

    private void applySaleIdentity(Sales target,SaleDto saved){if(target==null||saved==null)return;target.setId(n(saved.id));target.setInvoiceNo(saved.invoiceNo);target.setRowVersion(saved.rowVersion);}
    private void applyPurchaseIdentity(Purchase target,PurchaseDto saved){if(target==null||saved==null)return;target.setId(n(saved.id));target.setInvoiceNo(saved.invoiceNo);target.setRowVersion(saved.rowVersion);}

    private SaleDto saleDto(Sales s){
        Party c=s.getCustomer(); PartyDto p=c==null?null:new PartyDto(c.getId(),c.getPartyCode(),c.getName(),c.getEmail(),c.getPhone(),c.getGstin(),c.getAddress());
        List<LineDto> lines=s.getLines()==null?List.of():s.getLines().stream().map(x->new LineDto(x.getItemCode(),x.getItemDescription(),x.getItemHsn(),x.getItemUnit(),x.getItemRemarks(),x.getQuantity(),x.getRate(),x.getDiscountPercent(),x.getDiscountAmount(),x.getGstPercent(),x.getTotalAmount())).toList();
        List<ChargeDto> charges=s.getCharges().stream().map(x->new ChargeDto(x.getChargeType(),x.getAmount(),x.isTaxable(),x.getGstPercent())).toList();
        return new SaleDto(s.getId(),s.getInvoiceNo(),str(s.getInvoiceDate()),p,s.getSubtotal(),s.getDiscountAmount(),s.getGstAmount(),s.getTotalAmount(),s.getRemarks(),s.getCreatedAt(),s.isEmailSent(),str(s.getDueDate()),s.getPaidAmount(),s.getPaymentStatus(),s.isWhatsappSent(),s.getInvoiceType(),s.getSalesperson(),s.getSource(),s.getNotes(),s.getDeliveryAddress(),s.getPaymentTerms(),s.getTransporter(),s.getReferenceNo(),str(s.getPoDate()),s.getBillingAddress(),s.getGstType(),s.getDoorDelivery(),s.getVehicleNumber(),s.getContactPerson(),s.getTransportNote(),s.getOrderNo(),s.getGstin(),s.getBillingGstin(),s.getDeliveryGstin(),s.isSameAsBilling(),s.getTransporterGstin(),s.getChargeType(),s.getChargeAmount(),s.getContactPersonMobile(),s.getDocumentStatus(),s.getAttachmentPath(),s.getQuantity(),charges,lines,s.getRowVersion());
    }
    private Sales sale(SaleDto d){
        Sales s=new Sales();s.setId(n(d.id));s.setInvoiceNo(d.invoiceNo);s.setInvoiceDate(date(d.invoiceDate));s.setCustomer(party(d.customer));s.setSubtotal(d.subtotal);s.setDiscountAmount(d.discountAmount);s.setGstAmount(d.gstAmount);s.setTotalAmount(d.totalAmount);s.setRemarks(d.remarks);s.setCreatedAt(d.createdAt);s.setEmailSent(d.emailSent);s.setDueDate(date(d.dueDate));s.setPaidAmount(d.paidAmount);s.setPaymentStatus(d.paymentStatus);s.setWhatsappSent(d.whatsappSent);s.setInvoiceType(d.invoiceType);s.setSalesperson(d.salesperson);s.setSource(d.source);s.setNotes(d.notes);s.setDeliveryAddress(d.deliveryAddress);s.setPaymentTerms(d.paymentTerms);s.setTransporter(d.transporter);s.setReferenceNo(d.referenceNo);s.setPoDate(date(d.poDate));s.setBillingAddress(d.billingAddress);s.setGstType(d.gstType);s.setDoorDelivery(d.doorDelivery);s.setVehicleNumber(d.vehicleNumber);s.setContactPerson(d.contactPerson);s.setTransportNote(d.transportNote);s.setOrderNo(d.orderNo);s.setGstin(d.gstin);s.setBillingGstin(d.billingGstin);s.setDeliveryGstin(d.deliveryGstin);s.setSameAsBilling(d.sameAsBilling);s.setTransporterGstin(d.transporterGstin);s.setChargeType(d.chargeType);s.setChargeAmount(d.chargeAmount);s.setCharges(d.charges==null?List.of():d.charges.stream().map(x->new SalesCharge(x.chargeType,x.amount,x.taxable,x.gstPercent)).toList());s.setContactPersonMobile(d.contactPersonMobile);s.setDocumentStatus(d.documentStatus);s.setAttachmentPath(d.attachmentPath);s.setQuantity(d.quantity);s.setRowVersion(d.rowVersion);s.setLines(d.lines==null?new ArrayList<>():d.lines.stream().map(this::salesLine).toList());return s;
    }
    private PurchaseDto purchaseDto(Purchase p){
        Party c=p.getSupplier(); PartyDto party=c==null?null:new PartyDto(c.getId(),c.getPartyCode(),c.getName(),c.getEmail(),c.getPhone(),c.getGstin(),c.getAddress());
        List<LineDto> lines=p.getLines()==null?List.of():p.getLines().stream().map(x->new LineDto(x.getItemCode(),x.getItemDescription(),x.getItemHsn(),x.getItemUnit(),x.getItemRemarks(),x.getQuantity(),x.getRate(),x.getDiscountPercent(),x.getDiscountAmount(),x.getGstPercent(),x.getTotalAmount())).toList();
        List<ChargeDto> charges=p.getCharges()==null?List.of():p.getCharges().stream().map(x->new ChargeDto(x.getChargeType(),x.getAmount(),x.isTaxable(),x.getGstPercent())).toList();
        return new PurchaseDto(p.getId(),p.getInvoiceNo(),str(p.getInvoiceDate()),party,p.getSubtotal(),p.getGstAmount(),p.getTotalAmount(),p.getRemarks(),p.getCreatedAt(),p.isEmailSent(),str(p.getDueDate()),p.getPaidAmount(),p.getPaymentStatus(),p.getDocumentStatus(),p.getWarehouse(),p.getPaymentTerms(),p.getCurrency(),p.getReferenceNo(),p.getGstTreatment(),p.getTransporter(),p.getLrAwbNo(),p.getDiscountType(),p.getDiscountAmount(),p.getAttachmentPath(),p.getCreatedBy(),str(p.getDeliveryDate()),p.getBillingAddress(),p.getDeliveryAddress(),p.getBillingGstin(),p.getDeliveryGstin(),p.getGstType(),p.getTransporterGstin(),p.getVehicleNumber(),p.getContactPerson(),p.getContactPersonMobile(),p.getNotes(),p.getOrderNo(),str(p.getPoDate()),p.isSameAsBilling(),p.getQuantity(),charges,lines,p.getRowVersion());
    }
    private Purchase purchase(PurchaseDto d){
        Purchase p=new Purchase();p.setId(n(d.id));p.setInvoiceNo(d.invoiceNo);p.setInvoiceDate(date(d.invoiceDate));p.setSupplier(party(d.supplier));p.setSubtotal(d.subtotal);p.setGstAmount(d.gstAmount);p.setTotalAmount(d.totalAmount);p.setRemarks(d.remarks);p.setCreatedAt(d.createdAt);p.setEmailSent(d.emailSent);p.setDueDate(date(d.dueDate));p.setPaidAmount(d.paidAmount);p.setPaymentStatus(d.paymentStatus);p.setDocumentStatus(d.documentStatus);p.setWarehouse(d.warehouse);p.setPaymentTerms(d.paymentTerms);p.setCurrency(d.currency);p.setReferenceNo(d.referenceNo);p.setGstTreatment(d.gstTreatment);p.setTransporter(d.transporter);p.setLrAwbNo(d.lrAwbNo);p.setDiscountType(d.discountType);p.setDiscountAmount(d.discountAmount);p.setAttachmentPath(d.attachmentPath);p.setCreatedBy(d.createdBy);p.setDeliveryDate(date(d.deliveryDate));p.setBillingAddress(d.billingAddress);p.setDeliveryAddress(d.deliveryAddress);p.setBillingGstin(d.billingGstin);p.setDeliveryGstin(d.deliveryGstin);p.setGstType(d.gstType);p.setTransporterGstin(d.transporterGstin);p.setVehicleNumber(d.vehicleNumber);p.setContactPerson(d.contactPerson);p.setContactPersonMobile(d.contactPersonMobile);p.setNotes(d.notes);p.setOrderNo(d.orderNo);p.setPoDate(date(d.poDate));p.setSameAsBilling(d.sameAsBilling);p.setQuantity(d.quantity);p.setRowVersion(d.rowVersion);p.setCharges(d.charges==null?List.of():d.charges.stream().map(x->new PurchaseCharge(x.chargeType,x.amount,x.taxable,x.gstPercent)).toList());p.setLines(d.lines==null?new ArrayList<>():d.lines.stream().map(this::purchaseLine).toList());return p;
    }
    private Party party(PartyDto d){if(d==null)return null;Party p=new Party();p.setId(n(d.id));p.setPartyCode(d.partyCode);p.setName(d.name);p.setEmail(d.email);p.setPhone(d.phone);p.setGstin(d.gstin);p.setAddress(d.address);return p;}
    private SalesLine salesLine(LineDto d){SalesLine x=new SalesLine();x.setItemCode(d.itemCode);x.setItemDescription(d.itemDescription);x.setItemHsn(d.itemHsn);x.setItemUnit(d.itemUnit);x.setItemRemarks(d.itemRemarks);x.setQuantity(d.quantity);x.setRate(d.rate);x.setDiscountPercent(d.discountPercent);x.setGstPercent(d.gstPercent);x.recalculate();return x;}
    private PurchaseLine purchaseLine(LineDto d){PurchaseLine x=new PurchaseLine();x.setItemCode(d.itemCode);x.setItemDescription(d.itemDescription);x.setItemHsn(d.itemHsn);x.setItemUnit(d.itemUnit);x.setItemRemarks(d.itemRemarks);x.setQuantity(d.quantity);x.setRate(d.rate);x.setDiscountPercent(d.discountPercent);x.setGstPercent(d.gstPercent);x.calculateAmounts();return x;}

    private <T>T get(String path,Class<T> c){return request("GET",path,null,c,null);} private <T>T get(String path,TypeReference<T> t){return request("GET",path,null,null,t);}
    private <T>T post(String path,Object b,Class<T> c){return request("POST",path,b,c,null);} private <T>T put(String path,Object b,Class<T> c){return request("PUT",path,b,c,null);}
    private void postNoBody(String path){request("POST",path,null,OperationResponse.class,null);} private void delete(String path){request("DELETE",path,null,OperationResponse.class,null);}
    private <T>T request(String method,String path,Object body,Class<T> cls,TypeReference<T> type){try{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(25)).header("Accept","application/json");org.example.api.ApiSession.authorize(b);if(body!=null){b.header("Content-Type","application/json");b.method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));}else b.method(method,HttpRequest.BodyPublishers.noBody());HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()<200||r.statusCode()>=300){org.example.api.ApiRuntime.logHttpFailure("Operations request",r.statusCode(),r.body());throw new IllegalStateException(org.example.api.ApiRuntime.userMessage("the requested operation",r.statusCode(),r.body()));}return type!=null?json.readValue(r.body(),type):json.readValue(r.body(),cls);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Operations API request interrupted",e);}catch(IOException|IllegalArgumentException e){throw new IllegalStateException("Cannot reach operations server at "+base,e);}}
    private String apiMessage(String body,String fallback){try{var n=json.readTree(body);String m=n.path("message").asText("");return m.isBlank()?fallback:m;}catch(Exception ignored){return fallback;}}
    private String enc(String v){return URLEncoder.encode(v==null?"":v, StandardCharsets.UTF_8);} private static String str(LocalDate d){return d==null?null:d.toString();} private static LocalDate date(String s){if(s==null||s.isBlank())return null;return LocalDate.parse(s.length()>=10?s.substring(0,10):s);} private static int n(Integer i){return i==null?0:i;}

    public record PartyDto(Integer id,String partyCode,String name,String email,String phone,String gstin,String address){}
    public record LineDto(String itemCode,String itemDescription,String itemHsn,String itemUnit,String itemRemarks,double quantity,double rate,double discountPercent,double discountAmount,double gstPercent,double totalAmount){}
    public record ChargeDto(String chargeType,double amount,boolean taxable,double gstPercent){}
    public record SaleDto(Integer id,String invoiceNo,String invoiceDate,PartyDto customer,double subtotal,double discountAmount,double gstAmount,double totalAmount,String remarks,String createdAt,boolean emailSent,String dueDate,double paidAmount,String paymentStatus,boolean whatsappSent,String invoiceType,String salesperson,String source,String notes,String deliveryAddress,String paymentTerms,String transporter,String referenceNo,String poDate,String billingAddress,String gstType,String doorDelivery,String vehicleNumber,String contactPerson,String transportNote,String orderNo,String gstin,String billingGstin,String deliveryGstin,boolean sameAsBilling,String transporterGstin,String chargeType,double chargeAmount,String contactPersonMobile,String documentStatus,String attachmentPath,double quantity,List<ChargeDto> charges,List<LineDto> lines,long rowVersion){}
    public record PurchaseDto(Integer id,String invoiceNo,String invoiceDate,PartyDto supplier,double subtotal,double gstAmount,double totalAmount,String remarks,String createdAt,boolean emailSent,String dueDate,double paidAmount,String paymentStatus,String documentStatus,String warehouse,String paymentTerms,String currency,String referenceNo,String gstTreatment,String transporter,String lrAwbNo,String discountType,double discountAmount,String attachmentPath,String createdBy,String deliveryDate,String billingAddress,String deliveryAddress,String billingGstin,String deliveryGstin,String gstType,String transporterGstin,String vehicleNumber,String contactPerson,String contactPersonMobile,String notes,String orderNo,String poDate,boolean sameAsBilling,double quantity,List<ChargeDto> charges,List<LineDto> lines,long rowVersion){}
    public record RegisterTotals(long rows,double total,double paid,double balance){}
    public record MetricPoint(String label,double value){}
    public record SalesMetrics(double totalSales,long invoiceCount,double todaySales,long todayCount,double pendingBalance,long pendingCount,double overdueBalance,long overdueCount,double dueSoonBalance,long dueSoonCount,double emailRate,List<MetricPoint> dueBuckets,List<MetricPoint> topCustomers,List<MetricPoint> monthlySales){}
    public record PurchaseMetrics(double totalPurchases,long activeDocuments,long suppliers,double itemQuantity,double paidAmount){}
    private record SalePageDto(List<SaleDto> rows,int page,int size,long totalRows,int totalPages,RegisterTotals filteredTotals,SalesMetrics metrics,List<String> customers){}
    private record PurchasePageDto(List<PurchaseDto> rows,int page,int size,long totalRows,int totalPages,RegisterTotals filteredTotals,PurchaseMetrics metrics,List<String> suppliers){}
    public record SalesPage(List<Sales> rows,int page,int size,long totalRows,int totalPages,RegisterTotals filteredTotals,SalesMetrics metrics,List<String> customers){}
    public record PurchasePage(List<Purchase> rows,int page,int size,long totalRows,int totalPages,RegisterTotals filteredTotals,PurchaseMetrics metrics,List<String> suppliers){}
    public record FinancePage(List<FinanceEntry> rows,int page,int size,long totalRows,int totalPages){}
    public record FinanceEntry(Integer id,String voucherNo,String voucherType,String voucherDate,Integer partyId,String category,String referenceNo,double amount,String paymentMode,String notes,String accountName,String billPath,boolean reconciled,Long statementTransactionId,String linkedTargetType,Integer linkedTargetId,String linkedDocumentNo,long rowVersion){
        public FinanceEntry(Integer id,String voucherNo,String voucherType,String voucherDate,Integer partyId,String category,String referenceNo,double amount,String paymentMode,String notes,String accountName,String billPath,boolean reconciled){this(id,voucherNo,voucherType,voucherDate,partyId,category,referenceNo,amount,paymentMode,notes,accountName,billPath,reconciled,null,null,null,null,0L);}
        public FinanceEntry(Integer id,String voucherNo,String voucherType,String voucherDate,Integer partyId,String category,String referenceNo,double amount,String paymentMode,String notes,String accountName,String billPath,boolean reconciled,long rowVersion){this(id,voucherNo,voucherType,voucherDate,partyId,category,referenceNo,amount,paymentMode,notes,accountName,billPath,reconciled,null,null,null,null,rowVersion);}
        public FinanceEntry(Integer id,String voucherNo,String voucherType,String voucherDate,String category,String referenceNo,double amount,String paymentMode,String notes,String accountName,String billPath,boolean reconciled){this(id,voucherNo,voucherType,voucherDate,null,category,referenceNo,amount,paymentMode,notes,accountName,billPath,reconciled);}
        public FinanceEntry(Integer id,String voucherNo,String voucherType,String voucherDate,String category,String referenceNo,double amount,String paymentMode,String notes,String accountName,String billPath,boolean reconciled,long rowVersion){this(id,voucherNo,voucherType,voucherDate,null,category,referenceNo,amount,paymentMode,notes,accountName,billPath,reconciled,rowVersion);}
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
