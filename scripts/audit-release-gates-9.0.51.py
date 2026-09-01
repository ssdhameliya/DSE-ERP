#!/usr/bin/env python3
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
checks=[
'audit-9.0.52-ui-stabilization-contract.py',
'audit-9.0.52-ui-behavior-freeze.py',
'audit-9.0.52-phase2-two-theme-contract.py',
'audit-9.0.52-phase3-semantic-ui-contract.py',
'audit-9.0.52-phase4-responsive-kpi-contract.py',
'audit-9.0.52-phase5-dynamic-table-contract.py',
'audit-9.0.49-financial-multi-user-contract.py',
'audit-9.0.50-registration-security-contract.py',
'audit-9.0.52-gate2-gate3-contract.py',
'audit-9.0.52-project-execution-contract.py',
'audit-9.0.52-final-contract.py',
]
failed=[]
for name in checks:
 print('\n=== '+name+' ===')
 r=subprocess.run([sys.executable,str(ROOT/'scripts'/name)],cwd=ROOT)
 if r.returncode: failed.append(name)
if failed:
 print('\nRELEASE_GATES_9_0_51_FAIL')
 for x in failed: print(' -',x)
 sys.exit(1)
print('\nRELEASE_GATES_9_0_51_OK')
