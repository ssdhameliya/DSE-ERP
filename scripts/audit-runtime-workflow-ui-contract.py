from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
def text(p): return (r/p).read_text(encoding='utf-8', errors='replace')
def need(c,m):
    if not c: raise SystemExit('FAIL: '+m)
# Version identity
for p in ['pom.xml','server/pom.xml','desktop/pom.xml','shared/pom.xml']:
    need('9.0.57' in text(p),f'9.0.57 missing from {p}')
for p in ['runtime/runtime-manifest.properties','desktop/src/main/resources/app-version.properties','server/src/main/resources/application.properties','shared/src/main/java/org/example/shared/RuntimeContract.java']:
    need('9.0.57' in text(p),f'9.0.57 missing from {p}')
need('9.0.57 - DEVELOPMENT / INTELLIJ ONLY' in text('Run DSE ERP.bat'),'launcher identity not 9.0.57')
# No markdown files
need(not list(r.rglob('*.md')),'markdown files present')
# CSS exactly two runtime files
css=list((r/'desktop/src/main/resources/css').glob('*.css'))
need(sorted(p.name for p in css)==['dark-theme.css','light-theme.css'],f'CSS set incorrect: {[p.name for p in css]}')
# XML parse
fxmls=list((r/'desktop/src/main/resources').rglob('*.fxml'))
for p in fxmls: ET.parse(p)
# Menu position
f=text('desktop/src/main/resources/fxml/pages/Dashboard.fxml')
need(f.index('text="Data Import"') < f.index('text="Project Execution"'),'Project Execution is not after Data Import')
# Customer 360
c=text('desktop/src/main/resources/fxml/pages/Customer360.fxml')
for tab in ['Overview','Contacts','Quotations','Sales Orders','Projects','Invoices','Payments','Notes','Documents']:
    need(f'text="{tab}"' in c,f'Customer360 missing {tab}')
need('customer-360-profile-card' in c,'Customer360 profile card missing')
for p in css:
    s=text(p.relative_to(r)); need('.customer-360-tabs' in s and '.customer-360-profile-card' in s,f'Customer360 CSS missing in {p.name}')
svc=text('server/src/main/java/org/example/server/customer360/Customer360Service.java')
need('COALESCE(total_amount,0)' in svc,'Customer360 quotation total_amount fix missing')
need('COALESCE(total,0)' not in svc,'stale Customer360 quotation total column remains')
# Workflow numbering
w=text('server/src/main/java/org/example/server/workflow/WorkflowService.java')
need('assignedNo=create?allocateNumber' in w,'save-time workflow number allocation missing')
need('Auto-generated on Save' in w,'non-consuming preview number missing')
wc=text('desktop/src/main/java/org/example/controller/WorkflowDocumentController.java')
need('Auto-generated on Save' in wc,'desktop auto-number placeholder missing')
need('safeNext' not in wc,'old consuming safeNext remains')
# API error/runtime dynamic base
for p in ['desktop/src/main/java/org/example/api/workflow/WorkflowApiClient.java','desktop/src/main/java/org/example/api/customer360/Customer360ApiClient.java','desktop/src/main/java/org/example/api/support/SupportApiClient.java']:
    s=text(p); need('getDataApiBaseUrl()' in s,f'dynamic API base missing from {p}')
a=text('desktop/src/main/java/org/example/api/ApiRuntime.java')
need('transportMessage' in a and 'same application/build version' in a.lower(),'precise transport/version errors missing')
# Icons and sidebar
ic=text('desktop/src/main/java/org/example/util/IconFactory.java')
for semantic in ['sales-order','purchase-order','goods-receipt','dispatch','file-export']:
    need(semantic in ic,f'icon semantic missing {semantic}')
d=text('desktop/src/main/java/org/example/controller/DashboardController.java')
for semantic in ['"workflow"','"project"','"sales-order"','"purchase-order"','"goods-receipt"','"dispatch"','"import"']:
    need(semantic in d,f'sidebar semantic missing {semantic}')
for p in css:
    s=text(p.relative_to(r)); need('.erp-sidebar .button' in s and 'nav-expanded' in s,f'sidebar 3D normalization missing in {p.name}')
# Registration QR + layout + single configured role
rf=text('desktop/src/main/resources/fxml/pages/Registration.fxml')
need('fx:id="txtRole"' in rf and 'fx:id="cmbRole"' not in rf,'registration role should be read-only configured field')
need('Google / Microsoft Authenticator' in rf and 'Submit for Admin Approval' in rf,'registration security wording missing')
rc=text('desktop/src/main/java/org/example/controller/RegistrationController.java')
for token in ['showAuthenticatorSetup','provisioningUri()','manualSecret()','completeRegistrationMfa','Copy Setup Key']:
    need(token in rc,f'registration QR flow missing {token}')
