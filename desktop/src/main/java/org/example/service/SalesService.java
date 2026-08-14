package org.example.service;

import org.example.api.operations.OperationsApiClient;
import org.example.config.ConfigManager;
import org.example.dao.SalesDAO;
import org.example.model.Sales;
import java.util.List;

public class SalesService {
    private final SalesDAO dao = new SalesDAO();
    private final OperationsApiClient api = new OperationsApiClient();
    private boolean useApi(){ return ConfigManager.isApiDataEnabled(); }
    public void save(Sales sales){ if(useApi()) api.saveSale(sales); else dao.save(sales); }
    public void update(Sales sales){ if(useApi()) api.updateSale(sales); else dao.update(sales); }
    public String nextInvoiceNo(){ return useApi()?api.nextSaleInvoice():dao.nextInvoiceNo(); }
    public List<Sales> getAll(){ return useApi()?api.sales():dao.getAll(); }
    public Sales getByInvoice(String invoiceNo){ return useApi()?api.sale(invoiceNo):dao.getByInvoice(invoiceNo); }
    public boolean existsInvoice(String invoiceNo){ return useApi()?api.saleExists(invoiceNo):dao.getByInvoice(invoiceNo)!=null; }
    public void delete(String invoiceNo){ if(useApi())api.deleteSale(invoiceNo);else dao.delete(invoiceNo); }
    public void cancel(String invoiceNo){ if(useApi())api.cancelSale(invoiceNo);else dao.cancel(invoiceNo); }
    public void markEmailSent(int salesId){ if(useApi())api.markSaleEmail(salesId);else dao.markEmailSent(salesId); }
}
