package org.example.server.operations;

import org.example.server.util.BusinessClock;

import org.example.server.persistence.entity.*;
import org.example.server.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.*;

@Service
public class BusinessOperationsService {
 private final SalesHeaderRepository sales; private final SalesLineRepository salesLines; private final SalesChargeRepository salesCharges; private final PurchaseHeaderRepository purchases; private final PurchaseLineRepository purchaseLines; private final PartyRepository parties; private final ItemRepository items; private final LookupRepository lookups; private final MasterCategoryRepository categories; private final FinanceRegisterRepository finance; private final BankReconciliationAllocationRepository reconciliationAllocations; private final JpaNativeRepository jdbc;
 public BusinessOperationsService(SalesHeaderRepository s,SalesLineRepository sl,SalesChargeRepository sc,PurchaseHeaderRepository p,PurchaseLineRepository pl,PartyRepository pa,ItemRepository i,LookupRepository l,MasterCategoryRepository c,FinanceRegisterRepository f,BankReconciliationAllocationRepository ra,JpaNativeRepository jdbc){sales=s;salesLines=sl;salesCharges=sc;purchases=p;purchaseLines=pl;parties=pa;items=i;lookups=l;categories=c;finance=f;reconciliationAllocations=ra;this.jdbc=jdbc;}

 @Transactional(readOnly=true) public List<OperationDtos.SaleDto> sales(){return sales.findAllByOrderByInvoiceDateDescIdDesc().stream().map(h->saleDto(h,false)).toList();}
 @Transactional(readOnly=true) public OperationDtos.SaleDto sale(String invoice){return saleDto(sales.findByInvoiceNo(invoice).orElseThrow(()->new IllegalArgumentException("Sale not found: "+invoice)),true);}
 @Transactional(readOnly=true) public boolean saleExists(String invoiceNo){return sales.findByInvoiceNo(invoiceNo).isPresent();}

 @Transactional public OperationDtos.SaleDto saveSale(OperationDtos.SaleDto d){if(d==null)throw new IllegalArgumentException("Sale data is required");validateDocumentLines(d.lines(),"Sale");SalesHeaderEntity h=new SalesHeaderEntity(); copySale(d,h); if(blank(h.getInvoiceNo())||sales.existsByInvoiceNo(h.getInvoiceNo()))h.setInvoiceNo(nextSalesInvoice()); h.setCreatedAt(BusinessClock.nowUtcText()); h.setEmailSent(0); h.setDocumentStatus(blank(h.getDocumentStatus())?"PENDING":h.getDocumentStatus()); h=sales.save(h); replaceSaleLines(h.getId(),d.lines(),false);replaceSaleCharges(h.getId(),normalizedCharges(d)); return saleDto(h,true);}
 @Transactional public OperationDtos.SaleDto updateSale(OperationDtos.SaleDto d){
  if(d==null)throw new IllegalArgumentException("Sale data is required");
  validateDocumentLines(d.lines(),"Sale");
  SalesHeaderEntity h=sales.findByInvoiceNoForUpdate(d.invoiceNo()).orElseThrow(()->new IllegalArgumentException("Sale not found: "+d.invoiceNo()));
  String existingStatus=up(h.getDocumentStatus());
  if(Set.of("DELETED","CANCELLED").contains(existingStatus))throw new IllegalStateException("Deleted or cancelled Sales invoices cannot be edited.");
  List<OperationDtos.ChargeDto> newCharges=normalizedCharges(d);
  boolean linesChanged=!sameSaleLines(h.getId(),d.lines());
  boolean chargesChanged=!sameSaleCharges(h.getId(),newCharges);
  boolean totalsChanged=!sameNumber(h.getSubtotal(),d.subtotal())||!sameNumber(h.getGstAmount(),d.gstAmount())||!sameNumber(h.getTotalAmount(),d.totalAmount())||!sameNumber(h.getDiscountAmount(),d.discountAmount());
  if(hasActiveReturn("SALES RETURN",h.getInvoiceNo())&&(linesChanged||totalsChanged))throw new IllegalStateException("A Sale with an active Sales Return cannot change items or financial totals. Reverse/cancel the return first.");
  double recordedPaid=recordedPaymentTotal("SALE",h.getId());
  if((linesChanged||totalsChanged)&&(n(h.getPaidAmount())>.0001||recordedPaid>.0001||Set.of("PAID","SETTLED","PARTIAL").contains(up(h.getPaymentStatus()))))throw new IllegalStateException("A paid or partially paid Sale cannot change items or financial totals. Use Sales Return / payment reversal first.");

  // Workflow/payment state is not owned by the invoice editor. Preserve it even
  // when an older/partial desktop client sends default values during an update.
  Double paidAmount=h.getPaidAmount();
  String paymentStatus=h.getPaymentStatus();
  Integer emailSent=h.getEmailSent();
  Integer whatsappSent=h.getWhatsappSent();
  String documentStatus=h.getDocumentStatus();
  String createdAt=h.getCreatedAt();
  String source=h.getSource();
  String invoiceType=h.getInvoiceType();

  // Metadata-only edits (address, GSTIN, transport, reference, notes, etc.) must
  // not reverse/reapply stock or delete/recreate invoice lines.
  if(linesChanged)restoreSaleStock(h.getId());
  copySale(d,h);
  h.setPaidAmount(paidAmount);
  h.setPaymentStatus(paymentStatus);
  h.setEmailSent(emailSent);
  h.setWhatsappSent(whatsappSent);
  h.setDocumentStatus(documentStatus);
  h.setCreatedAt(createdAt);
  h.setSource(source);
  h.setInvoiceType(invoiceType);
  if(linesChanged)replaceSaleLines(h.getId(),d.lines(),false);
  if(chargesChanged)replaceSaleCharges(h.getId(),newCharges);
  return saleDto(h,true);
 }
 @Transactional public void deleteSale(String invoice){SalesHeaderEntity h=sales.findByInvoiceNoForUpdate(invoice).orElseThrow(()->new IllegalArgumentException("Sale not found: "+invoice));assertDocumentHasNoPayments("SALE",h.getId(),n(h.getPaidAmount()),h.getPaymentStatus(),"deleted");if(hasActiveReturn("SALES RETURN",h.getInvoiceNo()))throw new IllegalStateException("A Sale with an active Sales Return cannot be deleted. Reverse/cancel the return first.");if(!"DELETED".equalsIgnoreCase(h.getDocumentStatus())&&!"CANCELLED".equalsIgnoreCase(h.getDocumentStatus()))restoreSaleStock(h.getId());h.setDocumentStatus("DELETED");}
 @Transactional public void cancelSale(String invoice){deleteSale(invoice);} // backward-compatible alias: legacy cancel now performs a soft delete
 @Transactional public void markSaleEmail(int id){SalesHeaderEntity h=sales.findById(id).orElseThrow();h.setEmailSent(1);}
 @Transactional(readOnly=true) public String nextSalesInvoice(){return configuredNext("REF_SALES","IN/DD-MM-YYYY/XXXX",sales.findAll().stream().map(SalesHeaderEntity::getInvoiceNo).filter(Objects::nonNull).toList());}

