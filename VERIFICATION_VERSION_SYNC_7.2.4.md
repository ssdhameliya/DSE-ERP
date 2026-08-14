# DSE ERP 7.2.4 — Runtime / Native Packaging Version Sync Verification

## Corrected values
- Root Maven project: 7.2.4
- Desktop Maven parent/project: 7.2.4
- Server Maven parent/project: 7.2.4
- Shared Maven parent/project: 7.2.4
- Desktop app-version.properties: 7.2.4
- Shared RuntimeContract.APP_VERSION: 7.2.4
- Server dse.app.version: 7.2.4
- Runtime manifest phase: 7.2.4

## Packaging behavior
- Windows packaging resolves the release version from Maven when no explicit version is passed.
- macOS packaging resolves the release version from Maven when no explicit version is passed.
- GitHub Actions validates the release tag against the Maven project version before building native installers.
- The packaged Spring backend now reports the same version expected by the JavaFX desktop.

## Required final release gate
Run `mvn clean verify` under JDK 25 on the release machine, then build the EXE/DMGs normally.
