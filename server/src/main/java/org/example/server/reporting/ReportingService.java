package org.example.server.reporting;

import org.example.server.insights.BusinessKpiPolicy;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.util.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

import static org.example.server.reporting.ReportingDtos.*;

/**
 * 9.0.46 unified reporting calculation source.
 *
 * <p>The JavaFX viewer and every export consume the same ReportResult. The
 * service never mutates accounting rows. Approved Returns reduce net business
 * values while original invoice payment state and Return refund settlement are
 * intentionally separate.</p>
 */
@Service
public class ReportingService {
    private static final double EPS = 0.0001d;
    private final JpaNativeRepository jdbc;

    public ReportingService(JpaNativeRepository jdbc) {
        this.jdbc = jdbc;
    }

    public List<ReportDefinition> definitions() {
        return List.of(
                def("SALES_REGISTER","Sales","Sales Register","Invoice-level sales with GST, payments, Returns and refund lifecycle.",
                        List.of("None","Customer","Salesperson","Payment Status","Return Status","Month"), commonSalesFilters()),
                def("SALES_BY_CUSTOMER","Sales","Sales by Customer","Customer performance with net sales and outstanding.",
                        List.of("Customer","Month","Payment Status"), commonSalesFilters()),
                def("SALES_BY_ITEM","Sales","Sales by Item","Item quantity, taxable value, GST and net sales.",
                        List.of("Item","Customer","Month","GST Rate"), commonSalesFilters()),
                def("PURCHASE_REGISTER","Purchase","Purchase Register","Supplier invoices with GST, payments and Returns.",
                        List.of("None","Supplier","Payment Status","Return Status","Month"), commonPurchaseFilters()),
                def("RETURNS_ANALYSIS","Returns","Returns Analysis","Approved Return quantity, value, source invoice and refund settlement.",
                        List.of("Return Type","Party","Item","Refund Status","Month"), List.of("Period","Party","Item","Return Status","Search")),
                def("GST_TAX","GST / Tax","GST / Tax Report","Output and input GST with taxable value and transaction totals.",
                        List.of("Direction","GST Rate","Party","Month"), List.of("Period","Party","Item","GST Rate","Search")),
                def("RECEIVABLE_AGEING","Receivable","Receivable Ageing","Outstanding customer invoices grouped by due-age bucket.",
                        List.of("Age Bucket","Customer","Payment Status"), List.of("Period","Party","Salesperson","Payment Status","Search","Amount")),
                def("PAYABLE_AGEING","Payable","Payable Ageing","Outstanding supplier invoices grouped by due-age bucket.",
                        List.of("Age Bucket","Supplier","Payment Status"), List.of("Period","Party","Payment Status","Search","Amount")),
                def("STOCK_SUMMARY","Inventory","Inventory / Stock Summary","Current quantity, average cost, stock value and reorder status.",
                        List.of("Category","Stock Status","Location"), List.of("Item","Warehouse","Search","Amount")),
                def("ITEM_LEDGER","Inventory","Item Ledger / Stock Movement","Cost-ledger movement history with quantity and value changes.",
                        List.of("Item","Movement Type","Month"), List.of("Period","Item","Search")),
                def("BANK_RECONCILIATION","Banking","Banking / Reconciliation","Imported statement activity with reconciliation status and allocations.",
                        List.of("Status","Bank","Month"), List.of("Period","Bank Status","Search","Amount")),
                def("PROFITABILITY","Profitability","Profitability Analysis","Invoice profitability from net taxable sales and historical unit-cost snapshots.",
                        List.of("Customer","Salesperson","Margin Band","Month"), commonSalesFilters())
        );
    }

    @Transactional(readOnly = true)
    public ReportFilters filters() {
        return new ReportFilters(
                strings("SELECT name FROM party_master WHERE COALESCE(is_active::text,'1') IN ('1','true','t') ORDER BY name"),
                strings("SELECT name FROM party_master WHERE UPPER(COALESCE(party_type,''))='CUSTOMER' AND COALESCE(is_active::text,'1') IN ('1','true','t') ORDER BY name"),
                strings("SELECT name FROM party_master WHERE UPPER(COALESCE(party_type,''))='SUPPLIER' AND COALESCE(is_active::text,'1') IN ('1','true','t') ORDER BY name"),
                strings("SELECT COALESCE(NULLIF(description,''),item_code) FROM item_master WHERE COALESCE(is_active::text,'1') IN ('1','true','t') ORDER BY 1"),
                strings("SELECT DISTINCT salesperson FROM sales_header WHERE "+BusinessKpiPolicy.salesActive("sales_header")+" AND COALESCE(NULLIF(TRIM(salesperson),''),'')<>'' ORDER BY salesperson"),
                List.of("APPROVED","COMPLETED","PENDING APPROVAL","DRAFT","CANCELLED","REJECTED"),
                List.of("PENDING","PARTIAL","PAID","OVERDUE"),
                List.of("N/A","PARTIALLY RETURNED","FULLY RETURNED","PENDING APPROVAL"),
                strings("SELECT DISTINCT TRIM(TRAILING '.0' FROM CAST(gst AS text)) FROM item_master WHERE gst IS NOT NULL ORDER BY 1"),
                strings("SELECT DISTINCT warehouse FROM purchase_header WHERE COALESCE(NULLIF(TRIM(warehouse),''),'')<>'' ORDER BY warehouse"),
                strings("SELECT DISTINCT UPPER(COALESCE(NULLIF(TRIM(status),''),'UNMATCHED')) FROM bank_statement_transaction ORDER BY 1")
        );
    }

