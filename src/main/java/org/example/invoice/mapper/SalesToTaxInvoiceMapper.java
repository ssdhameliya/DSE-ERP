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
        String customerGstin = firstNonBlank(sale.getGstin(), customer.getGstin());

        InvoiceParty billing = new InvoiceParty(
                customer.getName(), billingAddress, customerGstin,
                firstNonBlank(sale.getContactPerson(), customer.getContactPerson()),
                firstNonBlank(sale.getContactPersonMobile(), customer.getPhone()));

        InvoiceParty delivery = new InvoiceParty(
                customer.getName(), deliveryAddress, customerGstin,
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
                    description, line.getQuantity(),
                    masterItem == null ? "Nos" : firstNonBlank(masterItem.getUnit(), "Nos"),
                    line.getRate(), line.getDiscountPercent(), line.getGstPercent()));
        }
        if (items.isEmpty()) throw new IllegalArgumentException("At least one valid invoice item is required.");

        InvoiceTotals totals = InvoiceTaxCalculator.calculate(items, sale.getChargeAmount(), sale.getGstType());
        String words = "INR : " + AmountInWordsConverter.indianRupees(totals.grandTotal());

        String transporter = firstNonBlank(sale.getTransporter(), sale.getDoorDelivery());
        return new TaxInvoiceDocument(
                company, sale.getInvoiceNo(), sale.getInvoiceDate(),
                firstNonBlank(sale.getOrderNo(), sale.getReferenceNo()), sale.getPoDate(),
                billing, delivery, transporter, sale.getVehicleNumber(),
                firstNonBlank(sale.getContactPerson(), customer.getContactPerson()),
                items, sale.getGstType(), sale.getChargeAmount(), totals, words);
    }

    private static CompanyProfile company(String logoPath) {
        return new CompanyProfile(
                ConfigManager.get("company.name", "JASVI INDUSTRIES"),
                ConfigManager.get("company.address",
                        "52, Darshanvilla Park, Nr. Gopal Chowk, Bapasitaram Chowk, Nikol - Naroda, Ahmedabad, Gujarat, INDIA - 382345."),
                ConfigManager.get("company.gstin", "24ANXPD3352N1ZK"),
                ConfigManager.get("company.email", "jasviindustries1989@gmail.com"),
                ConfigManager.get("company.alternateEmail", "marketing@jasviindustries.in"),
                ConfigManager.get("company.phone", "+91 72280 99500"),
                ConfigManager.get("payment.bankName", "KOTAK MAHINDRA BANK LTD."),
                ConfigManager.get("payment.branch", "NIKOL"),
                ConfigManager.get("payment.accountNumber", "0745366171"),
                ConfigManager.get("payment.ifsc", "KKBK0002603"),
                ConfigManager.get("payment.accountType", "CURRENT"),
                ConfigManager.get("payment.mode", "AGAINST DELIVERY"),
                ConfigManager.get("company.terms", ""),
                logoPath);
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
