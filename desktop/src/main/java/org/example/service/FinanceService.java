package org.example.service;
import org.example.api.operations.OperationsApiClient;
import java.util.List;
/** Phase-3 facade for Bank/Expense data. PostgreSQL production mode uses the Spring API; legacy desktop JDBC paths are being retired separately. */
public class FinanceService {
 private final OperationsApiClient api=new OperationsApiClient();
 public List<OperationsApiClient.FinanceEntry> getAll(){return api.finance();}
 public OperationsApiClient.FinancePage page(int page,int size,String mode,String period,String type,String query,java.time.LocalDate from,java.time.LocalDate to){return api.financePage(page,size,mode,period,type,query,from,to);}
 public OperationsApiClient.FinanceEntry get(int id){return api.finance(id);}
 public OperationsApiClient.FinanceMetrics metrics(){return api.financeMetrics();}
 public OperationsApiClient.FinanceEntry save(OperationsApiClient.FinanceEntry e){return api.saveFinance(e);}
 public OperationsApiClient.FinanceEntry update(OperationsApiClient.FinanceEntry e){return api.updateFinance(e);}
 public void delete(int id,long rowVersion){api.deleteFinance(id,rowVersion);} public String nextVoucher(){return api.nextVoucher();}
}
