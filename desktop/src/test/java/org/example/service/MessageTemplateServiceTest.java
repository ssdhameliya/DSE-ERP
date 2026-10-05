package org.example.service;

import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.model.Party;
import org.example.model.Purchase;
import org.example.model.Sales;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageTemplateServiceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() throws Exception {
        WorkspaceManager.configure(tempDir);
        ConfigManager.load();
    }

    @Test
    void tokenSubstitutionReplacesAllPlaceholders() {
        String template = "Hello {CustomerName}, invoice {DocumentNo} is dated {DocumentDate}. Total: {TotalAmount}. Balance: {BalanceAmount}.";
        Map<String, String> tokens = Map.of(
            "CustomerName", "Acme Corp",
            "DocumentNo", "INV-101",
            "DocumentDate", "04/10/2026",
            "TotalAmount", "₹ 5,000.00",
            "BalanceAmount", "₹ 2,000.00"
        );
        String formatted = MessageTemplateService.format(template, tokens);
        assertEquals("Hello Acme Corp, invoice INV-101 is dated 04/10/2026. Total: ₹ 5,000.00. Balance: ₹ 2,000.00.", formatted);
    }

    @Test
    void defaultTemplatesContainStandardPlaceholders() {
        for (MessageTemplateService.DocumentType type : MessageTemplateService.DocumentType.values()) {
            String subject = MessageTemplateService.defaultEmailSubject(type);
            String body = MessageTemplateService.defaultEmailBody(type);
            assertNotNull(subject);
            assertNotNull(body);
            assertTrue(subject.contains("{DocumentNo}") || subject.contains("{CompanyName}"));
            assertTrue(body.contains("{CustomerName}") || body.contains("{DocumentNo}"));
            assertTrue(body.contains("{Signature}"));
        }
    }

    @Test
    void formatSalesEmailUsesTokensAndSignature() {
        Sales sale = new Sales();
        sale.setInvoiceNo("INV-2026-999");
        sale.setInvoiceDate(LocalDate.of(2026, 10, 4));
        sale.setTotalAmount(15000.0);
        sale.setPaidAmount(5000.0);
        Party customer = new Party();
        customer.setName("Alpha Traders");
        sale.setCustomer(customer);

        MessageTemplateService.FormattedMessage msg = MessageTemplateService.formatSalesEmail(sale);
        assertNotNull(msg);
        assertNotNull(msg.subject());
        assertNotNull(msg.body());
        assertTrue(msg.subject().contains("INV-2026-999"));
        assertTrue(msg.body().contains("Alpha Traders"));
        assertTrue(msg.body().contains("INV-2026-999"));
    }

    @Test
    void formatPurchaseEmailUsesTokens() {
        Purchase purchase = new Purchase();
        purchase.setInvoiceNo("PO-2026-555");
        purchase.setInvoiceDate(LocalDate.of(2026, 10, 4));
        purchase.setTotalAmount(45000.0);
        purchase.setPaidAmount(0.0);
        Party supplier = new Party();
        supplier.setName("Mega Supplies Ltd");
        purchase.setSupplier(supplier);

        MessageTemplateService.FormattedMessage msg = MessageTemplateService.formatPurchaseEmail(purchase);
        assertNotNull(msg);
        assertTrue(msg.subject().contains("PO-2026-555"));
        assertTrue(msg.body().contains("Mega Supplies Ltd"));
    }

    @Test
    void customTemplateOverridePersistsAndFormats() {
        MessageTemplateService.setEmailTemplate(
            MessageTemplateService.DocumentType.SALES_INVOICE,
            "Custom Invoice Subject: {DocumentNo}",
            "Hi {CustomerName}, you owe {BalanceAmount} on {DocumentNo}."
        );
        ConfigManager.save();

        Sales sale = new Sales();
        sale.setInvoiceNo("INV-CUSTOM-1");
        sale.setTotalAmount(1200.0);
        sale.setPaidAmount(0.0);
        Party customer = new Party();
        customer.setName("Beta Co");
        sale.setCustomer(customer);

        MessageTemplateService.FormattedMessage msg = MessageTemplateService.formatSalesEmail(sale);
        assertEquals("Custom Invoice Subject: INV-CUSTOM-1", msg.subject());
        assertTrue(msg.body().contains("Hi Beta Co"));
        assertTrue(msg.body().contains("INV-CUSTOM-1"));
    }
}
