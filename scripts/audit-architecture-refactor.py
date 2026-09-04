#!/usr/bin/env python3
"""Behavior-preserving architecture seams introduced in 9.0.76."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def t(p): return (ROOT/p).read_text(encoding='utf-8',errors='replace')
def need(cond,msg):
    if not cond:
        print('FAIL -',msg); sys.exit(1)
files=[
'desktop/src/main/java/org/example/importing/ImportModuleRegistry.java',
'desktop/src/main/java/org/example/importing/ImportMappingSupport.java',
'desktop/src/main/java/org/example/importing/ImportTemplateService.java',
'desktop/src/main/java/org/example/importing/ImportMergePolicy.java',
'desktop/src/main/java/org/example/importing/ImportValueParser.java',
'desktop/src/main/java/org/example/importing/ImportResultReportService.java',
'desktop/src/main/java/org/example/importing/ImportResultPolicy.java',
'desktop/src/main/java/org/example/document/DocumentLookupPolicy.java',
'desktop/src/main/java/org/example/service/SettingsAssetPreviewLoader.java',
'desktop/src/main/java/org/example/config/SettingsFieldSupport.java',
'desktop/src/main/java/org/example/documentstudio/service/ExcelWorkbookHistory.java',
'desktop/src/main/java/org/example/documentstudio/service/ExcelSelectionPolicy.java',
'desktop/src/main/java/org/example/documentstudio/service/PdfStudioHistory.java',
'desktop/src/main/java/org/example/documentstudio/service/PdfStudioGeometryPolicy.java',
'desktop/src/main/java/org/example/util/ProfessionalDocumentFormatSupport.java',
]
for f in files: need((ROOT/f).is_file(),f'missing architecture component: {f}')
imports=t('desktop/src/main/java/org/example/controller/ImportController.java')
sales=t('desktop/src/main/java/org/example/controller/SalesController.java')
purchase=t('desktop/src/main/java/org/example/controller/PurchaseController.java')
settings=t('desktop/src/main/java/org/example/controller/SettingsController.java')
excel=t('desktop/src/main/java/org/example/documentstudio/controller/ExcelDesignerController.java')
pdf=t('desktop/src/main/java/org/example/documentstudio/controller/PdfStudioController.java')
renderer=t('desktop/src/main/java/org/example/util/ProfessionalDocumentRenderer.java')
need('ImportModuleRegistry.' in imports and 'ImportMappingSupport.' in imports and 'ImportResultPolicy.' in imports,'ImportController is not delegating to extracted policies')
need('DocumentLookupPolicy.' in sales and 'DocumentLookupPolicy.' in purchase,'Sales/Purchase shared lookup policy not active')
need('SettingsFieldSupport.' in settings and 'SettingsAssetPreviewLoader' in settings,'Settings extracted helpers not active')
need('ExcelWorkbookHistory' in excel and 'ExcelSelectionPolicy.' in excel,'Excel Studio extracted history/selection not active')
need('PdfStudioHistory' in pdf and 'PdfStudioGeometryPolicy.' in pdf,'PDF Studio extracted history/geometry not active')
need('ProfessionalDocumentFormatSupport.' in renderer,'professional renderer formatting extraction not active')
need('APP_VERSION = "9.0.76"' in t('shared/src/main/java/org/example/shared/RuntimeContract.java'),'runtime version is not 9.0.76')
css=sorted(p.name for p in (ROOT/'desktop/src/main/resources/css').glob('*.css'))
need(css==['dark-theme.css','light-theme.css'],f'CSS contract changed: {css}')
print(f'ARCHITECTURE_REFACTOR_OK components={len(files)} version=9.0.76 css=2')
