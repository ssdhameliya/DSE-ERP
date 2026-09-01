#!/usr/bin/env python3
"""DSE ERP 9.0.49 Phase 3 semantic icon / colour contract."""
from __future__ import annotations

import re
import sys
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FXML_ROOT = ROOT / "desktop/src/main/resources/fxml"
CSS_ROOT = ROOT / "desktop/src/main/resources/css"
JAVA_ROOT = ROOT / "desktop/src/main/java"
REGISTRY = ROOT / "desktop/src/main/resources/ui/semantic-registry.properties"
ICON_FACTORY = ROOT / "desktop/src/main/java/org/example/util/IconFactory.java"
ENHANCER = ROOT / "desktop/src/main/java/org/example/util/ProfessionalUiEnhancer.java"
SEMANTIC_CELLS = ROOT / "desktop/src/main/java/org/example/util/SemanticTableCells.java"
SOURCE_MAP = ROOT / "scripts/phase3-semantic-ui-source-map-9.0.49.json"

FIELD_STYLE_TOKENS = (
    "field-label", "finance-field-label", "form-label", "filter-label", "meta-label",
    "detail-label", "field-caption", "inline-label", "field-blue", "field-orange",
    "field-cyan", "field-green", "field-red", "location-label",
)
INPUT_TAGS = {"TextField", "PasswordField", "TextArea", "ComboBox", "DatePicker", "Spinner", "ChoiceBox"}
ALLOWED_COLOURS = {"blue", "green", "orange", "purple", "pink", "teal", "indigo"}
GENERIC_ICONS = {"fas-question-circle"}


def fail(message: str) -> None:
    raise SystemExit("PHASE3_SEMANTIC_UI_FAIL: " + message)


def local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def key(text: str) -> str:
    value = (text or "").lower().strip()
    value = value.replace("&amp;", " and ").replace("&", " and ").replace("₹", " amount ")
    value = value.replace("↔", " link ").replace("→", " to ").replace("←", " from ")
    value = re.sub(r"[*:]+$", "", value).strip()
    value = re.sub(r"[^a-z0-9]+", " ", value)
    return re.sub(r"\s+", " ", value).strip()


def load_properties() -> dict[str, str]:
    if not REGISTRY.exists():
        fail("semantic-registry.properties is missing")
    values: dict[str, str] = {}
    for raw in REGISTRY.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" not in line:
            fail(f"invalid registry line: {raw}")
        k, v = line.split("=", 1)
        k, v = k.strip(), v.strip()
        if not k or not v:
            fail(f"blank registry key/value: {raw}")
        if k in values and values[k] != v:
            fail(f"conflicting registry key: {k}")
        values[k] = v
    return values


def semantic_spec(values: dict[str, str], semantic: str) -> tuple[str, str]:
    icon = values.get(f"semantic.{semantic}.icon", "")
    colour = values.get(f"semantic.{semantic}.colour", "")
    if not icon or not colour:
        fail(f"semantic '{semantic}' lacks icon/colour spec")
    if icon in GENERIC_ICONS:
        fail(f"semantic '{semantic}' uses prohibited generic icon {icon}")
    if colour not in ALLOWED_COLOURS:
        fail(f"semantic '{semantic}' has unsupported colour family '{colour}'")
    return icon, colour


def mapped(values: dict[str, str], namespace: str, text: str) -> str:
    k = key(text)
    if not k:
        return ""
    prop = f"{namespace}." + k.replace(" ", ".")
    semantic = values.get(prop, "")
    if not semantic:
        fail(f"unmapped {namespace} text: {text!r} ({prop})")
    semantic_spec(values, semantic)
    return semantic


def field_candidates(root: ET.Element) -> list[str]:
    parent = {child: node for node in root.iter() for child in node}
    result: list[str] = []
    for node in root.iter():
        if local(node.tag) != "Label":
            continue
        text = (node.attrib.get("text") or "").strip()
        if not text or not key(text):
            continue
        styles = (node.attrib.get("styleClass") or "").lower()
        candidate = any(token in styles for token in FIELD_STYLE_TOKENS)
        if not candidate:
            p = parent.get(node)
            if p is not None:
                ptag = local(p.tag)
                siblings = list(p)
                if ptag in {"VBox", "HBox"} and len(siblings) <= 6:
                    candidate = any(local(s.tag) in INPUT_TAGS for s in siblings if s is not node)
                elif ptag == "GridPane":
                    row = node.attrib.get("GridPane.rowIndex", "0")
                    candidate = any(
                        s is not node and local(s.tag) in INPUT_TAGS
                        and s.attrib.get("GridPane.rowIndex", "0") == row
                        for s in siblings
                    )
        if candidate:
            result.append(text)
    return result


def semantic_css_blocks(text: str) -> list[str]:
    prefixes = (
        "erp-field-label-colour-", "erp-table-header-colour-",
        "erp-kpi-label-colour-", "erp-kpi-value-colour-", "erp-status-glyph-",
    )
    blocks: list[str] = []
    for match in re.finditer(r"([^{}]+)\{([^{}]*)\}", text, re.S):
        selector = match.group(1)
        if any(prefix in selector for prefix in prefixes):
            blocks.append(match.group(0))
    if not blocks:
        fail("semantic CSS selectors are missing")
    return blocks

