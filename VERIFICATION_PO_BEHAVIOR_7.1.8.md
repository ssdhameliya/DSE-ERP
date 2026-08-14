# DSE ERP 7.1.8 — Sales PO Behaviour Verification Evidence

## Scope

This follow-up keeps the application version at **7.1.8** and changes only the Sales PO behaviour requested for internal testing:

1. New Sales PO Date is calculated from Invoice Date + Payment Terms.
2. Changing Invoice Date or Payment Terms recalculates PO Date.
3. Loading an existing Sale preserves its stored historical PO Date until the user changes Invoice Date or Payment Terms.
4. PO Order No. is user-entered and optional; a blank value remains blank after Save/Reopen.
5. The legacy hard-coded Sales PO generator and `/sales/next-order` API path are removed.
6. Existing machine-generated `PO/DD-MM-YYYY/XXXX` values are cleared by a separately keyed 7.1.8 follow-up migration, while other customer PO references are preserved.

## Evidence executed

All repository architecture guards passed:

- `scripts/audit-desktop-jdbc.py` — PASS
- `scripts/audit-phase2-data-boundary.py` — PASS
- `scripts/audit-postgres-only.py` — PASS
- `scripts/audit-final-data-architecture.py` — PASS

Focused source assertions: **14 PASS / 0 FAIL**.

Confirmed examples using Invoice Date **14/08/2026**:

- `15 Days` -> PO Date `29/08/2026`
- `30 Days` -> PO Date `13/09/2026`
- `Due on Receipt` -> PO Date `14/08/2026`

Confirmed source invariants:

- New Sale invokes `updatePoDateFromPaymentTerms()`.
- Invoice Date and Payment Terms both have PO-Date recalculation listeners.
- `loadSale()` suppresses those listeners while restoring the persisted PO Date.
- `BusinessOperationsService.saveSale()` no longer calls an automatic PO-order generator.
- No `PO/DD-MM-YYYY/XXXX` hard-coded Sales generator remains in production Java source.
- `/api/operations/sales/next-order` is removed from server and desktop API code.
- `V7_1_8_1__remove_legacy_auto_po_order` is registered in the migration runner.
- The old `PO_DATE_FORMAT / PO DATE FORMATE / POFMT001` seed remains absent from the always-run base schema.

## Migration safety

The earlier `V7_1_8__remove_po_date_format_master` migration may already be recorded as executed in an internal 7.1.8 database. For that reason the legacy PO-order cleanup is intentionally a new migration key:

`V7_1_8_1__remove_legacy_auto_po_order`

It only clears `sales_header.order_no` values matching the exact legacy generated shape:

`PO/DD-MM-YYYY/XXXX`

Other customer-entered PO references are left unchanged.

## Build limitation

The verification environment provides **JDK 21** and does not provide Maven. DSE ERP 7.1.8 requires **JDK 25**, so a fresh full `mvn clean verify` could not be executed here. Final release approval should still include a JDK 25 Maven build locally or through GitHub Actions.
