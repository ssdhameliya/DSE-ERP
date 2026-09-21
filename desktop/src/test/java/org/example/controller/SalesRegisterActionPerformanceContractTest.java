package org.example.controller;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SalesRegisterActionPerformanceContractTest {
    @Test
    void salesRegisterUsesOneSharedLazyMenuAndSelectsActionRow() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/example/controller/SalesListController.java"));
        int start = source.indexOf("private void configureActions()");
        int end = source.indexOf("@FXML public void refresh()", start);
        String block = source.substring(start, end);
        assertTrue(block.contains("final ContextMenu sharedMenu = new ContextMenu()"));
        assertTrue(block.contains("final Button button = new Button(\"Actions\")"));
        assertTrue(block.contains("getTableView().getSelectionModel().select(getIndex())"));
        assertFalse(block.contains("getTableView().scrollTo(getIndex())"),
                "Opening Actions must not move the clicked row to the top of the visible register");
        assertFalse(block.contains("new MenuButton"));
    }
}
