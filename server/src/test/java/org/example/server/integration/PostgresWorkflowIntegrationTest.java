package org.example.server.integration;

import org.example.server.returns.ReturnDtos;
import org.example.server.returns.ReturnService;
import org.example.server.reconciliation.BankReconciliationDtos;
import org.example.server.reconciliation.BankReconciliationService;
import org.example.server.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL workflow test. It is intentionally disabled unless CI (or a developer)
 * supplies DSE_IT_DB_URL so ordinary unit tests do not require Docker/PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "DSE_IT_DB_URL", matches = ".+")
class PostgresWorkflowIntegrationTest {
    private static final String INVOICE = "IT-SALE-9034";
    private static final String ITEM = "IT-ITEM-9034";
    private static final String PARTY = "IT-CUST-9034";
    private static final String BANK_SOURCE = "IT-BANK-SOURCE-9034";
    private static final String BANK_TX = "IT-BANK-TX-9034";

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("DSE_IT_DB_URL"));
        registry.add("spring.datasource.username", () -> env("DSE_IT_DB_USERNAME", "dse_it"));
        registry.add("spring.datasource.password", () -> env("DSE_IT_DB_PASSWORD", "dse_it_password"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("dse.backup.enabled", () -> "false");
        registry.add("dse.workspace.path", () -> System.getProperty("java.io.tmpdir") + "/dse-erp-it-workspace");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ReturnService returns;
    @Autowired BankReconciliationService bankReconciliation;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(90034, "integration-admin", "ADMIN"),
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
        SecurityContextHolder.clearContext();
    }

    @Test
    void approvedSalesReturnMovesStockAndTracksPartialThenFullRefund() {
        Integer partyId = jdbc.queryForObject(
                "INSERT INTO party_master(party_type,party_code,name,is_active) VALUES('CUSTOMER',?,?,1) RETURNING id",
                Integer.class, PARTY, "Integration Customer");
        assertNotNull(partyId);

        jdbc.update("INSERT INTO item_master(item_code,description,unit,gst,purchase_price,selling_price,opening_stock,minimum_stock,is_active) VALUES(?,?,?,?,?,?,?,?,1)",
                ITEM, "Integration Item", "Nos", 18d, 50d, 100d, 10d, 1d);

        Integer saleId = jdbc.queryForObject(
                "INSERT INTO sales_header(invoice_no,invoice_date,customer_id,subtotal,gst_amount,total_amount,paid_amount,payment_status,document_status,approval_status,inventory_posted,created_at,email_sent,whatsapp_sent,row_version) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,0) RETURNING id",
                Integer.class, INVOICE, LocalDate.now().toString(), partyId, 100d, 0d, 100d, 100d,
                "PAID", "APPROVED", "APPROVED", true, java.time.Instant.now().toString(), 0, 0);
        assertNotNull(saleId);

        jdbc.update("INSERT INTO sales_line(sales_id,item_code,quantity,rate,gst_percent,discount_percent,discount_amount,line_total,unit_cost_snapshot,item_description_snapshot,unit_snapshot) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                saleId, ITEM, 1d, 100d, 0d, 0d, 0d, 100d, 50d, "Integration Item", "Nos");

        double before = stock();
        ReturnDtos.Created created = returns.create(new ReturnDtos.CreateRequest(
                "SALES RETURN", INVOICE, partyId, LocalDate.now().toString(),
                List.of(new ReturnDtos.CreateLine(ITEM, 1d, 100d, "Integration lifecycle"))));
        assertNotNull(created.returnNo());
        assertEquals(before, stock(), 0.0001, "Pending approval must not move stock");
        assertSettlement(created.returnNo(), "RETURN APPROVAL PENDING", 0d);

        returns.approve(created.returnNo());
        assertEquals(before + 1d, stock(), 0.0001, "Approved Sales Return must restore stock");
        assertSettlement(created.returnNo(), "RETURN PENDING", 100d);

        returns.recordRefund(created.returnNo(), new ReturnDtos.RefundCreateRequest(
                LocalDate.now().toString(), 40d, "BANK", "IT-REF-1", "", "Integration Customer", "partial", "PARTIAL", "integration-admin"));
        assertSettlement(created.returnNo(), "RETURN PARTIAL", 60d);

        returns.recordRefund(created.returnNo(), new ReturnDtos.RefundCreateRequest(
                LocalDate.now().toString(), 60d, "BANK", "IT-REF-2", "", "Integration Customer", "final", "FINAL", "integration-admin"));
        assertSettlement(created.returnNo(), "RETURN PAID", 0d);

        assertThrows(IllegalStateException.class, () -> returns.cancel(created.returnNo(), true),
                "A financially settled Return must not be cancellable");
    }

    @Test
    void freshBankStatementImportCommitsOnceAndSameSourceIsIdempotent() {
        BankReconciliationDtos.ImportRequest request = new BankReconciliationDtos.ImportRequest(
                "Integration Bank", "IT-ACCOUNT-9034", "Integration Account",
                LocalDate.now().minusDays(1).toString(), LocalDate.now().toString(), "INR",
                1000d, 1125d, BANK_SOURCE, "integration-bank.csv",
                "Date,Description,Debit,Credit,Balance\n", "integration-admin", false,
                List.of(new BankReconciliationDtos.ImportRow(
                        2, LocalDate.now() + "T10:30:00", LocalDate.now().toString(), LocalDate.now().toString(),
                        "Integration customer receipt", "IT-BANK-REF", 0d, 125d, 1125d, BANK_TX)));

        BankReconciliationDtos.ImportResult first = bankReconciliation.importStatement(request);
        assertNotNull(first.batch());
        assertEquals(1, first.importedRows());
        assertEquals(0, first.duplicateRows());
        assertFalse(first.alreadyImported());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM bank_statement_transaction WHERE transaction_fingerprint=?", Integer.class, BANK_TX));

        BankReconciliationDtos.ImportResult second = bankReconciliation.importStatement(request);
        assertTrue(second.alreadyImported(), "The exact same statement source must resolve to the existing batch");
        assertEquals(0, second.importedRows());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM bank_statement_transaction WHERE transaction_fingerprint=?", Integer.class, BANK_TX));
    }

    private void assertSettlement(String returnNo, String expectedStatus, double expectedPending) {
        ReturnDtos.Settlement settlement = returns.settlements("SALES RETURN").stream()
                .filter(x -> INVOICE.equals(x.invoiceNo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No settlement row for " + returnNo));
        assertEquals(expectedStatus, settlement.status());
        assertEquals(expectedPending, settlement.pendingAmount(), 0.01);
    }

    private double stock() {
        Double value = jdbc.queryForObject("SELECT opening_stock FROM item_master WHERE item_code=?", Double.class, ITEM);
        return value == null ? 0d : value;
    }

    private void cleanup() {
        jdbc.update("DELETE FROM bank_reconciliation_audit WHERE statement_transaction_id IN (SELECT id FROM bank_statement_transaction WHERE transaction_fingerprint=?)", BANK_TX);
        jdbc.update("DELETE FROM bank_reconciliation_allocation WHERE statement_transaction_id IN (SELECT id FROM bank_statement_transaction WHERE transaction_fingerprint=?)", BANK_TX);
        jdbc.update("DELETE FROM bank_statement_transaction WHERE transaction_fingerprint=?", BANK_TX);
        jdbc.update("DELETE FROM bank_statement_import WHERE source_fingerprint=?", BANK_SOURCE);
        jdbc.update("DELETE FROM return_refund WHERE return_no IN (SELECT return_no FROM return_register WHERE invoice_no=?)", INVOICE);
        jdbc.update("DELETE FROM activity_log WHERE (entity_type IN ('SALE','SALES_RETURN') AND (detail LIKE ? OR detail LIKE ?))", "%" + INVOICE + "%", "%IT-%");
        jdbc.update("DELETE FROM return_register WHERE invoice_no=?", INVOICE);
        jdbc.update("DELETE FROM inventory_cost_ledger WHERE item_code=?", ITEM);
        jdbc.update("DELETE FROM inventory_cost_state WHERE item_code=?", ITEM);
        jdbc.update("DELETE FROM sales_line WHERE sales_id IN (SELECT id FROM sales_header WHERE invoice_no=?)", INVOICE);
        jdbc.update("DELETE FROM sales_header WHERE invoice_no=?", INVOICE);
        jdbc.update("DELETE FROM item_master WHERE item_code=?", ITEM);
        jdbc.update("DELETE FROM party_master WHERE party_code=?", PARTY);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " is required");
        return value;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
