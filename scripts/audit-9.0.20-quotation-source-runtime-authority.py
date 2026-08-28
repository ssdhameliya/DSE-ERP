from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding="utf-8",errors="replace")
def req(ok,msg):
    if not ok:
        print("FAIL:",msg);sys.exit(1)

master=text('server/src/main/java/org/example/server/master/MasterDataService.java')
qsvc=text('server/src/main/java/org/example/server/quotation/QuotationService.java')
qctrl=text('server/src/main/java/org/example/server/quotation/QuotationController.java')
qapi=text('desktop/src/main/java/org/example/api/quotation/QuotationApiClient.java')
runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
req('masterData.valuesByCategoryCode(QUOTATION_SOURCE_CODE)' in qsvc,'Quotation endpoint must delegate generic Master authority')
req('@GetMapping("/sources")' in qctrl and 'public List<String> sources()' in qapi,'backward-compatible Source REST route must remain')
req('quotationSourceLookups' not in master and 'sourceScore(' not in master and 'sourceLike(' not in master,'source-alias runtime selection must be removed')
req('public List<String> valuesByCategoryCode(String code)' in master,'generic Master category-code resolver must remain')
req('APP_VERSION = "9.0.28"' in runtime,'current runtime identity must be 9.0.28')
print('PASS: v9.0.20 API compatibility retained under v9.0.28 generic Master runtime')
