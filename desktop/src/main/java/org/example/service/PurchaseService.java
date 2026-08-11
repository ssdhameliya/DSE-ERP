package org.example.service;

import org.example.api.operations.OperationsApiClient;
import org.example.config.ConfigManager;
import org.example.dao.PurchaseDAO;
import org.example.model.Purchase;
import java.util.List;

public class PurchaseService {
    private final PurchaseDAO dao = new PurchaseDAO();
    private final OperationsApiClient api = new OperationsApiClient();
    private boolean useApi(){ return ConfigManager.isApiDataEnabled(); }
    public void save(Purchase purchase){ if(useApi())api.savePurchase(purchase);else dao.save(purchase); }
    public void update(Purchase purchase){ if(useApi())api.updatePurchase(purchase);else dao.update(purchase); }
    public String nextInvoiceNo(){ return useApi()?api.nextPurchaseInvoice():dao.nextInvoiceNo(); }
    public List<Purchase> getAll(){ return useApi()?api.purchases():dao.getAll(); }
    public Purchase getByInvoice(String invoiceNo){ return useApi()?api.purchase(invoiceNo):dao.getByInvoice(invoiceNo); }
    public boolean existsInvoice(String invoiceNo){ return useApi()?api.purchaseExists(invoiceNo):dao.getByInvoice(invoiceNo)!=null; }
    public void delete(String invoiceNo){
        if(useApi()){api.deletePurchase(invoiceNo);return;}
        Purchase document=dao.getByInvoice(invoiceNo);if(document==null)throw new IllegalArgumentException("Purchase document not found: "+invoiceNo);
        String ps=document.getPaymentStatus()==null?"":document.getPaymentStatus().trim().toUpperCase();
        boolean locked=document.getPaidAmount()>.0001||ps.equals("PAID")||ps.equals("SETTLED")||ps.equals("PARTIAL");
        if(locked)throw new IllegalStateException("Paid, partially paid, or settled purchase documents cannot be deleted. Use the return/reversal workflow.");
        dao.delete(invoiceNo);
    }
    public void markEmailSent(int purchaseId){ if(useApi())api.markPurchaseEmail(purchaseId);else dao.markEmailSent(purchaseId); }
}
