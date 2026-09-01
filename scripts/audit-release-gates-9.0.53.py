#!/usr/bin/env python3
from pathlib import Path
import subprocess,sys
R=Path(__file__).resolve().parents[1]
checks=['audit-9.0.53-session-focus-stock-contract.py','audit-9.0.53-customer-360-contract.py','audit-9.0.49-financial-multi-user-contract.py','audit-9.0.50-registration-security-contract.py','audit-9.0.53-gate2-gate3-contract.py','audit-9.0.53-project-execution-contract.py']
failed=[]
for x in checks:
 print('\n=== '+x+' ===');r=subprocess.run([sys.executable,str(R/'scripts'/x)],cwd=R)
 if r.returncode: failed.append(x)
if failed:
 print('\nRELEASE_GATES_9_0_53_FAIL');[print(' -',x) for x in failed];sys.exit(1)
print('\nRELEASE_GATES_9_0_53_OK')
