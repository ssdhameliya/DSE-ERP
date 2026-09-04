#!/usr/bin/env python3
"""DSE ERP 9.0.79 UI design-system release contract."""
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[1]

def t(path): return (ROOT/path).read_text(encoding='utf-8',errors='replace')
def need(cond,msg):
    if not cond:
        print('FAIL -',msg); raise SystemExit(1)

fxml=list((ROOT/'desktop/src/main/resources/fxml').rglob('*.fxml'))
need(len(fxml)==59,f'FXML count changed: {len(fxml)}')
css=sorted(p.name for p in (ROOT/'desktop/src/main/resources/css').glob('*.css'))
need(css==['dark-theme.css','light-theme.css'],f'CSS contract changed: {css}')

# FXML remains layout-only: no inline CSS and no fixed TableColumn widths.
inline=[]; fixed=[]
for p in fxml:
    s=p.read_text(encoding='utf-8',errors='ignore')
    if re.search(r'\sstyle="',s): inline.append(p.name)
    for m in re.finditer(r'<TableColumn\b([^>]*)>',s,re.S):
        if re.search(r'\b(?:prefWidth|minWidth|maxWidth)="',m.group(1)): fixed.append(p.name)
need(not inline,f'inline FXML styles remain: {sorted(set(inline))}')
need(not fixed,f'fixed FXML TableColumn widths remain: {sorted(set(fixed))}')

kpi=t('desktop/src/main/java/org/example/util/ResponsiveKpiLayoutManager.java')
need('int columns = cards.size();' in kpi,'KPI manager must use one column per card')
need('GridPane.setRowIndex(card, 0);' in kpi,'KPI manager can still wrap to a second row')
need('erp-kpi-density-dense' in kpi and 'erp-kpi-single-row' in kpi,'KPI density/single-row contract missing')
popup=t('desktop/src/main/java/org/example/util/PopupTableWorkspace.java')
need('ResponsiveKpiLayoutManager.KPI_SECTION_STYLE' in popup and 'erp-kpi-single-row' in popup,'popup metric strips are not on the KPI single-row contract')

ui=t('desktop/src/main/java/org/example/util/UiDesignSystem.java')
for token in ['erp-unified-surface','erp-control-button','erp-control-input','erp-realtime-search','erp-table-standard']:
    need(token in ui,f'UI design-system semantic missing: {token}')
for theme in css:
    s=t('desktop/src/main/resources/css/'+theme)
    for token in ['DSE ERP 9.0.79 — FINAL UI DESIGN SYSTEM','-dse-surface-1','erp-button-role-primary','erp-realtime-search','erp-table-standard']:
        need(token in s,f'{theme} missing final design-system token {token}')


# Action controls must remain completely visible even when a detail drawer
# reduces the table viewport, and dialog buttons must size from their full label.
table_layout=t('desktop/src/main/java/org/example/util/DynamicTableLayoutManager.java')
need('fitWithVisibleActionColumn' in table_layout and 'ACTION_CONTROL_MIN_WIDTH' in table_layout,
     'dynamic table layout no longer protects the visible Actions column')
need('width + 24.0' in table_layout,
     'rendered Actions controls no longer reserve table-cell chrome')
for theme in css:
    theme_text=t('desktop/src/main/resources/css/'+theme)
    need('-fx-pref-width: 124px;' not in theme_text,
         f'{theme} still forces dialog action labels into a fixed width')
    need('.modern-dialog .modern-dialog-button' in theme_text and '-fx-pref-width: -1;' in theme_text,
         f'{theme} dialog actions are not content-sized')

# Dialog/window unification.
for cls in ['OwnedDialog.java','OwnedTextInputDialog.java','OwnedChoiceDialog.java']:
    need('DialogPresentation.install(this)' in t('desktop/src/main/java/org/example/util/'+cls),f'{cls} bypasses DialogPresentation')
platform=t('desktop/src/main/java/org/example/util/PlatformUiSupport.java')
need('erp-modal-window-root' in platform,'secondary Stage windows do not use the shared modal root')

# Preserve the existing click-to-toggle table detail-drawer contract.
drawer=t('desktop/src/main/java/org/example/util/RegisterDetailDrawer.java')
need('class RegisterDetailDrawer' in drawer and 'isOpen()' in drawer and 'hideDrawer()' in drawer,'register detail drawer contract missing')
for cls in ['SalesListController.java','PurchaseListController.java','QuotationController.java','ReconSupplierController.java','PurchaseReconController.java','InventoryController.java']:
    s=t('desktop/src/main/java/org/example/controller/'+cls)
    need('isInteractiveTableTarget' in s and ('detailDrawer' in s or 'detailRow' in s),f'{cls} lost row/detail-panel interaction')

# Real-time search: every search-like FXML TextField/ComboBox must be wired through
# a text listener, inherited live filter, or the shared realtime search support.
party=t('desktop/src/main/java/org/example/controller/PartyMasterController.java')
settings=t('desktop/src/main/java/org/example/controller/SettingsController.java')
missing=[]
for p in fxml:
    s=p.read_text(encoding='utf-8',errors='ignore')
    cm=re.search(r'fx:controller="([^"]+)"',s)
    controller=cm.group(1) if cm else ''
    for m in re.finditer(r'<(TextField|ComboBox)\b([^>]*)>',s,re.S):
        tag,attrs=m.group(1),m.group(2)
        fm=re.search(r'fx:id="([^"]+)"',attrs)
        if not fm: continue
        fid=fm.group(1)
        prompt=(re.search(r'promptText="([^"]+)"',attrs) or [None,''])[1]
        styles=(re.search(r'styleClass="([^"]+)"',attrs) or [None,''])[1]
        if 'search' not in (fid+' '+prompt+' '+styles).lower() and 'find' not in (fid+' '+prompt+' '+styles).lower(): continue
        if p.name in {'Customer.fxml','Suppliers.fxml'}:
            src=party
        elif 'settings/' in str(p).replace('\\','/') and fid=='txtShortcutSearch':
            src=settings
        elif controller:
            cp=ROOT/'desktop/src/main/java'/Path(*controller.split('.')).with_suffix('.java')
            src=cp.read_text(encoding='utf-8',errors='ignore') if cp.exists() else ''
        else: src=''
        live=(bool(re.search(rf'\b{re.escape(fid)}\s*\.\s*textProperty\s*\(\)\s*\.\s*addListener',src,re.S)) or
              bool(re.search(rf'\b{re.escape(fid)}\s*\.\s*getEditor\s*\(\)\s*\.\s*textProperty\s*\(\)\s*\.\s*addListener',src,re.S)) or
              f'RealtimeSearchSupport.installRemote({fid}' in src or
              f'RealtimeSearchSupport.installLocal({fid}' in src)
        if not live: missing.append(f'{p.name}:{fid}')
need(not missing,'non-realtime search controls: '+', '.join(missing))

print(f'UI_DESIGN_SYSTEM_OK fxml={len(fxml)} css=2 realtime_search=yes kpi_single_row=yes modal_unified=yes')
