#!/usr/bin/env python3
"""DSE ERP 9.0.46 corrective UI stabilization contract.

Locks the post-Phase-7 fixes for first-paint table sizing, action-column
readability, drawer-triggered reflow, purple warning-button surfaces, and the
canonical Reporting UI patterns.
"""
from __future__ import annotations

import re
import sys
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CSS_ROOT = ROOT / "desktop/src/main/resources/css"
REPORTS = ROOT / "desktop/src/main/resources/fxml/pages/Reports.fxml"
VIEWER = ROOT / "desktop/src/main/resources/fxml/pages/ReportViewer.fxml"
REPORTS_CTRL = ROOT / "desktop/src/main/java/org/example/controller/ReportsController.java"
VIEWER_CTRL = ROOT / "desktop/src/main/java/org/example/controller/ReportViewerController.java"
TABLE_MANAGER = ROOT / "desktop/src/main/java/org/example/util/DynamicTableLayoutManager.java"
KPI_MANAGER = ROOT / "desktop/src/main/java/org/example/util/ResponsiveKpiLayoutManager.java"
REGISTER_SUPPORT = ROOT / "desktop/src/main/java/org/example/util/RegisterUiSupport.java"
FAIL: list[str] = []


def fail(message: str) -> None:
    FAIL.append(message)


def blocks(text: str):
    return re.findall(r"([^{}]+)\{([^{}]*)\}", text, re.S)


def norm(value: str) -> str:
    return " ".join(value.split())


css_files = sorted(p.name for p in CSS_ROOT.glob("*.css"))
if css_files != ["dark-theme.css", "light-theme.css"]:
    fail(f"exactly two CSS themes required, found {css_files}")

YELLOW = {
    "#f59e0b", "#fbbf24", "#d97706", "#b45309", "#fde68a", "#fffbeb",
    "#f2ba2d", "#b77a00", "#4b3510", "#765516",
}
ACTION_TOKENS = (
    "table-action-menu", "row-actions", "user-action-menu", "backup-row-actions",
    "bank-row-action", "reminder-action-button", "approved-row-action", "safe-rollback-row-actions",
)
for theme in css_files:
    text = (CSS_ROOT / theme).read_text(encoding="utf-8")
    report_selectors = []
    for selector, body in blocks(text):
        selector_n = norm(selector)
        low = selector_n.lower()
        if "report" in low:
            report_selectors.append(selector_n)
            if "-fx-fixed-cell-size" in body:
                fail(f"Reporting-specific fixed table density remains in {theme}: {selector_n}")
        if any(token in low for token in ACTION_TOKENS):
            if re.search(r"-fx-(?:min|pref|max)-width\s*:", body):
                fail(f"fixed row-action control width remains in {theme}: {selector_n}")
        if ("button" in low or "row-action" in low) and "glyph" not in low and "ikonli" not in low:
            body_low = body.lower()
            used = sorted(color for color in YELLOW if color in body_low)
            if used:
                fail(f"yellow/amber button surface remains in {theme}: {selector_n} -> {used}")
    duplicates = [selector for selector, count in Counter(report_selectors).items() if count > 1]
    if duplicates:
        fail(f"duplicate exact Reporting selectors remain in {theme}: {duplicates[:10]}")

viewer_raw = VIEWER.read_text(encoding="utf-8")
reports_raw = REPORTS.read_text(encoding="utf-8")
for path in (VIEWER, REPORTS):
    try:
        ET.parse(path)
    except Exception as exc:
        fail(f"FXML parse failure {path.name}: {exc}")

if 'fixedCellSize=' in viewer_raw:
    fail("ReportViewer.fxml still owns a fixed row height")
if 'fx:id="metricPane"' in viewer_raw or 'report-metric-strip' in viewer_raw:
    fail("Individual Report Viewer KPI strip must remain removed in 9.0.46")
for action in ("#exportPdf", "#exportExcel", "#exportCsv", "#printReport"):
    expected = 1
    actual = viewer_raw.count(f'onAction="{action}"')
    if actual != expected:
        fail(f"Report Viewer duplicate/missing action {action}: expected {expected}, found {actual}")
if '#backToReports' in viewer_raw:
    fail("duplicate Report Center back action remains in Report Viewer")
