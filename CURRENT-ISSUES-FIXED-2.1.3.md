# DSE ERP 2.1.3 — Current Issues Fix

## Root causes
- Quotation quick ranges reused the full legacy advanced-filter predicate. Hidden legacy controls could silently participate and clear the result set.
- Status renderers selected icons mainly by state, causing unrelated business columns to look identical.
- Sales communication logging omitted the subject column even though the email itself had a subject.
- User Access menu actions reused broad `security`/`role` semantics and used large tiled icons in menus.
- Inventory dialogs added button types but never assigned semantic graphics to the generated dialog buttons.

## Corrections
- Quotation filtering now evaluates only visible supported fields: search, quotation number, customer, from date, to date and status.
- Quick ranges update dates and re-run the simplified predicate.
- Row status icons now preserve column meaning using compact semantic icons.
- Sales email and WhatsApp logs write `Sales Invoice <invoice number>` into `communication_log.subject`.
- User Access actions use edit, lock/unlock, permission and delete icons with compact menu graphics.
- Stock Adjustment Apply/Cancel and Stock History Close buttons receive explicit icons.

## Verification
- 36 FXML files parsed successfully.
- Source searches confirmed no old Sales communication signature remains.
- ZIP integrity checked after packaging.
