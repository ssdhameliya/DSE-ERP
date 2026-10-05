package org.example.service;

import org.example.config.ConfigManager;
import org.example.model.Purchase;
import org.example.model.Sales;
import org.example.util.BusinessClock;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Manages user-configurable email and WhatsApp message templates, signatures,
 * and dynamic token substitutions for business documents.
 */
public final class MessageTemplateService {

    public enum DocumentType {
        SALES_INVOICE("sale", "Sales Invoice"),
        QUOTATION("quote", "Quotation"),
        PURCHASE_BILL("purchase", "Purchase Bill"),
        PAYMENT_RECEIPT("payment", "Payment Receipt"),
        SALES_RETURN("sales_return", "Sales Return");

        private final String key;
        private final String displayName;

        DocumentType(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public String key() {
            return key;
        }

        public String displayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public record FormattedMessage(String subject, String body) {}

    private MessageTemplateService() {}

    /**
     * Replaces dynamic tokens such as {CustomerName}, {DocumentNo}, {TotalAmount}, etc.
     */
    public static String format(String template, Map<String, String> tokens) {
        if (template == null || template.isBlank()) return "";
        String result = template;
        if (tokens != null) {
            for (Map.Entry<String, String> entry : tokens.entrySet()) {
                String token = entry.getKey();
                String value = entry.getValue() == null ? "" : entry.getValue();
                result = result.replace("{" + token + "}", value);
            }
        }
        return result;
    }

    public static String getSignature() {
        return ConfigManager.get("email.signature", defaultSignature());
    }

    public static void setSignature(String signature) {
        ConfigManager.setWithoutSaving("email.signature", signature == null ? "" : signature.trim());
    }

    public static String getSignatureImagePath() {
        return ConfigManager.get("email.signature.image", "").trim();
    }

    public static void setSignatureImagePath(String path) {
        ConfigManager.setWithoutSaving("email.signature.image", path == null ? "" : path.trim());
    }

    public static String defaultSignature() {
        String company = BrandingService.companyName();
        String phone = ConfigManager.get("company.phone", "").trim();
        String email = ConfigManager.get("company.email", "").trim();
        StringBuilder sb = new StringBuilder();
        sb.append("Regards,\n").append(company);
        if (!phone.isBlank() || !email.isBlank()) {
            sb.append("\n");
            if (!phone.isBlank()) sb.append("Phone: ").append(phone);
            if (!phone.isBlank() && !email.isBlank()) sb.append(" | ");
            if (!email.isBlank()) sb.append("Email: ").append(email);
        }
        return sb.toString();
    }

    public static String getEmailSubject(DocumentType type) {
        return ConfigManager.get("email.template." + type.key() + ".subject", defaultEmailSubject(type));
    }

    public static String getEmailBody(DocumentType type) {
        return ConfigManager.get("email.template." + type.key() + ".body", defaultEmailBody(type));
    }

    public static void setEmailTemplate(DocumentType type, String subject, String body) {
        ConfigManager.setWithoutSaving("email.template." + type.key() + ".subject", subject == null ? "" : subject.trim());
        ConfigManager.setWithoutSaving("email.template." + type.key() + ".body", body == null ? "" : body.trim());
    }

    public static String getWhatsappBody(DocumentType type) {
        return ConfigManager.get("whatsapp.template." + type.key() + ".body", defaultWhatsappBody(type));
    }

    public static void setWhatsappTemplate(DocumentType type, String body) {
        ConfigManager.setWithoutSaving("whatsapp.template." + type.key() + ".body", body == null ? "" : body.trim());
    }

    public static String defaultEmailSubject(DocumentType type) {
        return switch (type) {
            case SALES_INVOICE -> "Sales Invoice {DocumentNo} from {CompanyName}";
            case QUOTATION -> "Quotation {DocumentNo} from {CompanyName}";
            case PURCHASE_BILL -> "Purchase Order {DocumentNo} from {CompanyName}";
            case PAYMENT_RECEIPT -> "Payment Receipt for {DocumentNo} from {CompanyName}";
            case SALES_RETURN -> "Sales Return Note {DocumentNo} from {CompanyName}";
        };
    }

    public static String defaultEmailBody(DocumentType type) {
        return switch (type) {
            case SALES_INVOICE -> """
                Dear {CustomerName},

                Please find attached your sales invoice {DocumentNo} dated {DocumentDate}.

                Invoice Summary:
                • Invoice Number: {DocumentNo}
                • Invoice Date: {DocumentDate}
                • Total Amount: {TotalAmount}
                • Balance Due: {BalanceAmount}

                Thank you for your business.

                {Signature}""";
            case QUOTATION -> """
                Dear {CustomerName},

                Thank you for your enquiry. Please find attached our quotation {DocumentNo} dated {DocumentDate} for total amount {TotalAmount}.

                We look forward to serving you. Please let us know if you need any adjustments or further information.

                {Signature}""";
            case PURCHASE_BILL -> """
                Dear {CustomerName},

                Please find attached our purchase order {DocumentNo} dated {DocumentDate}.

                Kindly confirm receipt and proceed with fulfillment per agreed terms.

                {Signature}""";
            case PAYMENT_RECEIPT -> """
                Dear {CustomerName},

                Thank you for your payment. We have successfully received payment against document {DocumentNo}.

                Receipt Summary:
                • Document Number: {DocumentNo}
                • Receipt Date: {DocumentDate}
                • Amount Paid: {PaidAmount}
                • Remaining Balance: {BalanceAmount}

                Thank you for your business.

                {Signature}""";
            case SALES_RETURN -> """
                Dear {CustomerName},

                Please find attached sales return / credit note {DocumentNo} dated {DocumentDate}.

                • Total Credit: {TotalAmount}

                {Signature}""";
        };
    }

    public static String defaultWhatsappBody(DocumentType type) {
        return switch (type) {
            case SALES_INVOICE -> "Hello *{CustomerName}*,\n\nPlease find attached sales invoice *{DocumentNo}* dated *{DocumentDate}* for total amount *{TotalAmount}* (Balance: *{BalanceAmount}*).\n\nThank you for your business!\n\nRegards,\n*{CompanyName}*";
            case QUOTATION -> "Hello *{CustomerName}*,\n\nPlease find attached quotation *{DocumentNo}* dated *{DocumentDate}* for total amount *{TotalAmount}*.\n\nLooking forward to your feedback!\n\nRegards,\n*{CompanyName}*";
            case PURCHASE_BILL -> "Hello *{CustomerName}*,\n\nPlease find attached purchase order *{DocumentNo}* dated *{DocumentDate}*.\n\nRegards,\n*{CompanyName}*";
            case PAYMENT_RECEIPT -> "Hello *{CustomerName}*,\n\nWe have received payment of *{PaidAmount}* against *{DocumentNo}* (Balance: *{BalanceAmount}*).\n\nThank you!\n\n*{CompanyName}*";
            case SALES_RETURN -> "Hello *{CustomerName}*,\n\nPlease find attached sales return note *{DocumentNo}* for amount *{TotalAmount}*.\n\n*{CompanyName}*";
        };
    }

    public static FormattedMessage formatSalesEmail(Sales sale) {
        Map<String, String> tokens = buildTokens(
            sale.getCustomer() == null ? "Customer" : sale.getCustomer().getName(),
            sale.getInvoiceNo(),
            sale.getInvoiceDate(),
            sale.getTotalAmount(),
            sale.getPaidAmount(),
            sale.getBalanceAmount()
        );
        String subject = format(getEmailSubject(DocumentType.SALES_INVOICE), tokens);
        String body = format(getEmailBody(DocumentType.SALES_INVOICE), tokens);
        return new FormattedMessage(subject, body);
    }

    public static FormattedMessage formatPurchaseEmail(Purchase purchase) {
        Map<String, String> tokens = buildTokens(
            purchase.getSupplier() == null ? "Supplier" : purchase.getSupplier().getName(),
            purchase.getInvoiceNo(),
            purchase.getInvoiceDate(),
            purchase.getTotalAmount(),
            purchase.getPaidAmount(),
            purchase.getBalanceAmount()
        );
        String subject = format(getEmailSubject(DocumentType.PURCHASE_BILL), tokens);
        String body = format(getEmailBody(DocumentType.PURCHASE_BILL), tokens);
        return new FormattedMessage(subject, body);
    }

    public static FormattedMessage formatQuotationEmail(String quoteNo, String customerName, LocalDate date, double amount) {
        Map<String, String> tokens = buildTokens(
            customerName == null || customerName.isBlank() ? "Customer" : customerName,
            quoteNo,
            date,
            amount,
            0.0,
            amount
        );
        String subject = format(getEmailSubject(DocumentType.QUOTATION), tokens);
        String body = format(getEmailBody(DocumentType.QUOTATION), tokens);
        return new FormattedMessage(subject, body);
    }

    public static String formatSalesWhatsapp(Sales sale) {
        String configured = ConfigManager.get("whatsapp.template.sale.body", "").trim();
        if (configured.isBlank()) {
            return PaymentMessageService.salesMessage(sale);
        }
        Map<String, String> tokens = buildTokens(
            sale.getCustomer() == null ? "Customer" : sale.getCustomer().getName(),
            sale.getInvoiceNo(),
            sale.getInvoiceDate(),
            sale.getTotalAmount(),
            sale.getPaidAmount(),
            sale.getBalanceAmount()
        );
        return format(configured, tokens);
    }

    public static String formatQuotationWhatsapp(int quotationId, String quoteNo, String customerName, LocalDate date, double amount) {
        String configured = ConfigManager.get("whatsapp.template.quote.body", "").trim();
        if (configured.isBlank()) {
            return PaymentMessageService.quotationMessage(quotationId);
        }
        Map<String, String> tokens = buildTokens(
            customerName == null || customerName.isBlank() ? "Customer" : customerName,
            quoteNo,
            date,
            amount,
            0.0,
            amount
        );
        return format(configured, tokens);
    }

    public static Map<String, String> buildTokens(String customerName, String documentNo, LocalDate date,
                                                  double totalAmount, double paidAmount, double balanceAmount) {
        Map<String, String> tokens = new HashMap<>();
        String company = BrandingService.companyName();
        String phone = ConfigManager.get("company.phone", "").trim();
        String email = ConfigManager.get("company.email", "").trim();
        String signature = getSignature();
        // Replace company tokens inside signature as well
        signature = signature.replace("{CompanyName}", company)
                             .replace("{CompanyPhone}", phone)
                             .replace("{CompanyEmail}", email);

        tokens.put("CustomerName", customerName == null || customerName.isBlank() ? "Customer" : customerName);
        tokens.put("PartyName", customerName == null || customerName.isBlank() ? "Customer" : customerName);
        tokens.put("DocumentNo", documentNo == null ? "" : documentNo);
        tokens.put("DocumentDate", date == null ? "" : BusinessClock.formatDate(date));
        tokens.put("TotalAmount", money(totalAmount));
        tokens.put("PaidAmount", money(paidAmount));
        tokens.put("BalanceAmount", money(balanceAmount));
        tokens.put("CompanyName", company);
        tokens.put("CompanyPhone", phone);
        tokens.put("CompanyEmail", email);
        tokens.put("Signature", signature);
        return tokens;
    }

    public static Map<String, String> sampleTokens() {
        return buildTokens("Acme Enterprises", "INV-2026-0042", LocalDate.now(), 24500.00, 10000.00, 14500.00);
    }

    private static String money(double amount) {
        return "\u20B9" + String.format(Locale.of("en", "IN"), "%,.2f", amount);
    }
}
