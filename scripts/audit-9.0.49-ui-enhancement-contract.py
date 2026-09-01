#!/usr/bin/env python3
"""DSE ERP 9.0.49 table-first Reporting / icon+text / theme-parity UI contract."""
from __future__ import annotations
import re, sys
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
CSS=ROOT/'desktop/src/main/resources/css'
FXML=ROOT/'desktop/src/main/resources/fxml/pages'
JAVA=ROOT/'desktop/src/main/java'
REGISTRY=ROOT/'desktop/src/main/resources/ui/semantic-registry.properties'
FAIL=[]
def need(ok,msg):
    if not ok: FAIL.append(msg)
def text(rel): return (ROOT/rel).read_text(encoding='utf-8',errors='ignore')

def props():
    out={}
    for raw in REGISTRY.read_text(encoding='utf-8').splitlines():
        line=raw.strip()
        if line and not line.startswith(('#','!')) and '=' in line:
            k,v=line.split('=',1);out[k.strip()]=v.strip()
    return out

# Two themes only and the component selectors that previously diverged are explicit in both.
themes=sorted(p.name for p in CSS.glob('*.css'))
need(themes==['dark-theme.css','light-theme.css'],f'exactly two themes required: {themes}')
for theme in themes:
    raw=(CSS/theme).read_text(encoding='utf-8')
    need(':not(' not in raw,f'unsupported CSS :not selector remains in {theme}')
    need('/*' not in raw and '*/' not in raw,f'production CSS comment remains in {theme}')
    need(not re.search(r'\.table-cell\s+\.menu-button\s*\{[^}]*-fx-(?:min|pref|max)-width',raw,re.S),f'generic TableCell MenuButton width cap remains in {theme}')
    for selector in (
        '.table-view .table-cell .menu-button.table-action-menu > .label',
        '.register-action-button > .label',
        '.approved-menu-button > .label',
        '.report-viewer-root .report-results-panel',
        '.report-category-strip-list .list-cell',
        '.bank-recon-kpi-panel.erp-kpi-single-row .bank-recon-kpi',
    ):
        need(selector in raw,f'{theme} lacks theme-parity selector {selector}')
    need('-fx-min-height: 230' not in raw,f'obsolete 230px Reporting card minimum remains in {theme}')

reports=text('desktop/src/main/resources/fxml/pages/Reports.fxml')
viewer=text('desktop/src/main/resources/fxml/pages/ReportViewer.fxml')
ET.parse(FXML/'Reports.fxml');ET.parse(FXML/'ReportViewer.fxml')
need('report-category-panel' not in reports,'Report Center full-height category rail returned')
need('orientation="HORIZONTAL"' in reports and 'report-category-strip-list' in reports,'Report Center compact horizontal category strip missing')
need('report-viewer-identity-panel' not in viewer,'separate blank Report Viewer identity panel returned')
need('VBox.vgrow="ALWAYS" styleClass="report-card,report-results-panel"' in viewer,'Report Viewer table-first results panel missing')
need('VBox.vgrow="ALWAYS" styleClass="approved-table,report-table,erp-table-profile-register"' in viewer,'Report Viewer table does not own remaining height')
need('erp-kpi-single-row' in text('desktop/src/main/resources/fxml/pages/BankStatement.fxml'),'Bank Statement 8x1 KPI marker missing')

# Reporting input captions must use the same semantic field-label path as operational screens.
for label in ('Period','From Date','To Date','Party','Item','Salesperson','Status','Format'):
    need(f'text="{label}" styleClass="field-label"' in reports,f'Reports field lacks semantic field-label: {label}')
for label in ('Period','Party','Item','Salesperson','Status','Payment','From Date','To Date','Return Status','GST Rate','Warehouse / Location','Bank Status','Min Amount','Max Amount','Group By','Sort','Direction','Rows per page'):
    need(f'text="{label}" styleClass="field-label"' in viewer,f'Report Viewer field lacks semantic field-label: {label}')
for icon_host in ('reportCenterSearchIcon','savedReportSearchIcon','scheduleSearchIcon'):
    need(f'fx:id="{icon_host}"' in reports,f'Reports search semantic icon host missing: {icon_host}')
need('fx:id="reportSearchIcon"' in viewer,'Report Viewer search semantic icon host missing')

registry=props()
for mapping in ('field.direction','field.group.by','field.sort','field.dashboard.period','field.report.categories','field.rows.per.page','field.salesperson','field.format','field.gross.margin'):
    need(mapping in registry,f'semantic registry mapping missing: {mapping}')

# Report identity and category icons must be report-specific instead of one generic icon.
reports_ctrl=text('desktop/src/main/java/org/example/controller/ReportsController.java')
viewer_ctrl=text('desktop/src/main/java/org/example/controller/ReportViewerController.java')
for token in ('private String reportSemantic(ReportDefinition def)','case "GST_TAX" -> "tax"','case "SALES_BY_CUSTOMER" -> "customer"','case "BANK_RECONCILIATION" -> "bank"'):
    need(token in reports_ctrl,f'Report Center semantic identity missing: {token}')
need('private void updateReportIdentityIcon()' in viewer_ctrl and 'case "GST_TAX" -> "tax"' in viewer_ctrl,'Report Viewer report-specific identity icon missing')

# Every production table-row action MenuButton owner must retain visible Actions text.
action_tokens=('row-actions','table-action-menu','user-action-menu','backup-row-actions','bank-row-action','reminder-action-button','approved-row-action')
offenders=[]
for path in sorted(JAVA.rglob('*.java')):
    raw=path.read_text(encoding='utf-8',errors='ignore')
    if 'new MenuButton(' not in raw or 'setCellFactory' not in raw or not any(t in raw for t in action_tokens):
        continue
    has_text = 'new MenuButton("Actions")' in raw or '.setText("Actions")' in raw
    has_icon = 'compactIcon("actions"' in raw or 'IconFactory.decorateActionMenu(' in raw
    if not (has_text and has_icon):
        offenders.append(path.relative_to(ROOT).as_posix())
need(not offenders,'table action owner lacks icon + visible Actions text: '+', '.join(offenders[:12]))
icon_factory=text('desktop/src/main/java/org/example/util/IconFactory.java')
need('menu.setText("Actions")' in icon_factory,'shared action decorator no longer enforces Actions text')
manager=text('desktop/src/main/java/org/example/util/DynamicTableLayoutManager.java')
need('ACTION_CONTROL_MIN_WIDTH = 112.0' in manager and 'renderedCellControlWidth(table, column)' in manager,'dynamic action column does not measure icon+text control')

if FAIL:
    print('UI_ENHANCEMENT_9_0_47_FAIL')
    for item in FAIL: print(' -',item)
    sys.exit(1)
print('UI_ENHANCEMENT_9_0_47_OK reporting=table-first report_icons=semantic fields=semantic action=icon+text bank_kpi=8x1 theme_parity=light+dark css=2')
