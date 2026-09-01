#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FAIL=[]
def text(rel):
    p=ROOT/rel
    if not p.exists(): FAIL.append(f"missing {rel}"); return ""
    return p.read_text(errors="ignore")
def need(rel,*tokens):
    s=text(rel)
    for token in tokens:
        if token not in s: FAIL.append(f"{rel}: missing {token!r}")

def forbid(rel,*tokens):
    s=text(rel)
    for token in tokens:
        if token in s: FAIL.append(f"{rel}: forbidden {token!r}")

need('pom.xml','<version>9.0.47</version>','<maven.compiler.release>25</maven.compiler.release>')
need('mvnw','3.9.11')
need('mvnw.cmd','3.9.11')
need('.mvn/wrapper/maven-wrapper.properties','apache-maven-3.9.11-bin.zip')
need('.github/workflows/ci.yml','./mvnw','postgres-integration','DSE_IT_DB_URL','PostgresWorkflowIntegrationTest')
need('.github/workflows/release.yml','./mvnw','audit-9.0.47-engineering-hardening.py')
need('.github/workflows/ci.yml','audit-9.0.47-ui-behavior-freeze.py','audit-9.0.47-phase2-two-theme-contract.py','audit-9.0.47-phase3-semantic-ui-contract.py','audit-9.0.47-phase4-responsive-kpi-contract.py','audit-9.0.47-phase5-dynamic-table-contract.py','audit-9.0.47-phase6-controller-cleanup-contract.py','audit-9.0.47-phase7-production-clean-contract.py','audit-9.0.47-phase8-ui-stabilization-contract.py')
need('.github/workflows/release.yml','audit-9.0.47-ui-behavior-freeze.py','audit-9.0.47-phase2-two-theme-contract.py','audit-9.0.47-phase3-semantic-ui-contract.py','audit-9.0.47-phase4-responsive-kpi-contract.py','audit-9.0.47-phase5-dynamic-table-contract.py','audit-9.0.47-phase6-controller-cleanup-contract.py','audit-9.0.47-phase7-production-clean-contract.py','audit-9.0.47-phase8-ui-stabilization-contract.py')
need('.github/workflows/ci.yml','audit-9.0.47-ui-enhancement-contract.py')
need('.github/workflows/release.yml','audit-9.0.47-ui-enhancement-contract.py')
need('scripts/audit-9.0.47-ui-behavior-freeze.py','UI_BEHAVIOR_FREEZE_OK','protected_navigation_hashes')
need('scripts/audit-9.0.47-phase2-two-theme-contract.py','PHASE2_TWO_THEME_OK','EXPECTED_CSS','scene.getStylesheets().setAll(')
need('scripts/audit-9.0.47-phase3-semantic-ui-contract.py','PHASE3_SEMANTIC_UI_OK','UiSemanticRegistry.headerSemantic')
need('scripts/audit-9.0.47-phase4-responsive-kpi-contract.py','PHASE4_RESPONSIVE_KPI_OK','ResponsiveKpiLayoutManager','responsiveColumnCount')
need('scripts/audit-9.0.47-phase5-dynamic-table-contract.py','PHASE5_DYNAMIC_TABLE_OK','DynamicTableLayoutManager','width_attrs=0')
need('scripts/audit-9.0.47-phase6-controller-cleanup-contract.py','PHASE6_CONTROLLER_CLEANUP_OK','SettingsAssetService','static_fxml_header_calls=0')
need('scripts/audit-9.0.47-phase7-production-clean-contract.py','PHASE7_PRODUCTION_CLEAN_OK','EXPECTED_CSS','junk=0 trailing_whitespace=0')
need('scripts/audit-9.0.47-phase8-ui-stabilization-contract.py','PHASE8_UI_STABILIZATION_OK','renderedCellControlWidth','yellow_button_surfaces=0')
need('scripts/audit-9.0.47-ui-enhancement-contract.py','UI_ENHANCEMENT_9_0_47_OK','reporting=table-first','action=icon+text','bank_kpi=8x1')
need('scripts/audit-9.0.47-final-contract.py','FINAL_9_0_47_OK','schedule_pdf_xlsx=both','full_runtime=yes')
need('.github/workflows/ci.yml','audit-9.0.47-financial-multi-user-contract.py')
need('.github/workflows/release.yml','audit-9.0.47-financial-multi-user-contract.py')
need('scripts/audit-9.0.47-financial-multi-user-contract.py','FINANCIAL_MULTI_USER_9_0_47_OK','payments=roundoff-safe','quotation_lock=yes','permissions_lock=yes')
need('desktop/src/main/java/org/example/util/DynamicTableLayoutManager.java','UNCONSTRAINED_RESIZE_POLICY','getVisibleLeafColumns()','sampledContentWidth','column.setPrefWidth(width)')
need('desktop/src/main/java/org/example/util/ResponsiveKpiLayoutManager.java','erp-kpi-section','responsiveColumnCount','pane instanceof FlowPane flow','grid.getColumnConstraints().clear()')
need('scripts/ui-behavior-freeze-9.0.47.json','DSE ERP 9.0.47 approved UI/behavior freeze')
need('server/src/test/java/org/example/server/integration/PostgresWorkflowIntegrationTest.java','RETURN APPROVAL PENDING','RETURN PENDING','RETURN PARTIAL','RETURN PAID','assertThrows')
need('desktop/src/main/java/org/example/util/DesktopLog.java','desktop.log','BUNDLE_EXPORTED' if False else 'Structured desktop log')
need('desktop/src/main/java/org/example/service/DiagnosticBundleService.java','config-sanitized.properties','<redacted>','database and business documents' if False else 'Business documents/database contents are never included')
need('desktop/src/main/resources/fxml/settings/WorkspaceSettingsPanel.fxml' if (ROOT/'desktop/src/main/resources/fxml/settings/WorkspaceSettingsPanel.fxml').exists() else 'desktop/src/main/resources/fxml/pages/settings/WorkspaceSettingsPanel.fxml','Export Diagnostic Package','#exportDiagnostics')
need('desktop/src/main/java/org/example/service/ReferenceDataCache.java','TTL_NANOS','invalidateAll')
need('desktop/src/main/java/org/example/service/SessionService.java','ReferenceDataCache.invalidateAll()')
need('desktop/src/main/java/org/example/service/PartyService.java','ReferenceDataCache.getList("PARTY:')
need('desktop/src/main/java/org/example/service/LookupService.java','ReferenceDataCache.getList("LOOKUP:')
need('desktop/src/main/java/org/example/service/ItemService.java','ReferenceDataCache.getList("ITEM:ALL"')
forbid('desktop/src/main/java/org/example/controller/SalesController.java','private void saveDraft()','setDocumentStatus("DRAFT")','setRemarks("DRAFT\\n"')
forbid('desktop/src/main/java/org/example/controller/PurchaseController.java','private void saveDraft()')
need('server/src/main/java/org/example/server/operations/BusinessOperationsService.java','String requested="APPROVED";','audit.log("SALE",h.getId(),"CREATED"','audit.log("PURCHASE",h.getId(),"CREATED"')
forbid('server/src/main/java/org/example/server/operations/BusinessOperationsService.java','DRAFT_CREATED','DRAFT_UPDATED','DRAFT_PROMOTED','h.setApprovalStatus("DRAFT")')
need('server/src/main/java/org/example/server/support/SupportController.java','@GetMapping("/activity")')
need('server/src/main/java/org/example/server/support/SupportService.java','activityRows(String type,int id)','ORDER BY id DESC LIMIT 200')
need('desktop/src/main/java/org/example/util/ActivityTimelineDialog.java','Activity Timeline','api.activity(entityType, entityId)')
need('desktop/src/main/java/org/example/controller/SalesListController.java','Activity Timeline','RegisterColumnPreferences.install(tableSales','loadSavedViews()')
need('desktop/src/main/java/org/example/controller/PurchaseListController.java','Activity Timeline','RegisterColumnPreferences.install(tablePurchase','PURCHASE_REGISTER','saveCurrentView')
need('desktop/src/main/java/org/example/controller/QuotationController.java','Activity Timeline','RegisterColumnPreferences.install(table','QUOTATION_REGISTER')
need('desktop/src/main/java/org/example/util/RegisterUiSupport.java','configureHeaderSearch','setCurrentYearRange')
need('desktop/src/main/java/org/example/util/RegisterColumnPreferences.java','setTableMenuButtonVisible(true)','visibleProperty()','prefs.remove(key + ".width")','order')
need('desktop/src/main/java/org/example/update/ReleaseHighlights.java','if ("9.0.47".equals(version))','Engineering' if False else 'Maven Wrapper')

if FAIL:
    print('FAIL: 9.0.47 engineering hardening audit')
    for f in FAIL: print(' -',f)
    sys.exit(1)
print('PASS: 9.0.47 engineering hardening audit')
