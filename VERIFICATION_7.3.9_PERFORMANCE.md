# DSE ERP 7.3.9 Performance Verification

## Source baseline

- Input: `DSE-ERP-7.3.8-INTELLIJ-WINDOWS-NATIVE-RUNTIME-SALE-INVOICE-FIXED(3).zip`
- Output version: 7.3.9
- Database schema generation: unchanged (`1`)

## Evidence addressed

Production logs showed macOS `aarch64`, Java 25.0.4, JavaFX 25.0.2 and a 2.0x2.0 output scale with repeated JavaFX pulse stalls commonly in the 500-1500+ ms range. Windows ran the same Java/JavaFX generation at 1.0x1.0 scale with substantially smaller interaction stalls. Source inspection also proved the launcher globally forced `prism.order=sw`, including macOS.

## Implemented checks

1. Windows retains the software-pipeline workaround; macOS no longer receives the forced software renderer.
2. Runtime performance diagnostics include Prism override/verbose state.
3. Create Sale startup API work is backgrounded through `UiTaskExecutor`.
4. Create Sale Item Search uses a short debounce and precomputed search index.
5. Bank Statement batch/transaction/metrics startup work is backgrounded.
6. Settings startup asset preview work is backgrounded.
7. Settings property persistence uses one final `ConfigManager.save()` per Save action.
8. macOS-only effect guardrails remove high-cost Gaussian effects from the three measured hotspot pages.
9. Packaged PostgreSQL runtime discovery is isolated from development configuration.
10. PostgreSQL readiness uses `pg_isready`; production packaging verifies that the command is present.
11. Packaged app exit stops the managed PostgreSQL runtime by default to make subsequent installer replacement safe.

## Static verification performed in the build workspace

- Desktop JDBC audit: PASS.
- Phase 2 desktop data-boundary audit: PASS.
- PostgreSQL-only desktop audit: PASS.
- Final Spring/Hibernate architecture audit: PASS.
- XML/FXML/POM parse: 47 / 47 PASS.
- Windows bundled PostgreSQL command presence check including `pg_isready.exe`: PASS.
- Java parser smoke check of modified Java files with the available JDK: no syntax-indicator errors found.
- Version consistency probes: 7.3.9 applied to parent/module POMs, runtime contract, app/server version properties and visible version labels.

## Environment limitation

The build container currently provides Java 21 and does not provide Maven, while DSE ERP targets Java 25. A truthful Java 25 `mvn clean verify` and native EXE/DMG packaging therefore cannot be executed in this container. The repository's GitHub release workflow remains configured to run `mvn clean verify` with Temurin Java 25 on Ubuntu, Windows, macOS Apple Silicon and macOS Intel before producing installers.

## Required production acceptance test

After a 7.3.9 Mac build is produced, repeat the same measured sequence used for the 7.3.8 baseline:

1. Login password typing.
2. Create Sale opening and Item Search typing.
3. Bank Statement opening/refresh.
4. Settings opening, field typing and section switching.
5. Compare `performance.log` for `fx-freeze`, `fx-thread-stall`, navigation phases and the new Prism runtime line.

Primary acceptance criterion: text input is immediate and the sustained 500-1500+ ms macOS pulse-stall pattern is eliminated or materially reduced.
