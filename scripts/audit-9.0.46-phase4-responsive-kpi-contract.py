#!/usr/bin/env python3
"""DSE ERP 9.0.46 Phase 4 responsive KPI layout contract."""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FXML_ROOT = ROOT / "desktop/src/main/resources/fxml/pages"
MANAGER = ROOT / "desktop/src/main/java/org/example/util/ResponsiveKpiLayoutManager.java"
ENHANCER = ROOT / "desktop/src/main/java/org/example/util/ProfessionalUiEnhancer.java"
SOURCE_MAP = ROOT / "scripts/phase4-responsive-kpi-source-map-9.0.46.json"
STYLE = "erp-kpi-section"


def fail(message: str) -> None:
    raise SystemExit("PHASE4_RESPONSIVE_KPI_FAIL: " + message)


def local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def direct_cards(node: ET.Element) -> list[ET.Element]:
    ignored = {"columnConstraints", "rowConstraints", "padding"}
    return [child for child in list(node) if local(child.tag) not in ignored]


def capture_containers() -> list[dict]:
    result: list[dict] = []
    for path in sorted(FXML_ROOT.glob("*.fxml")):
        root = ET.parse(path).getroot()
        ordinal = 0
        for node in root.iter():
            styles = [part.strip() for part in (node.attrib.get("styleClass") or "").split(",") if part.strip()]
            if STYLE not in styles:
                continue
            ordinal += 1
            cards = direct_cards(node)
            result.append({
                "file": path.name,
                "ordinal": ordinal,
                "container": local(node.tag),
                "cards": len(cards),
                "card_tags": [local(card.tag) for card in cards],
                "style_class": node.attrib.get("styleClass", ""),
            })
    return result


def main() -> int:
    if not MANAGER.exists() or not SOURCE_MAP.exists():
        fail("Phase 4 manager/source map is missing")
    baseline = json.loads(SOURCE_MAP.read_text(encoding="utf-8"))
    containers = capture_containers()
    if containers != baseline.get("responsive_kpi_containers"):
        fail("responsive KPI container inventory drifted from the reviewed Phase 4 baseline")
    if len(containers) != 26:
        fail(f"expected 26 reviewed KPI containers, found {len(containers)}")

    for item in containers:
        if item["container"] not in {"HBox", "GridPane", "FlowPane"}:
            fail(f"unsupported KPI container {item['file']}:{item['container']}")
        if item["container"] != "FlowPane" and item["cards"] < 2:
            fail(f"KPI row must contain at least two cards: {item['file']}")
        if item["container"] == "FlowPane" and item["cards"] != 0:
            fail(f"dynamic Report Viewer FlowPane should start empty in FXML: {item['file']}")
        if any(tag not in {"HBox", "VBox"} for tag in item["card_tags"]):
            fail(f"KPI row contains non-card direct child: {item['file']} {item['card_tags']}")

    manager = MANAGER.read_text(encoding="utf-8")
    required_manager_tokens = (
        'KPI_SECTION_STYLE = "erp-kpi-section"',
        "responsiveColumnCount(cards.size(), grid.getWidth(), grid.getHgap())",
        "responsiveColumnCount(cards.size(), flow.getWidth(), flow.getHgap())",
        "grid.getColumnConstraints().clear()",
        "column.setPercentWidth(percent)",
        "GridPane.setRowIndex(card, i / columns)",
        "HBox.setHgrow(card, Priority.ALWAYS)",
        "GridPane.setHgrow(card, Priority.ALWAYS)",
        "region.setMinWidth(0)",
        "region.setMaxWidth(Double.MAX_VALUE)",
        "pane.widthProperty().addListener",
        "child.managedProperty().addListener",
        "pane.getChildrenUnmodifiable().addListener",
    )
    for token in required_manager_tokens:
        if token not in manager:
            fail(f"responsive KPI manager contract missing: {token}")
    if sha256(MANAGER) != baseline.get("manager_sha256"):
        fail("ResponsiveKpiLayoutManager drifted from reviewed Phase 4 implementation")

    enhancer = ENHANCER.read_text(encoding="utf-8")
    if "ResponsiveKpiLayoutManager.install(node);" not in enhancer:
        fail("ProfessionalUiEnhancer does not install the responsive KPI manager")

    # Fixed-count screens explicitly requested during planning must be enrolled.
    required_files = {
        "DashboardHome.fxml": 2,
        "Reports.fxml": 2,
        "BackupRestore.fxml": 1,
        "BankStatement.fxml": 1,
        "SafeRollback.fxml": 1,
        "NotificationCenter.fxml": 1,
        "ReminderCenter.fxml": 1,
        "UserAccess.fxml": 1,
        "SalesList.fxml": 1,
        "PurchaseList.fxml": 1,
    }
    actual = {}
    for item in containers:
        actual[item["file"]] = actual.get(item["file"], 0) + 1
    for name, minimum in required_files.items():
        if actual.get(name, 0) < minimum:
            fail(f"required KPI screen is not enrolled: {name}")

    # Phase 4 is layout-only: no new CSS file and no screen-specific KPI width class is allowed.
    css_files = sorted(p.name for p in (ROOT / "desktop/src/main/resources/css").glob("*.css"))
    if css_files != ["dark-theme.css", "light-theme.css"]:
        fail(f"two-theme contract regressed: {css_files}")

    print(
        "PHASE4_RESPONSIVE_KPI_OK "
        f"containers={len(containers)} cards={sum(x['cards'] for x in containers)} "
        f"hbox={sum(x['container']=='HBox' for x in containers)} "
        f"grid={sum(x['container']=='GridPane' for x in containers)} flow={sum(x['container']=='FlowPane' for x in containers)} css=2"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
