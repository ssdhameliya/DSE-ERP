#!/usr/bin/env python3
"""DSE ERP 9.0.49 Phase 7 production-source cleanliness contract."""
from __future__ import annotations

import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "desktop/src/main/resources"
CSS_ROOT = RESOURCES / "css"
FAIL: list[str] = []

EXPECTED_CSS = {
    "desktop/src/main/resources/css/dark-theme.css",
    "desktop/src/main/resources/css/light-theme.css",
}
JUNK_DIRS = {"target", "build", "out", ".idea", ".gradle", ".pytest_cache", "__pycache__"}
JUNK_FILE_PATTERNS = (
    re.compile(r"^\.DS_Store$"), re.compile(r"^Thumbs\.db$", re.I),
    re.compile(r".*\.(?:class|jar|log|tmp|bak|orig|pyc)$", re.I), re.compile(r"^javac\..*\.args$", re.I), re.compile(r".*~$"),
)
TEXT_EXTENSIONS = {
    ".java", ".fxml", ".css", ".xml", ".properties", ".py", ".yml", ".yaml",
    ".md", ".sh", ".ps1", ".bat", ".cmd",
}


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def fail(message: str) -> None:
    FAIL.append(message)


# Exactly two CSS files in the whole source repository, both canonical runtime themes.
all_css = {rel(p) for p in ROOT.rglob("*.css") if p.is_file()}
if all_css != EXPECTED_CSS:
    fail(f"repository must contain exactly the two canonical CSS files, found {sorted(all_css)}")

# Canonical CSS should contain no migration comments, trailing whitespace, or repeated blank noise.
for path in sorted(CSS_ROOT.glob("*.css")):
    text = path.read_text(encoding="utf-8")
    if "/*" in text or "*/" in text:
        fail(f"production CSS comments/merge markers remain: {rel(path)}")
    if re.search(r"[ \t]+$", text, re.M):
        fail(f"trailing whitespace remains: {rel(path)}")
    if "\n\n\n" in text:
        fail(f"excess consecutive blank lines remain: {rel(path)}")
    if not text.endswith("\n"):
        fail(f"missing final newline: {rel(path)}")
    if text.count("{") != text.count("}"):
        fail(f"unbalanced CSS braces: {rel(path)}")

# No FXML may load another stylesheet; every FXML must remain parseable.
fxml_files = sorted((RESOURCES / "fxml").rglob("*.fxml"))
for path in fxml_files:
    try:
        root = ET.parse(path).getroot()
    except Exception as exc:
        fail(f"FXML parse failure {rel(path)}: {exc}")
        continue
    raw = path.read_text(encoding="utf-8")
    if "stylesheets" in root.attrib or "stylesheets=" in raw:
        fail(f"FXML-local stylesheet reference remains: {rel(path)}")

# Source archive cleanliness: no IDE/build/cache/junk outputs.
for path in ROOT.rglob("*"):
    if path.is_dir() and path.name in JUNK_DIRS:
        fail(f"junk/build directory present: {rel(path)}")
    elif path.is_file() and any(pattern.match(path.name) for pattern in JUNK_FILE_PATTERNS):
        fail(f"junk/build file present: {rel(path)}")

# No trailing whitespace in normal source/config text.
trailing_files = []
for path in ROOT.rglob("*"):
    if not path.is_file() or path.suffix.lower() not in TEXT_EXTENSIONS:
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    if re.search(r"[ \t]+$", text, re.M):
        trailing_files.append(rel(path))
if trailing_files:
    fail("trailing whitespace remains in: " + ", ".join(trailing_files[:20]))

# Version identity must be production-consistent.
version_contract = {
    "desktop/src/main/resources/app-version.properties": "version=9.0.49",
    "runtime/runtime-manifest.properties": "runtime.phase=9.0.52",
    "pom.xml": "<version>9.0.52</version>",
    "desktop/pom.xml": "<version>9.0.52</version>",
    "server/pom.xml": "<version>9.0.52</version>",
}
for file_name, token in version_contract.items():
    path = ROOT / file_name
    if not path.exists() or token not in path.read_text(encoding="utf-8", errors="ignore"):
        fail(f"9.0.49 version contract missing from {file_name}: {token}")

# Temporary Phase-2 merge metadata must be gone after final canonicalization.
if (ROOT / "scripts/phase2-two-theme-source-map-9.0.49.json").exists():
    fail("obsolete Phase-2 merge source-map remains")

if FAIL:
    print("PHASE7_PRODUCTION_CLEAN_FAIL")
    for item in FAIL:
        print(" -", item)
    sys.exit(1)

print(
    "PHASE7_PRODUCTION_CLEAN_OK "
    f"css={len(all_css)} fxml={len(fxml_files)} resources={sum(1 for p in RESOURCES.rglob('*') if p.is_file())} "
    "junk=0 trailing_whitespace=0"
)
