package org.example.dao;

import org.example.database.DatabaseManager;
import org.example.model.Party;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PartyDAO {
    public void save(Party party) {
        String sql = "INSERT INTO party_master (party_type, party_code, name, contact_person, phone, email, gstin, address, opening_balance, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            setFields(ps, party);
            ps.executeUpdate();


        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save party", exception);
        }
    }


    public void update(Party party) {
        String sql = "UPDATE party_master SET name=?, contact_person=?, phone=?, email=?, gstin=?, address=?, opening_balance=?, is_active=? WHERE id=?";
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, party.getName());
            ps.setString(2, party.getContactPerson());
            ps.setString(3, party.getPhone());
            ps.setString(4, party.getEmail());
            ps.setString(5, party.getGstin());
            ps.setString(6, party.getAddress());
            ps.setDouble(7, party.getOpeningBalance());
            ps.setBoolean(8, party.isActive());
            ps.setInt(9, party.getId());
            ps.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update party", exception);
        }
    }

    public void delete(int id) {
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement("DELETE FROM party_master WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not delete party", exception);
        }
    }

    public List<Party> getByType(String type) {
        List<Party> parties = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT * FROM party_master WHERE party_type=? ORDER BY name")) {
            ps.setString(1, type);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Party p = new Party();
                p.setId(rs.getInt("id"));
                p.setPartyType(rs.getString("party_type"));
                p.setPartyCode(rs.getString("party_code"));
                p.setName(rs.getString("name"));
                p.setContactPerson(rs.getString("contact_person"));
                p.setPhone(rs.getString("phone"));
                p.setEmail(rs.getString("email"));
                p.setGstin(rs.getString("gstin"));
                p.setAddress(rs.getString("address"));
                p.setOpeningBalance(rs.getDouble("opening_balance"));
                p.setActive(rs.getBoolean("is_active"));
                parties.add(p);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load parties", exception);
        }
        return parties;
    }

    public String nextCode(String type) {
        String prefix = type.equals("CUSTOMER") ? "CUS" : "SUP";
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM party_master WHERE party_type=?")) {
            ps.setString(1, type);
            ResultSet rs = ps.executeQuery();
            return prefix + String.format("%03d", rs.next() ? rs.getInt(1) + 1 : 1);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not generate party code", exception);
        }
    }

    private void setFields(PreparedStatement ps, Party p) throws SQLException {
        ps.setString(1, p.getPartyType());
        ps.setString(2, p.getPartyCode());
        ps.setString(3, p.getName());
        ps.setString(4, p.getContactPerson());
        ps.setString(5, p.getPhone());
        ps.setString(6, p.getEmail());
        ps.setString(7, p.getGstin());
        ps.setString(8, p.getAddress());
        ps.setDouble(9, p.getOpeningBalance());
        ps.setBoolean(10, p.isActive());
    }

    public boolean existsByCode(String code) {
        String sql = "SELECT 1 FROM party_master WHERE party_code=?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not check party_code", e);
        }
    }

}
