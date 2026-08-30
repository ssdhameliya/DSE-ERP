package org.example.service;

import org.example.api.operations.OperationsApiClient;
import org.example.api.returns.ReturnApiClient;
import org.example.config.ConfigManager;
import org.example.dao.PurchaseDAO;
import org.example.model.Purchase;
import java.util.*;
import java.time.LocalDate;
import org.example.util.BusinessClock;

public class PurchaseService {
    private final PurchaseDAO dao = new PurchaseDAO();
    private final OperationsApiClient api = new OperationsApiClient();
    private final ReturnApiClient returnApi = new ReturnApiClient();
    private boolean useApi(){ return ConfigManager.isApiDataEnabled(); }
    public void save(Purchase purchase){ if(useApi())api.savePurchase(purchase);else dao.save(purchase); }
    public void update(Purchase purchase){ if(useApi())api.updatePurchase(purchase);else dao.update(purchase); }
    public String nextInvoiceNo(){ return useApi()?api.nextPurchaseInvoice():dao.nextInvoiceNo(); }
    public List<Purchase> getAll(){
        if(!useApi())return dao.getAll();
        List<Purchase> rows=api.purchases();
        applyReturnSettlements(rows);
        return rows;
    }
    public OperationsApiClient.PurchasePage page(int page,int size,String q,String supplier,LocalDate from,LocalDate to,String paymentStatus,String due,String mail,String documentStatus){if(useApi()){OperationsApiClient.PurchasePage result=api.purchasesPage(page,size,q,supplier,from,to,paymentStatus,due,mail,documentStatus);applyReturnSettlements(result.rows());return result;}List<Purchase> source=new ArrayList<>(dao.getAll());List<Purchase> filtered=source.stream().filter(x->match(x,q,supplier,from,to,paymentStatus,due,mail,documentStatus)).toList();int safeSize=Math.max(10,Math.min(size,200)),pages=filtered.isEmpty()?0:(int)Math.ceil(filtered.size()/(double)safeSize),safePage=pages==0?0:Math.min(Math.max(0,page),pages-1),start=Math.min(safePage*safeSize,filtered.size()),end=Math.min(start+safeSize,filtered.size());List<Purchase> activeFiltered=filtered.stream().filter(this::active).toList();var totals=new OperationsApiClient.RegisterTotals(activeFiltered.size(),activeFiltered.stream().mapToDouble(Purchase::getTotalAmount).sum(),activeFiltered.stream().mapToDouble(Purchase::getPaidAmount).sum(),activeFiltered.stream().mapToDouble(Purchase::getBalanceAmount).sum());List<Purchase> active=activeFiltered;var metrics=new OperationsApiClient.PurchaseMetrics(active.stream().mapToDouble(Purchase::getTotalAmount).sum(),active.size(),active.stream().map(Purchase::getSupplier).filter(Objects::nonNull).map(org.example.model.Party::getId).distinct().count(),localDistinctItemCount(active),active.stream().mapToDouble(Purchase::getPaidAmount).sum());return new OperationsApiClient.PurchasePage(List.copyOf(filtered.subList(start,end)),safePage,safeSize,filtered.size(),pages,totals,metrics,source.stream().map(Purchase::getSupplier).filter(Objects::nonNull).map(org.example.model.Party::getName).filter(Objects::nonNull).distinct().sorted().toList());}
    public List<Purchase> allFiltered(String q,String supplier,LocalDate from,LocalDate to,String paymentStatus,String due,String mail,String documentStatus){
        if(!useApi())return dao.getAll().stream().filter(x->match(x,q,supplier,from,to,paymentStatus,due,mail,documentStatus)).toList();
        OperationsApiClient.PurchasePage first=api.purchasesPage(0,200,q,supplier,from,to,paymentStatus,due,mail,documentStatus);applyReturnSettlements(first.rows());List<Purchase> out=new ArrayList<>(first.rows()==null?List.of():first.rows());
        for(int p=1;p<first.totalPages();p++){OperationsApiClient.PurchasePage next=api.purchasesPage(p,200,q,supplier,from,to,paymentStatus,due,mail,documentStatus);applyReturnSettlements(next.rows());if(next.rows()!=null)out.addAll(next.rows());}
        return out;
    }
    public Purchase getByInvoice(String invoiceNo){ if(!useApi())return dao.getByInvoice(invoiceNo);Purchase row=api.purchase(invoiceNo);applyReturnSettlements(row==null?List.of():List.of(row));return row; }
    public boolean existsInvoice(String invoiceNo){ return useApi()?api.purchaseExists(invoiceNo):dao.getByInvoice(invoiceNo)!=null; }
    public void delete(String invoiceNo){
        if(useApi()){api.deletePurchase(invoiceNo);return;}
        Purchase document=dao.getByInvoice(invoiceNo);if(document==null)throw new IllegalArgumentException("Purchase document not found: "+invoiceNo);
        String ps=document.getPaymentStatus()==null?"":document.getPaymentStatus().trim().toUpperCase();
        boolean locked=document.getPaidAmount()>.0001||document.getBalanceAmount()<=.0001||ps.equals("PAID")||ps.equals("SETTLED")||ps.equals("PARTIAL");
        if(locked)throw new IllegalStateException("Paid, partially paid, or settled purchase documents cannot be deleted. Use the return/reversal workflow.");
        dao.delete(invoiceNo);
    }
    public void cancel(String invoiceNo){
        if(useApi()){api.cancelPurchase(invoiceNo);return;}
        Purchase document=dao.getByInvoice(invoiceNo);if(document==null)throw new IllegalArgumentException("Purchase document not found: "+invoiceNo);
        String ps=document.getPaymentStatus()==null?"":document.getPaymentStatus().trim().toUpperCase();
        boolean locked=document.getPaidAmount()>.0001||document.getBalanceAmount()<=.0001||ps.equals("PAID")||ps.equals("SETTLED")||ps.equals("PARTIAL");
        if(locked)throw new IllegalStateException("Paid, partially paid, or settled purchase documents cannot be cancelled. Use the return/reversal workflow.");
        dao.cancel(invoiceNo);
    }
    public void approve(String invoiceNo){ if(!useApi())throw new IllegalStateException("Approval workflow requires the server-owned data mode"); api.approvePurchase(invoiceNo); }
    public void reject(String invoiceNo,String reason){ if(!useApi())throw new IllegalStateException("Approval workflow requires the server-owned data mode"); api.rejectPurchase(invoiceNo,reason); }
    public void markEmailSent(int purchaseId){ if(useApi())api.markPurchaseEmail(purchaseId);else dao.markEmailSent(purchaseId); }


