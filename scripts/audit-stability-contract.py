#!/usr/bin/env python3
from pathlib import Path
import sys
R=Path(__file__).resolve().parents[1]
def text(p): return (R/p).read_text(encoding='utf-8',errors='ignore')
def need(ok,msg):
    if not ok:
        print('FAIL:',msg); sys.exit(1)
# Release identity
need('<version>9.0.73</version>' in text('pom.xml') and '<dse.phase>9.0.73</dse.phase>' in text('pom.xml'),'root identity')
need('APP_VERSION = "9.0.73"' in text('shared/src/main/java/org/example/shared/RuntimeContract.java'),'shared identity')
need('dse.app.version=9.0.73' in text('server/src/main/resources/application.properties'),'server identity')
need('version=9.0.73' in text('desktop/src/main/resources/app-version.properties'),'desktop identity')
# Workspace recovery
wm=text('desktop/src/main/java/org/example/config/WorkspaceManager.java')
sw=text('desktop/src/main/java/org/example/controller/SetupWizardController.java')
fxml=text('desktop/src/main/resources/fxml/pages/SetupWizard.fxml')
main=text('desktop/src/main/java/org/example/app/Main.java')
for tok in ['inspectExisting','configureExisting','markSetupComplete']:
    need(tok in wm,'WorkspaceManager missing '+tok)
need('Use Existing Workspace' in fxml and 'useExistingWorkspace' in sw,'permanent existing-workspace action missing')
need('new SetupApiClient().requiresSetup()' in sw,'existing workspace database verification missing')
need('Select Existing Workspace' in main and 'showStartupFailureWithWorkspaceRecovery' in main,'startup-error workspace recovery missing')
# Master data + transaction snapshots
mig=text('server/src/main/resources/db/migration/V9_0_63__workspace_master_email_stability.sql')
runner=text('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java')
for code in ['CATEGORY','UNIT','MATERIAL','BRAND','GST']:
    need("('"+code+"'" in mig or "('CATEGORY','CATEGORY'" in mig if code=='CATEGORY' else code in mig,'migration missing '+code)
need('V9_0_63__workspace_master_email_stability' in runner,'9.0.73 migration not registered')
for col in ['category_snapshot','hsn_snapshot','unit_snapshot']:
    need(col in mig and col in runner,'snapshot migration/schema guard missing '+col)
for f,tokens in {
 'desktop/src/main/resources/fxml/pages/Sale.fxml':['Item Code','Category','HSN/SAC','Unit'],
 'desktop/src/main/resources/fxml/pages/Purchase.fxml':['Item Code','Category','HSN/SAC','Unit'],
 'desktop/src/main/resources/fxml/pages/QuotationEditor.fxml':['Category','HSN/SAC','Unit'],
 'desktop/src/main/resources/fxml/pages/Inventory.fxml':['GST %']
}.items():
    s=text(f)
    for tok in tokens: need(tok in s,f+' missing '+tok)
# Email safety / UX
reg=text('desktop/src/main/java/org/example/controller/RegistrationController.java')
policy=text('desktop/src/main/java/org/example/util/RegistrationErrorPolicy.java')
mail=text('server/src/main/java/org/example/server/auth/EmailDeliveryException.java')
emailctl=text('server/src/main/java/org/example/server/authority/BusinessEmailController.java')
settings=text('desktop/src/main/java/org/example/controller/SettingsController.java')
need('RegistrationErrorPolicy.userMessage(e)' in reg,'registration error sanitizer missing')
need('RegistrationErrorPolicy.isCaptchaFailure(e)' in reg,'CAPTCHA-specific refresh missing')
need('Verification email is temporarily unavailable' in policy and 'Verification email is temporarily unavailable' in mail,'public email message missing')
need('535' in mail and 'Google App Password' in mail,'admin SMTP authentication guidance missing')
need('new Settings(current.email(), "", current.host(), current.port(), !current.password().isBlank())' in emailctl,'server settings still risk returning SMTP password')
need('txtSmtpPassword.clear()' in settings and 'leave blank to keep current password' in settings,'desktop password configured-state handling missing')
print('STABILITY_9063_CONTRACT_OK workspace=recoverable master_data=restored email=sanitized item_metadata=complete')
