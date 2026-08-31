from pathlib import Path
import hashlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def text(path):
    return (ROOT / path).read_text(encoding='utf-8')

def req(condition, message):
    if not condition:
        print('FAIL:', message)
        sys.exit(1)

# Locked production generation files must remain byte-identical to the approved 9.0.7 baseline.
locked = {
    'desktop/src/main/java/org/example/documentstudio/service/DocumentOutputService.java': '5d84c57c22299bfedcc969512b33f2a8cd0371455918ef82f71037827ee2686c',
    'desktop/src/main/java/org/example/service/InvoicePdfService.java': 'ddc9bd1120388058ae60742f343553c6c0de2634e885360deef8e31298033fa8',
    'desktop/src/main/java/org/example/invoice/service/SalesTaxInvoiceService.java': '27eb0498f015a410b60aa86f71c8bced4e0ff0e45f8a7e0207b7be9a7ce74082',
}
for rel, expected in locked.items():
    digest = hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()
    req(digest == expected, f'locked production generation file changed: {rel}')

repo = text('desktop/src/main/java/org/example/documentstudio/service/PdfStudioTemplateRepository.java')
model = text('desktop/src/main/java/org/example/documentstudio/model/DocumentTemplate.java')
controller = text('desktop/src/main/java/org/example/documentstudio/controller/PdfStudioController.java')
fxml_path = ROOT / 'desktop/src/main/resources/fxml/pages/PdfDesigner.fxml'
fxml = fxml_path.read_text(encoding='utf-8')
server = text('server/src/main/java/org/example/server/authority/PdfStudioTemplateController.java')
facade = text('desktop/src/main/java/org/example/documentstudio/service/TemplateStorageService.java')
renderer_facade = text('desktop/src/main/java/org/example/documentstudio/service/PdfTemplateRenderer.java')
runtime = text('shared/src/main/java/org/example/shared/RuntimeContract.java')
props = text('server/src/main/resources/application.properties')
root_pom = text('pom.xml')
shared_pom = text('shared/pom.xml')
server_pom = text('server/pom.xml')
desktop_pom = text('desktop/pom.xml')
app_version = text('desktop/src/main/resources/app-version.properties')
update_service = text('desktop/src/main/java/org/example/update/UpdateService.java')
runtime_manifest_path = ROOT / 'runtime/runtime-manifest.properties'
req(runtime_manifest_path.exists(), 'runtime identity manifest must be tracked in every release source handoff')
runtime_manifest = runtime_manifest_path.read_text(encoding='utf-8')
run_bat = text('Run DSE ERP.bat')
build_bat = text('Build Production Windows.bat')
postgres_bat = text('scripts/start-postgresql.cmd')

req('resolve("DocumentStudio").resolve("Pdf")' in repo, 'PDF Studio 3 must use an isolated template root')
req('private static final String PUBLISHED = "published"' in repo and 'private static final String ACTIVE = "active"' in repo,
    'published candidate and active runtime snapshots must be physically separate')
req('filter(DocumentTemplate::isRuntimeEnabled)' in repo and 'filter(t -> t.getActiveVersion() > 0)' in repo,
    'runtime lookup must require explicit activation')
req('replaceSnapshot(folder(template), ACTIVE, activeMeta)' in repo,
    'Mark Default must create a separate active snapshot')
req('if (template.isUnpublishedChanges())' in repo and 'Publish this template before marking it as the system default.' in repo,
    'draft changes must be blocked from activation')
req('Files.copy(sourcePdf, folder.resolve(ORIGINAL)' in repo and 'PdfImportSecurityService.normalizeForEditing' in repo,
    'uploaded PDF must be retained separately from the editable normalized copy')
req('private boolean runtimeEnabled;' in model and 'private boolean unpublishedChanges = true;' in model,
    'template model must explicitly distinguish draft state from runtime activation')
req('private int publishedVersion;' in model and 'private int activeVersion;' in model,
    'published and active versions must be tracked separately')
req('showDesignMode' in controller and 'showDataPreviewMode' in controller and 'showFinalMode' in controller,
    'PDF Studio must expose Design, Data Preview and Final PDF modes')
req('publishTemplate' in controller and 'markDefault' in controller,
    'Publish and Mark Default must be separate user actions')
req('hideSourceText' in controller and 'hideSourceImage' in controller and 'hideSourceVector' in controller,
    'deleting imported PDF content must be implemented as a non-destructive hide operation')
req('addHideArea' in controller, 'manual non-destructive Hide Area tool must be available')
req('dataPreviewMode && currentPreviewData != null' in controller,
    'ERP data should resolve on the editable canvas only in Data Preview mode')
req('PdfStudioTemplateRepository' in facade and 'PdfStudioRenderer' in renderer_facade,
    'legacy entry-point class names must be compatibility facades, not the Studio implementation')
req('PDF_STUDIO_V3_TEMPLATE' in server and '/api/pdf-studio/templates' in server,
    'server synchronization must have an isolated PDF Studio 3 endpoint and resource namespace')
req('fx:controller="org.example.documentstudio.controller.PdfStudioController"' in fxml,
    'FXML must use the rebuilt PDF Studio controller')
for action in ['#showDesignMode', '#showDataPreviewMode', '#showFinalMode', '#saveDraft', '#publishTemplate', '#markDefault', '#addHideArea']:
    req(action in fxml, f'missing PDF Studio action {action}')
ET.parse(fxml_path)
legacy_controller = ROOT / 'desktop/src/main/java/org/example/documentstudio/controller/PdfDesignerController.java'
if legacy_controller.exists():
    legacy_text = legacy_controller.read_text(encoding='utf-8')
    req('DSE_PDF_DESIGNER_TOMBSTONE' in legacy_text and 'class PdfDesignerController' not in legacy_text,
        'legacy PdfDesignerController must be absent or a neutral compatibility tombstone')
req('APP_VERSION = "9.0.46"' in runtime and 'BUILD_REVISION = "9.0.46"' in runtime,
    'desktop/shared runtime identity must be 9.0.18')
req('dse.app.version=9.0.46' in props and 'dse.build.revision=9.0.46' in props,
    'server runtime identity must be 9.0.18')
req('<artifactId>dse-erp-parent</artifactId>\n  <version>9.0.46</version>' in root_pom and '<dse.phase>9.0.46</dse.phase>' in root_pom,
    'root Maven application version and phase must be 9.0.18')
for name, pom in [('shared', shared_pom), ('server', server_pom), ('desktop', desktop_pom)]:
    req('<artifactId>dse-erp-parent</artifactId>' in pom and '<version>9.0.46</version>' in pom,
        f'{name} Maven parent version must be 9.0.18')
req('version=9.0.46' in app_version, 'desktop app-version.properties must be 9.0.18')
req('DEFAULT_VERSION="9.0.46"' in update_service, 'update fallback version must be 9.0.18')
req('runtime.phase=9.0.46' in runtime_manifest, 'runtime identity manifest phase must be 9.0.18')
req('DSE ERP 9.0.46 - DEVELOPMENT / INTELLIJ ONLY' in run_bat,
    'development launcher banner must be 9.0.18')
req('DSE ERP 9.0.46 - PRODUCTION WINDOWS BUILD' in build_bat,
    'production Windows launcher banner must be 9.0.18')
req('DSE ERP 9.0.46 uses application-managed PostgreSQL.' in postgres_bat,
    'PostgreSQL launcher banner must be 9.0.18')

print('PASS: DSE ERP 9.0.18 runtime with PDF Studio + release identity contract')
