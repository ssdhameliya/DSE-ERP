package org.example.server.runtime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.example.server.persistence.JpaNativeRepository;

import javax.sql.DataSource;

/**
 * Applies the security and financial-integrity upgrade exactly once per shared
 * PostgreSQL database. The migration is kept separate from the base schema so
 * existing installations are upgraded without replaying data cleanup at every launch.
 */
@Component
public final class SecurityFinancialMigrationRunner implements ApplicationRunner {
    private static final String MIGRATION = "V5_1_18__security_financial_integrity";
    private final DataSource dataSource;
    private final JpaNativeRepository database;
    private final TransactionTemplate transaction;

    public SecurityFinancialMigrationRunner(DataSource dataSource, JpaNativeRepository database,
                                            PlatformTransactionManager transactionManager) {
        this.dataSource = dataSource;
        this.database = database;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments arguments) {
        transaction.executeWithoutResult(status -> {
            database.query("SELECT pg_advisory_xact_lock(?)", (row, index) -> row.getObject(1), 51018001L);
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

            ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
                    new ClassPathResource("db/migration/V5_1_18__security_financial_integrity.sql"));
            database.update("INSERT INTO dse_schema_migration(migration_key) VALUES (?)", MIGRATION);
        });
    }
}
