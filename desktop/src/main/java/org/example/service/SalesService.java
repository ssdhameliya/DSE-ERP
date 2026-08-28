package org.example.service;

import org.example.api.operations.OperationsApiClient;
import org.example.api.returns.ReturnApiClient;
import org.example.config.ConfigManager;
import org.example.dao.SalesDAO;
import org.example.model.Sales;
import java.util.*;
import java.time.LocalDate;
import org.example.util.BusinessClock;

public class SalesService {
    private final SalesDAO dao = new SalesDAO();
    private final OperationsApiClient api = new OperationsApiClient();
    private final ReturnApiClient returnApi = new ReturnApiClient();
    private boolean useApi(){ return ConfigManager.isApiDataEnabled(); }
    public void save(Sales sales){ if(useApi()) api.saveSale(sales); else dao.save(sales); }
    public void update(Sales sales){ if(useApi()) api.updateSale(sales); else dao.update(sales); }
    public String nextInvoiceNo(){ return useApi()?api.nextSaleInvoice():dao.nextInvoiceNo(); }
    public List<Sales> getAll(){
        if(!useApi())return dao.getAll();
        List<Sales> rows=api.sales();
        applyReturnSettlements(rows);
        return rows;
    }
    public OperationsApiClient.SalesPage page(int page,int size,String q,String invoice,String customer,LocalDate from,LocalDate to,String paymentStatus,String due,String mail,String whatsapp,String invoiceType,String documentStatus,Double minAmount,Double maxAmount){if(useApi()){OperationsApiClient.SalesPage result=api.salesPage(page,size,q,invoice,customer,from,to,paymentStatus,due,mail,whatsapp,invoiceType,documentStatus,minAmount,maxAmount);applyReturnSettlements(result.rows());return result;}List<Sales> source=new ArrayList<>(dao.getAll());List<Sales> filtered=source.stream().filter(x->match(x,q,invoice,customer,from,to,paymentStatus,due,mail,whatsapp,invoiceType,documentStatus,minAmount,maxAmount)).toList();int safeSize=Math.max(10,Math.min(size,200)),pages=filtered.isEmpty()?0:(int)Math.ceil(filtered.size()/(double)safeSize),safePage=pages==0?0:Math.min(Math.max(0,page),pages-1),start=Math.min(safePage*safeSize,filtered.size()),end=Math.min(start+safeSize,filtered.size());List<Sales> activeFiltered=filtered.stream().filter(this::active).toList();var totals=new OperationsApiClient.RegisterTotals(activeFiltered.size(),sum(activeFiltered,Sales::getTotalAmount),sum(activeFiltered,Sales::getPaidAmount),sum(activeFiltered,Sales::getBalanceAmount));return new OperationsApiClient.SalesPage(List.copyOf(filtered.subList(start,end)),safePage,safeSize,filtered.size(),pages,totals,localMetrics(filtered),source.stream().map(Sales::getCustomer).filter(Objects::nonNull).map(org.example.model.Party::getName).filter(Objects::nonNull).distinct().sorted().toList());}
    public List<Sales> allFiltered(String q,String invoice,String customer,LocalDate from,LocalDate to,String paymentStatus,String due,String mail,String whatsapp,String invoiceType,String documentStatus,Double minAmount,Double maxAmount){
        if(!useApi())return dao.getAll().stream().filter(x->match(x,q,invoice,customer,from,to,paymentStatus,due,mail,whatsapp,invoiceType,documentStatus,minAmount,maxAmount)).toList();
        OperationsApiClient.SalesPage first=api.salesPage(0,200,q,invoice,customer,from,to,paymentStatus,due,mail,whatsapp,invoiceType,documentStatus,minAmount,maxAmount);applyReturnSettlements(first.rows());List<Sales> out=new ArrayList<>(first.rows()==null?List.of():first.rows());
        for(int p=1;p<first.totalPages();p++){OperationsApiClient.SalesPage next=api.salesPage(p,200,q,invoice,customer,from,to,paymentStatus,due,mail,whatsapp,invoiceType,documentStatus,minAmount,maxAmount);applyReturnSettlements(next.rows());if(next.rows()!=null)out.addAll(next.rows());}
        return out;
    }
    public Sales getByInvoice(String invoiceNo){ if(!useApi())return dao.getByInvoice(invoiceNo);Sales row=api.sale(invoiceNo);applyReturnSettlements(row==null?List.of():List.of(row));return row; }
    public boolean existsInvoice(String invoiceNo){ return useApi()?api.saleExists(invoiceNo):dao.getByInvoice(invoiceNo)!=null; }
    public void delete(String invoiceNo){ if(useApi())api.deleteSale(invoiceNo);else dao.delete(invoiceNo); }
    public void cancel(String invoiceNo){ if(useApi())api.cancelSale(invoiceNo);else dao.cancel(invoiceNo); }
    public void approve(String invoiceNo){ if(!useApi())throw new IllegalStateException("Approval workflow requires the server-owned data mode"); api.approveSale(invoiceNo); }
    public void reject(String invoiceNo,String reason){ if(!useApi())throw new IllegalStateException("Approval workflow requires the server-owned data mode"); api.rejectSale(invoiceNo,reason); }
    public void markEmailSent(int salesId){ if(useApi())api.markSaleEmail(salesId);else dao.markEmailSent(salesId); }

