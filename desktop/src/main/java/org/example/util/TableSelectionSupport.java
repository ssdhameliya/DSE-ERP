package org.example.util;

import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.geometry.Pos;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Configures the leading table column as a user-friendly row number.
 *
 * <p>The original implementation displayed selection checkboxes, but no caller
 * consumed its selected-item set. Keeping this compatibility method lets the
 * existing controllers remain unchanged while presenting stable 1-based row
 * numbers throughout the application.</p>
 */
public final class TableSelectionSupport {
    private TableSelectionSupport() {}

    public static <T> Set<T> install(TableView<T> table, TableColumn<T, Boolean> column) {
        Set<T> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        column.setText("No.");
        IconFactory.applyTableHeaderIcon(column, "quantity");
        column.setSortable(false);
        column.setReorderable(false);
        column.setMinWidth(62);
        column.setPrefWidth(62);
        column.setMaxWidth(62);
        column.setCellValueFactory(data -> new ReadOnlyBooleanWrapper(true));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(null);
                setText(empty || getIndex() < 0 ? null : Integer.toString(getIndex() + 1));
                setAlignment(Pos.CENTER);
            }
        });
        return selected;
    }
}
