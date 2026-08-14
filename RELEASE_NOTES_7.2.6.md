# DSE ERP 7.2.6

## Safe Rollback

DSE ERP 7.2.6 adds a production-focused **Safe Rollback** workspace directly below **Settings** in the main navigation.

### What Safe Rollback does

- Rolls back the **application version only** while preserving the current PostgreSQL business database.
- Creates a verified PostgreSQL safety backup before any rollback installer is launched.
- Creates a recovery-point snapshot of the current `Config` and `Templates` workspace folders.
- Leaves Attachments, Documents and the active workspace in place.
- Blocks application-only rollback when target database compatibility is unknown or incompatible.
- Supports importing a retained previous EXE/MSI/DMG/PKG installer.
- Supports downloading an exact previous published GitHub Release and requires its SHA-256 checksum before retention.
- Keeps rollback packages under `Workspace/Updates/Rollback/Packages`.
- Keeps recovery points under `Workspace/Updates/Rollback/RecoveryPoints`.
- Records rollback package preparation and installer activity in an audit history.
- Keeps **Full Database Recovery** separate and routes it to the existing Backup & Restore screen.

### Safety model

Normal rollback never restores an old database automatically. This prevents legitimate Sales, Purchase, Payment, Inventory, Customer, Supplier and other transactions entered after an upgrade from being discarded simply because the application version is changed.

### Compatibility

7.2.6 records database compatibility metadata in `app-version.properties`. Known 7.2.x packages on the same schema generation can be marked safe; unknown schema packages remain blocked rather than being guessed compatible.

### UI

- New orange-accented shield **Safe Rollback** navigation item immediately below Settings.
- Current version, database schema, data-preservation state and rollback-package metrics.
- Available-version table with compatibility and Ready/Blocked status.
- Recovery point and safety sequence cards.
- Rollback audit table.
- Light and dark theme styling appended to the existing shared CSS files.

### Existing functionality

The 7.2.5 Purchase Document Studio remains intact. Sales PDF generation and Sales business flow were not modified by Safe Rollback.

### Safe Rollback UI refinement
- Refined Safe Rollback on the same 7.2.6 version with geometric semantic icons, full-width tables, balanced panel sizing, and clearer step-by-step safety sequence presentation.
