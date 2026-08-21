package org.example.server.runtime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.example.server.persistence.JpaNativeRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the security and financial-integrity upgrade exactly once per shared
 * PostgreSQL database. Every statement runs through the application's
 * JPA/Hibernate-owned persistence boundary in one transaction.
 */
@Component
public final class SecurityFinancialMigrationRunner implements ApplicationRunner {
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration("V5_1_18__security_financial_integrity",
                    "db/migration/V5_1_18__security_financial_integrity.sql"),
            new Migration("V7_1_3__sale_gstin_details",
                    "db/migration/V7_1_3__sale_gstin_details.sql"),
            new Migration("V7_1_5__multiple_sales_charges",
                    "db/migration/V7_1_5__multiple_sales_charges.sql"),
            new Migration("V7_1_6__delivery_flag_and_notification_category",
                    "db/migration/V7_1_6__delivery_flag_and_notification_category.sql"),
            new Migration("V7_1_7__release_schema_repair",
                    "db/migration/V7_1_7__release_schema_repair.sql"),
            new Migration("V7_1_8__remove_po_date_format_master",
                    "db/migration/V7_1_8__remove_po_date_format_master.sql"),
            new Migration("V7_1_8_1__remove_legacy_auto_po_order",
                    "db/migration/V7_1_8_1__remove_legacy_auto_po_order.sql"),
            new Migration("V7_1_8_2__enforce_customer_po_reference",
                    "db/migration/V7_1_8_2__enforce_customer_po_reference.sql"),
            new Migration("V7_3_2__reminder_reliability",
                    "db/migration/V7_3_2__reminder_reliability.sql"),
            new Migration("V7_3_2_1__permission_catalog_alignment",
                    "db/migration/V7_3_2_1__permission_catalog_alignment.sql"),
            new Migration("V7_3_17__purchase_inventory_lifecycle",
                    "db/migration/V7_3_17__purchase_inventory_lifecycle.sql"),
            new Migration("V7_3_17_1__canonical_login_timestamp",
                    "db/migration/V7_3_17_1__canonical_login_timestamp.sql"),
            new Migration("V7_3_18__purchase_invoice_format",
                    "db/migration/V7_3_18__purchase_invoice_format.sql"),
            new Migration("V7_30_28__central_reference_and_quotation_attachment",
                    "db/migration/V7_30_28__central_reference_and_quotation_attachment.sql"),
            new Migration("V7_30_29__return_refunds_and_reference_cleanup",
                    "db/migration/V7_30_29__return_refunds_and_reference_cleanup.sql"),
            new Migration("V7_30_30__quotation_runtime_repair",
                    "db/migration/V7_30_30__quotation_runtime_repair.sql"),
            new Migration("V7_30_31__release_schema_guard",
                    "db/migration/V7_30_31__release_schema_guard.sql"),
            new Migration("V7_30_40__performance_indexes",
                    "db/migration/V7_30_40__performance_indexes.sql"),
            new Migration("V8_1_0__purchase_parity_excel_studio",
                    "db/migration/V8_1_0__purchase_parity_excel_studio.sql"),
            new Migration("V8_2_0__excel_purchase_documents",
                    "db/migration/V8_2_0__excel_purchase_documents.sql"),
            new Migration("V8_2_4__reminder_status_compatibility",
                    "db/migration/V8_2_4__reminder_status_compatibility.sql")
    );
    private static final long MIGRATION_LOCK = 51018001L;
    private final JpaNativeRepository database;
    private final TransactionTemplate transaction;

    public SecurityFinancialMigrationRunner(JpaNativeRepository database,
                                            PlatformTransactionManager transactionManager) {
        this.database = database;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments arguments) throws IOException {
        List<LoadedMigration> migrations = new ArrayList<>(MIGRATIONS.size());
        for (Migration migration : MIGRATIONS) {
            migrations.add(new LoadedMigration(migration.key(), loadStatements(migration.resource())));
        }
        transaction.executeWithoutResult(status -> {
            database.query("SELECT pg_advisory_xact_lock(?)",
                    (row, index) -> row.getObject(1), MIGRATION_LOCK);
            database.execute("""
                    CREATE TABLE IF NOT EXISTS dse_schema_migration (
                        migration_key VARCHAR(160) PRIMARY KEY,
                        applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            for (LoadedMigration migration : migrations) {
                Long applied = database.queryForObject(
                        "SELECT COUNT(*) FROM dse_schema_migration WHERE migration_key=?",
                        Long.class, migration.key());
                if (applied != null && applied > 0) continue;
                migration.statements().forEach(database::execute);
                database.update("INSERT INTO dse_schema_migration(migration_key) VALUES (?)", migration.key());
            }
            verifyRequiredSchema();
        });
    }

    /**
     * Refuses to advertise a healthy backend when a release-required column is
     * missing. This converts a later generic HTTP 500 into a precise startup
     * failure and protects every screen that depends on the upgraded schema.
     */
    private void verifyRequiredSchema() {
        requireColumn("sales_header", "same_as_billing");
        requireColumn("notifications", "category");
        requireColumn("reminder_register", "status");
        requireColumn("reminder_register", "snoozed_until");
        requireColumn("reminder_register", "completed_at");
        requireColumn("reminder_register", "created_by");
        requireColumn("reminder_register", "updated_at");
        requireColumn("purchase_header", "inventory_posted");
        requireColumn("users", "last_login_utc");
        requireTable("return_refund");
        requireColumn("return_refund", "attachment_path");
        requireColumn("return_refund", "bank_statement_transaction_id");
        requireColumn("quotation_header", "attachment_path");
        requireColumn("quotation_header", "follow_up_date");
        requireColumn("quotation_header", "converted_invoice_no");
        requireColumn("quotation_header", "discount_amount");
        requireColumn("quotation_line", "discount_percent");
        requireColumn("purchase_header", "billing_address");
        requireColumn("purchase_header", "delivery_address");
        requireColumn("purchase_header", "gst_type");
        requireColumn("purchase_header", "same_as_billing");
        requireTable("purchase_charge");
        requireTable("document_attachment");
    }

    private void requireTable(String table) {
        Long count = database.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """, Long.class, table);
        if (count == null || count == 0) {
            throw new IllegalStateException(
                    "Required database table is missing after migration: " + table);
        }
    }

    private void requireColumn(String table, String column) {
        Long count = database.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """, Long.class, table, column);
        if (count == null || count == 0) {
            throw new IllegalStateException(
                    "Required database column is missing after migration: " + table + "." + column);
        }
    }

    private static List<String> loadStatements(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        String script;
        try (InputStream input = resource.getInputStream()) {
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        return splitStatements(script);
    }

    /**
     * Splits the deliberately plain migration script without accepting
     * procedural blocks. Semicolons inside SQL string literals are preserved.
     */
    static List<String> splitStatements(String script) {
        StringBuilder withoutComments = new StringBuilder(script.length());
        for (String line : script.split("\\R", -1)) {
            int comment = line.indexOf("--");
            withoutComments.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }

        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        String cleaned = withoutComments.toString();
        for (int index = 0; index < cleaned.length(); index++) {
            char character = cleaned.charAt(index);
            if (character == '\'') {
                current.append(character);
                if (quoted && index + 1 < cleaned.length() && cleaned.charAt(index + 1) == '\'') {
                    current.append(cleaned.charAt(++index));
                } else {
                    quoted = !quoted;
                }
            } else if (character == ';' && !quoted) {
                addStatement(statements, current);
            } else {
                current.append(character);
            }
        }
        addStatement(statements, current);
        if (quoted) throw new IllegalArgumentException("Migration contains an unterminated SQL string literal");
        return List.copyOf(statements);
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) statements.add(statement);
        current.setLength(0);
    }

    private record Migration(String key, String resource) {}
    private record LoadedMigration(String key, List<String> statements) {}
}
