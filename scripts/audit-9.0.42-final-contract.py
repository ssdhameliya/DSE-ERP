#!/usr/bin/env python3
"""DSE ERP 9.0.42 final IntelliJ/reporting/scheduler contract."""
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
FAIL=[]
def need(ok,msg):
    if not ok: FAIL.append(msg)
def text(rel): return (ROOT/rel).read_text(encoding='utf-8',errors='ignore')

# Release identity / full runtime shell.
need('<version>9.0.42</version>' in text('pom.xml') and '<dse.phase>9.0.42</dse.phase>' in text('pom.xml'),'root Maven identity is not 9.0.42')
need('APP_VERSION = "9.0.42"' in text('shared/src/main/java/org/example/shared/RuntimeContract.java'),'desktop/shared runtime identity is not 9.0.42')
need('dse.app.version=9.0.42' in text('server/src/main/resources/application.properties'),'server runtime identity is not 9.0.42')
need('runtime.phase=9.0.42' in text('runtime/runtime-manifest.properties'),'bundled runtime manifest is not 9.0.42')
pg=ROOT/'runtime/postgresql'
need(pg.exists() and sum(1 for p in pg.rglob('*') if p.is_file())>100,'full bundled PostgreSQL runtime shell is missing')

# Exactly two CSS files and no legacy stylesheet reference anywhere in production resources/code.
css=sorted(p.name for p in (ROOT/'desktop/src/main/resources/css').glob('*.css'))
need(css==['dark-theme.css','light-theme.css'],f'exactly two CSS files required: {css}')
legacy=('global-search-v3.css','notification-center-v3.css','shortcut-manager.css','ui-components.css','ui-layout.css')
prod=[]
for root in [ROOT/'desktop/src/main/java',ROOT/'desktop/src/main/resources']:
    for p in root.rglob('*'):
        if p.is_file() and p.suffix.lower() in {'.java','.fxml','.properties','.css'}:
            prod.append(p)
for p in prod:
    raw=p.read_text(encoding='utf-8',errors='ignore')
    for name in legacy:
        need(name not in raw,f'legacy CSS reference remains in {p.relative_to(ROOT)}: {name}')

# Reporting FXML: no per-report KPI strip, one unified Export menu including CSV, and dashboard CSV.
viewer=ROOT/'desktop/src/main/resources/fxml/pages/ReportViewer.fxml'
reports=ROOT/'desktop/src/main/resources/fxml/pages/Reports.fxml'
ET.parse(viewer); ET.parse(reports)
v=viewer.read_text(encoding='utf-8'); r=reports.read_text(encoding='utf-8')
need('fx:id="metricPane"' not in v and 'report-metric-strip' not in v,'individual Report Viewer KPI strip must be removed')
for action in ('#exportPdf','#exportExcel','#exportCsv','#printReport'):
    need(v.count(f'onAction="{action}"')==1,f'Report Viewer must expose {action} exactly once')
need('fx:id="miCsv" text="Export CSV" onAction="#exportCsv"' in r,'Reports Dashboard CSV export is missing')

# Action controls: icon + text, never global graphic-only table action behavior.
for theme in css:
    raw=text('desktop/src/main/resources/css/'+theme)
    need(not re.search(r'\.icon-button\s*,\s*\.table-action-button\s*\{[^}]*GRAPHIC_ONLY',raw,re.S),f'{theme} still makes table actions graphic-only')
    need(re.search(r'\.table-action-button\s*\{[^}]*-fx-content-display\s*:\s*LEFT',raw,re.S) is not None,f'{theme} lacks icon+text table action rule')
table_mgr=text('desktop/src/main/java/org/example/util/DynamicTableLayoutManager.java')
need('renderedCellControlWidth(table, column)' in table_mgr and 'header + 38.0' in table_mgr,'dynamic action-column rendered-control floor is missing')

# Responsive KPI must wrap before cards become cramped; individual viewer no longer enrolled.
kpi=text('desktop/src/main/java/org/example/util/ResponsiveKpiLayoutManager.java')
need('MIN_COMFORTABLE_CARD = 220.0' in kpi,'responsive KPI comfortable width floor is not 220px')
need('GridPane.setRowIndex(card, i / columns)' in kpi,'responsive KPI wrapping is missing')

# Dashboard/Viewer CSV implementation.
reports_ctrl=text('desktop/src/main/java/org/example/controller/ReportsController.java')
service=text('desktop/src/main/java/org/example/service/BusinessReportService.java')
need('@FXML private void exportCsv(){exportDashboard("CSV Report","business-report.csv","csv");}' in reports_ctrl,'dashboard CSV controller action missing')
need('public void exportCsv(Path file, LocalDate from, LocalDate to)' in service,'BusinessReportService CSV export missing')

# Scheduled reports: combined PDF + XLSX must canonicalize to PDF_XLSX and generate both artifacts.
schedule=text('server/src/main/java/org/example/server/reporting/ReportScheduleService.java')
need('String format = canonicalToken(request.format());' in schedule,'scheduled format does not use canonical token normalization')
need('replaceAll("[^A-Z0-9]+", "_")' in schedule and 'replaceAll("_+", "_")' in schedule,'combined output normalization is not collapse-safe')
need('case "PDF_XLSX" -> {' in schedule,'combined PDF_XLSX execution branch missing')
need('exporter.pdf(pdf, result, visible); exporter.excel(xlsx, result, visible); files.add(pdf); files.add(xlsx);' in schedule,'combined schedule does not generate and attach both artifacts')
need('format.getItems().setAll("PDF","XLSX","PDF + XLSX","CSV")' in reports_ctrl,'scheduled-report combined output option missing from UI')

if FAIL:
    print('FINAL_9_0_42_FAIL')
    for x in FAIL: print(' -',x)
    sys.exit(1)
print('FINAL_9_0_42_OK css=2 report_viewer_kpi=removed action=icon+text dashboard_csv=yes schedule_pdf_xlsx=both full_runtime=yes')
