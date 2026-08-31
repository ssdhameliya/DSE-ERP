# DSE ERP 9.0.41 — UI Migration Phase 1

## Purpose

Phase 1 freezes the approved 9.0.40 application view and behavior before the agreed UI migration. It intentionally does **not** consolidate CSS, change TableView widths, change KPI layout, redesign Settings, or alter business workflows.

## Locked behavior

- Main sidebar and navigation structure/behavior remain unchanged.
- Controller save/edit/delete/validation/permission/navigation behavior remains unchanged.
- Table sorting, selection, pagination, filtering, row actions, detail drawers, saved-view behavior and data loading remain unchanged.
- Dialog, warning, confirmation and popup trigger/result/modality behavior remains unchanged.
- Database schema and all existing migration files remain unchanged.
- Existing FXML fx:id and event-handler contracts are frozen for regression comparison.

## Approved later presentation changes

Later phases may deliberately change only reviewed UI concerns: two-theme CSS consolidation, semantic colored icons/labels/table headers, responsive KPI widths, dynamic TableColumn widths, and removal of duplicated UI-only controller code. Each such phase must be reviewed against the baseline before its baseline is intentionally advanced.

## Phase 1 guardrail

Run:

```bash
python scripts/audit-9.0.41-ui-behavior-freeze.py
```

The reviewed baseline is stored in:

```text
scripts/ui-behavior-freeze-9.0.41.json
```

The baseline currently records 58 FXML files, 44 TableViews, 335 TableColumns, 1,774 fx:ids and 643 FXML event bindings, plus dialog/table Java behavior signatures, exact navigation hashes, CSS visual-baseline hashes and database schema/migration hashes.

## Baseline advancement rule

Do not run `--capture` simply to make a failing audit green. Re-capture is allowed only after an approved migration phase has been reviewed and its intentional source delta is understood. Sidebar/navigation and database hashes remain non-negotiable unless the project scope is explicitly changed.

## Build status for this handoff

Phase 1 is a source-only guardrail release. Maven build verification is deferred because the current execution environment cannot resolve Maven Central; this is an environment/network limitation, not a claimed successful build. Final build and visual regression verification remain part of Phase 7.
