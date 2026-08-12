#!/usr/bin/env python3
from pathlib import Path
import os
import platform
import subprocess
import sys

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path('target/jpackage-input').resolve()
errors=[]
required=[root/'DSE_Final.jar', root/'server'/'dse-erp-server.jar']
for p in required:
    if not p.is_file(): errors.append(f'missing file: {p}')
pg=root/'runtime'/'postgresql'
system=platform.system().lower()
win=system.startswith('win')
mac=system == 'darwin'
commands=[]
for name in ['initdb','pg_ctl','psql','createdb']:
    exe=pg/'bin'/(name+'.exe' if win else name)
    commands.append(exe)
    if not exe.is_file(): errors.append(f'missing PostgreSQL command: {exe}')
manifest=root/'runtime'/'runtime-manifest.properties'
if not manifest.is_file(): errors.append(f'missing runtime manifest: {manifest}')

# A file-presence check is not enough for native runtimes. On the target build OS,
# execute every command that DSE ERP needs so missing DLL/dylib dependencies fail CI.
if not errors:
    env=os.environ.copy()
    if win:
        env['PATH']=str(pg/'bin')+os.pathsep+env.get('PATH','')
    elif mac:
        dylib_dirs=[str(pg/'lib'), str(pg/'lib'/'postgresql'), str(pg/'lib'/'dse-deps')]
        env['DYLD_FALLBACK_LIBRARY_PATH']=':'.join(dylib_dirs + [env.get('DYLD_FALLBACK_LIBRARY_PATH','')]).rstrip(':')
    for exe in commands:
        try:
            result=subprocess.run([str(exe),'--version'], env=env, text=True,
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=15)
            if result.returncode != 0:
                errors.append(f'PostgreSQL command cannot execute: {exe} (exit {result.returncode}): {result.stdout.strip()}')
        except Exception as exc:
            errors.append(f'PostgreSQL command cannot execute: {exe}: {exc}')

# macOS release builds must never retain references to the Homebrew installation on
# the GitHub runner. This is the regression that caused initdb exit 134 on clean Macs.
if mac and pg.is_dir():
    forbidden=('/opt/homebrew/Cellar/','/usr/local/Cellar/','/opt/homebrew/opt/','/usr/local/opt/')
    for path in sorted(list((pg/'bin').glob('*')) + list((pg/'lib').rglob('*'))):
        if not path.is_file() or path.is_symlink():
            continue
        probe=subprocess.run(['file','-b',str(path)], text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        if 'Mach-O' not in probe.stdout:
            continue
        linked=subprocess.run(['otool','-L',str(path)], text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if linked.returncode != 0:
            errors.append(f'otool failed for {path}: {linked.stderr.strip()}')
            continue
        for line in linked.stdout.splitlines()[1:]:
            dep=line.strip().split(' (compatibility version',1)[0].strip()
            if dep.startswith(forbidden):
                errors.append(f'non-relocatable macOS dependency: {path.relative_to(pg)} -> {dep}')
            if dep.startswith('@loader_path/'):
                rel=dep[len('@loader_path/'):]
                if not (path.parent/rel).resolve().exists():
                    errors.append(f'broken bundled macOS dependency: {path.relative_to(pg)} -> {dep}')

if errors:
    print('DSE ERP production bundle verification FAILED', file=sys.stderr)
    for e in errors: print(' - '+e, file=sys.stderr)
    sys.exit(1)
print('DSE ERP production bundle verification PASS')
print(f'Bundle: {root}')
print(f'PostgreSQL: {pg}')
