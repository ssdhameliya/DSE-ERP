# Verification - Business Time & Import Dates - 7.2.4

## Business time source
- Desktop central clock: `org.example.util.BusinessClock`.
- Saved `company.timeZone` wins; system timezone is fallback only.
- Saved `company.dateFormat` wins; `dd/MM/yyyy` is fallback only.
- Managed Spring backend receives the workspace config path and reads `company.timeZone` directly for processing-time decisions.

## Import date behavior
- Excel numeric/formula date cells are recognized by Apache POI `DateUtil`.
- Real Excel dates are converted directly to `LocalDate`.
- Preview uses the configured application date format for mapped date fields.
- Text dates use configured format first plus supported compatibility formats.
- Blank required invoice dates are validation errors; they are not silently replaced with today.

## Regression scope retained
- Master lookup soft-deactivation behavior retained.
- Sales/Invoice Record Payment navigation fixes retained.
- No database migration rewrites historical business dates.
