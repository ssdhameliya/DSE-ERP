# DSE ERP 7.1.9 - PDF Footer Centering Verification

Scope: Sales Tax Invoice footer polish only.

## Verified behavior
- Footer address/value remains inside the existing fixed-height footer address band.
- Existing footer separator, two-colour bottom bar, Y coordinates, height, font and colour are unchanged.
- Footer address/value now uses `TextAlignment.CENTER` instead of `TextAlignment.LEFT`.
- Existing equal 8pt left/right safety padding is preserved, producing balanced visual breathing room on both sides.
- No Sales, GST, PO, transporter, bank, calculation, terms, signature, pagination or persistence logic was changed.
- Project version remains 7.1.9.
