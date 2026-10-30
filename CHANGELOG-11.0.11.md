# Changelog — DSE ERP v11.0.11

## Release 11.0.11 — Unified Inspection Modals, Double-Header Resolution & High-Performance Ledger Filtering

- **Release Date:** 2026-10-30
- **Baseline:** Git tag `v11.0.10`
- **Type:** UI/UX Symmetry, Performance Optimization, Modal Standardization & Bug Fixes

---

## Key Improvements & Fixes

### 1. Unified Enterprise Modal Architecture & Double-Header Bug Fix
- **Eliminated Duplicate Header Shells:** Resolved double-header rendering in **System & Application Diagnostics** (`SystemDiagnosticsDialog`) and **Audit Trail** (`ActivityTimelineDialog`) where an outer white dialog shell was rendered over the inner dark hero header. Removed redundant `AppDialogRenderer.configureWorkspace` wrapping and marked dialog panes as presented (`dse.dialog.presented = true`).
- **Persistent Solid Host Pattern:** Standardized all 7 enterprise document and diagnostic inspection dialogs (`SalesDocumentInspectionDialog`, `PurchaseDocumentInspectionDialog`, `QuotationDocumentInspectionDialog`, `ReturnDocumentInspectionDialog`, `CreditDebitNoteInspectionDialog`, `SystemDiagnosticsDialog`, `ActivityTimelineDialog`) on a solid opaque `StackPane host` (`#0d1527` in dark mode / `#ffffff` in light mode) with 14px rounded borders and elevation drop shadows.
- **Window Dragging & Close Action Symmetry:** Added unified window drag handlers to header hero bars and standardized top-right close buttons with `SemanticIconManager.compact("close", 14)` and `.dse-dialog-close` styling across all modals.

### 2. Instant Search & Dropdown Filtering Optimization
- **Pre-Computed Search Indexing:** Added single-pass `searchIndex` calculation for `DiagnosticRow` and `AuditTableRow` computed during background parsing/flattening. Substring search now evaluates a single pre-indexed string rather than repeatedly performing lowercasing and string concatenation on the JavaFX UI thread across 700+ entries.
- **Unified 250ms Debounce:** Debounced search text inputs and dropdown category/source combo box changes via `PauseTransition`, eliminating keystroke delays and rapid-selection stutter.
- **Table Cell Memory & Rendering Optimization:**
  - Replaced $O(N)$ `table.getItems().indexOf(...)` calls in row index columns (`colIndex`) with $O(1)$ page-relative index calculations.
  - Eliminated repetitive `new Tooltip(...)` allocations on every table cell update pass in old value, new value, remarks, and guidance columns by reusing single tooltip instances per table cell.

### 3. Inspection Sub-Modal Standardization
- **Technical Inspection Detail Modal:** Standardized `showTechnicalInspectionModal` inside System Diagnostics to adopt the solid opaque theme container, high-contrast monospace code styling, top-right close button, and window dragging.
