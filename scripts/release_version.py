from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
def release_version():
    text=(ROOT/'.mvn/maven.config').read_text(encoding='utf-8')
    m=re.search(r'(?m)^-Drevision=([^\s]+)\s*$', text)
    if not m: raise RuntimeError('Missing -Drevision in .mvn/maven.config')
    return m.group(1).strip()
VERSION=release_version()
