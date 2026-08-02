package org.example.service;

import org.apache.poi.ss.usermodel.*;
import org.example.model.Party;
import org.example.model.Item;
import org.example.model.Lookup;
import org.example.model.Sales;
import org.example.model.SalesLine;
import org.example.model.Purchase;
import org.example.model.PurchaseLine;
import org.example.database.DatabaseManager;
import org.example.util.SpreadsheetLayoutDetector;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.function.BiConsumer;

public class ImportService {

    // ---------------- Result wrapper ----------------
    public static class ImportResult {
        public final int processed;   // unique codes attempted
        public final int imported;    // new records created
        public final int updated;     // existing records updated
        public final int skipped;     // duplicates skipped
        public final List<String> errors;

        public ImportResult(int processed, int imported, int updated, int skipped, List<String> errors) {
            this.processed = processed;
            this.imported = imported;
            this.updated = updated;
            this.skipped = skipped;
            this.errors = errors;
        }
    }

    // ---------------- Customers ----------------
    public ImportResult importCustomers(Path file, Map<String,String> mapping, boolean dryRun,
                                        BiConsumer<Integer,Integer> progress) throws Exception {
        return importParties(file, mapping, dryRun, progress, "CUSTOMER");
    }

    // ---------------- Suppliers ----------------
    public ImportResult importSuppliers(Path file, Map<String,String> mapping, boolean dryRun,
                                        BiConsumer<Integer,Integer> progress) throws Exception {
        return importParties(file, mapping, dryRun, progress, "SUPPLIER");
    }

