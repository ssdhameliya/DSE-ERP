#!/usr/bin/env python3
"""DSE ERP 9.0.45 final IntelliJ/reporting/scheduler contract."""
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET
from collections import Counter

ROOT=Path(__file__).resolve().parents[1]
FAIL=[]
def need(ok,msg):
    if not ok: FAIL.append(msg)
def text(rel): return (ROOT/rel).read_text(encoding='utf-8',errors='ignore')

# Release identity / full runtime shell.
need('<version>9.0.45</version>' in text('pom.xml') and '<dse.phase>9.0.45</dse.phase>' in text('pom.xml'),'root Maven identity is not 9.0.45')
need('APP_VERSION = "9.0.45"' in text('shared/src/main/java/org/example/shared/RuntimeContract.java'),'desktop/shared runtime identity is not 9.0.45')
need('dse.app.version=9.0.45' in text('server/src/main/resources/application.properties'),'server runtime identity is not 9.0.45')
need('runtime.phase=9.0.45' in text('runtime/runtime-manifest.properties'),'bundled runtime manifest is not 9.0.45')
pg=ROOT/'runtime/postgresql'
need(pg.exists() and sum(1 for p in pg.rglob('*') if p.is_file())>100,'full bundled PostgreSQL runtime shell is missing')

# Exactly two CSS files and no legacy stylesheet reference anywhere in production resources/code.
css=sorted(p.name for p in (ROOT/'desktop/src/main/resources/css').glob('*.css'))
need(css==['dark-theme.css','light-theme.css'],f'exactly two CSS files required: {css}')
legacy=('global-search-v3.css','notification-center-v3.css','shortcut-manager.css','ui-components.css','ui-layout.css')
prod=[]
for root in [ROOT/'desktop/src/main/java',ROOT/'desktop/src/main/resources']:
    for p in root.rglob('*'):
        if p.is_file() and p.suffix.lower() in {'.java','.fxml','.properties','.css'}:
            prod.append(p)
for p in prod:
    raw=p.read_text(encoding='utf-8',errors='ignore')
    for name in legacy:
        need(name not in raw,f'legacy CSS reference remains in {p.relative_to(ROOT)}: {name}')

# Reporting FXML: no per-report KPI strip, one unified Export menu including CSV, and dashboard CSV.
viewer=ROOT/'desktop/src/main/resources/fxml/pages/ReportViewer.fxml'
reports=ROOT/'desktop/src/main/resources/fxml/pages/Reports.fxml'
ET.parse(viewer); ET.parse(reports)
v=viewer.read_text(encoding='utf-8'); r=reports.read_text(encoding='utf-8')
need('fx:id="metricPane"' not in v and 'report-metric-strip' not in v,'individual Report Viewer KPI strip must be removed')
for action in ('#exportPdf','#exportExcel','#exportCsv','#printReport'):
    need(v.count(f'onAction="{action}"')==1,f'Report Viewer must expose {action} exactly once')
need('fx:id="miCsv" text="Export CSV" onAction="#exportCsv"' in r,'Reports Dashboard CSV export is missing')
need('report-viewer-identity-panel' not in v,'obsolete separate Report Viewer identity panel must remain removed')
need('VBox.vgrow="ALWAYS" styleClass="report-card,report-results-panel"' in v,'Report Viewer result table must own remaining vertical space')
need('orientation="HORIZONTAL"' in r and 'report-category-strip-list' in r,'Report Center must use compact horizontal category navigation')
need('report-category-panel' not in r,'obsolete full-height Report Center category rail remains')

# Action controls: icon + text, never global graphic-only table action behavior.
for theme in css:
    raw=text('desktop/src/main/resources/css/'+theme)
    need(not re.search(r'\.icon-button\s*,\s*\.table-action-button\s*\{[^}]*GRAPHIC_ONLY',raw,re.S),f'{theme} still makes table actions graphic-only')
    need(re.search(r'\.table-action-button\s*\{[^}]*-fx-content-display\s*:\s*LEFT',raw,re.S) is not None,f'{theme} lacks icon+text table action rule')
table_mgr=text('desktop/src/main/java/org/example/util/DynamicTableLayoutManager.java')
need('renderedCellControlWidth(table, column)' in table_mgr and 'ACTION_CONTROL_MIN_WIDTH = 112.0' in table_mgr,'dynamic icon+text action-column rendered-control floor is missing')

