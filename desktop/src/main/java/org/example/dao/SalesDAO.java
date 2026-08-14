package org.example.dao;

import org.example.api.operations.OperationsApiClient;
import org.example.model.Sales;
import java.util.List;

/**
 * Compatibility DAO backed by the typed Spring operations API.
 * Keeps the historical DAO contract for JavaFX callers without exposing JDBC.
 */
public class SalesDAO {
    private final OperationsApiClient api = new OperationsApiClient();

    public synchronized void save(Sales sales) { api.saveSale(sales); }
    public List<Sales> getAll() { return api.sales(); }
    public String nextInvoiceNo() { return api.nextSaleInvoice(); }
    public Sales getByInvoice(String invoiceNo) { return api.sale(invoiceNo); }
    public void update(Sales sales) { api.updateSale(sales); }
    public void delete(String invoiceNo) { api.deleteSale(invoiceNo); }
    public void cancel(String invoiceNo) { api.cancelSale(invoiceNo); }
    public void markEmailSent(int salesId) { api.markSaleEmail(salesId); }
}
