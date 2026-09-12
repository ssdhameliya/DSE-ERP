# DSE ERP GitHub Deployment Setup

DSE ERP releases use GitHub Actions as the release control point. UAT deployment is automatic for a version tag after the build/test/package gates pass. Production uses a separate manually started workflow and should also use a protected GitHub `production` environment with required reviewers.

## Canonical deployment repository and mirror

`ssdhameliya/DSE-ERP` is the only repository allowed to auto-deploy UAT or run the controlled PROD promotion. `DSE-ERP-Enterprise` may receive the same `main` commits and version tags as a mirror, but the UAT deployment job is guarded by `github.repository` and is skipped there. This keeps both repositories synchronized without a duplicate cloud deployment.

## Release flow

1. Push the verified source to `main`.
2. Create and push the matching version tag (`vX.Y.Z`).
3. `Build Native Release` runs the release gates and platform packages, builds one server JAR, publishes the GitHub Release, and automatically deploys that exact server artifact to the `uat` GitHub environment.
4. Test the release in UAT with the normal desktop/client workflow.
5. When UAT is approved, open **Actions → Deploy PROD → Run workflow**, enter the exact UAT-approved tag, and approve the protected `production` environment when GitHub requests it.
6. PROD downloads the exact server JAR and checksum from the existing GitHub Release. It does not rebuild the server.

## GitHub environments

Create two GitHub Environments in repository settings:

- `uat` — no required reviewer, so release tags can auto-deploy.
- `production` — add the release owner/admin as a required reviewer. This creates GitHub's **Approve and deploy** gate before production secrets are exposed or deployment begins.

Use the same secret/variable names in both environments.

### Environment secrets

- `DSE_SSH_HOST` — Oracle VM host/IP for that environment.
- `DSE_SSH_USER` — dedicated deployment SSH user (recommended) or the approved Oracle admin account.
- `DSE_SSH_PRIVATE_KEY` — dedicated CI deployment private key. Do not reuse a developer's everyday SSH private key.
- `DSE_SSH_KNOWN_HOSTS` — pinned OpenSSH known-host entry for the target VM. The workflow uses `StrictHostKeyChecking=yes` and never silently trusts a new host key.

### Environment variables

- `DSE_SSH_PORT` — normally `22`.
- `DSE_PUBLIC_HEALTH_URL` — public runtime health URL for the environment, ending in `/api/runtime/health`.
- `DSE_EXPECTED_DATABASE` — `dse_erp_uat` for UAT and the isolated production database name for PROD.

The `production` environment also needs:

- `DSE_UAT_PUBLIC_HEALTH_URL` — UAT public runtime health URL.
- `DSE_UAT_EXPECTED_DATABASE` — expected UAT database, normally `dse_erp_uat`.

These two values enforce that PROD can only deploy a version currently healthy in UAT.

## Oracle host contract

Each environment must already have the DSE ERP runtime installed:

- `/etc/dse-erp/<env>.env`
- `/etc/dse-erp/<env>-db-password`
- `/srv/dse-erp/<env>/releases`
- `/srv/dse-erp/<env>/current`
- `/srv/dse-erp/<env>/workspace`

The release source owns a long-lived desktop compatibility baseline through `desktop.compatibility.baseline` in the root `pom.xml`. For the corrected 10.x compatibility line it starts at `10.0.4`. When `DSE_MINIMUM_SUPPORTED_DESKTOP_VERSION` is omitted, the server publishes that release baseline rather than forcing desktop/server version equality. Keep the environment value equal to the release policy. Raise the baseline only for a reviewed breaking API, security, or business-integrity change. UAT/PROD deployment now fails if `/api/runtime/health` publishes a different minimum. This allows, for example, a 10.0.4 desktop to choose **Not Now** and continue against a compatible 10.0.10 server while 10.0.1-10.0.3 and any other clients below the certified floor are blocked for the one-time transition.
- systemd service `dse-erp-<env>`

The deployment script never sends a database password through GitHub. It reads the environment's existing protected password file on the Oracle host.

## Automatic rollback behavior

Before switching binaries, `deploy-oracle-release.sh`:

- verifies the uploaded server JAR SHA-256;
- verifies the currently running environment/database identity;
- creates a custom-format PostgreSQL pre-upgrade backup;
- validates the backup with `pg_restore --list`;
- installs the release into a versioned release directory;
- switches the `current` symlink;
- restarts the systemd service;
- verifies version, build revision, environment, database name, the release-owned minimum supported desktop version, and `ready=true`.

If startup/health fails, it automatically restores the previous binary symlink, restarts the old release, and verifies the rollback health. The pre-upgrade database backup is retained. Database rollback remains a controlled operation if an incompatible schema migration was applied.

## Local fallback is intentionally not automatic

A Shared Client must not silently start writing to an old local database when the cloud/server is unavailable. That would create two independent financial histories. Temporary outages should remain Shared Client connection failures/retry states. A future disaster-recovery workflow may restore the latest server backup and server-owned files into a local environment before explicitly switching back to `LOCAL`.

## Protected Oracle runtime files

The GitHub SSH deployment account does not need direct read permission on `/etc/dse-erp/<env>.env` or `/etc/dse-erp/<env>-db-password`. The deployment script verifies and reads those protected Oracle files through `sudo -n`; keep them root/protected rather than loosening filesystem permissions for CI.
