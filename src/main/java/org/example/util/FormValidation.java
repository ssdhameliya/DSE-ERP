package org.example.util;

import javafx.scene.control.Control;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;

import java.util.regex.Pattern;

/** Shared validation state and messages for ERP forms. */
public final class FormValidation {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private FormValidation() {}

    public static boolean required(TextInputControl field, String label) {
        return validate(field, field != null && field.getText() != null && !field.getText().isBlank(), label + " is required.");
    }

    public static boolean email(TextInputControl field, String label) {
        String text = field == null ? "" : field.getText();
        return validate(field, text == null || text.isBlank() || EMAIL.matcher(text.trim()).matches(), "Enter a valid " + label + ".");
    }

    public static boolean positiveNumber(TextInputControl field, String label) {
        boolean valid;
        try { valid = field != null && Double.parseDouble(field.getText().trim()) > 0; }
        catch (Exception ignored) { valid = false; }
        return validate(field, valid, label + " must be greater than zero.");
    }

    public static boolean validate(Control control, boolean valid, String message) {
        if (control == null) return false;
        control.getStyleClass().remove("validation-error");
        if (valid) {
            control.setTooltip(null);
            return true;
        }
        control.getStyleClass().add("validation-error");
        control.setTooltip(new Tooltip(message));
        control.requestFocus();
        return false;
    }

    public static void clear(Control control) {
        if (control == null) return;
        control.getStyleClass().remove("validation-error");
        control.setTooltip(null);
    }
}
