from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding="utf-8",errors="replace")
def req(ok,msg):
    if not ok:
        print("FAIL:",msg);sys.exit(1)

master=text('server/src/main/java/org/example/server/master/MasterDataService.java')
runner=text('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java')
mig=text('server/src/main/resources/db/migration/V9_0_19__quotation_source_master_resolution.sql')
newmig=text('server/src/main/resources/db/migration/V9_0_22__quotation_source_generic_master.sql')
runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
req('V9_0_19__quotation_source_master_resolution' in runner,'v9.0.19 compatibility migration must remain registered')
req('SOURCE' in mig and 'QUOTATION_SOURCE' in mig,'historical Source compatibility data repair must remain')
req('QSRC_MIG_' in newmig,'v9.0.22 must bridge historical Source values to the canonical Master once')
req('quotationSourceLookups' not in master and 'sourceLike(' not in master,'runtime must no longer keep alias-specific Quotation logic')
req('APP_VERSION = "9.0.22"' in runtime,'current runtime identity must be 9.0.22')
print('PASS: v9.0.19 data compatibility retained; runtime superseded by v9.0.22 generic Master path')
