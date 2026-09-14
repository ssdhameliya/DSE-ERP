#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
def text(path): return (ROOT/path).read_text(encoding='utf-8',errors='replace')
def req(ok,msg):
    if not ok:
        print('FAIL -',msg); failures.append(msg)
    else: print('PASS -',msg)

failures=[]
update=text('desktop/src/main/java/org/example/update/UpdateService.java')
dialogs=text('desktop/src/main/java/org/example/update/UpdateDialogs.java')
icons=text('desktop/src/main/java/org/example/util/IconFactory.java')
item=text('desktop/src/main/resources/fxml/pages/Itemdialog.fxml')

req('downloadVerified(UpdateRelease release' in update,'updater owns verified download entry point')
req('cachedInstallerMatches(target,asset.size(),checksum)' in update,'cached installer is SHA-256 checked before reuse')
req('UPDATE_CACHE_CHECKSUM_MISMATCH' in update and 'purgeCachedInstaller(target)' in update,'stale final cache is purged automatically')
req('UPDATE_DOWNLOADED_CHECKSUM_MISMATCH' in update and 'verificationAttempt<=2' in update,'fresh checksum mismatch forces one clean retry')
req('Files.deleteIfExists(partialPath(target))' in update,'stale .part is purged with the final installer')
req('Files.isRegularFile(target) && (asset.size()<=0 || Files.size(target)==asset.size())' not in update,'unsafe size-only cache trust is absent')
req(dialogs.count('service.downloadVerified(release, asset') >= 2,'pre-login and normal updater flows use verified download')
req('service.download(asset' not in dialogs,'dialogs cannot bypass verified download')

for token,label in [
    ('node instanceof ScrollPane scroll','ScrollPane'),
    ('node instanceof TabPane tabs','TabPane'),
    ('node instanceof TitledPane titled','TitledPane'),
    ('node instanceof Accordion accordion','Accordion'),
    ('node instanceof SplitPane split','SplitPane'),
    ('IdentityHashMap<Node, Boolean> visited','cycle-safe logical traversal'),
]: req(token in icons,f'semantic decorator covers {label}')
req('<ScrollPane' in item and 'styleClass="field-label"' in item,'Add Item reproducer remains a logical-content semantic form')

logical=[]
for f in (ROOT/'desktop/src/main/resources/fxml').rglob('*.fxml'):
    t=f.read_text(encoding='utf-8',errors='ignore')
    if any(x in t for x in ('<ScrollPane','<TabPane','<TitledPane','<Accordion','<SplitPane')) and 'field-label' in t:
        logical.append(str(f.relative_to(ROOT)))
print(f'LOGICAL_SEMANTIC_SURFACES {len(logical)}')

if failures:
    print('UPDATE_SEMANTIC_LOGICAL_CONTRACT_FAIL')
    sys.exit(1)
print('UPDATE_SEMANTIC_LOGICAL_CONTRACT_OK cache=sha256 logical=central')
