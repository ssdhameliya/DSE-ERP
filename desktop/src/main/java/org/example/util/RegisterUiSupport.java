package org.example.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Region;

/** Shared, behavior-only helpers for register rows and detail drawers. */
public final class RegisterUiSupport {
    private RegisterUiSupport() { }

    /** Returns true when the click originated from a control embedded in a table row. */
    public static boolean isInteractiveTableTarget(Node target, TableRow<?> row) {
        for (Node node = target; node != null && node != row; node = node.getParent()) {
            if (node instanceof ButtonBase || node instanceof TextInputControl || node instanceof ComboBoxBase<?>) {
                return true;
            }
        }
        return false;
    }

    public static void updatePageNavigation(RegisterPageState state, ButtonBase previous, ButtonBase next) {
        if (state == null) return;
        if (previous != null) previous.setDisable(state.currentPage() <= 0);
        if (next != null) next.setDisable(state.currentPage() + 1 >= state.totalPages());
    }

    public static void updatePageLabels(RegisterPageState state, Label info, Label pageNumber,
                                        int pageSize, int currentRows, String noun) {
        if (state == null) return;
        if (info != null) info.setText(state.rangeText(pageSize, currentRows, noun));
        if (pageNumber != null) pageNumber.setText(state.pageNumberText());
    }

    /** Forces the active workspace to consume released shell space after the global sidebar changes. */
    public static void reflowAfterShellResize(Node root) {
        if (root == null) return;
        Runnable pass = () -> {
            try {
                root.applyCss();
                root.autosize();
                if (root instanceof Parent parent) {
                    parent.requestLayout();
                    parent.layout();
                } else if (root.getParent() != null) {
                    root.getParent().requestLayout();
                }
                for (Node node : root.lookupAll(".split-pane")) {
                    if (!(node instanceof SplitPane split)) continue;
                    long active = split.getItems().stream().filter(Node::isManaged).filter(Node::isVisible).count();
                    double[] positions = split.getDividerPositions();
                    split.requestLayout();
                    if (active <= 1 && positions.length > 0) split.setDividerPositions(1.0);
                    else if (positions.length > 0) split.setDividerPositions(positions);
                }
            } catch (RuntimeException ignored) { }
        };
        Platform.runLater(() -> { pass.run(); Platform.runLater(pass); });
    }

    public static void showDrawer(Region drawer, SplitPane splitPane, double dividerPosition) {
        if (drawer != null) {
            drawer.setManaged(true);
            drawer.setVisible(true);
        }
        if (splitPane != null) splitPane.setDividerPositions(dividerPosition);
    }

    public static void hideDrawer(Region drawer, SplitPane splitPane, TableView<?> table) {
        if (drawer != null) {
            drawer.setManaged(false);
            drawer.setVisible(false);
        }
        if (splitPane != null) splitPane.setDividerPositions(1.0);
        if (table != null) table.getSelectionModel().clearSelection();
    }
}
