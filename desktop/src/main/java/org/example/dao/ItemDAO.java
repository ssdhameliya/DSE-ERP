package org.example.dao;

import org.example.api.master.MasterApiClient;
import org.example.model.Item;
import java.util.List;

/** Compatibility DAO backed by the typed Spring master-data API. */
public class ItemDAO {
    private final MasterApiClient api = new MasterApiClient();

    public void save(Item item) { api.saveItem(item); }
    public void update(Item item) { api.updateItem(item); }
    public void delete(int id) {
        Item item = getAll().stream().filter(x -> x.getId() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + id));
        api.deleteItem(item.getItemCode());
    }
    public void deleteByCode(String itemCode) { api.deleteItem(itemCode); }
    public List<Item> getAll() { return api.items(); }
    public boolean existsByCode(String code) { return api.itemExists(code); }
    public void saveOrUpdate(Item item) { if (existsByCode(item.getItemCode())) update(item); else save(item); }
    public String nextCode() { return api.nextItemCode(); }
}
