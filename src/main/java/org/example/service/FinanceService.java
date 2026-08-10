package org.example.service;
import org.example.api.operations.OperationsApiClient;
import java.util.List;
/** Phase-3 facade for Bank/Expense data. PostgreSQL mode is API-only; SQLite stays in the legacy controller path. */
public class FinanceService {
 private final OperationsApiClient api=new OperationsApiClient();
 public List<OperationsApiClient.FinanceEntry> getAll(){return api.finance();}
 public OperationsApiClient.FinanceMetrics metrics(){return api.financeMetrics();}
 public OperationsApiClient.FinanceEntry save(OperationsApiClient.FinanceEntry e){return api.saveFinance(e);}
 public OperationsApiClient.FinanceEntry update(OperationsApiClient.FinanceEntry e){return api.updateFinance(e);}
 public void delete(int id){api.deleteFinance(id);} public String nextVoucher(){return api.nextVoucher();}
}
