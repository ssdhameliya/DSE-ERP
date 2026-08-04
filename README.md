# DSE ERP

DSE ERP is an open-source JavaFX desktop ERP application by **DS Engineers**. It targets Windows and macOS and uses JDK 25, JavaFX 25, Maven, and SQLite.

## Main modules

- Dashboard and business reports
- Sales, purchases, quotations, returns, and payments
- Customers, suppliers, items, master data, and inventory
- User access, roles, and permissions
- PDF/Excel exports and email/WhatsApp-assisted communication
- Production-safe backup and staged restore
- GitHub Releases update checking
- First-run workspace setup and movable business-data storage

## Requirements for development

- JDK 25
- Maven 3.9 or newer

## Build and test

```bash
mvn clean verify
```

Run during development:

```bash
mvn javafx:run
```

On Windows, `build.bat` performs the same verification and `Run DSE ERP.bat` launches the packaged JAR after a successful build.

## Native installers

Windows PowerShell:

```powershell
.\scripts\package-windows.ps1 -Version 2.1.12
```

macOS Terminal:

```bash
./scripts/package-macos.sh 2.1.12
```

The generated packages are written to:

- `target/windows-installer/`
- `target/macos-installer/`

## Automated releases

After committing a matching version in `pom.xml`, push a semantic-version tag:

```bash
git tag v2.1.12
git push origin v2.1.12
```

GitHub Actions builds:

- Windows x64 EXE
- macOS Apple Silicon DMG
- macOS Intel DMG
- SHA-256 checksums

Release assets are published to GitHub Releases automatically.

## Application data and workspace

Business data is stored outside the installed application so updates do not overwrite it. New installations use a setup wizard to select a workspace, which may be on another internal drive or external SSD.

See [`docs/WORKSPACE.md`](docs/WORKSPACE.md) for the folder structure, migration behavior, and backup recommendations.

## Security

Do not commit customer databases, runtime configuration, SMTP app passwords, signing certificates, or private keys. Runtime data is excluded by `.gitignore`.

## Repository

https://github.com/ssdhameliya/DSE-ERP

## Additional documentation

- Phase 2 automatic install-and-restart updater: `docs/PHASE-2-AUTOMATIC-UPDATER.md`
