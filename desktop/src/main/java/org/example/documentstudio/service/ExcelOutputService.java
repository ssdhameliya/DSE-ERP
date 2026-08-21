package org.example.documentstudio.service;

import org.example.config.WorkspaceManager;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ExcelTemplate;
import org.example.model.Purchase;
import org.example.model.PurchaseCharge;
import org.example.model.Sales;
import org.example.model.SalesCharge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runtime Excel equivalent of DocumentOutputService. A built-in workbook is always the safe fallback. */
public final class ExcelOutputService {
    private ExcelOutputService() {}

    public static Path sales(Sales sale) throws IOException {
        return generate(DocumentType.SALES_INVOICE, TemplateDataFactory.fromSales(sale), salesCharges(sale), "Sales-" + safeFile(sale.getInvoiceNo()));
    }

    public static Path purchase(Purchase purchase) throws IOException {
        return generate(DocumentType.PURCHASE_INVOICE, TemplateDataFactory.fromPurchase(purchase), purchaseCharges(purchase), "Purchase-" + safeFile(purchase.getInvoiceNo()));
    }

    /** Excel equivalent of DocumentOutputService for every currently automatic Document Studio flow. */
    public static Path generate(DocumentType type, String documentNo) throws IOException {
        if (type == null || !DocumentFlowRegistry.isExcelAutomatic(type))
            throw new IOException((type == null ? "This document type" : type.label()) + " is not connected to automatic Excel output.");
        if (documentNo == null || documentNo.isBlank()) throw new IOException("A valid document number is required.");
        try {
            if (type == DocumentType.SALES_INVOICE) {
                Sales sale = new org.example.service.SalesService().getByInvoice(documentNo);
                if (sale == null) throw new IOException("Sales invoice " + documentNo + " was not found.");
                return sales(sale);
            }
            if (type == DocumentType.PURCHASE_INVOICE) {
                Purchase purchase = new org.example.service.PurchaseService().getByInvoice(documentNo);
                if (purchase == null) throw new IOException("Purchase invoice " + documentNo + " was not found.");
                return purchase(purchase);
            }
            String prefix = switch (type) {
                case PURCHASE_RETURN -> "Purchase-Return-";
                case SALES_RETURN -> "Sales-Return-";
                case QUOTATION -> "Quotation-";
                default -> type.name().replace('_', '-') + "-";
            };
            return generate(type, DocumentDataService.load(type, documentNo), List.of(), prefix + safeFile(documentNo));
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("Excel output could not be created: " + rootMessage(error), error); }
    }

    public static Path generate(DocumentType type, org.example.documentstudio.model.TemplateData data,
                                List<ExcelTemplateRenderer.ChargeData> charges, String baseName) throws IOException {
        Path output = WorkspaceManager.getTempFolder().resolve(baseName + ".xlsx");
        ExcelTemplate template = ExcelTemplateStorageService.defaultFor(type).orElse(null);
        if (template != null) {
            try { return ExcelTemplateRenderer.render(template, data, charges, output); }
            catch (Exception templateError) {
                System.err.println("[ExcelStudio] Default " + type + " template failed; using built-in workbook: " + templateError.getMessage());
            }
        }
        return ExcelTemplateRenderer.renderBuiltIn(type, data, charges, output);
    }

    private static List<ExcelTemplateRenderer.ChargeData> salesCharges(Sales sale) {
        List<ExcelTemplateRenderer.ChargeData> out=new ArrayList<>();
        if(sale!=null)for(SalesCharge c:sale.getCharges())out.add(new ExcelTemplateRenderer.ChargeData(c.getChargeType(),c.getAmount(),c.isTaxable(),c.getGstPercent(),c.getTaxAmount(),c.getTotalAmount()));
        return out;
    }
    private static List<ExcelTemplateRenderer.ChargeData> purchaseCharges(Purchase purchase) {
        List<ExcelTemplateRenderer.ChargeData> out=new ArrayList<>();
        if(purchase!=null)for(PurchaseCharge c:purchase.getCharges())out.add(new ExcelTemplateRenderer.ChargeData(c.getChargeType(),c.getAmount(),c.isTaxable(),c.getGstPercent(),c.getTaxAmount(),c.getTotalAmount()));
        return out;
    }
    private static String safeFile(String v){return v==null?"Document":v.replaceAll("[^A-Za-z0-9._-]","-");}
    private static String rootMessage(Throwable error){Throwable root=error;while(root.getCause()!=null&&root.getCause()!=root)root=root.getCause();return root.getMessage()==null?root.getClass().getSimpleName():root.getMessage();}
}
