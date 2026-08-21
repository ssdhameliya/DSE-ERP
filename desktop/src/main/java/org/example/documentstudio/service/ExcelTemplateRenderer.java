package org.example.documentstudio.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ExcelTemplate;
import org.example.documentstudio.model.TemplateData;
import org.example.documentstudio.model.TemplateCharge;
import org.example.invoice.calculation.InvoiceTaxCalculator;
import org.example.invoice.model.InvoiceTotals;
import org.example.invoice.model.TaxInvoiceCharge;
import org.example.invoice.model.TaxInvoiceItem;
import org.example.shared.DocumentCalculationEngine;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders ERP data into a user-created Excel template while preserving workbook formatting. */
public final class ExcelTemplateRenderer {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");
    private static final Pattern WHOLE_TOKEN = Pattern.compile("^\\s*\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}\\s*$");
    private static final Pattern A1_REFERENCE = Pattern.compile("(?<![A-Za-z0-9_])(?:(?:'[^']+'|[A-Za-z_][A-Za-z0-9_.]*)!)?(\\$?)([A-Za-z]{1,3})(\\$?)([0-9]+)(?![A-Za-z0-9_])");

    public record ChargeData(String type,double amount,boolean taxable,double gstPercent,double taxAmount,double total) {}

    private ExcelTemplateRenderer() {}

    public static Path renderSample(ExcelTemplate template, Path output) throws IOException {
        TemplateData sample = TemplateDataFactory.sampleFor(template == null ? DocumentType.CUSTOM_ERP : template.getDocumentType());
        return render(template, sample, List.of(), output);
    }

    /** Renders the unsaved workbook from the JavaFX editor without adding a temporary template to the library. */
    public static Path renderSampleWorkbook(Path source, DocumentType type, Path output) throws IOException {
        if (source == null || !Files.isRegularFile(source)) throw new IOException("Excel working copy is missing.");
        Files.createDirectories(output.toAbsolutePath().getParent());
        TemplateData sample = TemplateDataFactory.sampleFor(type == null ? DocumentType.CUSTOM_ERP : type);
        try (Workbook workbook = openDetachedWorkbook(source)) {
            validateKnownTokens(workbook, type, sample);
            fillWorkbook(workbook, sample, effectiveCharges(sample, List.of()));
            try (OutputStream out = Files.newOutputStream(output)) { workbook.write(out); }
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("Excel preview could not be rendered: " + rootMessage(error), error); }
        return output;
    }

    /** Renders an unsaved workbook with a real ERP record for Excel Studio preview/validation. */
    public static Path renderWorkbook(Path source, DocumentType type, TemplateData data, Path output) throws IOException {
        if (source == null || !Files.isRegularFile(source)) throw new IOException("Excel working copy is missing.");
        if (data == null) throw new IOException("Document data is required.");
        Files.createDirectories(output.toAbsolutePath().getParent());
        List<ChargeData> charges = new ArrayList<>();
        for (TemplateCharge c : data.charges()) charges.add(new ChargeData(c.type(), c.amount(), c.taxable(), c.gstPercent(), c.taxAmount(), c.total()));
        try (Workbook workbook = openDetachedWorkbook(source)) {
            validateKnownTokens(workbook, type, data);
            fillWorkbook(workbook, data, charges);
            try (OutputStream out = Files.newOutputStream(output)) { workbook.write(out); }
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("Excel preview could not be rendered: " + rootMessage(error), error); }
        return output;
    }

    public static Path render(ExcelTemplate template, TemplateData data, List<ChargeData> charges, Path output) throws IOException {
        if (template == null) throw new IOException("Excel template is required.");
        if (data == null) throw new IOException("Document data is required.");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Path source = ExcelTemplateStorageService.sourceWorkbook(template);
        try (Workbook workbook = openDetachedWorkbook(source)) {
            validateKnownTokens(workbook, template.getDocumentType(), data);
            fillWorkbook(workbook, data, effectiveCharges(data, charges));
            try (OutputStream out = Files.newOutputStream(output)) { workbook.write(out); }
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("Excel template could not be rendered: " + rootMessage(error), error); }
        return output;
    }

