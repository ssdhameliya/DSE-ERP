# DSE ERP 7.1.9 — Multi-Page Tax Invoice Pagination Verification

## Locked pagination contract

- Every physical invoice page repeats the full approved header stack: company/logo, invoice details, billing/delivery, and transporter.
- Non-final pages contain up to 20 real item rows only.
- Non-final pages do not create blank/filler item rows.
- Real rows on non-final pages expand uniformly to consume the available item area and finish above the footer safety gap.
- Every non-final page receives its own fixed footer.
- Only the final page renders blank item-grid filler rows, and only to consume the flexible area above Bank/Calculation.
- Bank/Calculation, INR/Grand Total, Terms/Signature remain final-page-only.
- Existing dynamic item column widths and Product Description remainder-width behavior are unchanged.
- Wrapped descriptions remain height-safe: 20 is a maximum, not permission to overlap the footer.

## Fresh source checks

PASS — `MAX_ITEMS_PER_PAGE = 20` is defined and enforced.

PASS — compact `TAX INVOICE - CONTINUED` continuation renderer is removed from the pagination path.

PASS — continuation pages call the same `addCompanyHeader`, `addInvoiceTitleAndMeta`, `addAddressCards`, and `addTransportStrip` methods as page 1.

PASS — intermediate pages call `addFooter(...)` before the page break.

PASS — intermediate-page rendering uses `addExpandedContentItemsTable(...)`, which expands real rows only and never calls `addFillerRow(...)`.

PASS — final-page rendering continues through `addItemsTable(...)`, where filler rows are allowed to preserve the approved closing-stack geometry.

PASS — all four repository architecture guards pass.

## Environment limitation

The execution environment used for this source build provides JDK 21 and no Maven executable. DSE ERP 7.1.9 requires Java 25, so a fresh full `mvn clean verify` and rendered multi-page PDF visual check must be completed on the Java 25 development machine or GitHub Actions before release.
