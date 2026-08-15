# Verification — DSE ERP 7.3.2 Branding Asset Pipeline + Long Reminder ID Compile Fix

## Compile defect root cause
The 7.3.2 Reminder API changed `ReminderDto.id` to `Long`, but `OperationsController.ReminderRow` remained `int` and constructed a row with:

`r.id() == null ? 0 : r.id()`

Java promotes that conditional expression to `long`; passing it to an `int` constructor produces:

`incompatible types: bad type in conditional expression / possible lossy conversion from long to int`

The Operations reminder row now stores `long`, and its null fallback is `0L`, keeping the Reminder identity contract consistent end-to-end.

## Branding presentation contract
- Application Brand: responsive 5:1 banner, center-crop to fill width, no distortion.
- Company Logo: contain, preserve ratio, no crop.
- Authorized Signature: contain, preserve ratio, no crop.
- Payment QR: contain, preserve ratio; square source recommended.
- Splash/Login/Registration/Email share the same 520 × 118 maximum banner container contract.
- Settings Application Brand preview uses the exact same `BrandImagePresenter` logic as runtime authentication screens.

## Upload safety
- Decode/validate selected image before replacing an existing asset.
- 10 MB hard maximum; >5 MB warning.
- Role-specific resolution/aspect-ratio warnings.
- Copy to temporary file, re-decode, then atomic replacement where the filesystem supports it.
- Remove obsolete extension variants only after the new asset is safely in place.
- Preserve the previous configured asset if validation/copy/decoding fails.
- Load configured branding from streams to avoid stale URI caching after replacing the same filename.

## Protected behavior
No Sales/Purchase calculations, PDF business calculations, Reminder REST behavior, database migration, Safe Rollback, Backup/Restore, Document Studio engine, or accordion navigation logic is changed by this branding refinement.
