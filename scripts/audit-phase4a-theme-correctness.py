#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
light_path = root / 'desktop/src/main/resources/css/light-theme.css'
dark_path = root / 'desktop/src/main/resources/css/dark-theme.css'
light = light_path.read_text(encoding='utf-8')
dark = dark_path.read_text(encoding='utf-8')
java = (root / 'desktop/src/main/java/org/example/util/UiDesignSystem.java').read_text(encoding='utf-8')

light_final = light[light.rfind('PHASE 4A.1 REGRESSION SHIELD (LIGHT)'):]
dark_final = dark[dark.rfind('PHASE 4A.1 REGRESSION SHIELD (DARK)'):]

checks = {
    'light phase4a base ownership': 'PHASE 4A THEME CORRECTNESS OWNERSHIP (LIGHT)' in light,
    'dark phase4a base ownership': 'PHASE 4A THEME CORRECTNESS OWNERSHIP (DARK)' in dark,
    'light regression shield': 'PHASE 4A.1 REGRESSION SHIELD (LIGHT)' in light,
    'dark regression shield': 'PHASE 4A.1 REGRESSION SHIELD (DARK)' in dark,
    'auth panels excluded from generic surfaces': '"auth-", "splash-", "login-", "brand-panel"' in java,
    'light auth brand gradient owned': 'auth-standard-shell .auth-unified-brand-panel' in light_final and 'linear-gradient(to bottom right, #0b3f86' in light_final,
    'light auth brand text readable': 'auth-standard-shell .auth-brand-title { -fx-text-fill: #ffffff; }' in light_final,
    'dark auth brand gradient owned': 'auth-standard-shell .auth-unified-brand-panel' in dark_final and 'linear-gradient(to bottom right, #081423' in dark_final,
    'light continuous table header': '.erp-table-standard .column-header-background' in light_final and '.erp-table-standard .column-header,' in light_final and '-fx-background-color: transparent;' in light_final,
    'dark continuous table header': '.erp-table-standard .column-header-background' in dark_final and '.erp-table-standard .column-header,' in dark_final and '-fx-background-color: transparent;' in dark_final,
    'light selected full-row purple': '-fx-background-color: #6d28d9;' in light_final and '.table-row-cell:selected .table-cell.status-positive' in light_final,
    'dark selected full-row purple': '-fx-background-color: #6d28d9;' in dark_final and '.table-row-cell:selected .table-cell.status-positive' in dark_final,
    'selected cells transparent light': '.table-row-cell:selected .table-cell' in light_final and '-fx-background-color: transparent;' in light_final,
    'selected cells white text light': '-fx-text-background-color: #ffffff;' in light_final,
    'selected cells white text dark': '-fx-text-background-color: #ffffff;' in dark_final,
    'light text area internals unified': '.erp-control-input.text-area .scroll-pane' in light_final and '.erp-control-input.text-area .content' in light_final,
    'light datepicker internals unified': '.erp-control-input.date-picker > .text-field' in light_final and '.erp-control-input.date-picker > .arrow-button' in light_final,
    'light combobox internals unified': '.erp-control-input.combo-box-base > .list-cell' in light_final and '.erp-control-input.combo-box-base > .arrow-button' in light_final,
    'dark composite controls unified': '.erp-control-input.text-area .scroll-pane' in dark_final and '.erp-control-input.combo-box-base > .arrow-button' in dark_final,
    'choicebox decorated': 'node instanceof ChoiceBox<?>' in java,
    'spinner decorated': 'node instanceof Spinner<?>' in java,
    'colorpicker decorated': 'node instanceof ColorPicker' in java,
    'no css important escape hatches light': '!important' not in light,
    'no css important escape hatches dark': '!important' not in dark,
    'two css runtime files': len(list((root/'desktop/src/main/resources/css').glob('*.css'))) == 2,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
print(f'PHASE4A_THEME_CORRECTNESS checks={len(checks)} pass={len(checks)-len(failed)} fail={len(failed)}')
if failed:
    sys.exit(1)
