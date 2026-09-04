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
json_service = text('desktop/src/main/java/org/example/documentstudio/service/ErpDocumentJsonService.java')
json_adapter = text('desktop/src/main/java/org/example/documentstudio/service/JsonTemplateDataAdapter.java')
builtin_installer = text('desktop/src/main/java/org/example/documentstudio/service/BuiltInPdfTemplateInstaller.java')
renderer = text('desktop/src/main/java/org/example/documentstudio/service/PdfStudioRenderer.java')
element_model = text('desktop/src/main/java/org/example/documentstudio/model/TemplateElement.java')
field_catalog = text('desktop/src/main/java/org/example/documentstudio/service/TemplateFieldCatalog.java')
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
req('private int dataContractVersion = 2;' in model and 'private String layoutMode = "FREEFORM";' in model
    and 'isStrictFixedLayout()' in model,
    'PDF Studio template metadata must carry the stable ERP data-contract version and strict-layout mode')
req('document.number' in json_service and 'party.name' in json_service and 'transport.name' in json_service
    and 'totals.amountInWordsText' in json_service and 'items' in json_service,
    'PDF Studio JSON contract must expose document, party, transport, item and total business paths')
req('JsonTemplateDataAdapter.fromJson' in json_service and 'flatten(' in json_adapter,
    'renderer compatibility adapter must derive flat lookup values from the JSON business contract')
req('ErpDocumentJsonService.normalize' in renderer,
    'production PDF Studio rendering must consume the same normalized JSON contract shown by the mapper')
req('tableColumnWidths' in element_model and 'tableColumnAlignments' in element_model
    and 'getTableColumnWidths()' in renderer and 'getTableColumnAlignments()' in renderer,
    'fixed PDF item tables must support exact source-grid column geometry and alignment')
req('STRICT_FIXED' in builtin_installer and 'demoteExistingSalesDefaults' in builtin_installer
    and 'totals.amountInWordsText' in builtin_installer and 'PdfStudioRemoteStore.publish' in builtin_installer,
    'approved built-in Sales template must install once as fixed default and remain server-shareable')
req('UNIVERSAL_JSON' in field_catalog and 'document.number' in field_catalog and 'party.billingAddress' in field_catalog
    and 'requiredPdfFieldsFor' in field_catalog and 'isPdfRequirementMapped' in field_catalog,
    'every ERP PDF type must expose the universal JSON palette with backward-compatible required-field validation')
source_pdf = ROOT / 'desktop/src/main/resources/documentstudio/defaults/sales-invoice-jasvi.pdf'
req(source_pdf.exists(), 'approved Jasvi Sales Invoice PDF must be packaged as the built-in Sales template')
req(hashlib.sha256(source_pdf.read_bytes()).hexdigest() == '08cfb11fb104aef7c7a84c1171a5a10886b8221f4ccdadb7dcebf6dfef3340f9',
    'built-in Sales template PDF must remain byte-identical to the user-approved source PDF')
req('Available ERP / JSON Fields' in fxml and 'Browse / Drag ERP Fields' in fxml
    and '#validateMapping' in fxml and '#publishAndSetDefault' in fxml and '#viewJsonData' in fxml
    and 'Fixed PDF artwork' in fxml,
    'PDF Studio UI must expose the simplified fixed-PDF JSON mapping workflow')
req('fx:controller="org.example.documentstudio.controller.PdfStudioController"' in fxml,
    'FXML must use the rebuilt PDF Studio controller')
req('viewJsonData' in controller and 'ErpDocumentJsonService.pretty' in controller,
    'PDF Studio must expose the generated read-only JSON used for mapping')
library_controller = text('desktop/src/main/java/org/example/documentstudio/controller/DocumentStudioController.java')
req('choosePdfType' in library_controller and 'Arrays.asList(DocumentType.values())' in library_controller,
    'Import PDF and blank PDF workflows must allow General PDF plus every supported ERP document type')
