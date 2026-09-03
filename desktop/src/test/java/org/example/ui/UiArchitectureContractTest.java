package org.example.ui;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UiArchitectureContractTest {
    private static final Path FXML_ROOT = Path.of("src/main/resources/fxml");

    @Test
    void kpiSectionsHaveOneRuntimeWidthAuthority() throws Exception {
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(FXML_ROOT)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".fxml")).toList()) {
                var document = parse(path);
                NodeList all = document.getElementsByTagName("*");
                for (int i = 0; i < all.getLength(); i++) {
                    if (!(all.item(i) instanceof Element element)) continue;
                    String styles = element.getAttribute("styleClass");
                    if (!containsStyle(styles, "erp-kpi-section")) continue;
                    if (containsStyle(styles, "erp-kpi-bounded")) {
                        violations.add(path + ": bounded KPI class is forbidden");
                    }
                    NodeList children = element.getChildNodes();
                    for (int c = 0; c < children.getLength(); c++) {
                        Node child = children.item(c);
                        if (child.getNodeType() == Node.ELEMENT_NODE && "columnConstraints".equals(child.getNodeName())) {
                            violations.add(path + ": KPI section contains static columnConstraints");
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void sidebarButtonsNeverCarryActionPrimaryStyling() throws Exception {
        Path dashboard = FXML_ROOT.resolve("pages/Dashboard.fxml");
        var document = parse(dashboard);
        Element sidebar = findByStyle(document.getDocumentElement(), "erp-sidebar");
        assertNotNull(sidebar, "Dashboard sidebar root is missing");

        List<String> violations = new ArrayList<>();
        NodeList buttons = sidebar.getElementsByTagName("Button");
        for (int i = 0; i < buttons.getLength(); i++) {
            Element button = (Element) buttons.item(i);
            String id = button.getAttributeNS("http://javafx.com/fxml/1", "id");
            String styles = button.getAttribute("styleClass");
            for (String forbidden : List.of("approved-primary-button", "primary-button", "erp-button-primary", "erp-action-button")) {
                if (containsStyle(styles, forbidden)) violations.add(id + " carries forbidden sidebar style " + forbidden);
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    private static org.w3c.dom.Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static Element findByStyle(Element root, String style) {
        if (containsStyle(root.getAttribute("styleClass"), style)) return root;
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                Element found = findByStyle(child, style);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean containsStyle(String styleClass, String wanted) {
        if (styleClass == null || styleClass.isBlank()) return false;
        for (String value : styleClass.split(",")) if (wanted.equals(value.trim())) return true;
        return false;
    }
}
