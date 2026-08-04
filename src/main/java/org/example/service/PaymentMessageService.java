package org.example.service;

import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;
import org.example.model.Purchase;
import org.example.model.PurchaseLine;
import org.example.model.Sales;
import org.example.model.SalesLine;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds consistent, database-backed WhatsApp messages for ERP documents. */
public final class PaymentMessageService {

    private static final DateTimeFormatter MESSAGE_DATE =
        DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.ENGLISH);

    /* Unicode escapes keep the WhatsApp template stable on every Windows/JDK encoding. */
    private static final String DIVIDER = "\u2501".repeat(18);
    private static final String WAVE = "\uD83D\uDC4B";
    private static final String INVOICE = "\uD83E\uDDFE";
    private static final String PACKAGE = "\uD83D\uDCE6";
    private static final String CHECK = "\u2714";
    private static final String PAYMENT = "\uD83D\uDCB3";
    private static final String THANKS = "\uD83D\uDE4F";
    private static final String GREEN = "\uD83D\uDFE2";
    private static final String ORANGE = "\uD83D\uDFE0";
    private static final String RED = "\uD83D\uDD34";
    private static final String BLUE = "\uD83D\uDD35";

    private PaymentMessageService() {}

    /** Creates the customer-facing sales invoice message. */
    public static String salesMessage(Sales sale) {
        List<Item> items = new ArrayList<>();
        if (sale.getLines() != null) {
            for (SalesLine line : sale.getLines()) {
                items.add(new Item(line.getItemDescription(), line.getQuantity()));
            }
        }
        return build(new MessageData(
            sale.getCustomer() == null ? "Customer" : sale.getCustomer().getName(),
            "Invoice Summary",
            "Invoice No",
            sale.getInvoiceNo(),
            sale.getInvoiceDate(),
            sale.getTotalAmount(),
            sale.getPaidAmount(),
            sale.getBalanceAmount(),
            sale.getPaymentStatus(),
            "Purchased Items",
            items,
            "Thank you for shopping with us.\n\nYour order has been successfully completed."
        ));
    }

    /** Creates the supplier-facing purchase invoice message. */
    public static String purchaseMessage(Purchase purchase) {
        List<Item> items = new ArrayList<>();
        if (purchase.getLines() != null) {
            for (PurchaseLine line : purchase.getLines()) {
                items.add(new Item(line.getItemDescription(), line.getQuantity()));
            }
        }
        return build(new MessageData(
            purchase.getSupplier() == null ? "Supplier" : purchase.getSupplier().getName(),
            "Purchase Summary",
            "Purchase No",
            purchase.getInvoiceNo(),
            purchase.getInvoiceDate(),
            purchase.getTotalAmount(),
            purchase.getPaidAmount(),
            purchase.getBalanceAmount(),
            purchase.getPaymentStatus(),
            "Purchased Items",
            items,
            "Thank you for supplying our order.\n\nThe purchase has been successfully recorded."
        ));
    }

    /** Loads a quotation and its item rows directly from the connected database. */
    public static String quotationMessage(int quotationId) throws Exception {
        String party = "Customer";
        String number = "";
        String status = "DRAFT";
        LocalDate date = null;
        double total = 0;
        List<Item> items = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement header = connection.prepareStatement(
                 "SELECT q.quotation_no,q.quotation_date,q.total_amount,q.status,p.name " +
                     "FROM quotation_header q JOIN party_master p ON p.id=q.customer_id WHERE q.id=?");
             PreparedStatement lines = connection.prepareStatement(
                 "SELECT COALESCE(i.description,l.item_code),l.quantity " +
                     "FROM quotation_line l LEFT JOIN item_master i ON i.item_code=l.item_code " +
                     "WHERE l.quotation_id=? ORDER BY l.id")) {
            header.setInt(1, quotationId);
            try (ResultSet result = header.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("Quotation was not found. Refresh the register and try again.");
                }
                number = safe(result.getString(1));
                date = parseDate(result.getString(2));
                total = result.getDouble(3);
                status = safe(result.getString(4));
                party = safe(result.getString(5));
            }
            lines.setInt(1, quotationId);
            try (ResultSet result = lines.executeQuery()) {
                while (result.next()) {
                    items.add(new Item(result.getString(1), result.getDouble(2)));
                }
            }
        }
        return build(new MessageData(
            party, "Quotation Summary", "Quotation No", number, date,
            total, 0, total, status, "Quoted Items", items,
            "Thank you for your enquiry.\n\nYour quotation is ready for review."
        ));
    }

    /** Returns the QR asset saved on Settings > Payment & Bank, when readable. */
    public static Path configuredQrPath() {
        String configured = ConfigManager.get("payment.qrImagePath", "").trim();
        if (configured.isBlank()) return null;
        try {
            Path path = Path.of(configured);
            if (!path.isAbsolute()) path = ConfigManager.getConfigFolder().resolve(path);
            path = path.normalize().toAbsolutePath();
            return Files.isRegularFile(path) ? path : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String missingPaymentConfiguration() {
        String upi = ConfigManager.get("payment.upiId", "").trim();
        String account = ConfigManager.get("payment.accountNumber", "").trim();
        return upi.isBlank() && account.isBlank()
            ? "Payment details are not configured. Open Settings \u2192 Payment & Bank Details and enter a UPI ID or bank account details."
            : null;
    }

    private static String build(MessageData data) {
        String company = ConfigManager.get("company.name", "DSE Engineers").trim();
        String upi = ConfigManager.get("payment.upiId", "").trim();
        StringBuilder message = new StringBuilder();
        message.append("Hello *").append(safe(data.party())).append("* ").append(WAVE).append("\n\n")
            .append(data.introduction()).append("\n\n")
            .append(DIVIDER).append("\n\n")
            .append(INVOICE).append(" *").append(data.summaryTitle()).append("*\n\n")
            .append(data.numberLabel()).append(" : ").append(safe(data.number())).append("\n\n")
            .append("Date : ").append(formatDate(data.date())).append("\n\n")
            .append("Total Amount : ").append(money(data.total())).append("\n\n")
            .append("Paid : ").append(money(data.paid())).append("\n\n")
            .append("Balance : ").append(money(data.balance())).append("\n\n")
            .append("Status : ").append(status(data.status(), data.balance(), data.paid())).append("\n\n")
            .append(DIVIDER).append("\n\n")
            .append(PACKAGE).append(" *").append(data.itemsTitle()).append("*\n\n");

        if (data.items().isEmpty()) {
            message.append("No item details available\n");
        } else {
            for (Item item : data.items()) {
                message.append(CHECK).append(' ').append(safe(item.description()))
                    .append(" (").append(quantity(item.quantity())).append(")\n\n");
            }
        }

        /* Show configured payment details even when a document is already fully paid. */
        if (!upi.isBlank()) {
            message.append(DIVIDER).append("\n\n")
                .append(PAYMENT).append(" *Payment*\n\n")
                .append("UPI ID\n\n*").append(upi).append("*\n\n")
                .append("Outstanding Amount\n\n*").append(money(data.balance())).append("*\n\n")
                .append("Pay using UPI: ")
                .append(upiLink(upi, company, data.balance(), data.number())).append("\n\n");
            if (configuredQrPath() != null) {
                message.append("Payment QR scanner is included with this WhatsApp document.\n\n");
            }
        }

        return message.append(DIVIDER).append("\n\n")
            .append(THANKS).append(" Thank you for your business.\n\n")
            .append("Have a wonderful day! Looking forward to more business with you.\n\n")
            .append("Regards,\n*").append(company).append("*")
            .toString();
    }

    private static String status(String raw, double balance, double paid) {
        if (balance <= 0.005) return GREEN + " Completed";
        if (paid > 0.005) return ORANGE + " Partially Paid";
        String value = safe(raw).toUpperCase(Locale.ROOT);
        if (value.contains("ACCEPT")) return GREEN + " Accepted";
        if (value.contains("REJECT") || value.contains("EXPIRE") || value.contains("CANCEL")) {
            return RED + " " + titleCase(value);
        }
        if (value.contains("DRAFT")) return BLUE + " Draft";
        return BLUE + " Pending";
    }

    private static String titleCase(String value) {
        if (value.isBlank()) return "Pending";
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String money(double amount) {
        return "\u20B9" + String.format(Locale.of("en", "IN"), "%,.2f", amount);
    }

    private static String quantity(double value) {
        return Math.rint(value) == value
            ? String.format(Locale.ROOT, "%.0f", value)
            : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatDate(LocalDate value) {
        return value == null ? "Not available" : MESSAGE_DATE.format(value);
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "Not available" : value.trim();
    }

    private static String upiLink(String upi, String name, double amount, String reference) {
        return "upi://pay?pa=" + encode(upi) + "&pn=" + encode(name) + "&am="
            + String.format(Locale.ROOT, "%.2f", Math.max(0, amount))
            + "&cu=INR&tn=" + encode("Document " + reference);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Item(String description, double quantity) {}

    private record MessageData(
        String party, String summaryTitle, String numberLabel, String number,
        LocalDate date, double total, double paid, double balance, String status,
        String itemsTitle, List<Item> items, String introduction
    ) {}
}