req('private static final int RELEASE_VERSION = 5;' in builtin_installer
    and '58.6688, 224.05' in builtin_installer and '337.6388, 224.05' in builtin_installer,
    '9.0.77 built-in Sales template must migrate Billing/Delivery GSTIN to the corrected source-PDF baseline')
req('.builtin-sales-invoice-deleted' in builtin_installer and 'markIntentionallyDeleted' in builtin_installer and 'enforceIntentionalDeletion' in builtin_installer,
    'intentional built-in Sales template deletion must be persisted and honored by runtime lookup')
req('INTERMEDIATE' in builtin_installer and 'case "INTERMEDIATE"' in renderer,
    'multi-page PDF Studio Sales output must support final-only closing-stack suppression')
req('totals.breakdownLabels' in builtin_installer and 'totals.breakdownAmounts' in builtin_installer
    and 'financialBreakdownLabels' in json_service and 'financialBreakdownAmounts' in json_service
    and 'totals.chargeLabel' in json_service and 'totals.chargesAmount' in json_service,
    'fixed Sales template must render Standard-compatible zero/one/multiple charge breakdown rows while retaining compatibility aliases')
req('limit(2)' not in text('desktop/src/main/java/org/example/model/Sales.java')
    and 'maximum of two additional charges' not in text('server/src/main/java/org/example/server/operations/BusinessOperationsService.java'),
    'Sales additional charges must not be artificially limited to two rows')
for action in ['#showDesignMode', '#showDataPreviewMode', '#showFinalMode', '#saveDraft', '#publishTemplate', '#markDefault', '#addHideArea']:
    req(action in fxml, f'missing PDF Studio action {action}')
ET.parse(fxml_path)
legacy_controller = ROOT / 'desktop/src/main/java/org/example/documentstudio/controller/PdfDesignerController.java'
if legacy_controller.exists():
    legacy_text = legacy_controller.read_text(encoding='utf-8')
    req('DSE_PDF_DESIGNER_TOMBSTONE' in legacy_text and 'class PdfDesignerController' not in legacy_text,
        'legacy PdfDesignerController must be absent or a neutral compatibility tombstone')
req('APP_VERSION = "9.0.77"' in runtime and 'BUILD_REVISION = "9.0.77"' in runtime,
    'desktop/shared runtime identity must be 9.0.77')
req('dse.app.version=9.0.77' in props and 'dse.build.revision=9.0.77' in props,
    'server runtime identity must be 9.0.77')
req('<artifactId>dse-erp-parent</artifactId>\n  <version>9.0.77</version>' in root_pom and '<dse.phase>9.0.77</dse.phase>' in root_pom,
    'root Maven application version and phase must be 9.0.77')
for name, pom in [('shared', shared_pom), ('server', server_pom), ('desktop', desktop_pom)]:
    req('<artifactId>dse-erp-parent</artifactId>' in pom and '<version>9.0.77</version>' in pom,
        f'{name} Maven parent version must be 9.0.77')
req('version=9.0.77' in app_version, 'desktop app-version.properties must be 9.0.77')
req('DEFAULT_VERSION="9.0.77"' in update_service, 'update fallback version must be 9.0.77')
req('runtime.phase=9.0.77' in runtime_manifest, 'runtime identity manifest phase must be 9.0.77')
req('DSE ERP 9.0.77 - DEVELOPMENT / INTELLIJ ONLY' in run_bat,
    'development launcher banner must be 9.0.77')
req('DSE ERP 9.0.77 - PRODUCTION WINDOWS BUILD' in build_bat,
    'production Windows launcher banner must be 9.0.77')
req('DSE ERP 9.0.77 uses application-managed PostgreSQL.' in postgres_bat,
    'PostgreSQL launcher banner must be 9.0.77')

print('PDF_STUDIO_CONTRACT_OK version=9.0.77')
