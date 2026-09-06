# DSE ERP 9.0.85

## Release scope — deployment hardening and explicit disaster recovery

- Fixed the Oracle GitHub deployment script so the protected `/etc/dse-erp/<env>.env` and database-password files are read through non-interactive `sudo` instead of being tested/read as the unprivileged SSH deployment user.
- Keeps the 9.0.84 safety model: one canonical server artifact, automatic UAT deployment after a release tag, validated pre-upgrade PostgreSQL backup, versioned releases, health verification, and automatic binary rollback on startup/health failure.
- Added an administrator-only fresh disaster-recovery package endpoint on the company server.
- Recovery packages contain a newly created and validated PostgreSQL snapshot plus server-owned `Attachments`, `Documents`, and `Templates`; environment files and database credentials are excluded.
- Added **Export Recovery Package** for keeping an off-server recovery copy.
- Added **Emergency Local Recovery** for an administrator in Shared Client mode. Recovery is explicit: DSE ERP never silently writes to an old/stale LOCAL database when the server is unavailable.
- LOCAL recovery validates package paths and the database SHA-256, stages the database restore, preserves the prior LOCAL database/files, applies business files only after the database restore succeeds, and then continues in LOCAL mode on restart.
- Startup failure in Shared Client mode now offers Retry / Recover from Package / Exit while preserving the no-automatic-fallback rule.
- Added disaster-recovery release-gate coverage and Local recovery tests.

No business-schema, sidebar, normal sales/purchase/accounting workflow, or theme redesign is included in this release.
