# DSE ERP 5.0.3

Release 5.0.3 focuses on macOS responsiveness while preserving the PostgreSQL migration and existing ERP workflows.

## Performance and platform improvements

- Shows the startup screen immediately and performs PostgreSQL, migration, and Spring/Hibernate initialization away from the JavaFX thread.
- Uses one shared HikariCP PostgreSQL connection pool for legacy JDBC and Spring/Hibernate access.
- Performs password verification, OTP delivery, and successful-login database recording away from the JavaFX thread.
- Writes performance diagnostics asynchronously so slow workspace storage cannot stall the interface.
- Retains the bounded page cache and records first-load and cached-load budget results.
- Adds a JavaFX pulse watchdog that records UI stalls over 100 ms in `performance.log`.
- Corrects macOS platform selectors and removes expensive card effects on macOS.
- Makes the Sales form vertically scrollable and tightens Master Data at 1024x768.
- Validates all FXML screens in light and dark themes at 1024x768 on both macOS architectures during release builds.

## Acceptance budgets

- Warm startup: 5,000 ms
- Login response: 1,500 ms
- First register opening: 1,500 ms
- Cached page opening: 300 ms
- JavaFX pulse gap: 100 ms

Actual results are recorded as `budget-pass`, `budget-fail`, or `fx-freeze` entries in the active workspace's `performance.log`. Results depend on the Mac model, workspace storage, database latency, and data volume.

## Database compatibility

PostgreSQL remains the primary database for migrated and new 5.x workspaces. Existing SQLite users retain the automatic migration and safety-backup path introduced in 5.0.x.
