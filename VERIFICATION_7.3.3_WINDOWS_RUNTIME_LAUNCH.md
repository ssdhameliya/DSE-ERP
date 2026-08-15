# Verification — DSE ERP 7.3.3 Windows Runtime Launch

## Source review

1. `RuntimeBootstrapper.startServer()` already propagates the effective database URL, username, password, server port, internal bridge token, SMTP settings, and business settings into the child process environment. No new database-secret persistence was added.
2. `ManagedPostgresRuntime` still owns managed PostgreSQL startup/verification and applies the dynamically selected JDBC URL and generated application credential to `ConfigManager`.
3. `RuntimeBootstrapper.serverCommand()` now uses `java -jar` on all platforms.
4. `scripts/package-windows.ps1` now supplies `--jlink-options "--strip-debug --no-man-pages --no-header-files"`, deliberately omitting jpackage's default `--strip-native-commands`, so `runtime\bin\java.exe` remains available.
5. The Windows packaging script no longer creates or validates `DSE ERP Server.exe`; it validates the direct Java launcher and server JAR instead.
6. The macOS packaging script is unchanged. It already used the same jlink option set and direct Java server launch.
7. No database migration was added and rollback schema compatibility remains generation 1.

## Validation performed in the patch environment

- All four repository architecture audits passed:
  - `audit-desktop-jdbc.py`
  - `audit-phase2-data-boundary.py`
  - `audit-postgres-only.py`
  - `audit-final-data-architecture.py`
- Root/desktop/server/shared POM files parsed successfully as XML.
- All FXML files parsed successfully as XML.
- `RuntimeBootstrapper.java` passed a focused Java syntax/type smoke compile against local stubs.
- A local jpackage smoke test confirmed the relevant behavior: the default runtime omitted the `java` command, while specifying `--jlink-options "--strip-debug --no-man-pages --no-header-files"` retained it.

## Validation still required on the release platforms

The patch environment does not contain Maven/JDK 25 or a Windows/macOS native packager, so it cannot honestly claim a full `mvn clean verify`, Windows EXE build, or macOS DMG build here. Run the existing GitHub Actions release workflow or the commands below on the corresponding platform.

- `mvn clean verify` with JDK 25.
- IntelliJ startup: Workspace → PostgreSQL → Spring Boot → Schema → Login.
- Windows: run `scripts\package-windows.ps1 -Version 7.3.3`, install on a clean Windows user profile, confirm `runtime\bin\java.exe` exists, and confirm Spring Boot reaches READY.
- macOS Intel and Apple Silicon: run `scripts/package-macos.sh 7.3.3` or use the existing GitHub Actions jobs, then confirm normal startup.
