# DSE ERP 7.3.7 — Scope Verification

Baseline: DSE ERP 7.3.6 Phase 11 attached production source.

## Fresh verification completed in the handoff workspace

- Parsed all 43 FXML files successfully with XML parsing: 43/43, zero parse errors.
- Resolved all modified FXML `onAction` handlers against their controllers: zero missing handlers.
- Existing architecture/data-boundary audit scripts passed:
  - `python3 scripts/audit-desktop-jdbc.py`
  - `python3 scripts/audit-phase2-data-boundary.py`
  - `python3 scripts/audit-postgres-only.py`
  - `python3 scripts/audit-final-data-architecture.py`
- Re-ran the GitHub CI UI source contract used by the repository: `UI_SOURCE_CONTRACT_OK`, FXML=43, responsive profiles=8.
- Scope acceptance source checks passed 15/15 covering:
  1. Global structural field-label icons
  2. Settings sidebar accordion
  3. Sale/Purchase/Quotation `Remarks • Description` visible contract
  4. Payment UI/edit workflow
  5. Transactional payment re-summing and activity audit
  6. Sales 7-day default/reset
  7. Purchase 7-day default/reset
  8. Quotation 7-day default/reset
  9. Bank Bulk Actions sizing
  10. Document Studio Actions sizing/name
  11. DatePicker structural/dark contract
  12. Close/chevron icon semantics
  13. Sale Invoice PDF-only preview
  14. Three closing PDF rows at 65 / 2 / 33
  15. Version synchronized to 7.3.7

## Build-toolchain limitation of this verification environment

A full Maven compiler/test/package run was not executable in the handoff sandbox because the project requires Java 25, while the sandbox exposes Java 21 and does not provide Maven or external dependency/toolchain resolution. Therefore this document does **not** claim a successful `mvn clean verify` run in the sandbox.

The repository's existing GitHub workflows remain configured for Java 25 and run `mvn -B -ntp clean verify`; those workflows are retained in the GitHub-ready package for the authoritative full compiler/test run after push.

## Database and packaging safety

- No database schema migration was introduced.
- Payment edits use existing payment/activity structures and recalculate invoice totals/status from payment records inside the server transaction.
- Bank-reconciliation-created payment records cannot be directly edited from Record Payment.
- Existing native packaging scripts/workflows were retained apart from 7.3.7 version synchronization.

## Post-handoff Create Sale Description-column regression correction

Screenshot-based regression analysis identified that `ProfessionalUiEnhancer.decorateColumns()` clears `TableColumn.text` after preserving the original label in `erp-header-label`. The later first-column selection/index detection was reading the now-empty `TableColumn.text`, so a legitimate first column such as Create Sale `Description` could be misclassified as a blank legacy selection column.

Correction: first-column detection now reads the preserved `erp-header-label` before falling back to `TableColumn.text`.

Fresh source checks after the correction:
- Create Sale FXML still declares `colItem` as `Description`.
- Sales table still binds `colItem` through `itemNameForDisplay(...)`.
- Search/selected item display still resolves to `Remarks • Description`.
- All 43 FXML files parse successfully after the correction.
- No schema, persistence contract, package target or version change was introduced.
