package org.example;

import org.example.config.ConfigManager;
import org.example.dao.SalesDAO;
import org.example.database.DatabaseManager;
import org.example.model.Party;
import org.example.model.Sales;
import org.example.model.SalesLine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

/** Isolated regression test for migrations and duplicate invoice allocation. */
public final class ApprovedPatchPersistenceSmoke {
    public static void main(String[] args) throws Exception {
        ConfigManager.load();
        DatabaseManager.initialize();
        int customerId;
        try (Connection c=DatabaseManager.getConnection(); Statement s=c.createStatement()) {
            s.executeUpdate("INSERT OR IGNORE INTO party_master(party_type,party_code,name,is_active) VALUES('CUSTOMER','TEST-CUSTOMER','Test Customer',1)");
            s.executeUpdate("INSERT OR IGNORE INTO party_master(party_type,party_code,name,is_active) VALUES('SUPPLIER','TEST-SUPPLIER','Test Supplier',1)");
            s.executeUpdate("INSERT OR IGNORE INTO item_master(item_code,description,gst,selling_price,opening_stock,minimum_stock) VALUES('TEST-ITEM','Test Item',18,100,100,5)");
            try(ResultSet r=s.executeQuery("SELECT id FROM party_master WHERE party_code='TEST-CUSTOMER'")){r.next();customerId=r.getInt(1);}
            s.executeUpdate("INSERT OR IGNORE INTO sales_header(invoice_no,invoice_date,customer_id,subtotal,gst_amount,total_amount) VALUES('SAL-00001',date('now'),"+customerId+",100,18,118)");
        }
        Party party=new Party();party.setId(customerId);party.setPartyCode("TEST-CUSTOMER");party.setName("Test Customer");
        SalesLine line=new SalesLine();line.setItemCode("TEST-ITEM");line.setItemDescription("Test Item");line.setQuantity(1);line.setRate(100);line.setGstPercent(18);line.recalculate();
        Sales sale=new Sales();sale.setInvoiceNo("SAL-00001");sale.setInvoiceDate(LocalDate.now());sale.setDueDate(LocalDate.now().plusDays(15));sale.setCustomer(party);sale.setSubtotal(100);sale.setGstAmount(18);sale.setTotalAmount(118);sale.setLines(List.of(line));
        new SalesDAO().save(sale);
        if("SAL-00001".equals(sale.getInvoiceNo()))throw new IllegalStateException("Duplicate number was not reallocated");
        try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM sales_header WHERE invoice_no=?")){p.setString(1,sale.getInvoiceNo());try(ResultSet r=p.executeQuery()){if(!r.next()||r.getInt(1)!=1)throw new IllegalStateException("Reallocated sale was not persisted");}}
        try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement()){
            assertTable(s,"permissions");assertTable(s,"role_permission");assertTable(s,"application_setting");
            try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM permissions")){if(!r.next()||r.getInt(1)<80)throw new IllegalStateException("Permission seed incomplete");}
        }
        System.out.println("APPROVED_PATCH_OK invoice="+sale.getInvoiceNo());
    }
    private static void assertTable(Statement s,String table)throws Exception{try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)){if(!r.next())throw new IllegalStateException(table+" unavailable");}}
}
