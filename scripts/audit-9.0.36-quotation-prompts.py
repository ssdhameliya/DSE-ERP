from pathlib import Path
p=Path('desktop/src/main/java/org/example/controller/QuotationEditorController.java')
s=p.read_text()
checks={
 'Customer initial prompt is Select Customer':'cmbCustomer.setPromptText("Select Customer");',
 'Source initial prompt is Select Source':'cmbSource.setPromptText("Select Source");',
 'No Loading customers prompt':'Loading customers...' not in s,
 'No Loading sources prompt':'Loading sources...' not in s,
 'Customer uses Sale bootstrap API':'masterApi.salesEntryBootstrap().customers()',
 'Customer local fallback':'partyService.search("CUSTOMER",query,30)',
 'Source API lookup':'masterApi.lookupValuesByCategoryCode("QUOTATION_SOURCE")',
}
bad=[]
for name, ok in checks.items():
    if ok is not True and ok not in s: bad.append(name)
    elif ok is True and not True: bad.append(name)
print('PASS: Quotation prompt/loading regression audit' if not bad else 'FAIL: '+', '.join(bad))
if bad: raise SystemExit(1)
