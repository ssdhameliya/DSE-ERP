package org.example.server.master;

import org.example.server.persistence.entity.*;
import org.example.server.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import org.example.server.security.CurrentUser;

import java.util.*;

@Service
public class MasterDataService {
    private final PartyRepository parties;
    private final ItemRepository items;
    private final LookupRepository lookups;
    private final MasterCategoryRepository categories;

    public MasterDataService(PartyRepository p, ItemRepository i, LookupRepository l, MasterCategoryRepository c) {
        parties = p;
        items = i;
        lookups = l;
        categories = c;
    }

    @PostConstruct
    public void ensureFinanceMasterCategories() {
        ensureCategory("PAYMENT_MODE","PAYMENT MODE","Payment methods used by Bank, Expense and Invoice Payment",130);
        ensureCategory("EXPENSE_CATEGORY","EXPENSE CATEGORY","Expense classifications used by Expense Entry",140);
        ensureCategory("BANK_ACCOUNT","BANK ACCOUNT","Bank account master: lookup value = account number, description = bank name",150);
    }

    private void ensureCategory(String code,String name,String description,int order){
        if(categories.findByCategoryCode(code).isPresent()) return;
        MasterCategoryEntity e=new MasterCategoryEntity();e.setCategoryCode(code);e.setCategoryName(name);e.setDescription(description);e.setDisplayOrder(order);e.setActive(1);categories.save(e);
    }

    @Transactional(readOnly = true)
    public List<MasterDtos.PartyDto> parties(String type) {
        requirePartyAccess(type);
        return parties.findByPartyTypeOrderByNameAsc(normal(type)).stream().map(this::partyDto).toList();
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
        String t = normal(type), prefix = "CUSTOMER".equals(t) ? "CUS" : "SUP";
        return prefix + String.format("%03d", parties.countByPartyType(t) + 1);
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
        int max = 0;

        for (ItemEntity e : items.findAll()) {
            String s = e.getItemCode();

            if (s != null) {
                String n = s.replaceAll("\\D+", "");

                if (!n.isBlank()) {
                    try {
                        max = Math.max(max, Integer.parseInt(n));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return String.format("ITM%03d", max + 1);
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
        LookupEntity e = new LookupEntity();
        copy(d, e);
        return lookupDto(lookups.save(e));
    }

    @Transactional
    public MasterDtos.LookupDto updateLookup(MasterDtos.LookupDto d) {
        validateLookup(d);
        LookupEntity e = lookups.findById(req(d.id(), "Lookup id")).orElseThrow(() -> new IllegalArgumentException("Lookup not found"));
        copy(d, e);
        return lookupDto(e);
    }

    @Transactional
    public void deleteLookup(int id) {
        lookups.deleteById(id);
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
        return categories.findAllByOrderByDisplayOrderAscCategoryNameAsc().stream().map(c -> categoryDto(c, lookups.countByLookupType(c.getCategoryName()))).toList();
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
        return categoryDto(categories.save(e), 0);
    }

    @Transactional
    public MasterDtos.CategoryDto renameCategory(String oldName, String newName) {
        MasterCategoryEntity c = categories.findByCategoryName(oldName).orElseThrow(() -> new IllegalArgumentException("Category not found"));
        String n = normal(newName);
        List<LookupEntity> vals = lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(oldName);
        c.setCategoryName(n);
        for (LookupEntity l : vals) l.setLookupType(n);
        lookups.saveAll(vals);
        return categoryDto(c, vals.size());
    }

    @Transactional
    public void deleteCategory(String name) {
        lookups.deleteByLookupType(name);
        categories.findByCategoryName(name).ifPresent(categories::delete);
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
        return categoryDto(e, lookups.countByLookupType(e.getCategoryName()));
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

    private MasterDtos.CategoryDto categoryDto(MasterCategoryEntity e, long count) {
        return new MasterDtos.CategoryDto(e.getId(), e.getCategoryCode(), e.getCategoryName(), e.getDescription(), e.getDisplayOrder() == null ? 0 : e.getDisplayOrder(), e.getActive() == null || e.getActive() != 0, count);
    }

    private double n(Double v) {
        return v == null ? 0 : v;
    }
}
