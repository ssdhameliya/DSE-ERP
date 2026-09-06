#!/usr/bin/env python3
from pathlib import Path
import sys
from release_version import VERSION

ROOT=Path(__file__).resolve().parents[1]
fail=[]
def t(path): return (ROOT/path).read_text(encoding='utf-8',errors='replace')
def need(ok,msg):
    if not ok: fail.append(msg)

need(t('.mvn/maven.config').strip()==f'-Drevision={VERSION}','single release-version source is not active')
need('<version>${revision}</version>' in t('pom.xml') and '<dse.phase>${project.version}</dse.phase>' in t('pom.xml'),'root Maven release identity is not centralized')
for module in ('shared','server','desktop'):
    need('<version>${revision}</version>' in t(f'{module}/pom.xml'),f'{module} parent version is not revision-driven')
config=t('desktop/src/main/resources/config.properties')
need('deployment.mode=LOCAL' in config and 'deployment.environment=LOCAL' in config and 'server.baseUrl=' in config,'desktop deployment defaults are incomplete')
cm=t('desktop/src/main/java/org/example/config/ConfigManager.java')
need('getConfiguredDeploymentEnvironment' in cm and 'DSE_DEPLOYMENT_ENVIRONMENT' in cm,'desktop environment configuration API missing')
conn=t('desktop/src/main/java/org/example/api/runtime/DeploymentConnectionService.java')
need('expectedEnvironment' in conn and 'status.environment()' in conn,'company-server Test Connection does not protect UAT/PROD identity')
health=t('server/src/main/java/org/example/server/runtime/RuntimeController.java')
need('result.put("environment", environment)' in health and 'result.put("databaseName"' in health,'runtime health does not expose environment/database identity')
safety=t('server/src/main/java/org/example/server/runtime/DeploymentSafetyValidator.java')
need('@Value("${dse.expected.database:}")' in safety and 'expectedDatabase' in safety and 'current_database()' in safety and 'JpaNativeRepository' in safety,'server database safety validator missing')
props=t('server/src/main/resources/application.properties')
need('dse.deployment.environment=${DSE_DEPLOYMENT_ENVIRONMENT:LOCAL}' in props and 'dse.expected.database=${DSE_EXPECTED_DATABASE:}' in props,'server environment safety properties missing')
promotion=t('scripts/Enable Multi-User.ps1')
need('ExpectedVersion' in promotion and '.mvn\\maven.config' in promotion and 'deployment.environment=$Environment' in promotion,'multi-user promotion is not release/environment dynamic')
need(VERSION not in promotion and '9.0.81' not in promotion and '9.0.79' not in promotion,'multi-user promotion contains a hard-coded current/legacy release identity')
backup=t('server/src/main/java/org/example/server/authority/ServerBackupService.java')
need('setting("backup.schedule", "WEEKLY")' in backup and 'setting("backup.retention", "2")' in backup,'company-server backup policy is not Weekly / keep 2')
need('Backup verification failed:' in backup,'server backup is not validated immediately after pg_dump')
backup_ui=t('desktop/src/main/resources/fxml/pages/BackupRestore.fxml')
need('Backups to keep' in backup_ui and 'text="backups"' in backup_ui and '2 backups' in backup_ui,'backup retention UI still describes days instead of backup count')
manager=t('desktop/src/main/java/org/example/backup/BackupManager.java')
need('applyRetention(int retentionCount)' in manager and 'readRetentionCount()' in manager,'local backup retention count contract missing')
for path in ('scripts/linux/uat.env.example','scripts/linux/prod.env.example','scripts/linux/dse-erp@.service','scripts/linux/nginx-dse-erp.conf.example','scripts/linux/install-layout.sh','scripts/linux/deploy-release.sh','scripts/linux/restore-database.sh','scripts/linux/rollback-release.sh'):
    need((ROOT/path).is_file(),f'Linux deployment artifact missing: {path}')
need('DSE_EXPECTED_DATABASE=dse_erp_uat' in t('scripts/linux/uat.env.example'),'UAT database isolation template missing')
need('DSE_EXPECTED_DATABASE=dse_erp' in t('scripts/linux/prod.env.example'),'PROD database isolation template missing')
deploy=t('scripts/linux/deploy-release.sh')
need('pg_dump' in deploy and 'pg_restore --list' in deploy and '/api/runtime/health' in deploy and 'rollback_binary' in deploy,'Linux deployment does not enforce backup/health/rollback gates')
need('listen 443 ssl' in t('scripts/linux/nginx-dse-erp.conf.example'),'HTTPS reverse-proxy template missing')
if fail:
    print('CLOUD_UAT_CONTRACT_FAIL')
    for item in fail: print(' -',item)
    sys.exit(1)
print(f'CLOUD_UAT_CONTRACT_OK version={VERSION} env=UAT/PROD backup=WEEKLY/2 linux=yes')
