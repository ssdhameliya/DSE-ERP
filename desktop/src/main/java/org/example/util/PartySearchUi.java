package org.example.util;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.util.Duration;
import org.example.model.Party;
import org.example.service.PartyService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Lightweight capped type-ahead for customer/supplier register filters. */
public final class PartySearchUi {
    private static final String INTERNAL_UPDATE_KEY = PartySearchUi.class.getName() + ".internalUpdate";
    private static final String DEBOUNCE_KEY = PartySearchUi.class.getName() + ".debounce";
    private static final String TASK_KEY = PartySearchUi.class.getName() + ".taskKey";

    private PartySearchUi() {}

    public static void install(ComboBox<String> box, String type, String allLabel, String taskKey) {
        if (box == null) return;
        PartyService service = new PartyService();
        PauseTransition debounce = new PauseTransition(Duration.millis(180));
        AtomicBoolean internal = new AtomicBoolean(false);
        box.getProperties().put(DEBOUNCE_KEY, debounce);
        box.getProperties().put(TASK_KEY, taskKey);
        box.setEditable(true);
        internal.set(true);
        setInternalUpdate(box, true);
        try {
            box.getItems().setAll(allLabel);
            box.setValue(allLabel);
        } finally {
            setInternalUpdate(box, false);
            internal.set(false);
        }

        java.util.function.Consumer<String> search = raw -> {
            String query = normalizeQuery(raw, allLabel);
            UiTaskExecutor.submitLatest(taskKey, () -> service.search(type, query, query.isBlank() ? 40 : 30), parties -> {
                String selected = box.getValue();
                String typed = box.getEditor() == null ? "" : box.getEditor().getText();
                LinkedHashSet<String> names = new LinkedHashSet<>();
                names.add(allLabel);
                if (parties != null) for (Party p : parties) {
                    if (p != null && p.getName() != null && !p.getName().isBlank()) names.add(p.getName().trim());
                }
                if (selected != null && !selected.isBlank() && !selected.equalsIgnoreCase(allLabel)) names.add(selected);
                internal.set(true);
                setInternalUpdate(box, true);
                try {
                    box.getItems().setAll(new ArrayList<>(names));
                    if (selected != null && !selected.isBlank()) box.setValue(selected);
                    if (box.getEditor() != null && typed != null && !typed.isBlank() && !typed.equals(selected)) {
                        box.getEditor().setText(typed);
                        box.getEditor().positionCaret(typed.length());
                    }
                } finally {
                    setInternalUpdate(box, false);
                    internal.set(false);
                }
                if (!query.isBlank() && box.isShowing()) Platform.runLater(box::show);
            }, failure -> { /* filter suggestions are non-critical; typed filtering still works */ });
        };

        if (box.getEditor() != null) box.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (internal.get() || isInternalUpdate(box)) return;
            String value = newValue == null ? "" : newValue.trim();
            if (value.equalsIgnoreCase(allLabel)) return;
            debounce.stop();
            debounce.setOnFinished(event -> search.accept(value));
            debounce.playFromStart();
        });
        box.setOnShowing(event -> search.accept(box.getEditor() == null ? "" : box.getEditor().getText()));
        search.accept("");
    }

    public static void preserveSelection(ComboBox<String> box, String selected, String allLabel) {
        if (box == null) return;
        Object debounce = box.getProperties().get(DEBOUNCE_KEY);
        if (debounce instanceof PauseTransition pause) pause.stop();
        Object taskKey = box.getProperties().get(TASK_KEY);
        if (taskKey instanceof String key && !key.isBlank()) UiTaskExecutor.cancel(key);

        String value = selected == null || selected.isBlank() ? allLabel : selected;
        setInternalUpdate(box, true);
        try {
            if (!box.getItems().contains(allLabel)) box.getItems().add(0, allLabel);
            if (!box.getItems().contains(value)) box.getItems().add(value);
            box.setValue(value);
            if (box.isEditable() && box.getEditor() != null) {
                box.getEditor().setText(value);
                box.getEditor().positionCaret(value.length());
            }
        } finally {
            setInternalUpdate(box, false);
        }
    }

    public static boolean isInternalUpdate(ComboBox<?> box) {
        return box != null && Boolean.TRUE.equals(box.getProperties().get(INTERNAL_UPDATE_KEY));
    }

    private static void setInternalUpdate(ComboBox<?> box, boolean value) {
        if (box == null) return;
        if (value) box.getProperties().put(INTERNAL_UPDATE_KEY, Boolean.TRUE);
        else box.getProperties().remove(INTERNAL_UPDATE_KEY);
    }

    private static String normalizeQuery(String raw, String allLabel) {
        String value = raw == null ? "" : raw.trim();
        return value.equalsIgnoreCase(allLabel) ? "" : value;
    }
}
