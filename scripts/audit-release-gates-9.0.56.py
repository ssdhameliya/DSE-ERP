from pathlib import Path
import subprocess,sys
r=Path(__file__).resolve().parents[1]
for script in ['audit-9.0.56-runtime-workflow-ui-contract.py','audit-release-gates-9.0.53.py']:
    print('\n=== '+script+' ===')
    subprocess.run([sys.executable,str(r/'scripts'/script)],cwd=r,check=True)
print('\nRELEASE_GATES_9_0_56_OK')
