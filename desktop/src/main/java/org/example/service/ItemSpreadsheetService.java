package org.example.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.example.api.master.MasterApiClient;
import org.example.model.Item;
import org.example.model.Party;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Excel import/export contract for item masters. The first worksheet uses the columns exported here.
 */
public final class ItemSpreadsheetService {
    private static final String[] HEADERS = {"Item Code", "Description", "Category", "Unit", "HSN", "GST %", "Discount %", "Purchase Price", "Selling Price", "Remarks", "Opening Stock", "Minimum Stock", "Location"};
    private final ItemService itemService = new ItemService();


    public void exportItems(List<Item> items, Path file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(file)) {
            Sheet sheet = workbook.createSheet("Item Master");
            sheet.createFreezePane(0, 1);
            CellStyle header = headerStyle(workbook);
            CellStyle number = numberStyle(workbook, "#,##0.00");
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(header);
            }
            int rowIndex = 1;
            for (Item item : items) {
                Row row = sheet.createRow(rowIndex++);
                write(row, 0, item.getItemCode());
                write(row, 1, item.getDescription());
                write(row, 2, item.getCategory());
                write(row, 3, item.getUnit());
                write(row, 4, item.getHsn());
                writeNumber(row, 5, item.getGst(), number);
                writeNumber(row, 6, item.getDiscountPercent(), number);
                writeNumber(row, 7, item.getPurchasePrice(), number);
                writeNumber(row, 8, item.getSellingPrice(), number);
                write(row, 9, item.getRemarks());
                writeNumber(row, 10, item.getOpeningStock(), number);
                writeNumber(row, 11, item.getMinimumStock(), number);
                write(row, 12, item.getLocation());
            }
            for (int c = 0; c < HEADERS.length; c++) sheet.setColumnWidth(c, c == 1 || c == 9 ? 28 * 256 : 16 * 256);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, HEADERS.length - 1));
            workbook.write(output);
        }
    }


    public ImportResult importItems(Path file) throws IOException {
        List<String> errors = new ArrayList<>();
        List<Item> candidates = new ArrayList<>();
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) return importCsv(file);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) throw new IOException("Choose an .xlsx, .xls or .csv item-master file.");
        try (InputStream input = Files.newInputStream(file); Workbook workbook = name.endsWith(".xlsx") ? new XSSFWorkbook(input) : new HSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            if (sheet.getPhysicalNumberOfRows() == 0) return new ImportResult(0, List.of("The workbook is empty."));
            validateHeaders(sheet.getRow(0), formatter);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || blank(row, formatter)) continue;
                try {
                    Item item = readItem(row, formatter);
                    candidates.add(item);
                } catch (IllegalArgumentException ex) {
                    errors.add("Row " + (rowIndex + 1) + ": " + ex.getMessage());
                } catch (RuntimeException ex) {
                    errors.add("Row " + (rowIndex + 1) + ": could not be saved (" + ex.getMessage() + ")");
                }
            }
        }
        if (!errors.isEmpty()) return new ImportResult(0, errors);
        persistAll(candidates);
        return new ImportResult(candidates.size(), List.of());
    }

    private ImportResult importCsv(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return new ImportResult(0, List.of("The CSV file is empty."));
        String[] header = csv(lines.getFirst());
        for (int i=0;i<HEADERS.length;i++) if (i>=header.length || !normalize(header[i]).equals(normalize(HEADERS[i])))
            throw new IOException("Invalid header at column " + (i+1) + ". Expected '" + HEADERS[i] + "'. Export the Item Master template and use the same columns.");
        List<Item> items=new ArrayList<>(); List<String> errors=new ArrayList<>();
        for(int row=1;row<lines.size();row++){if(lines.get(row).isBlank())continue;String[] v=csv(lines.get(row));try{items.add(readCsvItem(v));}catch(Exception e){errors.add("Row "+(row+1)+": "+e.getMessage());}}
        if(!errors.isEmpty())return new ImportResult(0,errors);persistAll(items);return new ImportResult(items.size(),List.of());
    }

    private void validateHeaders(Row row, DataFormatter f) throws IOException {
        if(row==null)throw new IOException("Header row is missing.");
        for(int i=0;i<HEADERS.length;i++){String actual=text(row,i,f);if(!normalize(actual).equals(normalize(HEADERS[i])))throw new IOException("Invalid header at column "+(i+1)+". Expected '"+HEADERS[i]+"' but found '"+actual+"'. Export the Item Master template and use the same columns.");}
    }

    private String normalize(String value){return value==null?"":value.replace("\uFEFF","").trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
    private String[] csv(String line){return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)",-1);}
    private String cv(String[] v,int i){return i<v.length?v[i].trim().replaceAll("^\"|\"$","").replace("\"\"","\""):"";}
    private double cn(String[]v,int i){String x=cv(v,i).replace(",","");if(x.isBlank())return 0;try{return Double.parseDouble(x);}catch(Exception e){throw new IllegalArgumentException(HEADERS[i]+" must be a number");}}
    private Item readCsvItem(String[]v){Item i=new Item();i.setItemCode(cv(v,0));i.setDescription(cv(v,1));if(i.getItemCode().isBlank())throw new IllegalArgumentException("Item Code is required");if(i.getDescription().isBlank())throw new IllegalArgumentException("Description is required");i.setCategory(cv(v,2));i.setUnit(cv(v,3));i.setHsn(cv(v,4));if(i.getHsn().isBlank())throw new IllegalArgumentException("HSN Code is required");i.setGst(cn(v,5));i.setDiscountPercent(cn(v,6));i.setPurchasePrice(cn(v,7));i.setSellingPrice(cn(v,8));i.setRemarks(cv(v,9));i.setOpeningStock(cn(v,10));i.setMinimumStock(cn(v,11));i.setLocation(cv(v,12));i.setBrand(null);i.setMaterial(null);i.setSize(null);return i;}

    private void persistAll(List<Item> items) throws IOException {
        try { new MasterApiClient().saveItems(items); }
        catch (Exception e) { throw new IOException("No items were imported. Server transaction was rolled back: "+e.getMessage(), e); }
    }

    private Item readItem(Row row, DataFormatter f) {
        String code = text(row, 0, f);
        String description = text(row, 1, f);
        if (code.isBlank()) throw new IllegalArgumentException("Item Code is required");
        if (description.isBlank()) throw new IllegalArgumentException("Description is required");
        Item item = new Item();
        item.setItemCode(code);
        item.setDescription(description);
        item.setCategory(text(row, 2, f));
        item.setBrand(null); item.setMaterial(null); item.setSize(null);
        item.setUnit(text(row, 3, f));
        item.setHsn(text(row, 4, f));
        if(item.getHsn()==null||item.getHsn().isBlank()) throw new IllegalArgumentException("HSN Code is required");
        item.setGst(number(row, 5, f));
        item.setDiscountPercent(number(row, 6, f));
        item.setPurchasePrice(number(row, 7, f));
        item.setSellingPrice(number(row, 8, f));
        item.setRemarks(text(row, 9, f));
        item.setOpeningStock(number(row, 10, f));
        item.setMinimumStock(number(row, 11, f));
        item.setLocation(text(row, 12, f));
        return item;
    }

    private static String text(Row row, int column, DataFormatter formatter) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static double number(Row row, int column, DataFormatter formatter) {
        String value = text(row, column, formatter);
        if (value.isBlank()) return 0;
        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(HEADERS[column] + " must be a number");
        }
    }

    private static boolean blank(Row row, DataFormatter formatter) {
        for (int c = 0; c < HEADERS.length; c++) if (!text(row, c, formatter).isBlank()) return false;
        return true;
    }

    private static void write(Row row, int col, String value) {
        row.createCell(col).setCellValue(value == null ? "" : value);
    }

    private static void writeNumber(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static CellStyle numberStyle(Workbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        return style;
    }
      public record ImportResult(int imported, List<String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    public void exportparties(List<Party> parties, Path path) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Parties");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("party_code");
        header.createCell(1).setCellValue("name");
        header.createCell(2).setCellValue("contact_person");
        header.createCell(3).setCellValue("phone");
        header.createCell(4).setCellValue("email");
        header.createCell(5).setCellValue("gstin");
        header.createCell(6).setCellValue("address");
        header.createCell(7).setCellValue("opening_balance");
        header.createCell(8).setCellValue("is_active");




        int rowIndex = 1;
        for (Party p : parties) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(p.getPartyCode());
            row.createCell(1).setCellValue(p.getName());
            row.createCell(2).setCellValue(p.getContactPerson());
            row.createCell(3).setCellValue(p.getPhone());
            row.createCell(4).setCellValue(p.getEmail());
            row.createCell(5).setCellValue(p.getGstin());
            row.createCell(6).setCellValue(p.getAddress());
            row.createCell(7).setCellValue(p.getOpeningBalance());
            row.createCell(8).setCellValue(p.isActive());





        }

        try (OutputStream out = Files.newOutputStream(path)) {
            wb.write(out);
        }
        wb.close();
    }

}
