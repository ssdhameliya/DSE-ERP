# DSE ERP 7.3.3 — Windows Native Runtime Launch Reliability

## Summary

DSE ERP 7.3.3 fixes the Windows packaged startup failure where IntelliJ and macOS DMG builds started correctly but the installed Windows EXE could stop at **Spring Boot Services** with a native **Failed to launch JVM** message.

## Root cause

The application source was shared, but the backend launch path was not:

- IntelliJ started the executable Spring Boot JAR with the active JDK using `java -jar`.
- macOS packaging preserved the bundled `java` command and also used `java -jar`.
- Windows packaging used a jpackage secondary launcher (`DSE ERP Server.exe`) because the default jpackage Windows runtime strips native JDK commands, including `java.exe`.

That Windows-only secondary launcher used a class-path launcher configuration for the Spring Boot executable JAR, creating a different startup model from the two working environments.

## Changes

- Windows packaging now overrides jpackage's default jlink options so `runtime\bin\java.exe` is retained.
- Removed the Windows-only `DSE ERP Server.exe` secondary-launcher dependency.
- Windows now starts `server/dse-erp-server.jar` with the same direct `java -jar` command used by IntelliJ/macOS.
- Windows packaging verifies that the bundled `java.exe`, `jvm.dll`, desktop launcher, and Spring Boot JAR are present before producing the installer.
- Runtime bootstrap verification now requires a usable bundled Java launcher for packaged builds.
- Existing runtime DB URL, username, password, server port, bridge token, SMTP, and business configuration environment propagation is preserved unchanged.
- Version synchronized to 7.3.3.

## Compatibility / impact

- **IntelliJ:** no behavior change; continues to use the active JDK and direct `java -jar`.
- **macOS DMG:** no packaging change; it already preserved the Java launcher and used direct `java -jar`.
- **Windows EXE:** backend launch path is changed to match the proven IntelliJ/macOS path.
- **PostgreSQL:** no schema or credential changes. Managed PostgreSQL still uses the runtime-selected local port beginning at 55432.
- **Database schema:** unchanged; schema generation remains version 1.
- **Business/UI functionality:** unchanged.
