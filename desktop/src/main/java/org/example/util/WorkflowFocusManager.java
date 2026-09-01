package org.example.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** Central business-order focus sequence for transaction entry screens. */
public final class WorkflowFocusManager {
    private WorkflowFocusManager() {}

    public static void install(List<? extends Node> ordered) {
        List<Node> nodes = new ArrayList<>();
        if (ordered != null) ordered.stream().filter(java.util.Objects::nonNull).forEach(nodes::add);
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            final int index = i;
            node.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.TAB) {
                    focus(nodes, index, event.isShiftDown() ? -1 : 1);
                    event.consume();
                    return;
                }
                if (event.getCode() == KeyCode.ENTER && advanceOnEnter(node)) {
                    focus(nodes, index, 1);
                    event.consume();
                }
            });
        }
    }

    public static void initial(Node node) { if (node != null) Platform.runLater(node::requestFocus); }
    public static void selectAllOnFocus(TextField field) {
        if (field != null) field.focusedProperty().addListener((o, was, focused) -> { if (focused) Platform.runLater(field::selectAll); });
    }

    private static boolean advanceOnEnter(Node node) {
        if (node instanceof TextArea) return false;
        return node instanceof TextField || node instanceof ComboBox<?> || node instanceof DatePicker;
    }

    private static void focus(List<Node> nodes, int current, int direction) {
        int size = nodes.size();
        for (int step = 1; step <= size; step++) {
            int next = Math.floorMod(current + direction * step, size);
            Node candidate = nodes.get(next);
            if (candidate.isVisible() && candidate.isManaged() && !candidate.isDisabled() && candidate.isFocusTraversable()) {
                candidate.requestFocus();
                return;
            }
        }
    }
}
