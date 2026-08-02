package org.example.service;

import org.example.dao.ItemDAO;
import org.example.model.Item;

import java.util.List;

public class ItemService {
    private final ItemDAO dao = new ItemDAO();

    public void save(Item item) {
        dao.save(item);
    }

    public void update(Item item) {
        dao.update(item);
    }

    public void delete(int id) {
        dao.delete(id);
    }

    public void delete(String itemCode) {
        dao.deleteByCode(itemCode);
    }

    public List<Item> getAll() {
        return dao.getAll();
    }

    // Generate next item code
    public String nextCode() {
        return dao.nextCode();
    }

    // Save or update depending on existence
    public void saveOrUpdate(Item item) {
        dao.saveOrUpdate(item);
    }

    // ✅ NEW: Check if item exists by code
    public boolean existsByCode(String code) {
        return dao.existsByCode(code);
    }
}
