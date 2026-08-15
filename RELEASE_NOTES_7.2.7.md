# DSE ERP 7.2.7

## Purchase Document Studio — Advanced Editing & Usability

DSE ERP 7.2.7 builds on the live 7.2.6 baseline and keeps the Document Studio integration **Purchase-only**. Sales PDF generation and Sales controller routes are not changed.

### Edit text already present in an imported PDF

- Added **Edit Existing PDF Text** mode.
- PDFBox text positions are detected per imported page and shown as selectable editing targets.
- Double-click existing text to load it into the Properties panel.
- Applying a replacement stores a precise **whiteout + editable text overlay**; the imported source PDF remains immutable.
- Existing text can be converted directly to a selected Purchase ERP field (for example Supplier Name, Purchase Number or Date).
- If a page is scanned/flattened and no text is extractable, the designer clearly directs the user to **Replace / Hide Area** instead of pretending the PDF is editable.

### Larger, user-friendly designer side panels

- Left tools panel increased to a wider workspace and reorganized into **INSERT / DATA / PAGES** tabs.
- Right properties panel increased and reorganized into **OBJECT / IMAGE / ARRANGE** tabs.
- Apply / Duplicate / Delete actions are now in a sticky bottom action bar and no longer disappear when property content scrolls.
- Added Fit Page and Fit Width controls.

### More design tools

- Heading and normal Text.
- Image upload and replace.
- Quick insert from configured Settings assets: **Company Logo, Authorized Signature, Application Brand Image and Payment QR**.
- Replace / Hide Area, Rectangle and Line.
- Dynamic Purchase Item Table and Purchase ERP fields.
- Image fit mode (Fit / Fill / Stretch), preserve aspect ratio, opacity and rotation.
- Layer order controls: Bring to Front, Send to Back, Move Forward, Move Backward.
- Page alignment controls: Left, Center, Right, Top, Middle and Bottom.
- Object lock toggle.

### Settings layout refinement

- Application Name, Application Tagline and Startup Message now sit at the bottom of the main **Company & Billing** form instead of occupying a separate middle card.
- The approved three-image **Brand & Identity Assets** panel remains in place with Application Brand Image, Company Logo and Authorized Signature side-by-side.

### Compatibility and rollback

- Application version updated to 7.2.7.
- Database compatibility remains on schema generation 1; no new database migration is introduced by this release.
- Safe Rollback catalog recognizes 7.2.7 on the same schema generation.
- Existing 7.2.6 Safe Rollback behavior is otherwise unchanged.

### Regression boundary

The following files were hash-compared against the live 7.2.6 baseline and remain unchanged:

- `SalesController.java`
- `SalesListController.java`
- `InvoicePdfService.java`

This keeps the existing Sales PDF route isolated from the Purchase Document Studio enhancement.
