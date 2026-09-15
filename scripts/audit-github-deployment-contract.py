#!/usr/bin/env python3
from pathlib import Path
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
fail=[]

def text(path):
    return (ROOT/path).read_text(encoding='utf-8', errors='replace')

def need(ok,msg):
    if not ok: fail.append(msg)

release=text('.github/workflows/release.yml')
ci=text('.github/workflows/ci.yml')
prod=text('.github/workflows/deploy-prod.yml')
deploy=text('scripts/linux/deploy-oracle-release.sh')
token_setup=text('scripts/linux/configure-github-update-token.sh')
doc=text('GITHUB-DEPLOYMENT-SETUP.md')
cleanup_ps=text('scripts/github/cleanup-workflow-runs.ps1')
cleanup_sh=text('scripts/github/cleanup-workflow-runs.sh')

need('server-release' in release and 'DSE-ERP-${{ needs.validate.outputs.version }}-SERVER.jar' in release,
     'release workflow does not build/upload one canonical server artifact')
need('deploy-uat:' in release and 'environment: uat' in release,
     'release workflow does not automatically deploy the release artifact to the UAT environment')
need("if: github.repository == 'ssdhameliya/DSE-ERP'" in release,
     'UAT auto-deploy is not restricted to the canonical DSE-ERP repository')
need('StrictHostKeyChecking=yes' in release and 'DSE_SSH_KNOWN_HOSTS' in release,
     'UAT workflow does not pin SSH host identity')
need('deploy-oracle-release.sh uat' in release and 'UAT_DEPLOYMENT_OK' in release and 'PUBLIC_HEALTH_URL' in release and 'compatibility_baseline' in release and 'minimumSupportedDesktopVersion' in release,
     'UAT workflow does not invoke guarded deploy + desktop compatibility-aware public health verification')
need('valid_mobile_policy' in release and 'mobilePolicy=preserved' in release and 'minimumSupportedAndroidVersion' in release and 'latestIosVersion' in release,
     'UAT workflow does not validate the preserved environment-owned Android/iOS compatibility policy')
need('DSE_GITHUB_UPDATE_TOKEN: ${{ secrets.DSE_GITHUB_UPDATE_TOKEN }}' in release
     and 'Configure UAT private GitHub update token' in release
     and 'configure-github-update-token.sh uat' in release
     and "printf '%s\\n' \"$DSE_GITHUB_UPDATE_TOKEN\" |" in release,
     'UAT workflow does not securely stream the GitHub Environment update token to the protected Oracle env file')
need('Verify UAT private GitHub update gateway' in release
     and '/api/updates/releases/latest?includePrerelease=true' in release
     and 'UAT_PRIVATE_UPDATE_GATEWAY_OK' in release,
     'UAT workflow does not verify private GitHub release lookup through the DSE update gateway')
need('deploy-prod:' in release and 'needs: [validate, server, release, deploy-uat]' in release
     and 'environment: production' in release,
     'tagged release workflow does not continue from UAT to the protected production environment in the same run')
need('Configure PROD private GitHub update token' in release
     and 'configure-github-update-token.sh prod' in release
     and 'DSE_GITHUB_UPDATE_TOKEN: ${{ secrets.DSE_GITHUB_UPDATE_TOKEN }}' in release,
     'same-run PROD deployment does not securely configure the protected private-GitHub update token')
need('Verify PROD private GitHub update gateway' in release
     and 'PROD_PRIVATE_UPDATE_GATEWAY_OK' in release
     and 'PROD_DEPLOYMENT_OK' in release,
     'same-run PROD deployment does not verify health and private update lookup')
need('RELEASE_PROMOTED_TO_STABLE' in release
     and release.find('PROD_DEPLOYMENT_OK') < release.rfind('RELEASE_PROMOTED_TO_STABLE'),
     'same-run release can be promoted stable before PROD public health succeeds')
need('DSE_GITHUB_UPDATE_TOKEN' in token_setup and 'read -r TOKEN' in token_setup
     and '/etc/dse-erp/${ENVIRONMENT}.env' in token_setup and 'sudo -n install' in token_setup
     and 'GITHUB_UPDATE_TOKEN_CONFIGURED' in token_setup,
     'server token configuration script does not safely update the protected environment file')

