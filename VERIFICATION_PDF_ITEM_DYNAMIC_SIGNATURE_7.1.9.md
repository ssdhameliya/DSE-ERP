# DSE ERP 7.1.9 — PDF Item Dynamic Width + Signature Verification

## Scope
Only the Sales Tax Invoice item-table column sizing and signature inner composition were changed.

## Item table
- PASS: Removed fixed `{7, 14, 39, 9, 12, 8, 14}` percentage widths.
- PASS: Non-description columns are derived from the widest header/data value for the current invoice.
- PASS: Width calculations have protective minimum/maximum bounds.
- PASS: Product Description receives the complete remaining table width.
- PASS: Header and body share the same calculated point-width array.
- PASS: Existing row font, data mapping, filler rows and pagination logic are unchanged.

## Signature
- PASS: Outer Terms/Signature row remains dynamic `49 / 2 / 49`.
- PASS: Signature inner layout widened from `9 / 30 / 9` to `4 / 40 / 4`.
- PASS: Signature image fit area increased from `120 x 42` to `174 x 46` points.
- PASS: No fixed height was introduced.

## Architecture / structure
- PASS: Parent, desktop, server and shared POM XML parsing.
- PASS: `audit-desktop-jdbc.py`.
- PASS: `audit-phase2-data-boundary.py`.
- PASS: `audit-postgres-only.py`.
- PASS: `audit-final-data-architecture.py`.
- PASS: Controlled source assertions and brace balance.

## Environment limitation
The verification environment provides Java 21 and no Maven executable. The project requires Java 25, so a fresh `mvn clean verify` remains the final local/GitHub release gate.
