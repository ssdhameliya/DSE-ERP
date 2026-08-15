# DSE ERP 7.3.0 — Universal Document Studio & Settings Workspace

## Universal Document Studio

- Renamed Purchase Document Studio to **Document Studio** and generalized the 7.2.7 designer so one screen can handle general PDFs and ERP document templates.
- **Import PDF** now opens any PDF as a General PDF document without forcing an ERP document type.
- Added **Blank PDF** and **ERP Template** start paths in the same Document Library.
- General PDFs can later be connected to ERP data, and ERP templates can be disconnected back to general documents without changing the original imported file outside the workspace.
- Preserved the 7.2.7 safe existing-PDF editing model: detected existing text can be selected, masked, replaced, or converted into an ERP field while the imported source is kept protected.
- Added universal document types for Purchase Invoice, Purchase Order, Quotation, Delivery Challan, Credit Note, Debit Note, Payment Receipt, Sales Invoice, Sales Return, and Custom ERP documents.
- Added document-type-aware field catalogs and real/sample data preview. Purchase and Quotation can load existing ERP records in the preview selector.
- Generalized the dynamic line-item table so the same renderer can be reused by Purchase and Quotation templates.
- Added page actions for blank-page insertion, rotation, and page deletion on the workspace copy.
- Added General PDF / ERP Template categorization in the Document Library, with search/filter/default/version/status controls.
- Existing 7.2.7 Purchase templates remain compatible and are automatically treated as ERP templates when loaded.

## ERP Runtime Integration

- Existing Purchase custom-template integration remains active with the established built-in Purchase renderer as a fallback.
- Quotation can now use an active default Quotation template from Document Studio; any template/data/render problem automatically falls back to the existing Quotation renderer.
- **Sales PDF generation is intentionally unchanged.** Sales templates may be designed/previewed in Document Studio, but the existing Sales runtime route remains protected.

## Settings Workspace Redesign

- Replaced the tall vertical settings navigation with a horizontal, full-width category navigator directly below the existing Settings header.
- Selecting Company & Billing, Payment & Bank, Invoice & Delivery, Notifications, Email Settings, Workspace & Storage, or Application Updates loads that existing settings panel into one large content workspace below the navigator.
- Existing settings fields, save/test actions, configuration keys, and the approved three-image Brand & Identity Assets panel are preserved.
- Added responsive horizontal scrolling for settings categories on smaller windows while giving the selected content panel the full available width.

## Safety / Compatibility

- Version synchronized to **7.3.0** across Maven modules, runtime metadata, UI version labels, server metadata, packaging messages, and shared runtime contract.
- Safe Rollback compatibility catalog now recognizes 7.3.0 on the same current schema generation.
- No database migration is introduced by this release.


## Splash Branding & Settings Workspace Finalization
- Splash branding now loads from saved Settings before the first splash frame: Application Name, Tagline, Startup Message and Application Brand Image.
- Startup/finalizing/opening text and primary window title use the configured application name, with DSE ERP retained only as a safe fallback.
- Added a splash branding refresh after background config reload.
- Removed the redundant Settings Workspace heading/description/hint row.
- Reduced Settings header and workspace padding and aligned the three major page regions.
- Enlarged horizontal category tiles so complete labels are visible at normal desktop widths while preserving horizontal scrolling on smaller windows.
- Existing settings persistence, Document Studio, Sales route, Safe Rollback and Backup/Restore behavior remain unchanged.

## 7.3.0 Stabilization Refresh — Document Studio Import + Settings Tall Panels

- Fixed the Document Studio metadata reload failure that could show **Template unavailable** immediately after General PDF, Blank PDF, or ERP template creation.
- Computed ERP connection state is no longer serialized; older 7.3.0 template metadata with that property is loaded tolerantly and repaired automatically.
- Every newly created/imported template is reloaded as a verification step before the designer opens.
- PDF import now validates the source and creates a normalized private workspace copy while preserving the exact imported file as `original.pdf`.
- Password-protected PDFs prompt once for a valid password; when credentials permit editing/extraction, the workspace copy is saved without encryption. Passwords are never persisted.
- PDFs opened with restricted user permissions request the owner password before editable import.
- Imported PDF editing now adds selectable raster-image detection/conversion and AcroForm field replacement alongside existing text replacement. Complex vectors/scanned regions continue to use Replace / Hide Area.
- Company & Billing and Payment & Bank Settings panels were compacted to fit common desktop heights more effectively.
- Settings category switching now resets the shared scroll viewport to the top and forces a fresh layout pass, preventing stale sizing on the first two taller sections.
- Sales PDF runtime route, Safe Rollback, Backup & Restore, database schema, and business calculations are unchanged.

### Final Settings Fit Correction
- Company & Billing now uses a four-column desktop grid for the eight business fields and a compact identity row for Application Name, Tagline and Startup Message, allowing the approved three-image Brand & Identity Assets row to remain visible with substantially less vertical scrolling.
- Payment & Bank now uses a three-column/two-row bank-details grid and a more compact QR preview area.
- Company/Payment section switching always performs a fresh layout pass and resets the shared settings viewport to the top.
