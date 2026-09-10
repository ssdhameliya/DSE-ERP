package org.example.ui;

import org.example.util.IconFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Release gate for application-wide semantic button icon coverage. */
class CentralButtonIconCoverageTest {
    private static final Pattern BUTTON = Pattern.compile(
            "<(?:Button|MenuButton|ToggleButton)\\b([^>]*)>", Pattern.DOTALL);
    private static final Pattern TEXT = Pattern.compile("\\btext=\\\"([^\\\"]*)\\\"");

    @Test
    void everyTextButtonInEveryFxmlHasCentralSemanticIconCoverage() throws Exception {
        Method semantic = IconFactory.class.getDeclaredMethod("semantic", String.class);
        semantic.setAccessible(true);
        List<String> missing = new ArrayList<>();
        Path fxmlRoot = Path.of("src/main/resources/fxml");
        try (var files = Files.walk(fxmlRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".fxml")).toList()) {
                String xml = Files.readString(file);
                Matcher button = BUTTON.matcher(xml);
                while (button.find()) {
                    Matcher text = TEXT.matcher(button.group(1));
                    if (!text.find() || text.group(1).isBlank()) continue;
                    String label = text.group(1).replace("&amp;", "&").trim();
                    Object resolved = semantic.invoke(null, label);
                    if (resolved == null) missing.add(fxmlRoot.relativize(file) + " :: " + label);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Buttons without centralized icon semantics: " + missing);
    }

    @Test
    void cachedScreensReconcileButtonPresentationInsteadOfKeepingFirstLoadState() throws Exception {
        String enhancer = Files.readString(Path.of("src/main/java/org/example/util/ProfessionalUiEnhancer.java"));
        String icons = Files.readString(Path.of("src/main/java/org/example/util/IconFactory.java"));
        assertTrue(enhancer.contains("refreshInteractivePresentation(root)"));
        assertTrue(enhancer.contains("IconFactory.decorate(button)"));
        assertTrue(icons.contains("installButtonSemanticGuard(button)"));
        assertTrue(icons.contains("button.textProperty().addListener"));
        assertTrue(icons.contains("button.graphicProperty().addListener"));
    }
}
