package org.example.util;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import org.example.service.SessionService;

import java.util.*;
import java.util.prefs.Preferences;

/** Per-user persistence for register column visibility and order. Width is Phase 5 dynamic. */
public final class RegisterColumnPreferences {
    private RegisterColumnPreferences() { }

    public static void install(TableView<?> table, String screenKey) {
        if (table == null || screenKey == null || screenKey.isBlank()) return;
        if (table.getProperties().putIfAbsent("dse.register.columns.installed", Boolean.TRUE) != null) return;
        table.setTableMenuButtonVisible(true);
        Platform.runLater(() -> {
            installReadableChooser(table);
            restoreAndListen(table, screenKey);
        });
    }

    private static void installReadableChooser(TableView<?> table) {
        Runnable bind = () -> {
            Node chooser = table.lookup(".show-hide-columns-button");
            if (chooser == null || chooser.getProperties().putIfAbsent("dse.readable-column-menu", Boolean.TRUE) != null) return;
            chooser.setOnMouseClicked(e -> {
                e.consume();
                showColumnMenu(table, chooser);
            });
            chooser.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) e.consume();
            });
        };
        bind.run();
        if (table.getScene() == null) Platform.runLater(bind);
    }

    private static void showColumnMenu(TableView<?> table, Node anchor) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("register-column-chooser-menu");
        for (TableColumn<?, ?> column : table.getColumns()) {
            String labelText = column.getText() == null || column.getText().isBlank() ? key(column) : column.getText();
            CheckBox check = new CheckBox();
            check.setSelected(column.isVisible());
            check.setMouseTransparent(true);
            Label label = new Label(labelText);
            label.setMaxWidth(Double.MAX_VALUE);
            label.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
            javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(8, check, IconFactory.compactIcon(semanticIcon(labelText), 15), label);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            MenuItem item = new MenuItem();
            item.setGraphic(row);
            item.setOnAction(e -> {
                if (column.isVisible() && table.getVisibleLeafColumns().size() <= 1) {
                    check.setSelected(true);
                    return;
                }
                boolean selected = !column.isVisible();
                column.setVisible(selected);
                check.setSelected(selected);
            });
            menu.getItems().add(item);
        }
        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        CheckBox allCheck = new CheckBox();
        allCheck.setSelected(table.getColumns().stream().allMatch(TableColumn::isVisible));
        allCheck.setMouseTransparent(true);
        Label allLabel = new Label("Show all columns");
        javafx.scene.layout.HBox allRow = new javafx.scene.layout.HBox(8, allCheck, IconFactory.compactIcon("view", 15), allLabel);
        allRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        MenuItem all = new MenuItem();
        all.setGraphic(allRow);
        all.setOnAction(e -> {
            boolean show = !table.getColumns().stream().allMatch(TableColumn::isVisible);
            table.getColumns().forEach(c -> c.setVisible(show));
            allCheck.setSelected(show);
        });
        menu.getItems().add(all);
        menu.show(anchor, Side.BOTTOM, -322, 0);
    }

    private static String semanticIcon(String title) {
        String v = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (v.contains("date")) return "calendar";
        if (v.contains("customer") || v.contains("supplier")) return "user";
        if (v.contains("mobile") || v.contains("phone")) return "phone";
        if (v.contains("mail") || v.contains("email")) return "email";
        if (v.contains("amount") || v.contains("paid") || v.contains("pending") || v.contains("balance")) return "currency";
        if (v.contains("status")) return "complete";
        if (v.contains("action")) return "list";
        if (v.contains("gst") || v.contains("tax")) return "tax";
        if (v.contains("invoice") || v.contains("quotation") || v.contains("document") || v.contains("code")) return "document";
        if (v.contains("item")) return "item";
        return "view";
    }

    private static void restoreAndListen(TableView<?> table, String screenKey) {
        Preferences prefs = prefs(screenKey);
        boolean[] restoring = {true};
        try {
            restoreOrder(table, prefs.get("order", ""));
            for (TableColumn<?, ?> column : table.getColumns()) {
                String key = key(column);
                column.setVisible(prefs.getBoolean(key + ".visible", column.isVisible()));
                // Remove legacy per-user widths once. Phase 5 sizes from live content.
                prefs.remove(key + ".width");
            }
        } finally {
            restoring[0] = false;
        }

        for (TableColumn<?, ?> column : table.getColumns()) {
            String key = key(column);
            column.visibleProperty().addListener((o, a, b) -> {
                if (!restoring[0]) prefs.putBoolean(key + ".visible", b);
            });
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        ListChangeListener orderListener = change -> {
            if (!restoring[0]) prefs.put("order", table.getColumns().stream().map(RegisterColumnPreferences::key).reduce((a,b)->a+","+b).orElse(""));
        };
        table.getColumns().addListener(orderListener);
    }

    private static void restoreOrder(TableView<?> table, String saved) {
        if (saved == null || saved.isBlank()) return;
        Map<String, TableColumn<?, ?>> byKey = new LinkedHashMap<>();
        for (TableColumn<?, ?> c : table.getColumns()) byKey.put(key(c), c);
        List<TableColumn<?, ?>> ordered = new ArrayList<>();
        for (String token : saved.split(",")) {
            TableColumn<?, ?> c = byKey.remove(token);
            if (c != null) ordered.add(c);
        }
        ordered.addAll(byKey.values());
        if (ordered.size() == table.getColumns().size()) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            List raw = ordered;
            table.getColumns().setAll(raw);
        }
    }

    private static Preferences prefs(String screenKey) {
        String user = SessionService.current() == null ? "anonymous" : String.valueOf(SessionService.current().getId());
        return Preferences.userRoot().node("org/example/dseerp/register-columns/" + safe(user) + "/" + safe(screenKey));
    }

    private static String key(TableColumn<?, ?> c) {
        String value = c.getId();
        if (value == null || value.isBlank()) value = c.getText();
        return safe(value == null || value.isBlank() ? "column" : value);
    }

    private static String safe(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }
}
