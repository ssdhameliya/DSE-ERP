#!/usr/bin/env python3
"""DSE ERP release-gate audit: finance, multi-user, security, and centralized UI contracts."""
from pathlib import Path
import subprocess, sys

ROOT = Path(__file__).resolve().parents[1]
checks = [
    "audit-9.0.49-ui-behavior-freeze.py",
    "audit-9.0.49-phase2-two-theme-contract.py",
    "audit-9.0.49-phase3-semantic-ui-contract.py",
    "audit-9.0.49-phase5-dynamic-table-contract.py",
    "audit-9.0.49-financial-multi-user-contract.py",
    "audit-9.0.49-final-contract.py",
    "audit-9.0.49-gate2-gate3-contract.py",
    "audit-9.0.49-project-execution-contract.py",
]
failed=[]
for name in checks:
    print(f"\n=== {name} ===")
    result=subprocess.run([sys.executable, str(ROOT/'scripts'/name)], cwd=ROOT)
    if result.returncode: failed.append(name)

calc=(ROOT/'shared/src/main/java/org/example/shared/DocumentCalculationEngine.java').read_text()
ops=(ROOT/'server/src/main/java/org/example/server/operations/BusinessOperationsService.java').read_text()
auth=(ROOT/'server/src/main/java/org/example/server/auth/AuthService.java').read_text()
pay=(ROOT/'server/src/main/java/org/example/server/support/PaymentIntegrityService.java').read_text()
css=list((ROOT/'desktop/src/main/resources/css').glob('*.css'))
extra=[]
if 'BigDecimal qty = quantityDecimal' not in calc: extra.append('authoritative calculation is not BigDecimal internally')
if 'requestedTotals=documentTotals' not in ops: extra.append('sale/purchase update guard still trusts client totals')
if 'stored.equals(raw)' in auth: extra.append('plaintext password comparison remains enabled')
if 'FOR UPDATE' not in pay or 'effectivePaid' not in pay: extra.append('payment serialization/authoritative paid guard missing')
if sorted(p.name for p in css) != ['dark-theme.css','light-theme.css']: extra.append('central two-theme CSS contract changed')
if extra:
    print('\n=== RELEASE-GATE EXTRA CHECKS ===')
    for item in extra: print('FAIL -',item)
    failed.extend(extra)
else:
    print('\nRELEASE_GATE_EXTRA_OK bigdecimal=yes server_totals=yes plaintext_compare=no payment_lock=yes css=2')

if failed:
    print('\nRELEASE_GATES_FAIL')
    sys.exit(1)
print('\nRELEASE_GATES_OK')
