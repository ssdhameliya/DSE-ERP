# Phase 2 Automatic Updater

Version 2.1.2 introduces a detached installer helper so the running ERP does not try to replace its own files.

## Update sequence

1. Check GitHub Releases.
2. Select the correct Windows or macOS package.
3. Download the package to the workspace Updates folder.
4. Verify the SHA-256 entry from `checksums.txt`.
5. Create a consistent pre-update database backup.
6. Start an operating-system helper outside the ERP process.
7. Close DSE ERP.
8. Install over the existing application.
9. Restart DSE ERP.
10. Record success after the new version starts and database migration completes.

## Windows

The helper waits for the ERP process to close and invokes the jpackage EXE with quiet/no-restart options. If unattended installation fails, it opens the normal installer so the update is still recoverable.

Log file:

`%TEMP%\DSE-ERP-update.log`

## macOS

The helper waits for the ERP process, mounts the DMG, replaces `DSE ERP.app`, removes quarantine from the copied bundle, and reopens the app. macOS may request administrator approval when the application is installed under `/Applications`.

Log file:

`~/Library/Logs/DSE-ERP-update.log`

## IntelliJ testing

The project remains a normal Maven/JavaFX project. Use JDK 21 and run `org.example.app.Launcher` with `--enable-preview`, or use:

```text
mvn clean verify
mvn javafx:run
```

A real self-update should be tested from a packaged 2.1.1 installation after publishing a 2.1.2 pre-release or release. Running from IntelliJ validates the UI, release check, download, checksum and backup paths, but the IDE process itself is not an installed DSE ERP application to replace.
