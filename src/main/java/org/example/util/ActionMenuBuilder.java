package org.example.util;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;

/** Builds one consistent icon-only row action menu with semantic menu entries. */
public final class ActionMenuBuilder {
    private final MenuButton menu = new MenuButton();

    private ActionMenuBuilder(String tooltip) {
        menu.getStyleClass().addAll("row-actions", "erp-action-menu");
        menu.setText("");
        menu.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        menu.setGraphic(SemanticIconManager.compact("actions", 16));
        menu.setTooltip(new Tooltip(tooltip == null || tooltip.isBlank() ? "Actions" : tooltip));
        menu.setMinWidth(42);
        menu.setPrefWidth(46);
        menu.setMaxWidth(50);
        menu.getProperties().put("erp.action.menu", true);
    }

    public static ActionMenuBuilder create() {
        return new ActionMenuBuilder("Actions");
    }

    public static ActionMenuBuilder create(String tooltip) {
        return new ActionMenuBuilder(tooltip);
    }

    public ActionMenuBuilder item(String text, String semantic, EventHandler<ActionEvent> handler) {
        MenuItem item = new MenuItem(text);
        SemanticIconManager.apply(item, semantic);
        if (handler != null) item.setOnAction(handler);
        menu.getItems().add(item);
        return this;
    }

    public ActionMenuBuilder separator() {
        menu.getItems().add(new SeparatorMenuItem());
        return this;
    }

    public MenuButton build() {
        return menu;
    }
}