# Packaging/CI performance must preserve safety while avoiding duplicate native test work.
need('for attempt in 1 2 3 4 5 6' in ci and 'sleep 2' in ci and 'No verified merged PR association found after bounded retry' in ci,
     'main CI merged-PR detection does not protect against GitHub indexing delay')
need('warm-windows-packaging-runtime:' in ci and 'warm-macos-packaging-runtime:' in ci
     and 'prepare-postgresql-windows.ps1' in ci and 'prepare-postgresql-macos.sh' in ci
     and ci.count("if: github.event_name == 'push'") >= 2,
     'default-branch native PostgreSQL runtime cache warmup is missing or can run in PR-only cache scope')
need(ci.count('lookup-only: true') >= 2 and ci.count('actions/cache/save@v4') >= 2,
     'main cache warmup downloads large cache payloads even when the reusable cache already exists')
need(release.count('actions/cache/restore@v4') >= 3 and 'actions/cache/save@v4' not in release,
     'tag release should restore default-branch runtimes without writing useless tag-scoped caches')
need('MAIN_CI_REUSED_OK' in release and 'actions: read' in release and 'Require the tag to point at the current green main commit' in release,
     'tag release does not require the exact current main commit to have a successful Build and Test run')
need('Verify project on Windows' not in release and 'Verify project on macOS' not in release,
     'native packaging still duplicates the full Maven verification after the tagged Linux gate')
need(release.count('./mvnw -B -ntp clean verify') == 1,
     'tag release must run exactly one full Maven verification before native packaging')
need('needs: [validate, tests]' in release and release.count('needs: [validate, tests, server]') >= 3 and 'Validate tagged source (Linux)' in release,
     'Windows/macOS packaging does not wait for the single tagged-source verification gate and canonical server build')
need(release.count('-pl desktop -am package -DskipTests') >= 3 and release.count('name: Download canonical server artifact') >= 3,
     'native packaging does not rebuild platform-specific desktop artifacts and reuse the canonical server artifact')
need("hashFiles('scripts/ci/prepare-postgresql-windows.ps1')" in release
     and "hashFiles('scripts/build-postgresql-macos.sh', 'scripts/ci/prepare-postgresql-macos.sh')" in release,
     'release cache keys do not match the default-branch runtime seed contract')

# One tagged workflow must now carry the exact release through UAT and then PROD.
need('deploy-prod:' in release and 'needs: [validate, server, release, deploy-uat]' in release
     and 'environment: production' in release and 'group: dse-erp-production' in release,
     'tag release does not sequence protected PROD after successful UAT in the same workflow run')
need(release.count("if: github.repository == 'ssdhameliya/DSE-ERP'") >= 2,
     'both automatic deployment jobs are not restricted to the canonical DSE-ERP repository')
need('Verify the same version is currently healthy in UAT' in release and 'UAT_GATE_OK' in release
     and "r.get('environment')=='UAT'" in release,
     'same-run PROD job does not re-verify the exact version is healthy in UAT')
need('Verify canonical artifact matches published release' in release and 'PROD_ARTIFACT_MATCH_OK' in release
     and 'checksums.txt' in release,
     'same-run PROD job does not prove the canonical workflow artifact matches the published release checksum')
need('Configure PROD private GitHub update token' in release
     and 'configure-github-update-token.sh prod' in release
     and release.count('DSE_GITHUB_UPDATE_TOKEN: ${{ secrets.DSE_GITHUB_UPDATE_TOKEN }}') >= 2,
     'same-run PROD job does not securely configure the production private-update token')
need('deploy-oracle-release.sh prod' in release and 'PROD_DEPLOYMENT_OK' in release
     and 'PROD_PRIVATE_UPDATE_GATEWAY_OK' in release,
     'same-run PROD job does not run guarded deploy, health verification and private update-gateway verification')
need(release.count('valid_mobile_policy') >= 2 and 'mobilePolicy=environment-owned' in release
     and 'mobilePolicy=preserved' in release,
     'same-run UAT/PROD jobs do not validate independent environment-owned mobile policy')

# Keep the manual PROD workflow as a recovery/fallback path for an already UAT-approved tag.
need('workflow_dispatch:' in prod and 'environment: production' in prod,
     'manual PROD recovery workflow is missing or is not protected by the production environment')
