#!/usr/bin/env python3
from release_version import VERSION
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]

def text(p):
    return (ROOT / p).read_text(encoding='utf-8', errors='replace')

def req(ok, msg):
    if not ok:
        print('FAIL:', msg)
        sys.exit(1)
qsvc = text('server/src/main/java/org/example/server/quotation/QuotationService.java')
qctl = text('desktop/src/main/java/org/example/controller/QuotationController.java')
sales = text('desktop/src/main/java/org/example/controller/SalesListController.java')
fxml = text('desktop/src/main/resources/fxml/pages/Quotations.fxml')
runner = text('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java')
mig = text('server/src/main/resources/db/migration/V9_0_16__quotation_source_navigation_hardening.sql')
runtime = text('shared/src/main/java/org/example/shared/RuntimeContract.java')
props = text('server/src/main/resources/application.properties')
bootstrap = text('desktop/src/main/java/org/example/api/runtime/RuntimeBootstrapper.java')
pom = text('pom.xml')
app = text('desktop/src/main/resources/app-version.properties')
update = text('desktop/src/main/java/org/example/update/UpdateService.java')
pdf_audit = text('scripts/audit-pdf-studio-contract.py')
manifest = text('runtime/runtime-manifest.properties')
req('masterData.valuesByCategoryCode(QUOTATION_SOURCE_CODE)' in qsvc and 'activeQuotationSources()' in qsvc, 'Quotation Source must use canonical Master category-code authority')
req('return activeQuotationSources();' in qsvc and 'canonical=activeQuotationSources()' in qsvc, 'Quotation dropdown and save validation must use one canonical source authority')
req('V9_0_16__quotation_source_navigation_hardening' in runner, 'historical quotation-source migration must be registered')
req('QUOTATIONSOURCE' in mig and "category_code='QUOTATION_SOURCE'" in mig, 'migration must canonicalize legacy Quotation Source master categories')
req('fx:id="btnOpenConvertedSale"' in fxml and 'GridPane.rowIndex="2" GridPane.columnIndex="1"' in fxml, 'Open Sale must sit directly below Convert to Sale')
req('LinkedRecordContext.peek()' in sales and 'linkedRecordReloadInProgress' in sales, 'Sales deep link must survive a targeted reload')
req('private boolean linkedRecordReloadInProgress;' in sales, 'Sales deep-link reload guard must be declared as a controller field')
req('asException(' not in sales, 'Sales deep-link async failure path must use the existing Throwable error handler directly')
req('DeepLinkSupport.highlight(tableSales,sale)' in sales, 'linked Sale row must receive exact row highlight')
req('txtInvoice.setText(found.getInvoiceNo())' in sales and 'reloadPage(true);' in sales and 'batchFilterUpdate' in sales, 'off-page linked Sale must batch filter changes and reload the register once around the exact invoice')
req('LinkedRecordContext.open("SALE",null,q.converted.get()' in qctl, 'Quotation must publish converted Sale reference into linked-record context')
req("req(runtime_manifest_path.exists(), 'runtime identity manifest must be tracked in every release source handoff')" in pdf_audit, 'PDF Studio CI contract must require the tracked runtime identity manifest')
req('".dse-erp", "dev-server-cache"' in bootstrap and 'Files.copy(built, staging, StandardCopyOption.REPLACE_EXISTING)' in bootstrap, 'IntelliJ backend must execute from an external project-specific cache, never directly from Maven target')
req('cleanupOrphanDevelopmentServers(root)' in bootstrap and 'hasLiveOwningParent(handle)' in bootstrap, 'development bootstrap must clean only orphaned project-owned backend processes')
for prop in ('spring.datasource.hikari.maximum-pool-size=10', 'spring.datasource.hikari.minimum-idle=2', 'spring.datasource.hikari.validation-timeout=3000', 'spring.datasource.hikari.max-lifetime=900000', 'spring.datasource.hikari.keepalive-time=120000', 'spring.datasource.hikari.data-source-properties.tcpKeepAlive=true'):
    req(prop in props, f'missing PostgreSQL/Hikari stability setting: {prop}')
print(f'QUOTATION_SOURCE_SALE_LINK_CONTRACT_OK version={VERSION}')
