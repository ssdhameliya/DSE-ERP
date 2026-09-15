# DSE ERP GitHub Deployment Setup

DSE ERP releases use GitHub Actions as the release control point. The current release flow uses one version-tag workflow builds/tests/packages once, publishes a prerelease, deploys and verifies UAT, then continues to the protected `production` environment and deploys the exact same server artifact to PROD. A production required-reviewer gate may pause the same workflow run for approval. The separate **Deploy PROD** workflow remains only as a recovery/manual fallback.

## Canonical deployment repository and mirror

`ssdhameliya/DSE-ERP` is the only repository allowed to auto-deploy UAT or run the controlled PROD promotion. `DSE-ERP-Enterprise` may receive the same `main` commits and version tags as a mirror, but both deployment jobs are guarded by `github.repository` and are skipped there. This keeps both repositories synchronized without a duplicate cloud deployment.

## Release flow

1. Push the verified source to `main`.
2. Create and push the matching version tag (`vX.Y.Z`).
3. `Build Native Release` runs the release gates and platform packages once, builds one canonical server JAR, and publishes the GitHub Release as a prerelease.
4. The same workflow automatically configures the UAT private-update token, deploys the canonical server artifact to `uat`, verifies runtime health and verifies the private `/api/updates` gateway.
5. Only after the UAT job succeeds does the same workflow enter the protected `production` environment. If required reviewers are configured, approve that pending production job in the same workflow run.
6. The production job re-checks that the same version is healthy in UAT, configures the PROD private-update token, verifies the canonical artifact against the published release checksum, deploys it to PROD, verifies PROD health and the private update gateway, then promotes the same GitHub Release from prerelease to stable. No server rebuild occurs between UAT and PROD.
7. **Actions → Deploy PROD** remains available only as a manual recovery/fallback for an already UAT-approved tag.

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
- `DSE_GITHUB_UPDATE_TOKEN` — fine-grained token restricted to `ssdhameliya/DSE-ERP` with read-only Contents access. Store it separately in both the `uat` and `production` GitHub Environments. It is streamed to the target server over SSH stdin and never written to source, desktop configuration, release assets, command arguments or logs.

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
- `/srv/dse-erp/<env>/current` — created/maintained by the managed release flow; it may be absent before the first managed deployment.
- `/srv/dse-erp/<env>/workspace`

The release source owns a long-lived desktop compatibility baseline through `desktop.compatibility.baseline` in the root `pom.xml`. For the corrected 10.x compatibility line it starts at `10.0.4`. When `DSE_MINIMUM_SUPPORTED_DESKTOP_VERSION` is omitted, the server publishes that release baseline rather than forcing desktop/server version equality. Keep the environment value equal to the release policy. Raise the baseline only for a reviewed breaking API, security, or business-integrity change. UAT/PROD deployment now fails if `/api/runtime/health` publishes a different minimum. This allows, for example, a 10.0.4 desktop to choose **Not Now** and continue against a compatible newer 10.x server while 10.0.1-10.0.3 and any other clients below the certified floor are blocked for the one-time transition.
- systemd service `dse-erp-<env>`

The deployment script never sends a database password through GitHub. It reads the environment's existing protected password file on the Oracle host.

## Automatic rollback behavior

Before switching binaries, `deploy-oracle-release.sh`:

- reads Android/iOS compatibility policy from the protected `/etc/dse-erp/<env>.env` file (release POM values are bootstrap fallbacks only), so independent Mobile releases are preserved even when `current` did not exist before the first managed PROD deployment;
- if a prior managed `current` release exists but its service was left stopped by an earlier failed deployment, safely starts and re-verifies that current release before using it as the rollback target;

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

## Private release repository desktop updates

Desktop clients do not require direct access to the GitHub repository. The Spring server exposes the pre-login `/api/updates/**` gateway and performs private GitHub release access on the server side.

Create the fine-grained read-only `DSE_GITHUB_UPDATE_TOKEN` secret in **both** GitHub Environments: `uat` and `production`. Each deployment job receives only the secret for its own environment and streams it over the pinned SSH connection to `scripts/linux/configure-github-update-token.sh`. That helper updates only `/etc/dse-erp/<env>.env` while preserving owner/mode and validating that the file declares the expected environment. The credential never appears in desktop `config.properties`, source code, command arguments, workflow output, logs, or release assets. UAT must pass `/api/updates/releases/latest?includePrerelease=true` before the production job can start; PROD must pass the same gateway test before the release is promoted to stable.

The gateway intentionally permits update metadata and installer/checksum downloads before login so a desktop below the server compatibility floor can still update. It exposes release binaries only; source code and the GitHub credential remain private. The desktop continues to require the published SHA-256 checksum before an installer can run.

For the current release flow, the protected GitHub Environment secrets are the deployment authority for both UAT and PROD. Once both environment secrets are configured, one tag run can complete build → prerelease → UAT → protected PROD → stable promotion without putting the private-repository credential on a developer workstation.

## GitHub Actions workflow-run cleanup

`scripts/github/cleanup-workflow-runs.ps1` (Windows PowerShell) and `scripts/github/cleanup-workflow-runs.sh` (bash) clean old **GitHub Actions workflow-run history only** for these three repositories:

- `ssdhameliya/DSE-ERP`
- `ssdhameliya/DSE-ERP-Enterprise`
- `ssdhameliya/DES_Mobile`

The default policy keeps the newest **6 completed workflow runs per repository**. Queued, waiting and in-progress runs are never deleted. Releases, tags, repository files and branches are never touched. Both scripts run in preview mode first so the deletion list can be reviewed.

Windows preview:

```powershell
.\scripts\github\cleanup-workflow-runs.ps1
```

Windows apply after reviewing the preview:

```powershell
.\scripts\github\cleanup-workflow-runs.ps1 -Apply
```

Optional different retention count:

```powershell
.\scripts\github\cleanup-workflow-runs.ps1 -Keep 6 -Apply
```

Bash preview / apply:

```bash
./scripts/github/cleanup-workflow-runs.sh
./scripts/github/cleanup-workflow-runs.sh --apply
```

The machine running cleanup needs GitHub CLI (`gh`) authenticated with Actions write permission for all three repositories. Run preview first; only `-Apply` / `--apply` performs deletion.
