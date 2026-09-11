package org.example.server.documents;

import org.example.config.ConfigManager;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.service.ExcelTemplateRenderer;
import org.example.documentstudio.service.ExcelTemplateStorageService;
import org.example.documentstudio.service.PdfStudioRenderer;
import org.example.documentstudio.service.TemplateStorageService;
import org.example.invoice.pdf.TaxInvoicePdfGenerator;
import org.example.server.authority.ServerResourceService;
import org.example.server.audit.AuditService;
import org.example.server.operations.BusinessOperationsService;
import org.example.server.security.CurrentUser;
import org.example.server.support.SupportService;
import org.example.util.ProfessionalDocumentRenderer;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Single canonical renderer for Desktop Shared Client and Mobile.
 * Both clients receive the exact bytes produced here from the server-owned record,
 * server-owned business configuration and server-mirrored Document Studio default.
 */
@Service
public class CanonicalDocumentService {
    private static final String BUSINESS_ASSET="BUSINESS_ASSET";
    private final BusinessOperationsService operations;
    private final SupportService support;
    private final ServerResourceService resources;
    private final CanonicalTemplateStore templates;
    private final CanonicalDocumentDataFactory dataFactory;
    private final IssuedSalesPdfStore issuedSalesPdf;
    private final AuditService audit;

    public CanonicalDocumentService(BusinessOperationsService operations, SupportService support,
                                    ServerResourceService resources, CanonicalTemplateStore templates,
                                    CanonicalDocumentDataFactory dataFactory, IssuedSalesPdfStore issuedSalesPdf,
                                    AuditService audit) {
        this.operations=operations;this.support=support;this.resources=resources;this.templates=templates;this.dataFactory=dataFactory;
        this.issuedSalesPdf=issuedSalesPdf;this.audit=audit;
    }

    public Rendered render(String typeValue,String number,String formatValue) throws Exception {
        DocumentType type=parseType(typeValue); Format format=parseFormat(formatValue);
        if(number==null||number.isBlank())throw new IllegalArgumentException("A valid document number is required.");
        requireView(type);
        String documentNo=number.trim();
        Path work=Files.createTempDirectory("canonical-document-");
        try (Assets assets=loadAssets(work)) {
            Map<String,String> config=loadConfig();
            assets.applyTo(config);
            Path output=work.resolve(fileName(type,documentNo,format));
            if(type==DocumentType.SALES_INVOICE){
                var sale=operations.sale(documentNo);
                if(format==Format.PDF) {
                    SalesPdfLifecycle.State lifecycle=SalesPdfLifecycle.state(sale.documentStatus());
                    String finalName=fileName(type,documentNo,format);
                    if(lifecycle.official()) {
                        var issued=issuedSalesPdf.find(sale);
                        if(issued.isPresent()) {
                            safeAudit(sale.id(),"PDF_ACCESSED",documentNo+" • ISSUED_SNAPSHOT • rowVersion="+sale.rowVersion());
                            return new Rendered(finalName,format.contentType,issued.get());
                        }
                    }
                    String authority=renderSalesPdf(sale,config,assets,output);
                    PdfLifecycleWatermark.apply(output,lifecycle.watermark());
                    validate(output,format,documentNo);
                    byte[] bytes=Files.readAllBytes(output);
                    if(lifecycle.official()) issuedSalesPdf.save(sale,finalName,bytes);
                    safeAudit(sale.id(),"DOCUMENT_RENDERED",documentNo+" • PDF • "+authority+" • rowVersion="+sale.rowVersion()
                            +(lifecycle.watermark().isBlank()?"":" • watermark="+lifecycle.watermark()));
                    return new Rendered(finalName,format.contentType,bytes);
                }
                renderExcel(type,dataFactory.sales(sale,config,assets.templateImages()),output);
            }else{
                var purchase=operations.purchase(documentNo);
                if(format==Format.PDF) renderPurchasePdf(purchase,config,assets,output);
                else renderExcel(type,dataFactory.purchase(purchase,config,assets.templateImages()),output);
            }
            validate(output,format,documentNo);
            return new Rendered(fileName(type,documentNo,format),format.contentType,Files.readAllBytes(output));
        } finally { CanonicalTemplateStore.deleteTree(work); }
    }

