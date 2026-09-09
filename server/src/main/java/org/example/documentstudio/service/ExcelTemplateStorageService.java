package org.example.documentstudio.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ExcelTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/** Runtime-only server storage context plus the desktop-equivalent built-in workbook. */
public final class ExcelTemplateStorageService {
    private static final ThreadLocal<Path> ROOT = new ThreadLocal<>();
    private ExcelTemplateStorageService() {}

    public static <T> T withRoot(Path root, Callable<T> work) throws Exception {
        Path previous = ROOT.get();
        ROOT.set(root == null ? null : root.toAbsolutePath().normalize());
        try { return work.call(); }
        finally { if (previous == null) ROOT.remove(); else ROOT.set(previous); }
    }

    public static Path sourceWorkbook(ExcelTemplate template) throws IOException {
        Path root = ROOT.get();
        if (root == null) throw new IOException("No server Excel template context is active.");
        Path file = root.resolve(template.getSourceFile()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file))
            throw new IOException("Excel template workbook is missing: " + file);
        return file;
    }

    /** Exact built-in workbook structure used by the desktop module. */
    public static Workbook starterWorkbook(DocumentType type) {
        DocumentType effective = type == null ? DocumentType.CUSTOM_ERP : type;
        Set<String> supported = TemplateFieldCatalog.excelFieldsFor(effective).stream()
                .map(org.example.documentstudio.model.TemplateFieldDefinition::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(safeSheetName(effective.label()));
        sheet.setDisplayGridlines(false);

        CellStyle title = workbook.createCellStyle();
        Font titleFont = workbook.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 18); title.setFont(titleFont);
        CellStyle header = workbook.createCellStyle();
        Font headerFont = workbook.createFont(); headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex()); header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle money = workbook.createCellStyle(); money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        Row r0 = sheet.createRow(0); r0.setHeightInPoints(42);
        r0.createCell(0).setCellValue("{{company.logo}}");
        Cell company = r0.createCell(1); company.setCellValue("{{company.name}}"); company.setCellStyle(title);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0,0,1,7));
        Row r1 = sheet.createRow(1); r1.createCell(1).setCellValue("{{company.address}}"); sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1,1,1,7));
        Row r2 = sheet.createRow(2); r2.createCell(1).setCellValue("GSTIN: {{company.gstin}}   •   {{company.phone}}   •   {{company.email}}"); sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2,2,1,7));

        String numberKey = documentNumberKey(effective), dateKey = documentDateKey(effective);
        Row r4 = sheet.createRow(4); r4.createCell(0).setCellValue(effective.label() + " No."); r4.createCell(1).setCellValue(token(numberKey,supported)); r4.createCell(4).setCellValue("Date"); r4.createCell(5).setCellValue(token(dateKey,supported));
        String partyNameKey=partyNameKey(effective), partyGstinKey=partyGstinKey(effective), partyAddressKey=partyAddressKey(effective);
        Row r5=sheet.createRow(5); r5.createCell(0).setCellValue("Party"); r5.createCell(1).setCellValue(token(partyNameKey,supported));
        if(!partyGstinKey.isBlank()&&supported.contains(partyGstinKey)){r5.createCell(4).setCellValue("GSTIN");r5.createCell(5).setCellValue(token(partyGstinKey,supported));}
        if(!partyAddressKey.isBlank()&&supported.contains(partyAddressKey)){Row r6=sheet.createRow(6);r6.createCell(0).setCellValue("Address");r6.createCell(1).setCellValue(token(partyAddressKey,supported));sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(6,6,1,7));}

        int rowCursor=8;
        if(effective==DocumentType.PAYMENT_RECEIPT&&supported.contains("receipt.amount")){Row amount=sheet.createRow(rowCursor++);amount.createCell(5).setCellValue("Amount");Cell value=amount.createCell(7);value.setCellValue("{{receipt.amount}}");value.setCellStyle(money);rowCursor++;}
        if(TemplateFieldCatalog.supportsItemRows(effective)){
            sheet.createFreezePane(0,rowCursor+1); Row h=sheet.createRow(rowCursor++); String[] cols={"#","Item / Description","HSN","Qty","Unit","Rate","GST %","Amount"};
            for(int i=0;i<cols.length;i++){Cell cell=h.createCell(i);cell.setCellValue(cols[i]);cell.setCellStyle(header);} Row item=sheet.createRow(rowCursor++);
            String[] itemFields={"item.serial","item.descriptionWithRemarks","item.hsn","item.quantity","item.unit","item.rate","item.gstPercent","item.taxable"}; for(int i=0;i<itemFields.length;i++)item.createCell(i).setCellValue(token(itemFields[i],supported)); rowCursor++;
        }else sheet.createFreezePane(0,7);
        if(TemplateFieldCatalog.supportsChargeRows(effective)){Row charge=sheet.createRow(rowCursor++);charge.createCell(4).setCellValue("{{charge.type}}");charge.createCell(6).setCellValue("{{charge.gstPercent}}");charge.createCell(7).setCellValue("{{charge.total}}");rowCursor++;}
        if(supported.contains("totals.grandTotal")){
            String grandKey=supported.contains("totals.roundedGrandTotal")?"totals.roundedGrandTotal":"totals.grandTotal";
            String[][] totals={{"Subtotal","totals.subtotal"},{"Additional Charges","totals.chargesAmount"},{"Gross Total Before Tax","totals.grossBeforeTax"},{"CGST","totals.cgstAmount"},{"SGST","totals.sgstAmount"},{"IGST","totals.igstAmount"},{"Round Off","totals.roundOff"},{"Grand Total",grandKey}};
            for(String[] entry:totals){if(!supported.contains(entry[1]))continue;Row row=sheet.createRow(rowCursor++);row.createCell(5).setCellValue(entry[0]);Cell value=row.createCell(7);value.setCellValue("{{"+entry[1]+"}}");value.setCellStyle(money);}
            if(supported.contains("totals.amountInWords")){rowCursor++;Row words=sheet.createRow(rowCursor++);words.createCell(0).setCellValue("Amount in words");words.createCell(1).setCellValue("{{totals.amountInWords}}");sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(words.getRowNum(),words.getRowNum(),1,7));}
        }
        rowCursor++; Row signature=sheet.createRow(rowCursor);signature.setHeightInPoints(42);signature.createCell(5).setCellValue("{{company.signature}}");sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowCursor,rowCursor+1,5,7));
        Row auth=sheet.createRow(rowCursor+2);auth.createCell(5).setCellValue("Authorized Signatory");sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowCursor+2,rowCursor+2,5,7));
        int[] widths={12,34,14,10,10,14,12,16};for(int i=0;i<8;i++)sheet.setColumnWidth(i,widths[i]*256);
        return workbook;
    }

    private static String token(String key,Set<String> supported){return key==null||key.isBlank()||supported==null||!supported.contains(key)?"":"{{"+key+"}}";}
    private static String documentNumberKey(DocumentType type){return switch(type){case SALES_INVOICE->"sales.number";case PURCHASE_INVOICE,PURCHASE_ORDER->"purchase.number";case QUOTATION->"quotation.number";case DELIVERY_CHALLAN->"delivery.number";case CREDIT_NOTE,DEBIT_NOTE,SALES_RETURN,PURCHASE_RETURN->"return.number";case PAYMENT_RECEIPT->"receipt.number";default->"";};}
    private static String documentDateKey(DocumentType type){return switch(type){case SALES_INVOICE->"sales.date";case PURCHASE_INVOICE,PURCHASE_ORDER->"purchase.date";case QUOTATION->"quotation.date";case DELIVERY_CHALLAN->"delivery.date";case CREDIT_NOTE,DEBIT_NOTE,SALES_RETURN,PURCHASE_RETURN->"return.date";case PAYMENT_RECEIPT->"receipt.date";default->"";};}
    private static String partyNameKey(DocumentType type){return switch(type){case SALES_INVOICE,QUOTATION,DELIVERY_CHALLAN->"customer.name";case PURCHASE_INVOICE,PURCHASE_ORDER->"supplier.name";case CREDIT_NOTE,DEBIT_NOTE,SALES_RETURN,PURCHASE_RETURN->"party.name";case PAYMENT_RECEIPT->"receipt.partyName";default->"";};}
    private static String partyGstinKey(DocumentType type){return switch(type){case SALES_INVOICE,QUOTATION,DELIVERY_CHALLAN->"customer.gstin";case PURCHASE_INVOICE,PURCHASE_ORDER->"supplier.gstin";case CREDIT_NOTE,DEBIT_NOTE,SALES_RETURN,PURCHASE_RETURN->"party.gstin";default->"";};}
    private static String partyAddressKey(DocumentType type){return switch(type){case SALES_INVOICE,QUOTATION,DELIVERY_CHALLAN->"customer.address";case PURCHASE_INVOICE,PURCHASE_ORDER->"supplier.address";case CREDIT_NOTE,DEBIT_NOTE,SALES_RETURN,PURCHASE_RETURN->"party.address";default->"";};}
    private static String safeSheetName(String value){String cleaned=(value==null?"Document":value).replace('\\',' ').replace('/',' ').replace('?',' ').replace('*',' ').replace('[',' ').replace(']',' ').replace(':',' ').trim();if(cleaned.isBlank())cleaned="Document";return cleaned.substring(0,Math.min(31,cleaned.length()));}
}
