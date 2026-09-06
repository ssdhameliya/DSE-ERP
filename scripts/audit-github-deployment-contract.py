#!/usr/bin/env python3
from pathlib import Path
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
need('StrictHostKeyChecking=yes' in release and 'DSE_SSH_KNOWN_HOSTS' in release,
     'UAT workflow does not pin SSH host identity')
need('deploy-oracle-release.sh uat' in release and 'UAT_DEPLOYMENT_OK' in release and 'PUBLIC_HEALTH_URL' in release,
     'UAT workflow does not invoke guarded deploy + public health verification')

need('workflow_dispatch:' in prod and 'environment: production' in prod,
     'PROD deployment is not manual/protected by the production environment')
need('gh release download' in prod and 'checksums.txt' in prod,
     'PROD does not download/verify the exact published release artifact')
need('UAT_GATE_OK' in prod and "r.get('environment')=='UAT'" in prod,
     'PROD workflow does not require the same release to be healthy in UAT')
need('deploy-oracle-release.sh prod' in prod and 'PROD_DEPLOYMENT_OK' in prod,
     'PROD workflow does not run the guarded deploy/public health verification')

for token in ('sha256sum', 'pg_dump', 'pg_restore', 'PreUpgrade', 'previous-release',
              'ln -sfn', 'systemctl', '/api/runtime/health', 'rollback_binary', 'wait_for_health'):
    need(token in deploy, f'Oracle deployment safety token missing: {token}')
need('/srv/dse-erp/${ENVIRONMENT}' in deploy and 'dse-erp-${ENVIRONMENT}' in deploy,
     'Oracle deploy script does not match the live DSE ERP release/service layout')
need('${ENVIRONMENT}-db-password' in deploy and 'DSE_DB_PASSWORD' not in release and 'DSE_DB_PASSWORD' not in prod,
     'database password is not kept exclusively on the Oracle host')
need('required reviewer' in doc.lower() and 'local fallback is intentionally not automatic' in doc.lower(),
     'deployment documentation is missing production approval/local-fallback safety guidance')

syntax=subprocess.run(['bash','-n',str(ROOT/'scripts/linux/deploy-oracle-release.sh')], cwd=ROOT)
need(syntax.returncode==0, 'deploy-oracle-release.sh failed bash -n syntax validation')

if fail:
    print('GITHUB_DEPLOYMENT_CONTRACT_FAIL')
    for item in fail: print(' -',item)
    sys.exit(1)
print('GITHUB_DEPLOYMENT_CONTRACT_OK uat=automatic prod=manual artifact=same rollback=binary')
