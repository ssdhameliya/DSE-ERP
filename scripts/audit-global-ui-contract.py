#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
failures=[]
def require(condition, message):
    if not condition: failures.append(message)
def text(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

# Global semantic field/dialog enhancement: all ordinary FXML stages and OwnedDialog
# surfaces must pass the same label/icon/color enhancer.
icon_factory=text('desktop/src/main/java/org/example/util/IconFactory.java')
platform=text('desktop/src/main/java/org/example/util/PlatformUiSupport.java')
dialog_presentation=text('desktop/src/main/java/org/example/util/DialogPresentation.java')
drawer=text('desktop/src/main/java/org/example/util/RegisterDetailDrawer.java')
require('finance-field-label' in icon_factory, 'Finance field labels must participate in global semantic field decoration')
require('applySemanticLabelColour' in icon_factory and 'erp-field-label-colour-' in icon_factory,
        'Global field decoration must apply matching semantic colour to caption text as well as its icon')
require('applySemanticLabelColour(caption, semantic)' in drawer,
        'Canonical detail-drawer captions must use the same semantic icon/text colour contract')
require('ProfessionalUiEnhancer.enhance(stage.getScene().getRoot())' in platform,
        'FXML Stage dialogs must receive the global professional/semantic enhancer')
require('ProfessionalUiEnhancer.enhance(scene.getRoot())' in dialog_presentation,
        'OwnedDialog/DialogPresentation surfaces must receive the global professional/semantic enhancer')
for token in ('class RegisterDetailDrawer', 'attachBesideTable', 'showRecord', 'erp-global-detail-drawer'):
    require(token in drawer, f'Missing canonical register drawer contract: {token}')

# Current standardized registers/masters must use click=view and an explicit drawer.
controllers = [
    'BankExpenseController.java', 'ReconSupplierController.java', 'PurchaseReconController.java',
    'PartyMasterController.java', 'ItemMasterController.java', 'InventoryController.java',
    'UserAccessController.java', 'MasterDataController.java'
]
controller_dir=ROOT/'desktop/src/main/java/org/example/controller'
for name in controllers:
    source=(controller_dir/name).read_text(encoding='utf-8')
    require('RegisterDetailDrawer' in source, f'{name} must use the canonical read-only detail drawer')
    require(not re.search(r'getClickCount\(\)\s*==\s*2', source), f'{name} must not use hidden double-click behavior')
    require(re.search(r'getClickCount\(\)\s*(?:==|!=)\s*1', source) is not None,
            f'{name} must keep single-click record viewing')

bank=text('desktop/src/main/java/org/example/controller/BankExpenseController.java')
recon_supplier=text('desktop/src/main/java/org/example/controller/ReconSupplierController.java')
purchase_recon=text('desktop/src/main/java/org/example/controller/PurchaseReconController.java')
reminder=text('desktop/src/main/java/org/example/controller/ReminderCenterController.java')
reminder_fxml=text('desktop/src/main/resources/fxml/pages/ReminderCenter.fxml')
require('showEntryDetails(row)' in bank and 'requestLinkedEntry' in bank,
        'Linked Bank/Expense navigation must reveal details rather than silently edit')
require('"View"' in recon_supplier and 'showDetails(row)' in recon_supplier,
        'Recon Supplier View must use the read-only drawer')
require('"View"' in purchase_recon and 'showDetails(row)' in purchase_recon and 'editRecord' in purchase_recon,
        'Purchase Recon must keep View and Edit as separate paths')
require(not re.search(r'getClickCount\(\)\s*==\s*2', reminder), 'Reminder Center must not hide an Edit action behind double-click')
require('double-click to edit' not in reminder_fxml.lower(), 'Reminder Center help text must not advertise double-click editing')

# New controller code is not allowed to bypass the shared dialog shell with raw
# Alert/Dialog/TextInputDialog constructors.
raw_dialog_pattern=re.compile(r'new\s+(?:Dialog|Alert|TextInputDialog)\s*(?:<|\()')
for path in sorted(controller_dir.rglob('*.java')):
    source=path.read_text(encoding='utf-8')
    require(raw_dialog_pattern.search(source) is None,
            f'{path.name} creates a raw JavaFX business dialog; use OwnedDialog/OwnedAlert/OwnedTextInputDialog')

# Every controller-created Stage must run through PlatformUiSupport so semantic field
# decoration, ownership, modality and theme behavior cannot drift on future screens.
for path in sorted(controller_dir.rglob('*.java')):
    source=path.read_text(encoding='utf-8')
    if 'new Stage' in source:
        require('PlatformUiSupport.configureDialogStage' in source,
                f'{path.name} creates a Stage without the standard dialog-stage contract')

# Double-clicks are disallowed by default. These two existing navigation-only surfaces
# are deliberate exceptions: they open/view a linked result, never edit the record.
# Reporting uses explicit Open/View Source/Edit actions and therefore has no exception.
allowed_double_click={'GlobalSearchController.java','NotificationCenterController.java','Customer360Controller.java'}
for path in sorted(controller_dir.rglob('*.java')):
    source=path.read_text(encoding='utf-8')
    if re.search(r'getClickCount\(\)\s*==\s*2', source):
        require(path.name in allowed_double_click,
                f'{path.name} adds double-click behavior; record screens must default to single-click View and explicit Edit')

# Status/value semantics must use the shared icon + matching text-colour renderer on
# the standardized record screens and the 9.0.3 import/history additions.
semantic_cells=text('desktop/src/main/java/org/example/util/SemanticTableCells.java')
bank_statement=text('desktop/src/main/java/org/example/controller/BankStatementController.java')
inventory=text('desktop/src/main/java/org/example/controller/InventoryController.java')
party=text('desktop/src/main/java/org/example/controller/PartyMasterController.java')
user_access=text('desktop/src/main/java/org/example/controller/UserAccessController.java')
import_controller=text('desktop/src/main/java/org/example/controller/ImportController.java')
require('statusIcon' in semantic_cells and 'case "reconcile"' in semantic_cells and 'case "bank"' in semantic_cells,
        'Shared semantic status cells must own value-specific glyph and colour presentation')
require('SemanticTableCells.status("bank")' in bank_statement and 'SemanticTableCells.status("reconcile")' in bank_statement,
        'Bank Statement current/history status cells must use semantic icon + colour rendering')
require('SemanticTableCells.status("reconcile")' in purchase_recon,
        'Purchase Recon status must use semantic icon + colour rendering')
require('SemanticTableCells.status("status")' in recon_supplier,
        'Recon Supplier status must use semantic icon + colour rendering')
require('SemanticTableCells.status("inventory")' in inventory,
        'Inventory stock status must use semantic icon + colour rendering')
require('SemanticTableCells.activeBoolean()' in party,
        'CRM/HRM Active status must use semantic icon + colour rendering')
require(user_access.count('SemanticTableCells.status("status")') >= 3,
        'User Access user/MFA/role statuses must use semantic icon + colour rendering')
require('SemanticTableCells.status("validation")' in import_controller,
        'Import Validation result columns must use semantic icon + colour rendering')

# Exact release identity: desktop and server publish the same application/build value.
runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
server_props=text('server/src/main/resources/application.properties')
require('APP_VERSION = "9.0.76"' in runtime and 'BUILD_REVISION = "9.0.76"' in runtime,
        '9.0.76 desktop runtime version and build revision must be synchronized')
require('dse.app.version=9.0.76' in server_props and 'dse.build.revision=9.0.76' in server_props,
        '9.0.76 server runtime version and build revision must match the desktop contract')

if failures:
    print('GLOBAL_UI_CONTRACT_FAIL')
    for failure in failures: print(' -', failure)
    sys.exit(1)
print('GLOBAL_UI_CONTRACT_OK standardized_registers=%d' % len(controllers))
