# DSE ERP 7.1.9 - Sales Tax Invoice Layout Integrity

## Final PDF layout correction
- Final-page item capacity now uses iText's live remaining page area instead of fixed first/continuation height constants.
- Item-table bottom and Bank/Calculation top share one geometry contract: the visible gap is exactly the standard 5pt section gap by construction.
- Any recovered vertical space from compact/dynamic lower sections is absorbed into blank item-grid rows rather than rendered as detached white space.
- Invoice/PO metadata cards use 1pt compact vertical padding; Transport, Bank rows, Calculation rows, INR/Grand Total, Terms and Signature use the same compact vertical rhythm where appropriate.
- GRAND TOTAL is centered as one complete label+value group: equal 18% outer gutters surround a 64% content group, with the label/value balanced inside it.
- Rounded primary section styling from the first 7.1.9 pass is retained.
- Product Description continues to use the same body font size as other item row values.
- Footer remains physically anchored to the bottom of the final page.

## Compatibility
- No changes to invoice calculations, GST/IGST logic, charges, amount-in-words, customer mapping, PO data, transporter data, bank data, terms content, logo/signature data, or persistence.
- Version remains 7.1.9.

## PDF finishing corrections
- Sales Tax Invoice always renders `ORDER NO`; a missing customer PO/order reference is displayed as `NA` without modifying stored Sales data.
- Added a dedicated dynamic `PAYMENT TERMS` card directly below `TERMS & CONDITIONS`. The stored Sales payment term is used first; if unavailable, a safe display fallback is derived from Invoice Date -> PO Date.
- Signature content now uses a balanced 9 / 30 / 9 inner layout while retaining the existing dynamic closing-stack measurement.
- Transport details now render in one 100%-width rounded card with centered content and consistent spacing between Transporter, GSTIN, Vehicle and Contact details.

## Final PDF finishing correction
- Payment Terms moved into Bank Details.
- Terms & Conditions restored to the original dynamic 49/2/49 pairing with Signature.
- Signature retains the balanced 9/30/9 inner composition.
- Transporter uses 5pt left/right outer padding and dynamically shares remaining width across present fields only.

- Final PDF transporter strip now uses content-proportional single-line sizing with no reserved side gutters; dense rows compress slightly instead of wrapping.

### Final footer alignment polish
- Centered the footer address/value within its existing fixed footer band while preserving equal left/right safety padding and the existing footer bar geometry.

## Final item table and signature polish
- Replaced fixed Sales Tax Invoice item-table column percentages with content-aware sizing for SR No., HSN, Qty, Unit Rate, Unit and Amount.
- Product Description is now the sole flexible column and receives all remaining table width after the other columns are sized from their widest header/data values.
- Added protective min/max bounds so unusually large numeric values cannot collapse Product Description or destabilize the table.
- Expanded the signature card inner content layout from 9/30/9 to 4/40/4 while preserving the existing dynamic Terms/Signature 49/2/49 row and page-height behavior.
- Increased the signature image fit area to use the recovered horizontal space without changing the closing-stack height model.

### Multi-page footer pagination polish
- Multi-page invoices now display `Page X of Y` in the bottom-right footer bar only when more than one page exists.
- The centered footer address remains unchanged and is not shifted by the page indicator.
- Full invoice header/details/address/transporter continue to repeat on every page.
- Non-final pages remain capped at 20 real items (subject to physical fit); the final page uses its smaller measured item region above Bank/Calculation and may use filler rows only there.

## Multi-page row-height consistency
- Multi-page item rows now reuse one standard row height across every page.
- Final pages use fewer total rows at the same height; any remaining capacity is filled with same-height blank grid rows above the closing financial stack.
- Single-page invoice rendering is unchanged.
