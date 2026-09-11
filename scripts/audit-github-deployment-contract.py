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
prod=text('.github/workflows/deploy-prod.yml')
deploy=text('scripts/linux/deploy-oracle-release.sh')
doc=text('GITHUB-DEPLOYMENT-SETUP.md')

need('server-release' in release and 'DSE-ERP-${{ needs.validate.outputs.version }}-SERVER.jar' in release,
     'release workflow does not build/upload one canonical server artifact')
need('deploy-uat:' in release and 'environment: uat' in release,
     'release workflow does not automatically deploy the release artifact to the UAT environment')
need("if: github.repository == 'ssdhameliya/DSE-ERP'" in release,
     'UAT auto-deploy is not restricted to the canonical DSE-ERP repository')
need('StrictHostKeyChecking=yes' in release and 'DSE_SSH_KNOWN_HOSTS' in release,
     'UAT workflow does not pin SSH host identity')
need('deploy-oracle-release.sh uat' in release and 'UAT_DEPLOYMENT_OK' in release and 'PUBLIC_HEALTH_URL' in release and 'compatibility_baseline' in release and 'minimumSupportedDesktopVersion' in release,
     'UAT workflow does not invoke guarded deploy + compatibility-aware public health verification')

need('workflow_dispatch:' in prod and 'environment: production' in prod,
     'PROD deployment is not manual/protected by the production environment')
need('gh release download' in prod and 'checksums.txt' in prod,
     'PROD does not download/verify the exact published release artifact')
need('UAT_GATE_OK' in prod and "r.get('environment')=='UAT'" in prod,
     'PROD workflow does not require the same release to be healthy in UAT')
need('deploy-oracle-release.sh prod' in prod and 'PROD_DEPLOYMENT_OK' in prod and 'COMPATIBILITY_BASELINE' in prod and 'minimumSupportedDesktopVersion' in prod,
     'PROD workflow does not run the guarded compatibility-aware deploy/public health verification')

need('--prerelease' in release,
     'GitHub release is not published as a prerelease for UAT validation')
need('gh release edit "$TAG"' in prod and '--prerelease=false' in prod and 'RELEASE_PROMOTED_TO_STABLE' in prod,
     'PROD workflow does not promote the exact approved release from prerelease to stable')
need(prod.find('PROD_DEPLOYMENT_OK') < prod.find('RELEASE_PROMOTED_TO_STABLE'),
     'release promotion can occur before PROD public health verification')

for token in ('sha256sum', 'pg_dump', 'pg_restore', 'PreUpgrade', 'previous-release',
              'ln -sfn', 'systemctl', '/api/runtime/health', 'rollback_binary', 'wait_for_health'):
    need(token in deploy, f'Oracle deployment safety token missing: {token}')
need('EXPECTED_MINIMUM_DESKTOP' in deploy and "r.get('minimumSupportedDesktopVersion') == minimum" in deploy,
     'Oracle deployment safety does not verify the release-owned minimum desktop version')
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
need('required reviewer' in doc.lower() and 'local fallback is intentionally not automatic' in doc.lower(),
     'deployment documentation is missing production approval/local-fallback safety guidance')

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
else:
    need(False, 'bash executable not available for deploy-oracle-release.sh syntax validation')

if fail:
    print('GITHUB_DEPLOYMENT_CONTRACT_FAIL')
    for item in fail: print(' -',item)
    sys.exit(1)
print('GITHUB_DEPLOYMENT_CONTRACT_OK uat=automatic prod=manual artifact=same rollback=binary')
