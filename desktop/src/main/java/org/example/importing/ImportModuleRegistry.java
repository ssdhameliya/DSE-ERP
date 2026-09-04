package org.example.importing;

import org.example.util.BusinessClock;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Immutable metadata for the desktop import wizard.
 *
 * <p>This class deliberately contains no JavaFX state.  It is the single source
 * of truth for module fields, required identifiers, field types, target screens
 * and sample rows used by the import UI and template generator.</p>
 */
public final class ImportModuleRegistry {
    private ImportModuleRegistry() { }

    private static final List<String> ITEM_FIELDS = List.of(
        "item_code", "description", "category", "brand", "material", "size", "unit", "hsn", "gst",
        "discount_percent", "purchase_price", "selling_price", "remarks", "opening_stock", "minimum_stock", "location"
    );
    private static final List<String> CUSTOMER_FIELDS = List.of(
        "party_code", "name", "contact_person", "phone", "email", "gstin", "address", "opening_balance", "is_active"
    );
    private static final List<String> SUPPLIER_FIELDS = List.of(
        "party_code", "name", "contact_person", "phone", "email", "gstin", "address", "opening_balance", "is_active"
    );
    private static final List<String> PURCHASE_DOCUMENT_FIELDS = List.of(
        "invoice_no", "invoice_date", "party_code", "item_code", "quantity", "rate", "gst_percent", "gst_type",
        "payment_terms", "paid_amount", "remarks",
        "charge_1_type", "charge_1_amount", "charge_1_taxable", "charge_1_gst_percent",
        "charge_2_type", "charge_2_amount", "charge_2_taxable", "charge_2_gst_percent",
        "additional_charges", "attachment_file", "attachment_files"
    );
    private static final List<String> SALES_DOCUMENT_FIELDS = List.of(
        "invoice_no", "invoice_date", "party_code", "item_code", "quantity", "rate", "gst_percent", "gst_type",
        "payment_terms", "paid_amount", "remarks",
        "charge_1_type", "charge_1_amount", "charge_1_taxable", "charge_1_gst_percent",
        "charge_2_type", "charge_2_amount", "charge_2_taxable", "charge_2_gst_percent", "attachment_file"
    );
    private static final List<String> MASTER_FIELDS = List.of(
        "category_code", "category_name", "category_description", "value_code", "value", "value_description", "display_order", "is_active"
    );
    private static final List<String> PURCHASE_RECON_FIELDS = List.of(
        "supplier_name", "supplier_gstin", "supplier_invoice_no", "invoice_date", "taxable_value", "cgst", "sgst", "igst", "invoice_value"
    );
    private static final List<String> BANK_STATEMENT_FIELDS = List.of(
        "transaction_date", "value_date", "description", "reference", "amount", "direction", "balance"
    );

    public static List<String> fields(String module) {
        return switch (module == null ? "" : module) {
            case "Customers/CRM" -> CUSTOMER_FIELDS;
            case "Suppliers/HRM" -> SUPPLIER_FIELDS;
            case "Sales" -> SALES_DOCUMENT_FIELDS;
            case "Purchases" -> PURCHASE_DOCUMENT_FIELDS;
            case "Master Categories and Values" -> MASTER_FIELDS;
            case "Purchase Recon" -> PURCHASE_RECON_FIELDS;
            case "Bank Statement" -> BANK_STATEMENT_FIELDS;
            default -> ITEM_FIELDS;
        };
    }

    public static Set<String> requiredFields(String module) {
        return switch (module == null ? "" : module) {
            case "Customers/CRM" -> Set.of("party_code", "name");
            case "Suppliers/HRM" -> Set.of("party_code", "name", "email");
            case "Sales", "Purchases" -> Set.of("invoice_no", "invoice_date", "party_code", "item_code", "quantity", "rate");
            case "Master Categories and Values" -> Set.of("category_code", "category_name", "value_code", "value");
            case "Purchase Recon" -> Set.of("supplier_name", "supplier_invoice_no", "invoice_date", "invoice_value");
            case "Bank Statement" -> Set.of("transaction_date", "value_date", "description", "reference", "amount", "direction", "balance");
            default -> Set.of("item_code", "description", "unit", "hsn", "remarks");
        };
    }

    public static String dataType(String field) {
        return switch (field == null ? "" : field) {
            case "invoice_date" -> "Date";
            case "quantity", "rate", "gst", "gst_percent", "purchase_price", "selling_price", "opening_stock",
                 "minimum_stock", "opening_balance", "paid_amount", "charge_1_amount", "charge_1_gst_percent",
                 "charge_2_amount", "charge_2_gst_percent", "display_order", "taxable_value", "cgst", "sgst", "igst",
                 "invoice_value", "amount", "balance" -> "Number";
            case "is_active", "charge_1_taxable", "charge_2_taxable" -> "Boolean";
            case "email" -> "Email";
            case "phone" -> "Phone";
            default -> "Text";
        };
    }

