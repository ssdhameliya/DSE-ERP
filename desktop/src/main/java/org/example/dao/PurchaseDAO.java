package org.example.dao;

import org.example.api.operations.OperationsApiClient;
import org.example.model.Purchase;
import java.util.List;

/** Compatibility DAO backed by the typed Spring operations API. */
public class PurchaseDAO {
    private final OperationsApiClient api = new OperationsApiClient();

    public void save(Purchase purchase) { api.savePurchase(purchase); }
    public List<Purchase> getAll() { return api.purchases(); }
    public String nextInvoiceNo() { return api.nextPurchaseInvoice(); }
    public Purchase getByInvoice(String invoiceNo) { return api.purchase(invoiceNo); }
    public void update(Purchase purchase) { api.updatePurchase(purchase); }
    public void delete(String invoiceNo) { api.deletePurchase(invoiceNo); }
    public void cancel(String invoiceNo) { api.cancelPurchase(invoiceNo); }
    public void markEmailSent(int purchaseId) { api.markPurchaseEmail(purchaseId); }
}
