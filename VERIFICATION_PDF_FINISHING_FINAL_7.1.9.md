# DSE ERP 7.1.9 — PDF finishing final verification

Scope: final Sales Tax Invoice finishing corrections only.

## Verified source contracts

- Payment Terms is rendered inside Bank Details as a normal bank row.
- The separate Payment Terms card beneath Terms & Conditions has been removed.
- Terms & Conditions and Signature are restored to the same dynamic `49 / 2 / 49` closing row; no fixed height was introduced.
- Signature retains the `9 / 30 / 9` inner composition.
- Transporter is one full-width rounded card with no reserved outer left/right gutters.
- Remaining Transporter width is distributed proportionally to each visible field's content length, so long fields receive more room and missing fields reserve no empty slot.
- Transporter font size compresses only for unusually dense rows to preserve the single-line contract.
- Existing 7.1.9 Order No fallback, item-to-bank geometry, Grand Total composition, and dynamic closing-stack measurement remain unchanged.

## Automated checks executed

- `audit-desktop-jdbc.py` — PASS
- `audit-phase2-data-boundary.py` — PASS
- `audit-postgres-only.py` — PASS
- `audit-final-data-architecture.py` — PASS
- Parent/desktop/server/shared POM XML parse — PASS
- Focused PDF finishing source assertions — PASS
- Project version remains 7.1.9 — PASS

## Environment limitation

The execution environment provides Java 21 and no Maven command. The project requires Java 25, so a fresh full `mvn clean verify` and rendered-PDF visual check must be performed on the Java 25 development machine or GitHub Actions before release.
