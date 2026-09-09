package org.example.server.documents;

import org.example.documentstudio.model.TemplateCharge;
import org.example.documentstudio.model.TemplateData;
import org.example.invoice.calculation.AmountInWordsConverter;
import org.example.invoice.calculation.InvoiceTaxCalculator;
import org.example.invoice.model.*;
import org.example.server.operations.OperationDtos;
import org.example.server.persistence.entity.ItemEntity;
import org.example.server.persistence.entity.PartyEntity;
import org.example.server.persistence.repository.ItemRepository;
import org.example.server.persistence.repository.PartyRepository;
import org.example.util.ProfessionalDocumentRenderer;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Server-side equivalent of the desktop TemplateDataFactory for canonical Sales/Purchase output. */
@Component
public class CanonicalDocumentDataFactory {
    private final ItemRepository items;
    private final PartyRepository parties;
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    public CanonicalDocumentDataFactory(ItemRepository items, PartyRepository parties) { this.items=items; this.parties=parties; }

    public TemplateData sales(OperationDtos.SaleDto sale, Map<String,String> config, Map<String,Path> images) {
        Map<String,String> v = base(config); Map<String,Path> imgs = new LinkedHashMap<>(images);
        put(v,"sales.number",sale.invoiceNo()); put(v,"sales.date",date(sale.invoiceDate(),config)); put(v,"sales.dueDate",date(sale.dueDate(),config));
        put(v,"sales.invoiceType",sale.invoiceType()); put(v,"sales.referenceNo",sale.referenceNo()); put(v,"sales.orderNo",sale.orderNo()); put(v,"sales.poDate",date(sale.poDate(),config));
        put(v,"sales.paymentTerms",sale.paymentTerms()); put(v,"sales.transporter",sale.transporter()); put(v,"sales.transporterGstin",sale.transporterGstin()); put(v,"sales.vehicleNo",sale.vehicleNumber());
        put(v,"sales.doorDelivery",sale.doorDelivery()); put(v,"sales.contactPerson",sale.contactPerson()); put(v,"sales.contactMobile",sale.contactPersonMobile()); put(v,"sales.transportNote",sale.transportNote());
        put(v,"sales.salesperson",sale.salesperson()); put(v,"sales.source",sale.source()); put(v,"sales.notes",sale.notes()); put(v,"sales.remarks",sale.remarks()); put(v,"sales.documentStatus",sale.documentStatus());
        put(v,"sales.paymentStatus",sale.paymentStatus()); put(v,"sales.emailStatus",sale.emailSent()?"SENT":"PENDING"); put(v,"sales.whatsappStatus",sale.whatsappSent()?"SENT":"PENDING");
        put(v,"sales.billingAddress",sale.billingAddress()); put(v,"sales.deliveryAddress",sale.deliveryAddress()); put(v,"sales.shippingAddress",sale.deliveryAddress());
        put(v,"sales.billingGstin",sale.billingGstin()); put(v,"sales.deliveryGstin",sale.deliveryGstin()); put(v,"sales.shippingGstin",sale.deliveryGstin()); put(v,"sales.gstin",sale.gstin()); put(v,"sales.gstType",sale.gstType()); put(v,"sales.createdAt",sale.createdAt());
        double qty = sale.lines()==null ? sale.quantity() : sale.lines().stream().filter(Objects::nonNull).mapToDouble(OperationDtos.LineDto::quantity).sum();
        put(v,"sales.totalQuantity",number(qty)); put(v,"sales.sameAsBilling",sale.sameAsBilling()?"Yes":"No"); party(v,sale.customer(),"customer"); enrichPartyContact(v,sale.customer(),"customer");
        totals(v,sale.subtotal(),sale.discountAmount(),sale.gstAmount(),sale.totalAmount(),sale.paidAmount(),sale.gstType());
        return new TemplateData(v,imgs,lineItems(sale.lines()),charges(sale.charges()),safe(sale.gstType()));
    }

