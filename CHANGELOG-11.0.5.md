# Changelog — DSE ERP v11.0.5

## Release 11.0.5 — Recon Supplier Drill-Down & Auto-Reference, Centralized Search/Export, Bank Auto-Match Tolerance Fix & Statement Dialog Optimization

- **Release Date:** 2026-10-26
- **Baseline:** Git tag `v11.0.4`
- **Type:** Feature & Defect Resolution Release (no breaking database changes)

---

## Key Features & Improvements

### 1. Recon Supplier Records Inspection Modal & Multi-Link Drill-Down
- **Problem:** When the Recon Supplier register showed a record count (e.g. 3 transactions), users could not inspect which transactions were linked, what their values were, or jump directly to linked bank statements or expenses.
- **Solution:**
  - Built `ReconSupplierRecordsDialog.java` displaying all `PurchaseRecon` records belonging to the supplier.
  - Table includes System Ref (with clickable navigation), Supplier Invoice No, Invoice Date (formatted with business clock), Taxable Value, CGST, SGST, IGST, Other Adj, Total Value, Status badge, and interactive multi-link pills to open linked Bank Statements or Bank Expense entries.
  - Header displays supplier legal name, reference, and GSTIN with dynamic total record count badge.
  - Toolbar includes real-time search input with magnifying glass icon, instant filter Reset button, and complete Excel workbook export.

### 2. Auto-Generated Read-Only Recon Supplier Reference
- **Problem:** When creating a new Recon Supplier, the reference code had to be entered manually, risking duplicates or inconsistent numbering formats.
- **Solution:**
  - Implemented backend sequence generation in `PurchaseReconService.java` (`nextSupplierReference()`) querying the latest numeric sequence (`RS-%04d`).
  - Added REST endpoint `GET /api/recon/purchase/suppliers/next-reference`.
  - In `ReconSupplierController.java`, new supplier dialog automatically populates the next reference asynchronously and locks the field as read-only.

### 3. Centralized UI Standardization Across Registers
- **Problem:** Several registers lacked basic filtering tools like Reset buttons, search icons, and Excel export options.
- **Solution:**
  - **Recon Supplier:** Added search icon, Reset button, Export Excel button, and standard pagination footer with dynamic "Showing X to Y of Z entries" counter matching Bank Statement.
  - **Purchase Recon:** Added Reset button and Export Excel button.
  - **Bank Expense:** Added Export Excel button for bank and expense entry records.

### 4. Bank Statement Auto-Match Tolerance Resolution
- **Problem:** Transactions with minor decimal round-off differences (such as ₹0.10) within the configured tolerance failed auto-matching because the full transaction amount was passed rather than allocating the exact bank amount to trigger server-side round-off handling.
- **Solution:**
  - Updated auto-match allocation in `BankStatementController.java` to allocate the exact bank amount (`it.bankAmount()`), allowing the backend reconciliation engine to apply configured tolerance rules seamlessly.
  - Enhanced user feedback: instead of a false green toast when transactions fail matching, the system presents detailed failure warnings and suggestions.
  - Incorporated animated scanning progress dialog with live visual feedback during reconciliation matching.

### 5. Browse Statements Dialog Layout & Resizing Fix
- **Problem:** The Statement History / Browse Statements modal clipped action buttons ("Delete Statement" and "Open Statement") on standard display resolutions.
- **Solution:**
  - Corrected dialog minimum bounds, container constraints, and scrollable area in `BankStatement.fxml`.
  - Added bottom button margin clearance, enabled flexible table height (`minHeight="140" prefHeight="220" VBox.vgrow="ALWAYS"`), and implemented screen-adaptive dialog bounds (`Math.min(780, screenHeight - 60)`).
  - Installed `DynamicTableLayoutManager` on statement history table.

### 6. Email Transmission & Recon Records Dialog Sizing Fixes
- **Problem:**
  - Email transmission dialog message body collapsed into a single-line text field due to invalid CSS dimension unit syntax.
  - Recon supplier records dialog right-side toolbar buttons (`Reset`, `Export Excel`) and table columns (`Linked Documents`, `Notes`) were clipped.
- **Solution:**
  - Restored message body to multi-line `TextArea` with unitless JavaFX CSS, `fitToHeight` expansion, and programmatic 200px/240px sizing.
  - Installed `DynamicTableLayoutManager` and compacted column widths in `ReconSupplierRecordsDialog`, preventing horizontal button and table clipping.

---

## Release Gate Status
All 32 release gates pass — `RELEASE_GATES_CURRENT_OK version=11.0.5`
