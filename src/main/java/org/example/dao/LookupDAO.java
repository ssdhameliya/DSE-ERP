package org.example.dao;

import org.example.database.DatabaseManager;
import org.example.model.Lookup;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LookupDAO {

    /*
     * Save
     */
    public void save(Lookup lookup) {

        String sql = """
            INSERT INTO lookup_master
            (lookup_type,lookup_code,lookup_value,description,display_order,is_active)
            VALUES(?,?,?,?,?,?)
            """;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, lookup.getLookupType());
            ps.setString(2, lookup.getLookupCode());
            ps.setString(3, lookup.getLookupValue());
            ps.setString(4, lookup.getDescription());
            ps.setInt(5, lookup.getDisplayOrder());
            ps.setBoolean(6, lookup.isActive());

            ps.executeUpdate();

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    /*
     * Update
     */
    public void update(Lookup lookup) {

        String sql = """
            UPDATE lookup_master
            SET
                lookup_value=?,
                description=?,
                display_order=?,
                is_active=?
            WHERE id=?
            """;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, lookup.getLookupValue());
            ps.setString(2, lookup.getDescription());
            ps.setInt(3, lookup.getDisplayOrder());
            ps.setBoolean(4, lookup.isActive());
            ps.setInt(5, lookup.getId());

            ps.executeUpdate();

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    /*
     * Delete
     */
    public void delete(int id) {

        String sql = "DELETE FROM lookup_master WHERE id=?";

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);

            ps.executeUpdate();

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    /*
     * Load by Type
     */
    public List<Lookup> getByType(String type) {

        List<Lookup> list = new ArrayList<>();

        String sql = """
            SELECT *
            FROM lookup_master
            WHERE lookup_type=?
            ORDER BY display_order,lookup_value
            """;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, type);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                Lookup l = new Lookup();

                l.setId(rs.getInt("id"));
                l.setLookupType(rs.getString("lookup_type"));
                l.setLookupCode(rs.getString("lookup_code"));
                l.setLookupValue(rs.getString("lookup_value"));
                l.setDescription(rs.getString("description"));
                l.setDisplayOrder(rs.getInt("display_order"));
                l.setActive(rs.getBoolean("is_active"));

                list.add(l);

            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return list;

    }

    /*
     * Dropdown values
     */
    public List<String> getValues(String type) {

        List<String> list = new ArrayList<>();

        String sql = """
            SELECT lookup_value
            FROM lookup_master
            WHERE lookup_type=?
            AND is_active=1
            ORDER BY display_order
            """;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, type);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                list.add(rs.getString("lookup_value"));

            }

        } catch (Exception ex) {

            ex.printStackTrace();

        }

        return list;

    }

    /*
     * Next Code Generator
     */
    public String generateNextCode(String type) {

        String prefix = switch (type) {
            case "CATEGORY" -> "CAT";
            case "UNIT" -> "UNT";
            case "MATERIAL" -> "MAT";
            case "BRAND" -> "BRD";
            case "GST" -> "GST";
            default -> "GEN";
        };

        String sql = """
            SELECT lookup_code
            FROM lookup_master
            WHERE lookup_type = ?
            ORDER BY lookup_code DESC
            LIMIT 1
            """;

        try (
            Connection con = DatabaseManager.getConnection();
            PreparedStatement ps = con.prepareStatement(sql)
        ) {

            ps.setString(1, type);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {

                String lastCode = rs.getString("lookup_code");

                String numberPart = lastCode.substring(prefix.length());

                int next = Integer.parseInt(numberPart) + 1;

                return prefix + String.format("%03d", next);

            }

        } catch (Exception ex) {

            ex.printStackTrace();

        }

        return prefix + "001";

    }

}
