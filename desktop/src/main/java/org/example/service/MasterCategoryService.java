package org.example.service;

import org.example.api.master.MasterApiClient;
import java.util.List;
import java.util.Locale;

/** Master-category service backed exclusively by the typed Spring master API. */
public class MasterCategoryService {
    private final MasterApiClient api = new MasterApiClient();
    public record Category(String code, String name, boolean active, long valueCount, long activeValueCount) {}

    public List<Category> getAll() {
        return api.categories().stream().map(c -> new Category(c.categoryCode(), c.categoryName(), c.active(), c.valueCount(), c.activeValueCount())).toList();
    }
    public void add(String name) { api.addCategory(normalize(name)); }
    public void rename(String oldName, String newName) { api.renameCategory(oldName, normalize(newName)); }
    public void delete(String name) { api.deleteCategory(name); }
    public void setActive(String name, boolean active) { api.setCategoryActive(name, active); }
    private String normalize(String v) { return v == null ? "" : v.trim().toUpperCase(Locale.ROOT); }
}