    public TemplateData purchase(OperationDtos.PurchaseDto p, Map<String,String> config, Map<String,Path> images) {
        Map<String,String> v=base(config); Map<String,Path> imgs=new LinkedHashMap<>(images);
        put(v,"purchase.number",p.invoiceNo()); put(v,"purchase.date",date(p.invoiceDate(),config)); put(v,"purchase.dueDate",date(p.dueDate(),config)); put(v,"purchase.deliveryDate",date(p.deliveryDate(),config));
        put(v,"purchase.referenceNo",p.referenceNo()); put(v,"purchase.paymentTerms",p.paymentTerms()); put(v,"purchase.gstTreatment",p.gstTreatment()); put(v,"purchase.warehouse",p.warehouse()); put(v,"purchase.currency",p.currency());
        put(v,"purchase.transporter",p.transporter()); put(v,"purchase.transporterGstin",p.transporterGstin()); put(v,"purchase.vehicleNo",p.vehicleNumber()); put(v,"purchase.contactPerson",p.contactPerson()); put(v,"purchase.contactMobile",p.contactPersonMobile());
        put(v,"purchase.lrAwbNo",p.lrAwbNo()); put(v,"purchase.remarks",p.remarks()); put(v,"purchase.notes",p.notes()); put(v,"purchase.billingAddress",p.billingAddress()); put(v,"purchase.deliveryAddress",p.deliveryAddress());
        put(v,"purchase.billingGstin",p.billingGstin()); put(v,"purchase.deliveryGstin",p.deliveryGstin()); put(v,"purchase.gstType",p.gstType()); put(v,"purchase.orderNo",p.orderNo()); put(v,"purchase.poDate",date(p.poDate(),config));
        put(v,"purchase.createdBy",p.createdBy()); put(v,"purchase.documentStatus",p.documentStatus()); put(v,"purchase.paymentStatus",p.paymentStatus()); party(v,p.supplier(),"supplier"); enrichPartyContact(v,p.supplier(),"supplier");
        totals(v,p.subtotal(),p.discountAmount(),p.gstAmount(),p.totalAmount(),p.paidAmount(),p.gstType());
        return new TemplateData(v,imgs,lineItems(p.lines()),charges(p.charges()),safe(p.gstType()));
    }

    public TaxInvoiceDocument salesBuiltIn(OperationDtos.SaleDto sale, Map<String,String> config, Path logo, Path signature) {
        CompanyProfile company = new CompanyProfile(cfg(config,"company.name",""),cfg(config,"company.address",""),cfg(config,"company.gstin",""),cfg(config,"company.email",""),cfg(config,"company.alternateEmail",""),cfg(config,"company.phone",""),cfg(config,"payment.bankName",""),cfg(config,"payment.branch",""),cfg(config,"payment.accountNumber",""),cfg(config,"payment.ifsc",""),cfg(config,"payment.accountType",""),cfg(config,"payment.mode",""),cfg(config,"company.terms",""),logo==null?"":logo.toString(),signature==null?"":signature.toString(),cfg(config,"company.certificationText","AN ISO 9001 : 2015 COMPANY"));
        var masterParty=partyEntity(sale.customer());
        String partyName=sale.customer()==null?"":safe(sale.customer().name()), partyAddress=sale.customer()==null?"":safe(sale.customer().address()), partyGstin=sale.customer()==null?"":safe(sale.customer().gstin()), partyPhone=sale.customer()==null?"":safe(sale.customer().phone());
        String contact=first(sale.contactPerson(),masterParty==null?"":masterParty.getContactPerson()), mobile=first(sale.contactPersonMobile(),partyPhone);
        String billingAddress=first(sale.billingAddress(),partyAddress), deliveryAddress=first(sale.deliveryAddress(),billingAddress);
        String billingGstin=first(sale.billingGstin(),sale.gstin(),partyGstin), deliveryGstin=first(sale.deliveryGstin(),billingGstin);
        InvoiceParty billing=new InvoiceParty(partyName,billingAddress,billingGstin,contact,mobile); InvoiceParty delivery=new InvoiceParty(partyName,deliveryAddress,deliveryGstin,contact,mobile);
        List<TaxInvoiceItem> lineItems=lineItems(sale.lines()); List<TaxInvoiceCharge> taxCharges=taxCharges(sale.charges()); InvoiceTotals totals=InvoiceTaxCalculator.calculate(lineItems,taxCharges,sale.gstType());
        return new TaxInvoiceDocument(company,sale.invoiceNo(),parseDate(sale.invoiceDate()),first(sale.orderNo(),sale.referenceNo()),parseDate(sale.poDate()),sale.paymentTerms(),billing,delivery,sale.transporter(),sale.transporterGstin(),sale.vehicleNumber(),contact,mobile,lineItems,sale.gstType(),taxCharges,totals,"INR : "+AmountInWordsConverter.indianRupees(totals.grandTotal()));
    }

