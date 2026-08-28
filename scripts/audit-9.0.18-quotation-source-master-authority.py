from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding="utf-8",errors="replace")
def req(ok,msg):
    if not ok:
        print("FAIL:",msg);sys.exit(1)

qsvc=text('server/src/main/java/org/example/server/quotation/QuotationService.java')
qedit=text('desktop/src/main/java/org/example/controller/QuotationEditorController.java')
master=text('server/src/main/java/org/example/server/master/MasterDataService.java')
imp=text('desktop/src/main/java/org/example/service/ImportService.java')
runner=text('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java')
runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
req('masterData.valuesByCategoryCode(QUOTATION_SOURCE_CODE)' in qsvc,'Quotation Source must remain MasterDataService-backed')
req('return c == null ? List.of() : values(c.getCategoryName());' in master,'category-code Master lookup must use generic resolver')
req('canonicalLookupType' in imp,'Master import canonical lookup-type repair must remain')
req('savedSource=safe(quote.source()).trim()' in qedit,'historical saved Source must remain visible during edit')
req('V9_0_18__quotation_source_master_authority' in runner,'v9.0.18 compatibility migration must remain registered')
req('APP_VERSION = "9.0.32"' in runtime,'current runtime identity must be 9.0.32')
print('PASS: v9.0.18 Quotation Source authority protections retained under v9.0.32 generic Master architecture')
