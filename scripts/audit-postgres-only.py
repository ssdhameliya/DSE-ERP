#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
needle=("sql"+"ite").lower()
violations=[]
for base in [root/"desktop"/"src"/"main", root/"desktop"/"pom.xml"]:
    paths=[base] if base.is_file() else base.rglob("*")
    for p in paths:
        if not p.is_file() or p==Path(__file__): continue
        if p.suffix.lower() not in {".java",".xml",".fxml",".properties",".css"}: continue
        try: text=p.read_text(errors="ignore").lower()
        except Exception: continue
        if needle in text:
            violations.append(str(p.relative_to(root)))
if violations:
    print("Legacy embedded-database references are not allowed in production source:")
    for v in violations: print(" -",v)
    sys.exit(1)
print("PASS: production desktop source is PostgreSQL-only.")