    public static String humanize(String field) {
        if (field == null || field.isBlank()) return "";
        StringBuilder result = new StringBuilder();
        for (String word : field.split("_")) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            if (word.equalsIgnoreCase("gst") || word.equalsIgnoreCase("gstin") || word.equalsIgnoreCase("hsn")) {
                result.append(word.toUpperCase(Locale.ROOT));
            } else {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) result.append(word.substring(1));
            }
        }
        return result.toString();
    }

    public static String target(String module) {
        return switch (module == null ? "" : module) {
            case "Customers/CRM" -> "/fxml/pages/Customer.fxml";
            case "Suppliers/HRM" -> "/fxml/pages/Suppliers.fxml";
            case "Sales" -> "/fxml/pages/SalesList.fxml";
            case "Purchases" -> "/fxml/pages/PurchaseList.fxml";
            case "Master Categories and Values" -> "/fxml/pages/Masterdata.fxml";
            case "Purchase Recon" -> "/fxml/pages/PurchaseRecon.fxml";
            case "Bank Statement" -> "/fxml/pages/BankStatement.fxml";
            default -> "/fxml/pages/ItemMaster.fxml";
        };
    }

    public static String identifierGuidance(String module) {
        return switch (module == null ? "" : module) {
            case "Customers/CRM", "Suppliers/HRM" -> "party_code identifies the record to create, update or skip.";
            case "Sales", "Purchases" -> "invoice_no groups all item rows into one document; existing posted documents are protected.";
            case "Master Categories and Values" -> "category_code + value_code identify a reusable master value.";
            case "Purchase Recon" -> "Recon Supplier is matched by GSTIN first, then normalized name; Supplier Invoice No. + financial year protects against duplicate Purchase Recon records.";
            case "Bank Statement" -> "The source fingerprint and transaction row protect against duplicate imports.";
            default -> "item_code identifies the item to create, update or skip.";
        };
    }

    public static List<List<String>> exampleRows(String module) {
        if ("Sales".equals(module)) {
            return List.of(
                List.of("SAL-GST-0001", BusinessClock.formatDate(BusinessClock.today()), "CUS-0001", "ITEM-0001", "2", "1500", "18", "GST", "15 Days", "0", "Sample intra-state sale with two optional charges", "Freight", "250", "true", "18", "Packing", "100", "false", "0", ""),
                List.of("SAL-IGST-0002", BusinessClock.formatDate(BusinessClock.today()), "CUS-0002", "ITEM-0001", "1", "2000", "18", "IGST", "15 Days", "0", "Sample inter-state sale; attachment is optional", "", "", "", "", "", "", "", "", "")
            );
        }
        if ("Purchases".equals(module)) {
            return List.of(
                List.of("PUR-GST-0001", BusinessClock.formatDate(BusinessClock.today()), "SUP-0001", "ITEM-0001", "10", "1200", "18", "GST", "15 Days", "0", "Sample intra-state purchase with unlimited charge syntax", "Freight", "250", "true", "18", "Packing", "100", "false", "0", "Insurance|75|true|18;Handling|50|false|0", "", ""),
                List.of("PUR-IGST-0002", BusinessClock.formatDate(BusinessClock.today()), "SUP-0002", "ITEM-0001", "5", "1200", "18", "IGST", "15 Days", "0", "Sample inter-state purchase; multiple attachments are optional", "", "", "", "", "", "", "", "", "Freight|250|true|18", "", "invoice.pdf;quality-certificate.pdf")
            );
        }
        return List.of(exampleRow(module));
    }

    private static List<String> exampleRow(String module) {
        return switch (module == null ? "" : module) {
            case "Customers/CRM" -> List.of("CUS-0001", "ABC Enterprises", "Ravi Patel", "9876543210", "accounts@example.com", "24AAAAA1111A1Z5", "Ahmedabad, Gujarat", "0", "true");
            case "Suppliers/HRM" -> List.of("SUP-0001", "Steel Supplier Ltd", "Amit Shah", "9876500000", "sales@supplier.example", "24BBBBB2222B1Z4", "Rajkot, Gujarat", "0", "true");
            case "Sales" -> List.of("SAL-0001", "2026-07-28", "CUS-0001", "ITEM-0001", "2", "1500", "18", "GST", "15 Days", "0", "Sample sales invoice", "Freight", "250", "true", "18", "Packing", "100", "false", "0", "");
            case "Purchases" -> List.of("PUR-0001", "2026-07-28", "SUP-0001", "ITEM-0001", "10", "1200", "18", "GST", "15 Days", "0", "Sample purchase invoice", "Freight", "250", "true", "18", "Packing", "100", "false", "0", "");
            case "Purchase Recon" -> List.of("Shree Ram Engineering Works", "24APCPJ0791E1Z9", "25/26/61", BusinessClock.formatDate(BusinessClock.today()), "10620.00", "955.80", "955.80", "0.00", "12532.00");
            case "Master Categories and Values" -> List.of("UNIT", "Unit", "Units of measure", "UNT001", "Nos", "Number of items", "1", "true");
            default -> List.of("ITEM-0001", "MS Round Pipe", "Pipe", "Jasvi", "Mild Steel", "25 mm", "Nos", "73063000", "18", "0", "1200", "1500", "Sample item", "0", "10", "Main Warehouse");
        };
    }
}
