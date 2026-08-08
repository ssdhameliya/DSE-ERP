package org.example.util;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Adds the Purchase-Register style collapsible record drawer to suitable
 * read-only register/master tables. Editable transaction tables are excluded.
 */
public final class RegisterDetailsInstaller {
    private static final Set<String> CANDIDATE_SCREENS = Set.of(
            "approved-screen-purchase-returns",
            "approved-screen-sales-returns",
            "approved-screen-customer",
            "approved-screen-suppliers",
            "approved-screen-inventory",
            "approved-screen-item-master",
            "approved-screen-communication-center",
            "approved-screen-backup-restore"
    );

    private RegisterDetailsInstaller() {}

    public static void install(Node root) {
        if (!(root instanceof Parent parent)
                || Boolean.TRUE.equals(root.getProperties().get("erp-register-details-installed"))
                || !isCandidate(root)
                || root.lookup("#detailDrawer") != null) {
            return;
        }
        root.getProperties().put("erp-register-details-installed", true);
        Platform.runLater(() -> {
            TableView<?> table = findPrimaryTable(parent);
            if (table != null) attach(root, table);
        });
    }

    private static boolean isCandidate(Node root) {
        return root.getStyleClass().stream().anyMatch(CANDIDATE_SCREENS::contains);
    }

    private static TableView<?> findPrimaryTable(Parent root) {
        return root.lookupAll(".table-view").stream()
                .filter(TableView.class::isInstance)
                .map(TableView.class::cast)
                .filter(t -> !t.getStyleClass().contains("no-record-drawer"))
                .findFirst()
                .orElse(null);
    }

    private static void attach(Node root, TableView<?> table) {
        if (Boolean.TRUE.equals(table.getProperties().get("erp-record-drawer"))) return;
        Node target = findCard(table, root);
        Parent parent = target.getParent();
        if (!(parent instanceof Pane pane)) return;

        int index = pane.getChildren().indexOf(target);
        if (index < 0) return;

        VBox drawer = createDrawer(table);
        SplitPane split = new SplitPane(target);
        split.setOrientation(Orientation.HORIZONTAL);
        split.getStyleClass().addAll("register-split", "automatic-register-split");
        split.setDividerPositions(1.0);

        copyGrowConstraints(target, split);
        pane.getChildren().set(index, split);
        table.getProperties().put("erp-record-drawer", true);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, row) -> {
            if (row == null) {
                close(split, drawer);
            } else {
                populate(drawer, table, row);
                if (!split.getItems().contains(drawer)) split.getItems().add(drawer);
                split.setDividerPositions(0.76);
            }
        });
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                if (!split.getItems().contains(drawer)) split.getItems().add(drawer);
                split.setDividerPositions(0.76);
            }
        });
        Button close = (Button) drawer.lookup("#automaticDrawerClose");
        close.setOnAction(e -> {
            table.getSelectionModel().clearSelection();
            close(split, drawer);
        });
    }

    private static Node findCard(TableView<?> table, Node root) {
        Node current = table;
        Node best = table;
        while (current.getParent() != null && current.getParent() != root) {
            current = current.getParent();
            if (current.getStyleClass().stream().anyMatch(c ->
                    c.contains("table-card") || c.contains("register-workspace")
                            || c.contains("flow-card"))) {
                best = current;
                break;
            }
            best = current;
        }
        return best;
    }

    private static VBox createDrawer(TableView<?> table) {
        Label title = new Label("Record Details");
        title.getStyleClass().add("drawer-title");
        Button close = new Button("×");
        close.setId("automaticDrawerClose");
        close.getStyleClass().addAll("icon-button", "approved-button", "approved-secondary-button");
        HBox heading = new HBox(title, new Region(), close);
        HBox.setHgrow(heading.getChildren().get(1), Priority.ALWAYS);

        Label recordTitle = new Label("Select a record");
        recordTitle.setId("automaticDrawerTitle");
        recordTitle.getStyleClass().add("drawer-record-title");
        GridPane values = new GridPane();
        values.setId("automaticDrawerValues");
        values.setHgap(10);
        values.setVgap(8);
        ColumnConstraints key = new ColumnConstraints();
        key.setMinWidth(92);
        ColumnConstraints value = new ColumnConstraints();
        value.setHgrow(Priority.ALWAYS);
        values.getColumnConstraints().addAll(key, value);

        VBox drawer = new VBox(11, heading, recordTitle, new Separator(), values, new Region());
        drawer.setPadding(new Insets(16, 14, 16, 14));
        VBox.setVgrow(drawer.getChildren().get(drawer.getChildren().size() - 1), Priority.ALWAYS);
        drawer.setMinWidth(260);
        drawer.setPrefWidth(310);
        drawer.getStyleClass().addAll("detail-drawer", "automatic-detail-drawer");
        return drawer;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void populate(VBox drawer, TableView table, Object row) {
        GridPane grid = (GridPane) drawer.lookup("#automaticDrawerValues");
        Label title = (Label) drawer.lookup("#automaticDrawerTitle");
        grid.getChildren().clear();
        int gridRow = 0;
        String firstMeaningful = null;
        List<TableColumn> columns = table.getVisibleLeafColumns();
        for (TableColumn column : columns) {
            String heading = column.getText();
            if (heading == null || heading.isBlank()) continue;
            String normalized = heading.toLowerCase(Locale.ROOT);
            if (normalized.contains("action") || normalized.equals("#") || normalized.equals("select")) continue;

            ObservableValue valueObservable;
            try {
                valueObservable = column.getCellObservableValue(row);
            } catch (RuntimeException ex) {
                continue;
            }
            Object raw = valueObservable == null ? null : valueObservable.getValue();
            String value = raw == null || String.valueOf(raw).isBlank() ? "—" : String.valueOf(raw);
            if (firstMeaningful == null && !value.equals("—")) firstMeaningful = value;

            Label key = new Label(heading);
            key.getStyleClass().add("caption");
            Label val = new Label(value);
            val.setWrapText(true);
            val.setMaxWidth(Double.MAX_VALUE);
            grid.add(key, 0, gridRow);
            grid.add(val, 1, gridRow++);
        }
        title.setText(firstMeaningful == null ? "Selected record" : firstMeaningful);
    }

    private static void close(SplitPane split, VBox drawer) {
        split.getItems().remove(drawer);
        split.setDividerPositions(1.0);
    }

    private static void copyGrowConstraints(Node from, Node to) {
        VBox.setVgrow(to, VBox.getVgrow(from));
        HBox.setHgrow(to, HBox.getHgrow(from));
        to.setUserData(from.getUserData());
    }
}
