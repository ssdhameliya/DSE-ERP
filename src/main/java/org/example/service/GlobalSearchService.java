package org.example.service;

import org.example.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Performs one database-backed search across the ERP's operational modules. */
public final class GlobalSearchService {

    /** A result knows how it should be labelled and which screen should open. */
    public record SearchResult(String module, String reference, String description,
                               String detail, String targetFxml) {
        @Override public String toString() {
            return module + "  •  " + reference + "\n" + description +
                (detail == null || detail.isBlank() ? "" : "  •  " + detail);
        }
    }

    public List<SearchResult> search(String query) {
        String like = "%" + query.trim() + "%";
        List<SearchResult> results = new ArrayList<>();
        add(results, like, "Item", "/fxml/pages/ItemMaster.fxml",
            "SELECT item_code,description,COALESCE(category,'')||'  Stock: '||COALESCE(opening_stock,0) FROM item_master WHERE item_code LIKE ? OR description LIKE ? OR category LIKE ? OR brand LIKE ? LIMIT 20", 4);
        add(results, like, "Customer", "/fxml/pages/Customer.fxml",
            "SELECT party_code,name,COALESCE(phone,'')||'  '||COALESCE(gstin,'') FROM party_master WHERE party_type='CUSTOMER' AND (party_code LIKE ? OR name LIKE ? OR phone LIKE ? OR email LIKE ? OR gstin LIKE ?) LIMIT 20", 5);
        add(results, like, "Supplier", "/fxml/pages/Suppliers.fxml",
            "SELECT party_code,name,COALESCE(phone,'')||'  '||COALESCE(gstin,'') FROM party_master WHERE party_type='SUPPLIER' AND (party_code LIKE ? OR name LIKE ? OR phone LIKE ? OR email LIKE ? OR gstin LIKE ?) LIMIT 20", 5);
        add(results, like, "Sales Invoice", "/fxml/pages/SalesList.fxml",
            "SELECT s.invoice_no,p.name,s.invoice_date||'  ₹'||printf('%.2f',s.total_amount)||'  '||COALESCE(s.payment_status,'') FROM sales_header s JOIN party_master p ON p.id=s.customer_id WHERE s.invoice_no LIKE ? OR p.name LIKE ? LIMIT 20", 2);
        add(results, like, "Purchase Invoice", "/fxml/pages/PurchaseList.fxml",
            "SELECT s.invoice_no,p.name,s.invoice_date||'  ₹'||printf('%.2f',s.total_amount)||'  '||COALESCE(s.payment_status,'') FROM purchase_header s JOIN party_master p ON p.id=s.supplier_id WHERE s.invoice_no LIKE ? OR p.name LIKE ? OR COALESCE(s.reference_no,'') LIKE ? LIMIT 20", 3);
        add(results, like, "Quotation", "/fxml/pages/Quotations.fxml",
            "SELECT q.quotation_no,p.name,q.quotation_date||'  ₹'||printf('%,.2f',q.total_amount)||'  '||q.status FROM quotation_header q JOIN party_master p ON p.id=q.customer_id WHERE q.quotation_no LIKE ? OR p.name LIKE ? OR q.status LIKE ? LIMIT 20", 3);
        add(results, like, "Return", "/fxml/pages/Operations.fxml",
            "SELECT return_no,COALESCE(invoice_no,''),return_date||'  '||return_type||'  '||status FROM return_register WHERE return_no LIKE ? OR COALESCE(invoice_no,'') LIKE ? OR COALESCE(reason,'') LIKE ? LIMIT 20", 3);
        add(results, like, "Payment", "/fxml/pages/Operations.fxml",
            "SELECT COALESCE(reference_no,'Payment #'||id),document_type,payment_date||'  ₹'||printf('%,.2f',amount)||'  '||payment_mode FROM payment_record WHERE COALESCE(reference_no,'') LIKE ? OR document_type LIKE ? OR payment_mode LIKE ? LIMIT 20", 3);
        add(results, like, "Master Value", "/fxml/pages/Masterdata.fxml",
            "SELECT lookup_code,lookup_value,lookup_type||'  '||COALESCE(description,'') FROM lookup_master WHERE lookup_code LIKE ? OR lookup_value LIKE ? OR lookup_type LIKE ? OR COALESCE(description,'') LIKE ? LIMIT 20", 4);
        return results.stream().limit(100).toList();
    }

    private void add(List<SearchResult> destination, String like, String module, String target,
                     String sql, int parameterCount) {
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 1; i <= parameterCount; i++) ps.setString(i, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) destination.add(new SearchResult(module, safe(rs.getString(1)),
                    safe(rs.getString(2)), safe(rs.getString(3)), target));
            }
        } catch (Exception ignored) {
            // A module without data must not prevent results from other modules.
        }
    }

    private String safe(String value) { return value == null ? "" : value; }
}
