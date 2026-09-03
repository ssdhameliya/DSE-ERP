package org.example.service;

import org.example.util.BusinessClock;
import org.example.config.ConfigManager;

import org.apache.poi.ss.usermodel.*;
import org.example.model.Party;
import org.example.model.Item;
import org.example.model.Lookup;
import org.example.model.Sales;
import org.example.model.SalesLine;
import org.example.model.SalesCharge;
import org.example.model.Purchase;
import org.example.model.PurchaseLine;
import org.example.model.PurchaseCharge;
import org.example.api.master.MasterApiClient;
import org.example.api.support.SupportApiClient;
import org.example.util.SpreadsheetLayoutDetector;
import org.example.shared.ReferenceFormatRules;
import org.example.shared.DocumentCalculationEngine;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;

public class ImportService {
    public enum ImportMode { UPDATE_NON_BLANK, CREATE_ONLY, UPSERT, SKIP_EXISTING }

    private record DocumentImportRow(int sourceRow, String invoice, LocalDate date, String party, String item, double qty,
                                     double rate, double gst, String taxType, String terms, double paid, String remarks,
                                     String charge1Type, String charge1Amount, String charge1Taxable, String charge1GstPercent,
                                     String charge2Type, String charge2Amount, String charge2Taxable, String charge2GstPercent,
                                     String additionalCharges, String attachmentFile, String attachmentFiles) {}

    private record SalesImportExtras(List<SalesCharge> charges, Path attachmentSource) {}
    private record PurchaseImportExtras(List<PurchaseCharge> charges, List<Path> attachmentSources) {}

    // ---------------- Result wrapper ----------------
    public static final class ImportRowResult {
        public final String sourceRows;
        public final String reference;
        public final String status;
        public final String action;
        public final String message;
        public final String taxType;
        public final double gstPercent;

        public ImportRowResult(String sourceRows, String reference, String status, String action,
                               String message, String taxType, double gstPercent) {
            this.sourceRows = sourceRows == null ? "" : sourceRows;
            this.reference = reference == null ? "" : reference;
            this.status = status == null ? "" : status;
            this.action = action == null ? "" : action;
            this.message = message == null ? "" : message;
            this.taxType = taxType == null ? "" : taxType;
            this.gstPercent = gstPercent;
        }
    }

    public static class ImportResult {
        public final int processed;   // unique codes attempted
        public final int imported;    // new records created
        public final int updated;     // existing records updated
        public final int skipped;     // duplicates skipped
        public final List<String> errors;
        public final List<ImportRowResult> details;

        public ImportResult(int processed, int imported, int updated, int skipped, List<String> errors) {
            this(processed, imported, updated, skipped, errors, List.of());
        }

        public ImportResult(int processed, int imported, int updated, int skipped,
                            List<String> errors, List<ImportRowResult> details) {
            this.processed = processed;
            this.imported = imported;
            this.updated = updated;
            this.skipped = skipped;
            this.errors = errors == null ? List.of() : List.copyOf(errors);
            this.details = details == null ? List.of() : List.copyOf(details);
        }

        public int failedCount() {
            if (!details.isEmpty()) {
                return (int) details.stream().filter(row -> "FAILED".equalsIgnoreCase(row.status)).count();
            }
            return errors.size();
        }

        public int passedCount() {
            if (!details.isEmpty()) {
                return (int) details.stream().filter(row -> "PASSED".equalsIgnoreCase(row.status)).count();
            }
            return imported + updated;
        }
    }

    // ---------------- Customers ----------------
    public ImportResult importCustomers(Path file, Map<String,String> mapping, boolean dryRun, ImportMode mode,
                                        BiConsumer<Integer,Integer> progress) throws Exception {
        return importParties(file, mapping, dryRun, mode, progress, "CUSTOMER");
    }

    // ---------------- Suppliers ----------------
    public ImportResult importSuppliers(Path file, Map<String,String> mapping, boolean dryRun, ImportMode mode,
                                        BiConsumer<Integer,Integer> progress) throws Exception {
        return importParties(file, mapping, dryRun, mode, progress, "SUPPLIER");
    }

