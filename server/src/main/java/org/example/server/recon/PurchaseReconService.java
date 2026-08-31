package org.example.server.recon;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.persistence.entity.*;
import org.example.server.persistence.repository.*;
import org.example.server.security.CurrentUser;
import org.example.server.audit.AuditService;
import org.example.server.operations.BusinessOperationsService;
import org.example.server.web.ConcurrentEditException;
import org.example.server.util.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PurchaseReconService {
    private final ReconSupplierRepository suppliers;
    private final PurchaseReconRepository recons;
    private final PurchaseReconImportBatchRepository batches;
    private final JpaNativeRepository jdbc;
    private final AuditService audit;
    private final BusinessOperationsService operations;

    public PurchaseReconService(ReconSupplierRepository suppliers,PurchaseReconRepository recons,PurchaseReconImportBatchRepository batches,JpaNativeRepository jdbc,AuditService audit,BusinessOperationsService operations){this.suppliers=suppliers;this.recons=recons;this.batches=batches;this.jdbc=jdbc;this.audit=audit;this.operations=operations;}

    @Transactional(readOnly=true)
    public List<PurchaseReconDtos.SupplierDto> suppliers(){return searchSuppliers("",40);}
    @Transactional(readOnly=true)
    public List<PurchaseReconDtos.SupplierDto> searchSuppliers(String q,int limit){CurrentUser.requirePermission("RECON_SUPPLIER.VIEW","View Recon Suppliers");int safeLimit=Math.max(10,Math.min(limit,100));String query=safe(q).trim();Map<Integer,Long> counts=new HashMap<>();jdbc.query("SELECT recon_supplier_id,COUNT(*) FROM purchase_recon GROUP BY recon_supplier_id",r->{counts.put(r.getInt(1),r.getLong(2));});return suppliers.search(query,PageRequest.of(0,safeLimit)).stream().map(x->supplierDto(x,counts.getOrDefault(x.getId(),0L))).toList();}
    @Transactional(readOnly=true)
    public PurchaseReconDtos.SupplierDto supplier(Integer id){CurrentUser.requirePermission("RECON_SUPPLIER.VIEW","View Recon Suppliers");var entity=suppliers.findById(id).orElseThrow(()->new IllegalArgumentException("Recon Supplier not found."));return supplierDto(entity,recons.countByReconSupplier_Id(id));}
    @Transactional
    public void deleteSupplier(Integer id){CurrentUser.requirePermission("RECON_SUPPLIER.DELETE","Delete Recon Supplier");var entity=suppliers.findById(id).orElseThrow(()->new IllegalArgumentException("Recon Supplier not found."));long used=recons.countByReconSupplier_Id(id);if(used>0)throw new IllegalStateException("This Recon Supplier is used by "+used+" Purchase Recon record(s) and cannot be deleted.");suppliers.delete(entity);audit.log("RECON_SUPPLIER",id,"DELETED",entity.getReconSupplierRef()+" • "+entity.getLegalName());}

    @Transactional
    public PurchaseReconDtos.SupplierDto saveSupplier(PurchaseReconDtos.SupplierSaveRequest request){
        if(request==null||blank(request.legalName()))throw new IllegalArgumentException("Recon Supplier name is required.");
        boolean creating=request.id()==null;
        CurrentUser.requirePermission(creating?"RECON_SUPPLIER.CREATE":"RECON_SUPPLIER.EDIT", creating?"Create Recon Supplier":"Edit Recon Supplier");
        ReconSupplierEntity entity=creating?new ReconSupplierEntity():suppliers.findById(request.id()).orElseThrow(()->new IllegalArgumentException("Recon Supplier not found."));
        if(!creating)assertVersion(request.rowVersion(),entity.getRowVersion(),"Recon Supplier "+entity.getReconSupplierRef());
        Integer entityId=entity.getId();
        String gstin=safe(request.gstin()).trim().toUpperCase(Locale.ROOT);
        if(!gstin.isBlank()){
            suppliers.findByNormalizedGstin(gstin).filter(x->!Objects.equals(x.getId(),entityId)).ifPresent(x->{throw new IllegalStateException("GSTIN already belongs to Recon Supplier "+x.getReconSupplierRef()+" - "+x.getLegalName());});
        }else{
            suppliers.findByNormalizedNameKey(normalizeName(request.legalName())).stream().filter(x->!Objects.equals(x.getId(),entityId)).findFirst().ifPresent(x->{throw new IllegalStateException("Recon Supplier already exists: "+x.getReconSupplierRef()+" - "+x.getLegalName());});
        }
        if(entity.getId()==null){entity.setReconSupplierRef(nextReconSupplierReference());entity.setCreatedBy(CurrentUser.require().username());entity.setSource("MANUAL");}
        String status=blank(request.status())?"ACTIVE":request.status().trim().toUpperCase(Locale.ROOT);if(!Set.of("ACTIVE","INACTIVE").contains(status))throw new IllegalArgumentException("Recon Supplier status must be ACTIVE or INACTIVE.");
        entity.setLegalName(request.legalName().trim());entity.setGstin(gstin);entity.setPan(safe(request.pan()).trim().toUpperCase(Locale.ROOT));entity.setContactPerson(trim(request.contactPerson()));entity.setPhone(trim(request.phone()));entity.setEmail(trim(request.email()));entity.setNotes(trim(request.notes()));entity.setStatus(status);entity.setUpdatedBy(CurrentUser.require().username());
        ReconSupplierEntity saved=suppliers.saveAndFlush(entity);audit.log("RECON_SUPPLIER",saved.getId(),creating?"CREATED":"UPDATED",saved.getReconSupplierRef()+" • "+saved.getLegalName());return supplierDto(saved);
    }

    @Transactional(readOnly=true)
    public List<PurchaseReconDtos.ReconDto> recons(){CurrentUser.requirePermission("PURCHASE_RECON.VIEW","View Purchase Recon");return recons.findAllByOrderByInvoiceDateDescIdDesc().stream().map(this::reconDto).toList();}
    @Transactional(readOnly=true) public PurchaseReconDtos.Page page(int page,int size,String q,String status){CurrentUser.requirePermission("PURCHASE_RECON.VIEW","View Purchase Recon");int safeSize=Math.max(10,Math.min(size,200)),safePage=Math.max(0,page);String query=safe(q).trim(),state=safe(status).trim();if(state.toUpperCase(Locale.ROOT).startsWith("ALL"))state="";var result=recons.searchPage(query,state,PageRequest.of(safePage,safeSize,Sort.by(Sort.Order.desc("invoiceDate"),Sort.Order.desc("id"))));if(result.getTotalPages()>0&&safePage>=result.getTotalPages()){safePage=result.getTotalPages()-1;result=recons.searchPage(query,state,PageRequest.of(safePage,safeSize,Sort.by(Sort.Order.desc("invoiceDate"),Sort.Order.desc("id"))));}return new PurchaseReconDtos.Page(result.getContent().stream().map(this::reconDto).toList(),safePage,safeSize,result.getTotalElements(),result.getTotalPages(),metrics());}
    @Transactional(readOnly=true) public PurchaseReconDtos.ReconDto recon(Integer id){CurrentUser.requirePermission("PURCHASE_RECON.VIEW","View Purchase Recon");return reconDto(recons.findById(id).orElseThrow(()->new IllegalArgumentException("Purchase Recon record not found.")));}

    @Transactional
    public void deleteRecon(Integer id){CurrentUser.requirePermission("PURCHASE_RECON.DELETE","Delete Purchase Recon");var entity=recons.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Purchase Recon record not found."));if(n(entity.getLinkedAmount())>.009)throw new IllegalStateException("This Purchase Recon is linked to Bank Statement. Reverse / Unmatch the bank reconciliation before deleting it.");String ref=entity.getReconRef();String invoice=entity.getSupplierInvoiceNo();jdbc.update("DELETE FROM document_attachment WHERE document_type='PURCHASE_RECON' AND document_id=?",id);recons.delete(entity);audit.log("PURCHASE_RECON",id,"DELETED",ref+" • "+invoice);}

    @Transactional
    public PurchaseReconDtos.ReconDto saveRecon(PurchaseReconDtos.ReconSaveRequest request){
        if(request==null||request.supplierId()==null)throw new IllegalArgumentException("Recon Supplier is required.");
        boolean creating=request.id()==null;
        CurrentUser.requirePermission(creating?"PURCHASE_RECON.CREATE":"PURCHASE_RECON.EDIT", creating?"Create Purchase Recon":"Edit Purchase Recon");
        if(blank(request.supplierInvoiceNo()))throw new IllegalArgumentException("Supplier Invoice No. is required.");
        LocalDate date=parseDate(request.invoiceDate());if(date==null)throw new IllegalArgumentException("Invoice Date is required and must be valid.");
        if(!Double.isFinite(request.invoiceValue())||request.invoiceValue()<=0)throw new IllegalArgumentException("Invoice Value must be greater than zero.");
        ReconSupplierEntity supplier=suppliers.findById(request.supplierId()).orElseThrow(()->new IllegalArgumentException("Recon Supplier not found."));
        Integer id=request.id();PurchaseReconEntity entity=id==null?new PurchaseReconEntity():recons.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Purchase Recon record not found."));if(id!=null)assertVersion(request.rowVersion(),entity.getRowVersion(),"Purchase Recon "+entity.getReconRef());
        double linked=n(entity.getLinkedAmount());
        if(linked>.009&&materialReconChange(entity,supplier,request,date))throw new IllegalStateException("This Purchase Recon is linked to Bank Statement. Reverse / Unmatch the bank reconciliation before changing supplier, invoice date, invoice number or financial amounts.");
        String fy=financialYear(date);if(recons.duplicateBusinessKey(supplier.getId(),request.supplierInvoiceNo().trim(),fy,id))throw new IllegalStateException("A Purchase Recon already exists for this Recon Supplier, Supplier Invoice No. and financial year.");
        if(request.invoiceValue()+.01<linked)throw new IllegalStateException("Invoice Value cannot be reduced below the amount already reconciled from Bank Statement ("+money(linked)+").");
        if(entity.getId()==null){entity.setReconRef(nextPurchaseReconReference());entity.setCreatedBy(CurrentUser.require().username());entity.setSource("MANUAL");entity.setLinkedAmount(0d);}
        applyReconValues(entity,supplier,request.supplierInvoiceNo(),date,request.taxableValue(),request.cgst(),request.sgst(),request.igst(),request.otherAdjustment(),request.invoiceValue(),request.notes());
        entity.setUpdatedBy(CurrentUser.require().username());syncStatus(entity);
        entity=recons.saveAndFlush(entity);audit.log("PURCHASE_RECON",entity.getId(),creating?"CREATED":"UPDATED",entity.getReconRef()+" • "+entity.getSupplierInvoiceNo());return reconDto(entity);
    }

    @Transactional
    public PurchaseReconDtos.ImportResult importRows(PurchaseReconDtos.ImportRequest request){
        CurrentUser.requirePermission("PURCHASE_RECON.IMPORT","Purchase Recon import");
        if(request==null||request.rows()==null||request.rows().isEmpty())throw new IllegalArgumentException("No Purchase Recon rows were supplied.");
        if(blank(request.sourceFingerprint()))throw new IllegalArgumentException("Import source fingerprint is required.");

        List<PurchaseReconDtos.ImportRowResult> details=new ArrayList<>();
        Map<String,ReconSupplierEntity> staged=new LinkedHashMap<>();
        Map<String,String> seenBusinessKeys=new LinkedHashMap<>();
        int imported=0,updated=0,alreadyCurrent=0,newSuppliers=0,existingSuppliers=0,duplicates=0,conflicts=0,warnings=0,ignored=0;
        PurchaseReconImportBatchEntity batch=null;
        if(!request.dryRun()){
            batch=new PurchaseReconImportBatchEntity();
            batch.setSourceFileName(blank(request.sourceFileName())?"Purchase Recon import":request.sourceFileName());
            batch.setSourceFingerprint(request.sourceFingerprint());
            batch.setImportNote(trim(request.importNote()));
            batch.setImportedBy(CurrentUser.require().username());
            batch.setTotalRows(request.rows().size());batch.setImportedRows(0);batch.setDuplicateRows(0);batch.setWarningRows(0);batch.setIgnoredRows(0);
            batch=batches.save(batch);
        }

        for(PurchaseReconDtos.ImportRow row:request.rows()){
            if(row==null){ignored++;continue;}
            String sourceSheet=safe(row.sourceSheet()).trim();
            String supplierName=safe(row.supplierName()).trim(),invoice=safe(row.supplierInvoiceNo()).trim(),gstin=safe(row.supplierGstin()).trim().toUpperCase(Locale.ROOT);
            LocalDate date=parseDate(row.invoiceDate());
            if(isSummaryRow(row,supplierName,gstin,invoice)){
                ignored++;details.add(importResult(row,"PASSED","IGNORED",supplierName,"",invoice,"Summary / totals row ignored.",false));continue;
            }
            if((supplierName.isBlank()&&gstin.isBlank())||invoice.isBlank()||date==null||row.invoiceValue()<=0||!Double.isFinite(row.invoiceValue())){
                ignored++;details.add(importResult(row,"FAILED","INVALID",supplierName,"",invoice,"Supplier, Invoice No., valid Invoice Date and positive Invoice Value are required.",false));continue;
            }

            String gstinKey=gstin.isBlank()?"":"GSTIN:"+gstin,nameKey=supplierName.isBlank()?"":"NAME:"+normalizeName(supplierName);
            ReconSupplierEntity supplier=!gstinKey.isBlank()?staged.get(gstinKey):null;
            if(supplier==null&&!nameKey.isBlank())supplier=staged.get(nameKey);
            boolean created=false;
            if(supplier==null){
                if(!gstin.isBlank()){
                    supplier=suppliers.findByNormalizedGstin(gstin).orElse(null);
                    if(supplier==null&&!supplierName.isBlank()){
                        List<ReconSupplierEntity> sameName=suppliers.findByNormalizedNameKey(normalizeName(supplierName));
                        if(!sameName.isEmpty()){
                            ReconSupplierEntity candidate=sameName.getFirst();
                            String candidateGstin=up(candidate.getGstin());
                            if(!candidateGstin.isBlank()&&!candidateGstin.equals(gstin)){
                                conflicts++;details.add(importResult(row,"FAILED","SUPPLIER IDENTITY CONFLICT",supplierName,candidate.getReconSupplierRef(),invoice,
                                    "Supplier name matches "+candidate.getReconSupplierRef()+" but GSTIN differs. Review the supplier identity before import.",false));continue;
                            }
                            supplier=candidate;
                            if(candidateGstin.isBlank()&&!request.dryRun()){
                                supplier.setGstin(gstin);supplier.setUpdatedBy(CurrentUser.require().username());supplier=suppliers.save(supplier);
                            }
                        }
                    }
                }else if(!supplierName.isBlank()){
                    List<ReconSupplierEntity> sameName=suppliers.findByNormalizedNameKey(normalizeName(supplierName));
                    if(!sameName.isEmpty())supplier=sameName.getFirst();
                }
                if(supplier!=null)existingSuppliers++;
                else{
                    created=true;newSuppliers++;
                    supplier=new ReconSupplierEntity();supplier.setLegalName(supplierName.isBlank()?gstin:supplierName);supplier.setGstin(gstin);supplier.setStatus("ACTIVE");supplier.setSource("IMPORT");supplier.setCreatedBy(CurrentUser.require().username());supplier.setUpdatedBy(CurrentUser.require().username());
                    if(!request.dryRun()){supplier.setReconSupplierRef(nextReconSupplierReference());supplier.setImportBatchId(batch.getId());supplier=suppliers.save(supplier);}else supplier.setReconSupplierRef("NEW");
                }
            }
            if(!gstin.isBlank()&&supplier!=null){
                String knownGstin=up(supplier.getGstin());
                if(!knownGstin.isBlank()&&!knownGstin.equals(gstin)){
                    conflicts++;details.add(importResult(row,"FAILED","SUPPLIER IDENTITY CONFLICT",supplierName,supplier.getReconSupplierRef(),invoice,
                        "This workbook resolves the same supplier name to different GSTIN values. Review the supplier identity before import.",false));continue;
                }
                if(knownGstin.isBlank()){
                    supplier.setGstin(gstin);
                    if(!request.dryRun()&&supplier.getId()!=null){supplier.setUpdatedBy(CurrentUser.require().username());supplier=suppliers.save(supplier);}
                }
            }
            if(!gstinKey.isBlank())staged.put(gstinKey,supplier);if(!nameKey.isBlank())staged.put(nameKey,supplier);
            String supplierKey=supplier.getId()!=null?"ID:"+supplier.getId():!blank(supplier.getGstin())?"GSTIN:"+up(supplier.getGstin()):"NAME:"+normalizeName(supplier.getLegalName());
            String fy=financialYear(date),businessKey=supplierKey+"|"+invoice.toUpperCase(Locale.ROOT)+"|"+fy;
            String signature=importSignature(row,date);
            String priorSignature=seenBusinessKeys.putIfAbsent(businessKey,signature);
            if(priorSignature!=null){
                if(priorSignature.equals(signature)){
                    duplicates++;details.add(importResult(row,"PASSED","DUPLICATE IN FILE",supplierName,supplier.getReconSupplierRef(),invoice,"The same Purchase Recon row appears more than once in this workbook; later occurrence skipped.",false));
                }else{
                    conflicts++;details.add(importResult(row,"FAILED","CONFLICT",supplierName,supplier.getReconSupplierRef(),invoice,"The same supplier invoice appears more than once in this workbook with different values. Correct the workbook before import.",false));
                }
                continue;
            }

            PurchaseReconEntity existing=!created&&supplier.getId()!=null?recons.findBusinessKeyForUpdate(supplier.getId(),invoice,fy).orElse(null):null;
            double diff=round2(row.invoiceValue()-row.taxableValue()-row.cgst()-row.sgst()-row.igst());
            boolean warning=Math.abs(diff)>1.00;

            if(existing!=null){
                if(reconMatchesImport(existing,row,date)){
                    alreadyCurrent++;
                    details.add(importResult(row,"PASSED","ALREADY CURRENT",supplierName,supplier.getReconSupplierRef(),invoice,"Existing Purchase Recon already matches these imported values; no change required.",existing.getTaxReviewRequired()!=null&&existing.getTaxReviewRequired()!=0));
                    continue;
                }
                if(n(existing.getLinkedAmount())>.009){
                    conflicts++;
                    details.add(importResult(row,"FAILED","BANK-LINKED",supplierName,supplier.getReconSupplierRef(),invoice,"Existing Purchase Recon has Bank Statement reconciliation. Reverse / Unmatch it before importing corrected financial values.",false));
                    continue;
                }
                if(warning)warnings++;
                if(!request.dryRun()){
                    String oldSummary=money(n(existing.getInvoiceValue()));
                    applyReconValues(existing,supplier,invoice,date,row.taxableValue(),row.cgst(),row.sgst(),row.igst(),0d,row.invoiceValue(),existing.getNotes());
                    existing.setSource("IMPORT");existing.setImportBatch(batch);existing.setSourceSheet(sourceSheet);existing.setSourceRow(row.sourceRow());existing.setUpdatedBy(CurrentUser.require().username());syncStatus(existing);existing=recons.saveAndFlush(existing);
                    audit.log("PURCHASE_RECON",existing.getId(),"UPDATED_BY_IMPORT",existing.getReconRef()+" • "+invoice+" • Invoice Value "+oldSummary+" -> "+money(row.invoiceValue()));
                }
                imported++;updated++;
                details.add(importResult(row,"PASSED","UPDATE",supplierName,supplier.getReconSupplierRef(),invoice,warning?"Existing Purchase Recon will be updated; tax columns leave a difference of "+money(diff)+".":(request.dryRun()?"Existing unlinked Purchase Recon will be updated.":"Existing Purchase Recon updated from import."),warning));
                continue;
            }

            if(warning)warnings++;
            if(!request.dryRun()){
                PurchaseReconEntity recon=new PurchaseReconEntity();recon.setReconRef(nextPurchaseReconReference());recon.setReconSupplier(supplier);recon.setLinkedAmount(0d);recon.setSource("IMPORT");recon.setImportBatch(batch);recon.setSourceSheet(sourceSheet);recon.setSourceRow(row.sourceRow());recon.setCreatedBy(CurrentUser.require().username());recon.setUpdatedBy(CurrentUser.require().username());applyReconValues(recon,supplier,invoice,date,row.taxableValue(),row.cgst(),row.sgst(),row.igst(),0d,row.invoiceValue(),warning?"Imported with tax difference requiring review.":null);syncStatus(recon);recon=recons.saveAndFlush(recon);audit.log("PURCHASE_RECON",recon.getId(),"CREATED_BY_IMPORT",recon.getReconRef()+" • "+invoice);
            }
            imported++;
            String action=created?"NEW SUPPLIER + NEW":"NEW";
            details.add(importResult(row,"PASSED",action,supplierName,supplier.getReconSupplierRef(),invoice,warning?"New Purchase Recon will be created with tax review; difference "+money(diff)+".":(request.dryRun()?"Ready to create Purchase Recon.":"Purchase Recon imported."),warning));
        }
        if(batch!=null){batch.setImportedRows(imported);batch.setDuplicateRows(duplicates+alreadyCurrent);batch.setWarningRows(warnings);batch.setIgnoredRows(ignored+conflicts);batches.save(batch);}
        return new PurchaseReconDtos.ImportResult(request.rows().size(),imported,updated,alreadyCurrent,newSuppliers,existingSuppliers,duplicates,conflicts,warnings,ignored,List.copyOf(details));
    }

    @Transactional(readOnly=true)
    public PurchaseReconDtos.Metrics metrics(){List<Number[]> rows=jdbc.query("SELECT COUNT(*),COALESCE(SUM(CASE WHEN UPPER(COALESCE(status,''))='OPEN' THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN UPPER(COALESCE(status,''))='PARTIAL' THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN UPPER(COALESCE(status,''))='RECONCILED' THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN COALESCE(tax_review_required,0)<>0 THEN 1 ELSE 0 END),0),COALESCE(SUM(invoice_value),0),COALESCE(SUM(linked_amount),0) FROM purchase_recon",(r,i)->new Number[]{(Number)r.getObject(1),(Number)r.getObject(2),(Number)r.getObject(3),(Number)r.getObject(4),(Number)r.getObject(5),(Number)r.getObject(6),(Number)r.getObject(7)});Number[] x=rows.getFirst();long total=x[0].longValue(),open=x[1].longValue(),partial=x[2].longValue(),done=x[3].longValue(),review=x[4].longValue();double invoice=x[5].doubleValue(),linked=x[6].doubleValue();return new PurchaseReconDtos.Metrics(total,open,partial,done,review,invoice,linked,Math.max(0,invoice-linked));}

    @Transactional(readOnly=true)
    public List<PurchaseReconDtos.BankLinkDto> bankLinks(Integer reconId){
        return jdbc.query("SELECT a.id,a.statement_transaction_id,COALESCE(t.transaction_date,''),COALESCE(t.original_reference,''),COALESCE(a.allocated_amount,0),a.finance_entry_id,COALESCE(f.voucher_no,''),COALESCE(a.created_at,'') FROM bank_reconciliation_allocation a JOIN bank_statement_transaction t ON t.id=a.statement_transaction_id LEFT JOIN finance_register f ON f.id=a.finance_entry_id WHERE a.target_type='PURCHASE_RECON' AND a.target_id=? AND a.reversed_at IS NULL ORDER BY a.id DESC",
                (r,i)->new PurchaseReconDtos.BankLinkDto(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getDouble(5),r.getObject(6)==null?null:r.getInt(6),r.getString(7),r.getString(8)),reconId);
    }

    private PurchaseReconDtos.SupplierDto supplierDto(ReconSupplierEntity s){long count=s.getId()==null?0:recons.countByReconSupplier_Id(s.getId());return supplierDto(s,count);}private PurchaseReconDtos.SupplierDto supplierDto(ReconSupplierEntity s,long count){return new PurchaseReconDtos.SupplierDto(s.getId(),s.getReconSupplierRef(),s.getLegalName(),safe(s.getGstin()),safe(s.getPan()),safe(s.getContactPerson()),safe(s.getPhone()),safe(s.getEmail()),safe(s.getNotes()),safe(s.getStatus()),safe(s.getSource()),count,safe(s.getCreatedAt()),safe(s.getUpdatedAt()),nv(s.getRowVersion()));}
    private PurchaseReconDtos.ReconDto reconDto(PurchaseReconEntity r){double value=n(r.getInvoiceValue()),linked=n(r.getLinkedAmount());return new PurchaseReconDtos.ReconDto(r.getId(),r.getReconRef(),r.getReconSupplier().getId(),r.getReconSupplier().getReconSupplierRef(),r.getSupplierNameSnapshot(),safe(r.getSupplierGstinSnapshot()),r.getSupplierInvoiceNo(),r.getInvoiceDate()==null?"":r.getInvoiceDate().toString(),r.getFinancialYear(),n(r.getTaxableValue()),n(r.getCgst()),n(r.getSgst()),n(r.getIgst()),n(r.getOtherAdjustment()),value,linked,Math.max(0,value-linked),n(r.getTaxDifference()),r.getTaxReviewRequired()!=null&&r.getTaxReviewRequired()!=0,r.getStatus(),r.getSource(),r.getImportBatch()==null?null:r.getImportBatch().getId(),safe(r.getSourceSheet()),r.getSourceRow(),safe(r.getNotes()),safe(r.getCreatedAt()),safe(r.getUpdatedAt()),bankLinks(r.getId()),nv(r.getRowVersion()));}
    private PurchaseReconDtos.ImportRowResult importResult(PurchaseReconDtos.ImportRow row,String status,String action,String supplierName,String supplierReference,String invoice,String message,boolean warning){return new PurchaseReconDtos.ImportRowResult(safe(row.sourceSheet()),row.sourceRow(),status,action,supplierName,safe(supplierReference),invoice,message,warning);}
    private boolean reconMatchesImport(PurchaseReconEntity r,PurchaseReconDtos.ImportRow row,LocalDate date){return Objects.equals(r.getInvoiceDate(),date)&&Math.abs(n(r.getTaxableValue())-row.taxableValue())<=.009&&Math.abs(n(r.getCgst())-row.cgst())<=.009&&Math.abs(n(r.getSgst())-row.sgst())<=.009&&Math.abs(n(r.getIgst())-row.igst())<=.009&&Math.abs(n(r.getOtherAdjustment()))<=.009&&Math.abs(n(r.getInvoiceValue())-row.invoiceValue())<=.009;}
    private static String importSignature(PurchaseReconDtos.ImportRow row,LocalDate date){return date+"|"+round2(row.taxableValue())+"|"+round2(row.cgst())+"|"+round2(row.sgst())+"|"+round2(row.igst())+"|"+round2(0d)+"|"+round2(row.invoiceValue());}
    private void applyReconValues(PurchaseReconEntity r,ReconSupplierEntity supplier,String invoice,LocalDate date,double taxable,double cgst,double sgst,double igst,double other,double value,String notes){r.setReconSupplier(supplier);r.setSupplierNameSnapshot(supplier.getLegalName());r.setSupplierGstinSnapshot(supplier.getGstin());r.setSupplierInvoiceNo(invoice.trim());r.setInvoiceDate(date);r.setFinancialYear(financialYear(date));r.setTaxableValue(validMoney(taxable));r.setCgst(validMoney(cgst));r.setSgst(validMoney(sgst));r.setIgst(validMoney(igst));r.setOtherAdjustment(validMoney(other));r.setInvoiceValue(validMoney(value));double diff=round2(value-taxable-cgst-sgst-igst-other);r.setTaxDifference(diff);r.setTaxReviewRequired(Math.abs(diff)>1.00?1:0);r.setNotes(trim(notes));}
    public void syncStatus(PurchaseReconEntity r){double linked=n(r.getLinkedAmount()),value=n(r.getInvoiceValue());if(linked<=.009)r.setStatus(r.getTaxReviewRequired()!=null&&r.getTaxReviewRequired()!=0?"NEEDS REVIEW":"OPEN");else if(linked+.01>=value)r.setStatus("RECONCILED");else r.setStatus("PARTIAL");}
    private boolean materialReconChange(PurchaseReconEntity r,ReconSupplierEntity supplier,PurchaseReconDtos.ReconSaveRequest request,LocalDate date){return r.getReconSupplier()==null||!Objects.equals(r.getReconSupplier().getId(),supplier.getId())||!up(r.getSupplierInvoiceNo()).equals(up(request.supplierInvoiceNo()))||!Objects.equals(r.getInvoiceDate(),date)||Math.abs(n(r.getTaxableValue())-request.taxableValue())>.009||Math.abs(n(r.getCgst())-request.cgst())>.009||Math.abs(n(r.getSgst())-request.sgst())>.009||Math.abs(n(r.getIgst())-request.igst())>.009||Math.abs(n(r.getOtherAdjustment())-request.otherAdjustment())>.009||Math.abs(n(r.getInvoiceValue())-request.invoiceValue())>.009;}

    private String nextReconSupplierReference(){return operations.nextConfiguredReference("REF_RECON_SUPPLIER","RSP-YYYY-XXXXX",()->suppliers.findAllByOrderByLegalNameAscIdAsc().stream().map(ReconSupplierEntity::getReconSupplierRef).filter(Objects::nonNull).toList());}
    private String nextPurchaseReconReference(){return operations.nextConfiguredReference("REF_PURCHASE_RECON","PRC-YYYY-XXXXX",()->recons.findAll().stream().map(PurchaseReconEntity::getReconRef).filter(Objects::nonNull).toList());}
    private static String financialYear(LocalDate d){int start=d.getMonthValue()>=4?d.getYear():d.getYear()-1;return start+"-"+String.format(Locale.ROOT,"%02d",(start+1)%100);}
    private static LocalDate parseDate(String v){if(blank(v))return null;String s=v.trim();for(DateTimeFormatter f:List.of(DateTimeFormatter.ISO_LOCAL_DATE,DateTimeFormatter.ofPattern("dd/MM/uuuu"),DateTimeFormatter.ofPattern("dd-MM-uuuu"),DateTimeFormatter.ofPattern("d/M/uuuu"),DateTimeFormatter.ofPattern("d-M-uuuu"))){try{return LocalDate.parse(s,f);}catch(Exception ignored){}}return null;}
    private static boolean isSummaryRow(PurchaseReconDtos.ImportRow row,String name,String gstin,String invoice){String x=up(name+" "+invoice);if(x.equals("TOTAL")||x.startsWith("TOTAL ")||x.contains("GRAND TOTAL"))return true;return blank(name)&&blank(gstin)&&blank(invoice)&&blank(row.invoiceDate());}
    private static double validMoney(double v){if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException("Purchase Recon monetary values cannot be negative or invalid.");return round2(v);}
    private static void assertVersion(long expected,Long current,String label){long actual=nv(current);if(expected!=actual)throw new ConcurrentEditException(label);} private static long nv(Long v){return v==null?0L:v;} private static double round2(double v){return Math.round(v*100d)/100d;} private static double n(Number n){return n==null?0:n.doubleValue();} private static String money(double v){return String.format(Locale.ENGLISH,"%,.2f",v);} private static boolean blank(String v){return v==null||v.isBlank();} private static String safe(String v){return v==null?"":v;} private static String trim(String v){return blank(v)?null:v.trim();} private static String up(String v){return safe(v).trim().toUpperCase(Locale.ROOT);} private static String normalizeName(String v){return up(v).replaceAll("[^A-Z0-9]","");}
}
