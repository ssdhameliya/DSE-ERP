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
        assertTrue(controller.contains("AppDialogRenderer.configureWorkspace(dialog, \"document\")")
                || controller.contains("org.example.util.AppDialogRenderer.configureWorkspace(dialog, \"document\")"));
        assertTrue(controller.contains("json.getStyleClass().add(\"pdf-json-viewer\")"));
        assertFalse(controller.contains("json.setStyle(\"-fx-font-family"),
                "JSON viewer styling belongs to the centralized light/dark themes");
    }

    @Test
    void selectedRecordPreviewUsesTheRealRendererInsteadOfOverlayingSourceValues() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/org/example/documentstudio/controller/PdfStudioController.java"));
        assertTrue(controller.contains("pdf-studio-v3-record-preview-"));
        assertTrue(controller.contains("PdfStudioRenderer.render(template,previewData(),previewPdf)"));
        assertTrue(controller.contains("previewMode=true; dataPreviewMode=true"));
        assertTrue(controller.contains("Source regions: "));
        assertTrue(controller.contains("lblMappingPercent.setText(\"Source \" + pct + \"%\")"));
    }

    @Test
    void guidedMappingUiKeepsOnlyPrimaryActionsVisibleAndMovesDesignControlsBehindAdvanced() throws Exception {
        String xml = Files.readString(FXML);
        assertTrue(xml.contains("Mapping Coach"));
        assertTrue(xml.contains("Advanced Design (optional)"));
        assertTrue(xml.contains("Text / Position correction"));
        assertTrue(xml.contains("Validate Mapping"));
        assertTrue(xml.contains("Mapping Guide PDF"));
        assertFalse(xml.contains("View JSON Data"), "JSON is an engineering aid and should not clutter normal mapping");
        assertEquals(1, occurrences(xml, "onAction=\"#autoMapNow\""), "Auto Map must appear only once in the normal UI");
        assertEquals(1, occurrences(xml, "onAction=\"#detectItemHeaders\""), "Detect Item Headers must appear only once in the normal UI");
        assertFalse(xml.contains("text=\"Undo\""));
        assertFalse(xml.contains("text=\"Redo\""));
        assertFalse(xml.contains("text=\"Duplicate\""));
        assertFalse(xml.contains("text=\"Snap\""));
        assertFalse(xml.contains("text=\"+ Text\""));
    }

    @Test
    void mappingCoachGivesSpecificNextStepForCommonItemHeadersAndMultilineContent() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/org/example/documentstudio/controller/PdfStudioController.java"));
        assertTrue(controller.contains("Recommended ERP field:"));
        assertTrue(controller.contains("itemHeaderField"));
        assertTrue(controller.contains("item.descriptionWithRemarks"));
        assertTrue(controller.contains("item.quantity"));
        assertTrue(controller.contains("item.rate"));
        assertTrue(controller.contains("item.unit"));
        assertTrue(controller.contains("party.billingAddress"));
        assertTrue(controller.contains("company.terms"));
        assertTrue(controller.contains("Financial Summary"));
    }


    @Test
    void guidedFixNextPrefiltersRequiredFieldsAndHandlesUnmappedItemHeadersWithoutBlockingDialog() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/org/example/documentstudio/controller/PdfStudioController.java"));
        assertTrue(controller.contains("focusFirstActionableIssue(result)"));
        assertTrue(controller.contains("focusFirstUnmappedItemHeader()"));
        assertTrue(controller.contains("Suggested ERP field:"));
        assertTrue(controller.contains("requirement.acceptedFields().stream()"));
        assertTrue(controller.contains("txtFieldSearch.setText(query)"));
    }

    @Test
    void flowSafetyChangesApplyImmediatelyWithoutSeparateAdvancedApplyStep() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/org/example/documentstudio/controller/PdfStudioController.java"));
        assertTrue(controller.contains("cmbGrowthDirection.setOnAction(e -> applyInspectorSilently())"));
        assertTrue(controller.contains("cmbOverflowPolicy.setOnAction(e -> applyInspectorSilently())"));
        assertTrue(controller.contains("chkAutoHeight.setOnAction(e -> applyInspectorSilently())"));
    }


    @Test
    void guidedCanvasPrioritizesPreciseSourceValuesAndShowsPhysicalItemHeaderMappings() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/org/example/documentstudio/controller/PdfStudioController.java"));
        int vectors = controller.indexOf("addDetectedVectorTargets();");
        int objects = controller.indexOf("for (TemplateElement e : template.getElements())", vectors);
        int values = controller.indexOf("addDetectedValueTargets();", objects);
        assertTrue(vectors >= 0 && objects > vectors && values > objects,
                "Broad vectors must be behind Studio objects while precise text/form/image click targets stay on top");
        assertTrue(controller.contains("ITEM REPEATER • PHYSICAL HEADERS"));
        assertTrue(controller.contains("full.contains(\"contact details\")"));
        assertTrue(controller.contains("transport.contact"));
    }

    @Test
    void sourceSelectionSeparatesLabelValuesAndAllowsVectorSignatureMapping() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/org/example/documentstudio/controller/PdfStudioController.java"));
        assertTrue(controller.contains("PdfTextExtractionService.valueHitRegion(region)"),
                "Common Label : Value lines must expose the changing value as its own click target");
        assertTrue(controller.contains("selectedSourceVector != null") || controller.contains("selectedSourceVector!=null"));
        assertTrue(controller.contains("mapSourceVectorToImageField"));
        assertTrue(controller.contains("company.signature"));
        assertTrue(controller.contains("signature/vector artwork"));
    }


    @Test
    void autoDetectionReviewUsesOneCentralizedWorkspaceDialogAndNoFeatureCss() throws Exception {
        String xml = Files.readString(FXML);
        String controller = Files.readString(Path.of("src/main/java/org/example/documentstudio/controller/PdfStudioController.java"));
        String service = Files.readString(Path.of("src/main/java/org/example/util/AppDialogService.java"));
        String renderer = Files.readString(Path.of("src/main/java/org/example/util/AppDialogRenderer.java"));
        String css = Files.readString(Path.of("src/main/resources/css/app-dialog.css"));
        assertTrue(xml.contains("text=\"Review Mapping\""));
        assertTrue(xml.contains("onAction=\"#reviewMapping\""));
        assertTrue(controller.contains("AppDialogService.workspace(root"));
        assertTrue(controller.contains("PdfMappingReviewWorkspace"));
        assertTrue(controller.contains("showMappingReview(PdfMappingReviewSession.Section.ITEMS"));
        assertTrue(controller.contains("showMappingReview(PdfMappingReviewSession.Section.FINANCIAL"));
        assertTrue(service.contains("public static Optional<ButtonType> workspace"));
        assertTrue(renderer.contains("workspaceConfigured || !configuredMessage"),
                "Central renderer must preserve custom workspace content while still showing heading/help copy");
        assertTrue(css.contains("dse-workspace-mapping-review"));
        long cssFiles = Files.list(Path.of("src/main/resources/css")).filter(f -> f.getFileName().toString().endsWith(".css")).count();
        assertEquals(3, cssFiles, "Mapping review must reuse the existing three CSS files; no feature-specific stylesheet is allowed");
    }

    private static int occurrences(String text, String token) {
        int count = 0, from = 0;
        while ((from = text.indexOf(token, from)) >= 0) { count++; from += token.length(); }
        return count;
    }

}
