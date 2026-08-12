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
    private static final String MIGRATION = "V5_1_18__security_financial_integrity";
    private static final String MIGRATION_RESOURCE =
            "db/migration/V5_1_18__security_financial_integrity.sql";
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
        List<String> statements = loadStatements();
        transaction.executeWithoutResult(status -> {
            database.query("SELECT pg_advisory_xact_lock(?)",
                    (row, index) -> row.getObject(1), MIGRATION_LOCK);
            database.execute("""
                    CREATE TABLE IF NOT EXISTS dse_schema_migration (
                        migration_key VARCHAR(160) PRIMARY KEY,
                        applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            Long applied = database.queryForObject(
                    "SELECT COUNT(*) FROM dse_schema_migration WHERE migration_key=?",
                    Long.class, MIGRATION);
            if (applied != null && applied > 0) return;

            statements.forEach(database::execute);
            database.update("INSERT INTO dse_schema_migration(migration_key) VALUES (?)", MIGRATION);
        });
    }

    private static List<String> loadStatements() throws IOException {
        ClassPathResource resource = new ClassPathResource(MIGRATION_RESOURCE);
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
}
