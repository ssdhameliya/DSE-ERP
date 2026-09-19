package org.example.ui;

import org.example.util.ProfessionalUiEnhancer;
import org.example.util.UiSemanticRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-screen release contract for the shared shell/icon/table presentation layer.
 * These checks intentionally protect ownership boundaries rather than one business page.
 */
class SharedUiOwnershipContractTest {
    private static final Pattern TABLE_COLUMN = Pattern.compile("<TableColumn\\b([^>]*)>", Pattern.DOTALL);
    private static final Pattern TEXT = Pattern.compile("\\btext=\\\"([^\\\"]*)\\\"");

    @Test
    void topBarUsesCompactSemanticGraphicsInsteadOfNavigationTiles() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/example/controller/DashboardController.java"));
        assertTrue(source.contains("private void applyShellSemanticIcons()"));
        for (String control : List.of("btnSidebarToggle", "btnReminderTop", "btnNotifications",
                "btnEmailCenter", "btnWhatsappCenter", "btnShortcutInfo", "btnTheme")) {
            assertTrue(source.contains("UiActionIcons.apply(" + control), control + " must use compact shared action graphics");
        }
        assertFalse(source.contains("btnReminderTop.setGraphic(IconFactory.icon"));
        assertFalse(source.contains("btnNotifications.setGraphic(IconFactory.icon"));
        assertFalse(source.contains("btnEmailCenter.setGraphic(IconFactory.icon"));
        assertFalse(source.contains("btnWhatsappCenter.setGraphic(IconFactory.icon"));
        assertFalse(source.contains("btnTheme.setGraphic(IconFactory.icon"));
        assertFalse(source.contains("btnSidebarToggle.setGraphic(IconFactory.icon"));
    }

    @Test
    void iconOnlyTopBarButtonsOwnEnoughContentWidthAndTableHeadersDoNotForceBlue() throws Exception {
        for (String theme : List.of("light-theme.css", "dark-theme.css")) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            String compact = css.replaceAll("\\s+", " ");
            assertTrue(compact.matches("(?s).*\\.erp-topbar \\.top-icon \\{[^}]*-fx-padding: 0;[^}]*-fx-alignment: CENTER;[^}]*}.*"),
                    theme + " must keep icon-only shell controls centered without width-stealing padding");
            assertFalse(compact.matches("(?s).*\\.erp-icon-table-column \\.erp-action-glyph \\{[^}]*-fx-icon-color:[^}]*}.*"),
                    theme + " must let the semantic glyph class own each table-header colour");
            for (String colour : List.of("blue", "green", "orange", "purple", "pink", "teal", "indigo")) {
                assertTrue(css.contains(".erp-action-glyph-" + colour), theme + " missing semantic glyph colour " + colour);
                assertTrue(css.contains(".erp-table-header-colour-" + colour), theme + " missing semantic header label colour " + colour);
            }
        }
    }

    @Test
    void everyVisibleFxmlTableHeadingResolvesToOneSemantic() throws Exception {
        Method fallback = ProfessionalUiEnhancer.class.getDeclaredMethod("headerSemantic", String.class, String.class);
        fallback.setAccessible(true);
        Method finalFallback = ProfessionalUiEnhancer.class.getDeclaredMethod("fallbackHeaderSemantic", String.class, String.class);
        finalFallback.setAccessible(true);
        List<String> missing = new ArrayList<>();
        Path root = Path.of("src/main/resources/fxml");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".fxml")).toList()) {
                String xml = Files.readString(file);
                Matcher columns = TABLE_COLUMN.matcher(xml);
                while (columns.find()) {
                    String attrs = columns.group(1);
                    Matcher text = TEXT.matcher(attrs);
                    if (!text.find()) continue;
                    String heading = text.group(1).replace("&amp;", "&").trim();
                    if (heading.isBlank()) continue;
                    String semantic = UiSemanticRegistry.headerSemantic(heading);
                    if (semantic == null) semantic = (String) fallback.invoke(null, heading, "");
                    if (semantic == null) semantic = (String) finalFallback.invoke(null, heading, "");
                    if (semantic == null) missing.add(root.relativize(file) + " :: " + heading);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Table headings without central semantic ownership: " + missing);
    }
    @Test
    void semanticRegistryNeverReferencesUndefinedSemanticAndThemesStayStructurallyAligned() throws Exception {
        Path registry = Path.of("src/main/resources/ui/semantic-registry.properties");
        java.util.Properties values = new java.util.Properties();
        try (var in = Files.newInputStream(registry)) { values.load(in); }
        java.util.Set<String> defined = new java.util.HashSet<>();
        for (String name : values.stringPropertyNames()) {
            if (name.startsWith("semantic.") && name.endsWith(".icon")) {
                String semantic = name.substring("semantic.".length(), name.length() - ".icon".length());
                if (values.getProperty("semantic." + semantic + ".colour") != null) defined.add(semantic);
            }
        }
        List<String> undefined = new ArrayList<>();
        for (String name : values.stringPropertyNames()) {
            if (!(name.startsWith("field.") || name.startsWith("header.") || name.startsWith("kpi."))) continue;
            String semantic = values.getProperty(name).trim();
            if (!defined.contains(semantic)) undefined.add(name + "=" + semantic);
        }
        assertTrue(undefined.isEmpty(), "Registry mappings reference undefined semantics: " + undefined);

        String light = Files.readString(Path.of("src/main/resources/css/light-theme.css"));
        String dark = Files.readString(Path.of("src/main/resources/css/dark-theme.css"));
        for (String shared : List.of(".erp-topbar .top-icon", ".theme-switch", ".erp-table-header-content",
                ".erp-table-header-label", ".notification-button-wrap", ".shell-clock-icon")) {
            assertTrue(light.contains(shared), "Light theme missing shared selector " + shared);
            assertTrue(dark.contains(shared), "Dark theme missing shared selector " + shared);
        }
        for (String colour : List.of("blue", "green", "orange", "purple", "pink", "teal", "indigo")) {
            assertEquals(light.contains(".erp-action-glyph-" + colour), dark.contains(".erp-action-glyph-" + colour),
                    "Light/Dark semantic glyph selector mismatch for " + colour);
            assertEquals(light.contains(".erp-table-header-colour-" + colour), dark.contains(".erp-table-header-colour-" + colour),
                    "Light/Dark semantic header selector mismatch for " + colour);
        }
    }

    @Test
    void coreControllersDoNotOwnHardCodedPresentationColoursAndBlankFxmlButtonsAreExplicit() throws Exception {
        Path controllers = Path.of("src/main/java/org/example/controller");
        List<String> colourLeaks = new ArrayList<>();
        try (var files = Files.walk(controllers)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.matches("(?s).*#[0-9A-Fa-f]{6}.*")
                        || source.matches("(?s).*-fx-(?:background-color|text-fill|icon-color|border-color).*")) {
                    colourLeaks.add(controllers.relativize(file).toString());
                }
            }
        }
        assertTrue(colourLeaks.isEmpty(), "Core controllers must leave colour ownership to themes: " + colourLeaks);

        Path dashboard = Path.of("src/main/resources/fxml/pages/Dashboard.fxml");
        String fxml = Files.readString(dashboard);
        String controller = Files.readString(Path.of("src/main/java/org/example/controller/DashboardController.java"));
        for (String id : List.of("btnSidebarToggle", "btnReminderTop", "btnNotifications", "btnEmailCenter",
                "btnWhatsappCenter", "btnShortcutInfo")) {
            assertTrue(fxml.contains("fx:id=\"" + id + "\""), "Dashboard missing icon-only control " + id);
            assertTrue(controller.contains("UiActionIcons.apply(" + id), id + " must have explicit shell semantic ownership");
        }
        String user = Files.readString(Path.of("src/main/java/org/example/controller/UserDialogController.java"));
        for (String id : List.of("btnPasswordEye", "btnConfirmEye")) {
            assertTrue(user.contains(id + ".setGraphic(IconFactory.compactIcon"));
            assertTrue(user.contains(id + ".getProperties().put(\"erp.icon.skip\", true)"));
        }
    }

    @Test
    void auditCommunicationReminderAndDialogThemeUseSharedOwnershipWithoutLegacyOverrides() throws Exception {
        String audit = Files.readString(Path.of("src/main/java/org/example/controller/GlobalAuditController.java"));
        assertTrue(audit.contains("UiActionIcons.applyLabeledTableAction(b,\"View\",\"view\",\"View audit details\")"),
                "Audit View must keep the shared eye semantic and visible View label");

        String actionIcons = Files.readString(Path.of("src/main/java/org/example/util/UiActionIcons.java"));
        assertTrue(actionIcons.contains("applyLabeledTableAction"),
                "Direct table actions must use one shared icon+label treatment");
        String dynamicTable = Files.readString(Path.of("src/main/java/org/example/util/DynamicTableLayoutManager.java"));
        assertTrue(dynamicTable.contains("if (renderedControl > 0) minimum = Math.max(minimum, renderedControl)"),
                "Dynamic table layout must preserve every rendered action control, not only Actions menus");

        String communication = Files.readString(Path.of("src/main/java/org/example/controller/CommunicationCenterController.java"));
        assertTrue(communication.contains("approved-secondary-button\",\"communication-resend-button"),
                "Re-send must remain an approved secondary action");
        assertTrue(communication.contains("UiActionIcons.apply(resend,\"refresh\""),
                "Re-send icon must be centrally assigned");

        String reminder = Files.readString(Path.of("src/main/resources/fxml/pages/ReminderCenter.fxml"));
        assertTrue(reminder.contains("minWidth=\"360\" prefWidth=\"390\" maxWidth=\"430\""),
                "Reminder details must retain a readable drawer width");
        String reminderController = Files.readString(Path.of("src/main/java/org/example/controller/ReminderCenterController.java"));
        assertTrue(reminderController.contains("RegisterUiSupport.showDrawer(reminderDetailPanel, reminderWorkspace, 0.64)"));
        assertTrue(reminderController.contains("RegisterUiSupport.hideDrawer(reminderDetailPanel, reminderWorkspace, table)"));

        for (String theme : List.of("light-theme.css", "dark-theme.css")) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            String compact = css.replaceAll("\\s+", " ");
            assertFalse(compact.matches("(?s).*\\.communication-resend-button \\{[^}]*-fx-background-color:[^}]*}.*"),
                    theme + " must not override Re-send with a legacy green surface");
            Matcher drawer = Pattern.compile("\\.erp-detail-drawer-card \\{([^}]*)}").matcher(css);
            assertTrue(drawer.find(), theme + " missing base detail drawer selector");
            String baseDrawer = drawer.group(1);
            assertFalse(baseDrawer.contains("-fx-min-width"), theme + " base drawer must not override screen geometry");
            assertFalse(baseDrawer.contains("-fx-pref-width"), theme + " base drawer must not override screen geometry");
            assertFalse(baseDrawer.contains("-fx-max-width"), theme + " base drawer must not override screen geometry");
        }

        String dialogs = Files.readString(Path.of("src/main/java/org/example/util/DialogPresentation.java"));
        assertTrue(dialogs.contains("pane.sceneProperty().addListener"),
                "Dialog theme must be attached before first visible frame");
        assertTrue(dialogs.contains("applyOwnerTheme(dialog"),
                "Dialogs must inherit the owner scene theme instead of snapping later");
        String themes = Files.readString(Path.of("src/main/java/org/example/theme/ThemeManager.java"));
        assertTrue(themes.contains("public static void applyTheme(Scene scene, Window owner)"));
        assertTrue(themes.contains("ownerThemeUrl(owner)"));
    }

}
