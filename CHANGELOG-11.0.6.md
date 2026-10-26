# Changelog — DSE ERP v11.0.6

## Release 11.0.6 — Centralized Bank Reference Master (12 Formats), Multi-Target Allocation Chooser, Non-Clipped Tables & Universal Export

- **Release Date:** 2026-10-27
- **Baseline:** Git tag `v11.0.5`
- **Type:** Feature, Architectural Parity & Defect Resolution Release

---

## Key Features & Improvements

### 1. Centralized Bank Reference Master (12 Formats Resolution)
- **Problem:** Bank references were fragmented across legacy string prefixes (`BSE-...`, `BE/...`, `EXP-...`, `PRC-...`), making cross-module identification inconsistent across Bank Statement, Bank Entry, and Expense Entry.
- **Solution:**
  - Added Flyway migration `V10_0_35__bank_reference_format_master.sql` registering all 9 core format categories alongside 3 legacy/fallback patterns.
  - Updated `MasterDataService.java`, `BankReconciliationService.java`, and `BusinessOperationsService.java` to handle unified reference parsing, sequence generation, and bidirectional cross-module lookups.

### 2. Multi-Target Allocation Chooser Dialog
- **Problem:** Bank statement transactions reconciled against multiple ERP invoices or recons were rendered in a single clipped cell with no ability to choose which linked target to inspect.
- **Solution:**
  - Upgraded `BankStatementController.java` and `BankStatement.fxml` with a responsive 980px dialog displaying:
    - 5 Top KPI Cards: Bank Reference, Linked Count, Total Bank Linked (₹), Round-off (₹), and Difference (₹).
    - 6-Column Data Table: Target Type (with colorful icons), Document No, Details / Narration, Amount (₹), Round-off (₹), and Action (`👁️ View`).
  - Clicking `View` on any row seamlessly navigates directly to the target record's register workspace.

### 3. Side Drawer Suppression on Linked Navigation
- **Problem:** Traveling to an isolated Bank Entry or Expense Entry would automatically pop open the edit drawer, covering the isolated row and disorienting the user.
- **Solution:**
  - Updated `BankExpenseController.java` to strictly suppress drawer auto-opening during linked navigation, focusing and highlighting the record in the main table with clear visibility.

### 4. Non-Clipped Table Headers & Colorful Badge Chips
- **Problem:** Match/Link table headers and cells were constrained to 140px, causing text and badge clipping.
- **Solution:**
  - Expanded Match/Link column widths across `BankStatement.fxml`, `BankExpense.fxml`, and `PurchaseRecon.fxml`.
  - Added vibrant theme badges (`pill-purple`, `pill-cyan`, `pill-teal`, `pill-blue`, `pill-orange`) across `light-theme.css`, `dark-theme.css`, and `app-dialog.css`.

### 5. Universal Excel Workbook Export
- **Problem:** Export buttons were missing or incomplete on some bank and expense registers.
- **Solution:**
  - Verified and standardized one-click Excel workbook (.xlsx) export across Bank Entry, Expense Entry, Bank Statement, Purchase Recon, and Recon Supplier registers.

---

## Release Gate Status
All 32 release gates pass — `RELEASE_GATES_CURRENT_OK version=11.0.6`
