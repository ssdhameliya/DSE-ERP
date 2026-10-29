# Changelog — DSE ERP v11.0.9

## Release 11.0.9 — Synchronous Modal Dialog Pre-Rendering, Universal 3D Button Disambiguation, Reference Hyperlinks & Dynamic Table Refresh Drift Elimination

- **Release Date:** 2026-10-29
- **Baseline:** Git tag `v11.0.8`
- **Type:** UI Performance, Modal Lifecycle Stabilization, Table Symmetry & Color Disambiguation Release

---

## Key Improvements & Fixes

### 1. Synchronous Pre-Render Lifecycle (Elimination of Post-Show Jump & Color Flash)
- **Problem:** When opening any modal dialog or confirmation alert, the dialog would briefly flash on screen in an uncalculated, offset position with raw styles, then suddenly jump to the screen center, resize, and repaint its buttons and theme colors after appearing (`Platform.runLater()` inside `DIALOG_SHOWN` and `setOnShown`).
- **Solution:**
  - In `AppDialogRenderer.java`, consolidated theme application, semantic button styling, responsive class binding, and layout measurement into the synchronous `DIALOG_SHOWING` phase.
  - In `PlatformUiSupport.java`, replaced deferred `setOnShown(Platform.runLater(...))` execution with synchronous `prepareDialogStage()` triggered on `stage.setOnShowing()`.
  - Stages and dialogs are now styled, themed, sized, and centered *before* the first pixel is drawn on the screen. The dialog appears instantly in its final, centered position with zero visual jump, zero color flicker, and halved UI thread overhead.

### 2. Universal 3D Button Color Disambiguation & Icon Alignment
- **Problem:** Action buttons across multiple screens (View, Open File, Download, Refresh, Reset, and Audit Trail) frequently shared identical blue/neutral button hues and ambiguous icons, confusing users on critical document actions.
- **Solution:**
  - In `UiDesignSystem.java` and `IconFactory.java`, established distinct semantic palettes and icons:
    - **View:** Cyan (`approved-cyan-button`, `fas-eye`)
    - **Open File:** Amber (`approved-amber-button`, `fas-folder-open`)
    - **Download:** Sky Blue (`approved-sky-button`, `fas-cloud-download-alt`)
    - **Refresh:** Emerald Mint (`approved-emerald-button`, `fas-sync-alt`)
    - **Reset:** Coral Rose (`approved-coral-button`, `fas-broom`/`fas-undo`)
    - **Audit Trail:** Deep Indigo (`approved-indigo-button`, `fas-fingerprint`)
  - Elevated 3D depth gradients, tactile pressed states, and distinct SVG/FontAwesome glyphs across all 72 screens.

### 3. Dynamic Table Layout & Refresh Drift Elimination
- **Problem:** In reporting dashboards and register views, tables displayed inconsistently before and after data refresh. Column headers truncated tokens, rightmost columns clipped upon scrollbar appearance, and tab switches did not reflow tables cleanly.
- **Solution:**
  - In `DynamicTableLayoutManager.java`:
    - Added `forceLayout(TableView<?> table)` with cache invalidation (`LAST_AVAILABLE`) so scrollbar appearance on data binding never clips columns.
    - Updated `headerMinimum` to measure full multi-word header titles with icons rather than only the longest single token.
    - Added universal `TabPane` selection listener in `ProfessionalUiEnhancer.java` for instant table reflow on tab activation.
  - In `ReportViewerController.java`:
    - Re-registered dynamic layout manager and header decorations immediately after `tblReport.getColumns().clear()`.
  - In `ReportsController.java`:
    - Registered all 4 reporting tables (`tblSales`, `tblPurchases`, `tblSavedReports`, `tblSchedules`) and invoked `forceLayout()` in `applyReportCore()`.

### 4. Universal Reference Hyperlink Engine
- **Problem:** Document reference numbers and party codes across registers lacked clickable hyperlink affordances.
- **Solution:**
  - Expanded `SemanticValueCell.isLink` in `ProfessionalUiEnhancer.java` to detect all 75 document reference types (e.g., `INV-`, `SO-`, `PO-`, `QTN-`, `REF-`, `VOU-`, `CUST-`, `SUP-`).
  - Added `.table-cell.erp-table-link-cell` styling in `dark-theme.css`, `light-theme.css`, and `app-dialog.css` with `#38bdf8` electric blue text, hover underline, pointer hand cursor, and instant click-to-copy toast notification.

### 5. Action Menu Colorful Icon Parity
- **Problem:** Dropdown action menus and "No saved views" items lacked colorful iconography.
- **Solution:**
  - Added dedicated colorful icons (`fas-bookmark`, `fas-eye`, `fas-sliders-h`) in `SalesListController`, `PurchaseListController`, and `QuotationController`.
  - Enforced `IconFactory.decorateActionMenu(...)` in `FinancialStatementsController`.

### 6. Guard Against Hidden Action Buttons & Content Clipping on Scaled Monitors
- **Problem:** On smaller laptop monitors (1366x768) or displays running 125%/150% Windows scaling, dialogs with long forms (such as Party Master or Item Master) had their bottom `Save` / `Cancel` action buttons pushed off-screen.
- **Solution:**
  - In `AppDialogRenderer.java`, wrapped compact dialog message bodies in a transparent, zero-border `ScrollPane` with `fitToWidth=true` and `vbarPolicy=AS_NEEDED`.
  - In `PartyDialog.fxml` and `lookupDialog.fxml`, wrapped the center form grids in `<ScrollPane fitToWidth="true" hbarPolicy="NEVER" styleClass="dialog-scroll-pane">` matching `Itemdialog.fxml` and `UserDialog.fxml`.
  - In `WindowUtilsFx.java`, updated `fitDialogToOwnerScreen()` and `fitCompactDialogToOwnerScreen()` to run `sizeToScene()` after CSS/layout passes and clamp cleanly against the monitor's usable visual bounds.
