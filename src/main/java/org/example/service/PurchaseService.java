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
    public void delete(String invoiceNo) {
        Purchase document = dao.getByInvoice(invoiceNo);
        if (document == null) throw new IllegalArgumentException("Purchase document not found: " + invoiceNo);
        String paymentStatus = document.getPaymentStatus() == null ? "" : document.getPaymentStatus().trim().toUpperCase();
        boolean financiallyLocked = document.getPaidAmount() > 0.0001
            || paymentStatus.equals("PAID") || paymentStatus.equals("SETTLED") || paymentStatus.equals("PARTIAL");
        if (financiallyLocked) {
            throw new IllegalStateException("Paid, partially paid, or settled purchase documents cannot be deleted. Use the return/reversal workflow.");
        }
        dao.delete(invoiceNo);
    }


    /**
     * Update Email Status
     */
    public void markEmailSent(int purchaseId) {
        dao.markEmailSent(purchaseId);
    }

}
