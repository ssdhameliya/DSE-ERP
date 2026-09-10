package org.example.documentstudio.service;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.example.documentstudio.controller.PdfStudioController;

import static org.junit.jupiter.api.Assertions.*;

class PdfStudioEditorLayoutContractTest {
    private static final Path FXML = Path.of("src/main/resources/fxml/pages/PdfDesigner.fxml");

    @Test
    void editorExposesReadinessChecklistAndContextualSearchWithoutInlineStyles() throws Exception {
        String xml = Files.readString(FXML);
        assertTrue(xml.contains("fx:id=\"lblRequiredSummary\""));
        assertTrue(xml.contains("fx:id=\"lstRequirements\""));
        assertTrue(xml.contains("fx:id=\"txtFieldSearch\""));
        assertTrue(xml.contains("fx:id=\"txtInspectorFieldSearch\""));
        assertTrue(xml.contains("fx:id=\"lstInspectorFieldSuggestions\""));
        assertTrue(xml.contains("fx:id=\"btnFixNext\""));
        assertFalse(xml.contains(" style=\""), "PDF Studio FXML must use centralized CSS classes, not inline visual styling");
        assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Files.newInputStream(FXML)));
    }

    @Test
    void everyFxmlActionResolvesToThePdfStudioController() throws Exception {
        String xml = Files.readString(FXML);
        var matcher = Pattern.compile("#[A-Za-z_][A-Za-z0-9_]*").matcher(xml);
        while (matcher.find()) {
            String method = matcher.group().substring(1);
            boolean exists = java.util.Arrays.stream(PdfStudioController.class.getDeclaredMethods())
                    .anyMatch(candidate -> candidate.getName().equals(method));
            assertTrue(exists, "Missing PdfStudioController handler: " + method);
        }
    }
    @Test
    void manualMappingUsesSingleClickSourceAndRefreshesReadinessImmediately() throws Exception {
        String xml = Files.readString(FXML);
        String controller = Files.readString(Path.of("src/main/java/org/example/documentstudio/controller/PdfStudioController.java"));
        assertTrue(xml.contains("fx:id=\"btnMapSelectedField\""));
        assertTrue(xml.contains("readiness counters update immediately"));
        assertTrue(controller.contains("TemplateElement e = editableSelectionFromSource();"),
                "Manual mapping must materialize a normally clicked PDF source target");
        assertTrue(controller.contains("updateManualMappingState()"));
        assertTrue(controller.contains("readiness updated"));
        assertTrue(controller.contains("updateMappingUi(currentMappingAnalysis)"),
                "Manual autosave must refresh the top mapping progress summary too");
    }

    @Test
    void jsonViewerUsesResponsiveWorkspaceGrowthInsteadOfFixedInnerPanel() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/org/example/documentstudio/controller/PdfStudioController.java"));
        assertTrue(controller.contains("VBox.setVgrow(json, Priority.ALWAYS)"));
        assertTrue(controller.contains("DialogPresentation.configureWorkspace(dialog, \"document\")")
                || controller.contains("org.example.util.DialogPresentation.configureWorkspace(dialog, \"document\")"));
        assertTrue(controller.contains("json.getStyleClass().add(\"pdf-json-viewer\")"));
        assertFalse(controller.contains("json.setStyle(\"-fx-font-family"),
                "JSON viewer styling belongs to the centralized light/dark themes");
    }

}
