#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'desktop/src/main/java/org/example'
CONTROLLERS = JAVA / 'controller'
CSS = ROOT / 'desktop/src/main/resources/css'
FAIL = []


def read(path: Path) -> str:
    if not path.exists():
        FAIL.append(f'missing {path.relative_to(ROOT)}')
        return ''
    return path.read_text(encoding='utf-8', errors='ignore')


def fxml_table_columns(source: str) -> set[str]:
    result: set[str] = set()
    for match in re.finditer(r'@FXML\s+(?:private|protected|public)?\s*TableColumn\s*<[^;]+;', source, re.S):
        segment = match.group(0)
        if '>' not in segment:
            continue
        names = segment.rsplit('>', 1)[1].rsplit(';', 1)[0]
        for part in names.split(','):
            name = re.match(r'\s*(\w+)', part)
            if name:
                result.add(name.group(1))
    return result


css_files = sorted(p.name for p in CSS.glob('*.css'))
if css_files != ['dark-theme.css', 'light-theme.css']:
    FAIL.append(f'exactly two CSS files required, found {css_files}')

settings = read(CONTROLLERS / 'SettingsController.java')
settings_lines = len(settings.splitlines())
if settings_lines > 2000:
    FAIL.append(f'SettingsController exceeds Phase 6 ceiling: {settings_lines} > 2000 lines')
for legacy in (
    'private String shortcutUiCategory(',
    'private String shortcutCategoryAccent(',
    'private String shortcutCategoryIcon(',
    'private String shortcutScopeHint(',
    'private String shortcutDescription(',
    'private String normalizeShortcut(',
    'private AssetStoreResult storeSelectedImage(',
    'private Image loadPreviewImage(',
    'private boolean validatePaymentDetails() {\n\n        String upi',
):
    if legacy in settings:
        FAIL.append(f'SettingsController still owns extracted Phase 6 responsibility: {legacy}')

for rel, tokens in {
    'desktop/src/main/java/org/example/service/SettingsAssetService.java': (
        'public static Stored store(', 'ATOMIC_MOVE', 'ConfigManager.set(configKey', 'loadPreview('
    ),
    'desktop/src/main/java/org/example/shortcut/SettingsShortcutSupport.java': (
        'public static List<Action> managerActions()', 'scopeHint(', 'description(', 'normalize('
    ),
    'desktop/src/main/java/org/example/config/SettingsValidationSupport.java': (
        'validatePayment(', 'emailPortError('
    ),
}.items():
    source = read(ROOT / rel)
    for token in tokens:
        if token not in source:
            FAIL.append(f'{rel}: missing {token!r}')

explicit_header_methods = 0
static_fxml_header_calls = []
all_header_calls = 0
for path in sorted(CONTROLLERS.glob('*.java')):
    source = read(path)
    explicit_header_methods += len(re.findall(r'\bconfigureExplicitTableHeaderIcons\s*\(', source))
    injected = fxml_table_columns(source)
    for match in re.finditer(r'IconFactory\.applyTableHeaderIcon\(\s*(\w+)\s*,', source):
        all_header_calls += 1
        if match.group(1) in injected:
            line = source.count('\n', 0, match.start()) + 1
            static_fxml_header_calls.append(f'{path.name}:{line}:{match.group(1)}')

if explicit_header_methods:
    FAIL.append(f'legacy explicit FXML header setup methods remain: {explicit_header_methods}')
if static_fxml_header_calls:
    FAIL.append('FXML-injected TableColumns still decorated in controllers: ' + ', '.join(static_fxml_header_calls[:20]))

shared = read(JAVA / 'util/SharedUiFramework.java')
if 'TablePerformanceOptimizer' in shared:
    FAIL.append('SharedUiFramework still launches a separate table-performance traversal')
optimizer = read(JAVA / 'util/TablePerformanceOptimizer.java')
if 'javafx.scene.Parent' in optimizer or 'apply(Node root)' in optimizer:
    FAIL.append('TablePerformanceOptimizer still owns a recursive scene-graph traversal')
if 'public static void optimize(TableView<?> table)' not in optimizer:
    FAIL.append('TablePerformanceOptimizer does not expose the Phase 6 single-table optimizer')
enhancer = read(JAVA / 'util/ProfessionalUiEnhancer.java')
if 'TablePerformanceOptimizer.optimize(table);' not in enhancer:
    FAIL.append('ProfessionalUiEnhancer does not invoke single-table optimization at discovery time')

# Phase 6 is presentation/ownership cleanup only. The established structural
# freeze remains the authority for FXML events, navigation, dialogs and schema.
if FAIL:
    print('FAIL: 9.0.44 Phase 6 controller/UI ownership contract')
    for failure in FAIL:
        print(' -', failure)
    sys.exit(1)

controller_lines = sum(len(read(p).splitlines()) for p in CONTROLLERS.glob('*.java'))
print(
    'PHASE6_CONTROLLER_CLEANUP_OK '
    f'settings_lines={settings_lines} controller_lines={controller_lines} '
    f'programmatic_header_calls={all_header_calls} static_fxml_header_calls=0 css={len(css_files)}'
)
