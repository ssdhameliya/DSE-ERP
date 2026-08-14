# DSE ERP 7.1.8 — Sales PO Lifecycle Verification

## Implemented behavior

- New Sale explicitly establishes the default Payment Terms before calculating PO Date.
- New Sale PO Date is calculated from Invoice Date + Payment Terms.
- Invoice Date or Payment Terms changes recalculate PO Date during normal user interaction.
- Edit mode restores the PO Date persisted with the original Sale and suppresses automatic recalculation while the record is loading.
- PO Order No. is optional/customer-owned. New Sales start blank.
- The obsolete internal `PO/DD-MM-YYYY/XXXX` sequence is never shown or persisted as a customer PO reference.
- Existing manual customer PO references are preserved.
- A separately keyed `V7_1_8_2__enforce_customer_po_reference` migration clears legacy generated PO values even if earlier 7.1.8 internal migrations were already recorded.

## Fresh verification evidence

All repository architecture guards passed:

- `scripts/audit-desktop-jdbc.py` — PASS
- `scripts/audit-phase2-data-boundary.py` — PASS
- `scripts/audit-postgres-only.py` — PASS
- `scripts/audit-final-data-architecture.py` — PASS

Focused source assertions: **18/18 PASS**.

Independent compiled PO behavior smoke test:

- 14/08/2026 + 15 Days = 29/08/2026 — PASS
- 14/08/2026 + 30 Days = 13/09/2026 — PASS
- Due on Receipt = 14/08/2026 — PASS
- `PO/14-08-2026/0004` is treated as obsolete generated data — PASS
- `CUSTOMER-PO-778` is preserved — PASS

Maven POM XML parsing passed for parent, desktop, server, and shared modules.

## Environment limitation

The verification container provides JDK 21 and does not provide Maven. The project requires JDK 25, so a complete `mvn clean verify` was not executable here. Run the final Java 25 Maven verification locally or through the repository GitHub Actions workflow before a production release.
