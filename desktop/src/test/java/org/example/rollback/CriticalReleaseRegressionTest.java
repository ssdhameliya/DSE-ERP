package org.example.rollback;

import org.example.update.BuildInfo;
import org.example.util.UiSemanticRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CriticalReleaseRegressionTest {
    @Test void buildIdentityIsGeneratedFor981() {
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
    @Test void semanticAuditRegressionFor981() throws Exception {
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

}
