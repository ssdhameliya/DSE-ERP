# DSE ERP 7.2.6 — Safe Rollback Verification

## Scope

- Baseline: DSE ERP 7.2.5 IntelliJ Purchase Document Studio Compile Fixed.
- New feature: Safe Rollback only.
- Main navigation location: immediately below Settings and above Backup & Restore.
- Permission boundary: uses the existing `BACKUP.VIEW` permission, which the desktop role matrix restricts to ADMIN.

## Data protection

- Application-only rollback does not call database restore.
- A new `Before-Rollback-<from>-to-<target>.pgbackup` is created and validated before launch.
- Config and Templates are snapshot into the recovery point.
- Attachments and Documents are explicitly preserved in place.
- Full database restore remains a separate explicit Backup & Restore action.

## Compatibility gate

- Running build exposes current/min/max database compatibility metadata from `app-version.properties`.
- The rollback button is disabled if the target schema is unknown or incompatible.
- Known 7.2.2 / 7.2.4 / 7.2.5 / 7.2.6 schema generation is recorded as schema 1 for the current rollback catalog.

## Installer handling

- Previous installer can be imported locally.
- Exact prior GitHub Release can be downloaded by version.
- GitHub downloads require a checksums file and SHA-256 verification.
- Retained packages are copied under `Updates/Rollback/Packages`.
- Existing detached Windows/macOS installer launcher is reused so the ERP can close before replacement.

## Static verification

- All FXML documents are XML parsed during packaging verification.
- Version markers are aligned to 7.2.6 in Maven modules, runtime contract, server configuration, desktop resource version and packaging labels.
- Existing Sales source is not modified by Safe Rollback.

## Environment limitation

The build environment used to package this source has JDK 21 and no Maven installation, while this DSE ERP project targets Java 25. A full `mvn clean verify` must therefore be run on the production development machine with JDK 25 before release.

## UI refinement (same 7.2.6 version)
- Added explicit geometric semantic icons to both rollback tables and safety-sequence steps.
- Candidate and activity tables now use constrained resize policies to consume the full available width.
- Recovery / Safety Sequence cards are balanced 50/50 to avoid unused right-side space.
- Added dedicated visual identities for package, workflow, recovery, compatibility, snapshot, preserve and installer concepts.
- No Sales/Purchase business routes were changed by this UI-only refinement.

## Recovery-folder launcher refinement
- `Open Recovery Folder` and `Open Package Folder` now use the native OS file manager (`explorer.exe`, `open`, or `xdg-open`) with Java Desktop as fallback.
- This avoids a silent no-op when a packaged runtime reports `Desktop.isDesktopSupported()` as false.
- `Full Database Recovery` intentionally continues to navigate to the existing Backup & Restore screen; it is kept separate from application-only rollback to avoid accidental replacement of current business data.
