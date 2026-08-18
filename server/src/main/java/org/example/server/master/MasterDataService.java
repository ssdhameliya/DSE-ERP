package org.example.server.master;

import org.example.server.persistence.entity.*;
import org.example.server.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import jakarta.annotation.PostConstruct;
import org.example.server.security.CurrentUser;
import org.example.server.operations.BusinessOperationsService;
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

    @PersistenceContext
    private EntityManager entityManager;

    public MasterDataService(PartyRepository p, ItemRepository i, LookupRepository l, MasterCategoryRepository c, BusinessOperationsService referenceNumbers) {
        parties = p;
        items = i;
        lookups = l;
        categories = c;
        this.referenceNumbers = referenceNumbers;
    }

    @PostConstruct
    public void ensureFinanceMasterCategories() {
        ensureCategory("PAYMENT_MODE","PAYMENT MODE","Payment methods used by Bank, Expense and Invoice Payment",130);
        ensureCategory("EXPENSE_CATEGORY","EXPENSE CATEGORY","Expense classifications used by Expense Entry",140);
        ensureCategory("BANK_ACCOUNT","BANK ACCOUNT","Bank account master: lookup value = account number, description = bank name",150);
        ensureCategory("REFERENCE_FORMAT","REFERENCE FORMAT","Auto-generated reference number patterns. Use YYYY / YY for year and XX... for sequence digits.",160);
        ensureReferenceFormat("REF_SALES",legacyFormat("SALES_INVOICE_FORMAT","IN/DD-MM-YYYY/XXXX"),"Sales invoice reference",10);
        ensureReferenceFormat("REF_PURCHASE",legacyFormat("PURCHASE_INVOICE_FORMAT","PUR/DD-MM-YYYY/XXXX"),"Purchase invoice reference",20);
        ensureReferenceFormat("REF_QUOTATION","QT-YYYY-XXXX","Quotation reference",30);
        ensureReferenceFormat("REF_SALES_RETURN","SAL-RET-YYYY-XXXX","Sales Return reference",40);
        ensureReferenceFormat("REF_PURCHASE_RETURN","PUR-RET-YYYY-XXXX","Purchase Return reference",50);
        ensureReferenceFormat("REF_ITEM","ITMXXX","Item code reference",60);
        ensureReferenceFormat("REF_CUSTOMER","CUSXXX","Customer reference",70);
        ensureReferenceFormat("REF_SUPPLIER","SUPXXX","Supplier reference",80);
    }

    private void ensureCategory(String code,String name,String description,int order){
        if(categories.findByCategoryCode(code).isPresent()) return;
        MasterCategoryEntity e=new MasterCategoryEntity();e.setCategoryCode(code);e.setCategoryName(name);e.setDescription(description);e.setDisplayOrder(order);e.setActive(1);categories.save(e);
    }



    private String legacyFormat(String categoryCode,String fallback){
        MasterCategoryEntity category=categories.findByCategoryCode(categoryCode).orElse(null);
        if(category==null) return fallback;
        return lookups.findByLookupTypeAndActiveTrueOrderByDisplayOrderAscLookupValueAsc(category.getCategoryName()).stream()
            .map(LookupEntity::getLookupValue).filter(value->value!=null&&!value.isBlank()).findFirst().orElse(fallback);
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
        requirePartyAccess(d == null ? null : d.partyType());
        PartyEntity e = new PartyEntity();
        copy(d, e, true);
        return partyDto(parties.save(e));
    }

    @Transactional
    public MasterDtos.PartyDto updateParty(MasterDtos.PartyDto d) {
        PartyEntity e = parties.findById(req(d.id(), "Party id")).orElseThrow(() -> new IllegalArgumentException("Party not found"));
        requirePartyAccess(e.getPartyType());
        requirePartyAccess(d.partyType());
        copy(d, e, false);
        return partyDto(e);
    }

    @Transactional
    public void deleteParty(int id) {
        PartyEntity e = parties.findById(id).orElseThrow(() -> new IllegalArgumentException("Party not found"));
        requirePartyAccess(e.getPartyType());
        parties.delete(e);
    }

    @Transactional(readOnly = true)
    public boolean partyExists(String code) {
        return parties.existsByPartyCode(code);
    }



    @Transactional(readOnly = true)
    public String nextPartyCode(String type) {
        requirePartyAccess(type);
        String t = normal(type);
        String key = "CUSTOMER".equals(t) ? "REF_CUSTOMER" : "REF_SUPPLIER";
        String fallback = "CUSTOMER".equals(t) ? "CUSXXX" : "SUPXXX";
        List<String> existing = parties.findByPartyTypeOrderByNameAsc(t).stream().map(PartyEntity::getPartyCode).filter(Objects::nonNull).toList();
        return referenceNumbers.nextConfiguredReference(key, fallback, existing);
    }

    private void requirePartyAccess(String type) {
        if (CurrentUser.isSales() && !"CUSTOMER".equals(normal(type))) {
            throw new SecurityException("Supplier data requires Manager or Admin access");
        }
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.ItemDto> items() {
        return items.findAllByOrderByItemCodeAsc()
            .stream()
            .map(this::itemDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.ItemDto> searchItems(String query, int limit) {
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
        ItemEntity e = new ItemEntity();
        copy(d, e, true);
        return itemDto(items.save(e));
    }

    @Transactional
    public MasterDtos.ItemDto updateItem(MasterDtos.ItemDto d) {
        ItemEntity e = items.findByItemCode(d.itemCode()).orElseThrow(() -> new IllegalArgumentException("Item not found"));
        copy(d, e, false);
        return itemDto(e);
    }

    @Transactional
    public void deleteItem(String code) {
        ItemEntity e = items.findByItemCode(code).orElseThrow(() -> new IllegalArgumentException("Item not found"));
        items.delete(e);
    }

    @Transactional(readOnly = true)
    public boolean itemExists(String code) {
        return items.existsByItemCode(code);
    }

    @Transactional(readOnly = true)
    public String nextItemCode() {
        List<String> existing = items.findAll().stream().map(ItemEntity::getItemCode).filter(Objects::nonNull).toList();
        return referenceNumbers.nextConfiguredReference("REF_ITEM", "ITMXXX", existing);
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
        MasterCategoryEntity c = categories.findByCategoryCode(code).orElse(null);
        return c == null ? List.of() : values(c.getCategoryName());
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.LookupDto> lookupsByCategoryCode(String code) {
        MasterCategoryEntity c = categories.findByCategoryCode(code).orElse(null);
        if (c == null) return List.of();
        return lookups.findByLookupTypeAndActiveTrueOrderByDisplayOrderAscLookupValueAsc(c.getCategoryName())
            .stream().map(this::lookupDto).toList();
    }

    @Transactional
    public MasterDtos.LookupDto saveLookup(MasterDtos.LookupDto d) {
        validateLookup(d);
        requireActiveCategoryForActiveLookup(d.lookupType(), d.active());
        LookupEntity e = new LookupEntity();
        copy(d, e);
        return lookupDto(lookups.saveAndFlush(e));
    }

    @Transactional
    public MasterDtos.LookupDto updateLookup(MasterDtos.LookupDto d) {
        validateLookup(d);
        requireActiveCategoryForActiveLookup(d.lookupType(), d.active());
        LookupEntity e = lookups.findById(req(d.id(), "Lookup id")).orElseThrow(() -> new IllegalArgumentException("Lookup not found"));
        copy(d, e);
        return lookupDto(lookups.saveAndFlush(e));
    }

    private void requireActiveCategoryForActiveLookup(String type, boolean active) {
        if (!active) return;
        MasterCategoryEntity category = categories.findByCategoryName(type).orElse(null);
        if (category != null && !Objects.equals(category.getActive(), 1)) {
            throw new IllegalArgumentException("Master category '" + type + "' is inactive. Reactivate the category before activating or adding values.");
        }
    }

    @Transactional
    public void deleteLookup(int id) {
        LookupEntity e = lookups.findById(id).orElseThrow(() -> new IllegalArgumentException("Lookup not found"));
        // Master values are historical reference data. A user "delete" therefore retires the value
        // instead of physically removing it, whether or not the value has already been used.
        e.setActive(0);
        lookups.saveAndFlush(e);
    }

    @Transactional
    public MasterDtos.LookupDto setLookupActive(int id, boolean active) {
        LookupEntity e = lookups.findById(id).orElseThrow(() -> new IllegalArgumentException("Lookup not found"));
        requireActiveCategoryForActiveLookup(e.getLookupType(), active);
        e.setActive(active ? 1 : 0);
        return lookupDto(lookups.saveAndFlush(e));
    }

    @Transactional(readOnly = true)
    public String nextLookupCode(String type) {
        String prefix = switch (type) {
            case "CATEGORY" -> "CAT";
            case "UNIT" -> "UNT";
            case "MATERIAL" -> "MAT";
            case "BRAND" -> "BRD";
            case "GST" -> "GST";
            default -> "GEN";
        };
        int max = 0;
        for (LookupEntity e : lookups.findByLookupTypeOrderByLookupCodeDesc(type)) {
            String c = e.getLookupCode();
            if (c != null && c.startsWith(prefix)) try {
                max = Math.max(max, Integer.parseInt(c.substring(prefix.length())));
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%03d", max + 1);
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.CategoryDto> categories() {
        return categories.findAllByOrderByDisplayOrderAscCategoryNameAsc().stream().map(c -> categoryDto(c, lookups.countByLookupType(c.getCategoryName()), lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(c.getCategoryName()).stream().filter(v -> v.getActive() == null || v.getActive() != 0).count())).toList();
    }

    @Transactional
    public MasterDtos.CategoryDto addCategory(String name) {
        String n = normal(name), code = code(n);
        if (categories.findByCategoryName(n).isPresent()) throw new IllegalArgumentException("Category already exists");
        MasterCategoryEntity e = new MasterCategoryEntity();
        e.setCategoryCode(code);
        e.setCategoryName(n);
        e.setDisplayOrder(0);
        e.setActive(1);
        return categoryDto(categories.saveAndFlush(e), 0, 0);
    }

    @Transactional
    public MasterDtos.CategoryDto renameCategory(String oldName, String newName) {
        MasterCategoryEntity c = categories.findByCategoryName(oldName).orElseThrow(() -> new IllegalArgumentException("Category not found"));
        String n = normal(newName);
        List<LookupEntity> vals = lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(oldName);
        if (!oldName.equalsIgnoreCase(n) && categories.findByCategoryName(n).isPresent()) {
            throw new IllegalArgumentException("Category already exists");
        }
        c.setCategoryName(n);
        for (LookupEntity l : vals) l.setLookupType(n);
        lookups.saveAllAndFlush(vals);
        categories.saveAndFlush(c);
        return categoryDto(c, vals.size(), vals.stream().filter(v -> v.getActive() == null || v.getActive() != 0).count());
    }

    @Transactional
    public MasterDtos.CategoryDto setCategoryActive(String name, boolean active) {
        MasterCategoryEntity category = categories.findByCategoryName(name)
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        category.setActive(active ? 1 : 0);
        category = categories.saveAndFlush(category);
        List<LookupEntity> values = lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(category.getCategoryName());
        long activeCount = values.stream().filter(value -> value.getActive() == null || value.getActive() != 0).count();
        return categoryDto(category, values.size(), activeCount);
    }

    @Transactional
    public void deleteCategory(String name) {
        MasterCategoryEntity category = categories.findByCategoryName(name)
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        List<LookupEntity> values = lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(name);
        // Retire the category and all of its values atomically. Existing transactions keep their
        // historical text, while active-only APIs stop offering these values for future use.
        category.setActive(0);
        for (LookupEntity value : values) value.setActive(0);
        if (!values.isEmpty()) lookups.saveAllAndFlush(values);
        categories.saveAndFlush(category);
    }

    private List<String> lookupUsage(LookupEntity lookup) {
        String type = normal(lookup.getLookupType());
        String code = categories.findByCategoryName(lookup.getLookupType())
            .map(MasterCategoryEntity::getCategoryCode).map(this::normal).orElse(type.replace(' ', '_'));
        String value = lookup.getLookupValue() == null ? "" : lookup.getLookupValue().trim();
        List<String> usage = new ArrayList<>();
        switch (code) {
            case "CATEGORY" -> addUsage(usage, countText("item_master", "category", value), "Item Master category");
            case "BRAND" -> addUsage(usage, countText("item_master", "brand", value), "Item Master brand");
            case "MATERIAL" -> addUsage(usage, countText("item_master", "material", value), "Item Master material");
            case "UNIT", "UOM" -> addUsage(usage, countText("item_master", "unit", value), "Item Master unit");
            case "EXPENSE_CATEGORY" -> addUsage(usage, countText("finance_register", "category", value), "Expense records");
            case "PAYMENT_MODE" -> {
                addUsage(usage, countText("finance_register", "payment_mode", value), "Finance records");
                addUsage(usage, countText("payment_record", "payment_mode", value), "Payment records");
            }
            case "PAYMENT_TERMS" -> addUsage(usage, countText("sales_header", "payment_terms", value), "Sales invoices");
            case "GST_TYPE" -> addUsage(usage, countText("sales_header", "gst_type", value), "Sales invoices");
            case "CHARGES" -> {
                addUsage(usage, countText("sales_header", "charge_type", value), "Sales charge headers");
                long chargeUses = countText("sales_charge", "charge_name", value);
                if (lookup.getLookupCode() != null && !lookup.getLookupCode().isBlank()) {
                    chargeUses += countText("sales_charge", "charge_code", lookup.getLookupCode());
                }
                addUsage(usage, chargeUses, "Sales charges");
            }
            case "GST" -> addUsage(usage, countNumber("item_master", "gst", value), "Item Master GST");
            case "DISCOUNT" -> addUsage(usage, countNumber("item_master", "discount_percent", value), "Item Master discount");
            default -> { }
        }
        return usage;
    }

    private void addUsage(List<String> usage, long count, String label) {
        if (count > 0) usage.add(label + ": " + count);
    }

    private long countText(String table, String column, String value) {
        Number n = (Number) entityManager.createNativeQuery(
            "select count(*) from " + table + " where upper(trim(coalesce(" + column + ",''))) = upper(trim(:value))")
            .setParameter("value", value).getSingleResult();
        return n.longValue();
    }

    private long countNumber(String table, String column, String value) {
        try {
            double parsed = Double.parseDouble(value.replace("%", "").trim());
            Number n = (Number) entityManager.createNativeQuery(
                "select count(*) from " + table + " where " + column + " = :value")
                .setParameter("value", parsed).getSingleResult();
            return n.longValue();
        } catch (Exception ignored) {
            return 0;
        }
    }

    @Transactional
    public void saveItems(List<MasterDtos.ItemDto> rows) {
        for (MasterDtos.ItemDto d : rows) {
            ItemEntity e = items.findByItemCode(d.itemCode()).orElseGet(ItemEntity::new);
            copy(d, e, e.getId() == null);
            items.save(e);
        }
    }

    @Transactional
    public MasterDtos.CategoryDto upsertCategory(MasterDtos.CategoryUpsertRequest d) {
        String code = normal(d.code());
        MasterCategoryEntity e = categories.findByCategoryCode(code).orElseGet(MasterCategoryEntity::new);
        e.setCategoryCode(code); e.setCategoryName(normal(d.name())); e.setDescription(d.description());
        if (e.getActive() == null) e.setActive(1); if (e.getDisplayOrder() == null) e.setDisplayOrder(0);
        e = categories.save(e);
        List<LookupEntity> values = lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(e.getCategoryName());
        return categoryDto(e, values.size(), values.stream().filter(v -> v.getActive() == null || v.getActive() != 0).count());
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
        if ("SUPPLIER".equals(normal(d.partyType())) && (d.email() == null || d.email().isBlank())) {
            throw new IllegalArgumentException("Supplier email is required");
        }
        if (includeCode) {
            e.setPartyType(normal(d.partyType()));
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
        return new MasterDtos.PartyDto(e.getId(), e.getPartyType(), e.getPartyCode(), e.getName(), e.getContactPerson(), e.getPhone(), e.getEmail(), e.getGstin(), e.getAddress(), n(e.getOpeningBalance()), e.getActive() == null || e.getActive() != 0);
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
        e.setGst(d.gst());
        e.setDiscountPercent(d.discountPercent());
        e.setPurchasePrice(d.purchasePrice());
        e.setSellingPrice(d.sellingPrice());
        e.setOpeningStock(d.openingStock());
        e.setMinimumStock(d.minimumStock());
        e.setLocation(d.location());
        e.setRemarks(d.remarks());
        if (e.getReservedStock() == null) e.setReservedStock(d.reservedStock());
        if (e.getActive() == null) e.setActive(d.active() ? 1 : 0);
    }

    private MasterDtos.ItemDto itemDto(ItemEntity e) {
        return new MasterDtos.ItemDto(e.getId(), e.getItemCode(), e.getDescription(), e.getCategory(), e.getBrand(), e.getMaterial(), e.getSize(), e.getUnit(), e.getHsn(), n(e.getGst()), n(e.getDiscountPercent()), n(e.getPurchasePrice()), n(e.getSellingPrice()), n(e.getOpeningStock()), n(e.getMinimumStock()), n(e.getReservedStock()), e.getLocation(), e.getRemarks(), e.getActive() == null || e.getActive() != 0);
    }

    private void copy(MasterDtos.LookupDto d, LookupEntity e) {
        e.setLookupType(normal(d.lookupType()));
        e.setLookupCode(normal(d.lookupCode()));
        e.setLookupValue(d.lookupValue() == null ? null : d.lookupValue().trim());
        e.setDescription(d.description());
        e.setDisplayOrder(d.displayOrder());
        e.setActive(d.active() ? 1 : 0);
    }

    private void validateLookup(MasterDtos.LookupDto d) {
        if (d == null || d.lookupType() == null || d.lookupType().isBlank()) throw new IllegalArgumentException("Lookup type is required");
        if (d.lookupCode() == null || d.lookupCode().isBlank()) throw new IllegalArgumentException("Lookup code is required");
        if (d.lookupValue() == null || d.lookupValue().isBlank()) throw new IllegalArgumentException("Lookup value is required");
        if (lookups.duplicateCode(d.lookupType(), d.lookupCode(), d.id())) throw new IllegalArgumentException("This lookup code already exists in " + d.lookupType());
        if (lookups.duplicateValue(d.lookupType(), d.lookupValue(), d.id())) throw new IllegalArgumentException("This lookup value already exists in " + d.lookupType());
    }

    private MasterDtos.LookupDto lookupDto(LookupEntity e) {
        return new MasterDtos.LookupDto(e.getId(), e.getLookupType(), e.getLookupCode(), e.getLookupValue(), e.getDescription(), e.getDisplayOrder() == null ? 0 : e.getDisplayOrder(), e.getActive() == null || e.getActive() != 0);
    }

    private MasterDtos.CategoryDto categoryDto(MasterCategoryEntity e, long count, long activeCount) {
        return new MasterDtos.CategoryDto(e.getId(), e.getCategoryCode(), e.getCategoryName(), e.getDescription(), e.getDisplayOrder() == null ? 0 : e.getDisplayOrder(), e.getActive() == null || e.getActive() != 0, count, activeCount);
    }

    private double n(Double v) {
        return v == null ? 0 : v;
    }
}
