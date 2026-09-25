package org.example.documentstudio.controller;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ExcelStudioEditorLayoutContractTest {
    private static final Path FXML = Path.of("src/main/resources/fxml/pages/ExcelDesigner.fxml");

    @Test
    void excelStudioExposesBuiltInMappingGuideWithoutInlineStyles() throws Exception {
        String xml = Files.readString(FXML);
        assertTrue(xml.contains("Excel Mapping Guide PDF"));
        assertTrue(xml.contains("onAction=\"#downloadMappingGuide\""));
        assertFalse(xml.contains(" style=\""), "Excel Studio FXML must use centralized CSS classes");
        assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Files.newInputStream(FXML)));
    }

    @Test
    void everyFxmlActionResolvesToExcelDesignerController() throws Exception {
        String xml = Files.readString(FXML);
        var matcher = Pattern.compile("#[A-Za-z_][A-Za-z0-9_]*").matcher(xml);
        while (matcher.find()) {
            String method = matcher.group().substring(1);
            boolean exists = java.util.Arrays.stream(ExcelDesignerController.class.getDeclaredMethods())
                    .anyMatch(candidate -> candidate.getName().equals(method));
            assertTrue(exists, "Missing ExcelDesignerController handler: " + method);
        }
    }

    @Test
    void guideHandlerUsesVersionedBundledHelpService() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/org/example/documentstudio/controller/ExcelDesignerController.java"));
        assertTrue(controller.contains("ExcelStudioHelpService.exportToUserDownloadLocation()"));
        assertTrue(controller.contains("Excel mapping guide downloaded"));
    }
}
