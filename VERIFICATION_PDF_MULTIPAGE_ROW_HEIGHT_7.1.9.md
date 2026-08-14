# DSE ERP 7.1.9 — Multi-page Item Row Height Verification

Scope: Sales Tax Invoice pagination only.

Verified source contracts:
- Single-page invoice path remains unchanged.
- Multi-page invoices target a maximum of 20 real items on non-final pages.
- Page 1 computes one standard multi-page item-row minimum height from the available item region.
- The same row height is reused on every later page.
- Final-page blank filler rows use the same standard row height as real rows.
- Therefore the final page has fewer total rows rather than compressed/narrower rows.
- Full invoice header/address/transporter repetition remains enabled on continuation pages.
- Page X of Y footer numbering remains enabled only for multi-page PDFs and does not move the centered footer address.

Environment verification:
- All four repository architecture guards passed.
- Parent, desktop, server and shared POM XML files parsed successfully.
- Full Maven verification was not executable in this environment because Maven is unavailable and the installed JDK is 21 while the project targets Java 25.
