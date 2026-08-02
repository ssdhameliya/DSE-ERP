package org.example.service;

import org.example.dao.SalesDAO;
import org.example.model.Sales;

import java.util.List;

public class SalesService {

    private final SalesDAO dao = new SalesDAO();


    /**
     * Save New Sale
     */
    public void save(Sales sales) {
        dao.save(sales);
    }


    /**
     * Update Existing Sale
     */
    public void update(Sales sales) {
        dao.update(sales);
    }


    /**
     * Next Sales Invoice Number
     */
    public String nextInvoiceNo() {
        return dao.nextInvoiceNo();
    }


    /**
     * Sales Register
     */
    public List<Sales> getAll() {
        return dao.getAll();
    }


    /**
     * Load Complete Sale
     */
    public Sales getByInvoice(String invoiceNo) {
        return dao.getByInvoice(invoiceNo);
    }


    /**
     * Delete Sale
     */
    public void delete(String invoiceNo) {
        dao.delete(invoiceNo);
    }


    /**
     * Update Email Status
     */
    public void markEmailSent(int salesId) {
        dao.markEmailSent(salesId);
    }

}
