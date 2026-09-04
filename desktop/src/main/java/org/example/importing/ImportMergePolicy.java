package org.example.importing;

import org.example.model.Item;
import org.example.model.Party;

/** Merge rules for safe UPDATE_NON_BLANK imports. */
public final class ImportMergePolicy {
    private ImportMergePolicy() { }

    public static void applyPartyIdentity(Party incoming, Party existing) {
        if (existing == null) return;
        incoming.setId(existing.getId());
        incoming.setRowVersion(existing.getRowVersion());
    }

    public static void mergePartyNonBlank(Party incoming, Party existing) {
        if (existing == null) return;
        applyPartyIdentity(incoming, existing);
        if (blank(incoming.getName())) incoming.setName(existing.getName());
        if (blank(incoming.getContactPerson())) incoming.setContactPerson(existing.getContactPerson());
        if (blank(incoming.getPhone())) incoming.setPhone(existing.getPhone());
        if (blank(incoming.getEmail())) incoming.setEmail(existing.getEmail());
        if (blank(incoming.getGstin())) incoming.setGstin(existing.getGstin());
        if (blank(incoming.getAddress())) incoming.setAddress(existing.getAddress());
    }

    public static void applyItemIdentity(Item incoming, Item existing) {
        if (existing == null) return;
        incoming.setId(existing.getId());
        incoming.setRowVersion(existing.getRowVersion());
        // Opening Stock is a creation baseline. Existing inventory changes must use Stock Adjustment.
        incoming.setOpeningStock(existing.getOpeningStock());
        incoming.setReservedStock(existing.getReservedStock());
    }

    public static void mergeItemNonBlank(Item incoming, Item existing) {
        if (existing == null) return;
        applyItemIdentity(incoming, existing);
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

    public static void validateItem(Item item) {
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
}
