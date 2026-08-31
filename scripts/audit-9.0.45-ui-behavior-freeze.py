#!/usr/bin/env python3
"""DSE ERP 9.0.45 reviewed UI / behavior preservation audit.

The baseline freezes the currently approved 9.0.45 IntelliJ source after each
reviewed UI migration phase. It may be updated only when the approved phase
delta is intentional and navigation, database, controller/table/dialog behavior
and all non-approved FXML structure remain equivalent.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "scripts" / "ui-behavior-freeze-9.0.45.json"
FXML_ROOT = ROOT / "desktop" / "src" / "main" / "resources" / "fxml"
CSS_ROOT = ROOT / "desktop" / "src" / "main" / "resources" / "css"
JAVA_ROOT = ROOT / "desktop" / "src" / "main" / "java"
FX_NS = "http://javafx.com/fxml/1"

PROTECTED_NAVIGATION = [
    "desktop/src/main/resources/fxml/pages/Dashboard.fxml",
    "desktop/src/main/java/org/example/controller/DashboardController.java",
    "desktop/src/main/java/org/example/navigation/DeepLinkRouter.java",
    "desktop/src/main/java/org/example/navigation/DeepLinkSupport.java",
    "desktop/src/main/java/org/example/navigation/NavigationGuardRegistry.java",
    "desktop/src/main/java/org/example/navigation/NavigationManager.java",
    "desktop/src/main/java/org/example/navigation/ScreenLifecycle.java",
]

DIALOG_TOKENS = [
    "OwnedAlert(", "OwnedDialog<", "OwnedDialog(", "OwnedTextInputDialog(",
    "new Alert(", "new Dialog<", "new Dialog(", "new TextInputDialog(",
    ".showAndWait(", ".show(", "ButtonType.YES", "ButtonType.NO",
    "ButtonType.OK", "ButtonType.CANCEL", "Alert.AlertType.CONFIRMATION",
    "Alert.AlertType.WARNING", "Alert.AlertType.ERROR", "Alert.AlertType.INFORMATION",
    "initModality(", "setOnCloseRequest(", "configureDialogStage(",
]

TABLE_TOKENS = [
    ".setCellFactory(", ".setCellValueFactory(", ".setRowFactory(",
    ".setOnMouseClicked(", ".getSelectionModel()", ".setSelectionMode(",
    "RegisterColumnPreferences.install(", ".setSortPolicy(", ".setOnSort(",
    ".setComparator(", ".setOnKeyPressed(",
]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def fxml_snapshot(path: Path) -> dict:
    root = ET.parse(path).getroot()
    fxid_key = f"{{{FX_NS}}}id"
    controller_key = f"{{{FX_NS}}}controller"
    nodes = []
    fx_ids = []
    events = []
    tables = []
    columns = []

    def walk(node: ET.Element, route: str) -> None:
        tag = local_name(node.tag)
        fxid = node.attrib.get(fxid_key, "")
        if fxid:
            fx_ids.append(fxid)
        event_attrs = sorted((k, v) for k, v in node.attrib.items() if local_name(k).startswith("on"))
        for key, value in event_attrs:
            events.append([route, tag, fxid, local_name(key), value])
        if tag == "TableView":
            tables.append(fxid)
        if tag == "TableColumn":
            columns.append({
                "fx_id": fxid,
                "text": node.attrib.get("text", ""),
                "sortable": node.attrib.get("sortable", "true"),
                "resizable": node.attrib.get("resizable", "true"),
            })
        # Freeze hierarchy/copy/geometry and existing CSS class wiring. Inline CSS and
        # stylesheet references are recorded too so Phase 2 must explicitly review them.
        attrs = []
        for k, v in sorted(node.attrib.items(), key=lambda item: local_name(item[0])):
            name = local_name(k)
            if name == "controller":
                continue
            attrs.append([name, v])
        nodes.append([route, tag, fxid, attrs])
        child_counts = Counter()
        for child in list(node):
            ctag = local_name(child.tag)
            child_counts[ctag] += 1
            walk(child, f"{route}/{ctag}[{child_counts[ctag]}]")

    walk(root, f"/{local_name(root.tag)}[1]")
    return {
        "controller": root.attrib.get(controller_key, ""),
        "fx_ids": sorted(fx_ids),
        "events": sorted(events),
        "tables": sorted(tables),
        "columns": columns,
        "nodes": nodes,
    }


def java_token_snapshot(tokens: list[str]) -> dict:
    result = {}
    for path in sorted(JAVA_ROOT.rglob("*.java")):
        source = path.read_text(encoding="utf-8", errors="ignore")
        counts = {token: source.count(token) for token in tokens}
        if any(counts.values()):
            result[rel(path)] = counts
    return result


def capture() -> dict:
    fxml_files = sorted(FXML_ROOT.rglob("*.fxml"))
    css_files = sorted(CSS_ROOT.glob("*.css"))
    migration_files = sorted((ROOT / "server/src/main/resources/db/migration").glob("*.sql"))
    database_files = [ROOT / "server/src/main/resources/schema.sql", *migration_files]

    fxml = {rel(path): fxml_snapshot(path) for path in fxml_files}
    return {
        "contract": "DSE ERP 9.0.45 approved UI/behavior freeze",
        "version": "9.0.45",
        "protected_navigation_hashes": {p: sha256(ROOT / p) for p in PROTECTED_NAVIGATION},
        "database_schema_hashes": {rel(p): sha256(p) for p in database_files},
        "css_visual_baseline_hashes": {rel(p): sha256(p) for p in css_files},
        "fxml": fxml,
        "dialog_behavior": java_token_snapshot(DIALOG_TOKENS),
        "table_behavior": java_token_snapshot(TABLE_TOKENS),
        "inventory": {
            "fxml_files": len(fxml_files),
            "css_files": [p.name for p in css_files],
            "table_views": sum(len(v["tables"]) for v in fxml.values()),
            "table_columns": sum(len(v["columns"]) for v in fxml.values()),
            "fxml_fx_ids": sum(len(v["fx_ids"]) for v in fxml.values()),
            "fxml_event_bindings": sum(len(v["events"]) for v in fxml.values()),
            "dialog_java_files": len(java_token_snapshot(DIALOG_TOKENS)),
            "table_behavior_java_files": len(java_token_snapshot(TABLE_TOKENS)),
        },
    }


def diff(expected, actual, path="root", failures=None):
    if failures is None:
        failures = []
    if type(expected) is not type(actual):
        failures.append(f"{path}: type changed {type(expected).__name__} -> {type(actual).__name__}")
        return failures
    if isinstance(expected, dict):
        ek, ak = set(expected), set(actual)
        for key in sorted(ek - ak):
            failures.append(f"{path}: missing key {key}")
        for key in sorted(ak - ek):
            failures.append(f"{path}: unexpected key {key}")
        for key in sorted(ek & ak):
            diff(expected[key], actual[key], f"{path}.{key}", failures)
    elif isinstance(expected, list):
        if expected != actual:
            failures.append(f"{path}: list/signature changed")
    elif expected != actual:
        failures.append(f"{path}: {expected!r} -> {actual!r}")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--capture", action="store_true", help="write/replace the reviewed baseline")
    args = parser.parse_args()
    current = capture()
    if args.capture:
        BASELINE.write_text(json.dumps(current, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        inv = current["inventory"]
        print("CAPTURED", BASELINE.relative_to(ROOT))
        print("inventory", json.dumps(inv, sort_keys=True))
        return 0
    if not BASELINE.exists():
        print("FAIL: baseline missing; run with --capture only after an explicit phase review")
        return 1
    expected = json.loads(BASELINE.read_text(encoding="utf-8"))
    failures = diff(expected, current)
    if failures:
        print("UI_BEHAVIOR_FREEZE_FAIL")
        for failure in failures[:120]:
            print(" -", failure)
        if len(failures) > 120:
            print(f" - ... {len(failures)-120} additional differences")
        return 1
    inv = current["inventory"]
    print(
        "UI_BEHAVIOR_FREEZE_OK "
        f"fxml={inv['fxml_files']} tables={inv['table_views']} columns={inv['table_columns']} "
        f"fxids={inv['fxml_fx_ids']} events={inv['fxml_event_bindings']} css={len(inv['css_files'])}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
