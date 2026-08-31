# DSE ERP 9.0.42 - Open Items

This repository is the approved 9.0.42 source baseline for continued development.

## Open items

1. **Payment History integration**
   - The user's current live project contains a long-standing Payment History screen.
   - `PaymentHistory.fxml` and `PaymentHistoryController.java` were not present in the latest source archive used to assemble this 9.0.42 baseline.
   - Reconcile the live Payment History implementation into this branch before treating this source as feature-complete.
   - Preserve the current 9.0.42 two-theme, semantic-icon, dynamic-table, dialog/confirmation, and navigation contracts when merging it.

2. **Local Maven/runtime verification**
   - Static project/UI/data audits passed in the packaging environment.
   - Full Maven verification could not start there because Maven Central DNS resolution was unavailable.
   - Run `./mvnw -B -ntp clean verify` (or `mvnw.cmd -B -ntp clean verify` on Windows) in a normal networked development environment before production release.

## 9.0.42 UI rules to preserve

- Keep exactly two application CSS files: `light-theme.css` and `dark-theme.css`.
- Row actions must display semantic icon + visible action text without clipping.
- Dynamic TableView sizing remains globally owned; do not reintroduce per-column fixed widths.
- Reporting individual report screens should not reintroduce KPI strips removed in 9.0.42.
- Scheduled reports must continue supporting PDF, XLSX, CSV, and combined PDF + XLSX output.
- Preserve existing warning, confirmation, dialog, navigation, and business behavior unless a change is explicitly reviewed.
