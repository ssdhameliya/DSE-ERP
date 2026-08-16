# DSE ERP 7.3.9 Release Notes

## Priority: responsiveness and runtime reliability

DSE ERP 7.3.9 is an evidence-driven performance release based on the production 7.3.8 Sales Invoice corrected baseline. The primary goal is to remove the long JavaFX pulse stalls observed on macOS Retina systems while preserving the existing business behavior and document output.

### macOS rendering

- The Windows-only JavaFX software-renderer workaround is no longer forced on macOS.
- macOS can use JavaFX's native graphics pipeline instead of being locked to `prism.order=sw`.
- The Windows software-pipeline workaround remains unchanged to avoid reintroducing the historical Direct3D blank-page issue.
- Runtime diagnostics now record `prism.order` and `prism.verbose` in `performance.log`.
- Expensive Gaussian effects are disabled only on the measured macOS hotspots: Create Sale, Bank Statement and Settings. Layout, borders, spacing, colors and business controls remain unchanged.

### Create Sale

- API-backed startup data is loaded with `UiTaskExecutor` instead of serially blocking the JavaFX Application Thread.
- Payment Terms, Charges, GST Types, Transporters, Customers, Items and the next invoice number are applied after the form is already interactive.
- Edit mode safely rebinds customer/transporter/master selections after asynchronous bootstrap without replacing saved historical invoice addresses.
- Item search is debounced by 90 ms so every physical keystroke no longer rebuilds the suggestion menu.
- Item search text is pre-indexed when Items arrive, reducing repeated string construction during search.

### Bank Statement

- Statement batch discovery now runs in the background.
- Transaction and metrics loading now runs in the background and applies to JavaFX only when the requested batch is still selected.
- Refresh/select behavior is latest-result-wins, preventing stale batch results from repainting the screen.

### Settings

- Startup image preview reads/decoding/inspection run through `UiTaskExecutor` rather than the JavaFX Application Thread.
- Settings Save now batches property mutations and writes `config.properties` once instead of repeatedly rewriting the file for each field.
- User-selected image previews still update immediately after a deliberate upload/change action.

### Managed PostgreSQL reliability

- Packaged EXE/DMG installations resolve their own bundled PostgreSQL runtime before any development path, preventing stale IntelliJ paths from hijacking production startup.
- `pg_isready` is now required in production bundles and is used for protocol-level readiness instead of treating an open TCP socket as proof that PostgreSQL is healthy.
- Managed localhost verification explicitly uses non-SSL loopback connections and a short connection timeout.
- Packaged applications stop the managed PostgreSQL cluster on application exit unless the explicit `runtime.postgres.keepAliveOnExit=true` support override is set. This prevents an installer/update from replacing PostgreSQL executables while they are still running.

## Compatibility

- Database schema generation remains `1`.
- No database migration is introduced by 7.3.9.
- Existing workspace, PostgreSQL data, invoice data, PDF behavior and permissions are preserved.
- Windows and both macOS architectures remain supported by the existing release workflow.