    public ProfessionalDocumentRenderer.Data professionalSale(OperationDtos.SaleDto sale, Map<String,String> config) { return professionalInvoice(sale,null,config); }
    public ProfessionalDocumentRenderer.Data professionalPurchase(OperationDtos.PurchaseDto purchase, Map<String,String> config) { return professionalInvoice(null,purchase,config); }

    private ProfessionalDocumentRenderer.Data professionalInvoice(OperationDtos.SaleDto sale, OperationDtos.PurchaseDto purchase, Map<String,String> config) {
        boolean sales=sale!=null; var d=new ProfessionalDocumentRenderer.Data(); d.title="TAX INVOICE";d.numberLabel="Invoice No.";d.dateLabel="Invoice Date";
        if(sales){
            d.number=sale.invoiceNo();d.date=rawDate(sale.invoiceDate());d.dueDate=rawDate(sale.dueDate());d.poDate=rawDate(sale.poDate());populateProfessionalParty(d,sale.customer());d.partyAddress=first(sale.billingAddress(),d.partyAddress);d.billingGstin=first(sale.billingGstin(),sale.gstin(),d.partyGstin);d.sameAsBilling=sale.sameAsBilling();d.shipTo=d.sameAsBilling?d.partyAddress:present(sale.deliveryAddress());d.deliveryGstin=d.sameAsBilling?d.billingGstin:present(sale.deliveryGstin());d.subtotal=sale.subtotal();d.gst=sale.gstAmount();d.total=sale.totalAmount();d.salesperson=sale.salesperson();d.paymentTerms=sale.paymentTerms();d.transporter=sale.transporter();d.transporterGstin=sale.transporterGstin();d.gstType=sale.gstType();d.vehicleNumber=sale.vehicleNumber();d.contactPerson=sale.contactPerson();d.contactPersonMobile=sale.contactPersonMobile();d.transportNote=sale.transportNote();d.chargeType=sale.chargeType();d.chargeAmount=sale.chargeAmount();d.reference=sale.referenceNo();d.purchaseOrder=sale.orderNo();d.partyGstin=d.billingGstin;professionalLines(d,sale.lines());
        }else{
            d.number=purchase.invoiceNo();d.date=rawDate(purchase.invoiceDate());d.dueDate=rawDate(purchase.dueDate());populateProfessionalParty(d,purchase.supplier());d.subtotal=purchase.subtotal();d.gst=purchase.gstAmount();d.total=purchase.totalAmount();d.paymentTerms=purchase.paymentTerms();d.transporter=purchase.transporter();d.reference=purchase.referenceNo();d.shipTo=cfg(config,"company.shipAddress",cfg(config,"company.address","Company delivery address not configured"));professionalLines(d,purchase.lines());
        }
        normalizeProfessionalTotals(d); return d;
    }