    private String renderSalesPdf(org.example.server.operations.OperationDtos.SaleDto sale,Map<String,String> config,Assets assets,Path output) throws Exception {
        String studioFailure=null;
        try(var selected=templates.pdf(DocumentType.SALES_INVOICE).orElse(null)){
            if(selected!=null){
                try {
                    var data=dataFactory.sales(sale,config,assets.templateImages());
                    TemplateStorageService.withRoot(selected.activeRoot(),()->PdfStudioRenderer.render(selected.template(),data,output));
                    validate(output,Format.PDF,sale.invoiceNo());
                    return "PDF_STUDIO:"+safeTemplate(selected.template().getId())+":v"+selected.template().getActiveVersion();
                } catch (Exception failure) {
                    studioFailure=rootMessage(failure);
                    Files.deleteIfExists(output);
                    safeAudit(sale.id(),"PDF_STUDIO_FALLBACK",sale.invoiceNo()+" • "+studioFailure);
                }
            }
        } catch (Exception failure) {
            studioFailure=rootMessage(failure);
            Files.deleteIfExists(output);
            safeAudit(sale.id(),"PDF_STUDIO_FALLBACK",sale.invoiceNo()+" • "+studioFailure);
        }
        TaxInvoicePdfGenerator.generate(dataFactory.salesBuiltIn(sale,config,assets.logo,assets.signature),output,TaxInvoicePdfGenerator.Presentation.FULL);
        validate(output,Format.PDF,sale.invoiceNo());
        return studioFailure==null?"STANDARD:NO_ACTIVE_STUDIO":"STANDARD:FALLBACK";
    }

    private void renderPurchasePdf(org.example.server.operations.OperationDtos.PurchaseDto purchase,Map<String,String> config,Assets assets,Path output) throws Exception {
        try(var selected=templates.pdf(DocumentType.PURCHASE_INVOICE).orElse(null)){
            if(selected!=null){
                var data=dataFactory.purchase(purchase,config,assets.templateImages());
                TemplateStorageService.withRoot(selected.activeRoot(),()->PdfStudioRenderer.render(selected.template(),data,output));
                return;
            }
        }
        ConfigManager.withValues(config,()->{ProfessionalDocumentRenderer.render(output,assets.logo,dataFactory.professionalPurchase(purchase,config),ProfessionalDocumentRenderer.Kind.PURCHASE_INVOICE);return output;});
    }

    private void renderExcel(DocumentType type,org.example.documentstudio.model.TemplateData data,Path output) throws Exception {
        try(var selected=templates.excel(type).orElse(null)){
            if(selected!=null){
                ExcelTemplateStorageService.withRoot(selected.root(),()->ExcelTemplateRenderer.render(selected.template(),data,List.of(),output));
                return;
            }
        }
        ExcelTemplateRenderer.renderBuiltIn(type,data,List.of(),output);
    }

    private Map<String,String> loadConfig(){
        Map<String,String> c=new HashMap<>();
        List<String> keys=List.of(
            "company.name","company.address","company.shipAddress","company.gstin","company.phone","company.email","company.alternateEmail","company.website","company.terms","company.certificationText","company.dateFormat","company.timeZone",
            "payment.bankName","payment.branch","payment.accountNumber","payment.ifsc","payment.accountType","payment.mode","payment.accountHolder","date.format","timezone.business");
        for(String key:keys)c.put(key,support.setting(key,""));
        return c;
    }

    private Assets loadAssets(Path work){
        Path logo=asset(work,"company.logoPath","logo"),signature=asset(work,"company.signaturePath","signature"),qr=asset(work,"payment.qrImagePath","qr");
        return new Assets(work,logo,signature,qr);
    }

