package org.example.util;

import javafx.scene.Node;
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
