#!/usr/bin/env python3
from pathlib import Path
import sys
from release_version import VERSION

ROOT=Path(__file__).resolve().parents[1]
fail=[]
def t(p): return (ROOT/p).read_text(encoding='utf-8',errors='replace')
def need(ok,msg):
    if not ok: fail.append(msg)

version=t('.mvn/maven.config')
server=t('server/src/main/java/org/example/server/authority/ServerBackupService.java')
controller=t('server/src/main/java/org/example/server/authority/ServerBackupController.java')
security=t('server/src/main/java/org/example/server/security/SecurityConfig.java')
client=t('desktop/src/main/java/org/example/api/authority/ServerBackupClient.java')
recovery=t('desktop/src/main/java/org/example/backup/LocalRecoveryManager.java')
workspace=t('desktop/src/main/java/org/example/config/WorkspaceManager.java')
main=t('desktop/src/main/java/org/example/app/Main.java')
fxml=t('desktop/src/main/resources/fxml/pages/BackupRestore.fxml')
settings=t('desktop/src/main/java/org/example/controller/SettingsController.java')

need(version.strip() == f'-Drevision={VERSION}', f'release version is not {VERSION}')
need('createRecoveryPackage()' in server and 'database.pgbackup' in server,'server recovery package does not create a fresh database snapshot')
for token in ('workspace/Attachments','workspace/Documents','workspace/Templates','database.sha256'):
    need(token in server,f'server recovery package missing {token}')
need('/recovery-package' in controller and 'application/zip' in controller,'recovery package endpoint missing')
need('HttpMethod.POST, "/api/authority/backups/recovery-package"' in security and 'hasAuthority("ROLE_ADMIN")' in security,
     'recovery package endpoint is not admin-only')
need('downloadRecoveryPackage' in client and 'BodyHandlers.ofByteArray' in client,'shared desktop recovery-package download missing')
need('stageForLocal' in recovery and 'database.sha256' in recovery and 'Unsafe recovery package path' in recovery,
     'LOCAL recovery package validation/staging contract missing')
need('markDatabaseRestoreSuccess' in recovery and 'applyPendingFilesIfReady' in recovery,
     'LOCAL recovery does not gate files on successful database restore')
need('ConfigBeforeRecovery-' in workspace and 'deployment.mode", DeploymentMode.LOCAL.name()' in workspace,
     'LOCAL recovery target/config preservation contract missing')
need('LOCAL_RECOVERY_FILES_FAILED' in main and 'LOCAL recovery completed' in main,
     'startup does not block/complete LOCAL recovery safely')
need('Recover Server to LOCAL' in fxml,'admin Server -> LOCAL recovery action is missing from Backup & Restore UI')
need('A shared company cannot be changed back to local mode with a simple toggle.' in settings,
     'simple Shared -> LOCAL toggle protection was removed')
need('automatically' not in recovery.lower().split('offline fallback')[0] if 'offline fallback' in recovery.lower() else True,
     'LOCAL recovery must remain explicit rather than automatic fallback')

if fail:
    print('DISASTER_RECOVERY_CONTRACT_FAIL')
    for x in fail: print(' -',x)
    sys.exit(1)
print('DISASTER_RECOVERY_CONTRACT_OK admin_only=yes fresh_snapshot=yes files=attachments,documents,templates local_switch=explicit')
