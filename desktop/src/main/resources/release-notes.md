# What's New in DSE ERP v11.0.8

### Bidirectional Business Image Synchronization (All 6 Assets)
- **Real-Time Image Sync**: All 6 company assets (Invoice Logo, Authorized Signature, Email Signature Image, UPI Payment QR, Application Wallpaper Banner, and Application Splash Mark) synchronize seamlessly between Desktop and Web in real time.
- **Alias Key Mirroring**: Updates to canonical desktop keys automatically synchronize with Web alias keys (`invoice.header.logo`, `invoice.digital.seal`, `payment.upi.qr`, `application.banner`, `application.mark`).
- **Deletion Parity**: Removing an image on Web immediately clears the local desktop asset and deletes cached files without zombie re-uploads.

### Dedicated Company Email Signature Preview
- **Full Image Preview**: Added a dedicated "Preview Image" button in Desktop Settings alongside "Browse Image" and "Clear Image".
- **Asset Key Isolation**: Isolated `email.signature.image` from `company.signaturePath`, preventing cross-overwriting or accidental clearing of authorized document seals.
