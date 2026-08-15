# DSE ERP

DSE ERP is an open-source JavaFX desktop ERP application by **DS Engineers**. It targets Windows and macOS and uses JDK 25, JavaFX 25, Maven, Spring Data JPA, Hibernate, and PostgreSQL.

## Main modules

- Dashboard and business reports
- Sales, purchases, quotations, returns, and payments
- Customers, suppliers, items, master data, and inventory
- User access, roles, and permissions
- Universal Document Studio for editable/general PDFs and ERP templates
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
.\scripts\package-windows.ps1 -Version 7.3.0
```

macOS Terminal:

```bash
./scripts/package-macos.sh 7.3.0
```

The generated packages are written to:

- `target/windows-installer/`
- `target/macos-installer/`

## Automated releases

After committing a matching version in `pom.xml`, push a semantic-version tag:

```bash
git tag v7.3.0
git push origin v7.3.0
```

GitHub Actions builds:

- Windows x64 EXE
- macOS Apple Silicon DMG
- macOS Intel DMG
- SHA-256 checksums

Release assets are published to GitHub Releases automatically.

## Application data and workspace

Business data is stored outside the installed application so updates do not overwrite it. New installations use a setup wizard to select a workspace, which may be on another internal drive or external SSD.

The setup wizard creates the workspace folders and stores the selected workspace pointer in the operating-system application-data folder.

## Security

Do not commit customer databases, runtime configuration, SMTP app passwords, signing certificates, or private keys. Runtime data is excluded by `.gitignore`.

## Repository

https://github.com/ssdhameliya/DSE-ERP

## PostgreSQL development setup

The JavaFX application is PostgreSQL-only. JavaFX accesses business data through the
authenticated Spring API; database access is owned by Spring Data JPA and Hibernate.
There is no SQLite runtime or desktop JDBC persistence path.

An explicit `db.url` or `DSE_DB_URL` always takes precedence. For a shared office setup,
install PostgreSQL once on an always-on server and point every desktop client to the same
verified schema URL. Do not expose PostgreSQL directly to the public internet.

Local IntelliJ/Maven configuration:

* JDK: `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot`
* PostgreSQL binaries: `D:\PostgreSQL\18\pgsql\bin`
* PostgreSQL data: `D:\PostgreSQL\18\data`
* URL: `jdbc:postgresql://localhost:5432/dse_erp`
* User: `dse_erp_app`
* Password: read from the `DSE_DB_PASSWORD` user environment variable

Run `scripts\start-postgresql.cmd` if PostgreSQL is not already accepting connections,
then run `org.example.app.Launcher` from IntelliJ or `mvn javafx:run` from a Java 25 shell.

Manual and scheduled backups are PostgreSQL custom-format `.pgbackup` files created with
`pg_dump`. Restore is staged and applied atomically on the next application start.
Installer packages include the managed PostgreSQL runtime required by the application.