    /**
     * Apache POI may open an OOXML package from File in read/write mode. Rendering expands rows and
     * replaces ERP tokens, so a renderer must never hold the persisted template package directly.
     */
    static Workbook openDetachedWorkbook(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) throw new IOException("Excel workbook is missing: " + source);
        byte[] snapshot = Files.readAllBytes(source);
        if (snapshot.length == 0) throw new IOException("Excel workbook is empty: " + source);
        try {
            return WorkbookFactory.create(new ByteArrayInputStream(snapshot));
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Excel workbook could not be opened from a safe snapshot: " + rootMessage(error), error);
        }
    }

    public static Path renderBuiltIn(DocumentType type, TemplateData data, List<ChargeData> charges, Path output) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (Workbook workbook = ExcelTemplateStorageService.starterWorkbook(type)) {
            validateKnownTokens(workbook, type, data);
            fillWorkbook(workbook, data, effectiveCharges(data, charges));
            try (OutputStream out = Files.newOutputStream(output)) { workbook.write(out); }
        }
        return output;
    }


    private static List<ChargeData> effectiveCharges(TemplateData data, List<ChargeData> supplied) {
        if (supplied != null && !supplied.isEmpty()) return supplied;
        if (data == null || data.charges().isEmpty()) return List.of();
        List<ChargeData> out = new ArrayList<>();
        for (TemplateCharge charge : data.charges())
            out.add(new ChargeData(charge.type(), charge.amount(), charge.taxable(), charge.gstPercent(), charge.taxAmount(), charge.total()));
        return List.copyOf(out);
    }

    public static void fillWorkbook(Workbook workbook, TemplateData data, List<ChargeData> charges) {
        Map<String,String> values = new LinkedHashMap<>(data.values());
        values.putAll(derivedExcelValues(data, charges));
        for (int s=0;s<workbook.getNumberOfSheets();s++) {
            Sheet sheet = workbook.getSheetAt(s);
            expandRepeatingRows(sheet, data.items(), charges, data.gstType());
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() != CellType.STRING) continue;
                    String text = cell.getStringCellValue();
                    if (text == null || text.isBlank()) continue;
                    String withoutImages = placeImages(workbook, sheet, cell, text, data.images());
                    writeReplacedValue(cell, withoutImages, replaceTokens(withoutImages, values));
                }
            }
        }
        // Repeating rows can contain relative Excel formulas. POI stores the adjusted
        // formulas below and Excel recalculates the final workbook on first open.
        workbook.setForceFormulaRecalculation(true);
    }

    /** Excel-only derived values kept out of the shared PDF field catalog. */
    public static Map<String,String> derivedExcelValues(TemplateData data) {
        return derivedExcelValues(data, effectiveCharges(data, List.of()));
    }

    private static Map<String,String> derivedExcelValues(TemplateData data,List<ChargeData> charges) {
        if (data == null || data.items() == null || data.items().isEmpty()) return Map.of();
        try {
            List<ChargeData> safeCharges=charges==null?List.of():charges.stream().filter(Objects::nonNull).toList();
            List<TaxInvoiceCharge> invoiceCharges = safeCharges.stream()
                    .map(c -> new TaxInvoiceCharge(c.type(), c.amount(), c.taxable(), c.gstPercent()))
                    .toList();
            InvoiceTotals totals = InvoiceTaxCalculator.calculate(data.items(), invoiceCharges, data.gstType());
            double preRound = DocumentCalculationEngine.money(totals.grandTotal() - totals.roundOff());
            double chargesAmount=0,chargeTax=0,chargesTotal=0;
            for(ChargeData charge:safeCharges){
                DocumentCalculationEngine.ChargeResult result=DocumentCalculationEngine.charge(charge.amount(),charge.taxable(),charge.gstPercent());
                chargesAmount+=result.amount();chargeTax+=result.taxAmount();chargesTotal+=result.totalAmount();
            }
            Map<String,String> values = new LinkedHashMap<>();
            boolean interstate=DocumentCalculationEngine.taxMode(data.gstType())==DocumentCalculationEngine.TaxMode.IGST;
            values.put("totals.cgstAmount",interstate?"0.00":money(totals.cgst()));
            values.put("totals.sgstAmount",interstate?"0.00":money(totals.sgst()));
            values.put("totals.igstAmount",interstate?money(totals.igst()):"0.00");
            values.put("totals.gstAmount",money(DocumentCalculationEngine.money(totals.cgst()+totals.sgst()+totals.igst())));
            values.put("totals.chargesAmount",money(DocumentCalculationEngine.money(chargesAmount)));
            values.put("totals.chargeTaxAmount",money(DocumentCalculationEngine.money(chargeTax)));
            values.put("totals.chargesTotal",money(DocumentCalculationEngine.money(chargesTotal)));
            double grossBeforeTax = DocumentCalculationEngine.money(totals.basicAmount() - totals.discountAmount() + totals.chargesAmount());
            values.put("totals.grossBeforeTax", money(grossBeforeTax));
            values.put("totals.preRoundTotal", money(preRound));
            values.put("totals.roundOff", money(totals.roundOff()));
            values.put("totals.roundedGrandTotal", money(totals.grandTotal()));
            return Map.copyOf(values);
        } catch (Exception ignored) {
            return Map.of();
        }
    }


    private static String placeImages(Workbook workbook, Sheet sheet, Cell cell, String text, Map<String, Path> images) {
        if (text == null || text.isBlank() || images == null || images.isEmpty()) return text;
        String result = text;
        for (Map.Entry<String, Path> entry : images.entrySet()) {
            String token = "{{" + entry.getKey() + "}}";
            if (!result.contains(token)) continue;
            Path image = entry.getValue();
            if (image != null && Files.isRegularFile(image)) {
                try { addImage(workbook, sheet, cell, image); }
                catch (Exception error) { System.err.println("[ExcelStudio] Image placeholder " + entry.getKey() + " could not be rendered: " + error.getMessage()); }
            }
            result = result.replace(token, "").trim();
        }
        return result;
    }

    private static void addImage(Workbook workbook, Sheet sheet, Cell cell, Path image) throws IOException {
        byte[] bytes = Files.readAllBytes(image);
        int pictureType = pictureType(image);
        if (pictureType < 0) throw new IOException("Only PNG and JPEG images are supported for Excel image placeholders.");
        int pictureIndex = workbook.addPicture(bytes, pictureType);
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        CreationHelper helper = workbook.getCreationHelper();
        ClientAnchor anchor = helper.createClientAnchor();
        int row1 = cell.getRowIndex(), col1 = cell.getColumnIndex();
        CellRangeAddress merged = mergedAt(sheet, row1, col1);
        anchor.setRow1(row1); anchor.setCol1(col1);
        anchor.setRow2(merged == null ? Math.min(1048575, row1 + 4) : merged.getLastRow() + 1);
        anchor.setCol2(merged == null ? Math.min(16383, col1 + 3) : merged.getLastColumn() + 1);
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
        Picture picture = drawing.createPicture(anchor, pictureIndex);
        if (merged == null) {
            try { picture.resize(1.0); } catch (Exception ignored) { }
        }
    }

    private static CellRangeAddress mergedAt(Sheet sheet, int row, int col) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.isInRange(row, col)) return region;
        }
        return null;
    }

    private static int pictureType(Path image) {
        String name = image.getFileName() == null ? "" : image.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return Workbook.PICTURE_TYPE_PNG;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return Workbook.PICTURE_TYPE_JPEG;
        return -1;
    }

    private record RepeatSection(int row, boolean item) { }

    private static void expandRepeatingRows(Sheet sheet, List<TaxInvoiceItem> items, List<ChargeData> charges, String gstType) {
        List<RepeatSection> sections = new ArrayList<>();
        for (int row : findRows(sheet, "{{item.")) sections.add(new RepeatSection(row, true));
        for (int row : findRows(sheet, "{{charge.")) sections.add(new RepeatSection(row, false));
        sections.sort(Comparator.comparingInt(RepeatSection::row).reversed());
        for (RepeatSection section : sections) {
            if (section.item()) {
                clearStaleRepeatingValues(sheet, section.row(), "{{item.");
                expandItems(sheet, section.row(), items == null ? List.of() : items, gstType);
            } else {
                clearStaleRepeatingValues(sheet, section.row(), "{{charge.");
                expandCharges(sheet, section.row(), charges == null ? List.of() : charges, gstType);
            }
        }
    }

    /** Clears literal sample values only in columns owned by a repeating ERP row. */
    private static void clearStaleRepeatingValues(Sheet sheet, int templateRowIndex, String marker) {
        Row template = sheet.getRow(templateRowIndex);
        if (template == null) return;
        Set<Integer> mappedColumns = new LinkedHashSet<>();
        for (Cell cell : template) if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().contains(marker)) mappedColumns.add(cell.getColumnIndex());
        if (mappedColumns.isEmpty()) return;
        for (int r = templateRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || rowIsBlank(row) || rowContainsToken(row) || looksLikeSummaryRow(row)) break;
            if (!looksLikeRepeatingDataRow(template, row, mappedColumns)) break;
            for (int c : mappedColumns) {
                Cell cell = row.getCell(c);
                if (cell != null) cell.setBlank();
            }
        }
    }

    private static boolean looksLikeRepeatingDataRow(Row template, Row candidate, Set<Integer> mappedColumns) {
        int nonBlank = 0, styleComparable = 0, styleMatches = 0;
        for (int c : mappedColumns) {
            Cell cell = candidate.getCell(c);
            if (cell == null || cell.getCellType() == CellType.BLANK || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank())) continue;
            nonBlank++;
            Cell templateCell = template.getCell(c);
            if (templateCell != null) {
                styleComparable++;
                if (templateCell.getCellStyle().getIndex() == cell.getCellStyle().getIndex()) styleMatches++;
            }
        }
        if (nonBlank == 0) return false;
        if (styleComparable > 0 && styleMatches * 2 >= styleComparable) return true;
        return nonBlank >= Math.min(3, mappedColumns.size());
    }

    private static boolean looksLikeSummaryRow(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.STRING) continue;
            String value = cell.getStringCellValue().trim().toLowerCase(Locale.ROOT);
            if (value.matches(".*\\b(sub\\s*total|subtotal|grand\\s+total|total\\s+gst|cgst|sgst|igst|amount\\s+in\\s+words|round\\s*off|balance|paid\\s+amount)\\b.*")) return true;
        }
        return false;
    }

    private static boolean rowContainsToken(Row row) {
        for (Cell cell : row) if (cell.getCellType() == CellType.STRING && TOKEN.matcher(cell.getStringCellValue()).find()) return true;
        return false;
    }

    private static boolean rowIsBlank(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.BLANK) continue;
            if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank()) continue;
            return false;
        }
        return true;
    }

    private static List<Integer> findRows(Sheet sheet, String marker) {
        List<Integer> rows = new ArrayList<>();
        for (int r=sheet.getFirstRowNum();r<=sheet.getLastRowNum();r++) {
            Row row=sheet.getRow(r); if(row==null)continue;
            for(Cell cell:row) if(cell.getCellType()==CellType.STRING && cell.getStringCellValue().contains(marker)) { rows.add(r); break; }
        }
        return rows;
    }

    private static void expandItems(Sheet sheet, int templateRowIndex, List<TaxInvoiceItem> items, String gstType) {
        Row template = sheet.getRow(templateRowIndex); if(template==null)return;
        int count = Math.max(1, items.size());
        if (count > 1 && sheet.getLastRowNum() >= templateRowIndex+1)
            sheet.shiftRows(templateRowIndex+1, sheet.getLastRowNum(), count-1, true, false);
        // Clone every destination while the source still contains ERP tokens. Filling the first
        // row before cloning would copy the first item's resolved values into every later row.
        for (int i=1;i<count;i++) copyRow(sheet, template, templateRowIndex+i);
        for (int i=0;i<count;i++) {
            Row row = sheet.getRow(templateRowIndex+i);
            TaxInvoiceItem item = items.isEmpty() ? null : items.get(i);
            Map<String,String> values = itemValues(item, i+1, gstType);
            fillRow(row, values);
        }
    }

    private static void expandCharges(Sheet sheet, int templateRowIndex, List<ChargeData> charges, String gstType) {
        Row template = sheet.getRow(templateRowIndex); if(template==null)return;
        if (charges.isEmpty()) { clearRepeatingRow(template, "{{charge."); return; }
        int count=charges.size();
        if(count>1&&sheet.getLastRowNum()>=templateRowIndex+1)
            sheet.shiftRows(templateRowIndex+1,sheet.getLastRowNum(),count-1,true,false);
        for(int i=1;i<count;i++)copyRow(sheet,template,templateRowIndex+i);
        for(int i=0;i<count;i++){
            Row row=sheet.getRow(templateRowIndex+i);
            fillRow(row, chargeValues(charges.get(i), i+1, gstType));
        }
    }

    private static Row copyRow(Sheet sheet, Row source, int targetIndex) {
        Row target = sheet.createRow(targetIndex);
        target.setHeight(source.getHeight());
        for (int c=source.getFirstCellNum();c<source.getLastCellNum();c++) {
            if(c<0)continue; Cell sc=source.getCell(c); if(sc==null)continue;
            Cell tc=target.createCell(c, sc.getCellType()); tc.setCellStyle(sc.getCellStyle());
            switch(sc.getCellType()){
                case STRING -> tc.setCellValue(sc.getStringCellValue());
                case NUMERIC -> tc.setCellValue(sc.getNumericCellValue());
                case BOOLEAN -> tc.setCellValue(sc.getBooleanCellValue());
                case FORMULA -> tc.setCellFormula(shiftFormulaForCopiedRow(sc.getCellFormula(), targetIndex - source.getRowNum()));
                case ERROR -> tc.setCellErrorValue(sc.getErrorCellValue());
                default -> { }
            }
        }
        return target;
    }

    private static void fillRow(Row row, Map<String,String> values) {
        for(Cell cell:row){
            if(cell.getCellType()!=CellType.STRING)continue;
            String text=cell.getStringCellValue(); if(text==null)continue;
            String replaced=replaceTokens(text,values);
            Matcher whole=WHOLE_TOKEN.matcher(text);
            if(whole.matches()&&"item.descriptionWithRemarks".equals(whole.group(1)))ensureWrap(cell);
            // If the whole cell is a numeric repeating token, store a real number for formulas/sorting.
            if(text.trim().matches("\\{\\{\\s*(item|charge)\\.[A-Za-z0-9_.-]+\\s*}}") && replaced.matches("-?\\d+(\\.\\d+)?")){
                try{cell.setCellValue(Double.parseDouble(replaced));continue;}catch(Exception ignored){}
            }
            cell.setCellValue(replaced);
        }
    }

    private static void clearRepeatingRow(Row row, String marker) {
        for(Cell cell:row)if(cell.getCellType()==CellType.STRING&&cell.getStringCellValue().contains(marker))cell.setBlank();
    }

    private static Map<String,String> itemValues(TaxInvoiceItem item,int serial,String gstType) {
        Map<String,String> v=new HashMap<>();
        List<String> keys=List.of("serial","code","description","descriptionWithRemarks","remarks","category","brand","material","size","hsn","quantity","unit","rate","discountPercent","discountAmount","taxable","gstPercent","gstAmount","cgstPercent","cgstAmount","sgstPercent","sgstAmount","igstPercent","igstAmount","total","location","purchasePrice","sellingPrice","availableStock","openingStock","minimumStock","reservedStock","masterGstPercent","masterDiscountPercent");
        if(item==null){ for(String k:keys)v.put("item."+k,""); return v; }
        DocumentCalculationEngine.LineResult result=DocumentCalculationEngine.line(item.getQuantity(),item.getRate(),item.getDiscountPercent(),item.getGstPercent());
        TaxSplit split=taxSplit(item.getGstPercent(),result.taxAmount(),gstType);
        v.put("item.serial",Integer.toString(serial));
        v.put("item.code",safe(item.getItemCode()));
        // Description cells always include the optional line remark. A blank remark adds nothing.
        v.put("item.description",descriptionWithRemarks(item.getDescription(),item.getRemarks()));
        v.put("item.descriptionWithRemarks",descriptionWithRemarks(item.getDescription(),item.getRemarks()));
        v.put("item.remarks",safe(item.getRemarks()));
        v.put("item.category",safe(item.getCategory()));
        v.put("item.brand",safe(item.getBrand()));
        v.put("item.material",safe(item.getMaterial()));
        v.put("item.size",safe(item.getSize()));
        v.put("item.hsn",safe(item.getHsn()));
        v.put("item.quantity",number(item.getQuantity()));v.put("item.unit",safe(item.getUnit()));v.put("item.rate",money(item.getRate()));
        v.put("item.discountPercent",number(item.getDiscountPercent()));v.put("item.discountAmount",money(result.discountAmount()));v.put("item.taxable",money(result.taxableAmount()));
        v.put("item.gstPercent",number(item.getGstPercent()));v.put("item.gstAmount",money(result.taxAmount()));
        boolean interstate=DocumentCalculationEngine.taxMode(gstType)==DocumentCalculationEngine.TaxMode.IGST;
        v.put("item.cgstPercent",interstate?"":number(split.cgstPercent()));v.put("item.cgstAmount",interstate?"":money(split.cgstAmount()));
        v.put("item.sgstPercent",interstate?"":number(split.sgstPercent()));v.put("item.sgstAmount",interstate?"":money(split.sgstAmount()));
        v.put("item.igstPercent",interstate?number(split.igstPercent()):"");v.put("item.igstAmount",interstate?money(split.igstAmount()):"");
        v.put("item.total",money(result.totalAmount()));
        v.put("item.location",safe(item.getLocation()));
        v.put("item.purchasePrice",money(item.getPurchasePrice()));
        v.put("item.sellingPrice",money(item.getSellingPrice()));
        v.put("item.availableStock",number(item.getAvailableStock()));
        v.put("item.openingStock",number(item.getOpeningStock()));
        v.put("item.minimumStock",number(item.getMinimumStock()));
        v.put("item.reservedStock",number(item.getReservedStock()));
        v.put("item.masterGstPercent",number(item.getMasterGstPercent()));
        v.put("item.masterDiscountPercent",number(item.getMasterDiscountPercent()));
        return v;
    }

    private static Map<String,String> chargeValues(ChargeData c,int serial,String gstType) {
        Map<String,String> v=new HashMap<>();
        DocumentCalculationEngine.ChargeResult result=DocumentCalculationEngine.charge(c.amount(),c.taxable(),c.gstPercent());
        TaxSplit split=taxSplit(c.gstPercent(),result.taxAmount(),gstType);
        v.put("charge.serial",Integer.toString(serial));
        String chargeType=safe(c.type()).trim();
        v.put("charge.type",chargeType.isBlank()?"Charges":chargeType);v.put("charge.amount",money(result.amount()));v.put("charge.taxable",c.taxable()?"Yes":"No");
        v.put("charge.taxableAmount",money(result.taxableAmount()));
        v.put("charge.gstPercent",number(c.taxable()?c.gstPercent():0));v.put("charge.taxAmount",money(result.taxAmount()));
        boolean interstate=DocumentCalculationEngine.taxMode(gstType)==DocumentCalculationEngine.TaxMode.IGST;
        v.put("charge.cgstPercent",interstate?"":number(split.cgstPercent()));v.put("charge.cgstAmount",interstate?"":money(split.cgstAmount()));
        v.put("charge.sgstPercent",interstate?"":number(split.sgstPercent()));v.put("charge.sgstAmount",interstate?"":money(split.sgstAmount()));
        v.put("charge.igstPercent",interstate?number(split.igstPercent()):"");v.put("charge.igstAmount",interstate?money(split.igstAmount()):"");
        v.put("charge.total",money(result.totalAmount()));
        return v;
    }

    private record TaxSplit(double cgstPercent,double cgstAmount,double sgstPercent,double sgstAmount,double igstPercent,double igstAmount) { }

    private static TaxSplit taxSplit(double gstPercent,double taxAmount,String gstType) {
        double rate=DocumentCalculationEngine.percent(gstPercent);
        double tax=DocumentCalculationEngine.money(taxAmount);
        if(DocumentCalculationEngine.taxMode(gstType)==DocumentCalculationEngine.TaxMode.IGST)
            return new TaxSplit(0,0,0,0,rate,tax);
        double cgstRate=rate/2d;
        double sgstRate=rate-cgstRate;
        double cgst=DocumentCalculationEngine.money(tax/2d);
        double sgst=DocumentCalculationEngine.money(tax-cgst);
        return new TaxSplit(cgstRate,cgst,sgstRate,sgst,0,0);
    }

    /** Shift relative A1-style row references when a repeating template row is copied. String literals remain unchanged. */
    static String shiftFormulaForCopiedRow(String formula,int rowDelta) {
        if(formula==null||formula.isBlank()||rowDelta==0)return formula;
        StringBuilder out=new StringBuilder(formula.length()+8);
        int segmentStart=0;boolean quoted=false;
        for(int i=0;i<formula.length();i++){
            if(formula.charAt(i)!='\"')continue;
            if(quoted&&i+1<formula.length()&&formula.charAt(i+1)=='\"'){i++;continue;}
            if(!quoted){
                out.append(shiftFormulaSegment(formula.substring(segmentStart,i),rowDelta));
                quoted=true;segmentStart=i;
            }else{
                out.append(formula,segmentStart,i+1);
                quoted=false;segmentStart=i+1;
            }
        }
        if(quoted)out.append(formula.substring(segmentStart));
        else out.append(shiftFormulaSegment(formula.substring(segmentStart),rowDelta));
        return out.toString();
    }

    private static String shiftFormulaSegment(String segment,int rowDelta){
        Matcher matcher=A1_REFERENCE.matcher(segment);
        StringBuffer out=new StringBuffer();
        while(matcher.find()){
            String full=matcher.group();
            if(!matcher.group(3).isEmpty()){matcher.appendReplacement(out,Matcher.quoteReplacement(full));continue;}
            int row;
            try{row=Integer.parseInt(matcher.group(4));}catch(Exception e){matcher.appendReplacement(out,Matcher.quoteReplacement(full));continue;}
            int shifted=Math.max(1,row+rowDelta);
            String replacement=full.substring(0,full.length()-matcher.group(4).length())+shifted;
            matcher.appendReplacement(out,Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static List<String> unknownTokens(Workbook workbook, DocumentType type, TemplateData data) {
        Set<String> allowed = new LinkedHashSet<>();
        if (type != null) TemplateFieldCatalog.excelFieldsFor(type).forEach(field -> allowed.add(field.key()));
        else for (DocumentType candidate : DocumentType.values()) TemplateFieldCatalog.excelFieldsFor(candidate).forEach(field -> allowed.add(field.key()));
        if (data != null) allowed.addAll(data.images().keySet());
        Set<String> unknown = new LinkedHashSet<>();
        if (workbook == null) return List.of();
        for (int s=0;s<workbook.getNumberOfSheets();s++) for (Row row : workbook.getSheetAt(s)) for (Cell cell : row) {
            if (cell.getCellType()!=CellType.STRING) continue;
            Matcher matcher=TOKEN.matcher(cell.getStringCellValue());
            while(matcher.find()) if(!allowed.contains(matcher.group(1))) unknown.add(matcher.group(1)+" @ "+workbook.getSheetName(s)+"!"+cell.getAddress().formatAsString());
        }
        return List.copyOf(unknown);
    }

    /** Backward-compatible broad validation for callers that do not know the document type. */
    public static List<String> unknownTokens(Workbook workbook, TemplateData data) { return unknownTokens(workbook, null, data); }

    private static void validateKnownTokens(Workbook workbook, DocumentType type, TemplateData data) {
        List<String> unknown = unknownTokens(workbook, type, data);
        if (!unknown.isEmpty()) throw new IllegalArgumentException("Unsupported ERP field(s): " + String.join(", ", unknown));
    }

    private static String replaceTokens(String text, Map<String,String> values) {
        Matcher m=TOKEN.matcher(text);StringBuffer out=new StringBuffer();
        while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(values.getOrDefault(m.group(1),"")));
        m.appendTail(out);return out.toString();
    }

    private static void ensureWrap(Cell cell){
        if(cell==null||cell.getCellStyle().getWrapText())return;
        Workbook wb=cell.getSheet().getWorkbook();
        CellStyle style=wb.createCellStyle();style.cloneStyleFrom(cell.getCellStyle());style.setWrapText(true);cell.setCellStyle(style);
    }

    private static void writeReplacedValue(Cell cell,String source,String replaced){
        Matcher whole=WHOLE_TOKEN.matcher(source==null?"":source);
        String numeric=replaced==null?"":replaced.replace(",","").trim();
        if(whole.matches() && isNumericExcelField(whole.group(1)) && numeric.matches("-?\\d+(\\.\\d+)?")){
            try{double value=Double.parseDouble(numeric);cell.setCellValue(value);if(requiresTwoDecimalMoneyFormat(whole.group(1)))ensureTwoDecimalMoneyFormat(cell);return;}catch(Exception ignored){}
        }
        cell.setCellValue(replaced==null?"":replaced);
    }

    private static boolean isNumericExcelField(String key){
        if(key==null)return false;
        if(key.startsWith("item.")||key.startsWith("charge."))return true;
        return key.startsWith("totals.") && !key.equals("totals.amountInWords");
    }

    private static boolean requiresTwoDecimalMoneyFormat(String key){
        return key!=null&&key.startsWith("totals.")&&!key.equals("totals.amountInWords");
    }

    private static void ensureTwoDecimalMoneyFormat(Cell cell){
        Workbook workbook=cell.getSheet().getWorkbook();
        CellStyle style=workbook.createCellStyle();style.cloneStyleFrom(cell.getCellStyle());
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00;-#,##0.00;0.00"));
        cell.setCellStyle(style);
    }

    private static String descriptionWithRemarks(String description,String remarks){
        String d=safe(description).trim(),r=safe(remarks).trim();
        if(d.isBlank())return r;
        if(r.isBlank())return d;
        return d+"\n"+r;
    }

    private static String money(double value){return String.format(Locale.ROOT,"%.2f",value);} private static String number(double value){return Math.rint(value)==value?String.format(Locale.ROOT,"%.0f",value):String.format(Locale.ROOT,"%.2f",value);} private static String safe(String v){return v==null?"":v;}
    private static String rootMessage(Throwable e){Throwable r=e;while(r.getCause()!=null&&r.getCause()!=r)r=r.getCause();return r.getMessage()==null?r.getClass().getSimpleName():r.getMessage();}
}
