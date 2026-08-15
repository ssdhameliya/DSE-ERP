# DSE ERP 7.3.2 — Reminder Reliability & Control Styling Stabilization

## Reminder Center reliability
- Fixed the 7.3.1 Reminder row mapper that read alias names from a positional native-query result. This was returning `id=0` and blank reminder values, which directly caused edit HTTP 400 errors and post-create HTTP 500 errors.
- Reminder IDs are now `Long`/`long` end-to-end to match PostgreSQL `BIGSERIAL`.
- The REST path ID is authoritative for updates; request-body IDs are no longer used to decide which row is updated.
- Create now returns the new PostgreSQL ID and reloads that exact reminder instead of scanning a malformed list.
- Update, status changes and delete verify that exactly one database row was affected.
- Added the server-owned `V7_3_2__reminder_reliability` migration and registered it with the existing migration runner.
- Existing reminder status/priority/creator metadata is normalized without deleting reminder records.
- Reminder date sorting and dashboard overdue counting tolerate legacy malformed date text.
- Desktop API errors now display the server `message` plus HTTP status instead of dumping raw JSON.
- Reminder editor/details/actions were tightened to the same enterprise layout language as the register screens.

## Global ComboBox / dropdown stabilization
- Removed the dark-theme broad `.list-cell` cascade that was unintentionally styling the internal selected-value cell of every ComboBox.
- Added one shared geometry contract for closed ComboBox values, popup rows, editable ComboBoxes and ChoiceBoxes.
- Data Import mapping ComboBoxes now have enough height/padding to display the full Excel-column values.
- Light and dark themes keep their own colors while shared geometry lives in `ui-components.css`.

## Global row Actions stabilization
- Removed the legacy icon-only `row-actions` behavior from the shared component stylesheet.
- Standard row menus now use a semantic Actions icon + literal `Actions` + visible arrow.
- Standard menu width: 96–118px; standard Actions table column: 120–132px.
- Updated Sales, Purchase, Quotations, Customer, Supplier, Item Master, Inventory, Returns, Bank Statement, Backup/Restore, User Access and Reminder action columns/menus where applicable.
- CheckBoxes remain native tick/untick controls without automatic decorative icons.

## Protected areas
No business-calculation redesign was made to Sales, Purchase, Bank processing, PDF generation, Document Studio, Safe Rollback, Backup/Restore, Settings, or the 7.3.1 accordion sidebar.

## Branding asset reliability & responsive presentation
- Centralized application-brand rendering in `BrandImagePresenter`; Splash, Login, Registration and Email/OTP now use one responsive 5:1 banner contract instead of screen-specific hardcoded ImageView sizes.
- Application branding fills the available banner width without stretching. Source artwork is center-cropped to 5:1 when necessary, and Settings uses the same presentation so the administrator previews the real runtime behavior.
- Added upload-time image validation for Application Brand, Company Logo, Authorized Signature and Payment QR, including dimensions, aspect-ratio guidance and file-size protection.
- Image replacement is now staged and decoded before the active asset is replaced, with atomic move where supported. A corrupt upload can no longer delete the currently working production asset first.
- Application branding is loaded from a fresh file stream to avoid stale same-filename image caching after an upload/change.
- Company Logo and Authorized Signature retain contain semantics (no crop/stretch); Payment QR retains square/contain semantics.
- Added explicit branding names in `BrandingService` (`applicationBrandImage`, `companyLogo`, `authorizedSignature`, `paymentQrImage`) while retaining legacy aliases for compatibility.

## Compile correction
- Fixed the `possible lossy conversion from long to int` compile failure in `OperationsController`: Reminder IDs are now `long` in the Operations reminder row as well, matching the 7.3.2 `BIGSERIAL`/`Long` reminder contract.

## Unified UI / Permissions / Footer / Reminder finalization
- Standardized Settings outer gutters and fixed Application Brand preview clipping.
- Standardized Role Management and Permission Matrix top panels.
- Added current workflow permissions for Bank & Expense, Document Studio, Safe Rollback, Application Updates, Reminder completion/snooze and Communication re-send.
- Permission Matrix now supports module filtering and ADMIN-protected editing; desktop navigation reads the saved server matrix with a safe legacy fallback.
- Unified Splash, Login/Forgot Password, Registration and Email/OTP Setup around one two-panel authentication shell.
- Added one shared application footer component for all standalone authentication/startup screens and the logged-in shell.
- Stabilized MenuButton/ComboBox/context-menu geometry, widened Actions controls and removed deliberate user-name truncation.
- Communication Center now shows an icon + Re-send command button.
- Improved shared TableView sizing to sample real values, preserve readable minimums, fill unused width and keep Actions visible.
- Reminder Add/Edit/Delete/Complete/Reopen/Snooze now provide validation, confirmations and success feedback.
