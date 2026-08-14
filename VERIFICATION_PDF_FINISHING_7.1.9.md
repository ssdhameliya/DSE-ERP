# DSE ERP 7.1.9 - Sales Tax Invoice finishing verification

## Scope
This verification covers only the final Sales Tax Invoice presentation changes. No Sales persistence, GST calculation, inventory, API, server or database behavior was intentionally changed.

## Verified source contracts
- `ORDER NO` is always emitted in invoice metadata; blank value displays `NA`.
- `TaxInvoiceDocument` carries `paymentTerms` from the Sales model.
- `PAYMENT TERMS` is a separate rounded card below `TERMS & CONDITIONS`, not part of invoice metadata.
- Payment terms display uses the stored Sales payment term first and derives a day count from Invoice Date / PO Date only when the stored term is blank.
- Terms/Payment/Signature continue to participate in the existing measured dynamic closing-stack geometry.
- Signature inner layout uses equal outer gutters with `9 / 30 / 9` proportions.
- Transporter presentation no longer uses the former split-column layout; all available details are centered in one full-width rounded card.

## Checks run in this environment
- Parent, desktop, server and shared POM XML parsing: PASS
- `audit-desktop-jdbc.py`: PASS
- `audit-phase2-data-boundary.py`: PASS
- `audit-postgres-only.py`: PASS
- `audit-final-data-architecture.py`: PASS
- Focused source assertions for the five contracts above: PASS

## Environment limitation
The available runtime is Java 21 and Maven is not installed. DSE ERP 7.1.9 requires Java 25, so a fresh `mvn clean verify` and rendered PDF visual check must be completed on a Java 25 development machine or GitHub Actions before release.
