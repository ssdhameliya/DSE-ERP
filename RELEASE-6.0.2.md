# DSE ERP 6.0.2

## 6.0.2 packaging fix

- Hardened macOS PostgreSQL relocation subprocess decoding so non-UTF-8 `file` output cannot abort GitHub Actions packaging.
- Keeps the relocatable bundled PostgreSQL verification introduced for the 6.x production runtime.

## macOS managed PostgreSQL production fix

- Makes the bundled PostgreSQL 18 runtime relocatable on macOS instead of retaining Homebrew build-machine paths.
- Bundles transitive non-system dylib dependencies required by PostgreSQL utilities.
- Rewrites Mach-O dependency load commands to application-relative `@loader_path` references.
- Ad-hoc signs modified native PostgreSQL binaries/libraries after relocation.
- Executes `initdb`, `pg_ctl`, `psql`, and `createdb --version` during production bundle verification.
- Rejects release bundles that still reference `/opt/homebrew/Cellar`, `/usr/local/Cellar`, or Homebrew `opt` paths.
- Re-verifies the PostgreSQL runtime from the final jpackage `.app` staging layout before DMG creation.
- Adds a defensive bundled-library environment for PostgreSQL child processes on macOS.
- Makes fresh PostgreSQL cluster bootstrap staging-based so a failed first-time setup does not leave a half-created database that blocks retry.

## Version

Application/runtime phase updated from 5.1.30 to 6.0.2.
