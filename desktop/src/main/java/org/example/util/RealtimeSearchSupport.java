package org.example.util;

import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.time.Duration;

/** Shared real-time search behavior: type -> immediately refresh matching records. */
public final class RealtimeSearchSupport {
    private static final String INSTALLED = "erp.realtime.search.installed";

    private RealtimeSearchSupport() {}

    /** Local/in-memory search: no artificial delay. */
    public static FxDebouncer installLocal(TextField field, Runnable searchAction) {
        return install(field, searchAction, Duration.ZERO);
    }

    /** Server-backed search: short debounce prevents request storms while remaining immediate to users. */
    public static FxDebouncer installRemote(TextField field, Runnable searchAction) {
        return install(field, searchAction, Duration.ofMillis(180));
    }

    public static FxDebouncer install(TextField field, Runnable searchAction, Duration delay) {
        FxDebouncer debouncer = new FxDebouncer(delay == null ? Duration.ofMillis(180) : delay);
        if (field == null || searchAction == null) return debouncer;
        if (Boolean.TRUE.equals(field.getProperties().get(INSTALLED))) return debouncer;
        field.getProperties().put(INSTALLED, true);
        if (!field.getStyleClass().contains(UiDesignSystem.SEARCH)) field.getStyleClass().add(UiDesignSystem.SEARCH);
        field.textProperty().addListener((obs, oldValue, newValue) -> debouncer.submit(searchAction));
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && field.getText() != null && !field.getText().isEmpty()) {
                field.clear();
                event.consume();
            }
        });
        return debouncer;
    }
}
