#!/usr/bin/env python3
"""DSE ERP 9.0.52 Phase 2 final two-theme contract.

Phase 7 removed the temporary merge markers/source-map that were useful while
seven stylesheets were being consolidated. The approved final CSS bytes are now
frozen by audit-9.0.52-ui-behavior-freeze.py; this contract enforces the runtime
architecture: exactly two canonical themes, no FXML-local stylesheets, one active
theme in ThemeManager, and no unreviewed Light/Dark geometry drift.
"""
from __future__ import annotations

import re
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CSS_ROOT = ROOT / "desktop/src/main/resources/css"
FXML_ROOT = ROOT / "desktop/src/main/resources/fxml"
THEME_MANAGER = ROOT / "desktop/src/main/java/org/example/theme/ThemeManager.java"

EXPECTED_CSS = ["dark-theme.css", "light-theme.css"]
REMOVED_RUNTIME_CSS = [
    "ui-layout.css", "ui-components.css", "global-search-v3.css",
    "notification-center-v3.css", "shortcut-manager.css",
]
STRUCTURAL_PROPERTIES = {
    "-fx-padding", "-fx-font-size", "-fx-font-weight", "-fx-font-family",
    "-fx-min-width", "-fx-pref-width", "-fx-max-width",
    "-fx-min-height", "-fx-pref-height", "-fx-max-height",
    "-fx-fixed-cell-size", "-fx-cell-size", "-fx-graphic-text-gap",
    "-fx-border-width", "-fx-background-insets", "-fx-border-insets",
    "-fx-background-radius", "-fx-border-radius",
    "-fx-translate-x", "-fx-translate-y", "-fx-scale-x", "-fx-scale-y",
    "-fx-alignment", "-fx-content-display",
}
# This one difference existed in the approved 9.0.52 visual baseline before
# Phase 7 and is intentionally preserved rather than silently redesigning it.
APPROVED_STRUCTURAL_DIFFERENCES = {
    (".date-picker-popup > * > .month-year-pane > .spinner > .button", "-fx-border-width"):
        (None, "1px"),
}


def fail(message: str) -> None:
    raise SystemExit("FAIL: " + message)


def structural_effective(css_text: str) -> dict[tuple[str, str], str]:
    clean = re.sub(r"/\*.*?\*/", "", css_text, flags=re.S)
    result: dict[tuple[str, str], str] = {}
    for match in re.finditer(r"([^{}]+)\{([^{}]*)\}", clean, re.S):
        selectors = [s.strip() for s in match.group(1).split(",") if s.strip()]
        declarations = re.findall(r"(-fx-[\w-]+)\s*:\s*([^;}]*)", match.group(2))
        for selector in selectors:
            for prop, value in declarations:
                if prop in STRUCTURAL_PROPERTIES:
                    result[(selector, prop)] = value.strip()
    return result