need((r/'desktop/src/main/java/org/example/util/QrCodeImageFactory.java').exists(),'QR image factory missing')
q=text('desktop/src/main/java/org/example/util/QrCodeImageFactory.java')
need('BarcodeQRCode' in q and 'PDFRenderer' in q,'QR rendering implementation incomplete')

# 9.0.57 stabilization additions
reg=text('desktop/src/main/java/org/example/controller/RegistrationController.java')
need('import org.example.service.BrandImagePresenter;' in reg,'Registration BrandImagePresenter import missing')
need('registration-scroll' not in rf,'Registration should not require normal-screen ScrollPane')
settings=text('desktop/src/main/java/org/example/controller/SettingsController.java')
need('SECURITY' in settings and 'security.session.timeout.minutes' in settings and 'security.session.warning.minutes' in settings,'Security & Session settings missing')
session=text('desktop/src/main/java/org/example/service/SessionActivityManager.java')
need('OwnedDialog<ButtonType>' in session and 'SupportApiClient' in session and 'reloadPolicy' in session,'central configurable owned session warning missing')
need('REF_PROJECT' in text('server/src/main/java/org/example/server/master/MasterDataService.java'),'Project reference format master missing')
need('lookup_master' in w and 'REF_SALES_ORDER' in w and 'REF_GRN' in w,'workflow numbering not Master-defined')
need((r/'server/src/main/resources/db/migration/V9_0_56__customer360_runtime_guard.sql').exists(),'Customer360 runtime guard migration missing')
support=text('server/src/main/java/org/example/server/support/SupportService.java')
need("created_at::text" in support,'attachment timestamp PostgreSQL-safe cast missing')
need('workflow-standard-dialog' in wc and 'IconFactory.semanticForLabel' in wc,'workflow dialogs not standardized')
need('customer-360-profile-compact' in c,'Customer360 compact profile missing')
# Same-version corrective runtime checks (9.0.57): no stale :8080 fallback, upgraded C360 schema safety,
# server-owned session policy whitelist, and no generic document icon for unknown actions.
cfg=text('desktop/src/main/java/org/example/config/ConfigManager.java')
need('http://127.0.0.1:8080' not in cfg,'stale local API :8080 fallback remains')
rb=text('desktop/src/main/java/org/example/api/runtime/RuntimeBootstrapper.java')
need('if (!isPackagedRuntime()) return;' not in rb.split('private static void prepareManagedServerEndpoint()',1)[1].split('private static boolean isLocalApiEndpoint()',1)[0],'IntelliJ managed endpoint preparation is still bypassed')
need('DEFAULT_MANAGED_SERVER_PORT' in rb,'managed local endpoint authority missing')
need('created_at::text' in svc and 'updated_at::text' in svc,'Customer360 timestamp reads are not upgrade-safe')
need((r/'server/src/main/resources/db/migration/V9_0_56_1__customer360_upgrade_compatibility.sql').exists(),'same-version Customer360 compatibility migration missing')
need('security.session.' in support,'server-owned security session setting keys are not permitted')
need('semantic = originalText.isBlank() ? "actions" : "document"' not in ic,'generic button document fallback remains')
need('return text.isBlank() ? null : "document"' not in ic,'generic button semantic fallback remains')


# Runtime regressions confirmed from 9.0.57 field logs.
runner=text('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java')
for migration in ['V9_0_52__customer_360','V9_0_56__customer360_runtime_guard','V9_0_56_1__customer360_upgrade_compatibility','V9_0_56_2__customer360_schema_repair']:
    need(migration in runner,f'Customer360 migration not registered: {migration}')
for table in ['party_contact','party_note']:
    need(f'requireTable("{table}")' in runner,f'Customer360 startup schema guard missing {table}')
need('requireColumn("party_master", "attachment_path")' in runner,'Customer attachment schema guard missing')
need('COALESCE(lm.active,1)' not in w and 'COALESCE(lm.is_active,1)' in w,'workflow reference lookup uses wrong active column')
insights=text('server/src/main/java/org/example/server/insights/InsightsService.java')
need('today.minusDays(1),today,"SALE"' in insights,'Dashboard ageing document type must be bound after date parameters')
need('"SALE",today.minusDays(30)' not in insights,'Dashboard ageing still binds SALE into a date placeholder')
need((r/'server/src/main/resources/db/migration/V9_0_56_2__customer360_schema_repair.sql').exists(),'Customer360 schema repair migration missing')