need('Normalize release tag input' in prod and '^[vV]' in prod and 'ref: ${{ steps.normalize.outputs.tag }}' in prod,
     'manual PROD tag input is not normalized before checkout; uppercase V would fail as a Git ref')
need("steps.ssh.outcome == 'success'" in prod,
     'manual PROD cleanup can still attempt SSH before SSH configuration succeeds')
need('gh release download' in prod and 'checksums.txt' in prod,
     'manual PROD fallback does not download/verify the exact published release artifact')
need('UAT_GATE_OK' in prod and "r.get('environment')=='UAT'" in prod,
     'manual PROD fallback does not require the same release to be healthy in UAT')
need('DSE_GITHUB_UPDATE_TOKEN: ${{ secrets.DSE_GITHUB_UPDATE_TOKEN }}' in prod
     and 'configure-github-update-token.sh prod' in prod
     and 'PROD_PRIVATE_UPDATE_GATEWAY_OK' in prod,
     'manual PROD fallback does not configure and verify the private update gateway')
need('deploy-oracle-release.sh prod' in prod and 'PROD_DEPLOYMENT_OK' in prod and 'COMPATIBILITY_BASELINE' in prod and 'minimumSupportedDesktopVersion' in prod,
     'manual PROD fallback does not run the guarded desktop compatibility-aware deploy/public health verification')
need(prod.count('valid_mobile_policy') >= 2 and 'mobilePolicy=environment-owned' in prod and 'mobilePolicy=preserved' in prod and 'minimumSupportedAndroidVersion' in prod and 'latestIosVersion' in prod,
     'manual PROD fallback does not validate independent environment-owned Android/iOS compatibility policy')

need('--prerelease' in release,
     'GitHub release is not published as a prerelease for UAT validation')
need('Promote verified GitHub release to STABLE' in release and '--prerelease=false' in release
     and 'RELEASE_PROMOTED_TO_STABLE' in release,
     'same-run PROD job does not promote the exact verified release from prerelease to stable')
need(release.find('PROD_DEPLOYMENT_OK') < release.find('PROD_PRIVATE_UPDATE_GATEWAY_OK') < release.find('RELEASE_PROMOTED_TO_STABLE'),
     'release promotion can occur before PROD health/private-gateway verification')
need('gh release edit "$TAG"' in prod and '--prerelease=false' in prod and 'RELEASE_PROMOTED_TO_STABLE' in prod,
     'manual PROD fallback does not promote the exact approved release from prerelease to stable')

for token in ('sha256sum', 'pg_dump', 'pg_restore', 'PreUpgrade', 'previous-release',
              'ln -sfn', 'systemctl', '/api/runtime/health', 'rollback_binary', 'wait_for_health'):
    need(token in deploy, f'Oracle deployment safety token missing: {token}')
need('EXPECTED_MINIMUM_DESKTOP' in deploy and "r.get('minimumSupportedDesktopVersion') == min_desktop" in deploy,
     'Oracle deployment safety does not verify the release-owned minimum desktop version')
need('capture_live_mobile_policy' in deploy and 'EFFECTIVE_MINIMUM_ANDROID' in deploy and 'EFFECTIVE_LATEST_ANDROID' in deploy
     and 'EFFECTIVE_MINIMUM_IOS' in deploy and 'EFFECTIVE_LATEST_IOS' in deploy and 'MOBILE_POLICY_PRESERVED source=environment-file' in deploy
     and 'DSE_LATEST_ANDROID_VERSION' in deploy and 'DSE_LATEST_IOS_VERSION' in deploy
     and 'attempting safe service recovery before deployment' in deploy
     and "r.get('minimumSupportedAndroidVersion') == min_android" in deploy and "r.get('latestIosVersion') == latest_ios" in deploy,
     'Oracle deployment safety does not preserve environment-owned mobile policy across first-managed/recovery deployments')
need('/srv/dse-erp/${ENVIRONMENT}' in deploy and 'dse-erp-${ENVIRONMENT}' in deploy,
     'Oracle deploy script does not match the live DSE ERP release/service layout')
need('sudo -n test -r "$ENV_FILE"' in deploy and 'ENV_CONTENT=$(sudo -n cat "$ENV_FILE")' in deploy,
     'Oracle deploy script does not read the root-owned environment through non-interactive sudo')