    private void professionalLines(ProfessionalDocumentRenderer.Data d,List<OperationDtos.LineDto> lines){Map<String,ItemEntity> master=masters();for(var l:lines==null?List.<OperationDtos.LineDto>of():lines){if(l==null)continue;var m=master.get(norm(l.itemCode()));var x=new ProfessionalDocumentRenderer.Line();x.code=present(l.itemCode());x.description=present(l.itemDescription());x.hsn=first(l.itemHsn(),m==null?"":m.getHsn());x.unit=first(l.itemUnit(),m==null?"Nos":first(m.getUnit(),"Nos"));x.quantity=l.quantity();x.rate=l.rate();x.gst=l.gstPercent();x.discount=Math.max(0,l.discountAmount());d.lines.add(x);}}
    private static void normalizeProfessionalTotals(ProfessionalDocumentRenderer.Data d){if(d.lines.isEmpty())return;double taxable=0,gst=0;for(var l:d.lines){double base=l.quantity*l.rate-l.discount;taxable+=base;gst+=base*l.gst/100;}d.subtotal=taxable;d.gst=gst;d.total=taxable+gst;}

    private List<TaxInvoiceItem> lineItems(List<OperationDtos.LineDto> lines){Map<String,ItemEntity> master=masters();List<TaxInvoiceItem> out=new ArrayList<>();int serial=1;for(var l:lines==null?List.<OperationDtos.LineDto>of():lines){if(l==null)continue;var m=master.get(norm(l.itemCode()));String hsn=first(l.itemHsn(),m==null?"":m.getHsn()),unit=first(l.itemUnit(),m==null?"NOS":first(m.getUnit(),"NOS")),remarks=first(l.itemRemarks(),m==null?"":m.getRemarks());out.add(new TaxInvoiceItem(serial++,hsn,cleanDescription(l.itemDescription(),l.itemCode()),remarks,l.quantity(),unit,l.rate(),l.discountPercent(),l.gstPercent(),safe(l.itemCode()),m==null?"":safe(m.getCategory()),m==null?"":safe(m.getBrand()),m==null?"":safe(m.getMaterial()),m==null?"":safe(m.getSize()),m==null?"":safe(m.getLocation()),m==null?0:n(m.getPurchasePrice()),m==null?0:n(m.getSellingPrice()),m==null?0:Math.max(0,n(m.getOpeningStock())-n(m.getReservedStock())),m==null?0:n(m.getOpeningStock()),m==null?0:n(m.getMinimumStock()),m==null?0:n(m.getReservedStock()),m==null?0:n(m.getGst()),m==null?0:n(m.getDiscountPercent())));}return List.copyOf(out);}
    private List<TemplateCharge> charges(List<OperationDtos.ChargeDto> cs){List<TemplateCharge> out=new ArrayList<>();for(var c:cs==null?List.<OperationDtos.ChargeDto>of():cs){if(c==null)continue;double tax=c.taxable()?c.amount()*c.gstPercent()/100d:0;out.add(new TemplateCharge(c.chargeType(),c.amount(),c.taxable(),c.gstPercent(),tax,c.amount()+tax));}return List.copyOf(out);}
    private List<TaxInvoiceCharge> taxCharges(List<OperationDtos.ChargeDto> cs){List<TaxInvoiceCharge> out=new ArrayList<>();for(var c:cs==null?List.<OperationDtos.ChargeDto>of():cs)if(c!=null)out.add(new TaxInvoiceCharge(c.chargeType(),c.amount(),c.taxable(),c.gstPercent()));return List.copyOf(out);}
    private Map<String,ItemEntity> masters(){Map<String,ItemEntity> out=new HashMap<>();for(ItemEntity i:items.findAll())if(i!=null)out.put(norm(i.getItemCode()),i);return out;}
    private PartyEntity partyEntity(OperationDtos.PartyDto p){return p==null||p.id()==null?null:parties.findById(p.id()).orElse(null);}
    private void enrichPartyContact(Map<String,String> v,OperationDtos.PartyDto p,String prefix){var master=partyEntity(p);put(v,prefix+".contactPerson",master==null?"":master.getContactPerson());}
    private void populateProfessionalParty(ProfessionalDocumentRenderer.Data d,OperationDtos.PartyDto p){if(p==null)return;d.partyCode=present(p.partyCode());d.partyName=present(p.name());d.partyAddress=present(p.address());d.partyGstin=present(p.gstin());d.partyPhone=present(p.phone());d.partyEmail=present(p.email());}
    private Map<String,String> base(Map<String,String> c){Map<String,String> v=new LinkedHashMap<>();for(String k:List.of("company.name","company.address","company.gstin","company.phone","company.email","company.alternateEmail","company.terms","payment.bankName","payment.branch","payment.accountNumber","payment.ifsc","payment.accountType","payment.mode"))put(v,k,cfg(c,k,""));put(v,"company.certification",cfg(c,"company.certificationText","AN ISO 9001 : 2015 COMPANY"));return v;}
    private void party(Map<String,String> v,OperationDtos.PartyDto p,String prefix){if(p==null)return;put(v,prefix+".code",p.partyCode());put(v,prefix+".name",p.name());put(v,prefix+".address",p.address());put(v,prefix+".gstin",p.gstin());put(v,prefix+".phone",p.phone());put(v,prefix+".email",p.email());}
    private void totals(Map<String,String> v,double subtotal,double discount,double gst,double total,double paid,String gstType){put(v,"totals.subtotal",money(subtotal));put(v,"totals.discountAmount",money(discount));put(v,"totals.gstAmount",money(gst));if(norm(gstType).contains("IGST")){put(v,"totals.cgstAmount",money(0));put(v,"totals.sgstAmount",money(0));put(v,"totals.igstAmount",money(gst));}else{double half=gst/2d;put(v,"totals.cgstAmount",money(half));put(v,"totals.sgstAmount",money(half));put(v,"totals.igstAmount",money(0));}put(v,"totals.grandTotal",money(total));put(v,"totals.paidAmount",money(paid));put(v,"totals.balanceAmount",money(Math.max(0,total-paid)));put(v,"totals.amountInWords","INR : "+AmountInWordsConverter.indianRupees(total));}
    private static String date(String value,Map<String,String> c){LocalDate d=parseDate(value);if(d==null)return safe(value);String pattern=cfg(c,"date.format",cfg(c,"company.dateFormat","dd/MM/yyyy"));try{return d.format(DateTimeFormatter.ofPattern(pattern));}catch(Exception ignored){return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));}}
    private static LocalDate parseDate(String value){if(value==null||value.isBlank())return null;String x=value.trim();try{return LocalDate.parse(x.substring(0,Math.min(10,x.length())));}catch(Exception ignored){for(String p:List.of("dd/MM/yyyy","d/M/yyyy","dd-MM-yyyy"))try{return LocalDate.parse(x,DateTimeFormatter.ofPattern(p));}catch(Exception ignored2){}}return null;}
    private static String rawDate(String value){LocalDate d=parseDate(value);return d==null?safe(value):d.toString();}
    private static String cleanDescription(String value,String code){String text=safe(value),c=safe(code);return !c.isBlank()&&text.startsWith(c+" - ")?text.substring(c.length()+3).trim():text;}
    private static String cfg(Map<String,String> c,String key,String fallback){String v=c==null?null:c.get(key);return v==null||v.isBlank()?safe(fallback):v;}
    private static void put(Map<String,String> v,String k,String x){v.put(k,safe(x));}
    private static String safe(String x){return x==null?"":x.trim();} private static String norm(String x){return safe(x).toUpperCase(Locale.ROOT);} private static double n(Double x){return x==null?0:x;}
    private static String first(String... values){for(String v:values)if(v!=null&&!v.isBlank())return v.trim();return "";} private static String present(String v){String x=safe(v);return x.isBlank()?"-":x;}
    private static String money(double x){synchronized(MONEY){return MONEY.format(x);}} private static String number(double x){return Math.rint(x)==x?String.format(Locale.ROOT,"%.0f",x):String.format(Locale.ROOT,"%.2f",x);}
}
