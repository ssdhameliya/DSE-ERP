package org.example.service;

import org.example.api.operations.OperationsApiClient;
import org.example.config.ConfigManager;
import org.example.dao.PurchaseDAO;
import org.example.model.Purchase;
import java.util.*;
import java.time.LocalDate;
import org.example.util.BusinessClock;

public class PurchaseService {
    private final PurchaseDAO dao = new PurchaseDAO();
    private final OperationsApiClient api = new OperationsApiClient();
    private boolean useApi(){ return ConfigManager.isApiDataEnabled(); }
    public void save(Purchase purchase){ if(useApi())api.savePurchase(purchase);else dao.save(purchase); }
    public void update(Purchase purchase){ if(useApi())api.updatePurchase(purchase);else dao.update(purchase); }
    public String nextInvoiceNo(){ return useApi()?api.nextPurchaseInvoice():dao.nextInvoiceNo(); }
    public List<Purchase> getAll(){ return useApi()?api.purchases():dao.getAll(); }
    public OperationsApiClient.PurchasePage page(int page,int size,String q,String supplier,LocalDate from,LocalDate to,String paymentStatus,String mail){if(useApi())return api.purchasesPage(page,size,q,supplier,from,to,paymentStatus,mail);List<Purchase> source=new ArrayList<>(dao.getAll());List<Purchase> filtered=source.stream().filter(x->match(x,q,supplier,from,to,paymentStatus,mail)).toList();int safeSize=Math.max(10,Math.min(size,200)),pages=filtered.isEmpty()?0:(int)Math.ceil(filtered.size()/(double)safeSize),safePage=pages==0?0:Math.min(Math.max(0,page),pages-1),start=Math.min(safePage*safeSize,filtered.size()),end=Math.min(start+safeSize,filtered.size());List<Purchase> activeFiltered=filtered.stream().filter(this::active).toList();var totals=new OperationsApiClient.RegisterTotals(activeFiltered.size(),activeFiltered.stream().mapToDouble(Purchase::getTotalAmount).sum(),activeFiltered.stream().mapToDouble(Purchase::getPaidAmount).sum(),activeFiltered.stream().mapToDouble(Purchase::getBalanceAmount).sum());List<Purchase> active=source.stream().filter(this::active).toList();var metrics=new OperationsApiClient.PurchaseMetrics(active.stream().mapToDouble(Purchase::getTotalAmount).sum(),active.size(),active.stream().map(Purchase::getSupplier).filter(Objects::nonNull).map(org.example.model.Party::getId).distinct().count(),active.stream().mapToDouble(Purchase::getQuantity).sum(),active.stream().mapToDouble(Purchase::getPaidAmount).sum());return new OperationsApiClient.PurchasePage(List.copyOf(filtered.subList(start,end)),safePage,safeSize,filtered.size(),pages,totals,metrics,source.stream().map(Purchase::getSupplier).filter(Objects::nonNull).map(org.example.model.Party::getName).filter(Objects::nonNull).distinct().sorted().toList());}
    public List<Purchase> allFiltered(String q,String supplier,LocalDate from,LocalDate to,String paymentStatus,String mail){
        if(!useApi())return dao.getAll().stream().filter(x->match(x,q,supplier,from,to,paymentStatus,mail)).toList();
        OperationsApiClient.PurchasePage first=api.purchasesPage(0,200,q,supplier,from,to,paymentStatus,mail);List<Purchase> out=new ArrayList<>(first.rows()==null?List.of():first.rows());
        for(int p=1;p<first.totalPages();p++){OperationsApiClient.PurchasePage next=api.purchasesPage(p,200,q,supplier,from,to,paymentStatus,mail);if(next.rows()!=null)out.addAll(next.rows());}
        return out;
    }
    public Purchase getByInvoice(String invoiceNo){ return useApi()?api.purchase(invoiceNo):dao.getByInvoice(invoiceNo); }
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

    private boolean match(Purchase x,String q,String supplier,LocalDate from,LocalDate to,String paymentStatus,String mail){String global=low(q),hay=low(x.getInvoiceNo()+" "+(x.getSupplier()==null?"":x.getSupplier().getName())+" "+(x.getSupplier()==null?"":x.getSupplier().getPhone())+" "+(x.getSupplier()==null?"":x.getSupplier().getGstin()));if(!global.isBlank()&&!hay.contains(global))return false;if(supplier!=null&&!supplier.isBlank()&&!supplier.startsWith("All")&&(x.getSupplier()==null||!supplier.equals(x.getSupplier().getName())))return false;if(from!=null&&x.getInvoiceDate()!=null&&x.getInvoiceDate().isBefore(from))return false;if(to!=null&&x.getInvoiceDate()!=null&&x.getInvoiceDate().isAfter(to))return false;if(paymentStatus!=null&&!paymentStatus.isBlank()&&!"All".equalsIgnoreCase(paymentStatus)){if("OVERDUE".equalsIgnoreCase(paymentStatus)){if(!(active(x)&&x.getBalanceAmount()>.01&&x.getDueDate()!=null&&x.getDueDate().isBefore(BusinessClock.today())))return false;}else if(!paymentStatus.equalsIgnoreCase(x.getPaymentStatus()))return false;}return mail==null||mail.isBlank()||"All".equalsIgnoreCase(mail)||mail.equalsIgnoreCase(x.isEmailSent()?"Sent":"Not Sent");}
    private boolean active(Purchase x){String d=Objects.toString(x.getDocumentStatus(),"").toUpperCase(Locale.ROOT);return !d.contains("CANCEL")&&!d.contains("DELETE")&&!"DRAFT".equals(d);}
    private static String low(String v){return v==null?"":v.trim().toLowerCase(Locale.ROOT);}
}
