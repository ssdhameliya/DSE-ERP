# DSE ERP 9.0.83

## Shared-client startup and workspace safety
- First-run keeps the existing choice between Local and Shared / Company Server.
- Local mode continues to require a user-selected business workspace and managed local PostgreSQL.
- Shared Client no longer requires the user to create or select a business workspace. DSE ERP creates application-managed client storage under the operating-system application-data area for configuration, logs, cache and temporary files only.
- Existing promoted Shared Client installations are detached from their former local business workspace on startup. The original local workspace is preserved untouched as rollback/recovery data.
- Shared Client continues to never start local PostgreSQL or the embedded/local Spring runtime.
- An existing Local installation can explicitly choose “Connect to Existing Server” after a successful server test and confirmation. This path does not upload, overwrite or migrate the local company; promotion remains a separate workflow.
- The Local → Existing Server transition now writes a separate managed shared-client profile and leaves the original local `Config/config.properties` byte-for-byte untouched for rollback. The application closes immediately after the switch to avoid a mixed LOCAL/SHARED runtime.
- “Connect to Existing Server” is deployment-only: pressing Save during that transition does not push local company, email, storage or business settings to the existing server.
- UAT / PROD server environment must match the selected environment before the Shared Client connection is accepted.
- The Local → Existing Server confirmation now uses deployment-neutral wording: Connect preserves the local company and switches this PC to the verified server; Cancel keeps the PC local so a separate Local → Server promotion can be completed when needed. Legacy “Enable Multi-User” wording was removed from this warning.
- Fresh Shared Client setup and Local → Existing Server transitions stamp the running client version in the new managed profile, preventing a false “Update completed” notification on first login. Real later Shared Client upgrades use a client-specific update message and do not imply that the desktop migrated the company-server database schema.

## Promotion hardening
- `scripts/Enable Multi-User.ps1` now uses the canonical `Config/config.properties` path.
- `LocalDatabaseUrl` and `LocalDatabaseUser` are required only for the staging phase, not `-Finalize`.
- Finalization returns to its caller instead of terminating the parent PowerShell session.

## Backup history / retention hardening
- Staged restore files such as `restore-pending.pgbackup` and restore-validation artifacts are excluded from ordinary backup history and ordinary retention counts.

## Compatibility
- No ERP business schema change.
- No sidebar/navigation redesign.
- Existing Local workspaces remain supported.
- Existing Shared Client server/version/API compatibility checks remain enforced.
