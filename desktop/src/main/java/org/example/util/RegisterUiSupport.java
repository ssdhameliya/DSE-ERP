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
import javafx.scene.control.TextField;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.time.LocalDate;

/** Shared, behavior-only helpers for register rows and detail drawers. */
public final class RegisterUiSupport {
    private RegisterUiSupport() { }


    /** Standardizes the register-header search treatment used across operational lists. */
    public static void configureHeaderSearch(TextField search, StackPane iconHost, String prompt) {
        if (search != null) {
            if (prompt != null && !prompt.isBlank()) search.setPromptText(prompt);
            if (!search.getStyleClass().contains("erp-item-search")) search.getStyleClass().add("erp-item-search");
            if (!search.getStyleClass().contains("register-header-search-input")) search.getStyleClass().add("register-header-search-input");
        }
        if (iconHost != null) {
            iconHost.getChildren().setAll(IconFactory.compactIcon("search", 16));
            if (!iconHost.getStyleClass().contains("register-header-search-icon")) iconHost.getStyleClass().add("register-header-search-icon");
        }
    }

    /** Applies a deterministic date interval without duplicating date math in controllers. */
    public static void setDateRange(DatePicker from, DatePicker to, LocalDate start, LocalDate end) {
        if (from != null) from.setValue(start);
        if (to != null) to.setValue(end);
    }

    public static void setCurrentYearRange(DatePicker from, DatePicker to, LocalDate today) {
        LocalDate effective = today == null ? LocalDate.now() : today;
        setDateRange(from, to, effective.withDayOfYear(1), effective);
    }

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
        if (splitPane != null) {
            splitPane.setDividerPositions(dividerPosition);
            reflowDrawerTables(splitPane);
        }
    }

    public static void hideDrawer(Region drawer, SplitPane splitPane, TableView<?> table) {
        if (drawer != null) {
            drawer.setManaged(false);
            drawer.setVisible(false);
        }
        if (splitPane != null) {
            splitPane.setDividerPositions(1.0);
            reflowDrawerTables(splitPane);
        }
        if (table != null) table.getSelectionModel().clearSelection();
    }

    private static void reflowDrawerTables(SplitPane splitPane) {
        Platform.runLater(() -> {
            try {
                splitPane.applyCss();
                splitPane.layout();
                DynamicTableLayoutManager.requestLayoutIn(splitPane);
            } catch (RuntimeException ignored) { }
        });
    }
}