    // ---------------- Items ----------------
    public ImportResult importItems(Path file, Map<String,String> mapping, boolean dryRun,
                                    BiConsumer<Integer,Integer> progress) throws Exception {
        List<Item> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        ItemService service = new ItemService();

        // --- Step 1: Read Excel ---
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            SpreadsheetLayoutDetector.Layout layout = SpreadsheetLayoutDetector.detect(workbook, mapping.values());
            Sheet sheet = workbook.getSheetAt(layout.sheetIndex());
            int total = Math.max(0, sheet.getLastRowNum() - layout.headerRowIndex());
            for (int i = layout.headerRowIndex() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    Item item = new Item();

                    String code = getCellValue(row, mapping.get("item_code"));
                    if (code == null || code.isBlank()) {
                        code = service.nextCode();
                    }
                    item.setItemCode(code.trim());

                    String desc = getCellValue(row, mapping.get("description"));
                    if (desc == null || desc.isBlank()) {
                        throw new IllegalArgumentException("Missing description");
                    }
                    item.setDescription(desc.trim());

                    item.setCategory(getCellValue(row, mapping.get("category")));
                    item.setBrand(getCellValue(row, mapping.get("brand")));
                    item.setMaterial(getCellValue(row, mapping.get("material")));
                    item.setSize(getCellValue(row, mapping.get("size")));
                    item.setUnit(getCellValue(row, mapping.get("unit")));
                    item.setHsn(getCellValue(row, mapping.get("hsn")));
                    item.setGst(parseDouble(getCellValue(row, mapping.get("gst"))));
                    item.setDiscountPercent(parseDouble(getCellValue(row, mapping.get("discount_percent"))));
                    item.setPurchasePrice(parseDouble(getCellValue(row, mapping.get("purchase_price"))));
                    item.setSellingPrice(parseDouble(getCellValue(row, mapping.get("selling_price"))));
                    item.setOpeningStock(parseDouble(getCellValue(row, mapping.get("opening_stock"))));
                    item.setMinimumStock(parseDouble(getCellValue(row, mapping.get("minimum_stock"))));
                    item.setLocation(getCellValue(row, mapping.get("location")));
                    item.setRemarks(getCellValue(row, mapping.get("remarks")));

                    items.add(item);
                } catch (Exception ex) {
                    errors.add("Row " + i + ": " + ex.getMessage());
                }
                progress.accept(i, total);
            }
        }

        // --- Step 2: Process items ---
        Set<String> seenCodes = new HashSet<>();
        int processed = 0, imported = 0, updated = 0, skipped = 0;

        if (dryRun) {
            processed = (int) items.stream().map(Item::getItemCode).distinct().count();
        } else {
            for (Item item : items) {
                if (seenCodes.contains(item.getItemCode())) {
                    errors.add("Duplicate in sheet skipped: " + item.getItemCode());
                    skipped++;
                    continue;
                }
                seenCodes.add(item.getItemCode());
                processed++;

                try {
                    if (service.existsByCode(item.getItemCode())) {
                        service.update(item);
                        updated++;
                    } else {
                        service.save(item);
                        imported++;
                    }
                } catch (Exception e) {
                    errors.add("Duplicate in DB skipped: " + item.getItemCode());
                    skipped++;
                }
            }
        }

        return new ImportResult(processed, imported, updated, skipped, errors);
    }

    /** Imports sales invoices, grouping multiple spreadsheet rows by invoice number. */
    public ImportResult importSales(Path file, Map<String,String> mapping, boolean dryRun,
                                    BiConsumer<Integer,Integer> progress) throws Exception {
        return importDocuments(file, mapping, dryRun, progress, true);
    }

    /** Imports purchase invoices, grouping multiple spreadsheet rows by invoice number. */
    public ImportResult importPurchases(Path file, Map<String,String> mapping, boolean dryRun,
                                        BiConsumer<Integer,Integer> progress) throws Exception {
        return importDocuments(file, mapping, dryRun, progress, false);
    }

    private ImportResult importDocuments(Path file, Map<String,String> mapping, boolean dryRun,
                                         BiConsumer<Integer,Integer> progress, boolean sales) throws Exception {
        record ImportRow(String invoice, LocalDate date, String party, String item, double qty,
                         double rate, double gst, String terms, double paid, String remarks) {}
        List<ImportRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            SpreadsheetLayoutDetector.Layout layout = SpreadsheetLayoutDetector.detect(workbook, mapping.values());
            Sheet sheet = workbook.getSheetAt(layout.sheetIndex());
            int total = Math.max(0, sheet.getLastRowNum() - layout.headerRowIndex());
            for (int i = layout.headerRowIndex() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    String party = required(getCellValue(row, mapping.get("party_code")), "party_code");
                    String item = required(getCellValue(row, mapping.get("item_code")), "item_code");
                    String invoice = getCellValue(row, mapping.get("invoice_no"));
                    if (invoice == null || invoice.isBlank()) invoice = sales ? new SalesService().nextInvoiceNo() : new PurchaseService().nextInvoiceNo();
                    rows.add(new ImportRow(invoice.trim(), parseDate(getCellValue(row, mapping.get("invoice_date"))),
                        party.trim(), item.trim(), parsePositive(getCellValue(row, mapping.get("quantity")), "quantity"),
                        parsePositive(getCellValue(row, mapping.get("rate")), "rate"),
                        parseDouble(getCellValue(row, mapping.get("gst_percent"))),
                        defaultText(getCellValue(row, mapping.get("payment_terms")), "15 Days"),
                        parseDouble(getCellValue(row, mapping.get("paid_amount"))),
                        getCellValue(row, mapping.get("remarks"))));
                } catch (Exception ex) {
                    errors.add("Row " + (i + 1) + ": " + ex.getMessage());
                }
                progress.accept(i, Math.max(1, total));
            }
        }
        Map<String,List<ImportRow>> grouped = new LinkedHashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(row.invoice(), key -> new ArrayList<>()).add(row));
        if (dryRun) return new ImportResult(grouped.size(), 0, 0, errors.size(), errors);
        PartyService partyService = new PartyService();
        ItemService itemService = new ItemService();
        int imported = 0, skipped = 0;
        for (Map.Entry<String,List<ImportRow>> entry : grouped.entrySet()) {
            try {
                ImportRow first = entry.getValue().get(0);
                Party party = partyService.getByType(sales ? "CUSTOMER" : "SUPPLIER").stream()
                    .filter(p -> p.getPartyCode().equalsIgnoreCase(first.party())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Party not found: " + first.party()));
                Map<String,Item> itemByCode = new HashMap<>();
                itemService.getAll().forEach(item -> itemByCode.put(item.getItemCode().toUpperCase(Locale.ROOT), item));
                if (sales) {
                    SalesService service = new SalesService();
                    if (service.getAll().stream().anyMatch(doc -> entry.getKey().equalsIgnoreCase(doc.getInvoiceNo()))) { skipped++; continue; }
                    Sales document = new Sales();
                    document.setInvoiceNo(entry.getKey()); document.setInvoiceDate(first.date()); document.setCustomer(party);
                    document.setDueDate(first.date().plusDays(termDays(first.terms()))); document.setPaidAmount(first.paid());
                    document.setPaymentStatus(first.paid() > 0 ? "PARTIAL" : "PENDING"); document.setRemarks(first.remarks());
                    List<SalesLine> lines = new ArrayList<>();
                    for (ImportRow importedRow : entry.getValue()) {
                        Item item = requireItem(itemByCode, importedRow.item());
                        SalesLine line = new SalesLine(); line.setItemCode(item.getItemCode()); line.setItemDescription(item.getDescription());
                        line.setQuantity(importedRow.qty()); line.setRate(importedRow.rate()); line.setGstPercent(importedRow.gst()); line.recalculate(); lines.add(line);
                    }
                    document.setLines(lines); applySalesTotals(document); service.save(document);
                } else {
                    PurchaseService service = new PurchaseService();
                    if (service.getAll().stream().anyMatch(doc -> entry.getKey().equalsIgnoreCase(doc.getInvoiceNo()))) { skipped++; continue; }
                    Purchase document = new Purchase();
                    document.setInvoiceNo(entry.getKey()); document.setInvoiceDate(first.date()); document.setSupplier(party);
                    document.setDueDate(first.date().plusDays(termDays(first.terms()))); document.setPaymentTerms(first.terms());
                    document.setPaidAmount(first.paid()); document.setPaymentStatus(first.paid() > 0 ? "PARTIAL" : "PENDING");
                    document.setRemarks(first.remarks()); document.setCurrency("INR - Indian Rupee"); document.setWarehouse("Main Warehouse");
                    List<PurchaseLine> lines = new ArrayList<>();
                    for (ImportRow importedRow : entry.getValue()) {
                        Item item = requireItem(itemByCode, importedRow.item());
                        PurchaseLine line = new PurchaseLine(); line.setItemCode(item.getItemCode()); line.setItemDescription(item.getDescription());
                        line.setQuantity(importedRow.qty()); line.setRate(importedRow.rate()); line.setGstPercent(importedRow.gst()); line.calculateAmounts(); lines.add(line);
                    }
                    document.setLines(lines); applyPurchaseTotals(document); service.save(document);
                }
                imported++;
            } catch (Exception ex) {
                skipped++; errors.add(entry.getKey() + ": " + ex.getMessage());
            }
        }
        return new ImportResult(grouped.size(), imported, 0, skipped, errors);
    }

    /** Imports both master categories and their reusable values. */
    public ImportResult importMasterValues(Path file, Map<String,String> mapping, boolean dryRun,
                                           BiConsumer<Integer,Integer> progress) throws Exception {
        List<String> errors = new ArrayList<>();
        int processed = 0, imported = 0, updated = 0, skipped = 0;
        LookupService service = new LookupService();
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            SpreadsheetLayoutDetector.Layout layout = SpreadsheetLayoutDetector.detect(workbook, mapping.values());
            Sheet sheet = workbook.getSheetAt(layout.sheetIndex());
            int total = Math.max(0, sheet.getLastRowNum() - layout.headerRowIndex());
            for (int i = layout.headerRowIndex() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i); if (row == null) continue;
                try {
                    String categoryCode = required(getCellValue(row, mapping.get("category_code")), "category_code").toUpperCase(Locale.ROOT);
                    String categoryName = defaultText(getCellValue(row, mapping.get("category_name")), categoryCode);
                    String value = required(getCellValue(row, mapping.get("value")), "value");
                    String code = defaultText(getCellValue(row, mapping.get("value_code")), service.generateNextCode(categoryCode));
                    processed++;
                    if (!dryRun) {
                        try (Connection connection = DatabaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO master_category(category_code,category_name,description,is_active) VALUES(?,?,?,1) ON CONFLICT(category_code) DO UPDATE SET category_name=excluded.category_name,description=excluded.description")) {
                            statement.setString(1, categoryCode); statement.setString(2, categoryName);
                            statement.setString(3, getCellValue(row, mapping.get("category_description"))); statement.executeUpdate();
                        }
                        Lookup lookup = service.getByType(categoryCode).stream().filter(existing -> existing.getLookupCode().equalsIgnoreCase(code)).findFirst().orElse(null);
                        boolean exists = lookup != null;
                        if (!exists) lookup = new Lookup();
                        lookup.setLookupType(categoryCode); lookup.setLookupCode(code); lookup.setLookupValue(value);
                        lookup.setDescription(getCellValue(row, mapping.get("value_description")));
                        lookup.setDisplayOrder((int) parseDouble(getCellValue(row, mapping.get("display_order"))));
                        lookup.setActive(!"false".equalsIgnoreCase(getCellValue(row, mapping.get("is_active"))));
                        if (exists) { service.update(lookup); updated++; } else { service.save(lookup); imported++; }
                    }
                } catch (Exception ex) { skipped++; errors.add("Row " + (i + 1) + ": " + ex.getMessage()); }
                progress.accept(i, Math.max(1, total));
            }
        }
        return new ImportResult(processed, imported, updated, skipped, errors);
    }


    // ---------------- Shared Party Import ----------------
    private ImportResult importParties(Path file, Map<String,String> mapping, boolean dryRun,
                                       BiConsumer<Integer,Integer> progress, String partyType) throws Exception {
        List<Party> parties = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        PartyService service = new PartyService();

        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            SpreadsheetLayoutDetector.Layout layout = SpreadsheetLayoutDetector.detect(workbook, mapping.values());
            Sheet sheet = workbook.getSheetAt(layout.sheetIndex());
            int total = Math.max(0, sheet.getLastRowNum() - layout.headerRowIndex());
            for (int i = layout.headerRowIndex() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    Party p = new Party();
                    p.setPartyType(partyType);

                    String code = getCellValue(row, mapping.get("party_code"));
                    if (code == null || code.isBlank()) {
                        code = service.nextCode(partyType);
                    }
                    p.setPartyCode(code.trim());

                    String name = getCellValue(row, mapping.get("name"));
                    if (name == null || name.isBlank()) {
                        throw new IllegalArgumentException("Missing name");
                    }
                    p.setName(name.trim());

                    p.setContactPerson(getCellValue(row, mapping.get("contact_person")));
                    p.setPhone(getCellValue(row, mapping.get("phone")));
                    p.setEmail(getCellValue(row, mapping.get("email")));
                    p.setGstin(getCellValue(row, mapping.get("gstin")));
                    p.setAddress(getCellValue(row, mapping.get("address")));
                    p.setOpeningBalance(parseDouble(getCellValue(row, mapping.get("opening_balance"))));
                    p.setActive(Boolean.parseBoolean(getCellValue(row, mapping.get("is_active"))));

                    parties.add(p);
                } catch (Exception ex) {
                    errors.add("Row " + i + ": " + ex.getMessage());
                }
                progress.accept(i, total);
            }
        }

        Set<String> seenCodes = new HashSet<>();
        int processed = 0;
        int imported = 0;
        int updated = 0;
        int skipped = 0;

        if (dryRun) {
            processed = (int) parties.stream().map(Party::getPartyCode).distinct().count();
        } else {
            for (Party p : parties) {
                if (seenCodes.contains(p.getPartyCode())) {
                    errors.add("Duplicate in sheet skipped: " + p.getPartyCode());
                    skipped++;
                    continue;
                }
                seenCodes.add(p.getPartyCode());
                processed++;

                try {
                    if (service.existsByCode(p.getPartyCode())) {
                        service.update(p);
                        updated++;
                    } else {
                        service.save(p);
                        imported++;
                    }
                } catch (Exception e) {
                    errors.add("Duplicate in DB skipped: " + p.getPartyCode());
                    skipped++;
                }
            }
        }

        return new ImportResult(processed, imported, updated, skipped, errors);

    }

    // ---------------- Helpers ----------------
    private String getCellValue(Row row, String header) {
        if (header == null) return null;
        Workbook workbook = row.getSheet().getWorkbook();
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        int colIndex = -1;
        for (int headerIndex = Math.max(0, row.getSheet().getFirstRowNum());
             headerIndex < row.getRowNum() && headerIndex < 75; headerIndex++) {
            colIndex = SpreadsheetLayoutDetector.findHeaderIndex(row.getSheet().getRow(headerIndex), header, evaluator);
            if (colIndex >= 0) break;
        }
        if (colIndex >= 0 && row.getCell(colIndex) != null) {
            return SpreadsheetLayoutDetector.format(row.getCell(colIndex), evaluator);
        }
        return null;
    }

    private double parseDouble(String val) {
        try { return Double.parseDouble(val); } catch (Exception e) { return 0.0; }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + field);
        return value;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private double parsePositive(String value, String field) {
        double parsed = parseDouble(value);
        if (parsed <= 0) throw new IllegalArgumentException(field + " must be greater than zero");
        return parsed;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return LocalDate.now();
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"), DateTimeFormatter.ofPattern("d/M/yyyy"))) {
            try { return LocalDate.parse(value.trim(), formatter); } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Invalid date: " + value + " (use yyyy-MM-dd or dd/MM/yyyy)");
    }

    private int termDays(String term) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(defaultText(term, "0"));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private Item requireItem(Map<String,Item> itemByCode, String code) {
        Item item = itemByCode.get(code.toUpperCase(Locale.ROOT));
        if (item == null) throw new IllegalArgumentException("Item not found: " + code);
        return item;
    }

    private void applySalesTotals(Sales document) {
        double subtotal = document.getLines().stream().mapToDouble(SalesLine::getNetAmount).sum();
        double tax = document.getLines().stream().mapToDouble(SalesLine::getGstAmount).sum();
        document.setSubtotal(subtotal); document.setGstAmount(tax); document.setTotalAmount(subtotal + tax);
    }

    private void applyPurchaseTotals(Purchase document) {
        double subtotal = document.getLines().stream().mapToDouble(PurchaseLine::getNetAmount).sum();
        double tax = document.getLines().stream().mapToDouble(PurchaseLine::getGstAmount).sum();
        document.setSubtotal(subtotal); document.setGstAmount(tax); document.setTotalAmount(subtotal + tax);
    }
}
