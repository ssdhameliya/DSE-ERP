package org.example.util;

import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.CheckBox;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.geometry.Pos;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Configures a permanent leading checkbox column for visible table-row selection. */
public final class TableSelectionSupport {
    private TableSelectionSupport() {}

    public static <T> Set<T> install(TableView<T> table, TableColumn<T, Boolean> column) {
        Set<T> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        table.getProperties().put("erp-keep-selection", true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        column.setVisible(true);
        column.setText("");
        column.setSortable(false);
        column.setReorderable(false);
        column.setResizable(false);
        column.setMinWidth(54);
        column.setPrefWidth(54);
        column.setMaxWidth(54);
        column.setCellValueFactory(data -> new ReadOnlyBooleanWrapper(data.getValue() != null));
        CheckBox all = new CheckBox();
        all.setTooltip(new Tooltip("Select all visible rows"));
        all.setOnAction(e -> {
            if (all.isSelected()) selected.addAll(table.getItems()); else selected.clear();
            table.refresh();
        });
        column.setGraphic(all);
        column.setCellFactory(ignored -> new TableCell<>() {
            private final CheckBox box = new CheckBox();
            { box.setOnAction(e -> { T row=getTableRow()==null?null:getTableRow().getItem(); if(row==null)return; if(box.isSelected())selected.add(row);else selected.remove(row); all.setSelected(!table.getItems().isEmpty() && selected.containsAll(table.getItems())); e.consume(); }); }
            @Override protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty); setText(null);
                T row=getTableRow()==null?null:getTableRow().getItem();
                if(empty||row==null){setGraphic(null);return;}
                box.setSelected(selected.contains(row)); setGraphic(box); setAlignment(Pos.CENTER);
            }
        });
        return selected;
    }
}
