package org.example.database;

import org.example.config.ConfigManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;

public class DatabaseManager {

    // Resolve the same configured SQLite file used by Backup & Restore.
    private static final Path DATABASE_PATH = ConfigManager.getDatabasePath();
    private static final String DB_FOLDER = DATABASE_PATH.getParent().toString();
    private static final String DB_FILE = DATABASE_PATH.toString();
    private static final String DB_URL = ConfigManager.getDbUrl();

    /**
     * Initialize database and create required tables.
     */
    public static void initialize() {

        File folder = new File(DB_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        migrateBundledDatabaseIfNeeded();

        createUsersTable();
        ensureUserColumns();
        createRolesTable();

        createItemMasterTable();
        ensureInventoryColumns();

        createLookupTable();
        createMasterCategoryTable();

        createPartyTable();

        createPurchaseTables();
        ensurePurchaseLineDiscountColumns();
        ensurePurchaseWorkflowColumns();

        createSalesTables();
        ensureSalesLineDiscountColumns();
        ensureSalesWorkflowColumns();

        createQuotationTables();
        ensureQuotationWorkflowColumns();
        createOperationsTables();
        ensureReminderWorkflowColumns();
        createWorkflowTables();
        createUserAccessTables();
        createBackupSettingsTable();
        createBackupHistoryTable();
        createApplicationMetadataTable();
        addColumnIfMissing("communication_log", "is_read", "INTEGER NOT NULL DEFAULT 0");
        createNotificationTable();
        createBusinessIndexes();
        ensureReturnLineStorage();
        migrateCompletedWhatsappHandoffs();

        seedLookupData();
        seedAdministrator();

    }

    private static void migrateBundledDatabaseIfNeeded() {
        Path target = Path.of(DB_FILE);
        if (Files.exists(target)) return;
        Path bundled = Path.of(System.getProperty("user.dir"), "JavaAppERP.db");
        if (!Files.exists(bundled)) return;
        try {
            Files.copy(bundled, target, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (Exception ex) {
            throw new IllegalStateException("Existing ERP database could not be migrated to " + target, ex);
        }
    }

    /**
     * Returns database connection.
     */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("SQLite JDBC driver is unavailable", ex);
        }
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Generic table creator.
     */
    private static void createTable(String sql) {
        try (
            Connection con = getConnection();
            Statement stmt = con.createStatement()
        ) {
            stmt.execute(sql);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    //====================================================
    // USERS TABLE
    //====================================================

    private static void createUsersTable() {
        createTable("""
            CREATE TABLE IF NOT EXISTS users
            (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                full_name TEXT,
                role TEXT NOT NULL DEFAULT 'USER',
                email TEXT UNIQUE,
                active INTEGER NOT NULL DEFAULT 1
            );
            """);
    }

    //====================================================
    // ITEM MASTER
    //====================================================

    private static void createItemMasterTable() {
        createTable("""
            CREATE TABLE IF NOT EXISTS item_master
            (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                item_code TEXT UNIQUE,
                description TEXT,
                category TEXT,
                brand TEXT,
                material TEXT,
                size TEXT,
                unit TEXT,
                hsn TEXT,
                gst REAL,
                discount_percent REAL NOT NULL DEFAULT 0,
                purchase_price REAL,
                selling_price REAL,
                opening_stock REAL,
                minimum_stock REAL,
                location TEXT,
                remarks TEXT
            );
            """);
    }

    private static void ensureInventoryColumns() {
        addColumnIfMissing("item_master", "reserved_stock", "REAL NOT NULL DEFAULT 0");
        addColumnIfMissing("item_master", "is_active", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing("item_master", "discount_percent", "REAL NOT NULL DEFAULT 0");
        createTable("""
            CREATE TABLE IF NOT EXISTS stock_adjustment (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                item_code TEXT NOT NULL,
                adjustment_date TEXT NOT NULL,
                adjustment_type TEXT NOT NULL,
                quantity REAL NOT NULL,
                reason TEXT NOT NULL,
                reference_no TEXT,
                created_by TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(item_code) REFERENCES item_master(item_code)
            )
            """);
        createTable("CREATE INDEX IF NOT EXISTS idx_stock_adjustment_item ON stock_adjustment(item_code, adjustment_date)");
    }

    private static void createLookupTable() {
        createTable("""
            CREATE TABLE IF NOT EXISTS lookup_master (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                lookup_type TEXT NOT NULL,
                lookup_code TEXT NOT NULL,
                lookup_value TEXT NOT NULL,
                description TEXT,
                display_order INTEGER DEFAULT 0,
                is_active INTEGER DEFAULT 1
            );
            """);
    }

    private static void createMasterCategoryTable() {
        createTable("""
            CREATE TABLE IF NOT EXISTS master_category (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category_code TEXT NOT NULL UNIQUE,
                category_name TEXT NOT NULL UNIQUE,
                description TEXT,
                display_order INTEGER NOT NULL DEFAULT 0,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        createTable("""
            INSERT OR IGNORE INTO master_category(category_code, category_name, display_order)
            SELECT DISTINCT UPPER(TRIM(lookup_type)), UPPER(TRIM(lookup_type)), 0
            FROM lookup_master WHERE TRIM(COALESCE(lookup_type,'')) <> ''
            """);
        addColumnIfMissing("lookup_master", "created_at", "TEXT");
        createTable("UPDATE lookup_master SET created_at=CURRENT_TIMESTAMP WHERE created_at IS NULL");
    }

    private static void seedAdministrator() {
        String sql = "INSERT INTO users(username,password,full_name,role,role_id,email,active) " +
            "SELECT 'admin','admin','Administrator','ADMIN',(SELECT id FROM roles WHERE role_name='ADMIN'),'shailesh.rockstar007@yahoo.com',1 " +
            "WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='admin')";
        createTable(sql);
    }

    private static void ensureUserColumns() {
        addColumnIfMissing("users", "email", "TEXT");
        addColumnIfMissing("users", "active", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing("users", "role_id", "INTEGER");
        addColumnIfMissing("users", "last_login", "TEXT");
        addColumnIfMissing("users", "department", "TEXT");
        addColumnIfMissing("users", "branch", "TEXT");
        addColumnIfMissing("users", "access_level", "TEXT NOT NULL DEFAULT 'STANDARD'");
        addColumnIfMissing("users", "locked", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("users", "failed_attempts", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("users", "mfa_enabled", "INTEGER NOT NULL DEFAULT 0");
        try (Connection con = getConnection(); Statement stmt = con.createStatement()) {
            stmt.executeUpdate("UPDATE users SET email='shailesh.rockstar007@yahoo.com', role='ADMIN', active=1 WHERE username='admin'");
        } catch (SQLException ignored) {
        }
    }

    private static void createRolesTable() {
        createTable("CREATE TABLE IF NOT EXISTS roles(id INTEGER PRIMARY KEY AUTOINCREMENT, role_name TEXT NOT NULL UNIQUE, description TEXT, active INTEGER NOT NULL DEFAULT 1)");
        // Normalize legacy ADMINISTRATOR installations to the concise ADMIN role.
        createTable("INSERT OR IGNORE INTO roles(role_name,description) VALUES('ADMIN','Full application access'),('MANAGER','Business management access'),('USER','Standard operational access')");
        createTable("UPDATE users SET role='ADMIN',role_id=(SELECT id FROM roles WHERE role_name='ADMIN') WHERE UPPER(role)='ADMINISTRATOR' OR role_id IN (SELECT id FROM roles WHERE role_name='ADMINISTRATOR')");
        createTable("UPDATE users SET role_id=(SELECT id FROM roles WHERE role_name=CASE UPPER(role) WHEN 'ADMIN' THEN 'ADMIN' WHEN 'MANAGER' THEN 'MANAGER' ELSE 'USER' END) WHERE role_id IS NULL");
        createTable("UPDATE roles SET active=0 WHERE role_name='ADMINISTRATOR'");
    }

    private static void addColumnIfMissing(String table, String column, String definition) {
        try (Connection con = getConnection();
             Statement stmt = con.createStatement();
             ResultSet columns = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next())
                if (column.equalsIgnoreCase(columns.getString("name"))) return;
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private static void createPartyTable() {
        createTable("""
            CREATE TABLE IF NOT EXISTS party_master (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                party_type TEXT NOT NULL,
                party_code TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                contact_person TEXT,
                phone TEXT,
                email TEXT,
                gstin TEXT,
                address TEXT,
                opening_balance REAL DEFAULT 0,
                is_active INTEGER DEFAULT 1
            );
            """);
    }

    private static void createPurchaseTables() {
        createTable("""
            CREATE TABLE IF NOT EXISTS purchase_header (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                invoice_no TEXT NOT NULL UNIQUE,
                invoice_date TEXT NOT NULL,
                supplier_id INTEGER NOT NULL,
                subtotal REAL NOT NULL,
                gst_amount REAL NOT NULL,
                total_amount REAL NOT NULL,
                remarks TEXT,
                FOREIGN KEY(supplier_id) REFERENCES party_master(id)
            );
            """);
        createTable("""
            CREATE TABLE IF NOT EXISTS purchase_line (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                purchase_id INTEGER NOT NULL,
                item_code TEXT NOT NULL,
                quantity REAL NOT NULL,
                rate REAL NOT NULL,
                gst_percent REAL NOT NULL,
                discount_percent REAL NOT NULL DEFAULT 0,
                discount_amount REAL NOT NULL DEFAULT 0,
                line_total REAL NOT NULL,
                FOREIGN KEY(purchase_id) REFERENCES purchase_header(id),
                FOREIGN KEY(item_code) REFERENCES item_master(item_code)
            );
            """);
    }

    private static void createSalesTables() {
        createTable("""
            CREATE TABLE IF NOT EXISTS sales_header (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                invoice_no TEXT NOT NULL UNIQUE,
                invoice_date TEXT NOT NULL,
                customer_id INTEGER NOT NULL,
                subtotal REAL NOT NULL,
                gst_amount REAL NOT NULL,
                total_amount REAL NOT NULL,
                remarks TEXT,
                FOREIGN KEY(customer_id) REFERENCES party_master(id)
            );
            """);
        createTable("""
            CREATE TABLE IF NOT EXISTS sales_line (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sales_id INTEGER NOT NULL,
                item_code TEXT NOT NULL,
                quantity REAL NOT NULL,
                rate REAL NOT NULL,
                gst_percent REAL NOT NULL,
                discount_percent REAL NOT NULL DEFAULT 0,
                discount_amount REAL NOT NULL DEFAULT 0,
                line_total REAL NOT NULL,
                FOREIGN KEY(sales_id) REFERENCES sales_header(id),
                FOREIGN KEY(item_code) REFERENCES item_master(item_code)
            );
            """);
    }


    private static void ensurePurchaseLineDiscountColumns() {
        addColumnIfMissing("purchase_line", "discount_percent", "REAL NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_line", "discount_amount", "REAL NOT NULL DEFAULT 0");
    }

    private static void ensureSalesLineDiscountColumns() {
        addColumnIfMissing("sales_line", "discount_percent", "REAL NOT NULL DEFAULT 0");
        addColumnIfMissing("sales_line", "discount_amount", "REAL NOT NULL DEFAULT 0");
        addColumnIfMissing("sales_header", "discount_amount", "REAL NOT NULL DEFAULT 0");
    }

    private static void ensurePurchaseWorkflowColumns() {
        addColumnIfMissing("purchase_header", "due_date", "TEXT");
        addColumnIfMissing("purchase_header", "delivery_date", "TEXT");
        addColumnIfMissing("purchase_header", "paid_amount", "REAL NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_header", "payment_status", "TEXT NOT NULL DEFAULT 'PENDING'");
        addColumnIfMissing("purchase_header", "document_status", "TEXT NOT NULL DEFAULT 'COMPLETED'");
        addColumnIfMissing("purchase_header", "email_sent", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_header", "warehouse", "TEXT");
        addColumnIfMissing("purchase_header", "payment_terms", "TEXT");
        addColumnIfMissing("purchase_header", "currency", "TEXT");
        addColumnIfMissing("purchase_header", "reference_no", "TEXT");
        addColumnIfMissing("purchase_header", "gst_treatment", "TEXT");
        addColumnIfMissing("purchase_header", "transporter", "TEXT");
        addColumnIfMissing("purchase_header", "lr_awb_no", "TEXT");
        addColumnIfMissing("purchase_header", "discount_type", "TEXT");
        addColumnIfMissing("purchase_header", "discount_amount", "REAL NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_header", "attachment_path", "TEXT");
        addColumnIfMissing("purchase_header", "created_by", "TEXT");
        addColumnIfMissing("purchase_header", "created_at", "TEXT");
        addColumnIfMissing("purchase_header", "updated_at", "TEXT");
    }

    private static void ensureSalesWorkflowColumns() {
        addColumnIfMissing("sales_header", "created_at", "TEXT");
        addColumnIfMissing("sales_header", "email_sent", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("sales_header", "due_date", "TEXT");
        addColumnIfMissing("sales_header", "paid_amount", "REAL NOT NULL DEFAULT 0");
        addColumnIfMissing("sales_header", "payment_status", "TEXT NOT NULL DEFAULT 'PENDING'");
        addColumnIfMissing("sales_header", "whatsapp_sent", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("sales_header", "invoice_type", "TEXT NOT NULL DEFAULT 'TAX INVOICE'");
        addColumnIfMissing("sales_header", "salesperson", "TEXT");
        addColumnIfMissing("sales_header", "source", "TEXT");
        addColumnIfMissing("sales_header", "notes", "TEXT");
        addColumnIfMissing("sales_header", "delivery_address", "TEXT");
        addColumnIfMissing("sales_header", "payment_terms", "TEXT");
        addColumnIfMissing("sales_header", "transporter", "TEXT");
        addColumnIfMissing("sales_header", "reference_no", "TEXT");
        addColumnIfMissing("sales_header", "attachment_path", "TEXT");
        addColumnIfMissing("sales_header", "document_status", "TEXT NOT NULL DEFAULT 'COMPLETED'");
        addColumnIfMissing("purchase_header", "due_date", "TEXT");
        addColumnIfMissing("purchase_header", "paid_amount", "REAL NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_header", "payment_status", "TEXT NOT NULL DEFAULT 'PENDING'");
        addColumnIfMissing("purchase_header", "email_sent", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_header", "created_at", "TEXT");
    }

    private static void createQuotationTables() {
        createTable("""
            CREATE TABLE IF NOT EXISTS quotation_header (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                quotation_no TEXT NOT NULL UNIQUE,
                quotation_date TEXT NOT NULL,
                valid_until TEXT,
                customer_id INTEGER NOT NULL,
                subtotal REAL NOT NULL DEFAULT 0,
                gst_amount REAL NOT NULL DEFAULT 0,
                total_amount REAL NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'DRAFT',
                remarks TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(customer_id) REFERENCES party_master(id)
            )
            """);
        createTable("""
            CREATE TABLE IF NOT EXISTS quotation_line (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                quotation_id INTEGER NOT NULL,
                item_code TEXT NOT NULL,
                quantity REAL NOT NULL,
                rate REAL NOT NULL,
                gst_percent REAL NOT NULL DEFAULT 0,
                line_total REAL NOT NULL,
                FOREIGN KEY(quotation_id) REFERENCES quotation_header(id) ON DELETE CASCADE,
                FOREIGN KEY(item_code) REFERENCES item_master(item_code)
            )
            """);
    }

    private static void ensureQuotationWorkflowColumns() {
        addColumnIfMissing("quotation_header", "follow_up_date", "TEXT");
        addColumnIfMissing("quotation_header", "salesperson", "TEXT");
        addColumnIfMissing("quotation_header", "source", "TEXT");
        addColumnIfMissing("quotation_header", "created_by", "TEXT");
        addColumnIfMissing("quotation_header", "discount_amount", "REAL NOT NULL DEFAULT 0");
        addColumnIfMissing("quotation_line", "discount_percent", "REAL NOT NULL DEFAULT 0");
        addColumnIfMissing("quotation_header", "converted_invoice_no", "TEXT");
        addColumnIfMissing("quotation_header", "email_sent", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("quotation_header", "whatsapp_sent", "INTEGER NOT NULL DEFAULT 0");
    }

    private static void createOperationsTables() {
        createTable("""
            CREATE TABLE IF NOT EXISTS return_register (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                return_no TEXT NOT NULL UNIQUE,
                return_type TEXT NOT NULL,
                return_date TEXT NOT NULL,
                invoice_no TEXT,
                party_id INTEGER,
                item_code TEXT NOT NULL,
                quantity REAL NOT NULL,
                amount REAL NOT NULL DEFAULT 0,
                reason TEXT,
                status TEXT NOT NULL DEFAULT 'COMPLETED',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(party_id) REFERENCES party_master(id),
                FOREIGN KEY(item_code) REFERENCES item_master(item_code)
            )
            """);
        createTable("""
            CREATE TABLE IF NOT EXISTS finance_register (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                voucher_no TEXT NOT NULL UNIQUE,
                voucher_type TEXT NOT NULL,
                voucher_date TEXT NOT NULL,
                party_id INTEGER,
                category TEXT,
                reference_no TEXT,
                amount REAL NOT NULL,
                payment_mode TEXT,
                notes TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(party_id) REFERENCES party_master(id)
            )
            """);
        createTable("""
            CREATE TABLE IF NOT EXISTS reminder_register (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                reference_no TEXT,
                due_date TEXT NOT NULL,
                priority TEXT NOT NULL DEFAULT 'NORMAL',
                notes TEXT,
                status TEXT NOT NULL DEFAULT 'OPEN',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
    }

    /**
     * Adds the fields required by the dedicated Reminder Center.  The migration
     * is additive so databases created by older releases keep all reminders.
     */
    private static void ensureReminderWorkflowColumns() {
        addColumnIfMissing("reminder_register", "reference_type", "TEXT");
        addColumnIfMissing("reminder_register", "party_id", "INTEGER");
        addColumnIfMissing("reminder_register", "snoozed_until", "TEXT");
        addColumnIfMissing("reminder_register", "completed_at", "TEXT");
        addColumnIfMissing("reminder_register", "created_by", "TEXT");
        addColumnIfMissing("reminder_register", "updated_at", "TEXT");
    }

    private static void createWorkflowTables() {
        createTable("""
            CREATE TABLE IF NOT EXISTS payment_record (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                document_type TEXT NOT NULL,
                document_id INTEGER NOT NULL,
                payment_date TEXT NOT NULL,
                amount REAL NOT NULL,
                payment_mode TEXT NOT NULL,
                reference_no TEXT,
                notes TEXT,
                created_by TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        createTable("""
            CREATE TABLE IF NOT EXISTS communication_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT NOT NULL,
                entity_id INTEGER NOT NULL,
                channel TEXT NOT NULL,
                recipient TEXT,
                subject TEXT,
                status TEXT NOT NULL,
                error_message TEXT,
                created_by TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        createTable("""
            CREATE TABLE IF NOT EXISTS saved_filter (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                screen_key TEXT NOT NULL,
                view_name TEXT NOT NULL,
                filter_json TEXT NOT NULL,
                is_default INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(user_id, screen_key, view_name),
                FOREIGN KEY(user_id) REFERENCES users(id)
            )
            """);
        createTable("""
            CREATE TABLE IF NOT EXISTS activity_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT NOT NULL,
                entity_id INTEGER,
                action TEXT NOT NULL,
                detail TEXT,
                created_by TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        createTable("""
            CREATE TABLE IF NOT EXISTS document_note (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT NOT NULL,
                entity_id INTEGER NOT NULL,
                note_text TEXT NOT NULL,
                created_by TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
    }

    /**
     * Stores module/action permissions for each database role.  Existing roles
     * receive practical defaults, while Administrator always retains access.
     */
    private static void createUserAccessTables() {
        createTable("""
            CREATE TABLE IF NOT EXISTS permissions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                permission_key TEXT NOT NULL UNIQUE,
                module_name TEXT NOT NULL,
                action_name TEXT NOT NULL,
                description TEXT,
                active INTEGER NOT NULL DEFAULT 1
            )
            """);
        createTable("""
            CREATE TABLE IF NOT EXISTS role_permission (
                role_id INTEGER NOT NULL,
                permission_id INTEGER NOT NULL,
                allowed INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(role_id, permission_id),
                FOREIGN KEY(role_id) REFERENCES roles(id) ON DELETE CASCADE,
                FOREIGN KEY(permission_id) REFERENCES permissions(id) ON DELETE CASCADE
            )
            """);

        String[] modules = {
            "DASHBOARD", "SALES", "PURCHASE", "QUOTATION", "INVENTORY",
            "CUSTOMERS", "SUPPLIERS", "MASTERS", "REPORTS", "COMMUNICATION",
            "REMINDERS", "USERS", "BACKUP", "SETTINGS", "IMPORT"
        };
        String[] actions = {"VIEW", "CREATE", "EDIT", "DELETE", "APPROVE", "EXPORT"};
        for (String module : modules) {
            for (String action : actions) {
                String key = module + "." + action;
                createTable("INSERT OR IGNORE INTO permissions(permission_key,module_name,action_name,description) " +
                    "VALUES('" + key + "','" + module + "','" + action + "','" +
                    action + " access for " + module + "')");
            }
        }
        createTable("""
            INSERT OR IGNORE INTO role_permission(role_id, permission_id, allowed)
            SELECT r.id, p.id,
                   CASE
                     WHEN r.role_name='ADMIN' THEN 1
                     WHEN r.role_name='MANAGER' AND p.module_name NOT IN ('USERS','BACKUP','SETTINGS') THEN 1
                     WHEN r.role_name='USER' AND p.action_name IN ('VIEW','CREATE','EDIT')
                          AND p.module_name NOT IN ('USERS','BACKUP','SETTINGS') THEN 1
                     ELSE 0
                   END
            FROM roles r CROSS JOIN permissions p
            """);
        createTable("CREATE INDEX IF NOT EXISTS idx_role_permission_role ON role_permission(role_id, allowed)");
    }

    /** Persists the optional automatic-backup preference used by the backup page. */
    private static void createBackupSettingsTable() {
        createTable("""
            CREATE TABLE IF NOT EXISTS application_setting (
                setting_key TEXT PRIMARY KEY,
                setting_value TEXT,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        createTable("INSERT OR IGNORE INTO application_setting(setting_key,setting_value) VALUES('backup.schedule','MANUAL')");
        createTable("INSERT OR IGNORE INTO application_setting(setting_key,setting_value) VALUES('backup.retention','30')");
    }


    private static void createBackupHistoryTable() {
        createTable("""
            CREATE TABLE IF NOT EXISTS backup_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_name TEXT NOT NULL UNIQUE,
                original_name TEXT,
                source_type TEXT NOT NULL DEFAULT 'MANUAL',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                file_size INTEGER NOT NULL DEFAULT 0,
                integrity_status TEXT NOT NULL DEFAULT 'AVAILABLE',
                schema_version INTEGER,
                application_id TEXT,
                created_by TEXT
            )
            """);
        createTable("CREATE INDEX IF NOT EXISTS idx_backup_history_created ON backup_history(created_at DESC)");
    }

    private static void createApplicationMetadataTable() {
        createTable("""
            CREATE TABLE IF NOT EXISTS application_metadata (
                metadata_key TEXT PRIMARY KEY,
                metadata_value TEXT NOT NULL
            )
            """);
        createTable("INSERT INTO application_metadata(metadata_key,metadata_value) VALUES('application.id','DSE_ERP') " +
                "ON CONFLICT(metadata_key) DO UPDATE SET metadata_value='DSE_ERP'");
        createTable("INSERT INTO application_metadata(metadata_key,metadata_value) VALUES('schema.version','1') " +
                "ON CONFLICT(metadata_key) DO UPDATE SET metadata_value='1'");
        createTable("INSERT INTO application_metadata(metadata_key,metadata_value) VALUES('application.version','2.0') " +
                "ON CONFLICT(metadata_key) DO UPDATE SET metadata_value='2.0'");
        createTable("PRAGMA user_version=1");
    }

    /**
     * Creates the persistent notification inbox used by the header bell.
     * The optional navigation target lets a notification open its related ERP screen.
     */
    private static void createNotificationTable() {
        createTable("""
            CREATE TABLE IF NOT EXISTS notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                message TEXT NOT NULL,
                severity TEXT NOT NULL DEFAULT 'INFO',
                is_read INTEGER NOT NULL DEFAULT 0,
                target_fxml TEXT,
                reference_no TEXT,
                created_at INTEGER NOT NULL
            )
            """);
        addColumnIfMissing("notifications", "target_fxml", "TEXT");
        addColumnIfMissing("notifications", "reference_no", "TEXT");
        createTable("CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications(is_read, created_at)");
    }

    private static void createBusinessIndexes() {
        createTable("CREATE INDEX IF NOT EXISTS idx_sales_date ON sales_header(invoice_date)");
        createTable("CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales_header(customer_id)");
        createTable("CREATE INDEX IF NOT EXISTS idx_sales_due ON sales_header(due_date, payment_status)");
        createTable("CREATE INDEX IF NOT EXISTS idx_quote_date ON quotation_header(quotation_date)");
        createTable("CREATE INDEX IF NOT EXISTS idx_quote_status ON quotation_header(status, valid_until)");
        createTable("CREATE INDEX IF NOT EXISTS idx_payment_document ON payment_record(document_type, document_id)");
        createTable("CREATE INDEX IF NOT EXISTS idx_reminder_due ON reminder_register(status, due_date)");
        createTable("CREATE INDEX IF NOT EXISTS idx_activity_entity ON activity_log(entity_type, entity_id)");
        addColumnIfMissing("return_register", "refund_amount", "REAL NOT NULL DEFAULT 0");
        addColumnIfMissing("return_register", "refund_status", "TEXT NOT NULL DEFAULT 'PENDING'");
        addColumnIfMissing("return_register", "notes", "TEXT");
        addColumnIfMissing("return_register", "attachment_path", "TEXT");
        addColumnIfMissing("return_register", "updated_at", "TEXT");
    }

    /**
     * Older releases declared return_no as UNIQUE, which prevented a single
     * return document from containing multiple item rows. Rebuild the table
     * once without that constraint and retain every existing return record.
     */
    private static void ensureReturnLineStorage() {
        boolean uniqueReturnNumber = false;
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery("PRAGMA index_list('return_register')")) {
            while (indexes.next()) {
                if (indexes.getInt("unique") == 1 && "u".equalsIgnoreCase(indexes.getString("origin"))) {
                    uniqueReturnNumber = true;
                    break;
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Return storage could not be inspected", exception);
        }

        if (!uniqueReturnNumber) {
            createTable("CREATE INDEX IF NOT EXISTS idx_return_number ON return_register(return_no)");
            return;
        }

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=OFF");
            connection.setAutoCommit(false);
            try {
                statement.execute("ALTER TABLE return_register RENAME TO return_register_legacy");
                statement.execute("""
                    CREATE TABLE return_register (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        return_no TEXT NOT NULL,
                        return_type TEXT NOT NULL,
                        return_date TEXT NOT NULL,
                        invoice_no TEXT,
                        party_id INTEGER,
                        item_code TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        amount REAL NOT NULL DEFAULT 0,
                        reason TEXT,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        refund_amount REAL NOT NULL DEFAULT 0,
                        refund_status TEXT NOT NULL DEFAULT 'PENDING',
                        notes TEXT,
                        attachment_path TEXT,
                        updated_at TEXT,
                        FOREIGN KEY(party_id) REFERENCES party_master(id),
                        FOREIGN KEY(item_code) REFERENCES item_master(item_code)
                    )
                    """);
                statement.execute("""
                    INSERT INTO return_register
                    (id, return_no, return_type, return_date, invoice_no, party_id,
                     item_code, quantity, amount, reason, status, created_at,
                     refund_amount, refund_status, notes, attachment_path, updated_at)
                    SELECT id, return_no, return_type, return_date, invoice_no, party_id,
                           item_code, quantity, amount, reason, status, created_at,
                           COALESCE(refund_amount,0), COALESCE(refund_status,'PENDING'),
                           notes, attachment_path, updated_at
                    FROM return_register_legacy
                    """);
                statement.execute("DROP TABLE return_register_legacy");
                statement.execute("CREATE INDEX idx_return_number ON return_register(return_no)");
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                statement.execute("PRAGMA foreign_keys=ON");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Return storage could not be upgraded for multi-item returns", exception);
        }
    }

    /**
     * OPENED was used by older builds for a successful WhatsApp handoff.
     * The desktop integration cannot observe the final click inside WhatsApp,
     * so a successful handoff is the completed application-side state.
     */
    private static void migrateCompletedWhatsappHandoffs() {
        createTable("UPDATE communication_log SET status='SENT' " +
            "WHERE channel='WHATSAPP' AND status='OPENED'");
    }

    private static void seedLookupData() {
        String sql = """
            INSERT INTO lookup_master
            (
                lookup_type,
                lookup_code,
                lookup_value,
                is_active
            )
            SELECT ?, ?, ?, 1
            WHERE NOT EXISTS
            (
                SELECT 1
                FROM lookup_master
                WHERE lookup_type = ?
                AND lookup_value = ?
            );
            """;

        String[][] data = {
            {"CATEGORY", "CAT001", "Valve"},
            {"CATEGORY", "CAT002", "Pipe"},
            {"CATEGORY", "CAT003", "Flange"},
            {"UNIT", "UNT001", "Nos"},
            {"UNIT", "UNT002", "Kg"},
            {"UNIT", "UNT003", "Meter"},
            {"MATERIAL", "MAT001", "SS304"},
            {"MATERIAL", "MAT002", "SS316"},
            {"MATERIAL", "MAT003", "Carbon Steel"},
            {"BRAND", "BRD001", "L&T"},
            {"BRAND", "BRD002", "Kirloskar"},
            {"GST", "GST001", "0"},
            {"GST", "GST002", "5"},
            {"GST", "GST003", "12"},
            {"GST", "GST004", "18"},
            {"GST", "GST005", "28"},
            {"DISCOUNT", "DSC001", "0"},
            {"DISCOUNT", "DSC002", "2"},
            {"DISCOUNT", "DSC003", "5"},
            {"DISCOUNT", "DSC004", "10"},
            {"DISCOUNT", "DSC005", "15"},
            {"DISCOUNT", "DSC006", "20"}
        };

        try (
            Connection con = getConnection();
            PreparedStatement ps = con.prepareStatement(sql)
        ) {
            for (String[] row : data) {
                ps.setString(1, row[0]);
                ps.setString(2, row[1]);
                ps.setString(3, row[2]);
                ps.setString(4, row[0]);
                ps.setString(5, row[2]);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        createTable("INSERT OR IGNORE INTO master_category(category_code, category_name, description, display_order, is_active) VALUES('DISCOUNT','DISCOUNT','Default item discount percentages',60,1)");
    }
}
