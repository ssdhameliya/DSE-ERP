package org.example.dao;

import org.example.database.DatabaseManager;
import org.example.model.Item;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    // Insert new item
    public void save(Item item) {
        String sql = """
            INSERT INTO item_master (
                item_code, description, category, brand, material, size, unit,
                hsn, gst, discount_percent, purchase_price, selling_price, opening_stock,
                minimum_stock, location, remarks
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, item.getItemCode());
            ps.setString(2, item.getDescription());
            ps.setString(3, item.getCategory());
            ps.setString(4, item.getBrand());
            ps.setString(5, item.getMaterial());
            ps.setString(6, item.getSize());
            ps.setString(7, item.getUnit());
            ps.setString(8, item.getHsn());
            ps.setDouble(9, item.getGst());
            ps.setDouble(10, item.getDiscountPercent());
            ps.setDouble(11, item.getPurchasePrice());
            ps.setDouble(12, item.getSellingPrice());
            ps.setDouble(13, item.getOpeningStock());
            ps.setDouble(14, item.getMinimumStock());
            ps.setString(15, item.getLocation());
            ps.setString(16, item.getRemarks());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Insert failed", e);
        }
    }

    // Update existing item
    public void update(Item item) {
        String sql = """
            UPDATE item_master SET
                description=?, category=?, brand=?, material=?, size=?, unit=?,
                hsn=?, gst=?, discount_percent=?, purchase_price=?, selling_price=?, opening_stock=?,
                minimum_stock=?, location=?, remarks=?
            WHERE item_code=?
        """;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, item.getDescription());
            ps.setString(2, item.getCategory());
            ps.setString(3, item.getBrand());
            ps.setString(4, item.getMaterial());
            ps.setString(5, item.getSize());
            ps.setString(6, item.getUnit());
            ps.setString(7, item.getHsn());
            ps.setDouble(8, item.getGst());
            ps.setDouble(9, item.getDiscountPercent());
            ps.setDouble(10, item.getPurchasePrice());
            ps.setDouble(11, item.getSellingPrice());
            ps.setDouble(12, item.getOpeningStock());
            ps.setDouble(13, item.getMinimumStock());
            ps.setString(14, item.getLocation());
            ps.setString(15, item.getRemarks());
            ps.setString(16, item.getItemCode());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Update failed", e);
        }
    }

    // Delete by numeric ID
    public void delete(int id) {
        String sql = "DELETE FROM item_master WHERE id=?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Delete failed", e);
        }
    }

    // Delete by item code
    public void deleteByCode(String itemCode) {
        String sql = "DELETE FROM item_master WHERE item_code=?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Delete by code failed", e);
        }
    }

    // Get all items
    public List<Item> getAll() {
        List<Item> list = new ArrayList<>();
        String sql = "SELECT * FROM item_master ORDER BY item_code";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Item item = new Item();
                item.setItemCode(rs.getString("item_code"));
                item.setDescription(rs.getString("description"));
                item.setCategory(rs.getString("category"));
                item.setBrand(rs.getString("brand"));
                item.setMaterial(rs.getString("material"));
                item.setSize(rs.getString("size"));
                item.setUnit(rs.getString("unit"));
                item.setHsn(rs.getString("hsn"));
                item.setGst(rs.getDouble("gst"));
                item.setDiscountPercent(rs.getDouble("discount_percent"));
                item.setPurchasePrice(rs.getDouble("purchase_price"));
                item.setSellingPrice(rs.getDouble("selling_price"));
                item.setOpeningStock(rs.getDouble("opening_stock"));
                item.setMinimumStock(rs.getDouble("minimum_stock"));
                item.setLocation(rs.getString("location"));
                item.setReservedStock(rs.getDouble("reserved_stock"));
                item.setRemarks(rs.getString("remarks"));
                list.add(item);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed", e);
        }
        return list;
    }

    // Check existence by code
    public boolean existsByCode(String code) {
        String sql = "SELECT 1 FROM item_master WHERE item_code=?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new IllegalStateException("Exists check failed", e);
        }
    }

    // Save or update depending on existence
    public void saveOrUpdate(Item item) {
        if (existsByCode(item.getItemCode())) {
            update(item);
        } else {
            save(item);
        }
    }

    // Generate next code (simple ITM### pattern)
    public String nextCode() {
        String sql = "SELECT MAX(item_code) AS max_code FROM item_master";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getString("max_code") != null) {
                String maxCode = rs.getString("max_code");
                int num = Integer.parseInt(maxCode.replaceAll("\\D+", ""));
                return String.format("ITM%03d", num + 1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Next code generation failed", e);
        }
        return "ITM001";
    }
}
