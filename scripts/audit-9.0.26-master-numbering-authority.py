from pathlib import Path

root = Path(__file__).resolve().parents[1]
def text(path): return (root/path).read_text(encoding='utf-8')
def req(ok,msg):
    if not ok: raise SystemExit('FAIL: '+msg)
    print('PASS:',msg)

svc = text(Path('server/src/main/java/org/example/server/master/MasterDataService.java'))
runner = text(Path('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java'))
mig = text(Path('server/src/main/resources/db/migration/V9_0_26__master_lookup_reference_authority.sql'))
lookup_ui = text(Path('desktop/src/main/java/org/example/controller/LookupDialogController.java'))
lookup_service = text(Path('desktop/src/main/java/org/example/service/LookupService.java'))
runtime = text(Path('shared/src/main/java/org/example/shared/RuntimeContract.java'))
props = text(Path('server/src/main/resources/application.properties'))

req('APP_VERSION = "9.0.28"' in runtime and 'BUILD_REVISION = "9.0.28"' in runtime,
    'runtime identity is 9.0.28')
req('dse.app.version=9.0.28' in props and 'dse.build.revision=9.0.28' in props,
    'server identity is 9.0.28')
req('V9_0_26__master_lookup_reference_authority' in runner,
    '9.0.28 Master numbering migration is registered')
req('LookupNumberingScope scope = lookupNumberingScope(type);' in svc,
    'preview and allocation resolve one shared Master numbering scope')
req('return "REF_LOOKUP_" + categoryCode;' in svc,
    'reference sequence is keyed by immutable category_code')
req('categories.findByCategoryName(requested)' in svc and 'resolveCanonicalCategoryByCode(requested)' in svc,
    'display-name and category-code callers resolve to the same canonical category')
req('default -> derivedMasterPrefix(categoryCode);' in svc and 'default -> "GEN"' not in svc,
    'generic GEN fallback is removed for new Master records')
for code,prefix in {
    'BANK_ACCOUNT':'BNK','TRANSPORTER':'TRN','PAYMENT_TERMS':'PTM','PAYMENT_MODE':'PMD',
    'EXPENSE_CATEGORY':'EXP','CHARGES':'CHG','GST_TYPE':'GTP','QUOTATION_SOURCE':'QTS'
}.items():
    req(f'case "{code}" -> "{prefix}";' in svc, f'{code} has its own Master prefix {prefix}')
    req(f'REF_LOOKUP_{code}' in mig, f'{code} reference format is seeded by migration')
req('lookup.setLookupCode(created ? "" : txtCode.getText().trim());' in lookup_ui,
    'new Master save leaves final code allocation to the server')
req('generateNextCode(String t)' in lookup_service and 'api.nextLookupCode(t)' in lookup_service,
    'Add Master preview requests the authoritative server sequence')
req("'REF_LOOKUP_' || UPPER(TRIM(c.category_code))" in mig and "'REF_LOOKUP_' || TRIM(c.category_name)" in mig,
    'migration preserves old display-name custom formats under stable category-code keys')
req('GEN01' not in svc and 'GENXXX' not in svc,
    'Master service contains no legacy GEN numbering fallback')
print('PASS: DSE ERP 9.0.28 Master numbering authority contract')
