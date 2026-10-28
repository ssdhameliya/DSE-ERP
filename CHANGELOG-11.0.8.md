# Changelog — DSE ERP v11.0.8

## Release 11.0.8 — Bidirectional Business Image Synchronization (All 6 Assets), Email Signature Isolation & Preview

- **Release Date:** 2026-10-28
- **Baseline:** Git tag `v11.0.7`
- **Type:** Business Asset Storage, Real-Time Bidirectional Synchronization & Preview Release

---

## Key Features & Improvements

### 1. Full Bidirectional Synchronization for All 6 Business Images
- **Problem:** Desktop and Web business assets fell out of sync. Deleting an image on Web caused Desktop's sync routine to falsely detect a "missing remote asset" and re-upload the cached local file back to the server (zombie resurrection). Furthermore, Desktop sync only executed if `ConfigManager.isSharedClient()` was true, preventing sync in standard local or direct API session runtimes.
- **Solution:**
  - Updated `SharedAssetBridge.java` to synchronize whenever `ApiSession.isEstablished()` is true, regardless of client deployment mode.
  - When an asset pointer is empty on the server (deleted on Web or server), Desktop explicitly clears its local property (`ConfigManager.setLocalProperty(key, "")`) and removes the cached asset file without re-uploading.
  - Added Web alias key synchronization (`invoice.header.logo`, `invoice.digital.seal`, `payment.upi.qr`, `application.banner`, `application.mark`) whenever Desktop publishes or removes an asset.

### 2. Company Email Signature Preview & Asset Isolation
- **Problem:** In Desktop and Web settings, Company Email Signature had only "Browse" and "Clear" options with no "Preview" button. Additionally, Web settings previously collided `company.signaturePath` with `email.signature.image`, causing email signatures to overwrite or wipe invoice authorized seals.
- **Solution:**
  - Added `btnPreviewSignatureImage` in `EmailSettingsPanel.fxml` and `@FXML private void previewSignatureImage()` in `SettingsController.java` to preview email signature images directly.
  - Strictly isolated `email.signature.image` from `company.signaturePath` and `invoice.digital.seal`.
  - Added `email.signature.image` to `ServerResourceService.migrateLegacyDiskAssets()` for automatic server migration.

### 3. All 6 Managed Business Assets Supported
1. `company.logoPath` (`invoice.header.logo`) — Invoice Logo
2. `company.signaturePath` (`invoice.digital.seal`) — Authorized Signatory Seal
3. `email.signature.image` — Corporate Email Signature Image
4. `payment.qrImagePath` (`payment.upi.qr`) — UPI QR Payment Code
5. `application.brandImagePath` (`application.banner`) — Application Wallpaper / Banner
6. `application.markImagePath` (`application.mark`) — Application Splash Mark / Logo
