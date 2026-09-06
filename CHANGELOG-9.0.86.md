# DSE ERP 9.0.86

## UAT deployment hotfix

- Verifies the protected pre-upgrade PostgreSQL backup as the `dseerp` service account, matching the account that creates and owns the backup.
- Prevents a valid root/service-owned backup from being incorrectly reported as empty by the unprivileged GitHub SSH account.
- Restricts automatic UAT deployment to the canonical `ssdhameliya/DSE-ERP` repository, so the Enterprise mirror can keep the same source and release tags without attempting a duplicate UAT deployment.
- Keeps strict server/desktop version compatibility. A newer Shared Client is intentionally blocked while UAT is still running an older server release.

## Scope

No database schema change. No business workflow change. No UI layout change. No production deployment.
