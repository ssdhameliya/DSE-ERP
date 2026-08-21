package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.config.WorkspaceManager;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ExcelTemplate;
import org.example.documentstudio.model.TemplateStatus;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/** File-backed Excel template repository. PDF and Excel stores are intentionally isolated. */
public final class ExcelTemplateStorageService {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final String META = "template.json";
    private static final String SOURCE = "source.xlsx";

    private ExcelTemplateStorageService() {}

    public static Path root() throws IOException {
        Path root = WorkspaceManager.getTemplatesFolder().resolve("DocumentStudio").resolve("Excel");
        Files.createDirectories(root);
        return root;
    }

    public static List<ExcelTemplate> listAll() {
        List<ExcelTemplate> result = new ArrayList<>();
        try (Stream<Path> folders = Files.list(root())) {
            folders.filter(Files::isDirectory).forEach(folder -> {
                try { load(folder).ifPresent(result::add); }
                catch (Exception error) { log("list", folder, error); }
            });
        } catch (Exception error) { log("list-root", null, error); }
        result.sort(Comparator.comparing(ExcelTemplate::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    public static Optional<ExcelTemplate> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        try { return load(root().resolve(id)); }
        catch (Exception error) { log("find:" + id, null, error); return Optional.empty(); }
    }

    public static synchronized Optional<ExcelTemplate> defaultFor(DocumentType type) {
        List<ExcelTemplate> defaults = listAll().stream()
                .filter(t -> t.getDocumentType() == type)
                .filter(t -> t.getStatus() == TemplateStatus.ACTIVE)
                .filter(ExcelTemplate::isDefaultTemplate)
                .toList();
        if (defaults.isEmpty()) return Optional.empty();
        ExcelTemplate keeper = defaults.getFirst();
        for (int i = 1; i < defaults.size(); i++) {
            ExcelTemplate duplicate = defaults.get(i);
            duplicate.setDefaultTemplate(false);
            try { saveMetadata(duplicate); } catch (Exception ignored) { }
        }
        return Optional.of(keeper);
    }

    public static ExcelTemplate createBlank(String name, DocumentType type) throws IOException {
        if (type == null || type.isGeneral()) throw new IOException("Choose an ERP document type for the Excel template.");
        ExcelTemplate template = fresh(name, type);
        Path folder = folder(template);
        Files.createDirectories(folder.resolve("history"));
        try (Workbook workbook = starterWorkbook(type); OutputStream out = Files.newOutputStream(folder.resolve(SOURCE))) {
            workbook.write(out);
        }
        saveMetadata(template);
        return template;
    }

    public static ExcelTemplate importWorkbook(Path source, String name, DocumentType type) throws IOException {
        if (type == null || type.isGeneral()) throw new IOException("Choose an ERP document type for the Excel template.");
        if (source == null || !Files.isRegularFile(source)) throw new IOException("The selected Excel workbook does not exist.");
        String lower = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx")) throw new IOException("Excel Studio currently accepts .xlsx templates.");
        // Validate before copying so a corrupt workbook never appears in the library.
        try (Workbook ignored = WorkbookFactory.create(source.toFile())) { }
        catch (Exception error) { throw new IOException("The selected Excel workbook could not be opened: " + rootMessage(error), error); }
        ExcelTemplate template = fresh(name, type);
        Path folder = folder(template);
        Files.createDirectories(folder.resolve("history"));
        Files.copy(source, folder.resolve(SOURCE), StandardCopyOption.REPLACE_EXISTING);
        saveMetadata(template);
        return template;
    }

    private static ExcelTemplate fresh(String name, DocumentType type) {
        ExcelTemplate template = new ExcelTemplate();
        template.setId(UUID.randomUUID().toString());
        template.setName(name);
        template.setDocumentType(type);
        template.setStatus(TemplateStatus.DRAFT);
        template.setDefaultTemplate(false);
        template.setSourceFile(SOURCE);
        return template;
    }

    /**
     * Opens the editable workbook from an in-memory snapshot so the persisted source.xlsx
     * is never held open by Apache POI. This is required on Windows because an
     * XSSFWorkbook opened directly from File/Path can keep the OPC package locked and
     * prevent the save transaction from replacing source.xlsx.
     */
    public static Workbook openWorkbookDetached(ExcelTemplate template) throws IOException {
        if (template == null) throw new IOException("Excel template is required.");
        Path source = sourceWorkbook(template);
        if (!Files.isRegularFile(source)) throw new IOException("The Excel template workbook does not exist: " + source);
        byte[] snapshot = Files.readAllBytes(source);
        if (snapshot.length == 0) throw new IOException("The Excel template workbook is empty: " + source);
        try {
            return WorkbookFactory.create(new ByteArrayInputStream(snapshot));
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("The Excel template workbook could not be opened: " + rootMessage(error), error);
        }
    }

    public static synchronized void saveWorkbook(ExcelTemplate template, Workbook workbook) throws IOException {
        if (template == null || workbook == null) throw new IOException("Excel template and workbook are required.");
        Path folder = folder(template);
        Files.createDirectories(folder.resolve("history"));
        Path source = folder.resolve(SOURCE);
        Path temp = Files.createTempFile(folder, "source-save-", ".xlsx");
        Path history = null;
        int priorVersion = template.getVersion();
        String priorUpdatedAt = template.getUpdatedAt();
        boolean sourceExisted = Files.isRegularFile(source) && Files.size(source) > 0;
        try {
            // Write and reopen the candidate before touching the last known-good workbook.
            try (OutputStream out = Files.newOutputStream(temp)) { workbook.write(out); }
            try (Workbook check = WorkbookFactory.create(temp.toFile())) {
                if (check.getNumberOfSheets() < 1) throw new IOException("Excel workbook must contain at least one worksheet.");
            } catch (IOException error) { throw error; }
            catch (Exception error) { throw new IOException("The edited Excel workbook could not be reopened after saving: " + rootMessage(error), error); }

            if (sourceExisted) {
                history = folder.resolve("history").resolve("v" + priorVersion + "-" + System.currentTimeMillis() + ".xlsx");
                Files.copy(source, history, StandardCopyOption.REPLACE_EXISTING);
            }
            moveReplace(temp, source);
            if (sourceExisted) template.setVersion(priorVersion + 1);
            saveMetadata(template);
        } catch (Exception error) {
            template.setVersion(priorVersion);
            template.setUpdatedAt(priorUpdatedAt);
            try {
                if (history != null && Files.isRegularFile(history)) moveReplace(Files.copy(history, folder.resolve("source.rollback.xlsx"), StandardCopyOption.REPLACE_EXISTING), source);
                else if (!sourceExisted) Files.deleteIfExists(source);
            } catch (Exception rollbackError) {
                error.addSuppressed(rollbackError);
            }
            try { Files.deleteIfExists(temp); } catch (Exception ignored) { }
            if (error instanceof IOException io) throw io;
            throw new IOException("Excel workbook could not be saved: " + rootMessage(error), error);
        } finally {
            try { Files.deleteIfExists(temp); } catch (Exception ignored) { }
            try { Files.deleteIfExists(folder.resolve("source.rollback.xlsx")); } catch (Exception ignored) { }
        }
    }

    private static void moveReplace(Path from, Path to) throws IOException {
        IOException atomicFailure = null;
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        } catch (AtomicMoveNotSupportedException error) {
            atomicFailure = error;
        } catch (FileSystemException error) {
            // Windows can reject an atomic replacement even when a normal replacement is
            // allowed (for example while filesystem/AV metadata is being refreshed).
            atomicFailure = error;
        }
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            if (atomicFailure != null) error.addSuppressed(atomicFailure);
            if (error instanceof FileSystemException) {
                throw new IOException("Excel Studio could not replace the saved template file. Close any external program that has the workbook open and try again. "
                        + from + " -> " + to + ": " + rootMessage(error), error);
            }
            throw error;
        }
    }

