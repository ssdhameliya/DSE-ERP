# DSE ERP Cloud UAT / Production Deployment

The same tested server and desktop release is promoted through UAT and production. The application version is build identity; `deployment.environment` is deployment identity.

## Required order

1. Build and pass the complete release gates locally.
2. Install Oracle/Linux layout with `scripts/linux/install-layout.sh`.
3. Configure `/etc/dse-erp/uat.env` from `scripts/linux/uat.env.example` and keep it mode `600`.
4. Create the isolated UAT database/user and deploy the tested server JAR with `scripts/linux/deploy-release.sh uat ...`.
5. Point a desktop to `SHARED_CLIENT`, `deployment.environment=UAT`, and the UAT HTTPS URL. Use **Test Connection** before saving.
6. Perform UAT first with test data, then—when needed—with a restored copy of current production data. UAT never writes to the production database.
7. Obtain UAT sign-off before production migration.
8. At production cutover, stop transaction entry, take/validate the final source backup, restore it into the isolated production database, deploy the exact UAT-approved server artifact to PROD, reconcile data, then switch production desktops.
9. Keep the original local database untouched until production acceptance.

## Isolation contract

- UAT: `DSE_DEPLOYMENT_ENVIRONMENT=UAT`, `DSE_EXPECTED_DATABASE=dse_erp_uat`.
- PROD: `DSE_DEPLOYMENT_ENVIRONMENT=PROD`, `DSE_EXPECTED_DATABASE=dse_erp`.
- Server startup fails if the configured environment is wired to a different database name.
- PostgreSQL is not exposed publicly. Spring binds to loopback. Nginx/Caddy exposes HTTPS 443.

## Backup contract

Normal application backup policy is **weekly / keep latest 2 ordinary backups**. A deployment creates and validates an additional `PreUpgrade` backup before replacing the running server. That safety backup is outside the normal weekly rotation.

## Rollback

`deploy-release.sh` keeps releases in versioned directories and uses a `current` symlink. If startup or health verification fails, it restores the previous binary symlink automatically. If a release applied an incompatible database migration, restore the matching validated pre-upgrade database backup before reopening user access.

## GitHub release automation

For the live Oracle layout (`/srv/dse-erp/<env>` with services `dse-erp-uat` / `dse-erp-prod`), release tags use `.github/workflows/release.yml` and `scripts/linux/deploy-oracle-release.sh`. UAT is deployed automatically only after the release build/package jobs succeed. Production is deployed through the separate manually started `.github/workflows/deploy-prod.yml`, which downloads the exact already-published server artifact and verifies that the same version is healthy in UAT first.

The older `deploy-release.sh` / `dse-erp@.service` pair remains the generic fresh-install template. `deploy-oracle-release.sh` is the CI path for the current Oracle VM layout.

## Version management

The release version is set once in `.mvn/maven.config` as `-Drevision=<version>`. Maven-filtered desktop/server/shared/runtime metadata inherit it automatically. Historical migration/release references remain fixed by design.
