# DSE ERP 7.1.9 - Sales Tax Invoice PDF Final Verification

## Scope
Final source verification for `TaxInvoicePdfGenerator` after the agreed 7.1.9 geometry/padding correction.

## Verified invariants
1. Old fixed first-page and continuation-page item-height constants are removed.
2. Pagination reads iText's live `DocumentRenderer.getCurrentArea()`.
3. Final item capacity is calculated as `currentTopY - financialTopY - STANDARD_SECTION_GAP`.
4. `STANDARD_SECTION_GAP` remains 5pt and the final item section has no additional flow margin, so Item frame -> Bank/Calculation is governed by that single 5pt contract.
5. Closing-stack placement and final-item capacity both use the same `ClosingGeometry` calculation.
6. Compact section vertical padding token is 1pt.
7. GRAND TOTAL uses equal 18/64/18 outer proportions and a 70/30 label/value group inside the centered 64% region.
8. Product Description font remains equal to the standard table body font.
9. Rounded section styling remains in place.
10. Project version remains 7.1.9.

## Automated checks run in assembly environment
- `scripts/audit-desktop-jdbc.py` - PASS
- `scripts/audit-phase2-data-boundary.py` - PASS
- `scripts/audit-postgres-only.py` - PASS
- `scripts/audit-final-data-architecture.py` - PASS
- Root/desktop/server/shared Maven POM XML parse - PASS
- Java source brace/structure sanity check - PASS
- Focused PDF layout source assertions - PASS
- ZIP integrity checks - PASS

## Environment limitation
The application requires Java 25. The assembly environment provides Java 21 and no Maven, so a full Java 25 `mvn clean verify` and application-generated visual PDF regression still need to be run on the developer machine or GitHub Actions before production release.