 @Transactional(readOnly=true) public List<OperationDtos.PurchaseDto> purchases(){Map<Integer,Double> paid=recordedPurchasePayments();return purchases.findAllByOrderByInvoiceDateDescIdDesc().stream().map(h->purchaseDto(h,false,effectivePurchasePaid(h,paid.getOrDefault(h.getId(),0d)))).toList();}
 @Transactional(readOnly=true) public OperationDtos.PurchaseDto purchase(String invoice){PurchaseHeaderEntity h=purchases.findByInvoiceNo(invoice).orElseThrow(()->new IllegalArgumentException("Purchase not found: "+invoice));Double recorded=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE UPPER(document_type)='PURCHASE' AND document_id=?",Double.class,h.getId());return purchaseDto(h,true,effectivePurchasePaid(h,n(recorded)));}
 @Transactional(readOnly=true) public boolean purchaseExists(String invoiceNo){return purchases.findByInvoiceNo(invoiceNo).isPresent();}

 @Transactional public OperationDtos.PurchaseDto savePurchase(OperationDtos.PurchaseDto d){
  if(d==null)throw new IllegalArgumentException("Purchase data is required");
  validateDocumentLines(d.lines(),"Purchase");
  PurchaseHeaderEntity h=new PurchaseHeaderEntity();
  copyPurchase(d,h);
  if(blank(h.getInvoiceNo())||purchases.existsByInvoiceNo(h.getInvoiceNo()))h.setInvoiceNo(nextPurchaseInvoice());
  String status=normalizePurchaseStatus(h.getDocumentStatus());
  h.setDocumentStatus(status);
  h.setInventoryPosted(shouldPostPurchaseInventory(status));
  h.setCreatedAt(BusinessClock.nowUtcText());
  h=purchases.save(h);
  // New drafts keep their lines but deliberately do not affect inventory.
  replacePurchaseLines(h.getId(),d.lines(),!Boolean.TRUE.equals(h.getInventoryPosted()));
  return purchaseDto(h,true);
 }
 @Transactional public OperationDtos.PurchaseDto updatePurchase(OperationDtos.PurchaseDto d){
  if(d==null)throw new IllegalArgumentException("Purchase data is required");
  validateDocumentLines(d.lines(),"Purchase");
  PurchaseHeaderEntity h=purchases.findByInvoiceNoForUpdate(d.invoiceNo()).orElseThrow(()->new IllegalArgumentException("Purchase not found: "+d.invoiceNo()));
  String existingStatus=normalizePurchaseStatus(h.getDocumentStatus());
  if(isInactivePurchase(existingStatus))throw new IllegalStateException("Deleted or cancelled purchases cannot be edited.");

  boolean linesChanged=!samePurchaseLines(h.getId(),d.lines());
  boolean totalsChanged=!sameNumber(h.getSubtotal(),d.subtotal())||!sameNumber(h.getGstAmount(),d.gstAmount())||!sameNumber(h.getTotalAmount(),d.totalAmount())||!sameNumber(h.getDiscountAmount(),d.discountAmount());
  if(hasActiveReturn("PURCHASE RETURN",h.getInvoiceNo())&&(linesChanged||totalsChanged))
   throw new IllegalStateException("A purchase with an active Purchase Return cannot change items or financial totals. Reverse/cancel the return first.");
  double recordedPaid=recordedPaymentTotal("PURCHASE",h.getId());
  String existingPaymentStatus=up(h.getPaymentStatus());
  if((linesChanged||totalsChanged)&&(n(h.getPaidAmount())>.0001||recordedPaid>.0001||Set.of("PAID","SETTLED","PARTIAL").contains(existingPaymentStatus)))
   throw new IllegalStateException("A paid or partially paid purchase cannot change items or financial totals. Use Purchase Return / payment reversal first.");

  // The editor may promote a DRAFT to COMPLETED, but a posted document cannot be
  // silently downgraded back to DRAFT by an older/cached client.
  String requested=normalizePurchaseStatus(d.documentStatus());
  String nextStatus="DRAFT".equals(existingStatus)&&!"DRAFT".equals(requested)?"COMPLETED":existingStatus;
  boolean wasPosted=Boolean.TRUE.equals(h.getInventoryPosted());
  // Legacy releases posted stock even for DRAFT purchases. A header-only edit of one
  // of those records must preserve that historical posting. Once its lines change,
  // the draft adopts the new no-stock semantics; promotion posts the current lines.
  boolean shouldBePosted="DRAFT".equals(existingStatus)&&"DRAFT".equals(nextStatus)
          ? (wasPosted&&!linesChanged)
          : shouldPostPurchaseInventory(nextStatus);

  Double paidAmount=h.getPaidAmount(); String paymentStatus=h.getPaymentStatus(); Integer emailSent=h.getEmailSent();
  String createdAt=h.getCreatedAt(),createdBy=h.getCreatedBy(); String existingDocumentStatus=h.getDocumentStatus();

  if(wasPosted&&linesChanged)restorePurchaseStock(h.getId());
  copyPurchase(d,h);
  h.setPaidAmount(paidAmount);h.setPaymentStatus(paymentStatus);h.setEmailSent(emailSent);h.setCreatedAt(createdAt);h.setCreatedBy(createdBy);
  h.setDocumentStatus("DRAFT".equals(existingStatus)?nextStatus:existingDocumentStatus);
  h.setInventoryPosted(shouldBePosted);

  if(linesChanged)replacePurchaseLines(h.getId(),d.lines(),!shouldBePosted);
  else if(!wasPosted&&shouldBePosted)postPurchaseStock(h.getId());
  return purchaseDto(h,true);
 }
 @Transactional public void deletePurchase(String invoice){PurchaseHeaderEntity h=purchases.findByInvoiceNoForUpdate(invoice).orElseThrow(()->new IllegalArgumentException("Purchase not found: "+invoice));assertDocumentHasNoPayments("PURCHASE",h.getId(),n(h.getPaidAmount()),h.getPaymentStatus(),"deleted");if(hasActiveReturn("PURCHASE RETURN",h.getInvoiceNo()))throw new IllegalStateException("A purchase with an active Purchase Return cannot be deleted. Reverse/cancel the return first.");if(Boolean.TRUE.equals(h.getInventoryPosted())){restorePurchaseStock(h.getId());h.setInventoryPosted(false);}h.setDocumentStatus("DELETED");}
 @Transactional public void markPurchaseEmail(int id){PurchaseHeaderEntity h=purchases.findById(id).orElseThrow();h.setEmailSent(1);}
 @Transactional(readOnly=true) public String nextPurchaseInvoice(){return configuredNext("REF_PURCHASE","PUR/DD-MM-YYYY/XXXX",purchases.findAll().stream().map(PurchaseHeaderEntity::getInvoiceNo).filter(Objects::nonNull).toList());}

 private void assertDocumentHasNoPayments(String type,int id,double cachedPaid,String paymentStatus,String action){Double recorded=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE UPPER(document_type)=? AND document_id=?",Double.class,type,id);String ps=up(paymentStatus);if(cachedPaid>.0001||n(recorded)>.0001||Set.of("PAID","SETTLED","PARTIAL").contains(ps))throw new IllegalStateException("Paid, partially paid, or settled "+type.toLowerCase(Locale.ROOT)+" documents cannot be "+action+". Use the return/reversal workflow.");}

 @Transactional(readOnly=true) public List<OperationDtos.FinanceDto> finance(){return finance.findAllByOrderByVoucherDateDescIdDesc().stream().map(this::financeDto).toList();}
 @Transactional public OperationDtos.FinanceDto saveFinance(OperationDtos.FinanceDto d){FinanceRegisterEntity e=new FinanceRegisterEntity();copyFinance(d,e);if(blank(e.getVoucherNo()))e.setVoucherNo(nextVoucher());e.setCreatedAt(BusinessClock.nowUtcText());return financeDto(finance.save(e));}
 @Transactional public OperationDtos.FinanceDto updateFinance(OperationDtos.FinanceDto d){FinanceRegisterEntity e=finance.findById(req(d.id())).orElseThrow(()->new IllegalArgumentException("Finance entry not found"));copyFinance(d,e);return financeDto(e);}
 @Transactional public void deleteFinance(int id){finance.deleteById(id);}
 @Transactional(readOnly=true) public String nextVoucher(){int next=finance.findTopByOrderByIdDesc().map(x->x.getId()+1).orElse(1);return "VCH-"+BusinessClock.today().getYear()+"-"+String.format(Locale.ROOT,"%05d",next);}

 @Transactional(readOnly=true) public List<OperationDtos.StockHistoryDto> stockHistory(String itemCode){
   String sql="""
     SELECT CAST(adjustment_date AS DATE) AS movement_day, adjustment_type AS movement_type, quantity, reason, reference_no, created_by
     FROM stock_adjustment WHERE item_code=?
     UNION ALL
     SELECT CAST(h.invoice_date AS DATE), 'SALE', -l.quantity, 'Sales invoice', h.invoice_no, COALESCE(h.salesperson,'System')
     FROM sales_line l JOIN sales_header h ON h.id=l.sales_id WHERE l.item_code=? AND UPPER(COALESCE(h.document_status,'')) NOT IN ('DELETED','CANCELLED')
     UNION ALL
     SELECT CAST(h.invoice_date AS DATE), 'PURCHASE', l.quantity, 'Purchase invoice', h.invoice_no, 'System'
     FROM purchase_line l JOIN purchase_header h ON h.id=l.purchase_id WHERE l.item_code=? AND COALESCE(h.inventory_posted,false)=true AND UPPER(COALESCE(h.document_status,'')) NOT IN ('DELETED','CANCELLED')
     UNION ALL
     SELECT CASE WHEN COALESCE(return_date,'') ~ '^\\d{4}-\\d{2}-\\d{2}$' THEN TO_DATE(return_date,'YYYY-MM-DD') WHEN COALESCE(return_date,'') ~ '^\\d{2}/\\d{2}/\\d{4}$' THEN TO_DATE(return_date,'DD/MM/YYYY') WHEN COALESCE(return_date,'') ~ '^\\d{2}-\\d{2}-\\d{4}$' THEN TO_DATE(return_date,'DD-MM-YYYY') ELSE NULL END, return_type, CASE WHEN UPPER(return_type) IN ('SALE RETURN','SALES RETURN') THEN quantity ELSE -quantity END, COALESCE(reason,'Return'), return_no, 'System'
     FROM return_register WHERE item_code=? AND UPPER(COALESCE(status,'PENDING'))<>'CANCELLED'
     ORDER BY movement_day DESC
     """;
   return jdbc.query(sql,(r,i)->new OperationDtos.StockHistoryDto(String.valueOf(r.getObject(1)),r.getString(2),r.getDouble(3),r.getString(4),r.getString(5),r.getString(6)),itemCode,itemCode,itemCode,itemCode);
 }
 @Transactional public void adjustStock(OperationDtos.StockAdjustmentRequest d){
   if(d==null||blank(d.itemCode()))throw new IllegalArgumentException("Item code is required");
   ItemEntity item=items.findByItemCodeForUpdate(d.itemCode()).orElseThrow(()->new IllegalArgumentException("Item not found: "+d.itemCode()));
   double current=n(item.getOpeningStock()); double quantity=d.quantity(); if(!Double.isFinite(quantity)||quantity<0)throw new IllegalArgumentException("Quantity must be a finite non-negative number");
   String type=up(d.type()); double delta=switch(type){case "ADD"->quantity;case "REMOVE"->-quantity;case "SET"->quantity-current;default->throw new IllegalArgumentException("Invalid adjustment type");};
   if(current+delta<-.0001)throw new IllegalArgumentException("Adjustment would make stock negative");
   item.setOpeningStock(Math.max(0,current+delta));
   jdbc.update("INSERT INTO stock_adjustment(item_code,adjustment_date,adjustment_type,quantity,reason,reference_no,created_by,created_at) VALUES(?,?,?,?,?,?,?,?)",d.itemCode(),BusinessClock.today(),type,delta,d.reason(),d.referenceNo(),CurrentUser.require().username(),BusinessClock.nowUtcText());
 }
 @Transactional(readOnly=true) public OperationDtos.FinanceMetrics financeMetrics(){double cr=0,db=0,em=0,ey=0,pendAmt=0;long bc=0,dc=0,wc=0,ec=0,pend=0;Map<String,Double> cat=new HashMap<>();YearMonth ym=BusinessClock.currentMonth();int y=BusinessClock.today().getYear();for(var e:finance.findAll()){String t=up(e.getVoucherType());LocalDate d=date(e.getVoucherDate());double a=n(e.getAmount());if(t.equals("BANK DEPOSIT")){cr+=a;dc++;if(d!=null&&YearMonth.from(d).equals(ym))bc++;}else if(t.equals("BANK WITHDRAWAL")){db+=a;wc++;if(d!=null&&YearMonth.from(d).equals(ym))bc++;}else if(t.equals("EXPENSE")){if(d!=null&&YearMonth.from(d).equals(ym)){em+=a;ec++;}if(d!=null&&d.getYear()==y)ey+=a;String c=blank(e.getCategory())?"Other":e.getCategory();cat.merge(c,a,Double::sum);if(n(e.getReconciled())==0){pend++;pendAmt+=a;}}}var top=cat.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);return new OperationDtos.FinanceMetrics(cr-db,cr,db,bc,dc,wc,em,ey,ec,top==null?"No expenses":top.getKey(),top==null?0:top.getValue(),pend,pendAmt);}

 private boolean sameSaleLines(int salesId,List<OperationDtos.LineDto> incoming){
  List<SalesLineEntity> existing=salesLines.findBySalesIdOrderByIdAsc(salesId);
  List<OperationDtos.LineDto> requested=incoming==null?List.of():incoming;
  if(existing.size()!=requested.size())return false;
  for(int i=0;i<existing.size();i++){
   SalesLineEntity a=existing.get(i); OperationDtos.LineDto b=requested.get(i);
   if(b==null||!Objects.equals(up(a.getItemCode()),up(b.itemCode()))||!sameNumber(a.getQuantity(),b.quantity())||!sameNumber(a.getRate(),b.rate())||!sameNumber(a.getDiscountPercent(),b.discountPercent())||!sameNumber(a.getDiscountAmount(),b.discountAmount())||!sameNumber(a.getGstPercent(),b.gstPercent())||!sameNumber(a.getLineTotal(),b.totalAmount()))return false;
  }
  return true;
 }
 private boolean samePurchaseLines(int purchaseId,List<OperationDtos.LineDto> incoming){
  List<PurchaseLineEntity> existing=purchaseLines.findByPurchaseIdOrderByIdAsc(purchaseId);
  List<OperationDtos.LineDto> requested=incoming==null?List.of():incoming;
  if(existing.size()!=requested.size())return false;
  for(int i=0;i<existing.size();i++){
   PurchaseLineEntity a=existing.get(i); OperationDtos.LineDto b=requested.get(i);
   if(b==null||!Objects.equals(up(a.getItemCode()),up(b.itemCode()))||!sameNumber(a.getQuantity(),b.quantity())||!sameNumber(a.getRate(),b.rate())||!sameNumber(a.getDiscountPercent(),b.discountPercent())||!sameNumber(a.getDiscountAmount(),b.discountAmount())||!sameNumber(a.getGstPercent(),b.gstPercent())||!sameNumber(a.getLineTotal(),b.totalAmount()))return false;
  }
  return true;
 }
 private boolean sameSaleCharges(int salesId,List<OperationDtos.ChargeDto> incoming){
  List<SalesChargeEntity> existing=salesCharges.findBySalesIdOrderBySequenceNoAscIdAsc(salesId);
  List<OperationDtos.ChargeDto> requested=incoming==null?List.of():incoming;
  if(existing.size()!=requested.size())return false;
  for(int i=0;i<existing.size();i++){
   SalesChargeEntity a=existing.get(i); OperationDtos.ChargeDto b=requested.get(i);
   if(b==null||!Objects.equals(up(a.getChargeName()),up(b.chargeType()))||!sameNumber(a.getAmount(),b.amount())||!Objects.equals(Boolean.TRUE.equals(a.getTaxable()),b.taxable())||!sameNumber(a.getGstPercent(),b.taxable()?b.gstPercent():0))return false;
  }
  return true;
 }
 private static boolean sameNumber(Number a,double b){return money(n(a)).compareTo(money(b))==0;}
 private void replaceSaleLines(int id,List<OperationDtos.LineDto> ls,boolean skipStock){validateDocumentLines(ls,"Sale");salesLines.deleteBySalesId(id);if(ls==null)return;for(var d:ls){if(!skipStock)changeStock(d.itemCode(),-d.quantity(),true);SalesLineEntity l=new SalesLineEntity();l.setSalesId(id);l.setItemCode(d.itemCode());l.setQuantity(d.quantity());l.setRate(d.rate());l.setDiscountPercent(d.discountPercent());l.setDiscountAmount(d.discountAmount());l.setGstPercent(d.gstPercent());l.setLineTotal(d.totalAmount());salesLines.save(l);}}
 private void replaceSaleCharges(int salesId,List<OperationDtos.ChargeDto> charges){salesCharges.deleteBySalesId(salesId);int sequence=1;for(var d:charges){SalesChargeEntity e=new SalesChargeEntity();e.setSalesId(salesId);e.setSequenceNo(sequence++);e.setChargeCode(d.chargeType().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_"));e.setChargeName(d.chargeType().trim());e.setAmount(money(d.amount()));e.setTaxable(d.taxable());e.setGstPercent(money(d.taxable()?d.gstPercent():0));salesCharges.save(e);}}
 private List<OperationDtos.ChargeDto> normalizedCharges(OperationDtos.SaleDto d){List<OperationDtos.ChargeDto> input=d.charges()==null?List.of():d.charges();if(input.isEmpty()&&d.chargeAmount()>0)input=List.of(new OperationDtos.ChargeDto(blank(d.chargeType())?"Charges":d.chargeType(),d.chargeAmount(),false,0));if(input.size()>2)throw new IllegalArgumentException("A sales invoice supports a maximum of two additional charges");List<OperationDtos.ChargeDto> out=new ArrayList<>();Set<String> names=new HashSet<>();for(var c:input){if(c==null||blank(c.chargeType()))throw new IllegalArgumentException("Charge type is required");if(!Double.isFinite(c.amount())||c.amount()<=0)throw new IllegalArgumentException("Charge amount must be greater than zero");if(!Double.isFinite(c.gstPercent())||c.gstPercent()<0||c.gstPercent()>100)throw new IllegalArgumentException("Charge GST percent must be between 0 and 100");String key=up(c.chargeType());if(!names.add(key))throw new IllegalArgumentException("The same charge type cannot be selected twice");out.add(new OperationDtos.ChargeDto(c.chargeType().trim(),money(c.amount()).doubleValue(),c.taxable(),c.taxable()?money(c.gstPercent()).doubleValue():0));}return List.copyOf(out);}
 private void restoreSaleStock(int id){for(var l:salesLines.findBySalesIdOrderByIdAsc(id))changeStock(l.getItemCode(),n(l.getQuantity()),false);}
 private void replacePurchaseLines(int id,List<OperationDtos.LineDto> ls,boolean skipStock){validateDocumentLines(ls,"Purchase");purchaseLines.deleteByPurchaseId(id);if(ls==null)return;for(var d:ls){if(!skipStock)changeStock(d.itemCode(),d.quantity(),false);PurchaseLineEntity l=new PurchaseLineEntity();l.setPurchaseId(id);l.setItemCode(d.itemCode());l.setQuantity(d.quantity());l.setRate(d.rate());l.setDiscountPercent(d.discountPercent());l.setDiscountAmount(d.discountAmount());l.setGstPercent(d.gstPercent());l.setLineTotal(d.totalAmount());purchaseLines.save(l);}}
 private void restorePurchaseStock(int id){for(var l:purchaseLines.findByPurchaseIdOrderByIdAsc(id))changeStock(l.getItemCode(),-n(l.getQuantity()),true);}
 private void postPurchaseStock(int id){for(var l:purchaseLines.findByPurchaseIdOrderByIdAsc(id))changeStock(l.getItemCode(),n(l.getQuantity()),false);}
 private double recordedPaymentTotal(String type,int id){Double value=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE UPPER(document_type)=? AND document_id=?",Double.class,type,id);return n(value);}
 private boolean hasActiveReturn(String type,String invoice){Long count=jdbc.queryForObject("SELECT COUNT(*) FROM return_register WHERE (CASE WHEN UPPER(return_type)='SALE RETURN' THEN 'SALES RETURN' ELSE UPPER(return_type) END)=? AND invoice_no=? AND UPPER(COALESCE(status,'PENDING'))<>'CANCELLED'",Long.class,type,invoice);return count!=null&&count>0;}
 private static String normalizePurchaseStatus(String status){String value=up(status);return value.isBlank()?"COMPLETED":value;}
 private static boolean isInactivePurchase(String status){String value=up(status);return value.equals("DELETED")||value.equals("CANCELLED");}
 private static boolean shouldPostPurchaseInventory(String status){return !"DRAFT".equals(normalizePurchaseStatus(status))&&!isInactivePurchase(status);}
@Transactional public void applyStockDelta(String code,double delta,boolean enforce){changeStock(code,delta,enforce);}
 private void validateDocumentLines(List<OperationDtos.LineDto> ls,String document){if(ls==null)return;for(var d:ls){if(d==null)throw new IllegalArgumentException(document+" contains an empty item line");if(blank(d.itemCode()))throw new IllegalArgumentException(document+" item code is required");if(!Double.isFinite(d.quantity())||d.quantity()<=0)throw new IllegalArgumentException(document+" quantity for "+d.itemCode()+" must be a finite number greater than zero");if(!Double.isFinite(d.rate())||d.rate()<0)throw new IllegalArgumentException(document+" rate for "+d.itemCode()+" must be a finite non-negative number");if(!Double.isFinite(d.totalAmount())||d.totalAmount()<0)throw new IllegalArgumentException(document+" line total for "+d.itemCode()+" must be a finite non-negative number");}}
 private void changeStock(String code,double delta,boolean enforce){if(code==null||code.isBlank())throw new IllegalArgumentException("Item code is required");if(!Double.isFinite(delta))throw new IllegalArgumentException("Stock quantity must be finite");ItemEntity i=items.findByItemCodeForUpdate(code).orElseThrow(()->new IllegalArgumentException("Item not found: "+code));double now=n(i.getOpeningStock());double next=now+delta;if(enforce&&next<-.0001)throw new IllegalStateException("Insufficient stock for item "+code);i.setOpeningStock(Math.max(0,next));}

private OperationDtos.SaleDto saleDto(SalesHeaderEntity h,boolean detail){var p=h.getCustomer();List<OperationDtos.LineDto> ls=detail?salesLines.findBySalesIdOrderByIdAsc(h.getId()).stream().map(this::line).toList():List.of();double qty=detail?ls.stream().mapToDouble(OperationDtos.LineDto::quantity).sum():salesLines.findBySalesIdOrderByIdAsc(h.getId()).stream().mapToDouble(x->n(x.getQuantity())).sum();List<OperationDtos.ChargeDto> charges=salesCharges.findBySalesIdOrderBySequenceNoAscIdAsc(h.getId()).stream().map(x->new OperationDtos.ChargeDto(x.getChargeName(),n(x.getAmount()),Boolean.TRUE.equals(x.getTaxable()),n(x.getGstPercent()))).toList();if(charges.isEmpty()&&n(h.getChargeAmount())>0)charges=List.of(new OperationDtos.ChargeDto(blank(h.getChargeType())?"Charges":h.getChargeType(),n(h.getChargeAmount()),false,0));return new OperationDtos.SaleDto(h.getId(),h.getInvoiceNo(),dateOnly(h.getInvoiceDate()),party(p),n(h.getSubtotal()),n(h.getDiscountAmount()),n(h.getGstAmount()),n(h.getTotalAmount()),h.getRemarks(),h.getCreatedAt(),n(h.getEmailSent())!=0,dateOnly(h.getDueDate()),n(h.getPaidAmount()),h.getPaymentStatus(),n(h.getWhatsappSent())!=0,h.getInvoiceType(),h.getSalesperson(),h.getSource(),h.getNotes(),h.getDeliveryAddress(),h.getPaymentTerms(),h.getTransporter(),h.getReferenceNo(),dateOnly(h.getPoDate()),h.getBillingAddress(),h.getGstType(),h.getDoorDelivery(),h.getVehicleNumber(),h.getContactPerson(),h.getTransportNote(),customerPoOrderNo(h.getOrderNo()),h.getGstin(),h.getBillingGstin(),h.getDeliveryGstin(),!Boolean.FALSE.equals(h.getSameAsBilling()),h.getTransporterGstin(),h.getChargeType(),n(h.getChargeAmount()),h.getContactPersonMobile(),h.getDocumentStatus(),h.getAttachmentPath(),qty,charges,ls);}
 private OperationDtos.PurchaseDto purchaseDto(PurchaseHeaderEntity h,boolean detail){return purchaseDto(h,detail,effectivePurchasePaid(h,0d));}
 private OperationDtos.PurchaseDto purchaseDto(PurchaseHeaderEntity h,boolean detail,double paid){var p=h.getSupplier();List<OperationDtos.LineDto> ls=detail?purchaseLines.findByPurchaseIdOrderByIdAsc(h.getId()).stream().map(this::line).toList():List.of();double qty=detail?ls.stream().mapToDouble(OperationDtos.LineDto::quantity).sum():purchaseLines.findByPurchaseIdOrderByIdAsc(h.getId()).stream().mapToDouble(x->n(x.getQuantity())).sum();double total=n(h.getTotalAmount()),effective=Math.min(total,Math.max(0,paid));String ps=up(h.getPaymentStatus());String status=total>0&&effective+0.0001>=total?"PAID":effective>0.0001?"PARTIAL":(blank(ps)?"PENDING":ps);return new OperationDtos.PurchaseDto(h.getId(),h.getInvoiceNo(),dateOnly(h.getInvoiceDate()),party(p),n(h.getSubtotal()),n(h.getGstAmount()),total,h.getRemarks(),h.getCreatedAt(),n(h.getEmailSent())!=0,dateOnly(h.getDueDate()),effective,status,h.getDocumentStatus(),h.getWarehouse(),h.getPaymentTerms(),h.getCurrency(),h.getReferenceNo(),h.getGstTreatment(),h.getTransporter(),h.getLrAwbNo(),h.getDiscountType(),n(h.getDiscountAmount()),h.getAttachmentPath(),h.getCreatedBy(),dateOnly(h.getDeliveryDate()),qty,ls);}
 private Map<Integer,Double> recordedPurchasePayments(){Map<Integer,Double> out=new HashMap<>();jdbc.query("SELECT document_id,COALESCE(SUM(amount),0) FROM payment_record WHERE UPPER(document_type)='PURCHASE' GROUP BY document_id",r->out.put(r.getInt(1),r.getDouble(2)));return out;}
 private double effectivePurchasePaid(PurchaseHeaderEntity h,double recorded){double total=n(h.getTotalAmount()),paid=Math.max(n(h.getPaidAmount()),recorded);String ps=up(h.getPaymentStatus());if(total>0&&Set.of("PAID","SETTLED").contains(ps))paid=Math.max(paid,total);return total>0?Math.min(total,paid):Math.max(0,paid);}
 private OperationDtos.LineDto line(SalesLineEntity l){String desc=items.findByItemCode(l.getItemCode()).map(ItemEntity::getDescription).orElse(null);return new OperationDtos.LineDto(l.getItemCode(),desc,n(l.getQuantity()),n(l.getRate()),n(l.getDiscountPercent()),n(l.getDiscountAmount()),n(l.getGstPercent()),n(l.getLineTotal()));} private OperationDtos.LineDto line(PurchaseLineEntity l){String desc=items.findByItemCode(l.getItemCode()).map(ItemEntity::getDescription).orElse(null);return new OperationDtos.LineDto(l.getItemCode(),desc,n(l.getQuantity()),n(l.getRate()),n(l.getDiscountPercent()),n(l.getDiscountAmount()),n(l.getGstPercent()),n(l.getLineTotal()));}
 private OperationDtos.PartyDto party(PartyEntity p){return p==null?null:new OperationDtos.PartyDto(p.getId(),p.getPartyCode(),p.getName(),p.getEmail(),p.getPhone(),p.getGstin(),p.getAddress());}
private void copySale(OperationDtos.SaleDto d,SalesHeaderEntity h){h.setInvoiceNo(d.invoiceNo());h.setInvoiceDate(d.invoiceDate());h.setCustomer(parties.findById(req(d.customer()==null?null:d.customer().id())).orElseThrow(()->new IllegalArgumentException("Customer not found")));h.setSubtotal(d.subtotal());h.setDiscountAmount(d.discountAmount());h.setGstAmount(d.gstAmount());h.setTotalAmount(d.totalAmount());h.setRemarks(d.remarks());h.setDueDate(d.dueDate());h.setPaidAmount(d.paidAmount());h.setPaymentStatus(d.paymentStatus());h.setWhatsappSent(d.whatsappSent()?1:0);h.setInvoiceType(d.invoiceType());h.setSalesperson(d.salesperson());h.setSource(d.source());h.setNotes(d.notes());h.setDeliveryAddress(d.deliveryAddress());h.setPaymentTerms(d.paymentTerms());h.setTransporter(d.transporter());h.setReferenceNo(d.referenceNo());h.setPoDate(d.poDate());h.setBillingAddress(d.billingAddress());h.setGstType(d.gstType());h.setDoorDelivery(d.doorDelivery());h.setVehicleNumber(d.vehicleNumber());h.setContactPerson(d.contactPerson());h.setTransportNote(d.transportNote());h.setOrderNo(customerPoOrderNo(d.orderNo()));h.setGstin(d.gstin());h.setBillingGstin(d.billingGstin());h.setDeliveryGstin(d.deliveryGstin());h.setSameAsBilling(d.sameAsBilling());h.setTransporterGstin(d.transporterGstin());List<OperationDtos.ChargeDto> charges=normalizedCharges(d);OperationDtos.ChargeDto first=charges.isEmpty()?null:charges.get(0);h.setChargeType(first==null?"":first.chargeType());h.setChargeAmount(first==null?0:first.amount());h.setContactPersonMobile(d.contactPersonMobile());h.setDocumentStatus(d.documentStatus());}
 private void copyPurchase(OperationDtos.PurchaseDto d,PurchaseHeaderEntity h){h.setInvoiceNo(d.invoiceNo());h.setInvoiceDate(d.invoiceDate());h.setSupplier(parties.findById(req(d.supplier()==null?null:d.supplier().id())).orElseThrow(()->new IllegalArgumentException("Supplier not found")));h.setSubtotal(d.subtotal());h.setGstAmount(d.gstAmount());h.setTotalAmount(d.totalAmount());h.setRemarks(d.remarks());h.setDueDate(d.dueDate());h.setPaidAmount(d.paidAmount());h.setPaymentStatus(d.paymentStatus());h.setDocumentStatus(d.documentStatus());h.setEmailSent(d.emailSent()?1:0);h.setWarehouse(d.warehouse());h.setPaymentTerms(d.paymentTerms());h.setCurrency(d.currency());h.setReferenceNo(d.referenceNo());h.setGstTreatment(d.gstTreatment());h.setTransporter(d.transporter());h.setLrAwbNo(d.lrAwbNo());h.setDiscountType(d.discountType());h.setDiscountAmount(d.discountAmount());if(h.getId()==null)h.setCreatedBy(CurrentUser.require().username());h.setDeliveryDate(d.deliveryDate());}
 private void copyFinance(OperationDtos.FinanceDto d,FinanceRegisterEntity e){if(d.voucherNo()!=null)e.setVoucherNo(d.voucherNo());e.setVoucherType(d.voucherType());e.setVoucherDate(d.voucherDate());e.setPartyId(d.partyId());e.setCategory(d.category());e.setReferenceNo(d.referenceNo());e.setAmount(d.amount());e.setPaymentMode(d.paymentMode());e.setNotes(d.notes());e.setAccountName(d.accountName());if(d.billPath()!=null)e.setBillPath(d.billPath());e.setReconciled(d.reconciled()?1:0);}
 private OperationDtos.FinanceDto financeDto(FinanceRegisterEntity e){Long statementId=null;String targetType=null;Integer targetId=null;String documentNo=null;var active=reconciliationAllocations.findByFinanceEntryIdAndReversedAtIsNull(e.getId());if(!active.isEmpty()){var a=active.get(0);statementId=a.getStatementTransactionId();if(active.size()>1){targetType="MULTIPLE";documentNo="Multiple ("+active.size()+")";}else{targetType=up(a.getTargetType());targetId=a.getTargetId();if("SALE".equals(targetType)&&targetId!=null)documentNo=sales.findById(targetId).map(SalesHeaderEntity::getInvoiceNo).orElse(null);else if("PURCHASE".equals(targetType)&&targetId!=null)documentNo=purchases.findById(targetId).map(PurchaseHeaderEntity::getInvoiceNo).orElse(null);else if("EXPENSE".equals(targetType))documentNo=e.getVoucherNo();}}return new OperationDtos.FinanceDto(e.getId(),e.getVoucherNo(),e.getVoucherType(),dateOnly(e.getVoucherDate()),e.getPartyId(),e.getCategory(),e.getReferenceNo(),n(e.getAmount()),e.getPaymentMode(),e.getNotes(),e.getAccountName(),e.getBillPath(),n(e.getReconciled())!=0,statementId,targetType,targetId,documentNo);}
 public String nextConfiguredReference(String lookupCode,String fallback,List<String> existing){return configuredNext(lookupCode,fallback,existing);}
 private String configuredNext(String lookupCode,String fallback,List<String> existing){
  String fmt=fallback;
  var category=categories.findByCategoryCode("REFERENCE_FORMAT").orElse(null);
  if(category!=null&&category.getActive()!=null&&category.getActive()!=0){
   for(var value:lookups.findByLookupTypeAndActiveTrueOrderByDisplayOrderAscLookupValueAsc(category.getCategoryName())){
    if(value.getLookupCode()!=null&&value.getLookupCode().equalsIgnoreCase(lookupCode)&&!blank(value.getLookupValue())){fmt=value.getLookupValue().trim();break;}
   }
  }
  return nextFromFormat(fmt,existing==null?List.of():existing);
 }
 private String nextFromFormat(String fmt,List<String> existing){LocalDate t=BusinessClock.today();String dated=fmt.replace("DD-MM-YYYY",t.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))).replace("DD/MM/YYYY",t.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).replace("YYYY-MM-DD",t.toString()).replace("YYYY",String.valueOf(t.getYear())).replace("YY",String.format(Locale.ROOT,"%02d",t.getYear()%100));Matcher m=Pattern.compile("X{2,}").matcher(dated);if(!m.find()){dated+="/XXXX";m=Pattern.compile("X{2,}").matcher(dated);m.find();}int w=m.end()-m.start();String pre=dated.substring(0,m.start()),suf=dated.substring(m.end());int max=0;for(String x:existing){if(x!=null&&x.startsWith(pre)&&x.endsWith(suf)){try{max=Math.max(max,Integer.parseInt(x.substring(pre.length(),x.length()-suf.length())));}catch(Exception ignored){}}}return pre+String.format(Locale.ROOT,"%0"+w+"d",max+1)+suf;}
 private static int req(Integer v){if(v==null||v<=0)throw new IllegalArgumentException("Required id missing");return v;} private static double n(Number v){return v==null?0:v.doubleValue();} private static String up(String v){return v==null?"":v.trim().toUpperCase(Locale.ROOT);} private static boolean blank(String v){return v==null||v.isBlank();}
 private static String customerPoOrderNo(String value){if(blank(value))return null;String v=value.trim();return v.matches("(?i)^PO/\\d{2}-\\d{2}-\\d{4}/\\d{4}$")?null:v;}
 private static BigDecimal money(double value){return BigDecimal.valueOf(value).setScale(2,RoundingMode.HALF_UP);}
 private static String dateOnly(String v){if(v==null)return null;return v.length()>=10?v.substring(0,10):v;} private static LocalDate date(String v){try{return LocalDate.parse(dateOnly(v));}catch(Exception e){return null;}}
}
