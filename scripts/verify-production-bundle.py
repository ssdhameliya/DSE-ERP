#!/usr/bin/env python3
from pathlib import Path
import os, sys, platform

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path('target/jpackage-input').resolve()
errors=[]
required=[root/'DSE_Final.jar', root/'server'/'dse-erp-server.jar']
for p in required:
    if not p.is_file(): errors.append(f'missing file: {p}')
pg=root/'runtime'/'postgresql'
win=platform.system().lower().startswith('win')
for name in ['initdb','pg_ctl','psql','createdb']:
    exe=pg/'bin'/(name+'.exe' if win else name)
    if not exe.is_file(): errors.append(f'missing PostgreSQL command: {exe}')
manifest=root/'runtime'/'runtime-manifest.properties'
if not manifest.is_file(): errors.append(f'missing runtime manifest: {manifest}')
if errors:
    print('DSE ERP production bundle verification FAILED', file=sys.stderr)
    for e in errors: print(' - '+e, file=sys.stderr)
    sys.exit(1)
print('DSE ERP production bundle verification PASS')
print(f'Bundle: {root}')
print(f'PostgreSQL: {pg}')