# Final same-version 9.0.57 consolidated corrective checks.
# Workflow REST must serialize java.time consistently and error dialogs must never derive HTTP 127 from a loopback URL.
need('JavaTimeModule' in a and 'WRITE_DATES_AS_TIMESTAMPS' in a,'shared API JSON runtime does not support ISO Java Time values')
dpom=text('desktop/pom.xml')
need('jackson-datatype-jsr310' in dpom,'desktop Java Time Jackson module dependency missing')
dialog=text('desktop/src/main/java/org/example/util/DialogPresentation.java')
need('([1-5][0-9]{2})' in dialog and 'extractHttpCode' in dialog,'strict HTTP status extraction missing')
need('substring(i + 4)' not in dialog,'legacy HTTP digit scraping remains')
# Customer 360 Sales Orders shows authoritative workflow orders plus direct/unlinked sales and previews managed documents.
need('Direct / Unlinked Sales' in c and 'fx:id="tblDirectSales"' in c,'Customer360 direct/unlinked sales table missing')
need('text="Preview" onAction="#viewDocument"' in c,'Customer360 document action is not Preview')
capi=text('desktop/src/main/java/org/example/api/customer360/Customer360ApiClient.java')
need('/direct-sales' in capi,'Customer360 direct-sales API client missing')
cctl=text('desktop/src/main/java/org/example/controller/Customer360Controller.java')
need('tblDirectSales' in cctl and 'AttachmentPreviewSupport.materializeRequired' in cctl,'Customer360 direct-sales/preview controller support missing')
need('showSaveDialog' not in cctl,'Customer360 Preview still opens a Save As dialog')
cc=text('server/src/main/java/org/example/server/customer360/Customer360Controller.java')
need('/{id}/direct-sales' in cc,'Customer360 direct-sales server endpoint missing')
need('directSales(int customerId)' in svc and 'FROM sales_header sh' in svc,'Customer360 direct-sales server query missing')
# Workflow records carry a durable party/customer id, with a safe legacy-name fallback only for older rows.
need((r/'server/src/main/resources/db/migration/V9_0_56_3__workflow_party_link.sql').exists(),'workflow party-link migration missing')
need('V9_0_56_3__workflow_party_link' in runner,'workflow party-link migration not registered')
need('requireColumn("workflow_document", "party_id")' in runner,'workflow party-id startup schema guard missing')
need('party_id=?' in svc and 'party_id IS NULL' in svc,'Customer360 workflow linkage is not id-first with legacy fallback')
wdto=text('server/src/main/java/org/example/server/workflow/WorkflowDtos.java')
wapi=text('desktop/src/main/java/org/example/api/workflow/WorkflowApiClient.java')
went=text('server/src/main/java/org/example/server/persistence/entity/WorkflowDocumentEntity.java')
need('Integer partyId' in wdto and 'Integer partyId' in wapi and 'name="party_id"' in went,'workflow party id is not carried end-to-end')
need('resolvePartyId' in w,'workflow save does not resolve durable party linkage')
# KPI layout uses the established pre-profile centralized balancing authority; no screen-specific profile sizing is required.
kpi=text('desktop/src/main/java/org/example/util/ResponsiveKpiLayoutManager.java')
need('MIN_COMFORTABLE_CARD' in kpi and 'prepareFlexibleCard' in kpi,'established centralized KPI balancing authority missing')
need('erp-kpi-profile-six' not in text('desktop/src/main/resources/fxml/pages/DashboardHome.fxml'),'Dashboard still contains rolled-back KPI profile sizing')
need('erp-kpi-profile-four' not in text('desktop/src/main/resources/fxml/pages/BackupRestore.fxml'),'Backup still contains rolled-back KPI profile sizing')
# Runtime UI diagnostics must use the existing desktop.log/diagnostic package path and be toggleable in Settings.
need((r/'desktop/src/main/java/org/example/util/UiDiagnostics.java').exists(),'central UI diagnostics service missing')
uid=text('desktop/src/main/java/org/example/util/UiDiagnostics.java')
for token in ['UI_SCREEN_AUDIT','UI_CONTRACT_ISSUE','UI_KPI_LAYOUT','TABLE_WITHOUT_DYNAMIC_LAYOUT']:
    need(token in uid,f'UI diagnostics missing {token}')
nav=text('desktop/src/main/java/org/example/navigation/NavigationManager.java')
need('UiDiagnostics.audit' in nav,'rendered page UI audit hook missing')
need('UiDiagnostics.audit' in dialog,'owned dialog UI audit hook missing')
security_panel=text('desktop/src/main/resources/fxml/pages/settings/SecuritySettingsPanel.fxml')
need('fx:id="chkUiDiagnostics"' in security_panel and 'Enable UI Diagnostics' in security_panel,'Settings UI Diagnostics toggle missing')
need('UiDiagnostics.setEnabled' in settings,'Settings does not persist UI Diagnostics toggle')
# Unknown labels/actions must no longer receive misleading document/report fallback semantics.
need('if (semantic == null) return;' in ic,'IconFactory unmapped semantic no-op missing')
need('semantic = "report"' not in ic,'generic KPI report fallback remains')


