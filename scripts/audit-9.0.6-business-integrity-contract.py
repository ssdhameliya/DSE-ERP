from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def t(p): return (ROOT/p).read_text(encoding='utf-8')
def req(c,m):
    if not c: raise SystemExit('FAIL: '+m)
ops=t('server/src/main/java/org/example/server/operations/BusinessOperationsService.java')
ret=t('server/src/main/java/org/example/server/returns/ReturnService.java')
quote=t('server/src/main/java/org/example/server/quotation/QuotationService.java')
auth=t('server/src/main/java/org/example/server/auth/AuthService.java')
smtp=t('server/src/main/java/org/example/server/auth/SmtpMailService.java')
backup=t('server/src/main/java/org/example/server/authority/ServerBackupService.java')
recon=t('server/src/main/java/org/example/server/reconciliation/BankReconciliationService.java')
pr=t('server/src/main/java/org/example/server/recon/PurchaseReconService.java')
calc=t('shared/src/main/java/org/example/shared/DocumentCalculationEngine.java')
ins=t('server/src/main/java/org/example/server/insights/InsightsService.java')
imp=t('desktop/src/main/java/org/example/service/ImportService.java')
master=t('server/src/main/java/org/example/server/master/MasterDataService.java')
mapper=t('desktop/src/main/java/org/example/invoice/mapper/SalesToTaxInvoiceMapper.java')
tax=t('desktop/src/main/java/org/example/invoice/calculation/InvoiceTaxCalculator.java')
words=t('desktop/src/main/java/org/example/invoice/calculation/AmountInWordsConverter.java')
pay=t('server/src/main/java/org/example/server/payment/PaymentIntegrityService.java') if (ROOT/'server/src/main/java/org/example/server/payment/PaymentIntegrityService.java').exists() else ''
mig=t('server/src/main/resources/db/migration/V9_0_6__business_integrity_hardening.sql')
runner=t('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java')
runtime=t('shared/src/main/java/org/example/shared/RuntimeContract.java')
props=t('server/src/main/resources/application.properties')
# Server-owned financial/lifecycle boundaries
req('h.setPaidAmount(0d);h.setPaymentStatus("PENDING")' in ops,'new Sales must not trust client payment state')
req('must contain at least one item line' in ops,'Sales/Purchase must reject zero lines')
req('discount for "+d.itemCode()+" must be between 0 and 100' in ops and 'GST for "+d.itemCode()+" must be between 0 and 100' in ops,'document percentages must be rejected, not clamped')
req('Unsupported tax mode' in calc,'unknown tax mode must be rejected')
req('Return party must match the original invoice party' in ret and 'source_line_id' in ret,'Returns must bind to original party and exact source line')
req('original.lineTotal()/original.quantity()' in ret,'Return amount must be derived server-side from source invoice lines')
# Quotation/security
for permission in ('QUOTATION.VIEW','QUOTATION.CREATE','QUOTATION.EDIT','QUOTATION.DELETE'):
    req(permission in quote,f'Quotation permission missing: {permission}')
req('QuoteCalculation calc=calculate(d.lines())' in quote and 'DocumentCalculationEngine.line' in quote,'Quotation values must be recalculated server-side')
req('discount_amount' in quote and 'l.discount()' in quote,'Quotation conversion must preserve discount semantics')
req('role.equalsIgnoreCase("USER")' in auth or '"USER".equalsIgnoreCase' in auth,'public registration must be restricted to USER role')
# Secrets/restore
req('SecretValueCodec.encrypt' in smtp and 'SecretValueCodec.decrypt' in smtp,'SMTP password must be encrypted at rest')
req('validateArchiveListing' in backup and 'stageRestore' in backup and 'validate(candidate' in backup,'restore candidate must be validated before staging')
# Bank/tax/finance
req('sum=money(sum+money(a.amount()))' in recon and 'after monetary rounding' in recon,'bank allocation limit must be checked after rounding')
req('exactly one positive debit or credit amount' in recon.lower(),'bank import must reject debit+credit ambiguity')
req('v<0' in pr and 'cannot be negative or invalid' in pr,'Purchase Recon must reject negative tax/financial values')
req('Finance amount must be a finite number greater than zero' in ops,'finance vouchers must reject negative/zero amounts')
# Reporting/inventory
req('payment_date' in ins and 'paymentTotal(' in ins,'payment reports must use payment transaction date')
req('long salesCount=' in ins and 'purchaseCount=' in ins and 'sales/salesCount' in ins,'report KPIs must use full dataset counts')
req('salesReturns' in ins and 'purchaseReturns' in ins and 'returnCogs' in ins,'returns must be netted from reporting/profit')
req("due+\"=CURRENT_DATE\"" in ops or "due+\"=CURRENT_DATE" in ops,'Due Today must exclude overdue balances')
req('openingBalance(' in ins and 'opening_balance' in ins,'AR/AP must include opening balances')
req('inventory_cost_state' in ins and 'unit_cost_snapshot' in ins,'inventory valuation/gross profit must use historical cost state')
req('getReservedStock' in ops and 'Insufficient available stock' in ops,'Sales stock posting must enforce reserved stock')
# Import/master/snapshots/reference
req('header' in imp.lower() and 'inconsistent' in imp.lower(),'import must reject inconsistent invoice headers')
for f in ('purchase price','selling price','minimum stock','reserved stock'):
    req(f in master.lower(),f'Item validation missing: {f}')
req('customer_name_snapshot' in mig and 'item_description_snapshot' in mig and 'item_remarks_snapshot' in mig,'historical invoice snapshots must be persisted')
req('getItemRemarks()' in mapper,'tax invoice mapper must consume immutable item remarks snapshot')
req('reference_counter.next_value+1' in ops and 'Reference sequence exhausted' not in ops and 'seq.length()>width' not in ops,'reference allocation must stay atomic and auto-expand beyond configured minimum padding width')
# Invoice money precision
req('setScale(0' not in tax and 'totalTax - cgst' in tax,'invoice PDF must keep paise and paise-exact CGST/SGST')
req('PAISE' in words and 'setScale(2' in words,'amount-in-words must include paise')
# Database migration
req('ALTER COLUMN amount TYPE NUMERIC(19,2)' in mig,'finance REAL must migrate to NUMERIC(19,2)')
req('inventory_cost_ledger' in mig and 'inventory_cost_state' in mig,'inventory costing ledger/state migration missing')
req('V9_0_6__business_integrity_hardening.sql' in runner,'9.0.6 migration must be registered in runtime runner')
# Already-correct protections stay intact
if pay:
    req('BANK_RECONCILIATION' in pay,'bank-reconciled payment edit protection must remain')
req('rounding_adjustment' in ret,'Return refund balance must retain rounding adjustment handling')
# One exact startup identity
req('APP_VERSION = "9.0.22"' in runtime and 'BUILD_REVISION = "9.0.22"' in runtime,'desktop version/build must both be 9.0.18')
req('dse.app.version=9.0.22' in props and 'dse.build.revision=9.0.22' in props,'server version/build must both be 9.0.18')
print('PASS: DSE ERP 9.0.18 runtime with 9.0.6 business-integrity contract')
