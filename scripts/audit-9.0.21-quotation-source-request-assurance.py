from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding="utf-8",errors="replace")
def req(ok,msg):
    if not ok:
        print("FAIL:",msg);sys.exit(1)

master=text('server/src/main/java/org/example/server/master/MasterDataService.java')
qsvc=text('server/src/main/java/org/example/server/quotation/QuotationService.java')
editor=text('desktop/src/main/java/org/example/controller/QuotationEditorController.java')
insights=text('server/src/main/java/org/example/server/insights/InsightsService.java')
runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
req('cmbSource.setOnShowing(event -> refreshQuotationSources());' in editor,'Source dropdown must still refresh when opened')
req('lookupService.getValuesByCategoryCode("QUOTATION_SOURCE")' in editor,'dropdown refresh must use generic Master LookupService')
req('ensureQuotationSourceDefaults' not in master and 'Quotation Source resolved {}' not in master,'request-time Quotation-specific assurance must be removed')
req('masterData.valuesByCategoryCode(QUOTATION_SOURCE_CODE)' in qsvc,'backward-compatible server endpoint must use generic Master service')
req('SUM(rr.amount),0) FROM return_refund rr JOIN return_register' in insights,'v9.0.21 Dashboard refund SQL qualification fix must remain')
req('APP_VERSION = "9.0.22"' in runtime,'current runtime identity must be 9.0.22')
print('PASS: v9.0.21 refresh/Dashboard protections retained under v9.0.22 generic Master architecture')
