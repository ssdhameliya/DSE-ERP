# DSE ERP 9.0.82 — Cloud / UAT Readiness

- Single-source release identity via `.mvn/maven.config` (`revision`).
- UAT/PROD deployment environment separated from application version.
- Health endpoint reports environment and database name.
- Shared-client connection validates version, API revision and environment.
- UAT/PROD database startup safety guard via `DSE_EXPECTED_DATABASE`.
- Multi-user promotion script no longer hard-codes a release version.
- Backup schedule defaults to weekly and retention means number of backups; default keep count is 2.
- Newly created server backups are validated before retention cleanup.
- Linux UAT/PROD environment, systemd, Nginx and deployment/restore templates added.
- No business-feature or UI redesign scope.
