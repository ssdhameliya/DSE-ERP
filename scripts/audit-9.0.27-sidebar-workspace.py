from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]

def text(rel):
    return (root / rel).read_text(encoding='utf-8', errors='replace')

def req(cond, msg):
    if not cond:
        print('FAIL:', msg)
        sys.exit(1)
    print('PASS:', msg)

fxml = text('desktop/src/main/resources/fxml/pages/Dashboard.fxml')
controller = text('desktop/src/main/java/org/example/controller/DashboardController.java')
shortcuts = text('desktop/src/main/java/org/example/shortcut/ShortcutRegistry.java')
runtime = text('shared/src/main/java/org/example/shared/RuntimeContract.java')
props = text('server/src/main/resources/application.properties')
app = text('desktop/src/main/resources/app-version.properties')
release = text('desktop/src/main/java/org/example/update/ReleaseHighlights.java')

req('APP_VERSION = "9.0.28"' in runtime and 'BUILD_REVISION = "9.0.28"' in runtime,
    'desktop/server runtime identity is 9.0.28')
req('dse.app.version=9.0.28' in props and 'dse.build.revision=9.0.28' in props and 'version=9.0.28' in app,
    'server and desktop resource identity is 9.0.28')
req('fx:id="btnSidebarToggle"' in fxml and 'onAction="#toggleSidebar"' in fxml,
    'global Dashboard top bar owns a sidebar toggle button')
req('fx:id="sidebarRoot"' in fxml and 'fx:id="contentPane"' in fxml,
    'toggle operates on the shell sidebar beside the shared content host')
req('sidebarRoot.setManaged(visible);' in controller and 'sidebarRoot.setVisible(visible);' in controller,
    'sidebar releases/restores layout width using managed + visible state')
req('applySidebarVisibility(loadSidebarVisiblePreference(), false);' in controller,
    'saved sidebar preference is restored when the Dashboard shell initializes')
req('ConfigManager.set(sidebarPreferenceKey(), Boolean.toString(visible));' in controller,
    'sidebar choice is persisted locally when the user toggles it')
req('"ui.user." + safe + ".sidebar.visible"' in controller,
    'sidebar visibility preference is isolated per signed-in user')
req('TOGGLE_SIDEBAR("global.toggleSidebar", "Show / Hide Sidebar", "Application Actions", "Shortcut+B"' in shortcuts,
    'Ctrl/Cmd+B is the default configurable global sidebar shortcut')
req('action==Action.TOGGLE_SIDEBAR' in shortcuts and 'allowText' in shortcuts,
    'sidebar shortcut remains available while focus is inside ordinary text/search controls')
req('case TOGGLE_SIDEBAR -> toggleSidebar();' in controller,
    'central shell shortcut dispatcher invokes the exact same sidebar toggle action as the button')
req('ShortcutRegistry.display(Action.TOGGLE_SIDEBAR)' in controller,
    'sidebar tooltip reflects the configured shortcut rather than hard-coding only the default')
req('DSE ERP 9.0.28' in release and 'Show / Hide Sidebar' in release,
    '9.0.28 release highlights document the workspace/sidebar feature')

print('PASS: DSE ERP 9.0.28 Sidebar workspace + shortcut contract')