# Responsive KPI must wrap before cards become cramped; individual viewer no longer enrolled.
kpi=text('desktop/src/main/java/org/example/util/ResponsiveKpiLayoutManager.java')
need('MIN_COMFORTABLE_CARD = 170.0' in kpi,'responsive KPI comfortable width floor is not the 9.0.45 compact 170px value')
need('erp-kpi-single-row' in kpi,'responsive KPI manager lacks explicit single-row KPI contract')
need('erp-kpi-single-row' in text('desktop/src/main/resources/fxml/pages/BankStatement.fxml'),'Bank Statement must keep all eight KPI cards in one row')
need('GridPane.setRowIndex(card, i / columns)' in kpi,'responsive KPI wrapping is missing')

# Dashboard/Viewer CSV implementation.
reports_ctrl=text('desktop/src/main/java/org/example/controller/ReportsController.java')
need('private String reportSemantic(ReportDefinition def)' in reports_ctrl and 'case "GST_TAX" -> "tax"' in reports_ctrl,'Report Center lacks report-specific semantic icons')
need('final MenuButton actions=new MenuButton("Actions")' in reports_ctrl,'Saved Reports Actions menu must expose icon + Actions text')
service=text('desktop/src/main/java/org/example/service/BusinessReportService.java')
need('@FXML private void exportCsv(){exportDashboard("CSV Report","business-report.csv","csv");}' in reports_ctrl,'dashboard CSV controller action missing')
need('public void exportCsv(Path file, LocalDate from, LocalDate to)' in service,'BusinessReportService CSV export missing')

# Role Master identity / lookup refresh / dead-source cleanup.
role_master=text('server/src/main/java/org/example/server/master/RoleMasterService.java')
auth=text('server/src/main/java/org/example/server/auth/AuthService.java')
item_dialog=text('desktop/src/main/java/org/example/controller/ItemDialogController.java')
master_ctrl=text('desktop/src/main/java/org/example/controller/MasterDataController.java')
lookup_dialog=text('desktop/src/main/java/org/example/controller/LookupDialogController.java')
user_access=text('desktop/src/main/java/org/example/controller/UserAccessController.java')
permission_matrix=text('desktop/src/main/java/org/example/controller/PermissionMatrixController.java')
registration_ctrl=text('desktop/src/main/java/org/example/controller/RegistrationController.java')
sales_list=text('desktop/src/main/java/org/example/controller/SalesListController.java')
need('UPPER(TRIM(lm.lookup_value)) AS role_identity' in role_master,'ROLE security identity must come from lookup_value')
need('SELF_REGISTRATION_ROLE = "SALES"' in auth and '.filter(role -> SELF_REGISTRATION_ROLE.equalsIgnoreCase(role.code()))' in auth,'public registration must use the approved active SALES Role Master identity')
need('"USER".equalsIgnoreCase' not in auth,'obsolete USER self-registration assumption remains')
for category in ('CATEGORY','UNIT','GST','DISCOUNT'):
    need(f'getValuesByCategoryCode("{category}")' in item_dialog,f'Item Dialog must resolve {category} by immutable category code')
need('displayLookupCode' in master_ctrl and '"Role Code"' in master_ctrl and '"Role Name"' in master_ctrl,'Role Master register must hide technical ROLxxx IDs')
need('roleDisplayCode' in lookup_dialog and 'internal master ID is hidden' in lookup_dialog,'Role Master dialog must hide technical ROLxxx IDs')
need('implements ScreenLifecycle' in user_access and 'if (reusedFromCache) refresh();' in user_access,'User Access must refresh role-backed data when cache is reused')
need('if (reusedFromCache) loadRoles();' in permission_matrix,'Permission Matrix must refresh Role Master values when cache is reused')
need('users.registrationRoles()' in registration_ctrl and 'cmbRole.getItems().getFirst()' in registration_ctrl,'Registration UI must consume the server-owned registration-role policy')
need('\"SALES\".equalsIgnoreCase' not in registration_ctrl,'Registration UI must not hard-code a role identity')
need('getValuesByCategoryCode(\"PAYMENT_MODE\")' in sales_list,'Sales quick payment must read Payment Mode from immutable Master category code')
need('observableArrayList(\"Cash\",\"Bank\",\"UPI\"' not in sales_list,'Sales quick payment still hard-codes Payment Mode values')

