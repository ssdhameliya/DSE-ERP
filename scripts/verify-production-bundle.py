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
for name in ['initdb','pg_ctl','psql','createdb','postgres']:
    exe=pg/'bin'/(name+'.exe' if win else name)
    commands.append(exe)
    if not exe.is_file(): errors.append(f'missing PostgreSQL command: {exe}')
manifest=root/'runtime'/'runtime-manifest.properties'
if not manifest.is_file(): errors.append(f'missing runtime manifest: {manifest}')

# initdb --version is not sufficient: Homebrew can execute the binary while a real
# initialization still fails because its compiled pkgshare points back to the build Mac.
# Find the bundled postgres.bki directory and later perform a real throw-away initdb.
pg_share=None
share_root=pg/'share'
if share_root.is_dir():
    preferred=[share_root/'postgresql@18', share_root]
    for candidate in preferred:
        if (candidate/'postgres.bki').is_file():
            pg_share=candidate
            break
    if pg_share is None:
        pg_share=next((item.parent for item in share_root.rglob('postgres.bki') if item.is_file()), None)
if pg_share is None:
    errors.append(f'missing PostgreSQL bootstrap file postgres.bki under: {share_root}')

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

# Perform the exact operation that previously failed only on customer Macs.  The -L
# option is PostgreSQL's supported override for initdb input files.  --no-sync is used
# only for this disposable CI verification cluster.
if not errors and pg_share is not None:
    import tempfile
    import shutil
    temp_parent=Path(tempfile.mkdtemp(prefix='dse-pg-verify-'))
    cluster=temp_parent/'data'
    try:
        initdb=pg/'bin'/('initdb.exe' if win else 'initdb')
        result=subprocess.run([str(initdb), '-D', str(cluster), '-L', str(pg_share),
                               '-U', 'dse_verify', '--encoding=UTF8', '--locale=C',
                               '--auth-local=trust', '--auth-host=trust', '--no-sync'],
                              env=env, text=True, encoding='utf-8', errors='replace',
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
        if result.returncode != 0 or not (cluster/'PG_VERSION').is_file():
            errors.append(f'PostgreSQL real initdb verification failed (exit {result.returncode}): {result.stdout.strip()}')
    except Exception as exc:
        errors.append(f'PostgreSQL real initdb verification failed: {exc}')
    finally:
        shutil.rmtree(temp_parent, ignore_errors=True)

# macOS release builds must never retain references to the Homebrew installation on
# the GitHub runner. This is the regression that caused initdb exit 134 on clean Macs.
if mac and pg.is_dir():
    forbidden=('/opt/homebrew/Cellar/','/usr/local/Cellar/','/opt/homebrew/opt/','/usr/local/opt/')
    for path in sorted(list((pg/'bin').glob('*')) + list((pg/'lib').rglob('*'))):
        if not path.is_file() or path.is_symlink():
            continue
        probe=subprocess.run(['file','-b',str(path)], text=True, encoding='utf-8', errors='replace', stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        if 'Mach-O' not in probe.stdout:
            continue
        linked=subprocess.run(['otool','-L',str(path)], text=True, encoding='utf-8', errors='replace', stdout=subprocess.PIPE, stderr=subprocess.PIPE)
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