    private boolean match(Sales x,String q,String invoice,String customer,LocalDate from,LocalDate to,String paymentStatus,String due,String mail,String whatsapp,String invoiceType,String documentStatus,Double minAmount,Double maxAmount){String global=low(q),number=low(invoice),hay=low(x.getInvoiceNo()+" "+(x.getCustomer()==null?"":x.getCustomer().getName())+" "+(x.getCustomer()==null?"":x.getCustomer().getPhone())+" "+(x.getCustomer()==null?"":x.getCustomer().getGstin()));if(!global.isBlank()&&!hay.contains(global))return false;if(!number.isBlank()&&!low(x.getInvoiceNo()).contains(number))return false;if(customer!=null&&!customer.isBlank()&&!customer.startsWith("All")&&(x.getCustomer()==null||!customer.equals(x.getCustomer().getName())))return false;if(from!=null&&x.getInvoiceDate()!=null&&x.getInvoiceDate().isBefore(from))return false;if(to!=null&&x.getInvoiceDate()!=null&&x.getInvoiceDate().isAfter(to))return false;if(minAmount!=null&&x.getTotalAmount()<minAmount)return false;if(maxAmount!=null&&x.getTotalAmount()>maxAmount)return false;if(paymentStatus!=null&&!paymentStatus.isBlank()&&!"All".equalsIgnoreCase(paymentStatus)){if("OVERDUE".equalsIgnoreCase(paymentStatus)){if(!(x.getBalanceAmount()>.01&&x.getDueDate()!=null&&x.getDueDate().isBefore(BusinessClock.today())))return false;}else if(!paymentStatus.equalsIgnoreCase(x.getPaymentStatus()))return false;}if(mail!=null&&!mail.isBlank()&&!"All".equalsIgnoreCase(mail)&&!mail.equalsIgnoreCase(x.isEmailSent()?"Sent":"Not Sent"))return false;if(whatsapp!=null&&!whatsapp.isBlank()&&!"All".equalsIgnoreCase(whatsapp)&&!whatsapp.equalsIgnoreCase(x.isWhatsappSent()?"Sent":"Not Sent"))return false;if(invoiceType!=null&&!invoiceType.isBlank()&&!"All".equalsIgnoreCase(invoiceType)&&!invoiceType.equalsIgnoreCase(x.getInvoiceType()))return false;if(documentStatus!=null&&!documentStatus.isBlank()&&!"All".equalsIgnoreCase(documentStatus)&&!documentStatus.equalsIgnoreCase(documentStatus(x)))return false;if(due!=null&&!due.isBlank()&&!"All".equalsIgnoreCase(due)){if(x.getDueDate()==null||x.getBalanceAmount()<=0)return false;long days=java.time.temporal.ChronoUnit.DAYS.between(BusinessClock.today(),x.getDueDate());if("Overdue".equalsIgnoreCase(due)&&days>=0)return false;if("Due Today".equalsIgnoreCase(due)&&days!=0)return false;if("Next 7 Days".equalsIgnoreCase(due)&&(days<0||days>7))return false;if("Next 30 Days".equalsIgnoreCase(due)&&(days<0||days>30))return false;}return true;}
    private OperationsApiClient.SalesMetrics localMetrics(List<Sales> source){List<Sales> active=source.stream().filter(this::active).toList();double total=sum(active,Sales::getTotalAmount),today=sum(active.stream().filter(x->BusinessClock.today().equals(x.getInvoiceDate())).toList(),Sales::getTotalAmount),pending=sum(active,Sales::getBalanceAmount);List<Sales> overdue=active.stream().filter(x->x.getBalanceAmount()>0&&x.getDueDate()!=null&&x.getDueDate().isBefore(BusinessClock.today())).toList(),soon=active.stream().filter(x->x.getBalanceAmount()>0&&x.getDueDate()!=null&&!x.getDueDate().isBefore(BusinessClock.today())&&!x.getDueDate().isAfter(BusinessClock.today().plusDays(7))).toList();long sent=active.stream().filter(Sales::isEmailSent).count();Map<String,Double>buckets=new LinkedHashMap<>();buckets.put("Due Today",0d);buckets.put("1-7 Days",0d);buckets.put("8-30 Days",0d);buckets.put("Over 30 Days",0d);for(Sales x:active)if(x.getBalanceAmount()>0&&x.getDueDate()!=null){long d=java.time.temporal.ChronoUnit.DAYS.between(BusinessClock.today(),x.getDueDate());String k=d<=0?"Due Today":d<=7?"1-7 Days":d<=30?"8-30 Days":"Over 30 Days";buckets.merge(k,x.getBalanceAmount(),Double::sum);}Map<String,Double>customers=new HashMap<>();for(Sales x:active)customers.merge(x.getCustomer()==null?"Unknown Customer":Objects.toString(x.getCustomer().getName(),"Unknown Customer"),x.getTotalAmount(),Double::sum);Map<String,Double>months=new TreeMap<>();for(Sales x:active)if(x.getInvoiceDate()!=null)months.merge(x.getInvoiceDate().toString().substring(0,7),x.getTotalAmount(),Double::sum);return new OperationsApiClient.SalesMetrics(total,active.size(),today,active.stream().filter(x->BusinessClock.today().equals(x.getInvoiceDate())).count(),pending,active.stream().filter(x->x.getBalanceAmount()>0).count(),sum(overdue,Sales::getBalanceAmount),overdue.size(),sum(soon,Sales::getBalanceAmount),soon.size(),active.isEmpty()?0:sent*100d/active.size(),buckets.entrySet().stream().map(e->new OperationsApiClient.MetricPoint(e.getKey(),e.getValue())).toList(),customers.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed()).limit(5).map(e->new OperationsApiClient.MetricPoint(e.getKey(),e.getValue())).toList(),months.entrySet().stream().skip(Math.max(0,months.size()-7)).map(e->new OperationsApiClient.MetricPoint(e.getKey(),e.getValue())).toList());}
    private void applyReturnSettlements(List<Sales> rows){
        if(rows==null||rows.isEmpty())return;
        Map<String,ReturnApiClient.Settlement> byInvoice=new HashMap<>();
        try{for(ReturnApiClient.Settlement s:returnApi.settlements("SALES RETURN"))if(s!=null&&s.invoiceNo()!=null)byInvoice.put(s.invoiceNo(),s);}catch(Exception e){throw new IllegalStateException("Unable to load authoritative Sales Return lifecycle state. Refresh after the server connection is restored.",e);}
        for(Sales row:rows){
            if(row==null)continue;
            ReturnApiClient.Settlement s=byInvoice.get(row.getInvoiceNo());
            if(s==null){row.clearReturnSettlement();continue;}
            LocalDate due=null;
            try{if(s.dueDate()!=null&&!s.dueDate().isBlank())due=LocalDate.parse(s.dueDate());}catch(Exception ignored){}
            row.applyReturnSettlement(s.status(),s.pendingAmount(),due);
        }
    }
    private String documentStatus(Sales x){String stored=Objects.toString(x.getDocumentStatus(),"").trim().toUpperCase(Locale.ROOT);return stored.isBlank()?"PENDING APPROVAL":stored;}
    private boolean active(Sales x){String d=Objects.toString(x.getDocumentStatus(),"").toUpperCase(Locale.ROOT);return !"CANCELLED".equals(d)&&!"DELETED".equals(d);}
    private static String low(String v){return v==null?"":v.trim().toLowerCase(Locale.ROOT);}
    private static double sum(List<Sales> rows,java.util.function.ToDoubleFunction<Sales> f){return rows.stream().mapToDouble(f).sum();}
}