dead_paths = (
    'desktop/src/main/java/org/example/service/OtpService.java',
    'desktop/src/main/java/org/example/api/internal/SpringDataBridgeClient.java',
    'desktop/src/main/java/org/example/util/PdfPreviewDialog.java',
    'desktop/src/main/java/org/example/documentstudio/service/PdfFormFieldService.java',
    'desktop/src/main/java/org/example/documentstudio/model/PdfFormFieldRegion.java',
    'desktop/src/main/java/org/example/controller/OperationsController.java',
    'desktop/src/main/resources/fxml/pages/Operations.fxml',
    'desktop/src/main/java/org/example/documentstudio/controller/PdfDesignerController.java',
    'desktop/src/main/java/org/example/controller/PurchaseReturnDetailsController.java',
    'desktop/src/main/java/org/example/controller/PurchaseReturnContext.java',
    'desktop/src/main/resources/fxml/pages/PurchaseReturnDetails.fxml',
)
for rel in dead_paths:
    need(not (ROOT/rel).exists(),f'confirmed orphan source must remain removed: {rel}')
need((ROOT/'desktop/src/main/java/org/example/api/operations/OperationsApiClient.java').exists(),'shared OperationsApiClient was incorrectly removed')

# Production source must not carry desktop public Java types or private methods that are disconnected
# from the active Java/FXML/resource graph. This is intentionally limited to desktop production code
# because server Spring/JPA types can be annotation-discovered without direct textual callers.
desktop_java = sorted((ROOT/'desktop/src/main/java').rglob('*.java'))
desktop_resources = [p for p in (ROOT/'desktop/src/main/resources').rglob('*') if p.is_file() and p.suffix.lower() in {'.fxml','.properties'}]
source_text = {p:p.read_text(encoding='utf-8',errors='ignore') for p in desktop_java + desktop_resources}
words = Counter()
for raw in source_text.values(): words.update(re.findall(r'\b[A-Za-z_$][A-Za-z0-9_$]*\b',raw))
unlinked_types=[]
for path in desktop_java:
    raw=source_text[path]
    m=re.search(r'\bpublic\s+(?:final\s+|abstract\s+)?(?:class|interface|enum|record)\s+(\w+)',raw)
    if m and words[m.group(1)] <= 1: unlinked_types.append(path.relative_to(ROOT).as_posix()+':'+m.group(1))
need(not unlinked_types,'unlinked desktop public Java type(s): '+', '.join(unlinked_types[:8]))
private_pattern=re.compile(r'\bprivate\s+(?:static\s+)?(?:final\s+)?[\w<>, ?\[\].@]+\s+(\w+)\s*\([^;{}]*\)\s*(?:throws\s+[^\{]+)?\{')
unlinked_private=[]
for path in desktop_java:
    for match in private_pattern.finditer(source_text[path]):
        name=match.group(1)
        if name!='initialize' and words[name] <= 1: unlinked_private.append(path.relative_to(ROOT).as_posix()+':'+name)
need(not unlinked_private,'unlinked desktop private method(s): '+', '.join(unlinked_private[:8]))

# Every audit shipped in the release must be an actual CI/release gate; stale historical audit scripts
# are removed instead of being bundled as dead validation code.
workflow_text=text('.github/workflows/ci.yml')+'\n'+text('.github/workflows/release.yml')
workflow_audits=set(re.findall(r'scripts/(audit-[A-Za-z0-9_.-]+\.py)',workflow_text))
shipped_audits={p.name for p in (ROOT/'scripts').glob('audit-*.py')}
need(shipped_audits==workflow_audits,'scripts contains audit files not wired to CI/release or workflow references missing audits')

# Scheduled reports: combined PDF + XLSX must canonicalize to PDF_XLSX and generate both artifacts.
schedule=text('server/src/main/java/org/example/server/reporting/ReportScheduleService.java')
need('String format = canonicalToken(request.format());' in schedule,'scheduled format does not use canonical token normalization')
need('replaceAll("[^A-Z0-9]+", "_")' in schedule and 'replaceAll("_+", "_")' in schedule,'combined output normalization is not collapse-safe')
need('case "PDF_XLSX" -> {' in schedule,'combined PDF_XLSX execution branch missing')
need('exporter.pdf(pdf, result, visible); exporter.excel(xlsx, result, visible); files.add(pdf); files.add(xlsx);' in schedule,'combined schedule does not generate and attach both artifacts')
need('format.getItems().setAll("PDF","XLSX","PDF + XLSX","CSV")' in reports_ctrl,'scheduled-report combined output option missing from UI')

if FAIL:
    print('FINAL_9_0_45_FAIL')
    for x in FAIL: print(' -',x)
    sys.exit(1)
print('FINAL_9_0_45_OK css=2 reporting=table-first action=icon+text bank_kpi=8x1 semantic_fields=yes theme_parity=yes dashboard_csv=yes schedule_pdf_xlsx=both role_master=yes dynamic_lookups=yes dead_code=clean full_runtime=yes')