def main() -> int:
    css_files = sorted(p.name for p in CSS_ROOT.glob("*.css"))
    if css_files != EXPECTED_CSS:
        fail(f"Exactly two CSS files required: expected {EXPECTED_CSS}, found {css_files}")

    themes = {name: (CSS_ROOT / name).read_text(encoding="utf-8") for name in EXPECTED_CSS}
    for name, text in themes.items():
        if text.count("{") != text.count("}"):
            fail(f"Unbalanced CSS braces in {name}")
        for old_name in REMOVED_RUNTIME_CSS:
            if old_name in text:
                fail(f"Canonical theme still contains obsolete stylesheet reference {old_name}: {name}")

    light = structural_effective(themes["light-theme.css"])
    dark = structural_effective(themes["dark-theme.css"])
    actual_diffs: dict[tuple[str, str], tuple[str | None, str | None]] = {}
    for key in set(light) | set(dark):
        pair = (light.get(key), dark.get(key))
        if pair[0] != pair[1]:
            actual_diffs[key] = pair
    if actual_diffs != APPROVED_STRUCTURAL_DIFFERENCES:
        preview = sorted((k, v) for k, v in actual_diffs.items())[:10]
        fail(f"Light/Dark structural geometry drift detected: {preview}")

    # 9.0.52 consolidation: legacy Reporting / Shortcut namespaces must not remain,
    # and the central structural selectors may not define the same geometry property
    # more than once inside a theme. This prevents old/new CSS layers fighting.
    forbidden_legacy = {
        "reports-single-page", "dse-shortcut-panel", "dse-shortcut-v2-panel",
        "dse-shortcut-v2-card", "dse-shortcut-group-card", "dse-shortcut-action-name",
        "report-bottom-row", "report-chart-row", "reports-grow-row", "reports-bottom-fill",
        "reports-title-block", "report-static-summary", "report-table-row",
    }
    critical_selectors = {
        ".table-view", ".kpi-card", ".metric-card", ".status-pill",
        ".text-field", ".combo-box", ".page-title", ".page-subtitle",
        ".approved-page-header", ".approved-card", ".approved-surface",
        ".approved-kpi", ".approved-kpi-icon", ".approved-kpi-caption",
        ".approved-kpi-value", ".button", ".approved-button", ".approved-menu-button",
        ".report-viewer-filter-panel", ".report-results-toolbar", ".report-viewer-footer",
        ".report-card", ".report-filter-card",
        ".reports-workspace .erp-kpi-section .report-kpi-card",
    }
    for theme_name, theme_text in themes.items():
        for legacy in sorted(forbidden_legacy):
            if legacy in theme_text:
                fail(f"Legacy CSS namespace remains in {theme_name}: {legacy}")
        seen_geometry: dict[tuple[str, str], int] = {}
        clean = re.sub(r"/\*.*?\*/", "", theme_text, flags=re.S)
        for match in re.finditer(r"([^{}]+)\{([^{}]*)\}", clean, re.S):
            selectors = [item.strip() for item in match.group(1).split(",") if item.strip()]
            declarations = re.findall(r"(-fx-[\w-]+)\s*:\s*([^;}]*)", match.group(2))
            for selector in selectors:
                if selector not in critical_selectors:
                    continue
                for prop, _value in declarations:
                    if prop not in STRUCTURAL_PROPERTIES and prop != "-fx-spacing":
                        continue
                    key = (selector, prop)
                    seen_geometry[key] = seen_geometry.get(key, 0) + 1
        duplicates = sorted(key for key, count in seen_geometry.items() if count > 1)
        if duplicates:
            fail(f"Central CSS geometry is declared more than once in {theme_name}: {duplicates[:12]}")

    fxml_files = sorted(FXML_ROOT.rglob("*.fxml"))
    for path in fxml_files:
        root = ET.parse(path).getroot()
        raw = path.read_text(encoding="utf-8")
        if "stylesheets" in root.attrib or "stylesheets=" in raw:
            fail(f"FXML-local stylesheet remains: {path.relative_to(ROOT)}")

    namespace_contracts = {
        "dse-global-search-v3-root": "desktop/src/main/resources/fxml/pages/GlobalSearch.fxml",
        "dse-notification-v3-root": "desktop/src/main/resources/fxml/pages/NotificationCenter.fxml",
        "dse-shortcut-v3-root": "desktop/src/main/resources/fxml/pages/settings/ShortcutsSettingsPanel.fxml",
    }
    for style_class, rel_path in namespace_contracts.items():
        if style_class not in (ROOT / rel_path).read_text(encoding="utf-8"):
            fail(f"Protected screen namespace missing from {rel_path}: {style_class}")
        for theme, text in themes.items():
            if style_class not in text:
                fail(f"Protected screen CSS namespace missing from {theme}: {style_class}")

    manager = THEME_MANAGER.read_text(encoding="utf-8")
    for token in ('"/css/light-theme.css"', '"/css/dark-theme.css"', "scene.getStylesheets().setAll("):
        if token not in manager:
            fail(f"ThemeManager single-theme contract missing: {token}")
    for old_name in REMOVED_RUNTIME_CSS:
        if old_name in manager:
            fail(f"ThemeManager still references removed CSS: {old_name}")
    if "addOnce(scene" in manager:
        fail("ThemeManager still stacks shared stylesheets")

    runtime_roots = [ROOT / "desktop/src/main/java", ROOT / "desktop/src/main/resources/fxml"]
    for source_root in runtime_roots:
        for path in source_root.rglob("*"):
            if not path.is_file() or path.suffix not in {".java", ".fxml"}:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            for old_name in REMOVED_RUNTIME_CSS:
                if old_name in text:
                    fail(f"Runtime source still references removed CSS {old_name}: {path.relative_to(ROOT)}")

    print(
        "PHASE2_TWO_THEME_OK "
        f"css=2 fxml={len(fxml_files)} light_bytes={len(themes['light-theme.css'].encode())} "
        f"dark_bytes={len(themes['dark-theme.css'].encode())} structural_deltas={len(actual_diffs)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