# Final consolidated UI/workflow correction: business-type-aware editors, master selectors, standard actions,
# Sale stock visibility, bounded KPI geometry and stronger runtime UI diagnostics.
workflow_editor = text('desktop/src/main/java/org/example/controller/WorkflowDocumentController.java')
workflow_shell = text('desktop/src/main/resources/fxml/pages/WorkflowEditor.fxml')
need('WorkflowEditorShellController' in workflow_shell and 'fx:id="formGrid"' in workflow_shell,'shared FXML-owned workflow editor shell missing')
for token in ['partySelector("CUSTOMER")' if False else 'partySelector(partyType)', 'workflowSelector("PROJECT")', 'parentSelector(type)', 'MenuButton("Actions")', 'UI_WORKFLOW_FORM_CONTRACT']:
    need(token in workflow_editor,f'workflow editor contract missing: {token}')
need('Search and select ' in workflow_editor and 'Party / Customer / Supplier' not in workflow_shell,'workflow party entry is not a master selector')
need('Project / Job must be selected from existing Projects' in workflow_editor,'workflow project selector validation missing')
need('Purchase Order must be selected from existing Project Execution records' in workflow_editor or 'Purchase Order"' in workflow_editor,'GRN parent selector validation missing')
need('Sales Order"' in workflow_editor and 'projectDerived=true' in workflow_editor,'Dispatch derived-parent workflow contract missing')
need('Project ID is generated only when Save succeeds' in workflow_editor,'Project generated-ID UX missing')

sale_fxml=text('desktop/src/main/resources/fxml/pages/Sale.fxml')
sale_ctl=text('desktop/src/main/java/org/example/controller/SalesController.java')
for token in ['fx:id="stockPositionBar"','fx:id="lblStockOnHand"','fx:id="lblStockReserved"','fx:id="lblStockAvailable"','fx:id="lblStockAfterSale"']:
    need(token in sale_fxml,f'Sale stock-position UI missing: {token}')
need('openingStock - reservedStock' in sale_ctl or 'getOpeningStock()-' in sale_ctl or 'getReservedStock()' in sale_ctl,'Sale free-to-promise display/check missing reserved stock')
need('stock-shortage' in sale_ctl and 'stock-positive' in sale_ctl,'Sale stock semantic value states missing')

safe_rb=text('desktop/src/main/resources/fxml/pages/SafeRollback.fxml')
backup=text('desktop/src/main/resources/fxml/pages/BackupRestore.fxml')
need('erp-kpi-profile-four' not in safe_rb,'Safe Rollback still contains rolled-back KPI profile sizing')
need('erp-kpi-profile-four' not in backup,'Backup & Restore still contains rolled-back KPI profile sizing')
for token in ['SEMANTIC_COLLISION','actionMenus=']:
    need(token in uid,f'strengthened UI diagnostics missing: {token}')
need('UNCLASSIFIED_KPI_LAYOUT' not in uid and 'KPI_GEOMETRY_UNRESOLVED' not in uid,'rolled-back KPI geometry diagnostics are still active')

registry=text('desktop/src/main/resources/ui/semantic-registry.properties')
for token in ['field.number=number','field.parent.reference=reference','field.customer.po.no=purchase-order','field.schedule.name=document','field.run.day=calendar','field.output=export','field.recipients=email']:
    need(token in registry,f'semantic registry correction missing: {token}')
need('value.contains("report information")' in ic and 'value.contains("open report")' in ic and 'value.contains("saved report")' in ic,'reporting action semantic distinctions missing')
need('fx:id="btnTestEmail"' in text('desktop/src/main/resources/fxml/pages/Settings.fxml') and 'fx:id="btnSaveSettings"' in text('desktop/src/main/resources/fxml/pages/Settings.fxml'),'Settings explicit action ids missing')
need('UiActionIcons.apply(btnTestEmail' in settings and 'UiActionIcons.apply(btnSaveSettings' in settings,'Settings explicit action semantics missing')

support=text('server/src/main/java/org/example/server/support/SupportService.java')
need('Attachment exists but could not be read from managed storage' in support,'managed attachment read failures are still reduced to a generic server 500')
need('DSE ERP 9.0.57 - PRODUCTION WINDOWS BUILD' in text('Build Production Windows.bat'),'production build banner identity is not 9.0.57')
need('DSE ERP 9.0.57 - RELEASE GATE VERIFICATION' in text('Run Release Gates.bat'),'release-gate banner identity is not 9.0.57')
need('scripts/audit-release-gates.py' in text('.github/workflows/ci.yml') and 'scripts/audit-release-gates.py' in text('.github/workflows/release.yml'),'CI/release are not wired to the single current aggregate gate')

print(f'RUNTIME_WORKFLOW_UI_9_0_57_OK fxml={len(fxmls)} css=2 markdown=0 menu=DataImport->ProjectExecution numbering=save-only registration=qr')
