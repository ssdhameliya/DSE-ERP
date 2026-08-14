# DSE ERP 7.2.4 Verification — Clock, Import Results and GST/IGST

## Static checks completed
- Version references updated to 7.2.4.
- ReminderCenterController no longer references undefined DISPLAY_DATE.
- Shared ClockService appends BusinessClock.zoneAbbreviation().
- BusinessClock continues to prefer saved company.timeZone and company.dateFormat settings.
- Document import domain fields include gst_type.
- Sales/Purchase templates contain GST and IGST sample rows.
- ImportResult supports structured row/document results.
- ImportController generates a compact summary and Excel result workbook.
- Sales/Purchase import continues to calculate line and document tax totals in application code.

## Local release gate
Run `mvn clean verify` with Java 25 and perform one GST + one IGST Sales/Purchase import before release.
