#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
fail=[]
def text(rel):
    p=ROOT/rel
    if not p.exists(): fail.append(f"missing {rel}"); return ""
    return p.read_text(errors='replace')
def req(name, condition):
    if condition: print('PASS:',name)
    else: print('FAIL:',name); fail.append(name)

kpi=text('server/src/main/java/org/example/server/insights/BusinessKpiPolicy.java')
ins=text('server/src/main/java/org/example/server/insights/InsightsService.java')
sup=text('server/src/main/java/org/example/server/support/SupportService.java')
ops=text('server/src/main/java/org/example/server/operations/BusinessOperationsService.java')
quote=text('server/src/main/java/org/example/server/quotation/QuotationService.java')
master=text('server/src/main/java/org/example/server/master/MasterDataService.java')
pline=text('desktop/src/main/java/org/example/model/PurchaseLine.java')
api=text('desktop/src/main/java/org/example/api/operations/OperationsApiClient.java')
tpl=text('desktop/src/main/java/org/example/documentstudio/service/TemplateDataFactory.java')
pdf=text('desktop/src/main/java/org/example/util/ProfessionalDocumentRenderer.java')
ret=text('server/src/main/java/org/example/server/returns/ReturnService.java')
pctrl=text('desktop/src/main/java/org/example/controller/PurchaseController.java')
plist=text('desktop/src/main/java/org/example/controller/PurchaseListController.java')
runner=text('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java')
mig=text('server/src/main/resources/db/migration/V9_0_28__financial_authority_integrity.sql')
version=text('desktop/src/main/resources/app-version.properties')

req('v9.0.28 runtime identity', 'version=9.0.28' in version)
req('only APPROVED Returns are financially active', "status,''))='APPROVED'" in kpi and "NOT IN ('CANCELLED','DELETED')" not in kpi)
req('effective Return payment status is centralized', 'effectivePaymentStatus' in kpi and 'RETURN APPROVAL PENDING' in kpi and 'RETURN PARTIAL' in kpi and 'RETURN PAID' in kpi)
req('effective Return outstanding amount is centralized', 'effectiveOutstanding' in kpi and 'approvedReturnTotal' in kpi and 'settledReturnTotal' in kpi)
req('Reports count only accounting-active Returns', 'BusinessKpiPolicy.returnsActive("r")' in ins)
req('Reports and Dashboard use effective Return balances/status', ins.count('BusinessKpiPolicy.effectiveOutstanding') >= 4 and ins.count('BusinessKpiPolicy.effectivePaymentStatus') >= 2)
req('Global Search uses effective Sale/Purchase state', sup.count('BusinessKpiPolicy.effectivePaymentStatus') >= 2)
req('Purchase shared Notes endpoint writes notes, not remarks', 'String col="notes"' in sup and '"PURCHASE".equals(type)?"remarks"' not in sup)
req('Purchase Notes UI reads/writes dedicated notes field', 'new OwnedTextInputDialog(p.getNotes())' in plist and 'editingPurchase.setNotes(note)' in pctrl)
req('Sales/Purchase new writes enforce party type + active party', 'requireActivePartyReference' in ops and '"CUSTOMER","Customer"' in ops and '"SUPPLIER","Supplier"' in ops)
req('Sales/Purchase new or changed lines reject inactive items', 'validateActiveItems' in ops and 'is inactive. Reactivate it in Item Master' in ops)
req('Quotation enforces active customer/item on create/convert/duplicate', quote.count('requireCustomerReference') >= 4 and quote.count('requireActiveQuotationItems') >= 4)
req('Party delete explicitly blocks referenced accounting parties', 'partyDeleteUsages' in master and 'cannot be deleted because it is used by' in master and 'mark it Inactive' in master)
req('Rejected Sale/Purchase no longer populate approval audit fields', ops.count('setApprovedBy(null)') >= 2 and ops.count('setApprovedAt(null)') >= 2 and 'rejected_by' in ops and 'rejected_at' in ops)
req('9.0.28 rejection audit migration is registered', 'V9_0_28__financial_authority_integrity' in runner and 'rejected_by' in mig and 'rejected_at' in mig)
req('Purchase desktop retains item snapshots', all(x in pline for x in ['itemHsn','itemUnit','itemRemarks']) and all(x in api for x in ['x.getItemHsn()','x.getItemUnit()','x.getItemRemarks()']))
req('Document Studio renders Sales/Purchase snapshots before current master', 'firstNonBlank(line.getItemRemarks()' in tpl and tpl.count('line.getItemHsn()') >= 2 and tpl.count('line.getItemUnit()') >= 2)
req('Fallback PDF renderer uses snapshot HSN/unit', 'snapshotHsn' in pdf and 'snapshotUnit' in pdf and 'l.getItemHsn()' in pdf and 'l.getItemUnit()' in pdf)
req('Return details prefer source transaction snapshots', 'sl.item_description_snapshot' in ret and 'pl.item_description_snapshot' in ret and 'sl.unit_snapshot' in ret and 'pl.unit_snapshot' in ret)

if fail:
    print(f'FAILED: {len(fail)} assertion(s)')
    sys.exit(1)
print('PASS: v9.0.28 financial authority and integrity audit complete')
sys.exit(0)
