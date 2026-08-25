package org.example.service;

import org.example.api.master.MasterApiClient;
import java.util.List;
import java.util.Locale;

/** Master-category service backed exclusively by the typed Spring master API. */
public class MasterCategoryService {
    private final MasterApiClient api = new MasterApiClient();
    public record Category(String code, String name, boolean active, long valueCount, long activeValueCount, long rowVersion) {}

    public List<Category> getAll() {
        return api.categories().stream().map(c -> new Category(c.categoryCode(), c.categoryName(), c.active(), c.valueCount(), c.activeValueCount(), c.rowVersion())).toList();
    }
    public void add(String name) { api.addCategory(normalize(name)); }
    public void rename(String oldName, String newName, long rowVersion) { api.renameCategory(oldName, normalize(newName), rowVersion); }
    public void delete(String name, long rowVersion) { api.deleteCategory(name, rowVersion); }
    public void setActive(String name, boolean active, long rowVersion) { api.setCategoryActive(name, active, rowVersion); }
    private String normalize(String v) { return v == null ? "" : v.trim().toUpperCase(Locale.ROOT); }
}
