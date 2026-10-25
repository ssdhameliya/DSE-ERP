# What's New in DSE ERP v11.0.5

### Recon Supplier Records Drill-Down & Multi-Link Navigation
- **Interactive Record Count Drill-Down**: Clicking the linked record count in the Recon Supplier table launches the comprehensive `ReconSupplierRecordsDialog` showing all matching `PurchaseRecon` records.
- **Dynamic Popup Geometry & Formatting**: Adheres to centralized UI rules with themed column icons, right-aligned currency formatting, responsive column widths, and direct multi-link pills to navigate directly to linked Bank Statement or Bank Expense transactions.
- **Dedicated Toolbar with Search & Excel Export**: Features a live filter search bar with search icon, instant Reset button, and one-click Excel (.xlsx) export.

### Auto-Generated Supplier Reference
- **Deterministic Read-Only Reference**: Creating a new Recon Supplier automatically fetches the next sequential supplier code (e.g. `RS-0001`, `RS-0002`) from the backend.
- **Enforced Immutability**: The reference field is locked as read-only to prevent manual typos or conflicting references.

### Centralized Operational Standards Across Registers
- **Recon Supplier Table Upgrades**: Integrated search icon, dedicated Reset button, Excel export button, and standard pagination footer with dynamic "Showing X to Y of Z entries" counter matching Bank Statement.
- **Purchase Recon & Bank Expense Enhancements**: Added standard Reset filters and Excel workbook export across both registers.

### Bank Statement Auto-Match Tolerance & Scanning Improvements
- **Auto-Match Tolerance Fix**: Resolved matching failures for amounts within configured decimal tolerance (e.g., ₹0.10 round-off differences) by allocating exact bank transaction amounts during auto-reconciliation.
- **Animated Scanning Progress**: Embedded real-time scanning feedback within `LiveOperationDialog` with rotating progress and live transaction status counters.
- **Browse Statements Dialog Optimization**: Resolved button clipping on the Bank Statement History popup by establishing adaptive screen bounds, dynamic table height flexibility, and bottom action button clearance.

### Workspace & Dialog Layout Fixes
- **Email Transmission Dialog**: Restored the Message Body field to a full-height multi-line `TextArea` by correcting JavaFX CSS dimension specifications, enabling `fitToHeight` expansion, and enforcing minimum bounds.
- **Purchase Recon Records Dialog**: Eliminated horizontal clipping on right-side toolbar buttons (`Reset`, `Export Excel`) and table columns (`Linked Documents`, `Notes`) via `DynamicTableLayoutManager` installation and responsive column width compaction.
