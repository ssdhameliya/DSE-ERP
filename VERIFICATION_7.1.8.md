# DSE ERP 7.1.8 Verification Note

## Implemented integrity fixes

- Sales REST line reconstruction recalculates discount, taxable/net, GST, and total values on reload/edit.
- Purchase REST line reconstruction recalculates discount, taxable/net, GST, and total values on reload/edit.
- Sales Reference No. loads and saves instead of being blanked during Edit -> Save.
- Sales Invoice Message / Notes saves from the editor instead of being replaced with an empty value.
- Existing Purchase attachment path survives Edit -> Save when no replacement attachment is selected.
- New Sales/Purchase editor state clears persisted edit-only values to prevent cross-record carryover.
- Product/runtime version metadata is 7.1.8.

## Checks run in the preparation environment

PASS: `scripts/audit-desktop-jdbc.py`

PASS: `scripts/audit-phase2-data-boundary.py`

PASS: `scripts/audit-postgres-only.py`

PASS: `scripts/audit-final-data-architecture.py`

PASS: Maven POM XML parsing for parent/shared/server/desktop modules.

PASS: focused SalesLine/PurchaseLine financial calculation smoke using 10 x 1000, 10% discount, 18% GST; expected net 9000, GST 1620, total 10620.

PASS: focused source assertions for edit mapper recalculation, Sales notes/reference preservation, and Purchase attachment preservation.

## Full build limitation

The preparation environment provides JDK 21 and no Maven, while this project requires JDK 25. A fresh full `mvn clean verify` could therefore not be executed here. Before production release, run the repository build with JDK 25 or allow GitHub Actions CI to execute it.

## Follow-up Sales metadata update verification
- PASS: metadata-only Sales updates skip stock reversal/reapply when line values are unchanged.
- PASS: unchanged Sales charges are not deleted/recreated.
- PASS: server preserves payment/workflow/communication state during Sales Update.
- PASS: desktop carries existing payment/workflow/communication state in its edit payload.
- PASS: all four repository data-architecture audit scripts.
- LIMITATION: full Maven/JDK 25 compile could not be executed in the packaging environment (JDK 21 present, Maven unavailable). Run `mvn clean verify` with JDK 25 locally or in GitHub Actions before production release.

## Delivery / PO Date follow-up verification

- PASS: Sales `loadSale()` captures persisted `sameAsBilling`, Delivery Address and Delivery GSTIN before selecting the customer, then restores the correct delivery state after customer listeners finish.
- PASS: New Sales PO Date is explicitly initialized to `null`.
- PASS: Invoice Date and Payment Terms no longer have listeners that populate PO Date.
- PASS: `PO_DATE_FORMAT`/`PO DATE FORMATE`/`POFMT001` seeds are absent from the always-run `V5_1_2__server_owned_schema.sql`.
- PASS: `V7_1_8__remove_po_date_format_master.sql` is registered in `SecurityFinancialMigrationRunner` for existing databases.
- PASS: PO Order numbering no longer depends on the removed `PO_DATE_FORMAT` master category.
