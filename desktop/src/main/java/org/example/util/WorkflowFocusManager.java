package org.example.util;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Central business-order focus sequence and keyboard navigation for ERP screens. */
public final class WorkflowFocusManager {
    private static final String FOCUS_INSTALLED_PROP = "erp.workflow.focus.installed";
    private static final String ACTIVE_FOCUS_CLASS = "erp-input-active-focused";

    private WorkflowFocusManager() {}

    /**
     * Installs explicit ordered focus chain for business entry screens (Sales, Purchase, Quotation, Dialogs).
     */
    public static void install(List<? extends Node> ordered) {
        List<Node> nodes = new ArrayList<>();
        if (ordered != null) {
            ordered.stream().filter(Objects::nonNull).forEach(nodes::add);
        }
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            node.getProperties().put(FOCUS_INSTALLED_PROP, true);
            attachInputFocusHighlightAndSelection(node);
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

    public static void initial(Node node) {
        if (node != null) {
            Platform.runLater(node::requestFocus);
        }
    }

    public static void selectAllOnFocus(TextField field) {
        if (field != null) {
            field.focusedProperty().addListener((o, was, focused) -> {
                if (focused) Platform.runLater(field::selectAll);
            });
        }
    }

    /**
     * Automatically enhances any screen root:
     * 1. Attaches universal active focus highlighting and selectAll on inputs.
     * 2. If the container does not already have an explicit business focus chain,
     *    sorts editable controls geometrically in natural bookkeeping order (row by row, left to right).
     */
    public static void autoEnhanceScreen(Node root) {
        if (!(root instanceof Parent parent)) return;
        if (Boolean.TRUE.equals(parent.getProperties().get(FOCUS_INSTALLED_PROP))) return;

        List<Node> candidates = new ArrayList<>();
        collectInteractiveControls(parent, candidates);

        for (Node node : candidates) {
            attachInputFocusHighlightAndSelection(node);
        }

        boolean hasExplicitChain = candidates.stream()
                .anyMatch(n -> Boolean.TRUE.equals(n.getProperties().get(FOCUS_INSTALLED_PROP)));
        if (!hasExplicitChain && candidates.size() > 1) {
            candidates.sort(Comparator.comparingDouble((Node n) -> {
                Bounds b = n.localToScene(n.getBoundsInLocal());
                return b != null ? Math.round(b.getMinY() / 24.0) * 24.0 : 0.0;
            }).thenComparingDouble(n -> {
                Bounds b = n.localToScene(n.getBoundsInLocal());
                return b != null ? b.getMinX() : 0.0;
            }));

            install(candidates);
        }
        parent.getProperties().put(FOCUS_INSTALLED_PROP, true);
    }

    private static void collectInteractiveControls(Parent parent, List<Node> result) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (!child.isVisible() || !child.isManaged() || child.isDisabled()) continue;
            if (isFocusableInputField(child)) {
                result.add(child);
            } else if (child instanceof Parent nested) {
                collectInteractiveControls(nested, result);
            }
        }
    }

    private static boolean isFocusableInputField(Node node) {
        if (!node.isFocusTraversable()) return false;
        return node instanceof TextField
                || node instanceof ComboBox<?>
                || node instanceof DatePicker
                || node instanceof Spinner<?>
                || node instanceof CheckBox;
    }

    private static void attachInputFocusHighlightAndSelection(Node node) {
        if (node instanceof TextField tf) {
            selectAllOnFocus(tf);
        }
        node.focusedProperty().addListener((obs, was, isFocused) -> {
            if (Boolean.TRUE.equals(isFocused)) {
                if (!node.getStyleClass().contains(ACTIVE_FOCUS_CLASS)) {
                    node.getStyleClass().add(ACTIVE_FOCUS_CLASS);
                }
            } else {
                node.getStyleClass().remove(ACTIVE_FOCUS_CLASS);
            }
        });
    }

    private static boolean advanceOnEnter(Node node) {
        if (node instanceof TextArea) return false;
        if (node instanceof Button || node instanceof MenuButton) return false;
        return node instanceof TextField || node instanceof ComboBox<?> || node instanceof DatePicker || node instanceof Spinner<?>;
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
