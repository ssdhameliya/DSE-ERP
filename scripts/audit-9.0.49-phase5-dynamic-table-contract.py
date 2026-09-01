#!/usr/bin/env python3
"""DSE ERP 9.0.49 Phase 5 dynamic TableView layout / CSS cleanup contract."""
from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FXML_ROOT = ROOT / "desktop/src/main/resources/fxml"
JAVA_ROOT = ROOT / "desktop/src/main/java"
CSS_ROOT = ROOT / "desktop/src/main/resources/css"
MANAGER = JAVA_ROOT / "org/example/util/DynamicTableLayoutManager.java"
ENHANCER = JAVA_ROOT / "org/example/util/ProfessionalUiEnhancer.java"
PREFERENCES = JAVA_ROOT / "org/example/util/RegisterColumnPreferences.java"
SOURCE_MAP = ROOT / "scripts/phase5-dynamic-table-source-map-9.0.49.json"
EXPECTED_CSS = ["dark-theme.css", "light-theme.css"]


def fail(message: str) -> None:
    raise SystemExit("PHASE5_DYNAMIC_TABLE_FAIL: " + message)


def local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fxml_inventory() -> dict:
    tables = 0
    columns = 0
    width_attrs: list[str] = []
    table_files: dict[str, int] = {}
    for path in sorted(FXML_ROOT.rglob("*.fxml")):
        root = ET.parse(path).getroot()
        rel = str(path.relative_to(ROOT)).replace("\\", "/")
        own_tables = 0
        for node in root.iter():
            kind = local(node.tag)
            if kind == "TableView":
                tables += 1
                own_tables += 1
            elif kind == "TableColumn":
                columns += 1
                for attr in ("minWidth", "prefWidth", "maxWidth"):
                    if attr in node.attrib:
                        width_attrs.append(f"{rel}:{node.attrib.get('{http://javafx.com/fxml/1}id', node.attrib.get('text','?'))}:{attr}")
        if own_tables:
            table_files[rel] = own_tables
    return {"tables": tables, "columns": columns, "width_attrs": width_attrs, "table_files": table_files}


def table_column_vars(java: str) -> set[str]:
    names: set[str] = set()
    # Field/local declarations and method parameters. This deliberately does not
    # treat GridPane ColumnConstraints as TableColumns.
    for match in re.finditer(r"\bTableColumn\s*<[^;]+?>\s+([^;]+);", java, re.S):
        decl = match.group(1)
        first = re.match(r"\s*(\w+)", decl)
        if first:
            names.add(first.group(1))
        for token in re.finditer(r"(?:^|,)\s*(\w+)\s*(?:=|,|$)", decl):
            names.add(token.group(1))
    for match in re.finditer(r"\bTableColumn\s*<[^>]+>\s+(\w+)\s*\)", java):
        names.add(match.group(1))
    for match in re.finditer(r"for\s*\(\s*TableColumn\s*<[^>]+>\s+(\w+)\s*:", java):
        names.add(match.group(1))
    return names


def illegal_java_width_owners() -> list[str]:
    problems: list[str] = []
    for path in sorted(JAVA_ROOT.rglob("*.java")):
        if path == MANAGER:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        rel = str(path.relative_to(ROOT)).replace("\\", "/")
        names = table_column_vars(text)
        for name in names:
            if re.search(rf"\b{re.escape(name)}\.set(?:Min|Pref|Max)Width\s*\(", text):
                problems.append(f"{rel}:{name}")
        if "setColumnResizePolicy(" in text:
            problems.append(f"{rel}:setColumnResizePolicy")
        if "setFixedCellSize(" in text:
            problems.append(f"{rel}:setFixedCellSize")
    return sorted(set(problems))


def programmatic_table_sites() -> dict[str, int]:
    sites: dict[str, int] = {}
    for path in sorted(JAVA_ROOT.rglob("*.java")):
        if path == MANAGER:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        count = len(re.findall(r"new\s+TableView\s*<", text))
        if count:
            rel = str(path.relative_to(ROOT)).replace("\\", "/")
            sites[rel] = count
            if "DynamicTableLayoutManager.install(" not in text and "PopupTableWorkspace.prepareTable(" not in text:
                fail(f"programmatic TableView has no dynamic-layout authority: {rel}")
    return sites


