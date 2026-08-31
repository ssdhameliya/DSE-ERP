package org.example.server.master;
import org.example.shared.ReferenceFormatRules;

import org.example.server.persistence.entity.*;
import org.example.server.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.example.server.security.CurrentUser;
import org.example.server.operations.BusinessOperationsService;
import org.example.server.audit.AuditService;
import org.example.server.web.ConcurrentEditException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.*;

@Service
public class MasterDataService {
    private final PartyRepository parties;
    private final ItemRepository items;
    private final LookupRepository lookups;
    private final MasterCategoryRepository categories;
    private final BusinessOperationsService referenceNumbers;
    private final AuditService audit;

    @PersistenceContext
    private EntityManager entityManager;

    public MasterDataService(PartyRepository p, ItemRepository i, LookupRepository l, MasterCategoryRepository c, BusinessOperationsService referenceNumbers, AuditService audit) {
        parties = p;
        items = i;
        lookups = l;
        categories = c;
        this.referenceNumbers = referenceNumbers;
        this.audit = audit;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureFinanceMasterCategories() {
        ensureCategory("PAYMENT_MODE","PAYMENT MODE","Payment methods used by Bank, Expense and Invoice Payment",130);
        ensureCategory("EXPENSE_CATEGORY","EXPENSE CATEGORY","Expense classifications used by Expense Entry",140);
        ensureCategory("BANK_ACCOUNT","BANK ACCOUNT","Bank account master: lookup value = account number, description = bank name",150);
        ensureCategory("REFERENCE_FORMAT","REFERENCE FORMAT","Auto-generated reference patterns. YYYY / YY represent year; XX... defines minimum zero-padding and expands automatically as the sequence grows.",160);
        ensureReferenceFormat("REF_SALES","IN/DD-MM-YYYY/XXXX","Sales invoice reference",10);
        ensureReferenceFormat("REF_PURCHASE","PUR/DD-MM-YYYY/XXXX","Purchase invoice reference",20);
        ensureReferenceFormat("REF_QUOTATION","QT-YYYY-XXXX","Quotation reference",30);
        ensureReferenceFormat("REF_SALES_RETURN","SAL-RET-YYYY-XXXX","Sales Return reference",40);
        ensureReferenceFormat("REF_PURCHASE_RETURN","PUR-RET-YYYY-XXXX","Purchase Return reference",50);
        ensureReferenceFormat("REF_ITEM","ITMXXX","Item code reference",60);
        ensureReferenceFormat("REF_CUSTOMER","CUSXXX","Customer reference",70);
        ensureReferenceFormat("REF_SUPPLIER","SUPXXX","Supplier reference",80);
        ensureReferenceFormat("REF_FINANCE_VOUCHER","VCH-YYYY-XXXXX","Finance voucher reference",90);
        ensureReferenceFormat("REF_RECON_SUPPLIER","RSP-YYYY-XXXXX","Recon Supplier reference",100);
        ensureReferenceFormat("REF_PURCHASE_RECON","PRC-YYYY-XXXXX","Purchase Recon reference",110);
        ensureReferenceFormat("REF_LOOKUP_CATEGORY","CATXXX","Category Master code reference",210);
        ensureReferenceFormat("REF_LOOKUP_UNIT","UNTXXX","Unit Master code reference",220);
        ensureReferenceFormat("REF_LOOKUP_MATERIAL","MATXXX","Material Master code reference",230);
        ensureReferenceFormat("REF_LOOKUP_BRAND","BRDXXX","Brand Master code reference",240);
        ensureReferenceFormat("REF_LOOKUP_GST","GSTXXX","GST Master code reference",250);
        ensureReferenceFormat("REF_LOOKUP_ROLE","ROLXXX","Role Master code reference",260);
        ensureReferenceFormat("REF_LOOKUP_DISCOUNT","DSCXXX","Discount Master code reference",270);
        ensureReferenceFormat("REF_LOOKUP_GST_TYPE","GTPXXX","GST Type Master code reference",280);
        ensureReferenceFormat("REF_LOOKUP_TRANSPORTER","TRNXXX","Transporter Master code reference",290);
        ensureReferenceFormat("REF_LOOKUP_PAYMENT_TERMS","PTMXXX","Payment Terms Master code reference",300);
        ensureReferenceFormat("REF_LOOKUP_CHARGES","CHGXXX","Charges Master code reference",310);
        ensureReferenceFormat("REF_LOOKUP_PAYMENT_MODE","PMDXXX","Payment Mode Master code reference",320);
        ensureReferenceFormat("REF_LOOKUP_EXPENSE_CATEGORY","EXPXXX","Expense Category Master code reference",330);
        ensureReferenceFormat("REF_LOOKUP_BANK_ACCOUNT","BNKXXX","Bank Account Master code reference",340);
        ensureReferenceFormat("REF_LOOKUP_QUOTATION_SOURCE","QTSXXX","Quotation Source Master code reference",350);
        ensureReferenceFormat("REF_LOOKUP_REFERENCE_FORMAT","RFMXXX","Reference Format Master code reference",360);
    }

    private void ensureCategory(String code,String name,String description,int order){
        // Startup assurance resolves only the requested category code/name.
        if(resolveCanonicalCategoryByCode(code)!=null) return;
        MasterCategoryEntity e=new MasterCategoryEntity();e.setCategoryCode(code);e.setCategoryName(name);e.setDescription(description);e.setDisplayOrder(order);e.setActive(1);categories.save(e);
    }


    private void ensureReferenceFormat(String lookupCode,String value,String description,int order){
        MasterCategoryEntity category=categories.findByCategoryCode("REFERENCE_FORMAT").orElse(null);
        if(category==null) return;
        boolean exists=lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(category.getCategoryName()).stream()
            .anyMatch(row->row.getLookupCode()!=null&&row.getLookupCode().equalsIgnoreCase(lookupCode));
        if(exists) return;
        LookupEntity row=new LookupEntity();row.setLookupType(category.getCategoryName());row.setLookupCode(lookupCode);row.setLookupValue(value);
        row.setDescription(description);row.setDisplayOrder(order);row.setActive(1);lookups.save(row);
    }
    @Transactional(readOnly = true)
    public List<MasterDtos.PartyDto> parties(String type) {
        requirePartyAccess(type);
        return parties.findByPartyTypeOrderByNameAsc(normal(type)).stream().map(this::partyDto).toList();
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.PartyDto> searchParties(String type, String query, int limit) {
        requirePartyAccess(type);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return parties.searchActive(normal(type), query == null ? "" : query.trim(), PageRequest.of(0, safeLimit))
            .stream().map(this::partyDto).toList();
    }

    @Transactional
    public MasterDtos.PartyDto saveParty(MasterDtos.PartyDto d) {
        requirePartyPermission(d == null ? null : d.partyType(), "CREATE");
        PartyEntity e = new PartyEntity();
        copy(d, e, true);
        if (e.getPartyCode() == null || e.getPartyCode().isBlank()) e.setPartyCode(allocatePartyCode(e.getPartyType()));
        e = parties.saveAndFlush(e);
        audit.log("PARTY", e.getId(), "CREATED", e.getPartyType() + " " + e.getPartyCode());
        return partyDto(e);
    }

    @Transactional
    public MasterDtos.PartyDto updateParty(MasterDtos.PartyDto d) {
        PartyEntity e = parties.findById(req(d.id(), "Party id")).orElseThrow(() -> new IllegalArgumentException("Party not found"));
        requirePartyPermission(e.getPartyType(), "EDIT");
        requirePartyPermission(d.partyType(), "EDIT");
        assertVersion(d.rowVersion(), e.getRowVersion(), "Party " + e.getPartyCode());
        copy(d, e, false);
        e = parties.saveAndFlush(e);
        audit.log("PARTY", e.getId(), "UPDATED", e.getPartyType() + " " + e.getPartyCode());
        return partyDto(e);
    }

    @Transactional
    public void deleteParty(int id, long rowVersion) {
        PartyEntity e = parties.findById(id).orElseThrow(() -> new IllegalArgumentException("Party not found"));
        requirePartyPermission(e.getPartyType(), "DELETE");
        assertVersion(rowVersion, e.getRowVersion(), "Party " + e.getPartyCode());
        List<String> usages = partyDeleteUsages(e.getId());
        if (!usages.isEmpty()) {
            throw new IllegalStateException("This " + e.getPartyType().toLowerCase(Locale.ROOT) + " cannot be deleted because it is used by ERP transactions (" + String.join(", ", usages) + "). Edit the party and mark it Inactive instead so historical documents remain valid.");
        }
        parties.delete(e);
        parties.flush();
        audit.log("PARTY", e.getId(), "DELETED", e.getPartyType() + " " + e.getPartyCode());
    }

    private List<String> partyDeleteUsages(int partyId) {
        List<String> usages = new ArrayList<>();
        addPartyUsage(usages, "sales_header", "customer_id", partyId, "Sales");
        addPartyUsage(usages, "purchase_header", "supplier_id", partyId, "Purchases");
        addPartyUsage(usages, "quotation_header", "customer_id", partyId, "Quotations");
        addPartyUsage(usages, "return_register", "party_id", partyId, "Returns");
        addPartyUsage(usages, "finance_register", "party_id", partyId, "Finance");
        return List.copyOf(usages);
    }

    private void addPartyUsage(List<String> usages, String table, String column, int partyId, String label) {
        Number count = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = :partyId")
            .setParameter("partyId", partyId).getSingleResult();
        long total = count == null ? 0L : count.longValue();
        if (total > 0) usages.add(label + " " + total);
    }

    @Transactional(readOnly = true)
    public boolean partyExists(String code) {
        return parties.existsByPartyCode(code);
    }



    @Transactional(readOnly = true)
    public String nextPartyCode(String type) {
        requirePartyAccess(type);
        return previewPartyCode(type);
    }

    private String previewPartyCode(String type) {
        String t = normal(type);
        String key = "CUSTOMER".equals(t) ? "REF_CUSTOMER" : "REF_SUPPLIER";
        String fallback = "CUSTOMER".equals(t) ? "CUSXXX" : "SUPXXX";
        List<String> existing = parties.findByPartyTypeOrderByNameAsc(t).stream().map(PartyEntity::getPartyCode).filter(Objects::nonNull).toList();
        return referenceNumbers.previewConfiguredReference(key, fallback, existing);
    }

    private String allocatePartyCode(String type) {
        String t = normal(type);
        String key = "CUSTOMER".equals(t) ? "REF_CUSTOMER" : "REF_SUPPLIER";
        String fallback = "CUSTOMER".equals(t) ? "CUSXXX" : "SUPXXX";
        List<String> existing = parties.findByPartyTypeOrderByNameAsc(t).stream().map(PartyEntity::getPartyCode).filter(Objects::nonNull).toList();
        return referenceNumbers.nextConfiguredReference(key, fallback, existing);
    }

    private void requirePartyAccess(String type) {
        String normalized = normal(type);
        if (!Set.of("CUSTOMER", "SUPPLIER").contains(normalized)) throw new IllegalArgumentException("Party type must be CUSTOMER or SUPPLIER");
        if ("CUSTOMER".equals(normalized)) CurrentUser.requirePermission("CUSTOMERS.VIEW", "Customer access");
        else CurrentUser.requirePermission("SUPPLIERS.VIEW", "Supplier access");
    }

    private void requirePartyPermission(String type, String action) {
        String normalized = normal(type);
        if (!Set.of("CUSTOMER", "SUPPLIER").contains(normalized)) throw new IllegalArgumentException("Party type must be CUSTOMER or SUPPLIER");
        String prefix = "CUSTOMER".equals(normalized) ? "CUSTOMERS" : "SUPPLIERS";
        CurrentUser.requirePermission(prefix + "." + action, action + " " + normalized.toLowerCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.ItemDto> items() {
        CurrentUser.requirePermission("INVENTORY.VIEW", "View items");
        return items.findAllByOrderByItemCodeAsc()
            .stream()
            .map(this::itemDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.ItemDto> searchItems(String query, int limit) {
        CurrentUser.requirePermission("INVENTORY.VIEW", "Search items");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return items.searchActive(query == null ? "" : query.trim(), PageRequest.of(0, safeLimit))
            .stream().map(this::itemDto).toList();
    }

    @Transactional(readOnly = true)
    public MasterDtos.SalesEntryBootstrap salesEntryBootstrap() {
        return new MasterDtos.SalesEntryBootstrap(
            valuesByCategoryCode("PAYMENT_TERMS"),
            valuesByCategoryCode("CHARGES"),
            valuesByCategoryCode("GST_TYPE"),
            lookupsByCategoryCode("TRANSPORTER"),
            searchParties("CUSTOMER", "", 40)
        );
    }


    @Transactional
    public MasterDtos.ItemDto saveItem(MasterDtos.ItemDto d) {
        CurrentUser.requirePermission("INVENTORY.CREATE", "Create item");
        ItemEntity e = new ItemEntity();
        copy(d, e, true);
        if (e.getItemCode() == null || e.getItemCode().isBlank()) {
            List<String> existing = items.findAll().stream().map(ItemEntity::getItemCode).filter(Objects::nonNull).toList();
            e.setItemCode(referenceNumbers.nextConfiguredReference("REF_ITEM", "ITMXXX", existing));
        }
        e = items.saveAndFlush(e);
        audit.log("ITEM", e.getId(), "CREATED", e.getItemCode());
        return itemDto(e);
    }

    @Transactional
    public MasterDtos.ItemDto updateItem(MasterDtos.ItemDto d) {
        CurrentUser.requirePermission("INVENTORY.EDIT", "Edit item");
        ItemEntity e = items.findByItemCodeForUpdate(d.itemCode()).orElseThrow(() -> new IllegalArgumentException("Item not found"));
        assertVersion(d.rowVersion(), e.getRowVersion(), "Item " + e.getItemCode());
        copy(d, e, false);
        e = items.saveAndFlush(e);
        audit.log("ITEM", e.getId(), "UPDATED", e.getItemCode());
        return itemDto(e);
    }

    @Transactional
    public void deleteItem(String code, long rowVersion) {
        CurrentUser.requirePermission("INVENTORY.DELETE", "Delete item");
        ItemEntity e = items.findByItemCodeForUpdate(code).orElseThrow(() -> new IllegalArgumentException("Item not found"));
        assertVersion(rowVersion, e.getRowVersion(), "Item " + e.getItemCode());
        List<String> usages = new ArrayList<>(itemDeleteUsages(code));
        double stock = e.getOpeningStock() == null ? 0.0 : e.getOpeningStock();
        double reserved = e.getReservedStock() == null ? 0.0 : e.getReservedStock();
        if (Math.abs(stock) > 0.0001 || Math.abs(reserved) > 0.0001) usages.add("Current inventory balance");
        if (!usages.isEmpty()) throw new IllegalStateException("Item cannot be deleted while it has stock or is referenced by ERP transactions.");
        items.delete(e);
        items.flush();
        audit.log("ITEM", e.getId(), "DELETED", e.getItemCode());
    }

    @Transactional(readOnly = true)
    public boolean itemExists(String code) {
        return items.existsByItemCode(code);
    }

    @Transactional(readOnly = true)
    public MasterDtos.ItemBulkDeleteValidation validateBulkDeleteItems(List<String> requestedCodes) {
        CurrentUser.requirePermission("INVENTORY.DELETE", "Validate item deletion");
        List<String> codes = normalizeItemCodes(requestedCodes);
        if (codes.isEmpty()) throw new IllegalArgumentException("Select at least one item to delete.");
        List<MasterDtos.ItemDeleteIssue> issues = new ArrayList<>();
        for (String code : codes) {
            ItemEntity item = items.findByItemCode(code).orElse(null);
            if (item == null) {
                issues.add(new MasterDtos.ItemDeleteIssue(code, code, List.of("Item no longer exists. Refresh Item Master and try again.")));
                continue;
            }
            List<String> usages = new ArrayList<>(itemDeleteUsages(code));
            double stock = item.getOpeningStock() == null ? 0.0 : item.getOpeningStock();
            double reserved = item.getReservedStock() == null ? 0.0 : item.getReservedStock();
            if (Math.abs(stock) > 0.0001 || Math.abs(reserved) > 0.0001) {
                usages.add("Current inventory balance (stock " + stock + ", reserved " + reserved + ")");
            }
            if (!usages.isEmpty()) {
                issues.add(new MasterDtos.ItemDeleteIssue(code, Objects.toString(item.getDescription(), code), List.copyOf(usages)));
            }
        }
        return new MasterDtos.ItemBulkDeleteValidation(issues.isEmpty(), codes.size(), List.copyOf(issues));
    }

    @Transactional
    public int bulkDeleteItems(List<String> requestedCodes) {
        CurrentUser.requirePermission("INVENTORY.DELETE", "Delete items");
        List<String> codes = normalizeItemCodes(requestedCodes);
        MasterDtos.ItemBulkDeleteValidation validation = validateBulkDeleteItems(codes);
        if (!validation.valid()) {
            throw new IllegalStateException("Bulk delete blocked because one or more selected items are referenced by ERP transactions.");
        }
        int deleted = 0;
        for (String code : codes) {
            ItemEntity item = items.findByItemCodeForUpdate(code).orElseThrow(() -> new IllegalStateException("Item changed during bulk delete: " + code));
            List<String> usages = new ArrayList<>(itemDeleteUsages(code));
            double stock = item.getOpeningStock() == null ? 0.0 : item.getOpeningStock();
            double reserved = item.getReservedStock() == null ? 0.0 : item.getReservedStock();
            if (Math.abs(stock) > 0.0001 || Math.abs(reserved) > 0.0001) usages.add("Current inventory balance");
            if (!usages.isEmpty()) throw new IllegalStateException("Bulk delete stopped because item " + code + " changed or became referenced. Nothing was deleted.");
            Integer deletedId = item.getId();
            items.delete(item);
            audit.log("ITEM", deletedId, "DELETED", code + " • bulk delete");
            deleted++;
        }
        items.flush();
        return deleted;
    }

    private List<String> normalizeItemCodes(List<String> requestedCodes) {
        if (requestedCodes == null) return List.of();
        return requestedCodes.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(v -> !v.isBlank())
            .distinct()
            .toList();
    }

    private List<String> itemDeleteUsages(String code) {
        List<String> usages = new ArrayList<>();
        addItemUsage(usages, "sales_line", code, "Sales invoices");
        addItemUsage(usages, "purchase_line", code, "Purchase invoices");
        addItemUsage(usages, "quotation_line", code, "Quotations");
        addItemUsage(usages, "return_register", code, "Sales/Purchase returns");
        addItemUsage(usages, "stock_adjustment", code, "Stock adjustment history");
        return List.copyOf(usages);
    }

    private void addItemUsage(List<String> usages, String table, String code, String label) {
        Number count = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE item_code = :code")
            .setParameter("code", code)
            .getSingleResult();
        long total = count == null ? 0L : count.longValue();
        if (total > 0) usages.add(label + " (" + total + ")");
    }

    @Transactional(readOnly = true)
    public String nextItemCode() {
        List<String> existing = items.findAll().stream().map(ItemEntity::getItemCode).filter(Objects::nonNull).toList();
        return referenceNumbers.previewConfiguredReference("REF_ITEM", "ITMXXX", existing);
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.LookupDto> lookups(String type) {
        return lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(type).stream().map(this::lookupDto).toList();
    }

    @Transactional(readOnly = true)
    public List<String> values(String type) {
        return lookups.findByLookupTypeAndActiveTrueOrderByDisplayOrderAscLookupValueAsc(type).stream().map(LookupEntity::getLookupValue).toList();
    }

    @Transactional(readOnly = true)
    public List<String> valuesByCategoryCode(String code) {
        MasterCategoryEntity c = resolveCategoryByCode(code);
        return c == null ? List.of() : values(c.getCategoryName());
    }

    @Transactional(readOnly = true)
    public Map<String,String> referenceFormats() {
        LinkedHashMap<String,String> result = new LinkedHashMap<>();
        for (MasterDtos.LookupDto row : lookupsByCategoryCode("REFERENCE_FORMAT")) {
            if (row.active() && row.lookupCode() != null && !row.lookupCode().isBlank()
                    && row.lookupValue() != null && !row.lookupValue().isBlank()) {
                result.put(row.lookupCode().trim().toUpperCase(Locale.ROOT), row.lookupValue().trim());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.LookupDto> lookupsByCategoryCode(String code) {
        MasterCategoryEntity c = resolveCategoryByCode(code);
        return c == null ? List.of() :
            lookups.findByLookupTypeAndActiveTrueOrderByDisplayOrderAscLookupValueAsc(c.getCategoryName())
                .stream().map(this::lookupDto).toList();
    }

    /** Resolve a current Master code or a v9.0.46 legacy alias (for example GEN014 -> MAT001). */
    @Transactional(readOnly = true)
    public MasterDtos.LookupCodeResolution resolveLookupCode(String type, String code) {
        String requestedType = normal(type);
        String requestedCode = normal(code);
        if (requestedType.isBlank() || requestedCode.isBlank())
            return new MasterDtos.LookupCodeResolution(requestedCode, requestedCode, false);

        LookupEntity current = lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(requestedType).stream()
                .filter(row -> requestedCode.equals(normal(row.getLookupCode())))
                .findFirst().orElse(null);
        if (current != null)
            return new MasterDtos.LookupCodeResolution(requestedCode, normal(current.getLookupCode()), false);

        @SuppressWarnings("unchecked")
        List<Object[]> aliases = entityManager.createNativeQuery(
                        "select lm.lookup_code,a.alias_code from lookup_code_alias a join lookup_master lm on lm.id=a.lookup_id " +
                        "where upper(trim(a.lookup_type))=upper(trim(:type)) and upper(trim(a.alias_code))=upper(trim(:code)) order by a.id limit 1")
                .setParameter("type", requestedType).setParameter("code", requestedCode).getResultList();
        if (aliases.isEmpty()) return new MasterDtos.LookupCodeResolution(requestedCode, requestedCode, false);
        String canonical = aliases.getFirst()[0] == null ? requestedCode : normal(String.valueOf(aliases.getFirst()[0]));
        return new MasterDtos.LookupCodeResolution(requestedCode, canonical, true);
    }

    @Transactional
    public MasterDtos.LookupDto saveLookup(MasterDtos.LookupDto d) {
        requireMasterMutation("CREATE");
        validateLookup(d);
        validateRoleLookup(d, null);
        requireActiveCategoryForActiveLookup(d.lookupType(), d.active());
        LookupEntity e = new LookupEntity();
        copy(d, e);
        if (e.getLookupCode() == null || e.getLookupCode().isBlank()) e.setLookupCode(allocateLookupCode(e.getLookupType()));
        e = lookups.saveAndFlush(e);
        audit.log("MASTER_LOOKUP", e.getId(), "CREATED", e.getLookupType() + " / " + e.getLookupValue());
        return lookupDto(e);
    }

    @Transactional
    public MasterDtos.LookupDto updateLookup(MasterDtos.LookupDto d) {
        requireMasterMutation("EDIT");
        validateLookup(d);
        LookupEntity e = lookups.findById(req(d.id(), "Lookup id")).orElseThrow(() -> new IllegalArgumentException("Lookup not found"));
        assertVersion(d.rowVersion(), e.getRowVersion(), "Master value " + e.getLookupValue());
        validateRoleLookup(d, e);
        requireActiveCategoryForActiveLookup(d.lookupType(), d.active());
        cascadeRoleValueRenameIfNeeded(e, d);
        copy(d, e);
        e = lookups.saveAndFlush(e);
        syncLookupAliases(e);
        audit.log("MASTER_LOOKUP", e.getId(), "UPDATED", e.getLookupType() + " / " + e.getLookupValue());
        return lookupDto(e);
    }

    private void requireActiveCategoryForActiveLookup(String type, boolean active) {
        if (!active) return;
        MasterCategoryEntity category = categories.findByCategoryName(type).orElse(null);
        if (category != null && !Objects.equals(category.getActive(), 1)) {
            throw new IllegalArgumentException("Master category '" + type + "' is inactive. Reactivate the category before activating or adding values.");
        }
    }

    @Transactional
    public void deleteLookup(int id, long rowVersion) {
        requireMasterMutation("DELETE");
        LookupEntity e = lookups.findById(id).orElseThrow(() -> new IllegalArgumentException("Lookup not found"));
        assertVersion(rowVersion, e.getRowVersion(), "Master value " + e.getLookupValue());
        validateRoleDeactivation(e);
        // Master values are historical reference data. A user "delete" therefore retires the value
        // instead of physically removing it, whether or not the value has already been used.
        e.setActive(0);
        e = lookups.saveAndFlush(e);
        audit.log("MASTER_LOOKUP", e.getId(), "DEACTIVATED", e.getLookupType() + " / " + e.getLookupValue());
    }

    @Transactional
    public MasterDtos.LookupDto setLookupActive(int id, boolean active, long rowVersion) {
        requireMasterMutation("EDIT");
        LookupEntity e = lookups.findById(id).orElseThrow(() -> new IllegalArgumentException("Lookup not found"));
        assertVersion(rowVersion, e.getRowVersion(), "Master value " + e.getLookupValue());
        if (!active) validateRoleDeactivation(e);
        requireActiveCategoryForActiveLookup(e.getLookupType(), active);
        e.setActive(active ? 1 : 0);
        e = lookups.saveAndFlush(e);
        audit.log("MASTER_LOOKUP", e.getId(), active ? "ACTIVATED" : "DEACTIVATED", e.getLookupType() + " / " + e.getLookupValue());
        return lookupDto(e);
    }

    @Transactional(readOnly = true)
    public String nextLookupCode(String type) {
        LookupNumberingScope scope = lookupNumberingScope(type);
        List<String> existing = lookups.findByLookupTypeOrderByLookupCodeDesc(scope.lookupType()).stream()
                .map(LookupEntity::getLookupCode).filter(Objects::nonNull).toList();
        return referenceNumbers.previewConfiguredReference(scope.referenceKey(), scope.prefix() + "XXX", existing);
    }

    private String allocateLookupCode(String type) {
        LookupNumberingScope scope = lookupNumberingScope(type);
        List<String> existing = lookups.findByLookupTypeOrderByLookupCodeDesc(scope.lookupType()).stream()
                .map(LookupEntity::getLookupCode).filter(Objects::nonNull).toList();
        return referenceNumbers.nextConfiguredReference(scope.referenceKey(), scope.prefix() + "XXX", existing);
    }

    /**
     * Master lookup numbering is keyed by the immutable category_code, never the editable
     * category_name. This keeps preview/save on the same sequence after a Master category is
     * renamed and prevents unrelated Masters from sharing the legacy GEN counter.
     */
    private LookupNumberingScope lookupNumberingScope(String type) {
        String requested = normal(type);
        if (requested.isBlank()) throw new IllegalArgumentException("Lookup type is required");

        MasterCategoryEntity category = categories.findByCategoryName(requested).orElse(null);
        if (category == null) category = resolveCanonicalCategoryByCode(requested);

        String categoryCode = category == null ? code(requested) : normal(category.getCategoryCode());
        String lookupType = category == null ? requested : normal(category.getCategoryName());
        String prefix = lookupPrefix(categoryCode);
        return new LookupNumberingScope(categoryCode, lookupType, prefix);
    }

    private String lookupPrefix(String categoryCode) {
        return switch (normal(categoryCode)) {
            case "CATEGORY" -> "CAT";
            case "UNIT", "UOM" -> "UNT";
            case "MATERIAL" -> "MAT";
            case "BRAND" -> "BRD";
            case "GST" -> "GST";
            case "ROLE" -> "ROL";
            case "DISCOUNT" -> "DSC";
            case "GST_TYPE" -> "GTP";
            case "TRANSPORTER" -> "TRN";
            case "PAYMENT_TERMS" -> "PTM";
            case "CHARGES" -> "CHG";
            case "PAYMENT_MODE" -> "PMD";
            case "EXPENSE_CATEGORY" -> "EXP";
            case "BANK_ACCOUNT" -> "BNK";
            case "QUOTATION_SOURCE" -> "QTS";
            case "REFERENCE_FORMAT" -> "RFM";
            default -> derivedMasterPrefix(categoryCode);
        };
    }

    private String derivedMasterPrefix(String categoryCode) {
        String compact = normal(categoryCode).replaceAll("[^A-Z0-9]", "");
        if (compact.isBlank()) return "MST";
        if (compact.length() >= 3) return compact.substring(0, 3);
        return (compact + "MST").substring(0, 3);
    }

    private record LookupNumberingScope(String categoryCode, String lookupType, String prefix) {
        String referenceKey() { return "REF_LOOKUP_" + categoryCode; }
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.CategoryDto> categories() {
        return categories.findAllByOrderByDisplayOrderAscCategoryNameAsc().stream()
            .filter(c -> !Set.of("SALES_INVOICE_FORMAT","PURCHASE_INVOICE_FORMAT").contains(normal(c.getCategoryCode())))
            .map(c -> categoryDto(c, lookups.countByLookupType(c.getCategoryName()), lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(c.getCategoryName()).stream().filter(v -> v.getActive() == null || v.getActive() != 0).count())).toList();
    }

    @Transactional
    public MasterDtos.CategoryDto addCategory(String name) {
        requireMasterMutation("CREATE");
        String n = normal(name), code = code(n);
        if (Set.of("SALES INVOICE FORMAT","PURCHASE INVOICE FORMAT").contains(n)) throw new IllegalArgumentException("Invoice numbering is managed only by REFERENCE FORMAT.");
        if (categories.findByCategoryName(n).isPresent()) throw new IllegalArgumentException("Category already exists");
        MasterCategoryEntity e = new MasterCategoryEntity();
        e.setCategoryCode(code);
        e.setCategoryName(n);
        e.setDisplayOrder(0);
        e.setActive(1);
        e = categories.saveAndFlush(e);
        audit.log("MASTER_CATEGORY", e.getId(), "CREATED", e.getCategoryName());
        return categoryDto(e, 0, 0);
    }

    @Transactional
    public MasterDtos.CategoryDto renameCategory(String oldName, String newName, long rowVersion) {
        requireMasterMutation("EDIT");
        MasterCategoryEntity c = categories.findByCategoryName(oldName).orElseThrow(() -> new IllegalArgumentException("Category not found"));
        assertVersion(rowVersion, c.getRowVersion(), "Master category " + c.getCategoryName());
        if ("ROLE".equals(normal(c.getCategoryCode()))) throw new IllegalArgumentException("Role Master is a protected system category and cannot be renamed");
        String n = normal(newName);
        if (Set.of("SALES INVOICE FORMAT","PURCHASE INVOICE FORMAT").contains(n)) throw new IllegalArgumentException("Invoice numbering is managed only by REFERENCE FORMAT.");
        List<LookupEntity> vals = lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(oldName);
        if (!oldName.equalsIgnoreCase(n) && categories.findByCategoryName(n).isPresent()) {
            throw new IllegalArgumentException("Category already exists");
        }
        c.setCategoryName(n);
        for (LookupEntity l : vals) l.setLookupType(n);
        lookups.saveAllAndFlush(vals);
        vals.forEach(this::syncLookupAliases);
        c = categories.saveAndFlush(c);
        audit.log("MASTER_CATEGORY", c.getId(), "RENAMED", oldName + " -> " + c.getCategoryName());
        return categoryDto(c, vals.size(), vals.stream().filter(v -> v.getActive() == null || v.getActive() != 0).count());
    }

    @Transactional
    public MasterDtos.CategoryDto setCategoryActive(String name, boolean active, long rowVersion) {
        requireMasterMutation("EDIT");
        MasterCategoryEntity category = categories.findByCategoryName(name)
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        assertVersion(rowVersion, category.getRowVersion(), "Master category " + category.getCategoryName());
        if (!active && "ROLE".equals(normal(category.getCategoryCode()))) throw new IllegalArgumentException("Role Master cannot be deactivated");
        category.setActive(active ? 1 : 0);
        category = categories.saveAndFlush(category);
        List<LookupEntity> values = lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(category.getCategoryName());
        long activeCount = values.stream().filter(value -> value.getActive() == null || value.getActive() != 0).count();
        audit.log("MASTER_CATEGORY", category.getId(), active ? "ACTIVATED" : "DEACTIVATED", category.getCategoryName());
        return categoryDto(category, values.size(), activeCount);
    }

    @Transactional
    public void deleteCategory(String name, long rowVersion) {
        requireMasterMutation("DELETE");
        MasterCategoryEntity category = categories.findByCategoryName(name)
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        assertVersion(rowVersion, category.getRowVersion(), "Master category " + category.getCategoryName());
        if ("ROLE".equals(normal(category.getCategoryCode()))) throw new IllegalArgumentException("Role Master cannot be deactivated");
        List<LookupEntity> values = lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(name);
        // Retire the category and all of its values atomically. Existing transactions keep their
        // historical text, while active-only APIs stop offering these values for future use.
        category.setActive(0);
        for (LookupEntity value : values) value.setActive(0);
        if (!values.isEmpty()) lookups.saveAllAndFlush(values);
        category = categories.saveAndFlush(category);
        audit.log("MASTER_CATEGORY", category.getId(), "DEACTIVATED", category.getCategoryName());
    }



    private long countText(String table, String column, String value) {
        Number n = (Number) entityManager.createNativeQuery(
            "select count(*) from " + table + " where upper(trim(coalesce(" + column + ",''))) = upper(trim(:value))")
            .setParameter("value", value).getSingleResult();
        return n.longValue();
    }


    @Transactional
    public void saveItems(List<MasterDtos.ItemDto> rows) {
        if (rows == null) return;
        for (MasterDtos.ItemDto d : rows) {
            ItemEntity e = items.findByItemCode(d.itemCode()).orElseGet(ItemEntity::new);
            boolean creating = e.getId() == null;
            CurrentUser.requirePermission(creating ? "INVENTORY.CREATE" : "INVENTORY.EDIT", creating ? "Import new item" : "Update item from import");
            copy(d, e, creating);
            e = items.saveAndFlush(e);
            audit.log("ITEM", e.getId(), creating ? "CREATED" : "UPDATED", e.getItemCode() + " • bulk import");
        }
    }

    @Transactional
    public MasterDtos.CategoryDto upsertCategory(MasterDtos.CategoryUpsertRequest d) {
        requireMasterMutation("EDIT");
        if (d == null) throw new IllegalArgumentException("Category data is required");
        String code = normal(d.code());
        String nextName = normal(d.name());
        if (code.isBlank()) throw new IllegalArgumentException("Category code is required");
        if (nextName.isBlank()) throw new IllegalArgumentException("Category name is required");
        MasterCategoryEntity e = categories.findByCategoryCode(code).orElseGet(MasterCategoryEntity::new);
        String previousName = e.getId() == null ? null : e.getCategoryName();
        e.setCategoryCode(code); e.setCategoryName(nextName); e.setDescription(d.description());
        if (e.getActive() == null) e.setActive(1); if (e.getDisplayOrder() == null) e.setDisplayOrder(0);
        e = categories.saveAndFlush(e);
        if (previousName != null && !previousName.equalsIgnoreCase(nextName)) {
            List<LookupEntity> migrated = lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(previousName);
            for (LookupEntity value : migrated) value.setLookupType(nextName);
            if (!migrated.isEmpty()) {
                lookups.saveAllAndFlush(migrated);
                migrated.forEach(this::syncLookupAliases);
            }
        }
        audit.log("MASTER_CATEGORY", e.getId(), "UPSERTED", e.getCategoryName());
        List<LookupEntity> values = lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(e.getCategoryName());
        return categoryDto(e, values.size(), values.stream().filter(v -> v.getActive() == null || v.getActive() != 0).count());
    }


    private MasterCategoryEntity resolveCanonicalCategoryByCode(String code) {
        String requested = normal(code);
        if (requested.isBlank()) return null;
        MasterCategoryEntity exact = categories.findByCategoryCode(requested).orElse(null);
        if (exact != null) return exact;
        String key = categoryKey(requested);
        return categories.findAll().stream()
            .filter(c -> key.equals(categoryKey(c.getCategoryCode())) || key.equals(categoryKey(c.getCategoryName())))
            .min(Comparator.comparing((MasterCategoryEntity c) -> c.getId() == null ? Integer.MAX_VALUE : c.getId()))
            .orElse(null);
    }

    private MasterCategoryEntity resolveCategoryByCode(String code) {
        String requested = normal(code);
        if (requested.isBlank()) return null;
        MasterCategoryEntity exact = categories.findByCategoryCode(requested).orElse(null);
        if (exact != null) return exact;

        String key = categoryKey(requested);
        List<MasterCategoryEntity> all = categories.findAll();
        MasterCategoryEntity normalized = all.stream()
            .filter(c -> key.equals(categoryKey(c.getCategoryCode())) || key.equals(categoryKey(c.getCategoryName())))
            .min(Comparator.comparing((MasterCategoryEntity c) -> c.getId() == null ? Integer.MAX_VALUE : c.getId()))
            .orElse(null);
        if (normalized != null) return normalized;

        return null;
    }

    private String categoryKey(String value) {
        return normal(value).replaceAll("[^A-Z0-9]", "");
    }


    private String normal(String v) {
        return v == null ? "" : v.trim().toUpperCase(Locale.ROOT);
    }

    private String code(String n) {
        String c = n.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return c.isBlank() ? "CATEGORY" : c;
    }

    private Integer req(Integer v, String n) {
        if (v == null || v <= 0) throw new IllegalArgumentException(n + " is required");
        return v;
    }

    private void copy(MasterDtos.PartyDto d, PartyEntity e, boolean includeCode) {
        if (d == null) throw new IllegalArgumentException("Party data is required");
        String partyType = normal(d.partyType());
        if (!Set.of("CUSTOMER", "SUPPLIER").contains(partyType)) throw new IllegalArgumentException("Party type must be CUSTOMER or SUPPLIER");
        if (d.name() == null || d.name().isBlank()) throw new IllegalArgumentException("Party name is required");
        if ("SUPPLIER".equals(partyType) && (d.email() == null || d.email().isBlank())) {
            throw new IllegalArgumentException("Supplier email is required");
        }
        if (includeCode) {
            e.setPartyType(partyType);
            e.setPartyCode(d.partyCode());
        }
        e.setName(d.name());
        e.setContactPerson(d.contactPerson());
        e.setPhone(d.phone());
        e.setEmail(d.email());
        e.setGstin(d.gstin());
        e.setAddress(d.address());
        e.setOpeningBalance(d.openingBalance());
        e.setActive(d.active() ? 1 : 0);
    }

    private MasterDtos.PartyDto partyDto(PartyEntity e) {
        return new MasterDtos.PartyDto(e.getId(), e.getPartyType(), e.getPartyCode(), e.getName(), e.getContactPerson(), e.getPhone(), e.getEmail(), e.getGstin(), e.getAddress(), n(e.getOpeningBalance()), e.getActive() == null || e.getActive() != 0, nv(e.getRowVersion()));
    }

    private void copy(MasterDtos.ItemDto d, ItemEntity e, boolean includeCode) {
        if(d.hsn()==null||d.hsn().isBlank()) throw new IllegalArgumentException("HSN Code is required");
        if(d.remarks()==null||d.remarks().isBlank()) throw new IllegalArgumentException("Remarks are required");
        if (includeCode) e.setItemCode(d.itemCode());
        e.setDescription(d.description());
        e.setCategory(d.category());
        e.setBrand(d.brand());
        e.setMaterial(d.material());
        e.setSize(d.size());
        e.setUnit(d.unit());
        e.setHsn(d.hsn());
        if(!Double.isFinite(d.gst())||d.gst()<0||d.gst()>100) throw new IllegalArgumentException("GST percent must be between 0 and 100");
        if(!Double.isFinite(d.discountPercent())||d.discountPercent()<0||d.discountPercent()>100) throw new IllegalArgumentException("Discount percent must be between 0 and 100");
        if(!Double.isFinite(d.purchasePrice())||d.purchasePrice()<0) throw new IllegalArgumentException("Purchase price must be a finite non-negative number");
        if(!Double.isFinite(d.sellingPrice())||d.sellingPrice()<0) throw new IllegalArgumentException("Selling price must be a finite non-negative number");
        if(!Double.isFinite(d.minimumStock())||d.minimumStock()<0) throw new IllegalArgumentException("Minimum stock must be a finite non-negative number");
        if(!Double.isFinite(d.reservedStock())||d.reservedStock()<0) throw new IllegalArgumentException("Reserved stock must be a finite non-negative number");
        e.setGst(d.gst());
        e.setDiscountPercent(d.discountPercent());
        e.setPurchasePrice(d.purchasePrice());
        e.setSellingPrice(d.sellingPrice());
        if (includeCode) {
            if (!Double.isFinite(d.openingStock()) || d.openingStock() < 0) throw new IllegalArgumentException("Opening stock must be a finite non-negative number");
            e.setOpeningStock(d.openingStock());
        }
        e.setMinimumStock(d.minimumStock());
        e.setLocation(d.location());
        e.setRemarks(d.remarks());
        if (e.getReservedStock() == null) e.setReservedStock(d.reservedStock());
        if (e.getActive() == null) e.setActive(d.active() ? 1 : 0);
    }

    private MasterDtos.ItemDto itemDto(ItemEntity e) {
        return new MasterDtos.ItemDto(e.getId(), e.getItemCode(), e.getDescription(), e.getCategory(), e.getBrand(), e.getMaterial(), e.getSize(), e.getUnit(), e.getHsn(), n(e.getGst()), n(e.getDiscountPercent()), n(e.getPurchasePrice()), n(e.getSellingPrice()), n(e.getOpeningStock()), n(e.getMinimumStock()), n(e.getReservedStock()), e.getLocation(), e.getRemarks(), e.getActive() == null || e.getActive() != 0, nv(e.getRowVersion()));
    }

    private void validateRoleLookup(MasterDtos.LookupDto d, LookupEntity existing) {
        if (d == null || !"ROLE".equals(normal(d.lookupType()))) return;
        String roleValue = d.lookupValue() == null ? "" : d.lookupValue().trim();
        if (roleValue.isBlank()) throw new IllegalArgumentException("Role Name is required");
        // Role identity comes from Value only. The generated ROLxxx lookup code is a technical Master ID.
        if ("ADMIN".equals(normal(roleValue)) && !d.active()) {
            throw new IllegalArgumentException("The Admin Role Master entry cannot be deactivated");
        }
        if (existing != null && "ADMIN".equals(normal(existing.getLookupValue()))
                && !"ADMIN".equals(normal(roleValue))) {
            throw new IllegalArgumentException("The protected Admin role name cannot be changed");
        }
    }

    /**
     * Role values are referenced by users and permission assignments. Renaming a non-Admin role
     * therefore cascades atomically inside this transaction. Comparisons are case-insensitive.
     */
    private void cascadeRoleValueRenameIfNeeded(LookupEntity existing, MasterDtos.LookupDto incoming) {
        if (existing == null || incoming == null || !"ROLE".equals(normal(existing.getLookupType()))) return;
        String oldValue = existing.getLookupValue() == null ? "" : existing.getLookupValue().trim();
        String newValue = incoming.lookupValue() == null ? "" : incoming.lookupValue().trim();
        if (oldValue.isBlank() || newValue.isBlank() || normal(oldValue).equals(normal(newValue))) return;
        String canonical = normal(newValue);
        entityManager.createNativeQuery("update users set role=:newRole where upper(trim(coalesce(role,'')))=upper(trim(:oldRole))")
                .setParameter("newRole", canonical).setParameter("oldRole", oldValue).executeUpdate();
        entityManager.createNativeQuery("update role_permission set role_code=:newRole where upper(trim(coalesce(role_code,'')))=upper(trim(:oldRole))")
                .setParameter("newRole", canonical).setParameter("oldRole", oldValue).executeUpdate();
        entityManager.createNativeQuery("update application_setting set setting_value=:newRole,updated_at=CURRENT_TIMESTAMP where setting_key='auth.selfRegistrationRole' and upper(trim(coalesce(setting_value,'')))=upper(trim(:oldRole))")
                .setParameter("newRole", canonical).setParameter("oldRole", oldValue).executeUpdate();
    }

    private void validateRoleDeactivation(LookupEntity role) {
        if (role == null || !"ROLE".equals(normal(role.getLookupType()))) return;
        String roleValue = role.getLookupValue() == null ? "" : role.getLookupValue().trim();
        if ("ADMIN".equals(normal(roleValue))) throw new IllegalArgumentException("The Admin Role Master entry cannot be deactivated");
        if (isConfiguredSelfRegistrationRole(roleValue))
            throw new IllegalArgumentException("Choose another Public Registration Role in Role Management before deactivating this role");
        long assigned = countText("users", "role", roleValue);
        if (assigned > 0) throw new IllegalArgumentException("Move " + assigned + " assigned user" + (assigned == 1 ? "" : "s") + " to another active role before deactivating this role");
    }


    private boolean isConfiguredSelfRegistrationRole(String roleValue) {
        try {
            Number count = (Number) entityManager.createNativeQuery(
                            "select count(*) from application_setting where setting_key='auth.selfRegistrationRole' " +
                            "and upper(trim(coalesce(setting_value,'')))=upper(trim(:role))")
                    .setParameter("role", roleValue).getSingleResult();
            return count != null && count.longValue() > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void copy(MasterDtos.LookupDto d, LookupEntity e) {
        e.setLookupType(normal(d.lookupType()));
        e.setLookupCode(normal(d.lookupCode()));
        e.setLookupValue(d.lookupValue() == null ? null : d.lookupValue().trim());
        e.setDescription(d.description());
        e.setDisplayOrder(d.displayOrder());
        e.setActive(d.active() ? 1 : 0);
    }


    private void syncLookupAliases(LookupEntity lookup) {
        if (lookup == null || lookup.getId() == null) return;
        try {
            entityManager.createNativeQuery("update lookup_code_alias set lookup_type=:type,canonical_code=:code where lookup_id=:id")
                    .setParameter("type", lookup.getLookupType())
                    .setParameter("code", lookup.getLookupCode())
                    .setParameter("id", lookup.getId()).executeUpdate();
        } catch (RuntimeException ignored) {
            // Fresh databases without legacy aliases legitimately have nothing to synchronize.
        }
    }

    private void validateLookup(MasterDtos.LookupDto d) {
        if (d == null || d.lookupType() == null || d.lookupType().isBlank()) throw new IllegalArgumentException("Lookup type is required");
        boolean creating = d.id() == null || d.id() <= 0;
        if (!creating && (d.lookupCode() == null || d.lookupCode().isBlank())) throw new IllegalArgumentException("Lookup code is required");
        if (creating && d.lookupCode() != null && normal(d.lookupCode()).matches("^GEN\\d+$"))
            throw new IllegalArgumentException("GENxxx Master codes are retired. Leave the code blank so the server can allocate the category-specific reference.");
        if (d.lookupValue() == null || d.lookupValue().isBlank()) throw new IllegalArgumentException("Lookup value is required");
        MasterCategoryEntity referenceCategory = categories.findByCategoryCode("REFERENCE_FORMAT").orElse(null);
        if (referenceCategory != null && referenceCategory.getCategoryName().equalsIgnoreCase(d.lookupType().trim())) ReferenceFormatRules.requireValidFormat(d.lookupValue());
        if (d.lookupCode() != null && !d.lookupCode().isBlank() && lookups.duplicateCode(d.lookupType(), d.lookupCode(), d.id())) throw new IllegalArgumentException("This lookup code already exists in " + d.lookupType());
        if (lookups.duplicateValue(d.lookupType(), d.lookupValue(), d.id())) throw new IllegalArgumentException("This lookup value already exists in " + d.lookupType());
    }

    private MasterDtos.LookupDto lookupDto(LookupEntity e) {
        return new MasterDtos.LookupDto(e.getId(), e.getLookupType(), e.getLookupCode(), e.getLookupValue(), e.getDescription(), e.getDisplayOrder() == null ? 0 : e.getDisplayOrder(), e.getActive() == null || e.getActive() != 0, nv(e.getRowVersion()));
    }

    private MasterDtos.CategoryDto categoryDto(MasterCategoryEntity e, long count, long activeCount) {
        return new MasterDtos.CategoryDto(e.getId(), e.getCategoryCode(), e.getCategoryName(), e.getDescription(), e.getDisplayOrder() == null ? 0 : e.getDisplayOrder(), e.getActive() == null || e.getActive() != 0, count, activeCount, nv(e.getRowVersion()));
    }

    private void requireMasterMutation(String action) {
        if (CurrentUser.hasPermission("USERS.MANAGE_ROLES")) return;
        CurrentUser.requirePermission("MASTERS." + action, action + " master data");
    }

    private void assertVersion(long expected, Long current, String label) {
        long actual = nv(current);
        if (expected != actual) throw new ConcurrentEditException(label);
    }

    private long nv(Long v) { return v == null ? 0L : v; }

    private double n(Double v) {
        return v == null ? 0 : v;
    }
}
