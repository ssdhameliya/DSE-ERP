# DSE ERP 7.2.4

## Release focus
- Shared application clocks now follow the saved business timezone/date format and show the active timezone abbreviation (for example IST or UTC).
- Data Import completion popup is compact and no longer expands with every row/reference.
- Every import generates an Excel result workbook under the workspace Imports/Results folder with Summary and Import Results sheets.
- Sales and Purchase import templates now include `gst_type` and sample both GST (intra-state) and IGST (inter-state) documents.
- Sales/Purchase import calculates tax from line rate, quantity and `gst_percent`; imported tax amounts are not trusted from spreadsheet totals.
- Sales stores the selected GST/IGST treatment on the imported invoice; Purchase stores the same treatment in its GST treatment field.
- Real Excel date handling and Settings-driven business time/date behavior from 7.2.3 are retained.
- Fixed `ReminderCenterController` compile regression caused by the removed `DISPLAY_DATE` constant.

## Tax import rules
- `gst_type = GST`: DSE ERP treats the tax as intra-state GST and reports the configured rate as equal CGST + SGST halves.
- `gst_type = IGST`: DSE ERP treats the full GST rate as IGST.
- A single invoice may not mix GST and IGST treatment across its rows.
- If `gst_type` is blank, DSE ERP attempts to infer treatment from company and party GSTIN state codes, otherwise defaults to GST.

## Release validation
Run `mvn clean verify` in the production Java 25 environment before tagging and publishing.

## Runtime version synchronization correction
- Corrected the Spring backend `dse.app.version` from the stale `7.1.9` value to `7.2.4`.
- Desktop/runtime contract, Spring backend health endpoint, POMs, runtime manifest, and native installer version are now aligned on `7.2.4`.
- Updated Windows/macOS build examples and development/runtime script labels to `7.2.4`.
- This prevents the desktop from rejecting the freshly built 7.2.4 backend as an incompatible stale server.
