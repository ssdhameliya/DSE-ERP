package org.example.server.operations;

import org.example.server.util.BusinessClock;
import org.example.server.insights.BusinessKpiPolicy;
import org.example.server.insights.CashPositionService;
import org.example.server.audit.AuditService;
import org.example.server.web.ConcurrentEditException;
import org.example.shared.DocumentCalculationEngine;
import org.example.shared.ReferenceFormatRules;

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
 private final SalesHeaderRepository sales; private final SalesLineRepository salesLines; private final SalesChargeRepository salesCharges; private final PurchaseHeaderRepository purchases; private final PurchaseLineRepository purchaseLines; private final PurchaseChargeRepository purchaseCharges; private final PartyRepository parties; private final ItemRepository items; private final LookupRepository lookups; private final MasterCategoryRepository categories; private final FinanceRegisterRepository finance; private final BankReconciliationAllocationRepository reconciliationAllocations; private final JpaNativeRepository jdbc; private final AuditService audit; private final CashPositionService cashPosition;
 public BusinessOperationsService(SalesHeaderRepository s,SalesLineRepository sl,SalesChargeRepository sc,PurchaseHeaderRepository p,PurchaseLineRepository pl,PurchaseChargeRepository pc,PartyRepository pa,ItemRepository i,LookupRepository l,MasterCategoryRepository c,FinanceRegisterRepository f,BankReconciliationAllocationRepository ra,JpaNativeRepository jdbc,AuditService audit,CashPositionService cashPosition){sales=s;salesLines=sl;salesCharges=sc;purchases=p;purchaseLines=pl;purchaseCharges=pc;parties=pa;items=i;lookups=l;categories=c;finance=f;reconciliationAllocations=ra;this.jdbc=jdbc;this.audit=audit;this.cashPosition=cashPosition;}

 @Transactional(readOnly=true) public List<OperationDtos.SaleDto> sales(){
  Map<Integer,Double> quantities=saleQuantityTotals();
  Map<Integer,List<OperationDtos.ChargeDto>> charges=saleChargeSummaries();
  return sales.findAllByOrderByInvoiceDateDescIdDesc().stream()
    .filter(h -> !"DELETED".equalsIgnoreCase(h.getDocumentStatus()))
    .map(h->saleSummaryDto(h,quantities.getOrDefault(h.getId(),0d),charges.getOrDefault(h.getId(),List.of())))
    .toList();
 }
 @Transactional(readOnly=true) public OperationDtos.SalePage salesPage(int page,int size,String q,String invoice,String customer,String from,String to,String paymentStatus,String due,String mail,String whatsapp,String invoiceType,String documentStatus,Double minAmount,Double maxAmount){
  int safeSize=Math.max(10,Math.min(size,200)),safePage=Math.max(0,page);SqlWhere where=new SqlWhere("UPPER(COALESCE(h.document_status,''))<>'DELETED'");
  String global=trim(q),invoiceFilter=trim(invoice),customerFilter=trim(customer),fromDate=trim(from),toDate=trim(to),payment=up(paymentStatus),dueFilter=up(due),mailFilter=up(mail),whatsappFilter=up(whatsapp),typeFilter=up(invoiceType),docFilter=up(documentStatus);
  if(!blank(global))where.add("LOWER(CONCAT_WS(' ',COALESCE(h.invoice_no,''),COALESCE(p.name,''),COALESCE(p.phone,''),COALESCE(p.gstin,''))) LIKE ?","%"+global.toLowerCase(Locale.ROOT)+"%");
  if(!blank(invoiceFilter))where.add("LOWER(COALESCE(h.invoice_no,'')) LIKE ?","%"+invoiceFilter.toLowerCase(Locale.ROOT)+"%");
  if(!blank(customerFilter))where.add("LOWER(COALESCE(p.name,''))=LOWER(?)",customerFilter);
  String invoiceDate=sqlDate("h.invoice_date");
  String paymentJoin=" LEFT JOIN (SELECT document_id,COALESCE(SUM(amount),0) recorded_paid FROM payment_record WHERE UPPER(document_type)='SALE' GROUP BY document_id) pr ON pr.document_id=h.id ";
  String returnJoin=returnSettlementJoin("SALES RETURN");
  String effectivePaid=BusinessKpiPolicy.effectivePaid("h","pr"),baseBalance=BusinessKpiPolicy.outstanding("h","pr"),balance=currentBalanceSql(baseBalance),dueDate=currentDueDateSql(sqlDate("h.due_date"));
  String derivedStatus=currentPaymentStatusSql(effectivePaid,"h");
  if(!blank(fromDate))where.add(invoiceDate+">=TO_DATE(?,'YYYY-MM-DD')",fromDate);if(!blank(toDate))where.add(invoiceDate+"<=TO_DATE(?,'YYYY-MM-DD')",toDate);
  if(minAmount!=null&&Double.isFinite(minAmount))where.add("COALESCE(h.total_amount,0)>=?",minAmount);if(maxAmount!=null&&Double.isFinite(maxAmount))where.add("COALESCE(h.total_amount,0)<=?",maxAmount);
  if(!blank(payment)&&!"ALL".equals(payment)){if("OVERDUE".equals(payment))where.add(balance+">0.01 AND "+dueDate+"<CURRENT_DATE");else where.add("("+derivedStatus+")=?",payment);}
  if(!blank(dueFilter)&&!"ALL".equals(dueFilter)){where.add(balance+">0.01");switch(dueFilter){case "OVERDUE"->where.add(dueDate+"<CURRENT_DATE");case "DUE TODAY"->where.add(dueDate+"=CURRENT_DATE");case "NEXT 7 DAYS"->where.add(dueDate+" BETWEEN CURRENT_DATE AND CURRENT_DATE+7");case "NEXT 30 DAYS"->where.add(dueDate+" BETWEEN CURRENT_DATE AND CURRENT_DATE+30");default->{}}}
  if("SENT".equals(mailFilter))where.add("COALESCE(h.email_sent,0)<>0");else if("NOT SENT".equals(mailFilter))where.add("COALESCE(h.email_sent,0)=0");
  if("SENT".equals(whatsappFilter))where.add("COALESCE(h.whatsapp_sent,0)<>0");else if("NOT SENT".equals(whatsappFilter))where.add("COALESCE(h.whatsapp_sent,0)=0");
  if(!blank(typeFilter)&&!"ALL".equals(typeFilter))where.add("UPPER(COALESCE(h.invoice_type,''))=?",typeFilter);
  if(!blank(docFilter)&&!"ALL".equals(docFilter))where.add("UPPER(COALESCE(h.document_status,''))=?",docFilter);
  String join=" FROM sales_header h LEFT JOIN party_master p ON p.id=h.customer_id "+paymentJoin+returnJoin;
  long total=jdbc.queryForObject("SELECT COUNT(*)"+join+where.sql(),Long.class,where.args());int totalPages=total==0?0:(int)Math.ceil(total/(double)safeSize);if(totalPages>0&&safePage>=totalPages)safePage=totalPages-1;
  List<Integer> ids=jdbc.query("SELECT h.id"+join+where.sql()+" ORDER BY "+invoiceDate+" DESC NULLS LAST,h.id DESC LIMIT ? OFFSET ?",(r,i)->r.getInt(1),where.argsWith(safeSize,(long)safePage*safeSize));
  Map<Integer,SalesHeaderEntity> byId=new HashMap<>();sales.findAllById(ids).forEach(e->byId.put(e.getId(),e));Map<Integer,Double> quantities=saleQuantityTotals(ids);Map<Integer,List<OperationDtos.ChargeDto>> charges=saleChargeSummaries(ids);
  List<OperationDtos.SaleDto> rows=ids.stream().map(byId::get).filter(Objects::nonNull).map(h->saleSummaryDto(h,quantities.getOrDefault(h.getId(),0d),charges.getOrDefault(h.getId(),List.of()))).toList();
  String active=where.sql()+" AND "+BusinessKpiPolicy.salesActive("h");var totalsRow=jdbc.query("SELECT COUNT(*),COALESCE(SUM(h.total_amount),0),COALESCE(SUM("+effectivePaid+"),0),COALESCE(SUM("+balance+"),0)"+join+active,(r,i)->new OperationDtos.RegisterTotals(r.getLong(1),r.getDouble(2),r.getDouble(3),r.getDouble(4)),where.args()).getFirst();
  return new OperationDtos.SalePage(rows,safePage,safeSize,total,totalPages,totalsRow,salesMetrics(where),saleCustomerOptions());
 }

 @Transactional(readOnly=true) public OperationDtos.SaleDto sale(String invoice){return saleDto(sales.findByInvoiceNo(invoice).orElseThrow(()->new IllegalArgumentException("Sale not found: "+invoice)),true);}
 @Transactional(readOnly=true) public boolean saleExists(String invoiceNo){return sales.findByInvoiceNo(invoiceNo).isPresent();}

 @Transactional public OperationDtos.SaleDto saveSale(OperationDtos.SaleDto d){
  CurrentUser.requirePermission("SALES.CREATE","Create Sale");
  if(d==null)throw new IllegalArgumentException("Sale data is required");
  validateDocumentLines(d.lines(),"Sale");
  requireActivePartyReference(d.customer()==null?null:d.customer().id(),"CUSTOMER","Customer");
  validateActiveItems(d.lines(),"Sale");
  validateDocumentDate(d.invoiceDate(),"Sale invoice date");
  SalesHeaderEntity h=new SalesHeaderEntity();
  copySale(d,h);
  snapshotSaleParty(h);
  boolean imported="IMPORT".equalsIgnoreCase(trim(d.source()));
  if(imported&&!blank(d.invoiceNo())){
   if(sales.existsByInvoiceNo(d.invoiceNo()))throw new IllegalArgumentException("Imported Sales invoice already exists: "+d.invoiceNo());
   h.setInvoiceNo(d.invoiceNo().trim());
  }else h.setInvoiceNo(nextSalesInvoice());
  h.setPaidAmount(0d);h.setPaymentStatus("PENDING");
  h.setCreatedAt(BusinessClock.nowUtcText());
  h.setEmailSent(0);
  String requested="APPROVED";
  h.setRequestedDocumentStatus(requested);
  if(requiresAdminApproval()){
   h.setDocumentStatus("PENDING APPROVAL");h.setApprovalStatus("PENDING");h.setApprovalRequestedBy(CurrentUser.require().username());h.setApprovalRequestedAt(BusinessClock.nowUtcText());h.setInventoryPosted(false);
  }else{
   h.setDocumentStatus(requested);h.setApprovalStatus("APPROVED");h.setApprovedBy(CurrentUser.require().username());h.setApprovedAt(BusinessClock.nowUtcText());h.setInventoryPosted(true);
  }
  h=sales.save(h);
  replaceSaleLines(h.getId(),d.lines(),!Boolean.TRUE.equals(h.getInventoryPosted()));
  replaceSaleCharges(h.getId(),normalizedCharges(d));
  if("PENDING".equalsIgnoreCase(h.getApprovalStatus()))notifyApprovalRequired("SALE",h.getId(),h.getInvoiceNo());
  audit.log("SALE",h.getId(),"CREATED",h.getInvoiceNo());
  return saleDto(h,true);
 }
 @Transactional public OperationDtos.SaleDto updateSale(OperationDtos.SaleDto d){
  CurrentUser.requirePermission("SALES.EDIT","Edit Sale");
  if(d==null)throw new IllegalArgumentException("Sale data is required");
  validateDocumentLines(d.lines(),"Sale");
  validateDocumentDate(d.invoiceDate(),"Sale invoice date");
  SalesHeaderEntity h=sales.findByInvoiceNoForUpdate(d.invoiceNo()).orElseThrow(()->new IllegalArgumentException("Sale not found: "+d.invoiceNo()));
  assertVersion(d.rowVersion(),h.getRowVersion(),"Sale "+h.getInvoiceNo());
  String existingStatus=up(h.getDocumentStatus());
  if(Set.of("DELETED","CANCELLED","REJECTED").contains(existingStatus))throw new IllegalStateException("Deleted, cancelled or rejected Sales invoices cannot be edited.");
  List<OperationDtos.ChargeDto> newCharges=normalizedCharges(d);
  boolean linesChanged=!sameSaleLines(h.getId(),d.lines());
  Integer requestedCustomerId=req(d.customer()==null?null:d.customer().id());
  Integer currentCustomerId=h.getCustomer()==null?null:h.getCustomer().getId();
  requirePartyReference(requestedCustomerId,"CUSTOMER","Customer",!Objects.equals(currentCustomerId,requestedCustomerId));
  if(linesChanged)validateActiveItems(d.lines(),"Sale");
  boolean chargesChanged=!sameSaleCharges(h.getId(),newCharges);
  // Never trust client-computed totals for concurrency/business-rule decisions.
  // Recompute from lines + charges using the same server-owned canonical engine used by copySale().
  DocumentCalculationEngine.Totals requestedTotals=documentTotals(d.lines(),newCharges,blank(d.gstType())?"GST":d.gstType());
  boolean totalsChanged=!sameNumber(h.getSubtotal(),requestedTotals.itemTaxable())||!sameNumber(h.getGstAmount(),requestedTotals.taxAmount())||!sameNumber(h.getTotalAmount(),requestedTotals.grandTotal())||!sameNumber(h.getDiscountAmount(),requestedTotals.discountAmount());
  if(hasActiveReturn("SALES RETURN",h.getInvoiceNo())&&(linesChanged||totalsChanged))throw new IllegalStateException("A Sale with an active Sales Return cannot change items or financial totals. Reverse/cancel the return first.");
  double recordedPaid=recordedPaymentTotal("SALE",h.getId());
  if((linesChanged||chargesChanged||totalsChanged)&&(n(h.getPaidAmount())>.0001||recordedPaid>.0001||Set.of("PAID","SETTLED","PARTIAL").contains(up(h.getPaymentStatus()))))throw new IllegalStateException("A paid or partially paid Sale cannot change items or financial totals. Use Sales Return / payment reversal first.");

  Double paidAmount=h.getPaidAmount(); String paymentStatus=h.getPaymentStatus(); Integer emailSent=h.getEmailSent(); Integer whatsappSent=h.getWhatsappSent();
  String documentStatus=h.getDocumentStatus(),createdAt=h.getCreatedAt(),source=h.getSource(),invoiceType=h.getInvoiceType();
  Boolean inventoryPosted=h.getInventoryPosted(); String approvalStatus=h.getApprovalStatus(),requestedStatus=h.getRequestedDocumentStatus();
  String approvalRequestedBy=h.getApprovalRequestedBy(),approvalRequestedAt=h.getApprovalRequestedAt(),approvedBy=h.getApprovedBy(),approvedAt=h.getApprovedAt(),rejectionReason=h.getRejectionReason();

  Integer priorCustomerId=h.getCustomer()==null?null:h.getCustomer().getId();
  if(linesChanged&&Boolean.TRUE.equals(inventoryPosted))restoreSaleStock(h.getId());
  copySale(d,h);
  if(!Objects.equals(priorCustomerId,h.getCustomer()==null?null:h.getCustomer().getId())||blank(h.getCustomerNameSnapshot()))snapshotSaleParty(h);
  h.setPaidAmount(paidAmount);h.setPaymentStatus(paymentStatus);h.setEmailSent(emailSent);h.setWhatsappSent(whatsappSent);h.setDocumentStatus(documentStatus);h.setCreatedAt(createdAt);h.setSource(source);h.setInvoiceType(invoiceType);
  h.setInventoryPosted(inventoryPosted);h.setApprovalStatus(approvalStatus);h.setRequestedDocumentStatus(requestedStatus);h.setApprovalRequestedBy(approvalRequestedBy);h.setApprovalRequestedAt(approvalRequestedAt);h.setApprovedBy(approvedBy);h.setApprovedAt(approvedAt);h.setRejectionReason(rejectionReason);
  if(linesChanged)replaceSaleLines(h.getId(),d.lines(),!Boolean.TRUE.equals(inventoryPosted));
  if(chargesChanged)replaceSaleCharges(h.getId(),newCharges);
  sales.flush();
  audit.log("SALE",h.getId(),"UPDATED",h.getInvoiceNo());
  return saleDto(h,true);
 }
 @Transactional public OperationDtos.SaleDto duplicateSale(int id){
  CurrentUser.requirePermission("SALES.CREATE","Duplicate Sale");
  SalesHeaderEntity source=sales.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Sale not found: "+id));
  String status=up(source.getDocumentStatus());
  if(Set.of("DELETED","CANCELLED","REJECTED").contains(status))throw new IllegalStateException("Deleted, cancelled or rejected Sales invoices cannot be duplicated.");
  OperationDtos.SaleDto d=saleDto(source,true);
  OperationDtos.SaleDto request=new OperationDtos.SaleDto(null,null,BusinessClock.today().toString(),d.customer(),d.subtotal(),d.discountAmount(),d.gstAmount(),d.totalAmount(),"Duplicated from "+source.getInvoiceNo(),null,false,saleDueDate(BusinessClock.today(),d.paymentTerms()).toString(),0,"PENDING",false,d.invoiceType(),d.salesperson(),"DUPLICATE",d.notes(),d.deliveryAddress(),d.paymentTerms(),d.transporter(),d.referenceNo(),d.poDate(),d.billingAddress(),d.gstType(),d.doorDelivery(),d.vehicleNumber(),d.contactPerson(),d.transportNote(),null,d.gstin(),d.billingGstin(),d.deliveryGstin(),d.sameAsBilling(),d.transporterGstin(),d.chargeType(),d.chargeAmount(),d.contactPersonMobile(),"PENDING",null,d.quantity(),d.charges(),d.lines(),0);
  OperationDtos.SaleDto created=saveSale(request);
  audit.log("SALE",created.id(),"DUPLICATED","From "+source.getInvoiceNo());
  return created;
 }
 @Transactional public void deleteSale(String invoice){CurrentUser.requirePermission("SALES.DELETE","Delete Sale");SalesHeaderEntity h=sales.findByInvoiceNoForUpdate(invoice).orElseThrow(()->new IllegalArgumentException("Sale not found: "+invoice));assertDocumentHasNoPayments("SALE",h.getId(),n(h.getPaidAmount()),h.getPaymentStatus(),"deleted");if(hasActiveReturn("SALES RETURN",h.getInvoiceNo()))throw new IllegalStateException("A Sale with an active Sales Return cannot be deleted. Reverse/cancel the return first.");if(Boolean.TRUE.equals(h.getInventoryPosted())){restoreSaleStock(h.getId());h.setInventoryPosted(false);}h.setDocumentStatus("DELETED");audit.log("SALE",h.getId(),"DELETED",h.getInvoiceNo());}
 @Transactional public void cancelSale(String invoice){CurrentUser.requirePermission("SALES.EDIT","Cancel Sale");SalesHeaderEntity h=sales.findByInvoiceNoForUpdate(invoice).orElseThrow(()->new IllegalArgumentException("Sale not found: "+invoice));assertDocumentHasNoPayments("SALE",h.getId(),n(h.getPaidAmount()),h.getPaymentStatus(),"cancelled");String status=up(h.getDocumentStatus());if("DELETED".equals(status))throw new IllegalStateException("Deleted Sales invoices cannot be cancelled.");if("CANCELLED".equals(status))return;if(hasActiveReturn("SALES RETURN",h.getInvoiceNo()))throw new IllegalStateException("A Sale with an active Sales Return cannot be cancelled. Reverse/cancel the return first.");if(Boolean.TRUE.equals(h.getInventoryPosted())){restoreSaleStock(h.getId());h.setInventoryPosted(false);}h.setDocumentStatus("CANCELLED");audit.log("SALE",h.getId(),"CANCELLED",h.getInvoiceNo());}
 @Transactional public void markSaleEmail(int id){CurrentUser.requirePermission("SALES.EDIT","Update Sale email status");SalesHeaderEntity h=sales.findById(id).orElseThrow();h.setEmailSent(1);audit.log("SALE",h.getId(),"EMAIL_SENT",h.getInvoiceNo());}
 @Transactional public String nextSalesInvoice(){return configuredNextAtomic("REF_SALES","IN/DD-MM-YYYY/XXXX",()->sales.findAll().stream().map(SalesHeaderEntity::getInvoiceNo).filter(Objects::nonNull).toList());}
 @Transactional(readOnly=true) public String previewSalesInvoice(){return configuredPreviewAtomic("REF_SALES","IN/DD-MM-YYYY/XXXX",()->sales.findAll().stream().map(SalesHeaderEntity::getInvoiceNo).filter(Objects::nonNull).toList());}

 @Transactional(readOnly=true) public List<OperationDtos.PurchaseDto> purchases(){
  Map<Integer,Double> paid=recordedPurchasePayments();
  Map<Integer,Double> quantities=purchaseQuantityTotals();
  Map<Integer,List<OperationDtos.ChargeDto>> charges=purchaseChargeSummaries();
  return purchases.findAllByOrderByInvoiceDateDescIdDesc().stream()
    .filter(h -> !"DELETED".equalsIgnoreCase(h.getDocumentStatus()))
    .map(h->purchaseSummaryDto(h,effectivePurchasePaid(h,paid.getOrDefault(h.getId(),0d)),quantities.getOrDefault(h.getId(),0d),charges.getOrDefault(h.getId(),List.of())))
    .toList();
 }
 @Transactional(readOnly=true) public OperationDtos.PurchasePage purchasesPage(int page,int size,String q,String supplier,String from,String to,String paymentStatus,String due,String mail,String documentStatus){
  int safeSize=Math.max(10,Math.min(size,200)),safePage=Math.max(0,page);SqlWhere where=new SqlWhere("UPPER(COALESCE(h.document_status,''))<>'DELETED'");String global=trim(q),supplierFilter=trim(supplier),fromDate=trim(from),toDate=trim(to),payment=up(paymentStatus),dueFilter=up(due),mailFilter=up(mail),docFilter=up(documentStatus);
  String paymentJoin=" LEFT JOIN (SELECT document_id,COALESCE(SUM(amount),0) recorded_paid FROM payment_record WHERE UPPER(document_type)='PURCHASE' GROUP BY document_id) pr ON pr.document_id=h.id ";String returnJoin=returnSettlementJoin("PURCHASE RETURN");
  String join=" FROM purchase_header h LEFT JOIN party_master p ON p.id=h.supplier_id "+paymentJoin+returnJoin;
  String invoiceDate=sqlDate("h.invoice_date");String effectivePaid="LEAST(GREATEST(COALESCE(h.total_amount,0),0),GREATEST(COALESCE(h.paid_amount,0),COALESCE(pr.recorded_paid,0),CASE WHEN UPPER(COALESCE(h.payment_status,'')) IN ('PAID','SETTLED') THEN COALESCE(h.total_amount,0) ELSE 0 END))";String baseBalance="GREATEST(COALESCE(h.total_amount,0)-("+effectivePaid+"),0)",balance=currentBalanceSql(baseBalance),dueDate=currentDueDateSql(sqlDate("h.due_date"));String derivedStatus=currentPaymentStatusSql(effectivePaid,"h");
  if(!blank(global))where.add("LOWER(CONCAT_WS(' ',COALESCE(h.invoice_no,''),COALESCE(p.name,''),COALESCE(p.phone,''),COALESCE(p.gstin,''))) LIKE ?","%"+global.toLowerCase(Locale.ROOT)+"%");if(!blank(supplierFilter))where.add("LOWER(COALESCE(p.name,''))=LOWER(?)",supplierFilter);
  if(!blank(fromDate))where.add(invoiceDate+">=TO_DATE(?,'YYYY-MM-DD')",fromDate);if(!blank(toDate))where.add(invoiceDate+"<=TO_DATE(?,'YYYY-MM-DD')",toDate);
  if(!blank(payment)&&!"ALL".equals(payment)){if("OVERDUE".equals(payment))where.add(balance+">0.01 AND "+dueDate+"<CURRENT_DATE");else where.add("("+derivedStatus+")=?",payment);}if(!blank(dueFilter)&&!"ALL".equals(dueFilter)){where.add(balance+">0.01");switch(dueFilter){case "OVERDUE"->where.add(dueDate+"<CURRENT_DATE");case "DUE TODAY"->where.add(dueDate+"=CURRENT_DATE");case "NEXT 7 DAYS"->where.add(dueDate+" BETWEEN CURRENT_DATE AND CURRENT_DATE+7");case "NEXT 30 DAYS"->where.add(dueDate+" BETWEEN CURRENT_DATE AND CURRENT_DATE+30");default->{}}}if("SENT".equals(mailFilter))where.add("COALESCE(h.email_sent,0)<>0");else if("NOT SENT".equals(mailFilter))where.add("COALESCE(h.email_sent,0)=0");
  if(!blank(docFilter)&&!"ALL".equals(docFilter))where.add("UPPER(COALESCE(h.document_status,''))=?",docFilter);
  long total=jdbc.queryForObject("SELECT COUNT(*)"+join+where.sql(),Long.class,where.args());int totalPages=total==0?0:(int)Math.ceil(total/(double)safeSize);if(totalPages>0&&safePage>=totalPages)safePage=totalPages-1;
  List<Integer> ids=jdbc.query("SELECT h.id"+join+where.sql()+" ORDER BY "+invoiceDate+" DESC NULLS LAST,h.id DESC LIMIT ? OFFSET ?",(r,i)->r.getInt(1),where.argsWith(safeSize,(long)safePage*safeSize));Map<Integer,PurchaseHeaderEntity> byId=new HashMap<>();purchases.findAllById(ids).forEach(e->byId.put(e.getId(),e));Map<Integer,Double> paid=recordedPurchasePayments(ids),quantities=purchaseQuantityTotals(ids);Map<Integer,List<OperationDtos.ChargeDto>> charges=purchaseChargeSummaries(ids);List<OperationDtos.PurchaseDto> rows=ids.stream().map(byId::get).filter(Objects::nonNull).map(h->purchaseSummaryDto(h,effectivePurchasePaid(h,paid.getOrDefault(h.getId(),0d)),quantities.getOrDefault(h.getId(),0d),charges.getOrDefault(h.getId(),List.of()))).toList();
  String active=where.sql()+" AND "+BusinessKpiPolicy.purchasesActive("h");var filteredTotals=jdbc.query("SELECT COUNT(*),COALESCE(SUM(h.total_amount),0),COALESCE(SUM("+effectivePaid+"),0),COALESCE(SUM("+balance+"),0)"+join+active,(r,i)->new OperationDtos.RegisterTotals(r.getLong(1),r.getDouble(2),r.getDouble(3),r.getDouble(4)),where.args()).getFirst();return new OperationDtos.PurchasePage(rows,safePage,safeSize,total,totalPages,filteredTotals,purchaseMetrics(where),purchaseSupplierOptions());
 }

 @Transactional(readOnly=true) public OperationDtos.PurchaseDto purchase(String invoice){PurchaseHeaderEntity h=purchases.findByInvoiceNo(invoice).orElseThrow(()->new IllegalArgumentException("Purchase not found: "+invoice));Double recorded=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE UPPER(document_type)='PURCHASE' AND document_id=?",Double.class,h.getId());return purchaseDto(h,true,effectivePurchasePaid(h,n(recorded)));}
 @Transactional(readOnly=true) public boolean purchaseExists(String invoiceNo){return purchases.findByInvoiceNo(invoiceNo).isPresent();}

 @Transactional public OperationDtos.PurchaseDto savePurchase(OperationDtos.PurchaseDto d){
  CurrentUser.requirePermission("PURCHASE.CREATE","Create Purchase");
  if(d==null)throw new IllegalArgumentException("Purchase data is required");
  validateDocumentLines(d.lines(),"Purchase");
  requireActivePartyReference(d.supplier()==null?null:d.supplier().id(),"SUPPLIER","Supplier");
  validateActiveItems(d.lines(),"Purchase");
  validateDocumentDate(d.invoiceDate(),"Purchase invoice date");
  PurchaseHeaderEntity h=new PurchaseHeaderEntity();
  copyPurchase(d,h);
  snapshotPurchaseParty(h);
  h.setInvoiceNo(nextPurchaseInvoice());
  h.setPaidAmount(0d);h.setPaymentStatus("PENDING");
  String requested="APPROVED";
  h.setRequestedDocumentStatus(requested);
  if(requiresAdminApproval()){
   h.setDocumentStatus("PENDING APPROVAL");h.setApprovalStatus("PENDING");h.setApprovalRequestedBy(CurrentUser.require().username());h.setApprovalRequestedAt(BusinessClock.nowUtcText());h.setInventoryPosted(false);
  }else{
   h.setDocumentStatus(requested);h.setApprovalStatus("APPROVED");h.setApprovedBy(CurrentUser.require().username());h.setApprovedAt(BusinessClock.nowUtcText());h.setInventoryPosted(shouldPostPurchaseInventory(requested));
  }
  h.setCreatedAt(BusinessClock.nowUtcText());
  h=purchases.save(h);
  replacePurchaseLines(h.getId(),d.lines(),!Boolean.TRUE.equals(h.getInventoryPosted()));
  replacePurchaseCharges(h.getId(),normalizedPurchaseCharges(d));
  if("PENDING".equalsIgnoreCase(h.getApprovalStatus()))notifyApprovalRequired("PURCHASE",h.getId(),h.getInvoiceNo());
  audit.log("PURCHASE",h.getId(),"CREATED",h.getInvoiceNo());
  return purchaseDto(h,true);
 }
 @Transactional public OperationDtos.PurchaseDto updatePurchase(OperationDtos.PurchaseDto d){
  CurrentUser.requirePermission("PURCHASE.EDIT","Edit Purchase");
  if(d==null)throw new IllegalArgumentException("Purchase data is required");
  validateDocumentLines(d.lines(),"Purchase");
  validateDocumentDate(d.invoiceDate(),"Purchase invoice date");
  PurchaseHeaderEntity h=purchases.findByInvoiceNoForUpdate(d.invoiceNo()).orElseThrow(()->new IllegalArgumentException("Purchase not found: "+d.invoiceNo()));
  assertVersion(d.rowVersion(),h.getRowVersion(),"Purchase "+h.getInvoiceNo());
  String existingStatus=normalizePurchaseStatus(h.getDocumentStatus());
  if(isInactivePurchase(existingStatus)||"REJECTED".equals(existingStatus))throw new IllegalStateException("Deleted, cancelled or rejected purchases cannot be edited.");

  boolean linesChanged=!samePurchaseLines(h.getId(),d.lines());
  Integer requestedSupplierId=req(d.supplier()==null?null:d.supplier().id());
  Integer currentSupplierId=h.getSupplier()==null?null:h.getSupplier().getId();
  requirePartyReference(requestedSupplierId,"SUPPLIER","Supplier",!Objects.equals(currentSupplierId,requestedSupplierId));
  if(linesChanged)validateActiveItems(d.lines(),"Purchase");
  List<OperationDtos.ChargeDto> newCharges=normalizedPurchaseCharges(d);
  boolean chargesChanged=!samePurchaseCharges(h.getId(),newCharges);
  // Client totals are display hints only; server recomputation is authoritative.
  DocumentCalculationEngine.Totals requestedTotals=documentTotals(d.lines(),newCharges,d.gstType());
  boolean totalsChanged=!sameNumber(h.getSubtotal(),requestedTotals.itemTaxable())||!sameNumber(h.getGstAmount(),requestedTotals.taxAmount())||!sameNumber(h.getTotalAmount(),requestedTotals.grandTotal())||!sameNumber(h.getDiscountAmount(),requestedTotals.discountAmount());
  if(hasActiveReturn("PURCHASE RETURN",h.getInvoiceNo())&&(linesChanged||chargesChanged||totalsChanged))
   throw new IllegalStateException("A purchase with an active Purchase Return cannot change items or financial totals. Reverse/cancel the return first.");
  double recordedPaid=recordedPaymentTotal("PURCHASE",h.getId());
  String existingPaymentStatus=up(h.getPaymentStatus());
  if((linesChanged||chargesChanged||totalsChanged)&&(n(h.getPaidAmount())>.0001||recordedPaid>.0001||Set.of("PAID","SETTLED","PARTIAL").contains(existingPaymentStatus)))
   throw new IllegalStateException("A paid or partially paid purchase cannot change items or financial totals. Use Purchase Return / payment reversal first.");

  // The editor may promote a DRAFT to COMPLETED, but a posted document cannot be
  // silently downgraded back to DRAFT by an older/cached client.
  String requested=normalizePurchaseStatus(d.documentStatus());
  String nextStatus="PENDING APPROVAL".equals(existingStatus)?"PENDING APPROVAL":("DRAFT".equals(existingStatus)&&!"DRAFT".equals(requested)?"COMPLETED":existingStatus);
  boolean wasPosted=Boolean.TRUE.equals(h.getInventoryPosted());
  // Legacy releases posted stock even for DRAFT purchases. A header-only edit of one
  // of those records must preserve that historical posting. Once its lines change,
  // the draft adopts the new no-stock semantics; promotion posts the current lines.
  boolean shouldBePosted="DRAFT".equals(existingStatus)&&"DRAFT".equals(nextStatus)
          ? (wasPosted&&!linesChanged)
          : shouldPostPurchaseInventory(nextStatus);

  Double paidAmount=h.getPaidAmount(); String paymentStatus=h.getPaymentStatus(); Integer emailSent=h.getEmailSent();
  String createdAt=h.getCreatedAt(),createdBy=h.getCreatedBy(); String existingDocumentStatus=h.getDocumentStatus();
  String approvalStatus=h.getApprovalStatus(),requestedDocumentStatus=h.getRequestedDocumentStatus(),approvalRequestedBy=h.getApprovalRequestedBy(),approvalRequestedAt=h.getApprovalRequestedAt(),approvedBy=h.getApprovedBy(),approvedAt=h.getApprovedAt(),rejectionReason=h.getRejectionReason();

  Integer priorSupplierId=h.getSupplier()==null?null:h.getSupplier().getId();
  if(wasPosted&&linesChanged)restorePurchaseStock(h.getId());
  copyPurchase(d,h);
  if(!Objects.equals(priorSupplierId,h.getSupplier()==null?null:h.getSupplier().getId())||blank(h.getSupplierNameSnapshot()))snapshotPurchaseParty(h);
  h.setPaidAmount(paidAmount);h.setPaymentStatus(paymentStatus);h.setEmailSent(emailSent);h.setCreatedAt(createdAt);h.setCreatedBy(createdBy);
  h.setDocumentStatus("DRAFT".equals(existingStatus)?nextStatus:existingDocumentStatus);
  h.setInventoryPosted(shouldBePosted);
  h.setApprovalStatus(approvalStatus);h.setRequestedDocumentStatus(requestedDocumentStatus);h.setApprovalRequestedBy(approvalRequestedBy);h.setApprovalRequestedAt(approvalRequestedAt);h.setApprovedBy(approvedBy);h.setApprovedAt(approvedAt);h.setRejectionReason(rejectionReason);

  if(linesChanged)replacePurchaseLines(h.getId(),d.lines(),!shouldBePosted);
  else if(!wasPosted&&shouldBePosted)postPurchaseStock(h.getId());
  if(chargesChanged)replacePurchaseCharges(h.getId(),newCharges);
  purchases.flush();
  audit.log("PURCHASE",h.getId(),"UPDATED",h.getInvoiceNo());
  return purchaseDto(h,true);
 }
 @Transactional public void deletePurchase(String invoice){CurrentUser.requirePermission("PURCHASE.DELETE","Delete Purchase");PurchaseHeaderEntity h=purchases.findByInvoiceNoForUpdate(invoice).orElseThrow(()->new IllegalArgumentException("Purchase not found: "+invoice));assertDocumentHasNoPayments("PURCHASE",h.getId(),n(h.getPaidAmount()),h.getPaymentStatus(),"deleted");if(hasActiveReturn("PURCHASE RETURN",h.getInvoiceNo()))throw new IllegalStateException("A purchase with an active Purchase Return cannot be deleted. Reverse/cancel the return first.");if(Boolean.TRUE.equals(h.getInventoryPosted())){restorePurchaseStock(h.getId());h.setInventoryPosted(false);}h.setDocumentStatus("DELETED");audit.log("PURCHASE",h.getId(),"DELETED",h.getInvoiceNo());}
 @Transactional public void cancelPurchase(String invoice){CurrentUser.requirePermission("PURCHASE.EDIT","Cancel Purchase");PurchaseHeaderEntity h=purchases.findByInvoiceNoForUpdate(invoice).orElseThrow(()->new IllegalArgumentException("Purchase not found: "+invoice));assertDocumentHasNoPayments("PURCHASE",h.getId(),n(h.getPaidAmount()),h.getPaymentStatus(),"cancelled");String status=normalizePurchaseStatus(h.getDocumentStatus());if("DELETED".equals(status))throw new IllegalStateException("Deleted purchases cannot be cancelled.");if("CANCELLED".equals(status))return;if(hasActiveReturn("PURCHASE RETURN",h.getInvoiceNo()))throw new IllegalStateException("A purchase with an active Purchase Return cannot be cancelled. Reverse/cancel the return first.");if(Boolean.TRUE.equals(h.getInventoryPosted())){restorePurchaseStock(h.getId());h.setInventoryPosted(false);}h.setDocumentStatus("CANCELLED");audit.log("PURCHASE",h.getId(),"CANCELLED",h.getInvoiceNo());}
 @Transactional public void markPurchaseEmail(int id){CurrentUser.requirePermission("PURCHASE.EDIT","Update Purchase email status");PurchaseHeaderEntity h=purchases.findById(id).orElseThrow();h.setEmailSent(1);audit.log("PURCHASE",h.getId(),"EMAIL_SENT",h.getInvoiceNo());}
 @Transactional public String nextPurchaseInvoice(){return configuredNextAtomic("REF_PURCHASE","PUR/DD-MM-YYYY/XXXX",()->purchases.findAll().stream().map(PurchaseHeaderEntity::getInvoiceNo).filter(Objects::nonNull).toList());}
 @Transactional(readOnly=true) public String previewPurchaseInvoice(){return configuredPreviewAtomic("REF_PURCHASE","PUR/DD-MM-YYYY/XXXX",()->purchases.findAll().stream().map(PurchaseHeaderEntity::getInvoiceNo).filter(Objects::nonNull).toList());}

 private void assertDocumentHasNoPayments(String type,int id,double cachedPaid,String paymentStatus,String action){Double recorded=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE UPPER(document_type)=? AND document_id=?",Double.class,type,id);String ps=up(paymentStatus);if(cachedPaid>.0001||n(recorded)>.0001||Set.of("PAID","SETTLED","PARTIAL").contains(ps))throw new IllegalStateException("Paid, partially paid, or settled "+type.toLowerCase(Locale.ROOT)+" documents cannot be "+action+". Use the return/reversal workflow.");}

 @Transactional public void approveSale(String invoice){
  requireAdminApprovalAuthority();
  SalesHeaderEntity h=sales.findByInvoiceNoForUpdate(invoice).orElseThrow(()->new IllegalArgumentException("Sale not found: "+invoice));
  if(!"PENDING".equals(up(h.getApprovalStatus()))||!"PENDING APPROVAL".equals(up(h.getDocumentStatus())))throw new IllegalStateException("This Sale is not waiting for approval.");
  if(!Boolean.TRUE.equals(h.getInventoryPosted())){postSaleStock(h.getId());h.setInventoryPosted(true);}
  h.setDocumentStatus("APPROVED");h.setApprovalStatus("APPROVED");h.setApprovedBy(CurrentUser.require().username());h.setApprovedAt(BusinessClock.nowUtcText());h.setRejectionReason(null);jdbc.update("UPDATE sales_header SET rejected_by=NULL,rejected_at=NULL WHERE id=?",h.getId());
  notifyApprovalDecision("SALE",h.getId(),h.getInvoiceNo(),true,null);audit.log("SALE",h.getId(),"APPROVED",h.getInvoiceNo());
 }
 @Transactional public void rejectSale(String invoice,String reason){
  requireAdminApprovalAuthority();
  SalesHeaderEntity h=sales.findByInvoiceNoForUpdate(invoice).orElseThrow(()->new IllegalArgumentException("Sale not found: "+invoice));
  if(!"PENDING".equals(up(h.getApprovalStatus())))throw new IllegalStateException("This Sale is not waiting for approval.");
  if(Boolean.TRUE.equals(h.getInventoryPosted())){restoreSaleStock(h.getId());h.setInventoryPosted(false);}
  h.setDocumentStatus("REJECTED");h.setApprovalStatus("REJECTED");h.setApprovedBy(null);h.setApprovedAt(null);h.setRejectionReason(blank(reason)?"Rejected by Admin":reason.trim());jdbc.update("UPDATE sales_header SET rejected_by=?,rejected_at=? WHERE id=?",CurrentUser.require().username(),BusinessClock.nowUtcText(),h.getId());
  notifyApprovalDecision("SALE",h.getId(),h.getInvoiceNo(),false,h.getRejectionReason());audit.log("SALE",h.getId(),"REJECTED",h.getInvoiceNo()+" • "+h.getRejectionReason());
 }
 @Transactional public void approvePurchase(String invoice){
  requireAdminApprovalAuthority();
  PurchaseHeaderEntity h=purchases.findByInvoiceNoForUpdate(invoice).orElseThrow(()->new IllegalArgumentException("Purchase not found: "+invoice));
  if(!"PENDING".equals(up(h.getApprovalStatus()))||!"PENDING APPROVAL".equals(up(h.getDocumentStatus())))throw new IllegalStateException("This Purchase is not waiting for approval.");
  if(!Boolean.TRUE.equals(h.getInventoryPosted())){postPurchaseStock(h.getId());h.setInventoryPosted(true);}
  h.setDocumentStatus("APPROVED");h.setApprovalStatus("APPROVED");h.setApprovedBy(CurrentUser.require().username());h.setApprovedAt(BusinessClock.nowUtcText());h.setRejectionReason(null);jdbc.update("UPDATE purchase_header SET rejected_by=NULL,rejected_at=NULL WHERE id=?",h.getId());
  notifyApprovalDecision("PURCHASE",h.getId(),h.getInvoiceNo(),true,null);audit.log("PURCHASE",h.getId(),"APPROVED",h.getInvoiceNo());
 }
 @Transactional public void rejectPurchase(String invoice,String reason){
  requireAdminApprovalAuthority();
  PurchaseHeaderEntity h=purchases.findByInvoiceNoForUpdate(invoice).orElseThrow(()->new IllegalArgumentException("Purchase not found: "+invoice));
  if(!"PENDING".equals(up(h.getApprovalStatus())))throw new IllegalStateException("This Purchase is not waiting for approval.");
  if(Boolean.TRUE.equals(h.getInventoryPosted())){restorePurchaseStock(h.getId());h.setInventoryPosted(false);}
  h.setDocumentStatus("REJECTED");h.setApprovalStatus("REJECTED");h.setApprovedBy(null);h.setApprovedAt(null);h.setRejectionReason(blank(reason)?"Rejected by Admin":reason.trim());jdbc.update("UPDATE purchase_header SET rejected_by=?,rejected_at=? WHERE id=?",CurrentUser.require().username(),BusinessClock.nowUtcText(),h.getId());
  notifyApprovalDecision("PURCHASE",h.getId(),h.getInvoiceNo(),false,h.getRejectionReason());audit.log("PURCHASE",h.getId(),"REJECTED",h.getInvoiceNo()+" • "+h.getRejectionReason());
 }

 @Transactional(readOnly=true) public List<OperationDtos.FinanceDto> finance(){return finance.findAllByOrderByVoucherDateDescIdDesc().stream().map(this::financeDto).toList();}
 @Transactional(readOnly=true) public OperationDtos.FinancePage financePage(int page,int size,String mode,String period,String type,String q,String from,String to){int safeSize=Math.max(10,Math.min(size,200)),safePage=Math.max(0,page);SqlWhere where=new SqlWhere("1=1");String modeFilter=up(mode),periodFilter=up(period),typeFilter=up(type),query=trim(q),fromDate=trim(from),toDate=trim(to);String dateExpr=sqlDate("f.voucher_date");if("BANK".equals(modeFilter))where.add("UPPER(COALESCE(f.voucher_type,'')) IN ('BANK DEPOSIT','BANK WITHDRAWAL')");else if("EXPENSE".equals(modeFilter))where.add("UPPER(COALESCE(f.voucher_type,''))='EXPENSE'");if(!blank(fromDate))where.add(dateExpr+">=TO_DATE(?,'YYYY-MM-DD')",fromDate);if(!blank(toDate))where.add(dateExpr+"<=TO_DATE(?,'YYYY-MM-DD')",toDate);if(blank(fromDate)&&blank(toDate)&&!blank(periodFilter)&&!"ALL TIME".equals(periodFilter)){switch(periodFilter){case "THIS MONTH"->where.add(dateExpr+">=DATE_TRUNC('month',CURRENT_DATE)::date");case "THIS YEAR"->where.add(dateExpr+">=DATE_TRUNC('year',CURRENT_DATE)::date");case "3 MONTHS"->where.add(dateExpr+">=CURRENT_DATE-INTERVAL '3 months'");case "6 MONTHS"->where.add(dateExpr+">=CURRENT_DATE-INTERVAL '6 months'");default->{}}}if(!blank(typeFilter)&&!typeFilter.startsWith("ALL")){if("BANK".equals(modeFilter)){if(typeFilter.contains("DEPOSIT"))where.add("UPPER(COALESCE(f.voucher_type,''))='BANK DEPOSIT'");else if(typeFilter.contains("WITHDRAW"))where.add("UPPER(COALESCE(f.voucher_type,''))='BANK WITHDRAWAL'");}else where.add("UPPER(COALESCE(f.category,''))=?",typeFilter);}if(!blank(query))where.add("LOWER(CONCAT_WS(' ',COALESCE(f.voucher_type,''),COALESCE(f.notes,''),COALESCE(f.account_name,''),COALESCE(f.reference_no,''),COALESCE(f.category,''))) LIKE ?","%"+query.toLowerCase(Locale.ROOT)+"%");String sqlFrom=" FROM finance_register f ";long total=jdbc.queryForObject("SELECT COUNT(*)"+sqlFrom+where.sql(),Long.class,where.args());int totalPages=total==0?0:(int)Math.ceil(total/(double)safeSize);if(totalPages>0&&safePage>=totalPages)safePage=totalPages-1;List<Integer> ids=jdbc.query("SELECT f.id"+sqlFrom+where.sql()+" ORDER BY "+dateExpr+" DESC NULLS LAST,f.id DESC LIMIT ? OFFSET ?",(r,i)->r.getInt(1),where.argsWith(safeSize,(long)safePage*safeSize));Map<Integer,FinanceRegisterEntity> byId=new HashMap<>();finance.findAllById(ids).forEach(e->byId.put(e.getId(),e));List<OperationDtos.FinanceDto> rows=ids.stream().map(byId::get).filter(Objects::nonNull).map(this::financeDto).toList();return new OperationDtos.FinancePage(rows,safePage,safeSize,total,totalPages);}
 @Transactional(readOnly=true) public OperationDtos.FinanceDto finance(int id){return financeDto(finance.findById(id).orElseThrow(()->new IllegalArgumentException("Finance entry not found")));}
 @Transactional public OperationDtos.FinanceDto saveFinance(OperationDtos.FinanceDto d){CurrentUser.requirePermission("BANK_EXPENSE.CREATE","Create finance entry");validateFinance(d);FinanceRegisterEntity e=new FinanceRegisterEntity();copyFinance(d,e);if(blank(e.getVoucherNo()))e.setVoucherNo(nextVoucher());e.setCreatedAt(BusinessClock.nowUtcText());e=finance.saveAndFlush(e);audit.log("FINANCE",e.getId(),"CREATED",e.getVoucherNo());return financeDto(e);}
 @Transactional public OperationDtos.FinanceDto updateFinance(OperationDtos.FinanceDto d){
  CurrentUser.requirePermission("BANK_EXPENSE.EDIT","Edit finance entry");validateFinance(d);
  FinanceRegisterEntity e=finance.findById(req(d.id())).orElseThrow(()->new IllegalArgumentException("Finance entry not found"));
  assertVersion(d.rowVersion(),e.getRowVersion(),"Finance entry "+e.getVoucherNo());
  if(!reconciliationAllocations.findByFinanceEntryIdAndReversedAtIsNull(e.getId()).isEmpty())throw new IllegalStateException("Reconciled finance entries must be reversed from Bank Statement before editing.");
  Integer reconciled=e.getReconciled();copyFinance(d,e);e.setReconciled(reconciled);
  finance.flush();audit.log("FINANCE",e.getId(),"UPDATED",e.getVoucherNo());return financeDto(e);
 }
 @Transactional public void deleteFinance(int id,long rowVersion){
  CurrentUser.requirePermission("BANK_EXPENSE.DELETE","Delete finance entry");FinanceRegisterEntity e=finance.findById(id).orElseThrow(()->new IllegalArgumentException("Finance entry not found"));
  assertVersion(rowVersion,e.getRowVersion(),"Finance entry "+e.getVoucherNo());
  if(n(e.getReconciled())!=0||!reconciliationAllocations.findByFinanceEntryIdAndReversedAtIsNull(e.getId()).isEmpty())throw new IllegalStateException("Reconciled finance entries must be reversed from Bank Statement before deletion.");
  String ref=e.getVoucherNo();finance.delete(e);audit.log("FINANCE",id,"DELETED",ref);
 }
 @Transactional public String nextVoucher(){return configuredNextAtomic("REF_FINANCE_VOUCHER","VCH-YYYY-XXXXX",()->finance.findAll().stream().map(FinanceRegisterEntity::getVoucherNo).filter(Objects::nonNull).toList());}
 @Transactional(readOnly=true) public String previewVoucher(){return configuredPreviewAtomic("REF_FINANCE_VOUCHER","VCH-YYYY-XXXXX",()->finance.findAll().stream().map(FinanceRegisterEntity::getVoucherNo).filter(Objects::nonNull).toList());}

 @Transactional(readOnly=true) public List<OperationDtos.StockHistoryDto> stockHistory(String itemCode){
   String sql="""
     SELECT dse_safe_date(adjustment_date) AS movement_day, adjustment_type AS movement_type, quantity, reason, reference_no, created_by
     FROM stock_adjustment WHERE item_code=?
     UNION ALL
     SELECT dse_safe_date(h.invoice_date), 'SALE', -l.quantity, 'Sales invoice', h.invoice_no, COALESCE(h.salesperson,'System')
     FROM sales_line l JOIN sales_header h ON h.id=l.sales_id WHERE l.item_code=? AND COALESCE(h.inventory_posted,false)=true AND UPPER(COALESCE(h.document_status,'')) NOT IN ('DELETED','CANCELLED','REJECTED','PENDING APPROVAL')
     UNION ALL
     SELECT dse_safe_date(h.invoice_date), 'PURCHASE', l.quantity, 'Purchase invoice', h.invoice_no, 'System'
     FROM purchase_line l JOIN purchase_header h ON h.id=l.purchase_id WHERE l.item_code=? AND COALESCE(h.inventory_posted,false)=true AND UPPER(COALESCE(h.document_status,'')) NOT IN ('DELETED','CANCELLED')
     UNION ALL
     SELECT dse_safe_date(return_date), return_type, CASE WHEN UPPER(return_type) IN ('SALE RETURN','SALES RETURN') THEN quantity ELSE -quantity END, COALESCE(reason,'Return'), return_no, 'System'
     FROM return_register WHERE item_code=? AND UPPER(COALESCE(status,'PENDING APPROVAL'))='APPROVED'
     ORDER BY movement_day DESC
     """;
   return jdbc.query(sql,(r,i)->new OperationDtos.StockHistoryDto(String.valueOf(r.getObject(1)),r.getString(2),r.getDouble(3),r.getString(4),r.getString(5),r.getString(6)),itemCode,itemCode,itemCode,itemCode);
 }
 @Transactional public void adjustStock(OperationDtos.StockAdjustmentRequest d){
   CurrentUser.requirePermission("INVENTORY.EDIT","Adjust stock");
   if(d==null||blank(d.itemCode()))throw new IllegalArgumentException("Item code is required");
   ItemEntity item=items.findByItemCodeForUpdate(d.itemCode()).orElseThrow(()->new IllegalArgumentException("Item not found: "+d.itemCode()));
   double current=n(item.getOpeningStock()); double quantity=d.quantity(); if(!Double.isFinite(quantity)||quantity<0)throw new IllegalArgumentException("Quantity must be a finite non-negative number");
   String type=up(d.type()); double delta=switch(type){case "ADD"->quantity;case "REMOVE"->-quantity;case "SET"->quantity-current;default->throw new IllegalArgumentException("Invalid adjustment type");};
   if(current+delta<-.0001)throw new IllegalArgumentException("Adjustment would make stock negative");
   changeStockCost(d.itemCode(),delta,delta<0,currentAverageCost(d.itemCode(),n(item.getPurchasePrice())),"STOCK_ADJUSTMENT",null);
   jdbc.update("INSERT INTO stock_adjustment(item_code,adjustment_date,adjustment_type,quantity,reason,reference_no,created_by,created_at) VALUES(?,?,?,?,?,?,?,?)",d.itemCode(),BusinessClock.today(),type,delta,d.reason(),d.referenceNo(),CurrentUser.require().username(),BusinessClock.nowUtcText());
   audit.log("ITEM",item.getId(),"STOCK_ADJUSTED",d.itemCode()+" • "+type+" • "+delta);
 }
 @Transactional(readOnly=true) public OperationDtos.FinanceMetrics financeMetrics(){double cr=0,db=0,expenseTotal=0,em=0,ey=0;long bc=0,dc=0,wc=0,ec=0;Map<String,Double> cat=new HashMap<>();YearMonth ym=BusinessClock.currentMonth();int y=BusinessClock.today().getYear();for(var e:finance.findAll()){String t=up(e.getVoucherType());LocalDate d=date(e.getVoucherDate());double a=n(e.getAmount());if(t.equals("BANK DEPOSIT")){cr+=a;dc++;if(d!=null&&YearMonth.from(d).equals(ym))bc++;}else if(t.equals("BANK WITHDRAWAL")){db+=a;wc++;if(d!=null&&YearMonth.from(d).equals(ym))bc++;}else if(t.equals("EXPENSE")){expenseTotal+=a;if(d!=null&&YearMonth.from(d).equals(ym)){em+=a;ec++;}if(d!=null&&d.getYear()==y)ey+=a;String c=blank(e.getCategory())?"Other":e.getCategory();cat.merge(c,a,Double::sum);}}double[] pending=jdbc.query("SELECT COUNT(*),COALESCE(SUM(CASE WHEN credit_amount>0 THEN credit_amount ELSE debit_amount END),0) FROM bank_statement_transaction WHERE UPPER(COALESCE(status,'UNMATCHED')) IN ('UNMATCHED','SUGGESTED','REVIEW')",(r,i)->new double[]{r.getDouble(1),r.getDouble(2)}).getFirst();var top=cat.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);return new OperationDtos.FinanceMetrics(cashPosition.cashPosition(),cr,db,bc,dc,wc,em,ey,ec,top==null?"No expenses":top.getKey(),top==null?0:top.getValue(),(long)pending[0],pending[1]);}

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
 private boolean samePurchaseCharges(int purchaseId,List<OperationDtos.ChargeDto> incoming){
  List<PurchaseChargeEntity> existing=purchaseCharges.findByPurchaseIdOrderBySequenceNoAscIdAsc(purchaseId);
  if(existing.size()!=incoming.size())return false;
  for(int i=0;i<existing.size();i++){var a=existing.get(i);var b=incoming.get(i);if(!Objects.equals(up(a.getChargeName()),up(b.chargeType()))||!sameNumber(a.getAmount(),b.amount())||Boolean.TRUE.equals(a.getTaxable())!=b.taxable()||!sameNumber(a.getGstPercent(),b.taxable()?b.gstPercent():0))return false;}
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
 private DocumentCalculationEngine.Totals documentTotals(List<OperationDtos.LineDto> lines,List<OperationDtos.ChargeDto> charges,String taxType){
  List<DocumentCalculationEngine.LineInput> lineInputs=(lines==null?List.<OperationDtos.LineDto>of():lines).stream().filter(Objects::nonNull).map(x->new DocumentCalculationEngine.LineInput(x.quantity(),x.rate(),x.discountPercent(),x.gstPercent())).toList();
  List<DocumentCalculationEngine.ChargeInput> chargeInputs=(charges==null?List.<OperationDtos.ChargeDto>of():charges).stream().filter(Objects::nonNull).map(x->new DocumentCalculationEngine.ChargeInput(x.amount(),x.taxable(),x.gstPercent())).toList();
  return DocumentCalculationEngine.totals(lineInputs,chargeInputs,DocumentCalculationEngine.taxMode(taxType));
 }

 private void replaceSaleLines(int id,List<OperationDtos.LineDto> ls,boolean skipStock){validateDocumentLines(ls,"Sale");salesLines.deleteBySalesId(id);if(ls==null)return;for(var d:ls){ItemEntity item=items.findByItemCode(d.itemCode()).orElseThrow(()->new IllegalArgumentException("Item not found: "+d.itemCode()));double cost=currentAverageCost(d.itemCode(),n(item.getPurchasePrice()));if(!skipStock)changeStockCost(d.itemCode(),-d.quantity(),true,cost,"SALE",id);DocumentCalculationEngine.LineResult calc=DocumentCalculationEngine.line(d.quantity(),d.rate(),d.discountPercent(),d.gstPercent());SalesLineEntity l=new SalesLineEntity();l.setSalesId(id);l.setItemCode(d.itemCode());l.setQuantity(DocumentCalculationEngine.quantity(d.quantity()));l.setRate(DocumentCalculationEngine.money(d.rate()));l.setDiscountPercent(DocumentCalculationEngine.percent(d.discountPercent()));l.setDiscountAmount(calc.discountAmount());l.setGstPercent(DocumentCalculationEngine.percent(d.gstPercent()));l.setLineTotal(calc.totalAmount());l.setItemDescriptionSnapshot(item.getDescription());l.setCategorySnapshot(item.getCategory());l.setHsnSnapshot(item.getHsn());l.setUnitSnapshot(item.getUnit());l.setItemRemarksSnapshot(item.getRemarks());l.setUnitCostSnapshot(cost(cost).doubleValue());salesLines.save(l);}}
 private void replaceSaleCharges(int salesId,List<OperationDtos.ChargeDto> charges){salesCharges.deleteBySalesId(salesId);int sequence=1;for(var d:charges){SalesChargeEntity e=new SalesChargeEntity();e.setSalesId(salesId);e.setSequenceNo(sequence++);e.setChargeCode(d.chargeType().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_"));e.setChargeName(d.chargeType().trim());e.setAmount(money(d.amount()));e.setTaxable(d.taxable());e.setGstPercent(money(d.taxable()?d.gstPercent():0));salesCharges.save(e);}}
 private List<OperationDtos.ChargeDto> normalizedCharges(OperationDtos.SaleDto d){List<OperationDtos.ChargeDto> input=d.charges()==null?List.of():d.charges();if(input.isEmpty()&&d.chargeAmount()>0)input=List.of(new OperationDtos.ChargeDto(blank(d.chargeType())?"Charges":d.chargeType(),d.chargeAmount(),false,0));List<OperationDtos.ChargeDto> out=new ArrayList<>();Set<String> names=new HashSet<>();for(var c:input){if(c==null||blank(c.chargeType()))throw new IllegalArgumentException("Charge type is required");if(!Double.isFinite(c.amount())||c.amount()<=0)throw new IllegalArgumentException("Charge amount must be greater than zero");if(!Double.isFinite(c.gstPercent())||c.gstPercent()<0||c.gstPercent()>100)throw new IllegalArgumentException("Charge GST percent must be between 0 and 100");String key=up(c.chargeType());if(!names.add(key))throw new IllegalArgumentException("The same charge type cannot be selected twice");out.add(new OperationDtos.ChargeDto(c.chargeType().trim(),money(c.amount()).doubleValue(),c.taxable(),c.taxable()?money(c.gstPercent()).doubleValue():0));}return List.copyOf(out);}
 private void restoreSaleStock(int id){for(var l:salesLines.findBySalesIdOrderByIdAsc(id)){double cost=n(l.getUnitCostSnapshot());if(cost<=0)cost=currentAverageCost(l.getItemCode(),0);changeStockCost(l.getItemCode(),n(l.getQuantity()),false,cost,"SALE_REVERSAL",id);}}
 private void postSaleStock(int id){for(var l:salesLines.findBySalesIdOrderByIdAsc(id)){double cost=currentAverageCost(l.getItemCode(),n(l.getUnitCostSnapshot()));l.setUnitCostSnapshot(cost(cost).doubleValue());salesLines.save(l);changeStockCost(l.getItemCode(),-n(l.getQuantity()),true,cost,"SALE",id);}}
 private void replacePurchaseCharges(int purchaseId,List<OperationDtos.ChargeDto> charges){purchaseCharges.deleteByPurchaseId(purchaseId);int sequence=1;for(var d:charges){PurchaseChargeEntity e=new PurchaseChargeEntity();e.setPurchaseId(purchaseId);e.setSequenceNo(sequence++);e.setChargeCode(d.chargeType().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_"));e.setChargeName(d.chargeType().trim());e.setAmount(money(d.amount()));e.setTaxable(d.taxable());e.setGstPercent(money(d.taxable()?d.gstPercent():0));purchaseCharges.save(e);}}
 private List<OperationDtos.ChargeDto> normalizedPurchaseCharges(OperationDtos.PurchaseDto d){List<OperationDtos.ChargeDto> input=d.charges()==null?List.of():d.charges();List<OperationDtos.ChargeDto> out=new ArrayList<>();Set<String> names=new HashSet<>();for(var c:input){if(c==null||blank(c.chargeType()))throw new IllegalArgumentException("Charge type is required");if(!Double.isFinite(c.amount())||c.amount()<=0)throw new IllegalArgumentException("Charge amount must be greater than zero");if(!Double.isFinite(c.gstPercent())||c.gstPercent()<0||c.gstPercent()>100)throw new IllegalArgumentException("Charge GST percent must be between 0 and 100");String key=up(c.chargeType());if(!names.add(key))throw new IllegalArgumentException("The same purchase charge type cannot be selected twice");out.add(new OperationDtos.ChargeDto(c.chargeType().trim(),money(c.amount()).doubleValue(),c.taxable(),c.taxable()?money(c.gstPercent()).doubleValue():0));}return List.copyOf(out);}
 private void replacePurchaseLines(int id,List<OperationDtos.LineDto> ls,boolean skipStock){
  validateDocumentLines(ls,"Purchase");purchaseLines.deleteByPurchaseId(id);if(ls==null)return;
  for(var d:ls){
   ItemEntity item=items.findByItemCode(d.itemCode()).orElseThrow(()->new IllegalArgumentException("Item not found: "+d.itemCode()));
   DocumentCalculationEngine.LineResult calc=DocumentCalculationEngine.line(d.quantity(),d.rate(),d.discountPercent(),d.gstPercent());
   double normalizedQty=DocumentCalculationEngine.quantity(d.quantity());double unitCost=normalizedQty>0?cost(calc.taxableAmount()/normalizedQty).doubleValue():0;
   if(!skipStock)changeStockCost(d.itemCode(),d.quantity(),false,unitCost,"PURCHASE",id);
   PurchaseLineEntity l=new PurchaseLineEntity();l.setPurchaseId(id);l.setItemCode(d.itemCode());l.setQuantity(DocumentCalculationEngine.quantity(d.quantity()));l.setRate(DocumentCalculationEngine.money(d.rate()));l.setDiscountPercent(DocumentCalculationEngine.percent(d.discountPercent()));l.setDiscountAmount(calc.discountAmount());l.setGstPercent(DocumentCalculationEngine.percent(d.gstPercent()));l.setLineTotal(calc.totalAmount());
   l.setItemDescriptionSnapshot(item.getDescription());l.setCategorySnapshot(item.getCategory());l.setHsnSnapshot(item.getHsn());l.setUnitSnapshot(item.getUnit());l.setItemRemarksSnapshot(item.getRemarks());l.setUnitCostSnapshot(unitCost);
   purchaseLines.save(l);
  }
 }
 private void restorePurchaseStock(int id){for(var l:purchaseLines.findByPurchaseIdOrderByIdAsc(id)){double cost=purchaseUnitCost(l);changeStockCost(l.getItemCode(),-n(l.getQuantity()),true,cost,"PURCHASE_REVERSAL",id);}}
 private void postPurchaseStock(int id){for(var l:purchaseLines.findByPurchaseIdOrderByIdAsc(id)){double cost=purchaseUnitCost(l);changeStockCost(l.getItemCode(),n(l.getQuantity()),false,cost,"PURCHASE",id);}}
 private double recordedPaymentTotal(String type,int id){Double value=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE UPPER(document_type)=? AND document_id=?",Double.class,type,id);return n(value);}
 private boolean hasActiveReturn(String type,String invoice){Long count=jdbc.queryForObject("SELECT COUNT(*) FROM return_register WHERE (CASE WHEN UPPER(return_type)='SALE RETURN' THEN 'SALES RETURN' ELSE UPPER(return_type) END)=? AND invoice_no=? AND UPPER(COALESCE(status,'PENDING APPROVAL')) IN ('PENDING APPROVAL','APPROVED')",Long.class,type,invoice);return count!=null&&count>0;}
 private static String normalizePurchaseStatus(String status){String value=up(status);return value.isBlank()?"COMPLETED":value;}
 private static boolean isInactivePurchase(String status){String value=up(status);return value.equals("DELETED")||value.equals("CANCELLED");}
 private static boolean shouldPostPurchaseInventory(String status){String s=normalizePurchaseStatus(status);return !Set.of("DRAFT","PENDING APPROVAL","REJECTED").contains(s)&&!isInactivePurchase(s);}
@Transactional public void applyStockDelta(String code,double delta,boolean enforce){changeStock(code,delta,enforce);}
 @Transactional public void applyStockMovement(String code,double delta,boolean enforce,double unitCost,String movementType,Integer referenceId){changeStockCost(code,delta,enforce,unitCost,movementType,referenceId);}
 private PartyEntity requireActivePartyReference(Integer id,String expectedType,String label){return requirePartyReference(id,expectedType,label,true);}
 private PartyEntity requirePartyReference(Integer id,String expectedType,String label,boolean requireActive){int partyId=req(id);PartyEntity party=parties.findById(partyId).orElseThrow(()->new IllegalArgumentException(label+" not found"));if(!expectedType.equalsIgnoreCase(trim(party.getPartyType())))throw new IllegalArgumentException(label+" must be an active "+expectedType.toLowerCase(Locale.ROOT)+" record");if(requireActive&&(party.getActive()!=null&&party.getActive()==0))throw new IllegalArgumentException(label+" is inactive. Reactivate it in Master before using it on a new transaction.");return party;}
 private void validateActiveItems(List<OperationDtos.LineDto> ls,String document){if(ls==null)return;Set<String> seen=new HashSet<>();for(var d:ls){if(d==null||blank(d.itemCode())||!seen.add(d.itemCode().trim().toUpperCase(Locale.ROOT)))continue;ItemEntity item=items.findByItemCode(d.itemCode()).orElseThrow(()->new IllegalArgumentException("Item not found: "+d.itemCode()));if(item.getActive()!=null&&item.getActive()==0)throw new IllegalArgumentException(document+" item "+d.itemCode()+" is inactive. Reactivate it in Item Master before using it on a new or changed transaction.");}}
 private void validateDocumentLines(List<OperationDtos.LineDto> ls,String document){if(ls==null||ls.isEmpty())throw new IllegalArgumentException(document+" must contain at least one item line");for(var d:ls){if(d==null)throw new IllegalArgumentException(document+" contains an empty item line");if(blank(d.itemCode()))throw new IllegalArgumentException(document+" item code is required");if(!Double.isFinite(d.quantity())||d.quantity()<=0)throw new IllegalArgumentException(document+" quantity for "+d.itemCode()+" must be a finite number greater than zero");if(!Double.isFinite(d.rate())||d.rate()<0)throw new IllegalArgumentException(document+" rate for "+d.itemCode()+" must be a finite non-negative number");if(!Double.isFinite(d.discountPercent())||d.discountPercent()<0||d.discountPercent()>100)throw new IllegalArgumentException(document+" discount for "+d.itemCode()+" must be between 0 and 100");if(!Double.isFinite(d.gstPercent())||d.gstPercent()<0||d.gstPercent()>100)throw new IllegalArgumentException(document+" GST for "+d.itemCode()+" must be between 0 and 100");if(!Double.isFinite(d.totalAmount())||d.totalAmount()<0)throw new IllegalArgumentException(document+" line total for "+d.itemCode()+" must be a finite non-negative number");}}
 private void validateDocumentDate(String value,String field){if(blank(value))throw new IllegalArgumentException(field+" is required");try{LocalDate.parse(value.trim());}catch(Exception ex){throw new IllegalArgumentException(field+" must use YYYY-MM-DD and be a valid calendar date");}}
 private void validateFinance(OperationDtos.FinanceDto d){if(d==null)throw new IllegalArgumentException("Finance entry is required");if(!Double.isFinite(d.amount())||d.amount()<=0)throw new IllegalArgumentException("Finance amount must be a finite number greater than zero");validateDocumentDate(d.voucherDate(),"Voucher date");}
 private void changeStock(String code,double delta,boolean enforce){changeStockCost(code,delta,enforce,currentAverageCost(code,0),"ADJUSTMENT",null);}
 private void changeStockCost(String code,double delta,boolean enforce,double unitCost,String movementType,Integer referenceId){
  if(code==null||code.isBlank())throw new IllegalArgumentException("Item code is required");if(!Double.isFinite(delta))throw new IllegalArgumentException("Stock quantity must be finite");delta=DocumentCalculationEngine.quantity(Math.abs(delta))*Math.signum(delta);unitCost=DocumentCalculationEngine.unitCost(Math.max(0,unitCost));ItemEntity i=items.findByItemCodeForUpdate(code).orElseThrow(()->new IllegalArgumentException("Item not found: "+code));double now=n(i.getOpeningStock()),reserved=Math.max(0,n(i.getReservedStock()));double next=now+delta;if(enforce&&delta<0&&next+0.0001<reserved)throw new IllegalStateException("Insufficient available stock for item "+code+". On hand: "+DocumentCalculationEngine.money(now)+", reserved: "+DocumentCalculationEngine.money(reserved));if(enforce&&next<-.0001)throw new IllegalStateException("Insufficient stock for item "+code);next=Math.max(0,next);double cost=Math.max(0,Double.isFinite(unitCost)?unitCost:0);jdbc.update("INSERT INTO inventory_cost_state(item_code,quantity,average_unit_cost,updated_at) VALUES(?,?,?,?) ON CONFLICT (item_code) DO NOTHING",code,now,cost>0?cost:n(i.getPurchasePrice()),BusinessClock.nowUtcText());List<double[]> state=jdbc.query("SELECT quantity,average_unit_cost FROM inventory_cost_state WHERE item_code=? FOR UPDATE",(r,x)->new double[]{r.getDouble(1),r.getDouble(2)},code);double oldQty=state.isEmpty()?now:state.getFirst()[0],oldAvg=state.isEmpty()?cost:state.getFirst()[1];if(Math.abs(oldQty-now)>.01)oldQty=now;double effectiveCost=cost>0?cost:oldAvg,newAvg=oldAvg;if(delta>0&&next>.000001)newAvg=((oldQty*oldAvg)+(delta*effectiveCost))/next;else if(next<=.000001)newAvg=oldAvg;jdbc.update("UPDATE inventory_cost_state SET quantity=?,average_unit_cost=?,updated_at=? WHERE item_code=?",next,cost(newAvg),BusinessClock.nowUtcText(),code);jdbc.update("INSERT INTO inventory_cost_ledger(item_code,movement_type,reference_id,quantity_change,unit_cost,value_change,created_at) VALUES(?,?,?,?,?,?,?)",code,movementType,referenceId,DocumentCalculationEngine.quantity(Math.abs(delta))*Math.signum(delta),cost(effectiveCost),money(delta*effectiveCost),BusinessClock.nowUtcText());i.setOpeningStock(next);
 }
 private double currentAverageCost(String code,double fallback){try{Double v=jdbc.queryForObject("SELECT average_unit_cost FROM inventory_cost_state WHERE item_code=?",Double.class,code);if(v!=null&&v>=0)return v;}catch(Exception ignored){}return Math.max(0,fallback);}
 private static double purchaseUnitCost(PurchaseLineEntity l){double q=n(l.getQuantity());if(q<=0)return 0;double gross=n(l.getRate())*q,discount=n(l.getDiscountAmount());return DocumentCalculationEngine.unitCost(Math.max(0,gross-discount)/q);}

 private String returnSettlementJoin(String type){String t=type.replace("'","''");return " LEFT JOIN (SELECT x.invoice_no,SUM(CASE WHEN x.state='APPROVED' THEN x.return_total ELSE 0 END) approved_return,SUM(CASE WHEN x.state='APPROVED' THEN x.refunded ELSE 0 END) settled_return,MAX(CASE WHEN x.state='PENDING APPROVAL' THEN 1 ELSE 0 END) pending_approval,MAX(CASE WHEN x.state='APPROVED' THEN x.due_date END) return_due FROM (SELECT MAX(r.invoice_no) invoice_no,r.return_no,MAX(UPPER(COALESCE(r.status,'PENDING APPROVAL'))) state,SUM(COALESCE(r.amount,0)) return_total,COALESCE((SELECT SUM(rr.amount+COALESCE(rr.rounding_adjustment,0)) FROM return_refund rr WHERE rr.return_no=r.return_no),0) refunded,MAX(r.settlement_due_date) due_date FROM return_register r WHERE UPPER(COALESCE(r.return_type,''))='"+t+"' AND UPPER(COALESCE(r.status,'PENDING APPROVAL')) IN ('PENDING APPROVAL','APPROVED') GROUP BY r.return_no) x GROUP BY x.invoice_no) rs ON rs.invoice_no=h.invoice_no ";}
 private String currentPaymentStatusSql(String effectivePaid,String headerAlias){return "CASE WHEN COALESCE("+headerAlias+".total_amount,0)>0 AND ("+effectivePaid+")+0.0001>=COALESCE("+headerAlias+".total_amount,0) THEN 'PAID' WHEN ("+effectivePaid+")>0.0001 THEN 'PARTIAL' ELSE COALESCE(NULLIF(UPPER("+headerAlias+".payment_status),''),'PENDING') END";}
 private String currentBalanceSql(String baseBalance){return "("+baseBalance+")";}
 private String currentDueDateSql(String baseDue){return "("+baseDue+")";}

 private OperationDtos.SalesMetrics salesMetrics(SqlWhere where){
  String date=sqlDate("h.invoice_date");
  String join=" FROM sales_header h LEFT JOIN party_master p ON p.id=h.customer_id LEFT JOIN (SELECT document_id,COALESCE(SUM(amount),0) recorded_paid FROM payment_record WHERE UPPER(document_type)='SALE' GROUP BY document_id) pr ON pr.document_id=h.id "+returnSettlementJoin("SALES RETURN");
  String paid=BusinessKpiPolicy.effectivePaid("h","pr"),baseBalance=BusinessKpiPolicy.outstanding("h","pr"),balance=currentBalanceSql(baseBalance),due=currentDueDateSql(sqlDate("h.due_date"));
  String active=where.sql()+" AND "+BusinessKpiPolicy.salesActive("h");Object[] args=where.args();
  var base=jdbc.query("SELECT COALESCE(SUM(COALESCE(h.total_amount,0)),0),COUNT(*),COALESCE(SUM(CASE WHEN "+date+"=CURRENT_DATE THEN COALESCE(h.total_amount,0) ELSE 0 END),0),SUM(CASE WHEN "+date+"=CURRENT_DATE THEN 1 ELSE 0 END),COALESCE(SUM("+balance+"),0),SUM(CASE WHEN "+balance+">0.01 THEN 1 ELSE 0 END),COALESCE(SUM(CASE WHEN "+balance+">0.01 AND "+due+"<CURRENT_DATE THEN "+balance+" ELSE 0 END),0),SUM(CASE WHEN "+balance+">0.01 AND "+due+"<CURRENT_DATE THEN 1 ELSE 0 END),COALESCE(SUM(CASE WHEN "+balance+">0.01 AND "+due+" BETWEEN CURRENT_DATE AND CURRENT_DATE+7 THEN "+balance+" ELSE 0 END),0),SUM(CASE WHEN "+balance+">0.01 AND "+due+" BETWEEN CURRENT_DATE AND CURRENT_DATE+7 THEN 1 ELSE 0 END),SUM(CASE WHEN COALESCE(h.email_sent,0)<>0 THEN 1 ELSE 0 END)"+join+active,(r,i)->new double[]{r.getDouble(1),r.getLong(2),r.getDouble(3),r.getLong(4),r.getDouble(5),r.getLong(6),r.getDouble(7),r.getLong(8),r.getDouble(9),r.getLong(10),r.getLong(11)},args).getFirst();
  double rate=base[1]<=0?0:base[10]*100d/base[1];
  double[] bucket=jdbc.query("SELECT COALESCE(SUM(CASE WHEN "+balance+">0.01 AND "+due+"=CURRENT_DATE THEN "+balance+" ELSE 0 END),0),COALESCE(SUM(CASE WHEN "+balance+">0.01 AND "+due+">CURRENT_DATE AND "+due+"<=CURRENT_DATE+7 THEN "+balance+" ELSE 0 END),0),COALESCE(SUM(CASE WHEN "+balance+">0.01 AND "+due+">CURRENT_DATE+7 AND "+due+"<=CURRENT_DATE+30 THEN "+balance+" ELSE 0 END),0),COALESCE(SUM(CASE WHEN "+balance+">0.01 AND "+due+">CURRENT_DATE+30 THEN "+balance+" ELSE 0 END),0)"+join+active,(r,i)->new double[]{r.getDouble(1),r.getDouble(2),r.getDouble(3),r.getDouble(4)},args).getFirst();
  List<OperationDtos.MetricPoint> dueBuckets=List.of(new OperationDtos.MetricPoint("Due Today",bucket[0]),new OperationDtos.MetricPoint("1-7 Days",bucket[1]),new OperationDtos.MetricPoint("8-30 Days",bucket[2]),new OperationDtos.MetricPoint("Over 30 Days",bucket[3]));
  List<OperationDtos.MetricPoint> top=jdbc.query("SELECT COALESCE(p.name,'Unknown Customer'),COALESCE(SUM(COALESCE(h.total_amount,0)),0)"+join+active+" GROUP BY COALESCE(p.name,'Unknown Customer') ORDER BY 2 DESC LIMIT 5",(r,i)->new OperationDtos.MetricPoint(r.getString(1),r.getDouble(2)),args);
  List<OperationDtos.MetricPoint> months=jdbc.query("SELECT TO_CHAR("+date+",'YYYY-MM'),COALESCE(SUM(COALESCE(h.total_amount,0)),0)"+join+active+" AND "+date+" IS NOT NULL GROUP BY TO_CHAR("+date+",'YYYY-MM') ORDER BY TO_CHAR("+date+",'YYYY-MM') DESC LIMIT 7",(r,i)->new OperationDtos.MetricPoint(r.getString(1),r.getDouble(2)),args);Collections.reverse(months);
  return new OperationDtos.SalesMetrics(base[0],(long)base[1],base[2],(long)base[3],base[4],(long)base[5],base[6],(long)base[7],base[8],(long)base[9],rate,dueBuckets,top,months);
 }
 private OperationDtos.PurchaseMetrics purchaseMetrics(SqlWhere where){
  String effective="LEAST(GREATEST(COALESCE(h.total_amount,0),0),GREATEST(COALESCE(h.paid_amount,0),COALESCE(pr.recorded_paid,0),CASE WHEN UPPER(COALESCE(h.payment_status,'')) IN ('PAID','SETTLED') THEN COALESCE(h.total_amount,0) ELSE 0 END))";
  String join=" FROM purchase_header h LEFT JOIN party_master p ON p.id=h.supplier_id LEFT JOIN (SELECT document_id,COALESCE(SUM(amount),0) recorded_paid FROM payment_record WHERE UPPER(document_type)='PURCHASE' GROUP BY document_id) pr ON pr.document_id=h.id "+returnSettlementJoin("PURCHASE RETURN");
  String active=where.sql()+" AND "+BusinessKpiPolicy.purchasesActive("h");
  Object[] args=where.args();
  var row=jdbc.query("SELECT COALESCE(SUM(COALESCE(h.total_amount,0)),0),COUNT(*),COUNT(DISTINCT h.supplier_id),COALESCE(SUM("+effective+"),0)"+join+active,(r,i)->new double[]{r.getDouble(1),r.getLong(2),r.getLong(3),r.getDouble(4)},args).getFirst();
  String lineJoin=" FROM purchase_line l JOIN purchase_header h ON h.id=l.purchase_id LEFT JOIN party_master p ON p.id=h.supplier_id LEFT JOIN (SELECT document_id,COALESCE(SUM(amount),0) recorded_paid FROM payment_record WHERE UPPER(document_type)='PURCHASE' GROUP BY document_id) pr ON pr.document_id=h.id "+returnSettlementJoin("PURCHASE RETURN");
  Long distinctItems=jdbc.queryForObject("SELECT COUNT(DISTINCT NULLIF(TRIM(l.item_code),''))"+lineJoin+active,Long.class,args);
  return new OperationDtos.PurchaseMetrics(row[0],(long)row[1],(long)row[2],distinctItems==null?0:Math.max(0,distinctItems),row[3]);
 }
 private Map<Integer,Double> saleQuantityTotals(){return saleQuantityTotals(sales.findAll().stream().map(SalesHeaderEntity::getId).filter(Objects::nonNull).toList());}
 private Map<Integer,Double> saleQuantityTotals(Collection<Integer> ids){Map<Integer,Double> out=new HashMap<>();if(ids==null||ids.isEmpty())return out;String csv=idCsv(ids);jdbc.query("SELECT sales_id,COALESCE(SUM(quantity),0) FROM sales_line WHERE sales_id IN ("+csv+") GROUP BY sales_id",r->{out.put(r.getInt(1),r.getDouble(2));});return out;}
 private Map<Integer,List<OperationDtos.ChargeDto>> saleChargeSummaries(){return saleChargeSummaries(sales.findAll().stream().map(SalesHeaderEntity::getId).filter(Objects::nonNull).toList());}
 private Map<Integer,List<OperationDtos.ChargeDto>> saleChargeSummaries(Collection<Integer> ids){Map<Integer,List<OperationDtos.ChargeDto>> out=new HashMap<>();if(ids==null||ids.isEmpty())return out;for(var e:salesCharges.findAll()){if(e.getSalesId()==null||!ids.contains(e.getSalesId()))continue;out.computeIfAbsent(e.getSalesId(),k->new ArrayList<>()).add(new OperationDtos.ChargeDto(e.getChargeName(),n(e.getAmount()),Boolean.TRUE.equals(e.getTaxable()),n(e.getGstPercent())));}return out;}
 private OperationDtos.SaleDto saleSummaryDto(SalesHeaderEntity h,double quantity,List<OperationDtos.ChargeDto> charges){return saleDto(h,false,quantity,charges==null?List.of():charges);}
 private OperationDtos.SaleDto saleDto(SalesHeaderEntity h,boolean includeLines){List<OperationDtos.LineDto> lines=includeLines?salesLines.findBySalesIdOrderByIdAsc(h.getId()).stream().map(this::line).toList():List.of();double quantity=includeLines?lines.stream().mapToDouble(OperationDtos.LineDto::quantity).sum():saleQuantityTotals(List.of(h.getId())).getOrDefault(h.getId(),0d);List<OperationDtos.ChargeDto> charges=saleChargeSummaries(List.of(h.getId())).getOrDefault(h.getId(),List.of());return saleDto(h,includeLines,quantity,charges);}
 private OperationDtos.SaleDto saleDto(SalesHeaderEntity h,boolean includeLines,double quantity,List<OperationDtos.ChargeDto> charges){List<OperationDtos.LineDto> lines=includeLines?salesLines.findBySalesIdOrderByIdAsc(h.getId()).stream().map(this::line).toList():List.of();List<OperationDtos.ChargeDto> safeCharges=charges==null?List.of():List.copyOf(charges);OperationDtos.ChargeDto first=safeCharges.isEmpty()?null:safeCharges.getFirst();return new OperationDtos.SaleDto(h.getId(),h.getInvoiceNo(),h.getInvoiceDate(),saleParty(h),n(h.getSubtotal()),n(h.getDiscountAmount()),n(h.getGstAmount()),n(h.getTotalAmount()),h.getRemarks(),h.getCreatedAt(),n(h.getEmailSent())!=0,h.getDueDate(),n(h.getPaidAmount()),h.getPaymentStatus(),n(h.getWhatsappSent())!=0,h.getInvoiceType(),h.getSalesperson(),h.getSource(),h.getNotes(),h.getDeliveryAddress(),h.getPaymentTerms(),h.getTransporter(),h.getReferenceNo(),h.getPoDate(),h.getBillingAddress(),h.getGstType(),h.getDoorDelivery(),h.getVehicleNumber(),h.getContactPerson(),h.getTransportNote(),h.getOrderNo(),h.getGstin(),h.getBillingGstin(),h.getDeliveryGstin(),Boolean.TRUE.equals(h.getSameAsBilling()),h.getTransporterGstin(),first==null?h.getChargeType():first.chargeType(),first==null?n(h.getChargeAmount()):first.amount(),h.getContactPersonMobile(),h.getDocumentStatus(),h.getAttachmentPath(),quantity,safeCharges,lines,nv(h.getRowVersion()));}
 private Map<Integer,Double> recordedPurchasePayments(){return recordedPurchasePayments(purchases.findAll().stream().map(PurchaseHeaderEntity::getId).filter(Objects::nonNull).toList());}
 private Map<Integer,Double> recordedPurchasePayments(Collection<Integer> ids){Map<Integer,Double> out=new HashMap<>();if(ids==null||ids.isEmpty())return out;String csv=idCsv(ids);jdbc.query("SELECT document_id,COALESCE(SUM(amount),0) FROM payment_record WHERE UPPER(document_type)='PURCHASE' AND document_id IN ("+csv+") GROUP BY document_id",r->{out.put(r.getInt(1),r.getDouble(2));});return out;}
 private Map<Integer,Double> purchaseQuantityTotals(){return purchaseQuantityTotals(purchases.findAll().stream().map(PurchaseHeaderEntity::getId).filter(Objects::nonNull).toList());}
 private Map<Integer,Double> purchaseQuantityTotals(Collection<Integer> ids){Map<Integer,Double> out=new HashMap<>();if(ids==null||ids.isEmpty())return out;String csv=idCsv(ids);jdbc.query("SELECT purchase_id,COALESCE(SUM(quantity),0) FROM purchase_line WHERE purchase_id IN ("+csv+") GROUP BY purchase_id",r->{out.put(r.getInt(1),r.getDouble(2));});return out;}
 private Map<Integer,List<OperationDtos.ChargeDto>> purchaseChargeSummaries(){return purchaseChargeSummaries(purchases.findAll().stream().map(PurchaseHeaderEntity::getId).filter(Objects::nonNull).toList());}
 private Map<Integer,List<OperationDtos.ChargeDto>> purchaseChargeSummaries(Collection<Integer> ids){Map<Integer,List<OperationDtos.ChargeDto>> out=new HashMap<>();if(ids==null||ids.isEmpty())return out;for(var e:purchaseCharges.findAll()){if(e.getPurchaseId()==null||!ids.contains(e.getPurchaseId()))continue;out.computeIfAbsent(e.getPurchaseId(),k->new ArrayList<>()).add(new OperationDtos.ChargeDto(e.getChargeName(),n(e.getAmount()),Boolean.TRUE.equals(e.getTaxable()),n(e.getGstPercent())));}return out;}
 private OperationDtos.PurchaseDto purchaseSummaryDto(PurchaseHeaderEntity h,double paid,double quantity,List<OperationDtos.ChargeDto> charges){return purchaseDto(h,false,paid,quantity,charges==null?List.of():charges);}
 private OperationDtos.PurchaseDto purchaseDto(PurchaseHeaderEntity h,boolean includeLines){double recorded=recordedPurchasePayments(List.of(h.getId())).getOrDefault(h.getId(),0d);return purchaseDto(h,includeLines,effectivePurchasePaid(h,recorded));}
 private OperationDtos.PurchaseDto purchaseDto(PurchaseHeaderEntity h,boolean includeLines,double paid){List<OperationDtos.LineDto> lines=includeLines?purchaseLines.findByPurchaseIdOrderByIdAsc(h.getId()).stream().map(this::line).toList():List.of();double quantity=includeLines?lines.stream().mapToDouble(OperationDtos.LineDto::quantity).sum():purchaseQuantityTotals(List.of(h.getId())).getOrDefault(h.getId(),0d);List<OperationDtos.ChargeDto> charges=purchaseChargeSummaries(List.of(h.getId())).getOrDefault(h.getId(),List.of());return purchaseDto(h,includeLines,paid,quantity,charges);}
 private OperationDtos.PurchaseDto purchaseDto(PurchaseHeaderEntity h,boolean includeLines,double paid,double quantity,List<OperationDtos.ChargeDto> charges){List<OperationDtos.LineDto> lines=includeLines?purchaseLines.findByPurchaseIdOrderByIdAsc(h.getId()).stream().map(this::line).toList():List.of();return new OperationDtos.PurchaseDto(h.getId(),h.getInvoiceNo(),h.getInvoiceDate(),purchaseParty(h),n(h.getSubtotal()),n(h.getGstAmount()),n(h.getTotalAmount()),h.getRemarks(),h.getCreatedAt(),n(h.getEmailSent())!=0,h.getDueDate(),paid,derivedPaymentStatus(n(h.getTotalAmount()),paid,h.getPaymentStatus()),h.getDocumentStatus(),h.getWarehouse(),h.getPaymentTerms(),h.getCurrency(),h.getReferenceNo(),h.getGstTreatment(),h.getTransporter(),h.getLrAwbNo(),h.getDiscountType(),n(h.getDiscountAmount()),h.getAttachmentPath(),h.getCreatedBy(),h.getDeliveryDate(),h.getBillingAddress(),h.getDeliveryAddress(),h.getBillingGstin(),h.getDeliveryGstin(),h.getGstType(),h.getTransporterGstin(),h.getVehicleNumber(),h.getContactPerson(),h.getContactPersonMobile(),h.getNotes(),h.getOrderNo(),h.getPoDate(),Boolean.TRUE.equals(h.getSameAsBilling()),quantity,charges==null?List.of():List.copyOf(charges),lines,nv(h.getRowVersion()));}
 private static String derivedPaymentStatus(double total,double paid,String stored){if(total>0&&paid+.0001>=total)return "PAID";if(paid>.0001)return "PARTIAL";return blank(stored)?"PENDING":up(stored);}
 private List<String> saleCustomerOptions(){return jdbc.query("SELECT DISTINCT p.name FROM sales_header h JOIN party_master p ON p.id=h.customer_id WHERE UPPER(COALESCE(h.document_status,''))<>'DELETED' AND COALESCE(p.name,'')<>'' ORDER BY p.name LIMIT 40",(r,i)->r.getString(1));}
 private List<String> purchaseSupplierOptions(){return jdbc.query("SELECT DISTINCT p.name FROM purchase_header h JOIN party_master p ON p.id=h.supplier_id WHERE UPPER(COALESCE(h.document_status,''))<>'DELETED' AND COALESCE(p.name,'')<>'' ORDER BY p.name LIMIT 40",(r,i)->r.getString(1));}
 private double effectivePurchasePaid(PurchaseHeaderEntity h,double recorded){double total=n(h.getTotalAmount()),paid=Math.max(n(h.getPaidAmount()),recorded);String ps=up(h.getPaymentStatus());if(total>0&&Set.of("PAID","SETTLED").contains(ps))paid=Math.max(paid,total);return total>0?Math.min(total,paid):Math.max(0,paid);}
 private OperationDtos.LineDto line(SalesLineEntity l){ItemEntity item=items.findByItemCode(l.getItemCode()).orElse(null);String desc=blank(l.getItemDescriptionSnapshot())?(item==null?null:item.getDescription()):l.getItemDescriptionSnapshot();String category=blank(l.getCategorySnapshot())?(item==null?null:item.getCategory()):l.getCategorySnapshot();String hsn=blank(l.getHsnSnapshot())?(item==null?null:item.getHsn()):l.getHsnSnapshot();String unit=blank(l.getUnitSnapshot())?(item==null?null:item.getUnit()):l.getUnitSnapshot();String remarks=blank(l.getItemRemarksSnapshot())?(item==null?null:item.getRemarks()):l.getItemRemarksSnapshot();return new OperationDtos.LineDto(l.getItemCode(),desc,category,hsn,unit,remarks,n(l.getQuantity()),n(l.getRate()),n(l.getDiscountPercent()),n(l.getDiscountAmount()),n(l.getGstPercent()),n(l.getLineTotal()));}
 private OperationDtos.LineDto line(PurchaseLineEntity l){ItemEntity item=items.findByItemCode(l.getItemCode()).orElse(null);String desc=blank(l.getItemDescriptionSnapshot())?(item==null?null:item.getDescription()):l.getItemDescriptionSnapshot();String hsn=blank(l.getHsnSnapshot())?(item==null?null:item.getHsn()):l.getHsnSnapshot();String unit=blank(l.getUnitSnapshot())?(item==null?null:item.getUnit()):l.getUnitSnapshot();String remarks=blank(l.getItemRemarksSnapshot())?(item==null?null:item.getRemarks()):l.getItemRemarksSnapshot();String category=blank(l.getCategorySnapshot())?(item==null?null:item.getCategory()):l.getCategorySnapshot();return new OperationDtos.LineDto(l.getItemCode(),desc,category,hsn,unit,remarks,n(l.getQuantity()),n(l.getRate()),n(l.getDiscountPercent()),n(l.getDiscountAmount()),n(l.getGstPercent()),n(l.getLineTotal()));}
 private OperationDtos.PartyDto party(PartyEntity p){return p==null?null:new OperationDtos.PartyDto(p.getId(),p.getPartyCode(),p.getName(),p.getEmail(),p.getPhone(),p.getGstin(),p.getAddress());}
 private OperationDtos.PartyDto saleParty(SalesHeaderEntity h){PartyEntity p=h.getCustomer();if(p==null)return null;return new OperationDtos.PartyDto(p.getId(),p.getPartyCode(),blank(h.getCustomerNameSnapshot())?p.getName():h.getCustomerNameSnapshot(),blank(h.getCustomerEmailSnapshot())?p.getEmail():h.getCustomerEmailSnapshot(),blank(h.getCustomerPhoneSnapshot())?p.getPhone():h.getCustomerPhoneSnapshot(),blank(h.getCustomerGstinSnapshot())?p.getGstin():h.getCustomerGstinSnapshot(),blank(h.getCustomerAddressSnapshot())?p.getAddress():h.getCustomerAddressSnapshot());}
 private OperationDtos.PartyDto purchaseParty(PurchaseHeaderEntity h){PartyEntity p=h.getSupplier();if(p==null)return null;return new OperationDtos.PartyDto(p.getId(),p.getPartyCode(),blank(h.getSupplierNameSnapshot())?p.getName():h.getSupplierNameSnapshot(),blank(h.getSupplierEmailSnapshot())?p.getEmail():h.getSupplierEmailSnapshot(),blank(h.getSupplierPhoneSnapshot())?p.getPhone():h.getSupplierPhoneSnapshot(),blank(h.getSupplierGstinSnapshot())?p.getGstin():h.getSupplierGstinSnapshot(),blank(h.getSupplierAddressSnapshot())?p.getAddress():h.getSupplierAddressSnapshot());}
 private void snapshotSaleParty(SalesHeaderEntity h){PartyEntity p=h.getCustomer();if(p==null)return;h.setCustomerNameSnapshot(p.getName());h.setCustomerEmailSnapshot(p.getEmail());h.setCustomerPhoneSnapshot(p.getPhone());h.setCustomerGstinSnapshot(p.getGstin());h.setCustomerAddressSnapshot(p.getAddress());}
 private void snapshotPurchaseParty(PurchaseHeaderEntity h){PartyEntity p=h.getSupplier();if(p==null)return;h.setSupplierNameSnapshot(p.getName());h.setSupplierEmailSnapshot(p.getEmail());h.setSupplierPhoneSnapshot(p.getPhone());h.setSupplierGstinSnapshot(p.getGstin());h.setSupplierAddressSnapshot(p.getAddress());}
private void copySale(OperationDtos.SaleDto d,SalesHeaderEntity h){h.setInvoiceNo(d.invoiceNo());h.setInvoiceDate(d.invoiceDate());h.setCustomer(requirePartyReference(d.customer()==null?null:d.customer().id(),"CUSTOMER","Customer",false));String taxType=blank(d.gstType())?"GST":d.gstType();DocumentCalculationEngine.Totals totals=documentTotals(d.lines(),normalizedCharges(d),taxType);h.setSubtotal(totals.itemTaxable());h.setDiscountAmount(totals.discountAmount());h.setGstAmount(totals.taxAmount());h.setTotalAmount(totals.grandTotal());h.setRemarks(d.remarks());h.setDueDate(d.dueDate());h.setWhatsappSent(d.whatsappSent()?1:0);h.setInvoiceType(d.invoiceType());h.setSalesperson(d.salesperson());h.setSource(d.source());h.setNotes(d.notes());h.setDeliveryAddress(d.deliveryAddress());h.setPaymentTerms(d.paymentTerms());h.setTransporter(d.transporter());h.setReferenceNo(d.referenceNo());h.setPoDate(d.poDate());h.setBillingAddress(d.billingAddress());h.setGstType(taxType);h.setDoorDelivery(d.doorDelivery());h.setVehicleNumber(d.vehicleNumber());h.setContactPerson(d.contactPerson());h.setTransportNote(d.transportNote());h.setOrderNo(customerPoOrderNo(d.orderNo()));h.setGstin(d.gstin());h.setBillingGstin(d.billingGstin());h.setDeliveryGstin(d.deliveryGstin());h.setSameAsBilling(d.sameAsBilling());h.setTransporterGstin(d.transporterGstin());List<OperationDtos.ChargeDto> charges=normalizedCharges(d);OperationDtos.ChargeDto first=charges.isEmpty()?null:charges.get(0);h.setChargeType(first==null?"":first.chargeType());h.setChargeAmount(first==null?0:first.amount());h.setContactPersonMobile(d.contactPersonMobile());h.setDocumentStatus(d.documentStatus());}
 private void copyPurchase(OperationDtos.PurchaseDto d,PurchaseHeaderEntity h){h.setInvoiceNo(d.invoiceNo());h.setInvoiceDate(d.invoiceDate());h.setSupplier(requirePartyReference(d.supplier()==null?null:d.supplier().id(),"SUPPLIER","Supplier",false));DocumentCalculationEngine.Totals totals=documentTotals(d.lines(),normalizedPurchaseCharges(d),d.gstType());h.setSubtotal(totals.itemTaxable());h.setGstAmount(totals.taxAmount());h.setTotalAmount(totals.grandTotal());h.setDiscountAmount(totals.discountAmount());h.setRemarks(d.remarks());h.setDueDate(d.dueDate());h.setDocumentStatus(d.documentStatus());h.setEmailSent(d.emailSent()?1:0);h.setWarehouse(d.warehouse());h.setPaymentTerms(d.paymentTerms());h.setCurrency(d.currency());h.setReferenceNo(d.referenceNo());h.setGstTreatment(d.gstTreatment());h.setTransporter(d.transporter());h.setLrAwbNo(d.lrAwbNo());h.setDiscountType(d.discountType());if(h.getId()==null)h.setCreatedBy(CurrentUser.require().username());h.setDeliveryDate(d.deliveryDate());h.setBillingAddress(d.billingAddress());h.setDeliveryAddress(d.deliveryAddress());h.setBillingGstin(d.billingGstin());h.setDeliveryGstin(d.deliveryGstin());h.setGstType(d.gstType());h.setTransporterGstin(d.transporterGstin());h.setVehicleNumber(d.vehicleNumber());h.setContactPerson(d.contactPerson());h.setContactPersonMobile(d.contactPersonMobile());h.setNotes(d.notes());h.setOrderNo(d.orderNo());h.setPoDate(d.poDate());h.setSameAsBilling(d.sameAsBilling());}
 private void copyFinance(OperationDtos.FinanceDto d,FinanceRegisterEntity e){if(d.voucherNo()!=null)e.setVoucherNo(d.voucherNo());e.setVoucherType(d.voucherType());e.setVoucherDate(d.voucherDate());e.setPartyId(d.partyId());e.setCategory(d.category());e.setReferenceNo(d.referenceNo());e.setAmount(d.amount());e.setPaymentMode(d.paymentMode());e.setNotes(d.notes());e.setAccountName(d.accountName());if(d.billPath()!=null)e.setBillPath(d.billPath());if(e.getReconciled()==null)e.setReconciled(0);}
 private OperationDtos.FinanceDto financeDto(FinanceRegisterEntity e){Long statementId=null;String targetType=null;Integer targetId=null;String documentNo=null;var active=reconciliationAllocations.findByFinanceEntryIdAndReversedAtIsNull(e.getId());if(!active.isEmpty()){var a=active.get(0);statementId=a.getStatementTransactionId();if(active.size()>1){targetType="MULTIPLE";documentNo="Multiple ("+active.size()+")";}else{targetType=up(a.getTargetType());targetId=a.getTargetId();if("SALE".equals(targetType)&&targetId!=null)documentNo=sales.findById(targetId).map(SalesHeaderEntity::getInvoiceNo).orElse(null);else if("PURCHASE".equals(targetType)&&targetId!=null)documentNo=purchases.findById(targetId).map(PurchaseHeaderEntity::getInvoiceNo).orElse(null);else if("EXPENSE".equals(targetType))documentNo=e.getVoucherNo();}}return new OperationDtos.FinanceDto(e.getId(),e.getVoucherNo(),e.getVoucherType(),dateOnly(e.getVoucherDate()),e.getPartyId(),e.getCategory(),e.getReferenceNo(),n(e.getAmount()),e.getPaymentMode(),e.getNotes(),e.getAccountName(),e.getBillPath(),!active.isEmpty()||n(e.getReconciled())!=0,statementId,targetType,targetId,documentNo,nv(e.getRowVersion()));}
 private boolean requiresAdminApproval(){return !"ADMIN".equalsIgnoreCase(CurrentUser.require().role());}
 private void requireAdminApprovalAuthority(){if(!"ADMIN".equalsIgnoreCase(CurrentUser.require().role()))throw new SecurityException("Admin approval is required for this action");}
 private void notifyApprovalRequired(String module,Integer recordId,String reference){
  String label="SALE".equals(module)?"Sale":"Purchase";String target="SALE".equals(module)?"/fxml/pages/SalesList.fxml":"/fxml/pages/PurchaseList.fxml";
  jdbc.update("INSERT INTO notifications(title,message,severity,category,is_read,target_fxml,reference_no,module_key,record_id,action_code,created_at) VALUES(?,?,?,?,0,?,?,?,?,?,?)",
   label+" "+reference+" • PENDING APPROVAL",reference+" was submitted by "+CurrentUser.require().username()+" and is waiting for Admin approval.","WARNING","APPROVAL",target,reference,module,recordId,"APPROVE",System.currentTimeMillis());
 }
 private void notifyApprovalDecision(String module,Integer recordId,String reference,boolean approved,String reason){
  String label="SALE".equals(module)?"Sale":"Purchase";String target="SALE".equals(module)?"/fxml/pages/SalesList.fxml":"/fxml/pages/PurchaseList.fxml";
  String message=reference+(approved?" was approved by ":" was rejected by ")+CurrentUser.require().username()+(approved?".":". "+(reason==null?"":reason));
  jdbc.update("INSERT INTO notifications(title,message,severity,category,is_read,target_fxml,reference_no,module_key,record_id,action_code,created_at) VALUES(?,?,?,?,0,?,?,?,?,?,?)",
   label+" "+reference+" • "+(approved?"APPROVED":"REJECTED"),message,approved?"SUCCESS":"ERROR","APPROVAL",target,reference,module,recordId,"VIEW",System.currentTimeMillis());
 }
 @Transactional public String nextConfiguredReference(String lookupCode,String fallback,List<String> existing){return configuredNextAtomic(lookupCode,fallback,()->existing);}
 @Transactional public String nextConfiguredReference(String lookupCode,String fallback,java.util.function.Supplier<List<String>> existingSupplier){return configuredNextAtomic(lookupCode,fallback,existingSupplier);}
 @Transactional(readOnly=true) public String previewConfiguredReference(String lookupCode,String fallback,List<String> existing){return configuredPreviewAtomic(lookupCode,fallback,()->existing);}
 @Transactional(readOnly=true) public String previewConfiguredReference(String lookupCode,String fallback,java.util.function.Supplier<List<String>> existingSupplier){return configuredPreviewAtomic(lookupCode,fallback,existingSupplier);}
 private String configuredPreviewAtomic(String lookupCode,String fallback,java.util.function.Supplier<List<String>> existingSupplier){
  String dated=datedReferenceFormat(configuredFormat(lookupCode,fallback));
  Matcher sequence=ReferenceFormatRules.sequenceMatcher(dated);
  int width=sequence.end()-sequence.start();
  String prefix=dated.substring(0,sequence.start()),suffix=dated.substring(sequence.end());
  String scope=prefix+"\u0000"+suffix;
  String counterKey=lookupCode+"|"+UUID.nameUUIDFromBytes(scope.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  long observed=1L;List<String> existing=existingSupplier==null?List.of():existingSupplier.get();
  for(String value:existing==null?List.<String>of():existing){if(value==null||!value.startsWith(prefix)||!value.endsWith(suffix))continue;String seq=value.substring(prefix.length(),value.length()-suffix.length());if(!seq.matches("\\d+"))continue;try{observed=Math.max(observed,Long.parseLong(seq)+1L);}catch(Exception ignored){}}
  List<Long> stored=jdbc.query("SELECT next_value FROM reference_counter WHERE counter_key=?",(r,i)->r.getLong(1),counterKey);
  long candidate=stored.isEmpty()?observed:Math.max(observed,stored.getFirst()+1L);
  return prefix+String.format(Locale.ROOT,"%0"+width+"d",candidate)+suffix;
 }
 private String configuredNextAtomic(String lookupCode,String fallback,java.util.function.Supplier<List<String>> existingSupplier){
  String dated=datedReferenceFormat(configuredFormat(lookupCode,fallback));
  Matcher sequence=ReferenceFormatRules.sequenceMatcher(dated);
  int width=sequence.end()-sequence.start();
  String prefix=dated.substring(0,sequence.start()),suffix=dated.substring(sequence.end());
  String scope=prefix+"\u0000"+suffix;
  String counterKey=lookupCode+"|"+UUID.nameUUIDFromBytes(scope.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  long observed=1L;List<String> existing=existingSupplier==null?List.of():existingSupplier.get();
  for(String value:existing==null?List.<String>of():existing){if(value==null||!value.startsWith(prefix)||!value.endsWith(suffix))continue;String seq=value.substring(prefix.length(),value.length()-suffix.length());if(!seq.matches("\\d+"))continue;try{observed=Math.max(observed,Long.parseLong(seq)+1L);}catch(Exception ignored){}}
  Long allocatedValue=jdbc.queryForObject("INSERT INTO reference_counter(counter_key,next_value,updated_at) VALUES(?,?,?) ON CONFLICT(counter_key) DO UPDATE SET next_value=GREATEST(reference_counter.next_value+1,EXCLUDED.next_value),updated_at=EXCLUDED.updated_at RETURNING next_value",Long.class,counterKey,observed,BusinessClock.nowUtcText());
  long allocated=allocatedValue==null?observed:allocatedValue;
  return prefix+String.format(Locale.ROOT,"%0"+width+"d",allocated)+suffix;
 }
 private String configuredFormat(String lookupCode,String fallback){
  String fmt=fallback;
  var category=categories.findByCategoryCode("REFERENCE_FORMAT").orElse(null);
  if(category!=null&&category.getActive()!=null&&category.getActive()!=0){
   for(var value:lookups.findByLookupTypeAndActiveTrueOrderByDisplayOrderAscLookupValueAsc(category.getCategoryName())){
    if(value.getLookupCode()!=null&&value.getLookupCode().equalsIgnoreCase(lookupCode)&&!blank(value.getLookupValue())){fmt=value.getLookupValue().trim();break;}
   }
  }
  return fmt;
 }
 private String datedReferenceFormat(String fmt){LocalDate t=BusinessClock.today();return fmt.replace("DD-MM-YYYY",t.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))).replace("DD/MM/YYYY",t.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).replace("YYYY-MM-DD",t.toString()).replace("YYYY",String.valueOf(t.getYear())).replace("YY",String.format(Locale.ROOT,"%02d",t.getYear()%100));}
 private static String idCsv(Collection<Integer> ids){if(ids==null||ids.isEmpty())return "";return ids.stream().filter(Objects::nonNull).map(String::valueOf).collect(java.util.stream.Collectors.joining(","));}
 private static String sqlDate(String column){return "dse_safe_date("+column+")";}
 private static String trim(String v){return v==null?"":v.trim();}
 private static final class SqlWhere{private final StringBuilder sql=new StringBuilder(" WHERE ");private final List<Object> args=new ArrayList<>();SqlWhere(String base){sql.append(base);}void add(String clause,Object... values){sql.append(" AND (").append(clause).append(')');if(values!=null)Collections.addAll(args,values);}String sql(){return sql.toString();}Object[] args(){return args.toArray();}Object[] argsWith(Object... tail){List<Object> all=new ArrayList<>(args);if(tail!=null)Collections.addAll(all,tail);return all.toArray();}}
 private static int req(Integer v){if(v==null||v<=0)throw new IllegalArgumentException("Required id missing");return v;} private static double n(Number v){return v==null?0:v.doubleValue();} private static String up(String v){return v==null?"":v.trim().toUpperCase(Locale.ROOT);} private static LocalDate saleDueDate(LocalDate invoiceDate,String paymentTerms){LocalDate base=invoiceDate==null?BusinessClock.today():invoiceDate;String terms=paymentTerms==null?"":paymentTerms.trim();if(terms.isBlank()||terms.equalsIgnoreCase("Due on Receipt"))return base;Matcher m=Pattern.compile("(\\d+)").matcher(terms);return m.find()?base.plusDays(Long.parseLong(m.group(1))):base;} private static boolean blank(String v){return v==null||v.isBlank();}
 private static String customerPoOrderNo(String value){if(blank(value))return null;String v=value.trim();return v.matches("(?i)^PO/\\d{2}-\\d{2}-\\d{4}/\\d{4}$")?null:v;}
 private static BigDecimal money(double value){return BigDecimal.valueOf(value).setScale(2,RoundingMode.HALF_UP);}
 private static BigDecimal cost(double value){return BigDecimal.valueOf(value).setScale(4,RoundingMode.HALF_UP);}
 private static String dateOnly(String v){if(v==null)return null;return v.length()>=10?v.substring(0,10):v;} private static LocalDate date(String v){try{return LocalDate.parse(dateOnly(v));}catch(Exception e){return null;}}
 private void assertVersion(long expected,Long current,String label){long actual=nv(current);if(expected!=actual)throw new ConcurrentEditException(label);}
 private long nv(Long v){return v==null?0L:v;}
}
