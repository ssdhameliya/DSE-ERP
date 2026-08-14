package org.example.invoice.mapper;

import org.example.config.ConfigManager;
import org.example.dao.ItemDAO;
import org.example.invoice.calculation.AmountInWordsConverter;
import org.example.invoice.calculation.InvoiceTaxCalculator;
import org.example.invoice.model.*;
import org.example.model.Item;
import org.example.model.Party;
import org.example.model.Sales;
import org.example.model.SalesLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SalesToTaxInvoiceMapper {
    private SalesToTaxInvoiceMapper() {}

    public static TaxInvoiceDocument map(Sales sale, String logoPath) {
        if (sale == null) throw new IllegalArgumentException("Sales invoice is required.");
        if (sale.getInvoiceNo() == null || sale.getInvoiceNo().isBlank()) {
            throw new IllegalArgumentException("Invoice number is required.");
        }
        if (sale.getInvoiceDate() == null) throw new IllegalArgumentException("Invoice date is required.");
        if (sale.getCustomer() == null) throw new IllegalArgumentException("Customer is required.");
        if (sale.getLines() == null || sale.getLines().isEmpty()) {
            throw new IllegalArgumentException("At least one invoice item is required.");
        }

        CompanyProfile company = company(logoPath);
        Party customer = sale.getCustomer();

        String billingAddress = firstNonBlank(sale.getBillingAddress(), customer.getAddress());
        String deliveryAddress = firstNonBlank(sale.getDeliveryAddress(), billingAddress);
        String billingGstin = firstNonBlank(sale.getBillingGstin(), sale.getGstin(), customer.getGstin());
        String deliveryGstin = firstNonBlank(sale.getDeliveryGstin(), billingGstin);

        InvoiceParty billing = new InvoiceParty(
                customer.getName(), billingAddress, billingGstin,
                firstNonBlank(sale.getContactPerson(), customer.getContactPerson()),
                firstNonBlank(sale.getContactPersonMobile(), customer.getPhone()));

        InvoiceParty delivery = new InvoiceParty(
                customer.getName(), deliveryAddress, deliveryGstin,
                firstNonBlank(sale.getContactPerson(), customer.getContactPerson()),
                firstNonBlank(sale.getContactPersonMobile(), customer.getPhone()));

        Map<String, Item> itemByCode = new HashMap<>();
        ItemDAO itemDAO = new ItemDAO();
        for (Item item : itemDAO.getAll()) {
            if (item != null && item.getItemCode() != null) {
                itemByCode.put(normalize(item.getItemCode()), item);
            }
        }

        List<TaxInvoiceItem> items = new ArrayList<>();
        int serial = 1;
        for (SalesLine line : sale.getLines()) {
            if (line == null) continue;
            Item masterItem = itemByCode.get(normalize(line.getItemCode()));
            String description = cleanDescription(line.getItemDescription(), line.getItemCode());
            items.add(new TaxInvoiceItem(
                    serial++, masterItem == null ? "" : safe(masterItem.getHsn()),
                    description, masterItem == null ? "" : safe(masterItem.getRemarks()), line.getQuantity(),
                    masterItem == null ? "Nos" : firstNonBlank(masterItem.getUnit(), "Nos"),
                    line.getRate(), line.getDiscountPercent(), line.getGstPercent()));
        }
        if (items.isEmpty()) throw new IllegalArgumentException("At least one valid invoice item is required.");

        List<TaxInvoiceCharge> charges = sale.getCharges().stream()
                .map(charge -> new TaxInvoiceCharge(charge.getChargeType(), charge.getAmount(), charge.isTaxable(), charge.getGstPercent()))
                .toList();
        InvoiceTotals totals = InvoiceTaxCalculator.calculate(items, charges, sale.getGstType());
        String words = "INR : " + AmountInWordsConverter.indianRupees(totals.grandTotal());

        String transporter = firstNonBlank(sale.getTransporter());
        return new TaxInvoiceDocument(
                company, sale.getInvoiceNo(), sale.getInvoiceDate(),
                firstNonBlank(sale.getOrderNo(), sale.getReferenceNo()), sale.getPoDate(),
                billing, delivery, transporter, sale.getTransporterGstin(), sale.getVehicleNumber(),
                firstNonBlank(sale.getContactPerson(), customer.getContactPerson()),
                firstNonBlank(sale.getContactPersonMobile(), customer.getPhone()),
                items, sale.getGstType(), charges, totals, words);
    }

    private static CompanyProfile company(String logoPath) {
        return new CompanyProfile(
                ConfigManager.get("company.name", ""),
                ConfigManager.get("company.address", ""),
                ConfigManager.get("company.gstin", ""),
                ConfigManager.get("company.email", ""),
                ConfigManager.get("company.alternateEmail", ""),
                ConfigManager.get("company.phone", ""),
                ConfigManager.get("payment.bankName", ""),
                ConfigManager.get("payment.branch", ""),
                ConfigManager.get("payment.accountNumber", ""),
                ConfigManager.get("payment.ifsc", ""),
                ConfigManager.get("payment.accountType", ""),
                ConfigManager.get("payment.mode", ""),
                ConfigManager.get("company.terms", ""),
                logoPath,
                ConfigManager.get("company.signaturePath", ""),
                ConfigManager.get("company.certificationText", "AN ISO 9001 : 2015 COMPANY"));
    }

    private static String cleanDescription(String value, String code) {
        String text = safe(value);
        String itemCode = safe(code);
        if (!itemCode.isBlank() && text.startsWith(itemCode + " - ")) {
            return text.substring(itemCode.length() + 3).trim();
        }
        return text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static String normalize(String value) {
        return safe(value).toUpperCase(Locale.ROOT);
    }
}
