# DSE ERP 9.0.42 - Release Check

This repository is the approved 9.0.42 source baseline for release and continued development.

## Remaining verification

1. **Maven/runtime verification**
   - Static project/UI/data audits passed in the packaging environment.
   - Full Maven verification could not start there because Maven Central DNS resolution was unavailable.
   - GitHub Actions is configured to run `./mvnw -B -ntp clean verify` on the release workflow before creating native release artifacts.

## 9.0.42 UI rules to preserve

- Keep exactly two application CSS files: `light-theme.css` and `dark-theme.css`.
- Row actions must display semantic icon + visible action text without clipping.
- Dynamic TableView sizing remains globally owned; do not reintroduce per-column fixed widths.
- Reporting individual report screens should not reintroduce KPI strips removed in 9.0.42.
- Scheduled reports must continue supporting PDF, XLSX, CSV, and combined PDF + XLSX output.
- Preserve existing warning, confirmation, dialog, navigation, and business behavior unless a change is explicitly reviewed.

## Legacy Payment History compatibility

The old standalone `PaymentHistory.fxml` / `PaymentHistoryController.java` screen is intentionally absent. Compatibility routing for historical saved links remains in place and redirects legacy Payment History targets to the current Sales (`RecordPayment.fxml`) or Purchase (`PurchasePayment.fxml`) payment workspace.
