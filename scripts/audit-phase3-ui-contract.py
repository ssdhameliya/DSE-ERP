#!/usr/bin/env python3
from pathlib import Path
import re, sys
ROOT = Path(__file__).resolve().parents[1]
DESKTOP = ROOT / 'desktop'
JAVA = DESKTOP / 'src/main/java'
FXML = DESKTOP / 'src/main/resources/fxml'
CSS = DESKTOP / 'src/main/resources/css'
checks = []

def check(name, ok, detail=''):
    checks.append((name, bool(ok), detail))
    print(('PASS' if ok else 'FAIL') + ': ' + name + (f' — {detail}' if detail else ''))
css_files = sorted(CSS.glob('*.css'))
check('exactly two runtime CSS files', [p.name for p in css_files] == ['dark-theme.css', 'light-theme.css'], ', '.join((p.name for p in css_files)))
mgr = (JAVA / 'org/example/util/DynamicTableLayoutManager.java').read_text()
check('table minimum prevents legacy 44px fragments', 'MIN_READABLE_COLUMN = 58.0' in mgr)
check('semantic + no-break readable minimum policy exists', 'readableMinimum(' in mgr and 'longestHeaderTokenWidth(' in mgr and ('FontWeight.EXTRA_BOLD' in mgr) and ('semanticFloor = 86.0' in mgr))
check('action fitting no longer shrinks below readable minimum', 'double compact = measure.minimum();' in mgr and 'measure.minimum() * 0.72' not in mgr)
check('Actions remain protected', 'ACTION_CONTROL_MIN_WIDTH = 132.0' in mgr)
check('unconstrained resize keeps horizontal scroll available', 'UNCONSTRAINED_RESIZE_POLICY' in mgr)
icons = (JAVA / 'org/example/util/IconFactory.java').read_text()
check('table headers distinguish single and multi-word wrapping', 'erp-table-header-multi-word' in icons and 'erp-table-header-single-word' in icons and ('title.setWrapText(multiWord);' in icons))
check('wrapped header label can shrink inside column', 'title.setMinWidth(0);' in icons and 'HBox.setHgrow(title, Priority.ALWAYS);' in icons)
settings_files = [FXML / 'pages/settings/CompanySettingsPanel.fxml', FXML / 'pages/settings/InvoiceSettingsPanel.fxml', FXML / 'pages/settings/PaymentSettingsPanel.fxml']
combined = '\n'.join((p.read_text() for p in settings_files))
check('settings asset controls use wrapping action rows', combined.count('styleClass="settings-asset-actions"') == 5, str(combined.count('styleClass="settings-asset-actions"')))
check('legacy non-wrapping asset action rows removed', not re.search('<HBox spacing="8">\\s*<Button text="Add / Replace"', combined, re.S))
for theme in css_files:
    text = theme.read_text()
    check(f'{theme.name}: Phase 3 ownership layer present', 'PHASE 3 READABILITY + RESPONSIVE TABLE OWNERSHIP' in text)
    check(f'{theme.name}: 52px readable header row', '-fx-pref-height: 52px;' in text)
    check(f'{theme.name}: horizontal scrollbar remains usable', '.virtual-flow .scroll-bar:horizontal' in text)
    blocks = re.findall('([^{}]+)\\{([^{}]*)\\}', text, re.S)
    seen = set()
    dup = 0
    for sel, body in blocks:
        key = (' '.join(sel.split()), ' '.join(body.split()))
        if key in seen:
            dup += 1
        seen.add(key)
    check(f'{theme.name}: no exact duplicate CSS blocks', dup == 0, f'duplicates={dup}')
viol = []
for p in FXML.rglob('*.fxml'):
    for m in re.finditer('<TableColumn\\b[^>]*\\b(?:prefWidth|minWidth|maxWidth)\\s*=', p.read_text(), re.I):
        viol.append(str(p.relative_to(ROOT)))
check('FXML TableColumns remain free of fixed width ownership', not viol, ', '.join(viol[:5]))

# Central viewport lifecycle: tables/KPIs must be discovered and reflowed
# from shared infrastructure rather than screen-specific pulse workarounds.
enhancer = (JAVA / 'org/example/util/ProfessionalUiEnhancer.java').read_text()
check('logical control content is globally enhanced', all(token in enhancer for token in ('ScrollPane', 'TabPane', 'enhanceLogicalContent(', 'installTabContentEnhancement(', 'viewportBoundsProperty()')))
check('cached roots request central viewport reflow', 'UiViewportLayoutCoordinator.request(root);' in enhancer)
check('table layout rejects stale generations', all(token in mgr for token in ('erp.table.dynamic-layout.generation', 'bindViewportNodes(', 'VIEWPORT_STABILITY_TOLERANCE', 'MAX_EXPECTED_SKIN_CHROME')))
check('nested double-runLater table workaround removed', 'Platform.runLater(() -> Platform.runLater(() -> requestLayout(table)))' not in mgr)
kpi = (JAVA / 'org/example/util/ResponsiveKpiLayoutManager.java').read_text()
check('KPI density uses actual usable width', all(token in kpi for token in ('usableWidth(', 'DENSE_CARD_THRESHOLD', 'COMPACT_CARD_THRESHOLD', 'requestLayoutIn(Node root)')))
coordinator = (JAVA / 'org/example/util/UiViewportLayoutCoordinator.java').read_text()
register = (JAVA / 'org/example/util/RegisterUiSupport.java').read_text()
check('one viewport coordinator owns geometry notifications', 'DynamicTableLayoutManager.requestLayoutIn(root);' in coordinator and 'ResponsiveKpiLayoutManager.requestLayoutIn(root);' in coordinator)
check('register shell/drawer helper no longer forces CSS/layout pulses', 'UiViewportLayoutCoordinator.request' in register and 'applyCss()' not in register and 'autosize()' not in register and 'Platform.runLater(() -> { pass.run(); Platform.runLater(pass); })' not in register)
for theme in css_files:
    theme_text = theme.read_text()
    check(f'{theme.name}: Reports KPI width is not CSS-owned', '.reports-workspace .erp-kpi-section .report-kpi-card {\n-fx-min-width: 0;' not in theme_text and '-fx-pref-width: 0;' not in theme_text[theme_text.find('.reports-workspace .erp-kpi-section .report-kpi-card'):theme_text.find('.reports-workspace .report-kpi-card .metric-value')])
fxml_count = len(list(FXML.rglob('*.fxml')))
table_count = 0
column_count = 0
for p in FXML.rglob('*.fxml'):
    t = p.read_text()
    table_count += len(re.findall('<TableView\\b', t))
    column_count += len(re.findall('<TableColumn\\b', t))
print(f'PHASE3_INVENTORY fxml={fxml_count} tables={table_count} columns={column_count} css={len(css_files)}')
failed = [n for n, ok, d in checks if not ok]
if failed:
    print('PHASE3_UI_CONTRACT_FAIL count=' + str(len(failed)))
    sys.exit(1)
print('PHASE3_UI_CONTRACT_OK checks=' + str(len(checks)))
