package org.example;

import org.example.config.ConfigManager;
import org.example.dao.SalesDAO;
import org.example.database.DatabaseManager;
import org.example.model.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;

public final class SalesPersistenceSmoke {
    public static void main(String[] args) throws Exception {
        ConfigManager.load(); DatabaseManager.initialize();
        Party customer; Item item; double before;
        try (Connection c=DatabaseManager.getConnection(); Statement s=c.createStatement()) {
            try (ResultSet r=s.executeQuery("SELECT id,party_code,name FROM party_master WHERE party_type='CUSTOMER' ORDER BY id LIMIT 1")) {
                if(!r.next()) throw new IllegalStateException("Smoke test requires one customer");
                customer=new Party(); customer.setId(r.getInt(1)); customer.setPartyCode(r.getString(2)); customer.setName(r.getString(3));
            }
            try (ResultSet r=s.executeQuery("SELECT item_code,description,COALESCE(opening_stock,0),COALESCE(selling_price,1),COALESCE(gst,0) FROM item_master WHERE COALESCE(opening_stock,0)>=1 ORDER BY id LIMIT 1")) {
                if(!r.next()) throw new IllegalStateException("Smoke test requires one in-stock item");
                item=new Item(); item.setItemCode(r.getString(1)); item.setDescription(r.getString(2)); before=r.getDouble(3); item.setSellingPrice(r.getDouble(4)); item.setGst(r.getDouble(5));
            }
        }
        double rate=item.getSellingPrice()<=0?1:item.getSellingPrice();
        SalesLine line=new SalesLine(); line.setItemCode(item.getItemCode()); line.setItemDescription(item.getDescription()); line.setQuantity(1); line.setRate(rate); line.setGstPercent(item.getGst()); line.setNetAmount(rate); line.setGstAmount(rate*item.getGst()/100); line.setTotalAmount(line.getNetAmount()+line.getGstAmount());
        Sales sale=new Sales(); sale.setInvoiceNo("SMOKE-"+System.currentTimeMillis()); sale.setInvoiceDate(LocalDate.now()); sale.setDueDate(LocalDate.now().plusDays(15)); sale.setCustomer(customer); sale.setSubtotal(line.getNetAmount()); sale.setGstAmount(line.getGstAmount()); sale.setTotalAmount(line.getTotalAmount()); sale.setRemarks("Automated persistence smoke test"); sale.setSalesperson("Automated Test"); sale.setNotes("Safe database copy"); sale.setLines(List.of(line));
        SalesDAO dao=new SalesDAO(); dao.save(sale);
        Sales loaded=dao.getByInvoice(sale.getInvoiceNo());
        if(loaded==null||loaded.getLines()==null||loaded.getLines().size()!=1) throw new IllegalStateException("Saved sale could not be read back");
        try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("SELECT opening_stock FROM item_master WHERE item_code=?")){p.setString(1,item.getItemCode());try(ResultSet r=p.executeQuery()){if(!r.next()||Math.abs(r.getDouble(1)-(before-1))>.0001)throw new IllegalStateException("Stock was not reduced correctly");}}
        System.out.println("SALE_SMOKE_OK invoice="+sale.getInvoiceNo()+" total="+sale.getTotalAmount()+" stock="+before+"->"+(before-1));
    }
}