    public static synchronized void saveMetadata(ExcelTemplate template) throws IOException {
        if (template == null) throw new IOException("Excel template is required.");
        Path folder = folder(template);
        Files.createDirectories(folder.resolve("history"));
        template.touch();
        Path temp = folder.resolve(META + ".tmp");
        JSON.writeValue(temp.toFile(), template);
        try { Files.move(temp, folder.resolve(META), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, folder.resolve(META), StandardCopyOption.REPLACE_EXISTING); }
    }

    public static synchronized void activateAndSetDefault(ExcelTemplate template) throws IOException {
        if (template == null) return;
        if (!DocumentFlowRegistry.isExcelAutomatic(template.getDocumentType()))
            throw new IOException(template.getDocumentType().label() + " is not ERP-connected and cannot be an automatic Excel default.");
        Path source = sourceWorkbook(template);
        byte[] sourceBeforeValidation = Files.readAllBytes(source);
        Path test = folder(template).resolve(".activation-test.xlsx");
        Exception validationFailure = null;
        try {
            ExcelTemplateRenderer.renderSample(template, test);
            try (Workbook ignored = WorkbookFactory.create(test.toFile())) { }
        } catch (Exception error) {
            validationFailure = error;
        } finally {
            try { Files.deleteIfExists(test); } catch (Exception ignored) { }
            try {
                byte[] sourceAfterValidation = Files.readAllBytes(source);
                if (!Arrays.equals(sourceBeforeValidation, sourceAfterValidation)) {
                    Path restore = Files.createTempFile(folder(template), "source-activation-restore-", ".xlsx");
                    Files.write(restore, sourceBeforeValidation);
                    moveReplace(restore, source);
                    IOException mutation = new IOException("Default activation changed the saved template unexpectedly. The original token workbook was restored and activation was cancelled.");
                    if (validationFailure != null) mutation.addSuppressed(validationFailure);
                    validationFailure = mutation;
                }
            } catch (Exception auditError) {
                IOException auditFailure = new IOException("Excel Studio could not verify that default activation preserved the saved template: " + rootMessage(auditError), auditError);
                if (validationFailure != null) auditFailure.addSuppressed(validationFailure);
                validationFailure = auditFailure;
            }
        }
        if (validationFailure != null)
            throw new IOException("Excel template validation failed. The built-in Excel output remains active. " + rootMessage(validationFailure), validationFailure);
        for (ExcelTemplate other : listAll()) {
            if (other.getDocumentType() == template.getDocumentType() && other.isDefaultTemplate() && !other.getId().equals(template.getId())) {
                other.setDefaultTemplate(false);
                saveMetadata(other);
            }
        }
        template.setStatus(TemplateStatus.ACTIVE);
        template.setDefaultTemplate(true);
        saveMetadata(template);
    }

