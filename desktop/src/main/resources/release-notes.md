# What's New in DSE ERP v11.0.6

### Centralized Bank Reference Master (12 Formats Resolution)
- **Standardized Multi-Module References**: Standardized reference codes across all 12 bank-related formats (9 core + 3 fallback/legacy) for unified cross-module lookups between Bank Statements, Bank Entries, and Expense Entries.
- **Automated Database Sequence Registry**: Initialized reference format sequence tracking via `V10_0_35__bank_reference_format_master.sql`.

### Multi-Target Allocation Chooser Dialog
- **Interactive Multi-Target Inspection**: Reconciled bank transactions linked to multiple ERP invoices or recons now open a dedicated 980px dialog displaying summary KPI metrics.
- **Direct Workspace Navigation**: A 6-column table with individual `👁️ View` buttons enables seamless travel to the target document's workspace.

### Side Drawer Suppression on Linked Navigation
- **Workspace Focus**: Auto-opening of side drawers in Bank Entry and Expense Entry is suppressed when navigating to an isolated transaction, keeping the focused row unobstructed.

### Non-Clipped Table Headers & Expanded Column Widths
- **Expanded Column Geometry**: Match/Link headers widened across `BankStatement.fxml`, `BankExpense.fxml`, and `PurchaseRecon.fxml`.
- **Colorful Theme Badges**: Added vibrant badges (`pill-purple`, `pill-cyan`, `pill-teal`, `pill-blue`, `pill-orange`) eliminating text clipping.

### Universal Excel Workbook Export
- **One-Click Export**: Standardized full-table `.xlsx` export across all 5 registers under the Bank & Expense menu.
