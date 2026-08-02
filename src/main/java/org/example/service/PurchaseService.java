package org.example.service;

import org.example.dao.PurchaseDAO;
import org.example.model.Purchase;

import java.util.List;

public class PurchaseService {

    private final PurchaseDAO dao = new PurchaseDAO();


    /**
     * Save New Purchase
     */
    public void save(Purchase purchase) {
        dao.save(purchase);
    }


    /**
     * Update Existing Purchase
     */
    public void update(Purchase purchase) {
        dao.update(purchase);
    }


    /**
     * Next Purchase Invoice Number
     */
    public String nextInvoiceNo() {
        return dao.nextInvoiceNo();
    }


    /**
     * Purchase Register
     */
    public List<Purchase> getAll() {
        return dao.getAll();
    }


    /**
     * Load Complete Purchase
     */
    public Purchase getByInvoice(String invoiceNo) {
        return dao.getByInvoice(invoiceNo);
    }


    /**
     * Delete Purchase
     */
    public void delete(String invoiceNo){
        dao.delete(invoiceNo);
    }


    /**
     * Update Email Status
     */
    public void markEmailSent(int purchaseId) {
        dao.markEmailSent(purchaseId);
    }

}