    public static synchronized ExcelTemplate duplicate(ExcelTemplate source) throws IOException {
        if (source == null) throw new IOException("Excel template is required.");
        ExcelTemplate copy = JSON.readValue(JSON.writeValueAsBytes(source), ExcelTemplate.class);
        copy.setId(UUID.randomUUID().toString());
        copy.setName(source.getName() + " Copy");
        copy.setDefaultTemplate(false);
        copy.setStatus(TemplateStatus.DRAFT);
        copy.setVersion(1);
        copy.setCreatedAt(Instant.now().toString());
        copy.setUpdatedAt(Instant.now().toString());
        Path folder = folder(copy);
        Files.createDirectories(folder.resolve("history"));
        Files.copy(sourceWorkbook(source), folder.resolve(SOURCE), StandardCopyOption.REPLACE_EXISTING);
        saveMetadata(copy);
        return copy;
    }

    public static synchronized void archive(ExcelTemplate template) throws IOException {
        template.setStatus(TemplateStatus.ARCHIVED);
        template.setDefaultTemplate(false);
        saveMetadata(template);
    }

    public static synchronized void delete(ExcelTemplate template) throws IOException {
        if (template == null) return;
        Path folder = folder(template);
        if (!Files.exists(folder)) return;
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    public static Path folder(ExcelTemplate template) throws IOException {
        if (template == null || template.getId() == null || template.getId().isBlank()) throw new IOException("Invalid Excel template id.");
        return root().resolve(template.getId());
    }

    public static Path sourceWorkbook(ExcelTemplate template) throws IOException {
        Path file = folder(template).resolve(template.getSourceFile());
        if (!Files.isRegularFile(file)) throw new IOException("Excel template workbook is missing: " + file);
        return file;
    }

    /** Creates a starter workbook whose tokens are derived from the selected document type. */
    public static Workbook starterWorkbook(DocumentType type) {
        DocumentType effective = type == null ? DocumentType.CUSTOM_ERP : type;
        Set<String> supported = TemplateFieldCatalog.excelFieldsFor(effective).stream()
                .map(org.example.documentstudio.model.TemplateFieldDefinition::key)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(safeSheetName(effective.label()));
        sheet.setDisplayGridlines(false);

        CellStyle title = workbook.createCellStyle();
        Font titleFont = workbook.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 18); title.setFont(titleFont);
        CellStyle header = workbook.createCellStyle();
        Font headerFont = workbook.createFont(); headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex()); header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle money = workbook.createCellStyle(); money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        Row r0 = sheet.createRow(0);
        r0.setHeightInPoints(42);
        r0.createCell(0).setCellValue("{{company.logo}}");
        Cell company = r0.createCell(1); company.setCellValue("{{company.name}}"); company.setCellStyle(title);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0,0,1,7));
        Row r1 = sheet.createRow(1); r1.createCell(1).setCellValue("{{company.address}}"); sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1,1,1,7));
        Row r2 = sheet.createRow(2); r2.createCell(1).setCellValue("GSTIN: {{company.gstin}}   •   {{company.phone}}   •   {{company.email}}"); sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2,2,1,7));

        String numberKey = documentNumberKey(effective);
        String dateKey = documentDateKey(effective);
        Row r4 = sheet.createRow(4);
        r4.createCell(0).setCellValue(effective.label() + " No.");
        r4.createCell(1).setCellValue(token(numberKey, supported));
        r4.createCell(4).setCellValue("Date");
        r4.createCell(5).setCellValue(token(dateKey, supported));

        String partyNameKey = partyNameKey(effective), partyGstinKey = partyGstinKey(effective), partyAddressKey = partyAddressKey(effective);
        Row r5 = sheet.createRow(5);
        r5.createCell(0).setCellValue("Party"); r5.createCell(1).setCellValue(token(partyNameKey, supported));
        if(!partyGstinKey.isBlank() && supported.contains(partyGstinKey)){r5.createCell(4).setCellValue("GSTIN");r5.createCell(5).setCellValue(token(partyGstinKey, supported));}
        if(!partyAddressKey.isBlank() && supported.contains(partyAddressKey)){Row r6=sheet.createRow(6);r6.createCell(0).setCellValue("Address");r6.createCell(1).setCellValue(token(partyAddressKey,supported));sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(6,6,1,7));}

        int rowCursor = 8;
        if (effective == DocumentType.PAYMENT_RECEIPT && supported.contains("receipt.amount")) {
            Row amount=sheet.createRow(rowCursor++); amount.createCell(5).setCellValue("Amount"); Cell value=amount.createCell(7); value.setCellValue("{{receipt.amount}}"); value.setCellStyle(money);
            rowCursor++;
        }

        if (TemplateFieldCatalog.supportsItemRows(effective)) {
            sheet.createFreezePane(0, rowCursor + 1);
            Row h = sheet.createRow(rowCursor++);
            String[] cols = {"#","Item / Description","HSN","Qty","Unit","Rate","GST %","Amount"};
            for (int i=0;i<cols.length;i++){Cell cell=h.createCell(i);cell.setCellValue(cols[i]);cell.setCellStyle(header);}
            Row item = sheet.createRow(rowCursor++);
            String[] itemFields = {"item.serial","item.descriptionWithRemarks","item.hsn","item.quantity","item.unit","item.rate","item.gstPercent","item.taxable"};
            for(int i=0;i<itemFields.length;i++)item.createCell(i).setCellValue(token(itemFields[i],supported));
            rowCursor++;
        } else {
            sheet.createFreezePane(0, 7);
        }

        if (TemplateFieldCatalog.supportsChargeRows(effective)) {
            Row charge=sheet.createRow(rowCursor++);
            charge.createCell(4).setCellValue("{{charge.type}}"); charge.createCell(6).setCellValue("{{charge.gstPercent}}"); charge.createCell(7).setCellValue("{{charge.total}}");
            rowCursor++;
        }

        if (supported.contains("totals.grandTotal")) {
            String grandKey=supported.contains("totals.roundedGrandTotal")?"totals.roundedGrandTotal":"totals.grandTotal";
            String[][] totals = {{"Subtotal","totals.subtotal"},{"Additional Charges","totals.chargesAmount"},{"Gross Total Before Tax","totals.grossBeforeTax"},{"CGST","totals.cgstAmount"},{"SGST","totals.sgstAmount"},{"IGST","totals.igstAmount"},{"Round Off","totals.roundOff"},{"Grand Total",grandKey}};
            for(String[] entry:totals){if(!supported.contains(entry[1]))continue;Row row=sheet.createRow(rowCursor++);row.createCell(5).setCellValue(entry[0]);Cell value=row.createCell(7);value.setCellValue("{{"+entry[1]+"}}");value.setCellStyle(money);}
            if(supported.contains("totals.amountInWords")){rowCursor++;Row words=sheet.createRow(rowCursor++);words.createCell(0).setCellValue("Amount in words");words.createCell(1).setCellValue("{{totals.amountInWords}}");sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(words.getRowNum(),words.getRowNum(),1,7));}
        }

        rowCursor++;
        Row signature = sheet.createRow(rowCursor); signature.setHeightInPoints(42); signature.createCell(5).setCellValue("{{company.signature}}"); sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowCursor,rowCursor+1,5,7));
        Row auth = sheet.createRow(rowCursor+2); auth.createCell(5).setCellValue("Authorized Signatory"); sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowCursor+2,rowCursor+2,5,7));
        for (int i=0;i<8;i++) sheet.setColumnWidth(i, new int[]{12,34,14,10,10,14,12,16}[i]*256);
        return workbook;
    }

    private static String token(String key, Set<String> supported){
        return key==null||key.isBlank()||supported==null||!supported.contains(key)?"":"{{"+key+"}}";
    }

    private static String documentNumberKey(DocumentType type){
        return switch(type){
            case SALES_INVOICE -> "sales.number";
            case PURCHASE_INVOICE, PURCHASE_ORDER -> "purchase.number";
            case QUOTATION -> "quotation.number";
            case DELIVERY_CHALLAN -> "delivery.number";
            case CREDIT_NOTE, DEBIT_NOTE, SALES_RETURN, PURCHASE_RETURN -> "return.number";
            case PAYMENT_RECEIPT -> "receipt.number";
            default -> "";
        };
    }
    private static String documentDateKey(DocumentType type){
        return switch(type){
            case SALES_INVOICE -> "sales.date";
            case PURCHASE_INVOICE, PURCHASE_ORDER -> "purchase.date";
            case QUOTATION -> "quotation.date";
            case DELIVERY_CHALLAN -> "delivery.date";
            case CREDIT_NOTE, DEBIT_NOTE, SALES_RETURN, PURCHASE_RETURN -> "return.date";
            case PAYMENT_RECEIPT -> "receipt.date";
            default -> "";
        };
    }
    private static String partyNameKey(DocumentType type){
        return switch(type){
            case SALES_INVOICE, QUOTATION, DELIVERY_CHALLAN -> "customer.name";
            case PURCHASE_INVOICE, PURCHASE_ORDER -> "supplier.name";
            case CREDIT_NOTE, DEBIT_NOTE, SALES_RETURN, PURCHASE_RETURN -> "party.name";
            case PAYMENT_RECEIPT -> "receipt.partyName";
            default -> "";
        };
    }
    private static String partyGstinKey(DocumentType type){
        return switch(type){
            case SALES_INVOICE, QUOTATION, DELIVERY_CHALLAN -> "customer.gstin";
            case PURCHASE_INVOICE, PURCHASE_ORDER -> "supplier.gstin";
            case CREDIT_NOTE, DEBIT_NOTE, SALES_RETURN, PURCHASE_RETURN -> "party.gstin";
            default -> "";
        };
    }
    private static String partyAddressKey(DocumentType type){
        return switch(type){
            case SALES_INVOICE, QUOTATION, DELIVERY_CHALLAN -> "customer.address";
            case PURCHASE_INVOICE, PURCHASE_ORDER -> "supplier.address";
            case CREDIT_NOTE, DEBIT_NOTE, SALES_RETURN, PURCHASE_RETURN -> "party.address";
            default -> "";
        };
    }

    private static Optional<ExcelTemplate> load(Path folder) throws IOException {
        Path meta = folder.resolve(META);
        if (!Files.isRegularFile(meta)) return Optional.empty();
        ExcelTemplate template = JSON.readValue(meta.toFile(), ExcelTemplate.class);
        if (template.getId() == null || template.getId().isBlank()) template.setId(folder.getFileName().toString());
        return Optional.of(template);
    }

    private static String safeSheetName(String value) { String cleaned=(value==null?"Document":value).replace('\\',' ').replace('/',' ').replace('?',' ').replace('*',' ').replace('[',' ').replace(']',' ').replace(':',' ').trim(); if(cleaned.isBlank())cleaned="Document"; return cleaned.substring(0, Math.min(31, cleaned.length())); }
    private static void log(String operation, Path path, Exception error) { System.err.println("[ExcelStudio] " + operation + (path == null ? "" : " ["+path+"]") + " failed: " + rootMessage(error)); }
    private static String rootMessage(Throwable error) { Throwable root=error;while(root.getCause()!=null&&root.getCause()!=root)root=root.getCause();return root.getMessage()==null?root.getClass().getSimpleName():root.getMessage(); }
}