def main() -> int:
    if not MANAGER.exists() or not SOURCE_MAP.exists():
        fail("Phase 5 manager/source map is missing")
    baseline = json.loads(SOURCE_MAP.read_text(encoding="utf-8"))

    css_files = sorted(p.name for p in CSS_ROOT.rglob("*.css"))
    if css_files != EXPECTED_CSS:
        fail(f"exactly two CSS files required, found {css_files}")
    for theme in EXPECTED_CSS:
        text = (CSS_ROOT / theme).read_text(encoding="utf-8")
        density_rules = re.findall(r"([^{}]*?(?:\.table-view|erp-table-profile)[^{}]*)\{([^{}]*-fx-fixed-cell-size\s*:\s*44px;[^{}]*)\}", text, re.S)
        if not density_rules:
            fail(f"canonical preserved 44px row-density rule missing from {theme}")

    inventory = fxml_inventory()
    if inventory["width_attrs"]:
        fail(f"FXML TableColumn width attributes remain: {inventory['width_attrs'][:8]}")
    if inventory["tables"] != baseline["fxml_tables"] or inventory["columns"] != baseline["fxml_columns"]:
        fail(f"FXML table inventory drifted: {inventory['tables']} tables / {inventory['columns']} columns")
    if inventory["table_files"] != baseline["fxml_table_files"]:
        fail("FXML TableView file inventory drifted from reviewed Phase 5 baseline")

    illegal = illegal_java_width_owners()
    if illegal:
        fail("TableColumn/resize/row-height ownership remains outside the manager/themes: " + ", ".join(illegal[:12]))

    manager = MANAGER.read_text(encoding="utf-8")
    required = (
        "table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY)",
        "table.getVisibleLeafColumns()",
        "sampledContentWidth(table, column)",
        "headerWidth(column, heading)",
        "available - naturalTotal",
        "minimumTotal < available",
        "column.setMinWidth(0)",
        "column.setMaxWidth(Double.MAX_VALUE)",
        "column.setPrefWidth(width)",
        "table.widthProperty().addListener",
        "column.visibleProperty().addListener",
        "table.getColumns().addListener",
        "table.itemsProperty().addListener",
        "isLayoutReady(table)",
        "renderedCellControlWidth(table, column)",
        "requestLayoutIn(Node root)",
        "NATURAL_FLOOR",
    )
    for token in required:
        if token not in manager:
            fail(f"dynamic table manager contract missing: {token}")
    if sha256(MANAGER) != baseline["manager_sha256"]:
        fail("DynamicTableLayoutManager drifted from reviewed Phase 5 implementation")

    enhancer = ENHANCER.read_text(encoding="utf-8")
    if "DynamicTableLayoutManager.install(table);" not in enhancer:
        fail("ProfessionalUiEnhancer does not install the dynamic table manager")
    for forbidden in ("applyResponsiveWidth(", "setColumnResizePolicy("):
        if forbidden in enhancer:
            fail(f"ProfessionalUiEnhancer still owns table widths: {forbidden}")

    prefs = PREFERENCES.read_text(encoding="utf-8")
    if "widthProperty()" in prefs or "prefs.putDouble(" in prefs or 'getDouble(key + ".width"' in prefs:
        fail("RegisterColumnPreferences still persists/restores column widths")
    for token in ("visibleProperty()", 'prefs.remove(key + ".width")', 'prefs.put("order"'):
        if token not in prefs:
            fail(f"RegisterColumnPreferences visibility/order contract missing: {token}")

    sites = programmatic_table_sites()
    if sites != baseline["programmatic_table_sites"]:
        fail(f"programmatic TableView inventory drifted: {sites}")

    print(
        "PHASE5_DYNAMIC_TABLE_OK "
        f"fxml_tables={inventory['tables']} fxml_columns={inventory['columns']} "
        f"programmatic_tables={sum(sites.values())} width_attrs=0 java_width_owners=0 css=2"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
