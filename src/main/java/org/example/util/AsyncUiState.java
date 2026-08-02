package org.example.util;

import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ProgressIndicator;
import java.util.Collection;

/** Small shared helper for consistent loading/ready/error states. */
public final class AsyncUiState {
    private AsyncUiState() { }
    public static void loading(ProgressIndicator progress, Labeled message, Collection<? extends Control> controls, String text) {
        if (progress != null) { progress.setVisible(true); progress.setManaged(true); }
        if (message != null) message.setText(text == null ? "Loading…" : text);
        setDisabled(controls, true);
    }
    public static void ready(ProgressIndicator progress, Labeled message, Collection<? extends Control> controls, String text) {
        if (progress != null) { progress.setVisible(false); progress.setManaged(false); }
        if (message != null && text != null) message.setText(text);
        setDisabled(controls, false);
    }
    public static void error(ProgressIndicator progress, Labeled message, Collection<? extends Control> controls, String text) {
        ready(progress, message, controls, text == null ? "Unable to load data." : text);
    }
    private static void setDisabled(Collection<? extends Control> controls, boolean disabled) {
        if (controls != null) controls.forEach(control -> { if (control != null) control.setDisable(disabled); });
    }
}