for token in (
    'fx:id="reportSearchIcon"',
    'styleClass="erp-item-search-shell,register-header-search-shell,report-search-shell"',
    'styleClass="report-card,report-results-panel"',
):
    if token not in viewer_raw:
        fail(f"Report Viewer canonical panel/search contract missing: {token}")
for token in ('fx:id="reportCenterSearchIcon"', 'fx:id="savedReportSearchIcon"', 'fx:id="scheduleSearchIcon"'):
    if token not in reports_raw:
        fail(f"Reports canonical search icon host missing: {token}")

manager = TABLE_MANAGER.read_text(encoding="utf-8")
for token in (
    "isLayoutReady(table)",
    "renderedCellControlWidth(table, column)",
    "requestLayoutIn(Node root)",
    "NATURAL_FLOOR",
    "Platform.isFxApplicationThread() && isLayoutReady(table)",
):
    if token not in manager:
        fail(f"first-paint/action-column table stabilization missing: {token}")

register_support = REGISTER_SUPPORT.read_text(encoding="utf-8")
for token in ("reflowDrawerTables(splitPane)", "DynamicTableLayoutManager.requestLayoutIn(splitPane)"):
    if token not in register_support:
        fail(f"drawer-triggered table reflow missing: {token}")

kpi = KPI_MANAGER.read_text(encoding="utf-8")
for token in (
    "GridPane.setRowIndex(card, i / columns)",
    "pane.widthProperty().addListener",
):
    if token not in kpi:
        fail(f"balanced responsive KPI contract missing: {token}")

viewer_ctrl = VIEWER_CTRL.read_text(encoding="utf-8")
for forbidden in ("card.setMinWidth(175)", "card.setPrefWidth(230)"):
    if forbidden in viewer_ctrl:
        fail(f"Report Viewer still owns KPI width: {forbidden}")
for token in ("reflowFilterGrid()", "RegisterUiSupport.configureHeaderSearch(txtSearch,reportSearchIcon"):
    if token not in viewer_ctrl:
        fail(f"Report Viewer canonical layout behavior missing: {token}")

reports_ctrl = REPORTS_CTRL.read_text(encoding="utf-8")
for token in (
    "RegisterUiSupport.configureHeaderSearch(txtReportSearch,reportCenterSearchIcon",
    "RegisterUiSupport.configureHeaderSearch(txtSavedSearch,savedReportSearchIcon",
    "RegisterUiSupport.configureHeaderSearch(txtScheduleSearch,scheduleSearchIcon",
    'actions.getStyleClass().addAll("row-actions","table-action-menu","approved-row-action","report-row-actions")',
    'open.getStyleClass().addAll("approved-button","approved-primary-button","report-card-open-button")',
):
    if token not in reports_ctrl:
        fail(f"Reports canonical search/action behavior missing: {token}")

# 9.0.46 table-first Reporting / icon+text / Bank KPI contracts.
if 'report-viewer-identity-panel' in viewer_raw:
    fail("obsolete separate Report Viewer identity panel returned")
if 'VBox.vgrow="ALWAYS" styleClass="report-card,report-results-panel"' not in viewer_raw:
    fail("Report Viewer table-first vgrow panel is missing")
if 'orientation="HORIZONTAL"' not in reports_raw or 'report-category-strip-list' not in reports_raw:
    fail("Report Center horizontal semantic category strip is missing")
if 'erp-kpi-single-row' not in (ROOT / "desktop/src/main/resources/fxml/pages/BankStatement.fxml").read_text(encoding="utf-8"):
    fail("Bank Statement eight-card single-row KPI marker is missing")
if 'ACTION_CONTROL_MIN_WIDTH = 112.0' not in manager:
    fail("icon+text Actions column minimum content floor is missing")
if 'erp-kpi-single-row' not in kpi:
    fail("single-row KPI layout support is missing")
if FAIL:
    print("PHASE8_UI_STABILIZATION_FAIL")
    for item in FAIL:
        print(" -", item)
    sys.exit(1)

print(
    "PHASE8_UI_STABILIZATION_OK "
    "first_paint=stable action_control_floor=rendered drawer_reflow=explicit "
    "reporting=canonical yellow_button_surfaces=0 report_selector_duplicates=0 css=2"
)
