package org.example.util;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;

/** Shared toolbar construction for consistent register and master-page actions. */
public final class ToolbarBuilder {
    private final HBox bar = new HBox(8);

    private ToolbarBuilder() {
        bar.getStyleClass().add("erp-toolbar");
        bar.setAlignment(Pos.CENTER_LEFT);
    }

    public static ToolbarBuilder create() {
        return new ToolbarBuilder();
    }

    public ToolbarBuilder button(String text, String semantic, EventHandler<ActionEvent> handler) {
        Button button = new Button(text);
        UiActionIcons.apply(button, semantic);
        if (handler != null) button.setOnAction(handler);
        bar.getChildren().add(button);
        return this;
    }

    public ToolbarBuilder separator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("erp-toolbar-separator");
        bar.getChildren().add(separator);
        return this;
    }

    public HBox build() {
        return bar;
    }
}