    private double localDistinctItemCount(List<Purchase> purchases){
        Set<String> codes=new HashSet<>();
        for(Purchase summary:purchases){
            Purchase full=summary;
            if(full.getLines()==null||full.getLines().isEmpty()){try{Purchase loaded=dao.getByInvoice(summary.getInvoiceNo());if(loaded!=null)full=loaded;}catch(Exception ignored){}}
            if(full.getLines()!=null)full.getLines().stream().filter(Objects::nonNull).map(org.example.model.PurchaseLine::getItemCode).filter(Objects::nonNull).map(String::trim).filter(v->!v.isBlank()).map(v->v.toUpperCase(Locale.ROOT)).forEach(codes::add);
        }
        return codes.size();
    }

    private boolean match(Purchase x,String q,String supplier,LocalDate from,LocalDate to,String paymentStatus,String due,String mail,String documentStatus){String global=low(q),hay=low(x.getInvoiceNo()+" "+(x.getSupplier()==null?"":x.getSupplier().getName())+" "+(x.getSupplier()==null?"":x.getSupplier().getPhone())+" "+(x.getSupplier()==null?"":x.getSupplier().getGstin()));if(!global.isBlank()&&!hay.contains(global))return false;if(supplier!=null&&!supplier.isBlank()&&!supplier.startsWith("All")&&(x.getSupplier()==null||!supplier.equals(x.getSupplier().getName())))return false;if(from!=null&&x.getInvoiceDate()!=null&&x.getInvoiceDate().isBefore(from))return false;if(to!=null&&x.getInvoiceDate()!=null&&x.getInvoiceDate().isAfter(to))return false;if(paymentStatus!=null&&!paymentStatus.isBlank()&&!"All".equalsIgnoreCase(paymentStatus)){if("OVERDUE".equalsIgnoreCase(paymentStatus)){if(!(active(x)&&x.getBalanceAmount()>.01&&effectiveDueDate(x)!=null&&effectiveDueDate(x).isBefore(BusinessClock.today())))return false;}else if(!paymentStatus.equalsIgnoreCase(x.getPaymentStatus()))return false;}if(due!=null&&!due.isBlank()&&!"All".equalsIgnoreCase(due)){LocalDate d=effectiveDueDate(x);if(x.getBalanceAmount()<=.01||d==null)return false;String f=due.trim().toUpperCase(Locale.ROOT);LocalDate today=BusinessClock.today();if("OVERDUE".equals(f)&&!d.isBefore(today))return false;if("DUE TODAY".equals(f)&&!d.isEqual(today))return false;if("NEXT 7 DAYS".equals(f)&&(d.isBefore(today)||d.isAfter(today.plusDays(7))))return false;if("NEXT 30 DAYS".equals(f)&&(d.isBefore(today)||d.isAfter(today.plusDays(30))))return false;}if(mail!=null&&!mail.isBlank()&&!"All".equalsIgnoreCase(mail)&&!mail.equalsIgnoreCase(x.isEmailSent()?"Sent":"Not Sent"))return false;if(documentStatus!=null&&!documentStatus.isBlank()&&!"All".equalsIgnoreCase(documentStatus)&&!documentStatus.equalsIgnoreCase(documentStatus(x)))return false;return true;}
    private LocalDate effectiveDueDate(Purchase x){if(x==null)return null;String ps=Objects.toString(x.getPaymentStatus(),"").trim().toUpperCase(Locale.ROOT);if(ps.startsWith("RETURN "))return x.getReturnDueDate();return x.getDueDate();}
    private void applyReturnSettlements(List<Purchase> rows){
        if(rows==null||rows.isEmpty())return;
        Map<String,ReturnApiClient.Settlement> byInvoice=new HashMap<>();
        try{for(ReturnApiClient.Settlement s:returnApi.settlements("PURCHASE RETURN"))if(s!=null&&s.invoiceNo()!=null)byInvoice.put(s.invoiceNo(),s);}catch(Exception e){throw new IllegalStateException("Unable to load authoritative Purchase Return lifecycle state. Refresh after the server connection is restored.",e);}
        for(Purchase row:rows){
            if(row==null)continue;
            ReturnApiClient.Settlement s=byInvoice.get(row.getInvoiceNo());
            if(s==null){row.clearReturnSettlement();continue;}
            LocalDate due=null;
            try{if(s.dueDate()!=null&&!s.dueDate().isBlank())due=LocalDate.parse(s.dueDate());}catch(Exception ignored){}
            row.applyReturnSettlement(s.status(),s.pendingAmount(),due,s.approvedReturnAmount(),s.settledAmount(),s.returnStatus(),s.refundStatus(),s.returnedQuantity(),s.originalQuantity());
        }
    }
    private String documentStatus(Purchase x){String stored=Objects.toString(x.getDocumentStatus(),"").trim().toUpperCase(Locale.ROOT);return stored.isBlank()?"PENDING APPROVAL":stored;}
    private boolean active(Purchase x){String d=Objects.toString(x.getDocumentStatus(),"").toUpperCase(Locale.ROOT);return !Set.of("CANCELLED","DELETED","DRAFT","REJECTED","PENDING APPROVAL").contains(d);}
    private static String low(String v){return v==null?"":v.trim().toLowerCase(Locale.ROOT);}
}