def main() -> int:
    values = load_properties()
    if not SOURCE_MAP.exists():
        fail("Phase 3 source map is missing")
    baseline = json.loads(SOURCE_MAP.read_text(encoding="utf-8"))
    expected_registry_hash = baseline.get("registry", {}).get("sha256")
    current_registry_hash = hashlib.sha256(REGISTRY.read_bytes()).hexdigest()
    if expected_registry_hash != current_registry_hash:
        fail("semantic registry drifted from the reviewed Phase 3 baseline")

    # Every declared semantic must be complete and non-generic.
    semantics = sorted({k.split(".")[1] for k in values if k.startswith("semantic.") and k.endswith(".icon")})
    if not semantics:
        fail("registry has no semantic specs")
    for semantic in semantics:
        semantic_spec(values, semantic)

    field_count = header_count = kpi_count = 0
    field_unique: set[str] = set()
    header_unique: set[str] = set()
    kpi_unique: set[str] = set()

    for path in sorted(FXML_ROOT.rglob("*.fxml")):
        root = ET.parse(path).getroot()
        for text in field_candidates(root):
            mapped(values, "field", text)
            field_count += 1
            field_unique.add(key(text))
        for node in root.iter():
            tag = local(node.tag)
            text = (node.attrib.get("text") or "").strip()
            if tag == "TableColumn" and text and key(text):
                mapped(values, "header", text)
                header_count += 1
                header_unique.add(key(text))
            if tag == "Label" and text:
                styles = (node.attrib.get("styleClass") or "").lower()
                if ("metric-label" in styles or "metric-title" in styles or "kpi-label" in styles or "kpi-title" in styles) and key(text):
                    mapped(values, "kpi", text)
                    kpi_count += 1
                    kpi_unique.add(key(text))

    # Core business fields the user explicitly requested must never collapse to the same icon.
    core = {
        "customer": "customer",
        "address": "address",
        "invoice": "invoice",
        "reference": "reference",
        "number": "number",
        "phone": "phone",
        "email": "email",
        "gstin": "gstin",
        "amount": "amount",
        "quantity": "quantity",
        "bank-account": "bank-account",
        "code": "code",
    }
    icons: dict[str, str] = {}
    for label, semantic in core.items():
        icon, _ = semantic_spec(values, semantic)
        if icon in icons:
            fail(f"core semantic icon collision: {label}/{semantic} and {icons[icon]} both use {icon}")
        icons[icon] = f"{label}/{semantic}"

    icon_factory = ICON_FACTORY.read_text(encoding="utf-8")
    enhancer = ENHANCER.read_text(encoding="utf-8")
    semantic_cells = SEMANTIC_CELLS.read_text(encoding="utf-8")
    for token in (
        "UiSemanticRegistry.fieldSemantic(text)",
        "UiSemanticRegistry.kpiSemantic(text)",
        "erp-table-header-colour-",
        "erp-kpi-value-colour-",
    ):
        if token not in icon_factory:
            fail(f"IconFactory semantic integration missing: {token}")
    registry_header = "String semantic = UiSemanticRegistry.headerSemantic(heading);"
    explicit_header = "if (semantic == null && Boolean.TRUE.equals(column.getProperties().get(\"erp-header-explicit\"))"
    if registry_header not in enhancer or explicit_header not in enhancer:
        fail("Table header enhancer is not registry-first")
    if "UiSemanticRegistry.headerSemantic(heading)" not in icon_factory:
        fail("Explicit TableColumn decoration does not canonicalize through the registry")
    if "state.color" in semantic_cells or "-fx-text-fill:" in semantic_cells:
        fail("SemanticTableCells still hard-codes palette values")

    # Ordinary application UI controllers/utilities must not own palette hex values.
    excluded = {"documentstudio"}
    palette_pattern = re.compile(r"(?:setStyle\([^\n]*(?:#|rgb\(|rgba\()|statusIcon\([^\n]*#[0-9a-fA-F]{3,8})")
    offenders: list[str] = []
    for path in sorted(JAVA_ROOT.rglob("*.java")):
        if any(part in excluded for part in path.parts):
            continue
        source = path.read_text(encoding="utf-8", errors="ignore")
        if palette_pattern.search(source):
            offenders.append(path.relative_to(ROOT).as_posix())
    if offenders:
        fail("application UI Java still owns hard-coded palette values: " + ", ".join(offenders[:12]))

    # Exactly two CSS files stay authoritative; Phase 3 adds colour/font only, never geometry.
    css_files = sorted(p.name for p in CSS_ROOT.glob("*.css"))
    if css_files != ["dark-theme.css", "light-theme.css"]:
        fail(f"two-theme contract regressed: {css_files}")
    allowed_properties = {"-fx-text-fill", "-fx-font-weight", "-fx-icon-color"}
    for css_name in css_files:
        text = (CSS_ROOT / css_name).read_text(encoding="utf-8")
        blocks = semantic_css_blocks(text)
        semantic_css = "\n".join(blocks)
        properties = set(re.findall(r"(-fx-[\w-]+)\s*:", semantic_css))
        unexpected = sorted(properties - allowed_properties)
        if unexpected:
            fail(f"Phase 3 CSS changes geometry/unsupported presentation in {css_name}: {unexpected}")
        for colour in ALLOWED_COLOURS:
            for prefix in ("erp-field-label-colour-", "erp-table-header-colour-", "erp-kpi-label-colour-", "erp-kpi-value-colour-"):
                if prefix + colour not in semantic_css:
                    fail(f"missing {prefix}{colour} in {css_name}")
        for state in ("success", "info", "purple", "warning", "danger", "neutral"):
            if "erp-status-glyph-" + state not in semantic_css:
                fail(f"missing status glyph state {state} in {css_name}")

    print(
        "PHASE3_SEMANTIC_UI_OK "
        f"semantics={len(semantics)} field_instances={field_count} field_unique={len(field_unique)} "
        f"header_instances={header_count} header_unique={len(header_unique)} "
        f"kpi_instances={kpi_count} kpi_unique={len(kpi_unique)} css=2"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
