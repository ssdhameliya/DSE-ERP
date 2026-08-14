# DSE ERP 7.1.8 — Financial Edit Integrity

## Fixed

- Sales edit now reconstructs taxable/net amount, GST amount, discount amount, and total amount from persisted line inputs instead of leaving derived values at zero.
- Purchase edit now reconstructs taxable/net amount, GST amount, discount amount, and total amount from persisted line inputs instead of leaving derived values at zero.
- Sales Reference No. is restored on Edit and preserved on Save/Update.
- Sales Invoice Message / Notes is preserved on Save/Update.
- Purchase attachment path is preserved when an existing purchase is edited and saved without selecting a replacement attachment.
- New Sales/Purchase forms clear persisted edit-only fields so values cannot leak from a previously edited transaction.

## Version

- Application version: **7.1.8**
- Java: **25**
- JavaFX: **25.0.2**

## Verification focus

This release is intentionally scoped to edit/save data integrity. No database schema migration is required.

## Sales Edit Metadata Integrity (follow-up build)
- Fixed Sales Edit so delivery address/GSTIN/transport/reference/notes updates do not trigger unnecessary stock reversal and line recreation when invoice lines are unchanged.
- Preserves paid amount, payment status, document status, email/WhatsApp flags, created timestamp, source and invoice type during Edit -> Save.
- Sales charges are only replaced when the charge set actually changed.
- Keeps the 7.1.8 version because this build has not yet been published to GitHub.

## Delivery Address / PO Date follow-up

- Fixed Sales Edit reload ordering so a saved independent Delivery Address and Delivery GSTIN are restored after customer selection instead of being overwritten by Billing values.
- `Same as Billing Address = true` still intentionally mirrors Billing Address/GSTIN; `false` now preserves the saved independent delivery values across Edit -> Save -> Edit.
- PO Date is now optional and user-entered. New Sales forms leave PO Date blank; changing Invoice Date or Payment Terms no longer auto-populates it.
- Removed the obsolete `PO DATE FORMATE` (`PO_DATE_FORMAT`) category/lookup from the always-run base schema so it is not recreated at startup.
- Added `V7_1_8__remove_po_date_format_master.sql` to remove the obsolete category/lookup from existing databases.
- PO Order No. generation is decoupled from the removed Master category and retains its existing fixed numbering behavior.

## Database note

This follow-up **does include a small cleanup migration** for existing databases: `V7_1_8__remove_po_date_format_master.sql`. It deletes only the obsolete PO Date Format master category/lookup; it does not alter Sales transaction data.

## Sales PO behaviour follow-up
- New Sales PO Date now follows Invoice Date + Payment Terms (for example, 15 Days = T+15).
- Existing saved PO Date is preserved when an invoice is opened for Edit; it recalculates only after Invoice Date or Payment Terms changes.
- Sales PO Order No. is now optional/user-entered. Blank values remain blank instead of being replaced by an internally generated `PO/DD-MM-YYYY/XXXX` value.
- Removed the obsolete Sales `next-order` API/client path and hard-coded PO Order generator.
- Added a separately keyed 7.1.8 cleanup migration for legacy automatically generated PO Order values.

## Sales PO lifecycle follow-up

- Fixed New Sale initialization so PO Date is reliably calculated after Payment Terms is established.
- Preserved the originally saved PO Date when an existing Sale is opened for Edit.
- Kept PO Order No. blank unless the user enters a customer PO reference.
- Added UI and server-side protection against obsolete `PO/DD-MM-YYYY/XXXX` internal references.
- Added a separately keyed database cleanup migration for existing internal-test databases.
