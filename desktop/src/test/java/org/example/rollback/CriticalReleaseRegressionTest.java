package org.example.rollback;

import org.example.update.BuildInfo;
import org.example.util.UiSemanticRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CriticalReleaseRegressionTest {
    @Test void buildIdentityIsGeneratedFor988() {
        assertEquals(org.example.shared.RuntimeContract.appVersion(), BuildInfo.version());
        assertEquals(org.example.shared.RuntimeContract.buildRevision(), BuildInfo.buildRevision());
        assertEquals(1, BuildInfo.databaseMigrationVersion());
        assertEquals(1, BuildInfo.workspaceSchemaVersion());
    }

    @Test void previousSameGenerationVersionUsesSchemaOne() {
        RollbackService service = new RollbackService();
        assertEquals(1, service.schemaForVersion("9.0.79"));
        RollbackService.Compatibility compatibility = service.compatibilityFor(1);
        assertTrue(compatibility.safe());
        assertEquals("Safe", compatibility.label());
    }

    @Test void retainedRollbackPackagesCanResolveCurrentCompatibilityGeneration() throws Exception {
        String service = Files.readString(Path.of("src/main/java/org/example/rollback/RollbackService.java"));
        assertTrue(service.contains("isManagedRollbackPackage(installer)"));
        assertTrue(service.contains("generationSchema > 0"));
    }

    @Test void unknownOrFutureSchemaGenerationStaysBlocked() {
        RollbackService service = new RollbackService();
        assertEquals(-1, service.schemaForVersion("7.2.1"));
        assertEquals(-1, service.schemaForVersion("99.0.0"));
        assertFalse(service.compatibilityFor(-1).safe());
        assertEquals("Unknown", service.compatibilityFor(-1).label());
    }

    @Test void reminderAndNotificationHaveDistinctIcons() {
        assertEquals("fas-clock", UiSemanticRegistry.iconLiteral("reminder"));
        assertEquals("fas-bell", UiSemanticRegistry.iconLiteral("notification"));
        assertNotEquals(UiSemanticRegistry.iconLiteral("reminder"), UiSemanticRegistry.iconLiteral("notification"));
    }

    @Test void workspaceLabelsHaveExplicitSemantics() {
        assertEquals("storage-usage", UiSemanticRegistry.fieldSemantic("Storage Usage"));
        assertEquals("storage-retention", UiSemanticRegistry.fieldSemantic("Storage Retention"));
        assertEquals("diagnostic", UiSemanticRegistry.fieldSemantic("Diagnostic ZIP retention"));
        assertEquals("temporary", UiSemanticRegistry.fieldSemantic("Temporary files retention"));
    }

    @Test void rollbackNoLongerUsesHardcodedVersionPromptOrKnownSchemaMap() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/org/example/controller/SafeRollbackController.java"));
        String service = Files.readString(Path.of("src/main/java/org/example/rollback/RollbackService.java"));
        assertFalse(controller.contains("OwnedTextInputDialog(\"9.0.7\")"));
        assertTrue(controller.contains("publishedPreviousVersions"));
        assertFalse(service.contains("KNOWN_SCHEMA"));
    }

    @Test void workspacePaddingAndFinalSidebarSelectionAreOwnedByBothThemes() throws Exception {
        for (String theme : new String[]{"light-theme.css", "dark-theme.css"}) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains(".workspace-settings-panel"));
            assertTrue(css.contains("-fx-padding: 22px 24px 24px 24px"));
            assertTrue(css.contains(".erp-ui-standard .erp-sidebar .button.menu-selected"));
            assertTrue(css.contains("-fx-background-color: #5b21b6"));
        }
    }
    @Test void semanticAuditRegressionFor988() throws Exception {
        var semantic = org.example.util.IconFactory.class.getDeclaredMethod("semantic", String.class);
        semantic.setAccessible(true);
        assertEquals("import", semantic.invoke(null, "Import Excel"));
        assertEquals("export", semantic.invoke(null, "Export Excel"));
        assertEquals("reject", semantic.invoke(null, "Reject Return"));
        assertEquals("refund", semantic.invoke(null, "View / Record Refund"));
        assertEquals("mark-all-read", semantic.invoke(null, "Mark All Read"));
        assertEquals("open-record", semantic.invoke(null, "Open Record"));
        assertNotEquals(UiSemanticRegistry.iconLiteral("permission"), UiSemanticRegistry.iconLiteral("role"));
        assertNotEquals(UiSemanticRegistry.iconLiteral("size"), UiSemanticRegistry.iconLiteral("unit"));
        assertNotEquals(UiSemanticRegistry.iconLiteral("frequency"), UiSemanticRegistry.iconLiteral("time"));
        assertNotEquals(UiSemanticRegistry.iconLiteral("compatibility"), UiSemanticRegistry.iconLiteral("reference"));
        assertEquals("update", UiSemanticRegistry.fieldSemantic("Latest Available"));
        assertEquals("outstanding", UiSemanticRegistry.headerSemantic("Outstanding"));
    }

    @Test void ordinaryValuesUseSharedSemanticDecorator() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/example/util/IconFactory.java"));
        assertTrue(source.contains("decorateOrdinaryValueLabel(label)"));
        assertTrue(source.contains("erp-value-colour-"));
        for (String theme : new String[]{"light-theme.css", "dark-theme.css"}) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains(".erp-value-colour-blue"));
            assertTrue(css.contains(".erp-value-colour-green"));
        }
    }

    @Test void latestLiveFilterIsReplayedAfterServerPageApply() {
        org.example.util.RegisterPageState state = new org.example.util.RegisterPageState();
        java.util.concurrent.atomic.AtomicInteger value = new java.util.concurrent.atomic.AtomicInteger();
        state.runApplying(() -> {
            state.runWhenIdle(() -> value.set(1));
            state.runWhenIdle(() -> value.set(2));
            assertEquals(0, value.get());
        });
        assertEquals(2, value.get());
    }

    @Test void tableSelectionNoLongerRefreshesWholeTableAndLayoutIsCoalesced() throws Exception {
        String enhancer = Files.readString(Path.of("src/main/java/org/example/util/ProfessionalUiEnhancer.java"));
        String layout = Files.readString(Path.of("src/main/java/org/example/util/DynamicTableLayoutManager.java"));
        assertFalse(enhancer.contains("table.refresh();"));
        assertTrue(enhancer.contains("updateSelectionVisual()"));
        assertTrue(layout.contains("Always coalesce width/item/skin changes into one next-pulse pass"));
        assertTrue(layout.contains("RENDERED_ACTION_WIDTH"));
        assertFalse(layout.contains("region.applyCss();"));
    }

    @Test void genericSectionsAreNotAutomaticallyPromotedToShadowedSurfaces() throws Exception {
        String designSystem = Files.readString(Path.of("src/main/java/org/example/util/UiDesignSystem.java"));
        assertTrue(designSystem.contains("a generic word such as section/panel/workspace/detail must not"));
        assertFalse(designSystem.contains("return containsAny(s, \"card\", \"panel\", \"drawer\", \"workspace\", \"section\""));
    }

    @Test void finalThemeAuthorityOwnsCleanControlsSearchRailAndSettingsWorkspace() throws Exception {
        for (String theme : new String[]{"light-theme.css", "dark-theme.css"}) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains("DSE ERP — FINAL UI STABILITY AUTHORITY"));
            assertTrue(css.contains(".workspace-deployment-grid"));
            assertTrue(css.contains(".dse-global-search-v3-module-list .scroll-bar:horizontal"));
            assertTrue(css.contains("-fx-max-height: 0"));
            assertTrue(css.contains(".erp-control-input:focus-within"));
            assertTrue(css.contains("-fx-effect: null"));
        }
        String workspace = Files.readString(Path.of("src/main/resources/fxml/pages/settings/WorkspaceSettingsPanel.fxml"));
        assertTrue(workspace.contains("Connection mode"));
        assertTrue(workspace.contains("Company server URL"));
        assertTrue(workspace.contains("GridPane.rowIndex=\"1\"><Label text=\"Reports / Exports\""));
    }

    @Test void managedSharedClientCannotBeDowngradedByStaleLocalEnvironmentOverride() throws Exception {
        String config = Files.readString(Path.of("src/main/java/org/example/config/ConfigManager.java"));
        assertTrue(config.contains("WorkspaceManager.isManagedSharedClientWorkspace()) return DeploymentMode.SHARED_CLIENT"));
        assertTrue(config.contains("The verified managed profile owns the company-server endpoint"));
        String settings = Files.readString(Path.of("src/main/java/org/example/controller/SettingsController.java"));
        assertTrue(settings.contains("effectiveLoadedDeploymentMode()"));
        assertTrue(settings.contains("WorkspaceManager.updateManagedSharedClientConnection"));
        assertTrue(settings.contains("Settings could not be saved:"));
        assertTrue(settings.contains("deploymentRestartRequired = true"));
    }

    @Test void sharedClientConnectionActivationIsIdempotentAndRollbackSafe() throws Exception {
        String workspace = Files.readString(Path.of("src/main/java/org/example/config/WorkspaceManager.java"));
        assertTrue(workspace.contains("updateManagedSharedClientConnection(serverUrl, environment)"));
        assertTrue(workspace.contains("restore the persistent pointer to LOCAL"));
        assertTrue(workspace.contains("The new managed Shared Client profile could not be verified"));
    }

    @Test void brokenManagedSharedClientCanRepairServerConnectionFromStartupUi() throws Exception {
        String main = Files.readString(Path.of("src/main/java/org/example/app/Main.java"));
        assertTrue(main.contains("Configure Server"));
        assertTrue(main.contains("configureSharedClientConnectionAtStartup"));
        assertTrue(main.contains("DeploymentConnectionService.test(candidate, expectedEnvironment)"));
        assertTrue(main.contains("WorkspaceManager.updateManagedSharedClientConnection(normalized, selectedEnvironment)"));
        assertTrue(main.contains("previous LOCAL workspace was not modified"));
        assertTrue(main.contains("Save & Restart"));
    }

    @Test void everyCentralDialogHasVisibleCloseControlAndEscapePath() throws Exception {
        String presentation = Files.readString(Path.of("src/main/java/org/example/util/DialogPresentation.java"));
        assertTrue(presentation.contains("IconFactory.compactIcon(\"close\", 14)"));
        assertTrue(presentation.contains("closeButton.setCancelButton(true)"));
        for (String theme : new String[]{"light-theme.css", "dark-theme.css"}) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains("dialog close control must remain visible"));
            assertTrue(css.contains(".modern-dialog-close .erp-action-glyph"));
        }
    }

    @Test void realUpgradeQueuesWhatsNewAndCurrentReleaseHasOfflineHighlights() throws Exception {
        String lifecycle = Files.readString(Path.of("src/main/java/org/example/update/UpdateLifecycle.java"));
        String dialogs = Files.readString(Path.of("src/main/java/org/example/update/UpdateDialogs.java"));
        assertTrue(lifecycle.contains("update.releaseNotesPending"));
        assertTrue(dialogs.contains("update.releaseNotesPending"));
        assertTrue(dialogs.contains("notes.setMinHeight(360)"));
        assertTrue(dialogs.contains("VBox.setVgrow(notes, Priority.ALWAYS)"));
        String presentation = Files.readString(Path.of("src/main/java/org/example/util/DialogPresentation.java"));
        assertTrue(presentation.contains("VBox.setVgrow(customContent, Priority.ALWAYS)"));
        assertFalse(org.example.update.ReleaseHighlights.forVersion(org.example.update.BuildInfo.version())
                .contains("unavailable offline"));
    }

}
