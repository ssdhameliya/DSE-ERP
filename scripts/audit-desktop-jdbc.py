#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]/'desktop/src/main/java'
old=[]
bridge=[]
for p in root.rglob('*.java'):
    t=p.read_text(errors='ignore')
    if 'DatabaseManager.getConnection' in t or 'org.example.database.DatabaseManager' in t:
        old.append(str(p.relative_to(root)))
    n=t.count('SpringDataAccess.openConnection')
    if n: bridge.append((str(p.relative_to(root)),n))
if old:
    print('FAIL: legacy DatabaseManager references remain:')
    print('\n'.join(old)); sys.exit(1)
print('PASS: DatabaseManager references = 0')
print('SpringDataAccess compatibility call sites =',sum(n for _,n in bridge))