    private Path asset(Path work,String key,String fallbackName){
        try{
            var f=resources.get(BUSINESS_ASSET,key); String original=f.fileName()==null?"":f.fileName();
            String ext=original.contains(".")?original.substring(original.lastIndexOf('.')).replaceAll("[^A-Za-z0-9.]",""):".bin";
            Path path=work.resolve(fallbackName+ext); Files.write(path,f.content()); return path;
        }catch(Exception ignored){return null;}
    }

    private static void validate(Path file,Format format,String number)throws Exception{
        if(!Files.isRegularFile(file)||Files.size(file)<100)throw new IllegalStateException("Document generation failed for "+number+".");
        byte[] b=Files.readAllBytes(file);
        if(format==Format.PDF){if(b.length<4||b[0]!='%'||b[1]!='P'||b[2]!='D'||b[3]!='F')throw new IllegalStateException("Generated PDF is invalid for "+number+".");}
        else if(b.length<4||b[0]!='P'||b[1]!='K')throw new IllegalStateException("Generated Excel workbook is invalid for "+number+".");
    }
    private void safeAudit(Number saleId,String action,String detail){try{audit.log("SALE",saleId,action,detail);}catch(Exception ignored){}}
    private static String safeTemplate(String value){return value==null||value.isBlank()?"unknown":value.replaceAll("[^A-Za-z0-9._-]","_");}
    private static String rootMessage(Throwable error){Throwable root=error;while(root.getCause()!=null&&root.getCause()!=root)root=root.getCause();String m=root.getMessage();return m==null||m.isBlank()?root.getClass().getSimpleName():m;}

    private static DocumentType parseType(String value){String n=value==null?"":value.trim().toUpperCase(Locale.ROOT).replace('-','_').replace(' ','_');return switch(n){case "SALE","SALES","SALES_INVOICE"->DocumentType.SALES_INVOICE;case "PURCHASE","PURCHASES","PURCHASE_INVOICE"->DocumentType.PURCHASE_INVOICE;default->throw new IllegalArgumentException("Canonical output is currently supported for Sales Invoice and Purchase Invoice.");};}
    private static Format parseFormat(String value){try{return Format.valueOf(value==null?"PDF":value.trim().toUpperCase(Locale.ROOT));}catch(Exception e){throw new IllegalArgumentException("Format must be PDF or XLSX.");}}
    private static void requireView(DocumentType type){if(type==DocumentType.SALES_INVOICE)CurrentUser.requirePermission("SALES.VIEW","Render Sales Invoice");else CurrentUser.requirePermission("PURCHASE.VIEW","Render Purchase Invoice");}
    private static String fileName(DocumentType type,String no,Format f){String prefix=type==DocumentType.SALES_INVOICE?"Sales-Tax-Invoice-":"Purchase-Invoice-";return prefix+no.replaceAll("[\\\\/:*?\"<>|]","_")+f.extension;}

    private enum Format {PDF(".pdf","application/pdf"),XLSX(".xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); final String extension,contentType;Format(String e,String c){extension=e;contentType=c;}}
    public record Rendered(String fileName,String contentType,byte[] bytes){}
    private static final class Assets implements AutoCloseable{
        final Path work,logo,signature,qr; Assets(Path w,Path l,Path s,Path q){work=w;logo=l;signature=s;qr=q;}
        void applyTo(Map<String,String> c){if(logo!=null)c.put("company.logoPath",logo.toString());if(signature!=null)c.put("company.signaturePath",signature.toString());if(qr!=null)c.put("payment.qrImagePath",qr.toString());}
        Map<String,Path> templateImages(){Map<String,Path> m=new HashMap<>();if(logo!=null)m.put("company.logo",logo);if(signature!=null)m.put("company.signature",signature);if(qr!=null){m.put("payment.qr",qr);m.put("payment.qrImage",qr);}return m;}
        public void close(){}
    }
}