    @Transactional(readOnly = true)
    public ReportResult run(ReportRequest request, String generatedBy) {
        Request r = normalize(request);
        ReportDefinition definition = definitions().stream().filter(d -> d.id().equals(r.reportId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported report: " + r.reportId));

        Raw raw = switch (r.reportId) {
            case "SALES_REGISTER", "SALES_BY_CUSTOMER" -> salesRegister(r);
            case "SALES_BY_ITEM" -> salesByItem(r);
            case "PURCHASE_REGISTER" -> purchaseRegister(r);
            case "RETURNS_ANALYSIS" -> returnsAnalysis(r);
            case "GST_TAX" -> gstReport(r);
            case "RECEIVABLE_AGEING" -> receivableAgeing(r);
            case "PAYABLE_AGEING" -> payableAgeing(r);
            case "STOCK_SUMMARY" -> stockSummary(r);
            case "ITEM_LEDGER" -> itemLedger(r);
            case "BANK_RECONCILIATION" -> banking(r);
            case "PROFITABILITY" -> profitability(r);
            default -> throw new IllegalArgumentException("Unsupported report: " + r.reportId);
        };

        List<ReportRow> filtered = postFilter(raw.columns, raw.rows, r);
        sort(raw.columns, filtered, r.sortKey, r.sortDirection, r.groupBy);
        List<ReportMetric> metrics = metrics(r.reportId, raw.columns, filtered);
        Map<String,String> totals = totals(raw.columns, filtered);
        long total = filtered.size();
        int totalPages = (int)Math.max(1, Math.ceil(total / (double)r.size));
        int page = Math.min(r.page, totalPages - 1);
        int start = Math.min(filtered.size(), page * r.size);
        int end = Math.min(filtered.size(), start + r.size);
        List<ReportRow> pageRows = new ArrayList<>(filtered.subList(start, end));
        pageRows = withGroups(raw.columns, pageRows, r.groupBy);

        return new ReportResult(
                r.reportId, definition.title(), definition.description(), r.from.toString(), r.to.toString(),
                metrics, raw.columns, pageRows, total, page, r.size, totalPages,
                definition.groupByOptions(), applied(r), totals,
                java.time.ZonedDateTime.now(BusinessClock.zone()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")),
                generatedBy == null || generatedBy.isBlank() ? "System" : generatedBy
        );
    }

    private Raw salesRegister(Request r) {
        String returnValue = approvedReturnAmount("h", "SALES RETURN");
        String returnedQty = approvedReturnedQty("h", "SALES RETURN");
        String originalQty = "COALESCE((SELECT SUM(slq.quantity) FROM sales_line slq WHERE slq.sales_id=h.id),0)";
        String refundValue = settledReturnAmount("h", "SALES RETURN");
        String returnStatus = returnStatus(returnedQty, originalQty, "h", "SALES RETURN");
        String refundStatus = refundStatus(returnValue, refundValue);
        String paymentStatus = dueAwarePaymentStatus("h", "SALE");
        String charges = "COALESCE((SELECT SUM(sc.amount) FROM sales_charge sc WHERE sc.sales_id=h.id),COALESCE(h.charge_amount,0),0)";
        String paid = BusinessKpiPolicy.effectivePaidCorrelated("h", "SALE");
        String outstanding = BusinessKpiPolicy.effectiveOutstanding("h", "SALE");

        Sql w = new Sql(BusinessKpiPolicy.salesActive("h") + " AND " + safeDate("h.invoice_date") + " BETWEEN ? AND ?", r.from, r.to);
        if (!r.party.isBlank()) w.add("COALESCE(NULLIF(h.customer_name_snapshot,''),pm.name,'')=?", r.party);
        if (!r.salesperson.isBlank()) w.add("COALESCE(h.salesperson,'')=?", r.salesperson);
        if (!r.item.isBlank()) w.add("EXISTS (SELECT 1 FROM sales_line sx LEFT JOIN item_master ix ON ix.item_code=sx.item_code WHERE sx.sales_id=h.id AND (COALESCE(NULLIF(ix.description,''),sx.item_code)=? OR sx.item_code=?))", r.item, r.item);
        if (!r.gstRate.isBlank()) w.add("EXISTS (SELECT 1 FROM sales_line sg WHERE sg.sales_id=h.id AND CAST(sg.gst_percent AS numeric)=CAST(? AS numeric))", r.gstRate);
        if (!r.documentStatus.isBlank()) w.add("UPPER(COALESCE(h.document_status,''))=?", r.documentStatus);

        List<ReportColumn> c = cols(
                col("invoice","Invoice","TEXT",true,false,145), col("date","Date","DATE",true,false,105),
                col("customer","Customer","TEXT",true,false,190), col("gstin","GSTIN","TEXT",false,false,150),
                col("salesperson","Salesperson","TEXT",false,false,130), col("taxable","Taxable","MONEY",true,true,115),
                col("charges","Charges","MONEY",true,true,105), col("gst","GST","MONEY",true,true,105),
                col("gross","Gross Total","MONEY",true,true,120), col("returned","Returns","MONEY",true,true,110),
                col("net","Net Sales","MONEY",true,true,115), col("paid","Paid","MONEY",true,true,110),
                col("outstanding","Outstanding","MONEY",true,true,120), col("payment_status","Payment","STATUS",true,false,115),
                col("document_status","Document","STATUS",false,false,120), col("return_status","Return Status","STATUS",true,false,155),
                col("refund_status","Refund Status","STATUS",false,false,125)
        );
        List<ReportRow> rows = jdbc.query("SELECT h.id,h.invoice_no,"+safeDate("h.invoice_date")+",COALESCE(NULLIF(h.customer_name_snapshot,''),pm.name,''),COALESCE(NULLIF(h.customer_gstin_snapshot,''),h.gstin,pm.gstin,''),COALESCE(h.salesperson,''),COALESCE(h.subtotal,0),"+charges+",COALESCE(h.gst_amount,0),COALESCE(h.total_amount,0),"+returnValue+",GREATEST(COALESCE(h.total_amount,0)-("+returnValue+"),0),"+paid+","+outstanding+","+paymentStatus+",UPPER(COALESCE(NULLIF(h.document_status,''),'APPROVED')),"+returnStatus+","+refundStatus+" FROM sales_header h LEFT JOIN party_master pm ON pm.id=h.customer_id WHERE "+w.sql,
                (x,i)->row(x.getLong(1), List.of(s(x,2),s(x,3),s(x,4),s(x,5),s(x,6),n(x,7),n(x,8),n(x,9),n(x,10),n(x,11),n(x,12),n(x,13),n(x,14),s(x,15),s(x,16),s(x,17),s(x,18)), "/fxml/pages/SalesList.fxml", x.getLong(1), s(x,2)), w.args());
        return new Raw(c,rows,"gross");
    }

    private Raw salesByItem(Request r) {
        Sql w = new Sql(BusinessKpiPolicy.salesActive("h") + " AND " + safeDate("h.invoice_date") + " BETWEEN ? AND ?", r.from, r.to);
        if (!r.party.isBlank()) w.add("COALESCE(NULLIF(h.customer_name_snapshot,''),pm.name,'')=?",r.party);
        if (!r.item.isBlank()) w.add("(COALESCE(NULLIF(im.description,''),sl.item_code)=? OR sl.item_code=?)",r.item,r.item);
        if (!r.salesperson.isBlank()) w.add("COALESCE(h.salesperson,'')=?",r.salesperson);
        if (!r.gstRate.isBlank()) w.add("CAST(sl.gst_percent AS numeric)=CAST(? AS numeric)",r.gstRate);
        String returnedQty="COALESCE((SELECT SUM(rr.quantity) FROM return_register rr WHERE rr.source_line_id=sl.id AND UPPER(COALESCE(rr.return_type,'')) IN ('SALE RETURN','SALES RETURN') AND "+BusinessKpiPolicy.returnsActive("rr")+"),0)";
        String unitTaxable="CASE WHEN COALESCE(sl.quantity,0)=0 THEN 0 ELSE ((COALESCE(sl.quantity,0)*COALESCE(sl.rate,0))-COALESCE(sl.discount_amount,0))/sl.quantity END";
        String grossTaxable="((COALESCE(sl.quantity,0)*COALESCE(sl.rate,0))-COALESCE(sl.discount_amount,0))";
        String returnedTaxable="("+returnedQty+")*("+unitTaxable+")";
        List<ReportColumn> c=cols(col("item","Item","TEXT",true,false,190),col("code","Code","TEXT",true,false,110),col("customer","Customer","TEXT",true,false,180),col("date","Date","DATE",true,false,105),col("invoice","Invoice","TEXT",true,false,135),col("quantity","Qty","NUMBER",true,true,85),col("returned_qty","Returned Qty","NUMBER",true,true,105),col("net_qty","Net Qty","NUMBER",true,true,90),col("gst_rate","GST %","NUMBER",true,true,80),col("taxable","Taxable","MONEY",true,true,115),col("returns","Return Taxable","MONEY",false,true,120),col("net","Net Taxable","MONEY",true,true,120),col("gst","GST","MONEY",true,true,100),col("line_total","Line Total","MONEY",true,true,115));
        List<ReportRow> rows=jdbc.query("SELECT sl.id,COALESCE(NULLIF(im.description,''),sl.item_code),sl.item_code,COALESCE(NULLIF(h.customer_name_snapshot,''),pm.name,''),"+safeDate("h.invoice_date")+",h.invoice_no,sl.quantity,"+returnedQty+",GREATEST(sl.quantity-("+returnedQty+"),0),sl.gst_percent,"+grossTaxable+","+returnedTaxable+",GREATEST(("+grossTaxable+")-("+returnedTaxable+"),0),GREATEST(COALESCE(sl.line_total,0)-("+grossTaxable+"),0),sl.line_total FROM sales_line sl JOIN sales_header h ON h.id=sl.sales_id LEFT JOIN party_master pm ON pm.id=h.customer_id LEFT JOIN item_master im ON im.item_code=sl.item_code WHERE "+w.sql,
                (x,i)->row(x.getLong(1),List.of(s(x,2),s(x,3),s(x,4),s(x,5),s(x,6),n(x,7),n(x,8),n(x,9),n(x,10),n(x,11),n(x,12),n(x,13),n(x,14),n(x,15)),"/fxml/pages/SalesList.fxml",null,s(x,6)),w.args());
        return new Raw(c,rows,"net");
    }

    private Raw purchaseRegister(Request r) {
        String returnValue=approvedReturnAmount("h","PURCHASE RETURN"), returnedQty=approvedReturnedQty("h","PURCHASE RETURN"), originalQty="COALESCE((SELECT SUM(plq.quantity) FROM purchase_line plq WHERE plq.purchase_id=h.id),0)", refundValue=settledReturnAmount("h","PURCHASE RETURN");
        String returnStatus=returnStatus(returnedQty,originalQty,"h","PURCHASE RETURN"), refundStatus=refundStatus(returnValue,refundValue), paymentStatus=dueAwarePaymentStatus("h","PURCHASE");
        String charges="COALESCE((SELECT SUM(pc.amount) FROM purchase_charge pc WHERE pc.purchase_id=h.id),0)", paid=BusinessKpiPolicy.effectivePaidCorrelated("h","PURCHASE"), outstanding=BusinessKpiPolicy.effectiveOutstanding("h","PURCHASE");
        Sql w=new Sql(BusinessKpiPolicy.purchasesActive("h")+" AND "+safeDate("h.invoice_date")+" BETWEEN ? AND ?",r.from,r.to);
        if(!r.party.isBlank())w.add("COALESCE(NULLIF(h.supplier_name_snapshot,''),pm.name,'')=?",r.party);
        if(!r.item.isBlank())w.add("EXISTS (SELECT 1 FROM purchase_line px LEFT JOIN item_master ix ON ix.item_code=px.item_code WHERE px.purchase_id=h.id AND (COALESCE(NULLIF(ix.description,''),px.item_code)=? OR px.item_code=?))",r.item,r.item);
        if(!r.gstRate.isBlank())w.add("EXISTS (SELECT 1 FROM purchase_line pg WHERE pg.purchase_id=h.id AND CAST(pg.gst_percent AS numeric)=CAST(? AS numeric))",r.gstRate);
        if(!r.warehouse.isBlank())w.add("COALESCE(h.warehouse,'')=?",r.warehouse);
        if(!r.documentStatus.isBlank())w.add("UPPER(COALESCE(h.document_status,''))=?",r.documentStatus);
        List<ReportColumn> c=cols(col("invoice","Invoice","TEXT",true,false,145),col("date","Date","DATE",true,false,105),col("supplier","Supplier","TEXT",true,false,190),col("gstin","GSTIN","TEXT",false,false,150),col("warehouse","Warehouse","TEXT",false,false,120),col("taxable","Taxable","MONEY",true,true,115),col("charges","Charges","MONEY",true,true,105),col("gst","GST","MONEY",true,true,105),col("gross","Gross Total","MONEY",true,true,120),col("returned","Returns","MONEY",true,true,110),col("net","Net Purchase","MONEY",true,true,120),col("paid","Paid","MONEY",true,true,110),col("outstanding","Payable","MONEY",true,true,115),col("payment_status","Payment","STATUS",true,false,115),col("document_status","Document","STATUS",false,false,120),col("return_status","Return Status","STATUS",true,false,155),col("refund_status","Refund Status","STATUS",false,false,125));
        List<ReportRow> rows=jdbc.query("SELECT h.id,h.invoice_no,"+safeDate("h.invoice_date")+",COALESCE(NULLIF(h.supplier_name_snapshot,''),pm.name,''),COALESCE(NULLIF(h.supplier_gstin_snapshot,''),pm.gstin,''),COALESCE(h.warehouse,''),COALESCE(h.subtotal,0),"+charges+",COALESCE(h.gst_amount,0),COALESCE(h.total_amount,0),"+returnValue+",GREATEST(COALESCE(h.total_amount,0)-("+returnValue+"),0),"+paid+","+outstanding+","+paymentStatus+",UPPER(COALESCE(NULLIF(h.document_status,''),'APPROVED')),"+returnStatus+","+refundStatus+" FROM purchase_header h LEFT JOIN party_master pm ON pm.id=h.supplier_id WHERE "+w.sql,
                (x,i)->row(x.getLong(1),List.of(s(x,2),s(x,3),s(x,4),s(x,5),s(x,6),n(x,7),n(x,8),n(x,9),n(x,10),n(x,11),n(x,12),n(x,13),n(x,14),s(x,15),s(x,16),s(x,17),s(x,18)),"/fxml/pages/PurchaseList.fxml",x.getLong(1),s(x,2)),w.args());
        return new Raw(c,rows,"gross");
    }

    private Raw returnsAnalysis(Request r) {
        Sql w=new Sql(safeDate("r.return_date")+" BETWEEN ? AND ?",r.from,r.to);
        if(!r.party.isBlank())w.add("COALESCE(pm.name,'')=?",r.party);
        if(!r.item.isBlank())w.add("(COALESCE(NULLIF(im.description,''),r.item_code)=? OR r.item_code=?)",r.item,r.item);
        if(!r.documentStatus.isBlank())w.add("UPPER(COALESCE(r.status,''))=?",r.documentStatus);
        String originalQty="CASE WHEN UPPER(COALESCE(r.return_type,'')) IN ('SALE RETURN','SALES RETURN') THEN COALESCE(sl.quantity,0) ELSE COALESCE(pl.quantity,0) END";
        String returnTypeNormalized="CASE WHEN UPPER(COALESCE(r.return_type,'')) IN ('SALE RETURN','SALES RETURN') THEN 'SALES RETURN' ELSE 'PURCHASE RETURN' END";
        String refunded="COALESCE((SELECT SUM(rr.amount+COALESCE(rr.rounding_adjustment,0)) FROM return_refund rr WHERE rr.return_no=r.return_no),0)";
        String refundState="CASE WHEN "+refunded+"+0.0001>=COALESCE((SELECT SUM(r2.amount) FROM return_register r2 WHERE r2.return_no=r.return_no AND "+BusinessKpiPolicy.returnsActive("r2")+"),0) AND COALESCE((SELECT SUM(r2.amount) FROM return_register r2 WHERE r2.return_no=r.return_no AND "+BusinessKpiPolicy.returnsActive("r2")+"),0)>0 THEN 'REFUNDED' WHEN "+refunded+">0.0001 THEN 'PARTIAL' WHEN UPPER(COALESCE(r.status,''))='APPROVED' THEN 'PENDING' ELSE 'N/A' END";
        List<ReportColumn> c=cols(col("return_no","Return No.","TEXT",true,false,140),col("type","Return Type","TEXT",true,false,125),col("date","Date","DATE",true,false,105),col("invoice","Source Invoice","TEXT",true,false,140),col("party","Party","TEXT",true,false,180),col("item","Item","TEXT",true,false,180),col("quantity","Returned Qty","NUMBER",true,true,105),col("original_qty","Original Qty","NUMBER",true,true,100),col("amount","Return Value","MONEY",true,true,115),col("refunded","Refunded","MONEY",true,true,110),col("refund_status","Refund Status","STATUS",true,false,120),col("return_status","Approval","STATUS",true,false,115),col("reason","Reason","TEXT",false,false,200),col("settlement_due","Settlement Due","DATE",false,false,115));
        List<ReportRow> rows=jdbc.query("SELECT r.id,r.return_no,"+returnTypeNormalized+","+safeDate("r.return_date")+",COALESCE(r.invoice_no,''),COALESCE(pm.name,''),COALESCE(NULLIF(im.description,''),r.item_code),r.quantity,"+originalQty+",r.amount,"+refunded+","+refundState+",UPPER(COALESCE(NULLIF(r.status,''),'PENDING APPROVAL')),COALESCE(r.reason,''),COALESCE(CAST(r.settlement_due_date AS text),'') FROM return_register r LEFT JOIN party_master pm ON pm.id=r.party_id LEFT JOIN item_master im ON im.item_code=r.item_code LEFT JOIN sales_line sl ON sl.id=r.source_line_id LEFT JOIN purchase_line pl ON pl.id=r.source_line_id WHERE "+w.sql,
                (x,i)->row(x.getLong(1),List.of(s(x,2),s(x,3),s(x,4),s(x,5),s(x,6),s(x,7),n(x,8),n(x,9),n(x,10),n(x,11),s(x,12),s(x,13),s(x,14),s(x,15)),"SALES RETURN".equals(s(x,3))?"/fxml/pages/SalesReturns.fxml":"/fxml/pages/PurchaseReturns.fxml",x.getLong(1),s(x,2)),w.args());
        return new Raw(c,rows,"amount");
    }

    private Raw gstReport(Request r) {
        Sql sw=new Sql(BusinessKpiPolicy.salesActive("h")+" AND "+safeDate("h.invoice_date")+" BETWEEN ? AND ?",r.from,r.to);
        if(!r.party.isBlank())sw.add("COALESCE(NULLIF(h.customer_name_snapshot,''),pm.name,'')=?",r.party);
        if(!r.item.isBlank())sw.add("(COALESCE(NULLIF(im.description,''),sl.item_code)=? OR sl.item_code=?)",r.item,r.item);
        if(!r.gstRate.isBlank())sw.add("CAST(sl.gst_percent AS numeric)=CAST(? AS numeric)",r.gstRate);
        Sql pw=new Sql(BusinessKpiPolicy.purchasesActive("h")+" AND "+safeDate("h.invoice_date")+" BETWEEN ? AND ?",r.from,r.to);
        if(!r.party.isBlank())pw.add("COALESCE(NULLIF(h.supplier_name_snapshot,''),pm.name,'')=?",r.party);
        if(!r.item.isBlank())pw.add("(COALESCE(NULLIF(im.description,''),pl.item_code)=? OR pl.item_code=?)",r.item,r.item);
        if(!r.gstRate.isBlank())pw.add("CAST(pl.gst_percent AS numeric)=CAST(? AS numeric)",r.gstRate);
        List<ReportColumn> c=cols(col("direction","Direction","TEXT",true,false,90),col("invoice","Invoice","TEXT",true,false,135),col("date","Date","DATE",true,false,105),col("party","Party","TEXT",true,false,180),col("gstin","GSTIN","TEXT",false,false,145),col("item","Item","TEXT",true,false,170),col("hsn","HSN/SAC","TEXT",false,false,95),col("gst_rate","GST %","NUMBER",true,true,80),col("taxable","Taxable","MONEY",true,true,115),col("cgst","CGST","MONEY",true,true,100),col("sgst","SGST","MONEY",true,true,100),col("igst","IGST","MONEY",true,true,100),col("total_gst","Total GST","MONEY",true,true,105),col("invoice_value","Line Value","MONEY",true,true,115));
        String salesSql="SELECT sl.id,'OUTPUT',h.invoice_no,"+safeDate("h.invoice_date")+",COALESCE(NULLIF(h.customer_name_snapshot,''),pm.name,''),COALESCE(NULLIF(h.customer_gstin_snapshot,''),h.gstin,pm.gstin,''),COALESCE(NULLIF(im.description,''),sl.item_code),COALESCE(NULLIF(sl.hsn_snapshot,''),im.hsn,''),sl.gst_percent,((sl.quantity*sl.rate)-COALESCE(sl.discount_amount,0)) taxable,CASE WHEN UPPER(COALESCE(h.gst_type,'')) LIKE '%INTER%' THEN 0 ELSE GREATEST(sl.line_total-((sl.quantity*sl.rate)-COALESCE(sl.discount_amount,0)),0)/2 END cgst,CASE WHEN UPPER(COALESCE(h.gst_type,'')) LIKE '%INTER%' THEN 0 ELSE GREATEST(sl.line_total-((sl.quantity*sl.rate)-COALESCE(sl.discount_amount,0)),0)/2 END sgst,CASE WHEN UPPER(COALESCE(h.gst_type,'')) LIKE '%INTER%' THEN GREATEST(sl.line_total-((sl.quantity*sl.rate)-COALESCE(sl.discount_amount,0)),0) ELSE 0 END igst,GREATEST(sl.line_total-((sl.quantity*sl.rate)-COALESCE(sl.discount_amount,0)),0),sl.line_total FROM sales_line sl JOIN sales_header h ON h.id=sl.sales_id LEFT JOIN party_master pm ON pm.id=h.customer_id LEFT JOIN item_master im ON im.item_code=sl.item_code WHERE "+sw.sql;
        String purchaseSql="SELECT pl.id,'INPUT',h.invoice_no,"+safeDate("h.invoice_date")+",COALESCE(NULLIF(h.supplier_name_snapshot,''),pm.name,''),COALESCE(NULLIF(h.supplier_gstin_snapshot,''),pm.gstin,''),COALESCE(NULLIF(im.description,''),pl.item_code),COALESCE(NULLIF(pl.hsn_snapshot,''),im.hsn,''),pl.gst_percent,((pl.quantity*pl.rate)-COALESCE(pl.discount_amount,0)) taxable,CASE WHEN UPPER(COALESCE(h.gst_type,'')) LIKE '%INTER%' THEN 0 ELSE GREATEST(pl.line_total-((pl.quantity*pl.rate)-COALESCE(pl.discount_amount,0)),0)/2 END cgst,CASE WHEN UPPER(COALESCE(h.gst_type,'')) LIKE '%INTER%' THEN 0 ELSE GREATEST(pl.line_total-((pl.quantity*pl.rate)-COALESCE(pl.discount_amount,0)),0)/2 END sgst,CASE WHEN UPPER(COALESCE(h.gst_type,'')) LIKE '%INTER%' THEN GREATEST(pl.line_total-((pl.quantity*pl.rate)-COALESCE(pl.discount_amount,0)),0) ELSE 0 END igst,GREATEST(pl.line_total-((pl.quantity*pl.rate)-COALESCE(pl.discount_amount,0)),0),pl.line_total FROM purchase_line pl JOIN purchase_header h ON h.id=pl.purchase_id LEFT JOIN party_master pm ON pm.id=h.supplier_id LEFT JOIN item_master im ON im.item_code=pl.item_code WHERE "+pw.sql;
        List<ReportRow> rows=new ArrayList<>();
        rows.addAll(jdbc.query(salesSql,(x,i)->row("S-"+x.getLong(1),List.of(s(x,2),s(x,3),s(x,4),s(x,5),s(x,6),s(x,7),s(x,8),n(x,9),n(x,10),n(x,11),n(x,12),n(x,13),n(x,14),n(x,15)),"/fxml/pages/SalesList.fxml",null,s(x,3)),sw.args()));
        rows.addAll(jdbc.query(purchaseSql,(x,i)->row("P-"+x.getLong(1),List.of(s(x,2),s(x,3),s(x,4),s(x,5),s(x,6),s(x,7),s(x,8),n(x,9),n(x,10),n(x,11),n(x,12),n(x,13),n(x,14),n(x,15)),"/fxml/pages/PurchaseList.fxml",null,s(x,3)),pw.args()));
        return new Raw(c,rows,"invoice_value");
    }

    private Raw receivableAgeing(Request r) { return ageing(r,true); }
    private Raw payableAgeing(Request r) { return ageing(r,false); }
    private Raw ageing(Request r, boolean sales) {
        String h=sales?"sales_header":"purchase_header", alias="h", type=sales?"SALE":"PURCHASE", fk=sales?"customer_id":"supplier_id", snapshot=sales?"customer_name_snapshot":"supplier_name_snapshot";
        Sql w=new Sql((sales?BusinessKpiPolicy.salesActive(alias):BusinessKpiPolicy.purchasesActive(alias))+" AND "+safeDate(alias+".invoice_date")+" BETWEEN ? AND ?",r.from,r.to);
        if(!r.party.isBlank())w.add("COALESCE(NULLIF(h."+snapshot+",''),pm.name,'')=?",r.party);
        if(sales&&!r.salesperson.isBlank())w.add("COALESCE(h.salesperson,'')=?",r.salesperson);
        String outstanding=BusinessKpiPolicy.effectiveOutstanding(alias,type), payStatus=dueAwarePaymentStatus(alias,type), due=safeDate("h.due_date");
        String bucket="CASE WHEN "+due+" IS NULL OR "+due+">=CURRENT_DATE THEN 'CURRENT' WHEN CURRENT_DATE-"+due+" BETWEEN 1 AND 30 THEN '1-30 DAYS' WHEN CURRENT_DATE-"+due+" BETWEEN 31 AND 60 THEN '31-60 DAYS' WHEN CURRENT_DATE-"+due+" BETWEEN 61 AND 90 THEN '61-90 DAYS' ELSE '90+ DAYS' END";
        List<ReportColumn> c=cols(col("invoice","Invoice","TEXT",true,false,140),col("date","Invoice Date","DATE",true,false,105),col("party",sales?"Customer":"Supplier","TEXT",true,false,190),col("due_date","Due Date","DATE",true,false,105),col("age_bucket","Age Bucket","STATUS",true,false,110),col("invoice_value","Invoice Value","MONEY",true,true,120),col("paid","Paid","MONEY",true,true,110),col("outstanding",sales?"Receivable":"Payable","MONEY",true,true,120),col("payment_status","Payment","STATUS",true,false,110),col("days_overdue","Days Overdue","NUMBER",true,true,105));
        List<ReportRow> rows=jdbc.query("SELECT h.id,h.invoice_no,"+safeDate("h.invoice_date")+",COALESCE(NULLIF(h."+snapshot+",''),pm.name,''),COALESCE(CAST("+due+" AS text),''),"+bucket+",h.total_amount,"+BusinessKpiPolicy.effectivePaidCorrelated(alias,type)+","+outstanding+","+payStatus+",CASE WHEN "+due+" IS NULL OR "+due+">=CURRENT_DATE THEN 0 ELSE CURRENT_DATE-"+due+" END FROM "+h+" h LEFT JOIN party_master pm ON pm.id=h."+fk+" WHERE "+w.sql+" AND ("+outstanding+")>0.0001",
                (x,i)->row(x.getLong(1),List.of(s(x,2),s(x,3),s(x,4),s(x,5),s(x,6),n(x,7),n(x,8),n(x,9),s(x,10),n(x,11)),sales?"/fxml/pages/SalesList.fxml":"/fxml/pages/PurchaseList.fxml",x.getLong(1),s(x,2)),w.args());
        return new Raw(c,rows,"outstanding");
    }

    private Raw stockSummary(Request r) {
        Sql w=new Sql("COALESCE(i.is_active::text,'1') IN ('1','true','t')");
        if(!r.item.isBlank())w.add("(COALESCE(NULLIF(i.description,''),i.item_code)=? OR i.item_code=?)",r.item,r.item);
        if(!r.warehouse.isBlank())w.add("COALESCE(i.location,'')=?",r.warehouse);
        String qty="COALESCE(cs.quantity,COALESCE(i.opening_stock,0)-COALESCE(i.reserved_stock,0),0)",cost="COALESCE(cs.average_unit_cost,i.purchase_price,0)",value="("+qty+")*("+cost+")";
        String status="CASE WHEN "+qty+"<=0 THEN 'OUT OF STOCK' WHEN "+qty+"<=COALESCE(i.minimum_stock,0) THEN 'LOW STOCK' ELSE 'IN STOCK' END";
        List<ReportColumn> c=cols(col("item","Item","TEXT",true,false,200),col("code","Code","TEXT",true,false,115),col("category","Category","TEXT",true,false,125),col("unit","Unit","TEXT",true,false,75),col("quantity","Closing Stock","NUMBER",true,true,110),col("reserved","Reserved","NUMBER",false,true,90),col("available","Available","NUMBER",true,true,95),col("avg_cost","Avg. Cost","MONEY",true,true,105),col("stock_value","Stock Value","MONEY",true,true,120),col("minimum","Minimum","NUMBER",true,true,90),col("stock_status","Status","STATUS",true,false,115),col("location","Location","TEXT",true,false,120));
        List<ReportRow> rows=jdbc.query("SELECT i.id,COALESCE(NULLIF(i.description,''),i.item_code),i.item_code,COALESCE(i.category,''),COALESCE(i.unit,''),"+qty+",COALESCE(i.reserved_stock,0),GREATEST(("+qty+")-COALESCE(i.reserved_stock,0),0),"+cost+","+value+",COALESCE(i.minimum_stock,0),"+status+",COALESCE(i.location,'') FROM item_master i LEFT JOIN inventory_cost_state cs ON cs.item_code=i.item_code WHERE "+w.sql,
                (x,i)->row(x.getLong(1),List.of(s(x,2),s(x,3),s(x,4),s(x,5),n(x,6),n(x,7),n(x,8),n(x,9),n(x,10),n(x,11),s(x,12),s(x,13)),"/fxml/pages/Inventory.fxml",x.getLong(1),s(x,3)),w.args());
        return new Raw(c,rows,"stock_value");
    }

    private Raw itemLedger(Request r) {
        Sql w=new Sql(safeTimestampDate("l.created_at")+" BETWEEN ? AND ?",r.from,r.to);
        if(!r.item.isBlank())w.add("(COALESCE(NULLIF(i.description,''),l.item_code)=? OR l.item_code=?)",r.item,r.item);
        List<ReportColumn> c=cols(col("date","Date","DATE",true,false,105),col("item","Item","TEXT",true,false,190),col("code","Code","TEXT",true,false,110),col("movement","Movement","STATUS",true,false,125),col("reference","Reference ID","TEXT",true,false,105),col("qty_change","Qty Change","NUMBER",true,true,100),col("unit_cost","Unit Cost","MONEY",true,true,105),col("value_change","Value Change","MONEY",true,true,115));
        List<ReportRow> rows=jdbc.query("SELECT l.id,"+safeTimestampDate("l.created_at")+",COALESCE(NULLIF(i.description,''),l.item_code),l.item_code,UPPER(COALESCE(l.movement_type,'')),COALESCE(CAST(l.reference_id AS text),''),l.quantity_change,l.unit_cost,l.value_change FROM inventory_cost_ledger l LEFT JOIN item_master i ON i.item_code=l.item_code WHERE "+w.sql,
                (x,i)->row(x.getLong(1),List.of(s(x,2),s(x,3),s(x,4),s(x,5),s(x,6),n(x,7),n(x,8),n(x,9)),"/fxml/pages/Inventory.fxml",null,s(x,6)),w.args());
        return new Raw(c,rows,"value_change");
    }

    private Raw banking(Request r) {
        Sql w=new Sql(safeDate("t.transaction_date")+" BETWEEN ? AND ?",r.from,r.to);
        if(!r.bankStatus.isBlank())w.add("UPPER(COALESCE(t.status,'UNMATCHED'))=?",r.bankStatus);
        List<ReportColumn> c=cols(col("date","Date","DATE",true,false,105),col("bank","Bank","TEXT",true,false,150),col("account","Account","TEXT",false,false,145),col("description","Description","TEXT",true,false,240),col("reference","Reference","TEXT",true,false,135),col("debit","Debit","MONEY",true,true,110),col("credit","Credit","MONEY",true,true,110),col("balance","Balance","MONEY",true,true,115),col("bank_status","Status","STATUS",true,false,110),col("allocated","Allocated","MONEY",true,true,110),col("unallocated","Unallocated","MONEY",true,true,115));
        String allocated="COALESCE((SELECT SUM(a.allocated_amount) FROM bank_reconciliation_allocation a WHERE a.statement_transaction_id=t.id AND a.reversed_at IS NULL),0)",movement="GREATEST(COALESCE(t.credit_amount,0),COALESCE(t.debit_amount,0))";
        List<ReportRow> rows=jdbc.query("SELECT t.id,"+safeDate("t.transaction_date")+",COALESCE(b.bank_name,''),COALESCE(b.bank_account,''),COALESCE(t.original_description,''),COALESCE(t.original_reference,''),COALESCE(t.debit_amount,0),COALESCE(t.credit_amount,0),COALESCE(t.balance,0),UPPER(COALESCE(t.status,'UNMATCHED')),"+allocated+",GREATEST(("+movement+")-("+allocated+"),0) FROM bank_statement_transaction t JOIN bank_statement_import b ON b.id=t.import_id WHERE "+w.sql,
                (x,i)->row(x.getLong(1),List.of(s(x,2),s(x,3),s(x,4),s(x,5),s(x,6),n(x,7),n(x,8),n(x,9),s(x,10),n(x,11),n(x,12)),"/fxml/pages/BankStatement.fxml",x.getLong(1),s(x,6)),w.args());
        return new Raw(c,rows,"credit");
    }

    private Raw profitability(Request r) {
        Sql w=new Sql(BusinessKpiPolicy.salesActive("h")+" AND "+safeDate("h.invoice_date")+" BETWEEN ? AND ?",r.from,r.to);
        if(!r.party.isBlank())w.add("COALESCE(NULLIF(h.customer_name_snapshot,''),pm.name,'')=?",r.party);
        if(!r.salesperson.isBlank())w.add("COALESCE(h.salesperson,'')=?",r.salesperson);
        if(!r.item.isBlank())w.add("EXISTS (SELECT 1 FROM sales_line sx LEFT JOIN item_master ix ON ix.item_code=sx.item_code WHERE sx.sales_id=h.id AND (COALESCE(NULLIF(ix.description,''),sx.item_code)=? OR sx.item_code=?))",r.item,r.item);
        String taxable="COALESCE((SELECT SUM((sl.quantity*sl.rate)-COALESCE(sl.discount_amount,0)) FROM sales_line sl WHERE sl.sales_id=h.id),0)";
        String cogs="COALESCE((SELECT SUM(sl.quantity*COALESCE(sl.unit_cost_snapshot,0)) FROM sales_line sl WHERE sl.sales_id=h.id),0)";
        String retTax="COALESCE((SELECT SUM(CASE WHEN slr.id IS NOT NULL AND COALESCE(slr.quantity,0)>0 THEN rr.quantity*(((slr.quantity*slr.rate)-COALESCE(slr.discount_amount,0))/slr.quantity) ELSE rr.amount END) FROM return_register rr LEFT JOIN sales_line slr ON slr.id=rr.source_line_id WHERE rr.invoice_no=h.invoice_no AND UPPER(COALESCE(rr.return_type,'')) IN ('SALE RETURN','SALES RETURN') AND "+BusinessKpiPolicy.returnsActive("rr")+"),0)";
        String retCogs="COALESCE((SELECT SUM(CASE WHEN slr.id IS NOT NULL THEN rr.quantity*COALESCE(slr.unit_cost_snapshot,0) ELSE 0 END) FROM return_register rr LEFT JOIN sales_line slr ON slr.id=rr.source_line_id WHERE rr.invoice_no=h.invoice_no AND UPPER(COALESCE(rr.return_type,'')) IN ('SALE RETURN','SALES RETURN') AND "+BusinessKpiPolicy.returnsActive("rr")+"),0)";
        String netTax="GREATEST(("+taxable+")-("+retTax+"),0)",netCogs="GREATEST(("+cogs+")-("+retCogs+"),0)",profit="("+netTax+")-("+netCogs+")",margin="CASE WHEN ("+netTax+")>0 THEN (("+profit+")/("+netTax+"))*100 ELSE 0 END";
        List<ReportColumn> c=cols(col("invoice","Invoice","TEXT",true,false,140),col("date","Date","DATE",true,false,105),col("customer","Customer","TEXT",true,false,190),col("salesperson","Salesperson","TEXT",true,false,125),col("gross_taxable","Gross Taxable","MONEY",true,true,120),col("return_taxable","Return Taxable","MONEY",true,true,120),col("net_taxable","Net Taxable","MONEY",true,true,115),col("cogs","Net COGS","MONEY",true,true,110),col("profit","Gross Profit","MONEY",true,true,115),col("margin","Margin %","PERCENT",true,true,95));
        List<ReportRow> rows=jdbc.query("SELECT h.id,h.invoice_no,"+safeDate("h.invoice_date")+",COALESCE(NULLIF(h.customer_name_snapshot,''),pm.name,''),COALESCE(h.salesperson,''),"+taxable+","+retTax+","+netTax+","+netCogs+","+profit+","+margin+" FROM sales_header h LEFT JOIN party_master pm ON pm.id=h.customer_id WHERE "+w.sql,
                (x,i)->row(x.getLong(1),List.of(s(x,2),s(x,3),s(x,4),s(x,5),n(x,6),n(x,7),n(x,8),n(x,9),n(x,10),n(x,11)),"/fxml/pages/SalesList.fxml",x.getLong(1),s(x,2)),w.args());
        return new Raw(c,rows,"profit");
    }

    private List<ReportRow> postFilter(List<ReportColumn> columns,List<ReportRow> source,Request r){
        Map<String,Integer> index=index(columns); List<ReportRow> out=new ArrayList<>();
        for(ReportRow row:source){
            if(!r.paymentStatus.isBlank()&&!equalsAt(row,index,"payment_status",r.paymentStatus))continue;
            if(!r.returnStatus.isBlank()&&!equalsAt(row,index,"return_status",r.returnStatus))continue;
            if(!r.bankStatus.isBlank()&&!equalsAt(row,index,"bank_status",r.bankStatus))continue;
            if(!r.search.isBlank()){String q=r.search.toLowerCase(Locale.ROOT);boolean hit=false;for(String v:row.values())if(v!=null&&v.toLowerCase(Locale.ROOT).contains(q)){hit=true;break;}if(!hit)continue;}
            if(r.minAmount!=null||r.maxAmount!=null){String key=amountKey(index);if(key!=null){double amount=parse(row.values().get(index.get(key)));if(r.minAmount!=null&&amount+EPS<r.minAmount)continue;if(r.maxAmount!=null&&amount-EPS>r.maxAmount)continue;}}
            out.add(row);
        }
        return out;
    }

    private void sort(List<ReportColumn> columns,List<ReportRow> rows,String requested,String direction,String groupBy){
        Map<String,Integer> idx=index(columns);String key=idx.containsKey(requested)?requested:defaultSortKey(idx);int pos=idx.get(key);ReportColumn col=columns.get(pos);
        Comparator<ReportRow> detail=(a,b)->col.numeric()?Double.compare(parse(a.values().get(pos)),parse(b.values().get(pos))):natural(a.values().get(pos),b.values().get(pos));
        if(!"ASC".equals(direction))detail=detail.reversed();
        Comparator<ReportRow> cmp=detail;
        if(groupBy!=null&&!groupBy.isBlank()&&!"NONE".equals(groupBy))cmp=Comparator.<ReportRow, String>comparing(row -> groupValue(columns, row, groupBy), ReportingService::natural).thenComparing(detail);
        rows.sort(cmp.thenComparing(ReportRow::rowKey,Comparator.nullsLast(String::compareTo)));
    }

    private List<ReportRow> withGroups(List<ReportColumn> columns,List<ReportRow> rows,String groupBy){
        if(groupBy==null||groupBy.isBlank()||"NONE".equals(groupBy))return rows;
        List<ReportRow> out=new ArrayList<>(rows.size());for(ReportRow row:rows){String g=groupValue(columns,row,groupBy);out.add(new ReportRow(row.rowKey(),row.values(),g,row.targetFxml(),row.targetId(),row.referenceNo()));}return out;
    }

    private static String groupValue(List<ReportColumn> columns,ReportRow row,String groupBy){
        Map<String,Integer> idx=index(columns);String normalized=up(groupBy);
        if("MONTH".equals(normalized)){Integer p=idx.get("date");if(p==null)return "Unknown month";String date=row.values().get(p);return date!=null&&date.length()>=7?date.substring(0,7):"Unknown month";}
        if("MARGIN BAND".equals(normalized)){Integer p=idx.get("margin");if(p==null)return "N/A";double margin=parse(row.values().get(p));if(margin<0)return "Negative";if(margin<10)return "0-10%";if(margin<20)return "10-20%";return "20%+";}
        String key=groupKey(groupBy);Integer p=idx.get(key);return p==null?"":Objects.toString(row.values().get(p),"");
    }

    private List<ReportMetric> metrics(String reportId,List<ReportColumn> columns,List<ReportRow> rows){
        Map<String,Integer> idx=index(columns);List<ReportMetric> m=new ArrayList<>();
        switch(reportId){
            case "SALES_REGISTER","SALES_BY_CUSTOMER"->{metric(m,"gross","Gross Sales",sum(rows,idx,"gross"),"MONEY","");metric(m,"returned","Returns",sum(rows,idx,"returned"),"MONEY","Approved Returns");metric(m,"net","Net Sales",sum(rows,idx,"net"),"MONEY","Gross minus Returns");metric(m,"gst","GST",sum(rows,idx,"gst"),"MONEY","");metric(m,"outstanding","Outstanding",sum(rows,idx,"outstanding"),"MONEY","");metric(m,"records","Invoices",rows.size(),"COUNT","");}
            case "SALES_BY_ITEM"->{metric(m,"quantity","Quantity",sum(rows,idx,"quantity"),"NUMBER","");metric(m,"returned_qty","Returned Qty",sum(rows,idx,"returned_qty"),"NUMBER","");metric(m,"net_qty","Net Qty",sum(rows,idx,"net_qty"),"NUMBER","");metric(m,"net","Net Taxable",sum(rows,idx,"net"),"MONEY","");metric(m,"gst","GST",sum(rows,idx,"gst"),"MONEY","");}
            case "PURCHASE_REGISTER"->{metric(m,"gross","Gross Purchases",sum(rows,idx,"gross"),"MONEY","");metric(m,"returned","Returns",sum(rows,idx,"returned"),"MONEY","");metric(m,"net","Net Purchases",sum(rows,idx,"net"),"MONEY","");metric(m,"gst","Input GST",sum(rows,idx,"gst"),"MONEY","");metric(m,"outstanding","Payable",sum(rows,idx,"outstanding"),"MONEY","");}
            case "RETURNS_ANALYSIS"->{metric(m,"amount","Return Value",sum(rows,idx,"amount"),"MONEY","");metric(m,"refunded","Refunded",sum(rows,idx,"refunded"),"MONEY","");metric(m,"balance","Refund Balance",Math.max(0,sum(rows,idx,"amount")-sum(rows,idx,"refunded")),"MONEY","");metric(m,"quantity","Returned Qty",sum(rows,idx,"quantity"),"NUMBER","");metric(m,"records","Return Lines",rows.size(),"COUNT","");}
            case "GST_TAX"->{metric(m,"taxable","Taxable Value",sum(rows,idx,"taxable"),"MONEY","");metric(m,"cgst","CGST",sum(rows,idx,"cgst"),"MONEY","");metric(m,"sgst","SGST",sum(rows,idx,"sgst"),"MONEY","");metric(m,"igst","IGST",sum(rows,idx,"igst"),"MONEY","");metric(m,"total_gst","Total GST",sum(rows,idx,"total_gst"),"MONEY","");}
            case "RECEIVABLE_AGEING"->{metric(m,"outstanding","Receivables",sum(rows,idx,"outstanding"),"MONEY","");ageMetrics(m,rows,idx);}
            case "PAYABLE_AGEING"->{metric(m,"outstanding","Payables",sum(rows,idx,"outstanding"),"MONEY","");ageMetrics(m,rows,idx);}
            case "STOCK_SUMMARY"->{metric(m,"stock_value","Stock Value",sum(rows,idx,"stock_value"),"MONEY","");metric(m,"quantity","Stock Qty",sum(rows,idx,"quantity"),"NUMBER","");metric(m,"low","Low Stock",countValue(rows,idx,"stock_status","LOW STOCK"),"COUNT","");metric(m,"out","Out of Stock",countValue(rows,idx,"stock_status","OUT OF STOCK"),"COUNT","");}
            case "ITEM_LEDGER"->{metric(m,"qty_change","Net Quantity Movement",sum(rows,idx,"qty_change"),"NUMBER","");metric(m,"value_change","Net Value Movement",sum(rows,idx,"value_change"),"MONEY","");metric(m,"records","Movements",rows.size(),"COUNT","");}
            case "BANK_RECONCILIATION"->{metric(m,"debit","Total Debit",sum(rows,idx,"debit"),"MONEY","");metric(m,"credit","Total Credit",sum(rows,idx,"credit"),"MONEY","");metric(m,"allocated","Allocated",sum(rows,idx,"allocated"),"MONEY","");metric(m,"unallocated","Unallocated",sum(rows,idx,"unallocated"),"MONEY","");}
            case "PROFITABILITY"->{metric(m,"net_taxable","Net Sales",sum(rows,idx,"net_taxable"),"MONEY","");metric(m,"cogs","COGS",sum(rows,idx,"cogs"),"MONEY","");double p=sum(rows,idx,"profit"),n=sum(rows,idx,"net_taxable");metric(m,"profit","Gross Profit",p,"MONEY","");metric(m,"margin","Margin %",n==0?0:(p/n)*100,"PERCENT","");}
        }
        return m;
    }

    private void ageMetrics(List<ReportMetric> m,List<ReportRow> rows,Map<String,Integer> idx){
        for(String bucket:List.of("CURRENT","1-30 DAYS","31-60 DAYS","61-90 DAYS","90+ DAYS")){double value=0;for(ReportRow row:rows)if(equalsAt(row,idx,"age_bucket",bucket))value+=at(row,idx,"outstanding");metric(m,"age_"+bucket.replaceAll("[^A-Z0-9]+","_"),bucket,value,"MONEY","");}
    }

    private Map<String,String> totals(List<ReportColumn> columns,List<ReportRow> rows){Map<String,Integer> idx=index(columns);LinkedHashMap<String,String> out=new LinkedHashMap<>();for(ReportColumn c:columns)if(c.numeric())out.put(c.key(),dec(sum(rows,idx,c.key())));return out;}
    private Map<String,String> applied(Request r){LinkedHashMap<String,String> m=new LinkedHashMap<>();m.put("Period",r.from+" to "+r.to);put(m,"Party",r.party);put(m,"Item",r.item);put(m,"Salesperson",r.salesperson);put(m,"Document Status",r.documentStatus);put(m,"Payment Status",r.paymentStatus);put(m,"Return Status",r.returnStatus);put(m,"GST Rate",r.gstRate);put(m,"Warehouse",r.warehouse);put(m,"Bank Status",r.bankStatus);put(m,"Search",r.search);if(!"NONE".equals(r.groupBy))m.put("Group By",r.groupBy);return m;}

    private Request normalize(ReportRequest q){
        if(q==null)throw new IllegalArgumentException("Report request is required");String id=up(q.reportId());if(id.isBlank())id="SALES_REGISTER";LocalDate from=parseDate(q.from(),BusinessClock.today().withDayOfMonth(1)),to=parseDate(q.to(),BusinessClock.today());if(from.isAfter(to))throw new IllegalArgumentException("Report start date cannot be after end date");int size=Math.max(10,Math.min(250,q.size()==null?50:q.size())),page=Math.max(0,q.page()==null?0:q.page());Double min=q.minAmount(),max=q.maxAmount();if(min!=null&&max!=null&&min>max)throw new IllegalArgumentException("Minimum amount cannot exceed maximum amount");return new Request(id,from,to,norm(q.party()),norm(q.item()),norm(q.salesperson()),up(q.documentStatus()),up(q.paymentStatus()),up(q.returnStatus()),norm(q.gstRate()),norm(q.warehouse()),up(q.bankStatus()),norm(q.search()),normalizeGroup(q.groupBy()),norm(q.sortKey()),"ASC".equalsIgnoreCase(q.sortDirection())?"ASC":"DESC",min,max,page,size,q.visibleColumns()==null?List.of():List.copyOf(q.visibleColumns()));}

    private static String approvedReturnAmount(String h,String type){String types="SALES RETURN".equals(type)?"IN ('SALE RETURN','SALES RETURN')":"='PURCHASE RETURN'";return "COALESCE((SELECT SUM(ar.amount) FROM return_register ar WHERE ar.invoice_no="+h+".invoice_no AND UPPER(COALESCE(ar.return_type,'')) "+types+" AND "+BusinessKpiPolicy.returnsActive("ar")+"),0)";}
    private static String approvedReturnedQty(String h,String type){String types="SALES RETURN".equals(type)?"IN ('SALE RETURN','SALES RETURN')":"='PURCHASE RETURN'";return "COALESCE((SELECT SUM(ar.quantity) FROM return_register ar WHERE ar.invoice_no="+h+".invoice_no AND UPPER(COALESCE(ar.return_type,'')) "+types+" AND "+BusinessKpiPolicy.returnsActive("ar")+"),0)";}
    private static String settledReturnAmount(String h,String type){String types="SALES RETURN".equals(type)?"IN ('SALE RETURN','SALES RETURN')":"='PURCHASE RETURN'";return "COALESCE((SELECT SUM(rf.amount+COALESCE(rf.rounding_adjustment,0)) FROM return_refund rf WHERE rf.return_no IN (SELECT DISTINCT rr.return_no FROM return_register rr WHERE rr.invoice_no="+h+".invoice_no AND UPPER(COALESCE(rr.return_type,'')) "+types+" AND "+BusinessKpiPolicy.returnsActive("rr")+")),0)";}
    private static String returnStatus(String returnedQty,String originalQty,String h,String type){String types="SALES RETURN".equals(type)?"IN ('SALE RETURN','SALES RETURN')":"='PURCHASE RETURN'";String pending="EXISTS (SELECT 1 FROM return_register rp WHERE rp.invoice_no="+h+".invoice_no AND UPPER(COALESCE(rp.return_type,'')) "+types+" AND UPPER(COALESCE(rp.status,''))='PENDING APPROVAL')";return "CASE WHEN ("+returnedQty+")>0.0001 AND ("+returnedQty+")+0.0001>=("+originalQty+") AND ("+originalQty+")>0 THEN 'FULLY RETURNED' WHEN ("+returnedQty+")>0.0001 THEN 'PARTIALLY RETURNED' WHEN "+pending+" THEN 'PENDING APPROVAL' ELSE 'N/A' END";}
    private static String refundStatus(String returnValue,String refunded){return "CASE WHEN ("+returnValue+")<=0.0001 THEN 'N/A' WHEN ("+refunded+")+0.0001>=("+returnValue+") THEN 'REFUNDED' WHEN ("+refunded+")>0.0001 THEN 'PARTIAL' ELSE 'PENDING' END";}
    private static String dueAwarePaymentStatus(String h,String type){String out=BusinessKpiPolicy.effectiveOutstanding(h,type),base=BusinessKpiPolicy.effectivePaymentStatus(h,type),due=safeDate(h+".due_date");return "CASE WHEN ("+out+")>0.0001 AND "+due+" IS NOT NULL AND "+due+"<CURRENT_DATE THEN 'OVERDUE' ELSE ("+base+") END";}

    private static String safeDate(String c){String text="NULLIF(TRIM(CAST("+c+" AS text)),'')",slash="SPLIT_PART("+text+",'/',3)||'-'||SPLIT_PART("+text+",'/',2)||'-'||SPLIT_PART("+text+",'/',1)",dash="SPLIT_PART("+text+",'-',3)||'-'||SPLIT_PART("+text+",'-',2)||'-'||SPLIT_PART("+text+",'-',1)";return "CASE WHEN "+text+" IS NULL THEN NULL WHEN "+text+" ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' AND pg_input_is_valid("+text+",'date') THEN CAST("+text+" AS date) WHEN "+text+" ~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}$' AND pg_input_is_valid("+slash+",'date') THEN CAST("+slash+" AS date) WHEN "+text+" ~ '^[0-9]{2}-[0-9]{2}-[0-9]{4}$' AND pg_input_is_valid("+dash+",'date') THEN CAST("+dash+" AS date) ELSE NULL END";}
    private static String safeTimestampDate(String c){String text="NULLIF(TRIM(CAST("+c+" AS text)),'')",prefix="SUBSTRING("+text+" FROM 1 FOR 10)";return "CASE WHEN "+text+" IS NULL THEN NULL WHEN "+prefix+" ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' AND pg_input_is_valid("+prefix+",'date') THEN CAST("+prefix+" AS date) ELSE NULL END";}

    private static ReportDefinition def(String id,String category,String title,String description,List<String> groups,List<String> filters){return new ReportDefinition(id,category,title,description,groups,filters);}
    private static List<String> commonSalesFilters(){return List.of("Period","Party","Item","Salesperson","Document Status","Payment Status","Return Status","GST Rate","Search","Amount");}
    private static List<String> commonPurchaseFilters(){return List.of("Period","Party","Item","Document Status","Payment Status","Return Status","GST Rate","Warehouse","Search","Amount");}
    private static ReportColumn col(String k,String l,String t,boolean v,boolean n,double w){return new ReportColumn(k,l,t,v,n,w);}
    @SafeVarargs private static <T> List<T> cols(T... values){return List.of(values);}
    private static ReportRow row(long id,List<String> values,String target,Long targetId,String ref){return row(String.valueOf(id),values,target,targetId,ref);}
    private static ReportRow row(String id,List<String> values,String target,Long targetId,String ref){return new ReportRow(id,values,"",target,targetId,ref);}
    private static String s(JpaNativeRepository.NativeRow r,int i){return Objects.toString(r.getObject(i),"");}
    private static String n(JpaNativeRepository.NativeRow r,int i){Object o=r.getObject(i);if(o==null)return "0";if(o instanceof Number x)return dec(x.doubleValue());try{return dec(Double.parseDouble(String.valueOf(o)));}catch(Exception e){return "0";}}
    private List<String> strings(String sql,Object...args){return jdbc.query(sql,(r,i)->Objects.toString(r.getObject(1),""),args).stream().filter(x->!x.isBlank()).distinct().toList();}
    private static String dec(double value){if(!Double.isFinite(value))return "0";return BigDecimal.valueOf(value).setScale(4,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    private static double parse(String v){if(v==null||v.isBlank())return 0;try{return Double.parseDouble(v.replace(",","").replace("₹","").trim());}catch(Exception e){return 0;}}
    private static Map<String,Integer> index(List<ReportColumn> columns){LinkedHashMap<String,Integer> m=new LinkedHashMap<>();for(int i=0;i<columns.size();i++)m.put(columns.get(i).key(),i);return m;}
    private static boolean equalsAt(ReportRow row,Map<String,Integer> idx,String key,String expected){Integer p=idx.get(key);return p!=null&&expected.equalsIgnoreCase(row.values().get(p));}
    private static double at(ReportRow row,Map<String,Integer> idx,String key){Integer p=idx.get(key);return p==null?0:parse(row.values().get(p));}
    private static double sum(List<ReportRow> rows,Map<String,Integer> idx,String key){double v=0;for(ReportRow r:rows)v+=at(r,idx,key);return Math.round(v*10000d)/10000d;}
    private static long countValue(List<ReportRow> rows,Map<String,Integer> idx,String key,String value){return rows.stream().filter(r->equalsAt(r,idx,key,value)).count();}
    private static void metric(List<ReportMetric> m,String k,String l,double v,String f,String note){m.add(new ReportMetric(k,l,Math.round(v*10000d)/10000d,f,note));}
    private static int natural(String a,String b){return Objects.toString(a,"").compareToIgnoreCase(Objects.toString(b,""));}
    private static String defaultSortKey(Map<String,Integer> idx){for(String k:List.of("date","invoice","return_no","stock_value","profit","item"))if(idx.containsKey(k))return k;return idx.keySet().iterator().next();}
    private static String amountKey(Map<String,Integer> idx){for(String k:List.of("gross","net","amount","outstanding","stock_value","value_change","credit","debit","profit","invoice_value","line_total"))if(idx.containsKey(k))return k;return null;}
    private static String groupKey(String label){String g=up(label);return switch(g){case "CUSTOMER"->"customer";case "SUPPLIER"->"supplier";case "PARTY"->"party";case "ITEM"->"item";case "SALESPERSON"->"salesperson";case "PAYMENT STATUS"->"payment_status";case "RETURN STATUS"->"return_status";case "REFUND STATUS"->"refund_status";case "AGE BUCKET"->"age_bucket";case "CATEGORY"->"category";case "STOCK STATUS"->"stock_status";case "LOCATION"->"location";case "MOVEMENT TYPE"->"movement";case "STATUS"->"bank_status";case "BANK"->"bank";case "DIRECTION"->"direction";case "GST RATE"->"gst_rate";case "RETURN TYPE"->"type";default->"";};}
    private static String normalizeGroup(String value){String v=norm(value);return v.isBlank()?"NONE":up(v);}
    private static String norm(String v){return v==null?"":v.trim();}
    private static String up(String v){return norm(v).toUpperCase(Locale.ROOT);}
    private static LocalDate parseDate(String v,LocalDate fallback){try{return v==null||v.isBlank()?fallback:LocalDate.parse(v.trim());}catch(Exception e){throw new IllegalArgumentException("Invalid report date: "+v);}}
    private static void put(Map<String,String> m,String k,String v){if(v!=null&&!v.isBlank())m.put(k,v);}

    private record Raw(List<ReportColumn> columns,List<ReportRow> rows,String primaryAmount){}
    private record Request(String reportId,LocalDate from,LocalDate to,String party,String item,String salesperson,String documentStatus,String paymentStatus,String returnStatus,String gstRate,String warehouse,String bankStatus,String search,String groupBy,String sortKey,String sortDirection,Double minAmount,Double maxAmount,int page,int size,List<String> visibleColumns){}
    private static final class Sql{final StringBuilder text=new StringBuilder();final List<Object> values=new ArrayList<>();String sql;Sql(String first,Object...args){text.append(first);values.addAll(Arrays.asList(args));sync();}void add(String part,Object...args){text.append(" AND ").append(part);values.addAll(Arrays.asList(args));sync();}Object[] args(){return values.toArray();}private void sync(){sql=text.toString();}}
}
