# DSE ERP

DSE ERP is an open-source JavaFX desktop ERP application by **DS Engineers**. It targets Windows and macOS and uses Java 21, JavaFX, Maven, and SQLite.

## Main modules

- Dashboard and business reports
- Sales, purchases, quotations, returns, and payments
- Customers, suppliers, items, master data, and inventory
- User access, roles, and permissions
- PDF/Excel exports and email/WhatsApp-assisted communication
- Production-safe backup and staged restore
- GitHub Releases update checking

## Requirements for development

- JDK 21
- Maven 3.9 or newer

## Build and test

```bash
mvn clean verify
```

Run during development:

```bash
mvn javafx:run
```

## Native installers

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -Version 2.0.0
```

macOS Terminal:

```bash
chmod +x scripts/package-macos.sh
./scripts/package-macos.sh 2.0.0
```

The generated packages are written to:

- `target/windows-installer/`
- `target/macos-installer/`

## Automated releases

Pushing a semantic-version tag starts the GitHub Actions release workflow:

```bash
git tag v2.0.0
git push origin v2.0.0
```

The workflow builds:

- Windows x64 EXE
- macOS Apple Silicon DMG
- macOS Intel DMG
- SHA-256 checksums

Release assets are published to GitHub Releases automatically.

## Application data

Business data is stored outside the installed application so updates do not overwrite it.

- Windows: `%APPDATA%\DSE ERP\`
- macOS: `~/Library/Application Support/DSE ERP/` or the location configured by the application

Always keep off-device backups in addition to local backups.

## Repository

`https://github.com/ssdhameliya/DSE-ERP`