need('sudo -n test -r "$PASSWORD_FILE"' in deploy and 'DB_PASSWORD=$(sudo -n cat "$PASSWORD_FILE")' in deploy,
     'Oracle deploy script does not read the protected DB password through non-interactive sudo')
need('sudo -n -u dseerp test -s "$PRE_BACKUP"' in deploy,
     'Oracle deploy script does not verify the protected pre-upgrade backup as dseerp')
need('${ENVIRONMENT}-db-password' in deploy and 'DSE_DB_PASSWORD' not in release and 'DSE_DB_PASSWORD' not in prod,
     'database password is not kept exclusively on the Oracle host')
need('required reviewer' in doc.lower() and 'same workflow run' in doc.lower()
     and 'manual recovery/fallback' in doc.lower() and 'local fallback is intentionally not automatic' in doc.lower(),
     'deployment documentation is missing single-run production approval/manual-fallback/local-fallback safety guidance')

for repo in ('ssdhameliya/DSE-ERP','ssdhameliya/DSE-ERP-Enterprise','ssdhameliya/DES_Mobile'):
    need(repo in cleanup_ps and repo in cleanup_sh,
         f'workflow cleanup scripts do not include repository {repo}')
need('[int]$Keep = 6' in cleanup_ps and "[switch]$Apply" in cleanup_ps
     and "status -eq 'completed'" in cleanup_ps and 'actions/runs/$($run.id)' in cleanup_ps,
     'PowerShell workflow cleanup does not default to keep=6 completed runs with explicit apply mode')
need('KEEP="${KEEP:-6}"' in cleanup_sh and 'MODE="${1:-preview}"' in cleanup_sh
     and 'select(.status == "completed")' in cleanup_sh and 'actions/runs/$id' in cleanup_sh,
     'bash workflow cleanup does not default to keep=6 completed runs with preview/apply separation')

def find_bash():
    # On GitHub Windows runners, plain `bash` can resolve to the WSL launcher
    # (C:\Windows\System32\bash.exe), which fails when no WSL distro is installed.
    # Prefer the Git for Windows bash that ships on the runner.
    if os.name == 'nt':
        candidates = []
        git = shutil.which('git')
        if git:
            git_path = Path(git)
            candidates.extend((
                git_path.with_name('bash.exe'),
                git_path.parent.parent / 'bin' / 'bash.exe',
                git_path.parent.parent / 'usr' / 'bin' / 'bash.exe',
            ))
        for env_name in ('ProgramFiles', 'ProgramFiles(x86)'):
            base = os.environ.get(env_name)
            if base:
                candidates.extend((
                    Path(base) / 'Git' / 'bin' / 'bash.exe',
                    Path(base) / 'Git' / 'usr' / 'bin' / 'bash.exe',
                ))
        for candidate in candidates:
            if candidate.is_file():
                return str(candidate)
        return None
    return shutil.which('bash')

bash = find_bash()
if bash:
    syntax=subprocess.run([bash,'-n',str(ROOT/'scripts/linux/deploy-oracle-release.sh')], cwd=ROOT)
    need(syntax.returncode==0, 'deploy-oracle-release.sh failed bash -n syntax validation')
    token_syntax=subprocess.run([bash,'-n',str(ROOT/'scripts/linux/configure-github-update-token.sh')], cwd=ROOT)
    need(token_syntax.returncode==0, 'configure-github-update-token.sh failed bash -n syntax validation')
    cleanup_syntax=subprocess.run([bash,'-n',str(ROOT/'scripts/github/cleanup-workflow-runs.sh')], cwd=ROOT)
    need(cleanup_syntax.returncode==0, 'cleanup-workflow-runs.sh failed bash -n syntax validation')
else:
    need(False, 'bash executable not available for deploy-oracle-release.sh syntax validation')

if fail:
    print('GITHUB_DEPLOYMENT_CONTRACT_FAIL')
    for item in fail: print(' -',item)
    sys.exit(1)
print('GITHUB_DEPLOYMENT_CONTRACT_OK uat=automatic prod=sequential-protected manual-fallback=yes artifact=same rollback=binary ci=deduplicated cache=default-branch')
