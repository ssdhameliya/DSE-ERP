# What's New in DSE ERP v11.0.9

### Universal 3D Button Disambiguation & Distinct Semantic Colors
- **Eliminated Button Color Collisions**: View, Open File, Download, Refresh, Reset, and Audit Trail buttons now each feature distinct semantic palettes and icons:
  - **View**: Cyan (`#06b6d4`, `fas-eye`)
  - **Open File**: Amber (`#f59e0b`, `fas-folder-open`)
  - **Download**: Sky Blue (`#0284c7`, `fas-cloud-download-alt`)
  - **Refresh**: Emerald Mint (`#10b981`, `fas-sync-alt`)
  - **Reset**: Coral Rose (`#f43f5e`, `fas-broom`/`fas-undo`)
  - **Audit Trail**: Deep Indigo (`#6366f1`, `fas-fingerprint`)
- **Tactile 3D Depth**: Interactive elevation gradients, subtle borders, and smooth press states across all 72 screens.

### Dynamic Table Layout & Refresh Drift Elimination
- **Consistent Layout Before & After Refresh**: Added `forceLayout()` with width cache invalidation, ensuring scrollbar changes upon data load never clip table columns.
- **Full Header Text Measurement**: Column headers measure full multi-word titles and icons rather than single tokens, eliminating truncated text.
- **Instant Tab Reflow**: Integrated universal `TabPane` listener in `ProfessionalUiEnhancer` for seamless column resizing upon tab switching.
- **Reporting Dashboard Table Integrity**: Re-initialized dynamic auto-fit on all reporting tables (`tblSales`, `tblPurchases`, `tblSavedReports`, `tblSchedules`, `tblReport`).

### Universal Reference Hyperlinks
- **Click-to-Copy & Inspect**: All 75 document reference types (Invoices, Quotations, Purchase Orders, Vouchers, Customers, Suppliers) now render with interactive hyperlink styling (`#38bdf8`, hover underline, pointer cursor).

### Synchronous Modal Pre-Rendering
- **Zero Sizing Jump or Color Flash**: Dialogs and stages are fully measured, styled, and centered during `DIALOG_SHOWING` before the first frame appears on screen.

---

# What's New in DSE ERP v11.0.8

### Bidirectional Business Image Synchronization (All 6 Assets)
- **Real-Time Image Sync**: All 6 company assets (Invoice Logo, Authorized Signature, Email Signature Image, UPI Payment QR, Application Wallpaper Banner, and Application Splash Mark) synchronize seamlessly between Desktop and Web in real time.
- **Alias Key Mirroring**: Updates to canonical desktop keys automatically synchronize with Web alias keys (`invoice.header.logo`, `invoice.digital.seal`, `payment.upi.qr`, `application.banner`, `application.mark`).
- **Deletion Parity**: Removing an image on Web immediately clears the local desktop asset and deletes cached files without zombie re-uploads.

### Dedicated Company Email Signature Preview
- **Full Image Preview**: Added a dedicated "Preview Image" button in Desktop Settings alongside "Browse Image" and "Clear Image".
- **Asset Key Isolation**: Isolated `email.signature.image` from `company.signaturePath`, preventing cross-overwriting or accidental clearing of authorized document seals.