    // ---------------- Items ----------------
    public ImportResult importItems(Path file, Map<String,String> mapping, boolean dryRun, ImportMode mode,
                                    BiConsumer<Integer,Integer> progress) throws Exception {
        List<Item> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<ImportRowResult> details = new ArrayList<>();
        Map<String,Set<String>> suppliedItemFields = new HashMap<>();
        ItemService service = new ItemService();
        Map<String,String> referenceFormats = new MasterApiClient().referenceFormats();

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

                    String code = required(getCellValue(row, mapping.get("item_code")), "item_code").trim();
                    requireReference(referenceFormats, "REF_ITEM", code, null, "Item Code");
                    item.setItemCode(code);

                    String desc = getCellValue(row, mapping.get("description"));
                    if (desc == null || desc.isBlank()) {
                        throw new IllegalArgumentException("Missing description");
                    }
                    item.setDescription(desc.trim());

                    item.setCategory(getCellValue(row, mapping.get("category")));
                    item.setUnit(required(getCellValue(row, mapping.get("unit")), "unit"));
                    item.setHsn(required(getCellValue(row, mapping.get("hsn")), "hsn"));
                    Set<String> supplied = new HashSet<>();
                    String gstText=getCellValue(row,mapping.get("gst")); if(!blank(gstText))supplied.add("gst"); item.setGst(parseDouble(gstText));
                    String discountText=getCellValue(row,mapping.get("discount_percent")); if(!blank(discountText))supplied.add("discount_percent"); item.setDiscountPercent(parseDouble(discountText));
                    String purchaseText=getCellValue(row,mapping.get("purchase_price")); if(!blank(purchaseText))supplied.add("purchase_price"); item.setPurchasePrice(parseDouble(purchaseText));
                    String sellingText=getCellValue(row,mapping.get("selling_price")); if(!blank(sellingText))supplied.add("selling_price"); item.setSellingPrice(parseDouble(sellingText));
                    String openingText=getCellValue(row,mapping.get("opening_stock")); if(!blank(openingText))supplied.add("opening_stock"); item.setOpeningStock(parseDouble(openingText));
                    String minimumText=getCellValue(row,mapping.get("minimum_stock")); if(!blank(minimumText))supplied.add("minimum_stock"); item.setMinimumStock(parseDouble(minimumText));
                    item.setLocation(getCellValue(row, mapping.get("location")));
                    item.setRemarks(required(getCellValue(row, mapping.get("remarks")), "remarks"));
                    suppliedItemFields.put(code.toUpperCase(Locale.ROOT), supplied);
                    validateItemForImport(item);

                    items.add(item);
                } catch (Exception ex) {
                    errors.add("Row " + (i + 1) + ": " + ex.getMessage());
                    details.add(new ImportRowResult(String.valueOf(i + 1), "", "FAILED", "NONE", ex.getMessage(), "", 0));
                }
                progress.accept(i, total);
            }
        }

        // --- Step 2: Process items ---
        Set<String> seenCodes = new HashSet<>();
        int processed = 0, imported = 0, updated = 0, skipped = 0;
        Map<String,Item> existingItems = new HashMap<>();
        service.getAll().forEach(existing -> existingItems.put(existing.getItemCode().toUpperCase(Locale.ROOT), existing));

        if (dryRun) {
            Set<String> seen = new HashSet<>();
            for (Item item : items) {
                processed++;
                String key = item.getItemCode().toUpperCase(Locale.ROOT);
                if (!seen.add(key)) {
                    String message = "Duplicate Item Code in workbook";
                    errors.add(item.getItemCode() + ": " + message);
                    details.add(new ImportRowResult("", item.getItemCode(), "FAILED", "NONE", message, "", 0));
                    continue;
                }
                Item existing = existingItems.get(key);
                if (existing != null && (mode == ImportMode.CREATE_ONLY || mode == ImportMode.SKIP_EXISTING)) {
                    skipped++;
                    details.add(new ImportRowResult("", item.getItemCode(), "PASSED", "SKIPPED",
                            "Existing item will be preserved by the selected duplicate policy", "", 0));
                } else if (existing != null) {
                    details.add(new ImportRowResult("", item.getItemCode(), "PASSED", "WOULD UPDATE",
                            "Existing item matches this code and will be updated by the selected policy", "", 0));
                } else {
                    details.add(new ImportRowResult("", item.getItemCode(), "PASSED", "WOULD CREATE",
                            "New item will be created", "", 0));
                }
            }
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
                    Item existing = existingItems.get(item.getItemCode().toUpperCase(Locale.ROOT));
                    if (existing != null) {
                        if (mode == ImportMode.CREATE_ONLY || mode == ImportMode.SKIP_EXISTING) { skipped++; continue; }
                        applyItemUpdateIdentity(item, existing);
                        if (mode == ImportMode.UPDATE_NON_BLANK) {
                            mergeItem(item, existing);
                            Set<String> supplied=suppliedItemFields.getOrDefault(item.getItemCode().toUpperCase(Locale.ROOT),Set.of());
                            if(!supplied.contains("gst")) item.setGst(existing.getGst());
                            if(!supplied.contains("discount_percent")) item.setDiscountPercent(existing.getDiscountPercent());
                            if(!supplied.contains("purchase_price")) item.setPurchasePrice(existing.getPurchasePrice());
                            if(!supplied.contains("selling_price")) item.setSellingPrice(existing.getSellingPrice());
                            if(!supplied.contains("minimum_stock")) item.setMinimumStock(existing.getMinimumStock());
                        }
                        service.update(item); updated++;
                    } else {
                        service.save(item);
                        existingItems.put(item.getItemCode().toUpperCase(Locale.ROOT), item);
                        imported++;
                    }
                } catch (Exception e) {
                    String message = rootMessage(e);
                    errors.add("Item " + item.getItemCode() + ": " + message);
                    details.add(new ImportRowResult("", item.getItemCode(), "FAILED", "NONE", message, "", 0));
                    skipped++;
                }
            }
        }

        return new ImportResult(processed, imported, updated, skipped, errors, details);
    }

    /** Imports sales invoices, grouping multiple spreadsheet rows by invoice number. */
    public ImportResult importSales(Path file, Map<String,String> mapping, boolean dryRun, ImportMode mode,
                                    BiConsumer<Integer,Integer> progress) throws Exception {
        return importDocuments(file, mapping, dryRun, mode, progress, true);
    }

    /** Imports purchase invoices, grouping multiple spreadsheet rows by invoice number. */
    public ImportResult importPurchases(Path file, Map<String,String> mapping, boolean dryRun, ImportMode mode,
                                        BiConsumer<Integer,Integer> progress) throws Exception {
        return importDocuments(file, mapping, dryRun, mode, progress, false);
    }

    private ImportResult importDocuments(Path file, Map<String,String> mapping, boolean dryRun, ImportMode mode,
                                         BiConsumer<Integer,Integer> progress, boolean sales) throws Exception {
        List<DocumentImportRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<ImportRowResult> details = new ArrayList<>();
        MasterApiClient masterApi = new MasterApiClient();
        Map<String,String> referenceFormats = masterApi.referenceFormats();
        Set<String> validPartyCodes = new HashSet<>();
        masterApi.parties(sales ? "CUSTOMER" : "SUPPLIER").forEach(p -> validPartyCodes.add(p.getPartyCode().toUpperCase(Locale.ROOT)));
        Set<String> validItemCodes = new HashSet<>();
        Map<String,Double> availableStockByItem = new HashMap<>();
        masterApi.items().forEach(item -> {
            String code = item.getItemCode().toUpperCase(Locale.ROOT);
            validItemCodes.add(code);
            availableStockByItem.put(code, item.getAvailableStock());
        });

        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            SpreadsheetLayoutDetector.Layout layout = SpreadsheetLayoutDetector.detect(workbook, mapping.values());
            Sheet sheet = workbook.getSheetAt(layout.sheetIndex());
            int total = Math.max(0, sheet.getLastRowNum() - layout.headerRowIndex());
            for (int i = layout.headerRowIndex() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                int sourceRow = i + 1;
                try {
                    String party = required(getCellValue(row, mapping.get("party_code")), "party_code").trim();
                    String item = required(getCellValue(row, mapping.get("item_code")), "item_code").trim();
                    String invoice = required(getCellValue(row, mapping.get("invoice_no")), "invoice_no").trim();
                    LocalDate invoiceDate = getRequiredDateValue(row, mapping.get("invoice_date"), "invoice_date");
                    if (sales) requireReference(referenceFormats, "REF_SALES", invoice, invoiceDate, "Sales Invoice No.");
                    requireReference(referenceFormats, sales ? "REF_CUSTOMER" : "REF_SUPPLIER", party, null, sales ? "Customer Code" : "Supplier Code");
                    requireReference(referenceFormats, "REF_ITEM", item, null, "Item Code");
                    if (!validPartyCodes.contains(party.toUpperCase(Locale.ROOT))) throw new IllegalArgumentException((sales ? "Customer" : "Supplier") + " not found in master: " + party);
                    if (!validItemCodes.contains(item.toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Item not found in master: " + item);
                    String taxType = normalizeTaxType(getCellValue(row, mapping.get("gst_type")), party, sales);
                    rows.add(new DocumentImportRow(sourceRow, invoice,
                        invoiceDate,
                        party, item,
                        parsePositive(getCellValue(row, mapping.get("quantity")), "quantity"),
                        parsePositive(getCellValue(row, mapping.get("rate")), "rate"),
                        parseDouble(getCellValue(row, mapping.get("gst_percent"))),
                        taxType,
                        defaultText(getCellValue(row, mapping.get("payment_terms")), "15 Days"),
                        parseDouble(getCellValue(row, mapping.get("paid_amount"))),
                        getCellValue(row, mapping.get("remarks")),
                        getCellValue(row, mapping.get("charge_1_type")),
                        getCellValue(row, mapping.get("charge_1_amount")),
                        getCellValue(row, mapping.get("charge_1_taxable")),
                        getCellValue(row, mapping.get("charge_1_gst_percent")),
                        getCellValue(row, mapping.get("charge_2_type")),
                        getCellValue(row, mapping.get("charge_2_amount")),
                        getCellValue(row, mapping.get("charge_2_taxable")),
                        getCellValue(row, mapping.get("charge_2_gst_percent")),
                        getCellValue(row, mapping.get("additional_charges")),
                        getCellValue(row, mapping.get("attachment_file")),
                        getCellValue(row, mapping.get("attachment_files"))));
                } catch (Exception ex) {
                    String error = "Row " + sourceRow + ": " + ex.getMessage();
                    errors.add(error);
                    details.add(new ImportRowResult(String.valueOf(sourceRow), "", "FAILED", "NONE",
                        ex.getMessage(), "", 0));
                }
                progress.accept(i, Math.max(1, total));
            }
        }

        Map<String,List<DocumentImportRow>> grouped = new LinkedHashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(row.invoice(), key -> new ArrayList<>()).add(row));
        Set<String> existingDocumentNumbers = new HashSet<>();
        if (dryRun) {
            if (sales) new SalesService().getAll().forEach(doc -> addDocumentIdentity(existingDocumentNumbers, doc.getInvoiceNo()));
            else new PurchaseService().getAll().forEach(doc -> {
                addDocumentIdentity(existingDocumentNumbers, doc.getReferenceNo());
                addDocumentIdentity(existingDocumentNumbers, doc.getInvoiceNo());
            });
        }

        if (dryRun) {
            for (Map.Entry<String,List<DocumentImportRow>> entry : grouped.entrySet()) {
                DocumentImportRow first = entry.getValue().get(0);
                try {
                    validateDocumentHeaderConsistency(entry.getValue());
                    if (existingDocumentNumbers.contains(entry.getKey().toUpperCase(Locale.ROOT)))
                        throw new IllegalArgumentException("Existing posted " + (sales ? "sales" : "purchase") + " invoice is protected and cannot be imported again");
                    if (sales) {
                        Map<String,Double> invoiceQty = new HashMap<>();
                        for (DocumentImportRow importedRow : entry.getValue()) {
                            String code = importedRow.item().toUpperCase(Locale.ROOT);
                            invoiceQty.merge(code, importedRow.qty(), Double::sum);
                        }
                        for (Map.Entry<String,Double> required : invoiceQty.entrySet()) {
                            double available = availableStockByItem.getOrDefault(required.getKey(), 0d);
                            if (required.getValue() > available + 0.000001) {
                                throw new IllegalArgumentException("Insufficient stock for item " + required.getKey()
                                        + " (required " + required.getValue() + ", available " + available + ")");
                            }
                        }
                        invoiceQty.forEach((code, qty) -> availableStockByItem.compute(code, (k, value) -> Math.max(0d, (value == null ? 0d : value) - qty)));
                    }
                    int chargeCount; int attachmentCount;
                    if (sales) {
                        SalesImportExtras extras=salesImportExtras(file,entry.getValue());chargeCount=extras.charges().size();attachmentCount=extras.attachmentSource()==null?0:1;
                    } else {
                        PurchaseImportExtras extras=purchaseImportExtras(file,entry.getValue());chargeCount=extras.charges().size();attachmentCount=extras.attachmentSources().size();
                    }
                    String extrasText=String.format(Locale.ROOT," | %d charge%s | %d attachment%s",chargeCount,chargeCount==1?"":"s",attachmentCount,attachmentCount==1?"":"s");
                    details.add(new ImportRowResult(sourceRows(entry.getValue()), entry.getKey(), "PASSED", "VALIDATED",
                        taxDescription(first.taxType(), first.gst()) + extrasText, first.taxType(), first.gst()));
                } catch (Exception ex) {
                    String error = entry.getKey() + ": " + ex.getMessage();
                    errors.add(error);
                    details.add(new ImportRowResult(sourceRows(entry.getValue()), entry.getKey(), "FAILED", "NONE",
                        ex.getMessage(), first.taxType(), first.gst()));
                }
            }
            return new ImportResult(grouped.size(), 0, 0, errors.size(), errors, details);
        }

        PartyService partyService = new PartyService();
        ItemService itemService = new ItemService();
        Map<String,Party> partyByCode = new HashMap<>();
        partyService.getByType(sales ? "CUSTOMER" : "SUPPLIER").forEach(p -> partyByCode.put(p.getPartyCode().toUpperCase(Locale.ROOT), p));
        Map<String,Item> itemByCode = new HashMap<>();
        itemService.getAll().forEach(item -> itemByCode.put(item.getItemCode().toUpperCase(Locale.ROOT), item));
        Set<String> existingDocuments = new HashSet<>();
        SalesService salesService = sales ? new SalesService() : null;
        PurchaseService purchaseService = sales ? null : new PurchaseService();
        if (sales) salesService.getAll().forEach(doc -> addDocumentIdentity(existingDocuments, doc.getInvoiceNo()));
        else purchaseService.getAll().forEach(doc -> {
            addDocumentIdentity(existingDocuments, doc.getReferenceNo());
            addDocumentIdentity(existingDocuments, doc.getInvoiceNo());
        });
        int imported = 0, skipped = 0;

        for (Map.Entry<String,List<DocumentImportRow>> entry : grouped.entrySet()) {
            DocumentImportRow first = entry.getValue().get(0);
            String rowRange = sourceRows(entry.getValue());
            String postSaveWarning = "";
            try {
                validateDocumentHeaderConsistency(entry.getValue());
                Party party = partyByCode.get(first.party().toUpperCase(Locale.ROOT));
                if (party == null) throw new IllegalArgumentException("Party not found: " + first.party());

                String taxType = normalizeTaxType(first.taxType(), party.getPartyCode(), sales);
                double representativeRate = first.gst();

                if (sales) {
                    SalesService service = salesService;
                    if (existingDocuments.contains(entry.getKey().toUpperCase(Locale.ROOT))) {
                        skipped++;
                        String message = "Existing posted sales invoice was protected and skipped";
                        errors.add(entry.getKey() + ": " + message);
                        details.add(new ImportRowResult(rowRange, entry.getKey(), "SKIPPED", "NONE",
                            message, taxType, representativeRate));
                        continue;
                    }

                    SalesImportExtras extras = salesImportExtras(file, entry.getValue());
                    Sales document = new Sales();
                    document.setInvoiceNo(entry.getKey());
                    document.setInvoiceDate(first.date());
                    document.setCustomer(party);
                    document.setDueDate(first.date().plusDays(termDays(first.terms())));
                    document.setPaidAmount(0d);
                    document.setPaymentStatus("PENDING");
                    document.setSource("IMPORT");
                    document.setRemarks(first.remarks());
                    document.setGstType(taxType);
                    document.setCharges(extras.charges());

                    List<SalesLine> lines = new ArrayList<>();
                    for (DocumentImportRow importedRow : entry.getValue()) {
                        if (!taxType.equalsIgnoreCase(importedRow.taxType())) {
                            throw new IllegalArgumentException("Mixed GST/IGST treatment inside one invoice is not allowed");
                        }
                        Item item = requireItem(itemByCode, importedRow.item());
                        SalesLine line = new SalesLine();
                        line.setItemCode(item.getItemCode());
                        line.setItemDescription(item.getDescription());
                        line.setQuantity(importedRow.qty());
                        line.setRate(importedRow.rate());
                        line.setGstPercent(importedRow.gst());
                        line.recalculate();
                        lines.add(line);
                    }
                    document.setLines(lines);
                    applySalesTotals(document);
                    service.save(document);
                    existingDocuments.add(entry.getKey().toUpperCase(Locale.ROOT));
                    if (extras.attachmentSource() != null) {
                        try {
                            Sales persisted = service.getByInvoice(document.getInvoiceNo());
                            if (persisted == null || persisted.getId() <= 0)
                                throw new IllegalStateException("saved sale could not be reloaded for attachment upload");
                            String reference = new SupportApiClient().uploadDocumentAttachment("SALE", persisted.getId(), extras.attachmentSource());
                            document.setAttachmentPath(reference);
                        } catch (Exception attachmentFailure) {
                            postSaveWarning = "Record imported successfully; attachment could not be uploaded: "
                                + safeImportMessage(attachmentFailure);
                        }
                    }
                } else {
                    PurchaseService service = purchaseService;
                    if (existingDocuments.contains(entry.getKey().toUpperCase(Locale.ROOT))) {
                        skipped++;
                        String message = "Existing posted purchase invoice was protected and skipped";
                        errors.add(entry.getKey() + ": " + message);
                        details.add(new ImportRowResult(rowRange, entry.getKey(), "SKIPPED", "NONE",
                            message, taxType, representativeRate));
                        continue;
                    }

                    PurchaseImportExtras extras=purchaseImportExtras(file,entry.getValue());
                    Purchase document = new Purchase();
                    document.setInvoiceNo(null);
                    document.setReferenceNo(entry.getKey());
                    document.setInvoiceDate(first.date());
                    document.setSupplier(party);
                    document.setDueDate(first.date().plusDays(termDays(first.terms())));
                    document.setDeliveryDate(document.getDueDate());
                    document.setPaymentTerms(first.terms());
                    document.setPaidAmount(0d);
                    document.setPaymentStatus("PENDING");
                    document.setRemarks(first.remarks());
                    document.setNotes(first.remarks());
                    document.setCurrency("INR - Indian Rupee");
                    document.setWarehouse("Main Warehouse");
                    document.setGstTreatment(taxType);
                    document.setGstType(taxType);
                    document.setBillingAddress(party.getAddress());
                    document.setDeliveryAddress(party.getAddress());
                    document.setBillingGstin(party.getGstin());
                    document.setDeliveryGstin(party.getGstin());
                    document.setSameAsBilling(true);
                    document.setCharges(extras.charges());

                    List<PurchaseLine> lines = new ArrayList<>();
                    for (DocumentImportRow importedRow : entry.getValue()) {
                        if (!taxType.equalsIgnoreCase(importedRow.taxType())) {
                            throw new IllegalArgumentException("Mixed GST/IGST treatment inside one invoice is not allowed");
                        }
                        Item item = requireItem(itemByCode, importedRow.item());
                        PurchaseLine line = new PurchaseLine();
                        line.setItemCode(item.getItemCode());
                        line.setItemDescription(item.getDescription());
                        line.setQuantity(importedRow.qty());
                        line.setRate(importedRow.rate());
                        line.setGstPercent(importedRow.gst());
                        line.calculateAmounts();
                        lines.add(line);
                    }
                    document.setLines(lines);
                    applyPurchaseTotals(document);
                    service.save(document);
                    existingDocuments.add(entry.getKey().toUpperCase(Locale.ROOT));
                    if(!extras.attachmentSources().isEmpty()){
                        try {
                            Purchase persisted=service.getByInvoice(document.getInvoiceNo());
                            if(persisted==null||persisted.getId()<=0)throw new IllegalStateException("saved purchase could not be reloaded for attachment upload");
                            SupportApiClient api=new SupportApiClient();
                            for(Path source:extras.attachmentSources())api.addDocumentAttachment("PURCHASE",persisted.getId(),source);
                        } catch (Exception attachmentFailure) {
                            postSaveWarning = "Record imported successfully; one or more attachments could not be uploaded: "
                                + safeImportMessage(attachmentFailure);
                        }
                    }
                }

                imported++;
                String action = postSaveWarning.isBlank() ? "CREATED" : "CREATED WITH WARNING";
                String detailMessage = postSaveWarning.isBlank()
                    ? taxDescription(taxType, representativeRate)
                    : taxDescription(taxType, representativeRate) + " | " + postSaveWarning;
                details.add(new ImportRowResult(rowRange, entry.getKey(), "PASSED", action,
                    detailMessage, taxType, representativeRate));
            } catch (Exception ex) {
                skipped++;
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                errors.add(entry.getKey() + ": " + message);
                details.add(new ImportRowResult(rowRange, entry.getKey(), "FAILED", "NONE",
                    message, first.taxType(), first.gst()));
            }
        }

        return new ImportResult(grouped.size(), imported, 0, skipped, errors, details);
    }

    private static String safeImportMessage(Throwable failure) {
        if (failure == null) return "unexpected attachment error";
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return message.replaceAll("[\r\n]+", " ").trim();
    }

    /** Imports both master categories and their reusable values. */
    public ImportResult importMasterValues(Path file, Map<String,String> mapping, boolean dryRun, ImportMode mode,
                                           BiConsumer<Integer,Integer> progress) throws Exception {
        List<String> errors = new ArrayList<>();
        List<ImportRowResult> details = new ArrayList<>();
        int processed = 0, imported = 0, updated = 0, skipped = 0;
        LookupService service = new LookupService();
        MasterApiClient masterApi = new MasterApiClient();
        Map<String,MasterApiClient.CategoryDto> categories = new HashMap<>();
        for (MasterApiClient.CategoryDto category : masterApi.categories()) {
            if (category != null && category.categoryCode() != null)
                categories.put(category.categoryCode().trim().toUpperCase(Locale.ROOT), category);
        }
        Map<String,List<Lookup>> lookupCache = new HashMap<>();
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            SpreadsheetLayoutDetector.Layout layout = SpreadsheetLayoutDetector.detect(workbook, mapping.values());
            Sheet sheet = workbook.getSheetAt(layout.sheetIndex());
            int total = Math.max(0, sheet.getLastRowNum() - layout.headerRowIndex());
            for (int i = layout.headerRowIndex() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i); if (row == null) continue;
                try {
                    String categoryCode = required(getCellValue(row, mapping.get("category_code")), "category_code").trim().toUpperCase(Locale.ROOT);
                    String categoryName = required(getCellValue(row, mapping.get("category_name")), "category_name").trim();
                    String categoryDescription = getCellValue(row, mapping.get("category_description"));
                    String value = required(getCellValue(row, mapping.get("value")), "value").trim();
                    String code = required(getCellValue(row, mapping.get("value_code")), "value_code").trim();
                    String valueDescription = getCellValue(row, mapping.get("value_description"));
                    String displayOrderText = getCellValue(row, mapping.get("display_order"));
                    String activeText = getCellValue(row, mapping.get("is_active"));
                    Integer displayOrder = blank(displayOrderText) ? null : (int) parseDouble(displayOrderText);
                    Boolean active = blank(activeText) ? null : !Set.of("false","0","no","inactive","disabled").contains(activeText.trim().toLowerCase(Locale.ROOT));
                    processed++;
                    MasterApiClient.CategoryDto existingCategory = categories.get(categoryCode);
                    if (dryRun) {
                        String canonicalLookupType = existingCategory != null && !blank(existingCategory.categoryName())
                                ? existingCategory.categoryName().trim() : categoryName;
                        MasterApiClient.LookupCodeResolution resolution = masterApi.resolveLookupCode(canonicalLookupType, code);
                        String effectiveCode = resolution == null || blank(resolution.canonicalCode())
                                ? code.toUpperCase(Locale.ROOT) : resolution.canonicalCode().trim().toUpperCase(Locale.ROOT);
                        Lookup lookup = lookupCache.computeIfAbsent(canonicalLookupType,
                                        ignored -> new ArrayList<>(service.getByType(canonicalLookupType))).stream()
                                .filter(existing -> existing.getLookupCode().equalsIgnoreCase(effectiveCode)).findFirst().orElse(null);
                        if (lookup != null && (mode == ImportMode.CREATE_ONLY || mode == ImportMode.SKIP_EXISTING)) {
                            skipped++;
                            details.add(new ImportRowResult(String.valueOf(i + 1), categoryCode + "/" + effectiveCode,
                                    "PASSED", "SKIPPED", "Existing master value will be preserved by the selected duplicate policy", "", 0));
                        } else if (lookup != null) {
                            details.add(new ImportRowResult(String.valueOf(i + 1), categoryCode + "/" + effectiveCode,
                                    "PASSED", "WOULD UPDATE", "Existing master value will be updated by the selected policy", "", 0));
                        } else {
                            String message = existingCategory == null
                                    ? "New master category/value will be created"
                                    : "New master value will be created";
                            details.add(new ImportRowResult(String.valueOf(i + 1), categoryCode + "/" + effectiveCode,
                                    "PASSED", "WOULD CREATE", message, "", 0));
                        }
                    } else {
                        MasterApiClient.CategoryDto category = existingCategory;
                        boolean categoryWriteAllowed = existingCategory == null || (mode != ImportMode.CREATE_ONLY && mode != ImportMode.SKIP_EXISTING);
                        if (categoryWriteAllowed) {
                            String effectiveDescription = categoryDescription;
                            if (existingCategory != null && mode == ImportMode.UPDATE_NON_BLANK && blank(effectiveDescription))
                                effectiveDescription = existingCategory.description();
                            category = masterApi.upsertCategory(categoryCode, categoryName, effectiveDescription);
                            if (category != null) categories.put(categoryCode, category);
                        }
                        String canonicalLookupType = category != null && !blank(category.categoryName())
                                ? category.categoryName().trim()
                                : (existingCategory != null && !blank(existingCategory.categoryName()) ? existingCategory.categoryName().trim() : categoryName);
                        MasterApiClient.LookupCodeResolution resolution = masterApi.resolveLookupCode(canonicalLookupType, code);
                        String effectiveCode = resolution == null || blank(resolution.canonicalCode())
                                ? code.toUpperCase(Locale.ROOT) : resolution.canonicalCode().trim().toUpperCase(Locale.ROOT);
                        Lookup lookup = service.getByType(canonicalLookupType).stream()
                                .filter(existing -> existing.getLookupCode().equalsIgnoreCase(effectiveCode)).findFirst().orElse(null);
                        boolean exists = lookup != null;
                        if (exists && (mode == ImportMode.CREATE_ONLY || mode == ImportMode.SKIP_EXISTING)) {
                            skipped++;
                            details.add(new ImportRowResult(String.valueOf(i + 1), categoryCode + "/" + effectiveCode, "SKIPPED", "NONE", "Existing master value preserved", "", 0));
                            progress.accept(i, Math.max(1, total));
                            continue;
                        }
                        if (!exists) lookup = new Lookup();
                        lookup.setLookupType(canonicalLookupType);
                        boolean aliasMatched = resolution != null && resolution.aliasMatched();
                        // Unknown GENxxx identifiers are retired. Leaving the code blank lets the server
                        // allocate the correct category-specific reference (MATxxx, UNTxxx, ...).
                        lookup.setLookupCode(!exists && !aliasMatched && code.matches("(?i)^GEN\\d+$") ? "" : effectiveCode);
                        lookup.setLookupValue(value);
                        if (exists && mode == ImportMode.UPDATE_NON_BLANK) {
                            if (!blank(valueDescription)) lookup.setDescription(valueDescription);
                            if (displayOrder != null) lookup.setDisplayOrder(displayOrder);
                            if (active != null) lookup.setActive(active);
                        } else {
                            lookup.setDescription(valueDescription);
                            lookup.setDisplayOrder(displayOrder == null ? 0 : displayOrder);
                            lookup.setActive(active == null || active);
                        }
                        if (exists) { service.update(lookup); updated++; }
                        else { service.save(lookup); imported++; }
                    }
                } catch (Exception ex) {
                    skipped++;
                    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    errors.add("Row " + (i + 1) + ": " + message);
                    details.add(new ImportRowResult(String.valueOf(i + 1), "", "FAILED", "NONE", message, "", 0));
                }
                progress.accept(i, Math.max(1, total));
            }
        }
        return new ImportResult(processed, imported, updated, skipped, errors, details);
    }


    // ---------------- Shared Party Import ----------------
    private ImportResult importParties(Path file, Map<String,String> mapping, boolean dryRun, ImportMode mode,
                                       BiConsumer<Integer,Integer> progress, String partyType) throws Exception {
        List<Party> parties = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<ImportRowResult> details = new ArrayList<>();
        Map<String,Set<String>> suppliedPartyFields = new HashMap<>();
        PartyService service = new PartyService();
        Map<String,String> referenceFormats = new MasterApiClient().referenceFormats();

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

                    String code = required(getCellValue(row, mapping.get("party_code")), "party_code").trim();
                    requireReference(referenceFormats, "CUSTOMER".equals(partyType) ? "REF_CUSTOMER" : "REF_SUPPLIER", code, null, "CUSTOMER".equals(partyType) ? "Customer Code" : "Supplier Code");
                    p.setPartyCode(code);

                    String name = getCellValue(row, mapping.get("name"));
                    if (name == null || name.isBlank()) {
                        throw new IllegalArgumentException("Missing name");
                    }
                    p.setName(name.trim());

                    p.setContactPerson(getCellValue(row, mapping.get("contact_person")));
                    p.setPhone(getCellValue(row, mapping.get("phone")));
                    String email = getCellValue(row, mapping.get("email"));
                    if ("SUPPLIER".equals(partyType)) email = required(email, "email");
                    if (email != null && !email.isBlank() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("Invalid email");
                    p.setEmail(email);
                    p.setGstin(getCellValue(row, mapping.get("gstin")));
                    p.setAddress(getCellValue(row, mapping.get("address")));
                    Set<String> supplied = new HashSet<>();
                    String openingBalanceText=getCellValue(row,mapping.get("opening_balance")); if(!blank(openingBalanceText))supplied.add("opening_balance");
                    p.setOpeningBalance(parseDouble(openingBalanceText));
                    String activeValue = getCellValue(row, mapping.get("is_active")); if(!blank(activeValue))supplied.add("is_active");
                    p.setActive(activeValue == null || activeValue.isBlank() || !Set.of("false","0","no","inactive","disabled").contains(activeValue.trim().toLowerCase(Locale.ROOT)));
                    suppliedPartyFields.put(code.toUpperCase(Locale.ROOT), supplied);

                    parties.add(p);
                } catch (Exception ex) {
                    errors.add("Row " + (i + 1) + ": " + ex.getMessage());
                    details.add(new ImportRowResult(String.valueOf(i + 1), "", "FAILED", "NONE", ex.getMessage(), "", 0));
                }
                progress.accept(i, total);
            }
        }

        Set<String> seenCodes = new HashSet<>();
        int processed = 0;
        int imported = 0;
        int updated = 0;
        int skipped = 0;
        Map<String,Party> existingParties = new HashMap<>();
        service.getByType(partyType).forEach(existing -> existingParties.put(existing.getPartyCode().toUpperCase(Locale.ROOT), existing));

        if (dryRun) {
            Set<String> seen = new HashSet<>();
            for (Party party : parties) {
                processed++;
                String key = party.getPartyCode().toUpperCase(Locale.ROOT);
                if (!seen.add(key)) {
                    String message = "Duplicate code in workbook";
                    errors.add(party.getPartyCode() + ": " + message);
                    details.add(new ImportRowResult("", party.getPartyCode(), "FAILED", "NONE", message, "", 0));
                    continue;
                }
                Party existing = existingParties.get(key);
                if (existing != null && (mode == ImportMode.CREATE_ONLY || mode == ImportMode.SKIP_EXISTING)) {
                    skipped++;
                    details.add(new ImportRowResult("", party.getPartyCode(), "PASSED", "SKIPPED",
                            "Existing " + partyType.toLowerCase(Locale.ROOT) + " will be preserved by the selected duplicate policy", "", 0));
                } else if (existing != null) {
                    details.add(new ImportRowResult("", party.getPartyCode(), "PASSED", "WOULD UPDATE",
                            "Existing " + partyType.toLowerCase(Locale.ROOT) + " matches this code and will be updated", "", 0));
                } else {
                    details.add(new ImportRowResult("", party.getPartyCode(), "PASSED", "WOULD CREATE",
                            "New " + partyType.toLowerCase(Locale.ROOT) + " will be created", "", 0));
                }
            }
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
                    Party existing = existingParties.get(p.getPartyCode().toUpperCase(Locale.ROOT));
                    if (existing != null) {
                        if (mode == ImportMode.CREATE_ONLY || mode == ImportMode.SKIP_EXISTING) { skipped++; continue; }
                        applyPartyUpdateIdentity(p, existing);
                        if (mode == ImportMode.UPDATE_NON_BLANK) {
                            mergeParty(p, existing);
                            Set<String> supplied=suppliedPartyFields.getOrDefault(p.getPartyCode().toUpperCase(Locale.ROOT),Set.of());
                            if(!supplied.contains("opening_balance")) p.setOpeningBalance(existing.getOpeningBalance());
                            if(!supplied.contains("is_active")) p.setActive(existing.isActive());
                        }
                        service.update(p); updated++;
                    } else {
                        service.save(p);
                        existingParties.put(p.getPartyCode().toUpperCase(Locale.ROOT), p);
                        imported++;
                    }
                } catch (Exception e) {
                    String message = rootMessage(e);
                    errors.add("Party " + p.getPartyCode() + ": " + message);
                    details.add(new ImportRowResult("", p.getPartyCode(), "FAILED", "NONE", message, "", 0));
                    skipped++;
                }
            }
        }

        return new ImportResult(processed, imported, updated, skipped, errors, details);

    }

    private static void addDocumentIdentity(Set<String> identities, String value) {
        if (identities == null || value == null || value.isBlank()) return;
        identities.add(value.trim().toUpperCase(Locale.ROOT));
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root != null && root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root == null ? null : root.getMessage();
        return message == null || message.isBlank() ? (root == null ? "Import save failed" : root.getClass().getSimpleName()) : message.trim();
    }

    private static void applyPartyUpdateIdentity(Party incoming, Party existing) {
        if (existing == null) return;
        incoming.setId(existing.getId());
        incoming.setRowVersion(existing.getRowVersion());
    }

    private static void mergeParty(Party incoming, Party existing) {
        if (existing == null) return;
        applyPartyUpdateIdentity(incoming, existing);
        if (blank(incoming.getName())) incoming.setName(existing.getName());
        if (blank(incoming.getContactPerson())) incoming.setContactPerson(existing.getContactPerson());
        if (blank(incoming.getPhone())) incoming.setPhone(existing.getPhone());
        if (blank(incoming.getEmail())) incoming.setEmail(existing.getEmail());
        if (blank(incoming.getGstin())) incoming.setGstin(existing.getGstin());
        if (blank(incoming.getAddress())) incoming.setAddress(existing.getAddress());
    }

    private static void applyItemUpdateIdentity(Item incoming, Item existing) {
        if (existing == null) return;
        incoming.setId(existing.getId());
        incoming.setRowVersion(existing.getRowVersion());
        // Opening Stock is a creation baseline. Existing inventory changes must use Stock Adjustment.
        incoming.setOpeningStock(existing.getOpeningStock());
        incoming.setReservedStock(existing.getReservedStock());
    }

    private static void mergeItem(Item incoming, Item existing) {
        if (existing == null) return;
        applyItemUpdateIdentity(incoming, existing);
        if (blank(incoming.getDescription())) incoming.setDescription(existing.getDescription());
        if (blank(incoming.getCategory())) incoming.setCategory(existing.getCategory());
        if (blank(incoming.getBrand())) incoming.setBrand(existing.getBrand());
        if (blank(incoming.getMaterial())) incoming.setMaterial(existing.getMaterial());
        if (blank(incoming.getSize())) incoming.setSize(existing.getSize());
        if (blank(incoming.getUnit())) incoming.setUnit(existing.getUnit());
        if (blank(incoming.getHsn())) incoming.setHsn(existing.getHsn());
        if (blank(incoming.getLocation())) incoming.setLocation(existing.getLocation());
        if (blank(incoming.getRemarks())) incoming.setRemarks(existing.getRemarks());
    }


    private static void validateItemForImport(Item item) {
        if (item == null) throw new IllegalArgumentException("Item row is empty");
        if (!Double.isFinite(item.getGst()) || item.getGst() < 0 || item.getGst() > 100)
            throw new IllegalArgumentException("GST percent must be between 0 and 100");
        if (!Double.isFinite(item.getDiscountPercent()) || item.getDiscountPercent() < 0 || item.getDiscountPercent() > 100)
            throw new IllegalArgumentException("Discount percent must be between 0 and 100");
        if (!Double.isFinite(item.getPurchasePrice()) || item.getPurchasePrice() < 0)
            throw new IllegalArgumentException("Purchase price must be a finite non-negative number");
        if (!Double.isFinite(item.getSellingPrice()) || item.getSellingPrice() < 0)
            throw new IllegalArgumentException("Selling price must be a finite non-negative number");
        if (!Double.isFinite(item.getOpeningStock()) || item.getOpeningStock() < 0)
            throw new IllegalArgumentException("Opening stock must be a finite non-negative number");
        if (!Double.isFinite(item.getMinimumStock()) || item.getMinimumStock() < 0)
            throw new IllegalArgumentException("Minimum stock must be a finite non-negative number");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private void requireReference(Map<String,String> formats, String key, String value, LocalDate documentDate, String label) {
        String format = formats == null ? null : formats.get(key);
        if (format == null || format.isBlank()) throw new IllegalStateException(label + " format is not configured in REFERENCE FORMAT (" + key + ")");
        if (!ReferenceFormatRules.matches(format, value, documentDate))
            throw new IllegalArgumentException(label + " '" + value + "' does not match " + key + " format " + format);
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
        if (val == null || val.isBlank()) return 0.0;
        String normalized = val.trim().replace(",", "");
        try {
            double parsed = Double.parseDouble(normalized);
            if (!Double.isFinite(parsed)) throw new NumberFormatException("not finite");
            return parsed;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid numeric value: '" + val + "'");
        }
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
        LocalDate parsed = BusinessClock.parseDate(value);
        if (parsed == null) throw new IllegalArgumentException("Missing required date");
        return parsed;
    }

    private LocalDate getRequiredDateValue(Row row, String header, String field) {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("Missing " + field + " mapping");
        Workbook workbook = row.getSheet().getWorkbook();
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        int colIndex = -1;
        for (int headerIndex = Math.max(0, row.getSheet().getFirstRowNum());
             headerIndex < row.getRowNum() && headerIndex < 75; headerIndex++) {
            colIndex = SpreadsheetLayoutDetector.findHeaderIndex(row.getSheet().getRow(headerIndex), header, evaluator);
            if (colIndex >= 0) break;
        }
        if (colIndex < 0) throw new IllegalArgumentException("Missing " + field + " column");
        Cell cell = row.getCell(colIndex);
        LocalDate excelDate = SpreadsheetLayoutDetector.dateValue(cell, evaluator);
        if (excelDate != null) return excelDate;
        String text = SpreadsheetLayoutDetector.format(cell, evaluator);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Missing " + field);
        return parseDate(text);
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

    private String normalizeTaxType(String value, String partyReference, boolean sales) {
        String text = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (text.contains("IGST") || text.contains("INTER")) return "IGST";
        if (text.equals("GST") || text.contains("INTRA") || text.contains("CGST") || text.contains("SGST")) return "GST";

        // When the template leaves gst_type blank, infer it from GSTIN state codes where possible.
        String companyGstin = ConfigManager.get("company.gstin", "").trim();
        try {
            PartyService partyService = new PartyService();
            String type = sales ? "CUSTOMER" : "SUPPLIER";
            Party party = partyService.getByType(type).stream()
                .filter(candidate -> candidate.getPartyCode().equalsIgnoreCase(partyReference))
                .findFirst().orElse(null);
            String partyGstin = party == null || party.getGstin() == null ? "" : party.getGstin().trim();
            if (companyGstin.length() >= 2 && partyGstin.length() >= 2
                    && companyGstin.substring(0, 2).matches("\\d{2}")
                    && partyGstin.substring(0, 2).matches("\\d{2}")) {
                return companyGstin.substring(0, 2).equals(partyGstin.substring(0, 2)) ? "GST" : "IGST";
            }
        } catch (Exception ignored) { }
        return "GST";
    }

    private SalesImportExtras salesImportExtras(Path workbookFile, List<DocumentImportRow> rows) {
        SalesCharge first = salesCharge(rows, 1);
        SalesCharge second = salesCharge(rows, 2);
        List<SalesCharge> charges = new ArrayList<>();
        if (first != null) charges.add(first);
        if (second != null) charges.add(second);

        String attachment = consistentText(rows, DocumentImportRow::attachmentFile, "attachment_file", false);
        Path source = null;
        if (attachment != null && !attachment.isBlank()) {
            try { source = Path.of(attachment.trim()); }
            catch (Exception invalidPath) { throw new IllegalArgumentException("attachment_file is not a valid path: " + attachment); }
            if (!source.isAbsolute()) {
                Path parent = workbookFile.toAbsolutePath().normalize().getParent();
                source = (parent == null ? Path.of("") : parent).resolve(source).normalize();
            }
            if (!Files.isRegularFile(source)) throw new IllegalArgumentException("attachment_file was not found: " + attachment);
        }
        return new SalesImportExtras(List.copyOf(charges), source);
    }

    private PurchaseImportExtras purchaseImportExtras(Path workbookFile,List<DocumentImportRow> rows){
        List<PurchaseCharge> charges=new ArrayList<>();
        PurchaseCharge first=purchaseCharge(rows,1),second=purchaseCharge(rows,2);
        if(first!=null)charges.add(first);if(second!=null)charges.add(second);
        String flexible=consistentText(rows,DocumentImportRow::additionalCharges,"additional_charges",false);
        if(flexible!=null&&!flexible.isBlank())charges.addAll(parseAdditionalPurchaseCharges(flexible));
        java.util.Set<String> names=new java.util.HashSet<>();
        for(PurchaseCharge charge:charges){String key=charge.getChargeType().trim().toUpperCase(Locale.ROOT);if(!names.add(key))throw new IllegalArgumentException("Duplicate purchase charge type: "+charge.getChargeType());}
        List<Path> attachments=new ArrayList<>();
        String legacy=consistentText(rows,DocumentImportRow::attachmentFile,"attachment_file",false);
        Path legacyPath=resolveAttachment(workbookFile,legacy);if(legacyPath!=null)attachments.add(legacyPath);
        String many=consistentText(rows,DocumentImportRow::attachmentFiles,"attachment_files",false);
        if(many!=null&&!many.isBlank())for(String value:many.split(";")){Path path=resolveAttachment(workbookFile,value);if(path!=null&&!attachments.contains(path))attachments.add(path);}
        return new PurchaseImportExtras(List.copyOf(charges),List.copyOf(attachments));
    }

    private List<PurchaseCharge> parseAdditionalPurchaseCharges(String text){
        List<PurchaseCharge> out=new ArrayList<>();
        for(String raw:text.split(";")){
            String entry=raw==null?"":raw.trim();if(entry.isBlank())continue;
            String[] parts=entry.split("\\|",-1);
            if(parts.length<2||parts.length>4)throw new IllegalArgumentException("additional_charges entry must be Type|Amount|Taxable|GSTPercent: "+entry);
            String type=parts[0].trim();if(type.isBlank())throw new IllegalArgumentException("additional_charges charge type is required");
            double amount;try{amount=Double.parseDouble(parts[1].trim().replace(",",""));}catch(Exception e){throw new IllegalArgumentException("additional_charges amount must be a number for "+type);}
            if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("additional_charges amount must be greater than zero for "+type);
            boolean taxable=parts.length>=3&&!parts[2].trim().isBlank()?parseFlexibleBoolean(parts[2],"additional_charges taxable for "+type):false;
            double gst=0;if(parts.length>=4&&!parts[3].trim().isBlank()){try{gst=Double.parseDouble(parts[3].trim());}catch(Exception e){throw new IllegalArgumentException("additional_charges GST percent must be numeric for "+type);}}
            if(gst<0||gst>100)throw new IllegalArgumentException("additional_charges GST percent must be between 0 and 100 for "+type);
            if(!taxable&&gst>0.0001)throw new IllegalArgumentException("additional_charges GST percent requires taxable=true for "+type);
            out.add(new PurchaseCharge(type,amount,taxable,taxable?gst:0));
        }
        return out;
    }

    private boolean parseFlexibleBoolean(String value,String field){
        String clean=value==null?"":value.trim().toLowerCase(Locale.ROOT);
        return switch(clean){case "true","yes","y","1","taxable"->true;case "false","no","n","0","non-taxable","nontaxable"->false;default->throw new IllegalArgumentException(field+" must be true/false, yes/no, or 1/0");};
    }

    private PurchaseCharge purchaseCharge(List<DocumentImportRow> rows,int index){
        java.util.function.Function<DocumentImportRow,String> typeGetter=index==1?DocumentImportRow::charge1Type:DocumentImportRow::charge2Type;
        java.util.function.Function<DocumentImportRow,String> amountGetter=index==1?DocumentImportRow::charge1Amount:DocumentImportRow::charge2Amount;
        java.util.function.Function<DocumentImportRow,String> taxableGetter=index==1?DocumentImportRow::charge1Taxable:DocumentImportRow::charge2Taxable;
        java.util.function.Function<DocumentImportRow,String> gstGetter=index==1?DocumentImportRow::charge1GstPercent:DocumentImportRow::charge2GstPercent;
        String prefix="charge_"+index,type=consistentText(rows,typeGetter,prefix+"_type",true);Double amount=consistentNumber(rows,amountGetter,prefix+"_amount",0,Double.MAX_VALUE),gst=consistentNumber(rows,gstGetter,prefix+"_gst_percent",0,100);Boolean taxable=consistentBoolean(rows,taxableGetter,prefix+"_taxable");
        boolean supplied=!blank(type)||amount!=null||taxable!=null||gst!=null;if(!supplied)return null;double amountValue=amount==null?0:amount;if(amountValue<=0)throw new IllegalArgumentException(prefix+"_amount must be greater than zero when a charge is supplied");if(blank(type))throw new IllegalArgumentException(prefix+"_type is required when a charge amount is supplied");boolean taxableValue=Boolean.TRUE.equals(taxable);double gstValue=gst==null?0:gst;if(!taxableValue&&gstValue>0.0001)throw new IllegalArgumentException(prefix+"_gst_percent requires "+prefix+"_taxable=true");return new PurchaseCharge(type.trim(),amountValue,taxableValue,taxableValue?gstValue:0);
    }

    private Path resolveAttachment(Path workbookFile,String attachment){
        if(attachment==null||attachment.isBlank())return null;Path source;try{source=Path.of(attachment.trim());}catch(Exception invalid){throw new IllegalArgumentException("attachment_file is not a valid path: "+attachment);}
        if(!source.isAbsolute()){Path parent=workbookFile.toAbsolutePath().normalize().getParent();source=(parent==null?Path.of(""):parent).resolve(source).normalize();}
        if(!Files.isRegularFile(source))throw new IllegalArgumentException("attachment_file was not found: "+attachment);return source;
    }

    private SalesCharge salesCharge(List<DocumentImportRow> rows, int index) {
        java.util.function.Function<DocumentImportRow,String> typeGetter = index == 1 ? DocumentImportRow::charge1Type : DocumentImportRow::charge2Type;
        java.util.function.Function<DocumentImportRow,String> amountGetter = index == 1 ? DocumentImportRow::charge1Amount : DocumentImportRow::charge2Amount;
        java.util.function.Function<DocumentImportRow,String> taxableGetter = index == 1 ? DocumentImportRow::charge1Taxable : DocumentImportRow::charge2Taxable;
        java.util.function.Function<DocumentImportRow,String> gstGetter = index == 1 ? DocumentImportRow::charge1GstPercent : DocumentImportRow::charge2GstPercent;
        String prefix = "charge_" + index;
        String type = consistentText(rows, typeGetter, prefix + "_type", true);
        Double amount = consistentNumber(rows, amountGetter, prefix + "_amount", 0, Double.MAX_VALUE);
        Boolean taxable = consistentBoolean(rows, taxableGetter, prefix + "_taxable");
        Double gst = consistentNumber(rows, gstGetter, prefix + "_gst_percent", 0, 100);
        boolean supplied = !blank(type) || amount != null || taxable != null || gst != null;
        if (!supplied) return null;
        double amountValue = amount == null ? 0 : amount;
        if (amountValue <= 0) throw new IllegalArgumentException(prefix + "_amount must be greater than zero when a charge is supplied");
        if (blank(type)) throw new IllegalArgumentException(prefix + "_type is required when a charge amount is supplied");
        boolean taxableValue = Boolean.TRUE.equals(taxable);
        double gstValue = gst == null ? 0 : gst;
        if (!taxableValue && gstValue > 0.0001) throw new IllegalArgumentException(prefix + "_gst_percent requires " + prefix + "_taxable=true");
        return new SalesCharge(type.trim(), amountValue, taxableValue, taxableValue ? gstValue : 0);
    }

    private String consistentText(List<DocumentImportRow> rows, java.util.function.Function<DocumentImportRow,String> getter, String field, boolean ignoreCase) {
        String selected = null;
        for (DocumentImportRow row : rows) {
            String value = getter.apply(row);
            if (value == null || value.isBlank()) continue;
            String clean = value.trim();
            if (selected == null) selected = clean;
            else if (ignoreCase ? !selected.equalsIgnoreCase(clean) : !selected.equals(clean))
                throw new IllegalArgumentException("Conflicting " + field + " values across rows for the same invoice");
        }
        return selected;
    }

    private Double consistentNumber(List<DocumentImportRow> rows, java.util.function.Function<DocumentImportRow,String> getter, String field, double minimum, double maximum) {
        Double selected = null;
        for (DocumentImportRow row : rows) {
            String value = getter.apply(row);
            if (value == null || value.isBlank()) continue;
            final double parsed;
            try { parsed = Double.parseDouble(value.trim().replace(",", "")); }
            catch (Exception invalid) { throw new IllegalArgumentException(field + " must be a number"); }
            if (!Double.isFinite(parsed) || parsed < minimum || parsed > maximum)
                throw new IllegalArgumentException(field + " is outside the allowed range");
            if (selected == null) selected = parsed;
            else if (Math.abs(selected - parsed) > 0.005)
                throw new IllegalArgumentException("Conflicting " + field + " values across rows for the same invoice");
        }
        return selected;
    }

    private Boolean consistentBoolean(List<DocumentImportRow> rows, java.util.function.Function<DocumentImportRow,String> getter, String field) {
        Boolean selected = null;
        for (DocumentImportRow row : rows) {
            String value = getter.apply(row);
            if (value == null || value.isBlank()) continue;
            String clean = value.trim().toLowerCase(Locale.ROOT);
            Boolean parsed = switch (clean) {
                case "true", "yes", "y", "1", "taxable" -> true;
                case "false", "no", "n", "0", "non-taxable", "nontaxable" -> false;
                default -> throw new IllegalArgumentException(field + " must be true/false, yes/no, or 1/0");
            };
            if (selected == null) selected = parsed;
            else if (!selected.equals(parsed)) throw new IllegalArgumentException("Conflicting " + field + " values across rows for the same invoice");
        }
        return selected;
    }

    private static void validateDocumentHeaderConsistency(List<DocumentImportRow> rows) {
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("Invoice contains no rows");
        DocumentImportRow first = rows.getFirst();
        for (DocumentImportRow row : rows) {
            if (!Objects.equals(first.date(), row.date())) throw new IllegalArgumentException("Invoice rows contain different invoice dates");
            if (!sameHeaderText(first.party(), row.party())) throw new IllegalArgumentException("Invoice rows contain different party codes");
            if (!sameHeaderText(first.terms(), row.terms())) throw new IllegalArgumentException("Invoice rows contain different payment terms");
            if (!sameHeaderText(first.taxType(), row.taxType())) throw new IllegalArgumentException("Invoice rows contain different GST/IGST treatment");
            if (Math.abs(first.paid() - row.paid()) > .009) throw new IllegalArgumentException("Invoice rows contain inconsistent paid amounts");
            if (!sameHeaderText(first.remarks(), row.remarks())) throw new IllegalArgumentException("Invoice rows contain inconsistent remarks");
        }
        if (Math.abs(first.paid()) > .009) throw new IllegalArgumentException("Invoice import cannot establish a paid balance. Import payments through the supported payment workflow.");
    }
    private static boolean sameHeaderText(String a,String b){return Objects.equals(a==null?"":a.trim(),b==null?"":b.trim());}

    private String sourceRows(List<?> rawRows) {
        if (rawRows == null || rawRows.isEmpty()) return "";
        List<Integer> rows = new ArrayList<>();
        for (Object value : rawRows) {
            try {
                var method = value.getClass().getDeclaredMethod("sourceRow");
                method.setAccessible(true);
                rows.add((Integer) method.invoke(value));
            } catch (Exception ignored) { }
        }
        if (rows.isEmpty()) return "";
        Collections.sort(rows);
        return rows.size() == 1 ? String.valueOf(rows.get(0)) : rows.get(0) + "-" + rows.get(rows.size() - 1);
    }

    private String taxDescription(String taxType, double gstPercent) {
        if ("IGST".equalsIgnoreCase(taxType)) {
            return String.format(Locale.ROOT, "IGST %.2f%% calculated from line values", gstPercent);
        }
        double half = gstPercent / 2.0;
        return String.format(Locale.ROOT, "GST %.2f%% calculated as CGST %.2f%% + SGST %.2f%%", gstPercent, half, half);
    }

    private void applySalesTotals(Sales document) {
        List<DocumentCalculationEngine.LineInput> lines = document.getLines().stream()
                .map(line -> new DocumentCalculationEngine.LineInput(
                        line.getQuantity(), line.getRate(), line.getDiscountPercent(), line.getGstPercent()))
                .toList();
        List<DocumentCalculationEngine.ChargeInput> charges = document.getCharges().stream()
                .map(charge -> new DocumentCalculationEngine.ChargeInput(
                        charge.getAmount(), charge.isTaxable(), charge.getGstPercent()))
                .toList();
        DocumentCalculationEngine.Totals totals = DocumentCalculationEngine.totals(
                lines, charges, DocumentCalculationEngine.taxMode(document.getGstType()));
        document.setSubtotal(totals.itemTaxable());
        document.setGstAmount(totals.taxAmount());
        document.setTotalAmount(totals.grandTotal());
    }

    private void applyPurchaseTotals(Purchase document) {
        List<DocumentCalculationEngine.LineInput> lines = document.getLines().stream()
                .map(line -> new DocumentCalculationEngine.LineInput(
                        line.getQuantity(), line.getRate(), line.getDiscountPercent(), line.getGstPercent()))
                .toList();
        List<DocumentCalculationEngine.ChargeInput> charges = document.getCharges().stream()
                .map(charge -> new DocumentCalculationEngine.ChargeInput(
                        charge.getAmount(), charge.isTaxable(), charge.getGstPercent()))
                .toList();
        DocumentCalculationEngine.Totals totals = DocumentCalculationEngine.totals(
                lines, charges, DocumentCalculationEngine.taxMode(document.getGstType()));
        document.setSubtotal(totals.itemTaxable());
        document.setGstAmount(totals.taxAmount());
        document.setTotalAmount(totals.grandTotal());
    }
}
