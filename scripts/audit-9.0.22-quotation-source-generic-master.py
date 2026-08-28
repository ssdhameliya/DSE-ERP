#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding='utf-8',errors='replace')
def req(ok,msg):
    if not ok:
        print('FAIL:',msg);sys.exit(1)
master=text('server/src/main/java/org/example/server/master/MasterDataService.java')
qsvc=text('server/src/main/java/org/example/server/quotation/QuotationService.java')
qctrl=text('server/src/main/java/org/example/server/quotation/QuotationController.java')
qapi=text('desktop/src/main/java/org/example/api/quotation/QuotationApiClient.java')
qedit=text('desktop/src/main/java/org/example/controller/QuotationEditorController.java')
qreg=text('desktop/src/main/java/org/example/controller/QuotationController.java')
lookupsvc=text('desktop/src/main/java/org/example/service/LookupService.java')
purchase=text('desktop/src/main/java/org/example/controller/PurchaseController.java')
runner=text('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java')
mig=text('server/src/main/resources/db/migration/V9_0_22__quotation_source_generic_master.sql')
test=text('server/src/test/java/org/example/server/master/MasterDataQuotationSourceTest.java')
runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
props=text('server/src/main/resources/application.properties')
pom=text('pom.xml')
app=text('desktop/src/main/resources/app-version.properties')
update=text('desktop/src/main/java/org/example/update/UpdateService.java')

# Generic Master authority is the same implementation used by all category-code lookups.
req('public List<String> valuesByCategoryCode(String code)' in master and
    'MasterCategoryEntity c = resolveCategoryByCode(code);' in master and
    'return c == null ? List.of() : values(c.getCategoryName());' in master,
    'MasterDataService category-code values must be generic')
req('quotationSourceLookups' not in master and 'quotationSourceValues' not in master and
    'sourceLike(' not in master and 'ensureQuotationSourceDefaults' not in master,
    'Quotation-specific Master fallback/seeding runtime logic must be removed')
req('if ("QUOTATIONSOURCE".equals' not in master,
    'generic category resolver must not special-case Quotation Source')

# Desktop uses the same LookupService route as other Master-backed controls such as Purchase Transporter.
req('lookupService.getValuesByCategoryCode("QUOTATION_SOURCE")' in qedit,
    'Quotation editor must use generic LookupService category-code lookup')
req('lookupService.getValuesByCategoryCode("QUOTATION_SOURCE")' in qreg,
    'Quotation register Source filter must use generic LookupService category-code lookup')
req('api.sources()' not in qedit and 'quotationApi.sources()' not in qreg,
    'desktop Quotation screens must not use the quotation-specific Source route')
req('lookupService.getValuesByCategoryCode("TRANSPORTER")' in purchase,
    'reference working Transporter Master pattern must remain present')
req('getValuesByCategoryCode(String c)' in lookupsvc,
    'shared desktop LookupService category-code method must remain the common path')

# Server/mobile compatibility: endpoint remains but delegates to the same generic Master authority.
req('masterData.valuesByCategoryCode(QUOTATION_SOURCE_CODE)' in qsvc,
    'Quotation server endpoint/validation must delegate generic MasterDataService')
req('@GetMapping("/sources")public List<String> sources(){return s.sourceChoices();}' in qctrl,
    'existing quotation Source REST route must remain backward-compatible')
req('return get("/api/quotations/sources",new TypeReference<List<String>>(){});' in qapi,
    'QuotationApiClient compatibility method must remain for iOS/Android/older desktop clients')

# Migration is compatibility-only: canonicalize/copy once, then runtime stays generic.
req('V9_0_22__quotation_source_generic_master' in runner,
    'v9.0.28 generic-master compatibility migration must be registered')
req("'QUOTATION_SOURCE','QUOTATION SOURCE'" in mig and 'QSRC_MIG_' in mig,
    'migration must assure canonical category and bridge historical Source values')
req('quotationSourceUsesSameGenericCategoryCodeLookupAsTransporter' in test and
    'quotationSourceDoesNotFallBackToDifferentSourceCategoriesAtRuntime' in test,
    'regression tests must enforce the generic-only runtime contract')

# Release identity.
req('APP_VERSION = "9.0.28"' in runtime and 'BUILD_REVISION = "9.0.28"' in runtime,
    'runtime identity must be 9.0.28')
req('dse.app.version=9.0.28' in props and 'dse.build.revision=9.0.28' in props,
    'server identity must be 9.0.28')
req('<version>9.0.28</version>' in pom and '<dse.phase>9.0.28</dse.phase>' in pom,
    'Maven identity must be 9.0.28')
req('version=9.0.28' in app and 'DEFAULT_VERSION="9.0.28"' in update,
    'desktop/update identity must be 9.0.28')
print('PASS: DSE ERP 9.0.28 Quotation Source generic Master contract')
