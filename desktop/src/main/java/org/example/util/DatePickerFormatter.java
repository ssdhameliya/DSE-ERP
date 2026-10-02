package org.example.util;

import javafx.application.Platform;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Ensures JavaFX DatePicker controls format and parse dates strictly according
 * to the configured ERP company.dateFormat setting (BusinessClock) rather than
 * relying on the host OS locale, with zero regression on underlying LocalDate values.
 *
 * Automatically tracks active DatePickers and refreshes their editor text and
 * prompt text in real time whenever the user modifies the date format setting.
 */
public final class DatePickerFormatter {
    private static final String ATTACHED_KEY = "dse.datepicker.formatted";
    private static final Set<DatePicker> ACTIVE_PICKERS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private DatePickerFormatter() { }

    public static DatePicker attach(DatePicker picker) {
        if (picker == null) return null;
        ACTIVE_PICKERS.add(picker);
        if (Boolean.TRUE.equals(picker.getProperties().get(ATTACHED_KEY))) {
            refresh(picker);
            return picker;
        }
        picker.getProperties().put(ATTACHED_KEY, Boolean.TRUE);

        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : BusinessClock.formatDate(date);
            }

            @Override
            public LocalDate fromString(String string) {
                if (string == null || string.isBlank()) return null;
                try {
                    return BusinessClock.parseDate(string.trim());
                } catch (Exception ignored) {
                    return picker.getValue();
                }
            }
        });

        refresh(picker);

        // Ensure text manually typed by the user without pressing Enter is safely committed on focus lost
        // And when focused, keep display in sync with current format
        if (picker.getEditor() != null) {
            picker.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused) {
                    commitEditorText(picker);
                } else {
                    refresh(picker);
                }
            });
            picker.getEditor().setOnAction(event -> commitEditorText(picker));
        }

        picker.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                refresh(picker);
            }
        });

        return picker;
    }

    public static void attachAll(DatePicker... pickers) {
        if (pickers == null) return;
        for (DatePicker picker : pickers) {
            attach(picker);
        }
    }

    public static void refresh(DatePicker picker) {
        if (picker == null) return;
        updatePromptText(picker);
        LocalDate value = picker.getValue();
        if (picker.getEditor() != null) {
            picker.getEditor().setText(value == null ? "" : BusinessClock.formatDate(value));
        }
        StringConverter<LocalDate> conv = picker.getConverter();
        if (conv != null) {
            picker.setConverter(null);
            picker.setConverter(conv);
        }
    }

    public static void refreshAll() {
        Runnable task = () -> {
            synchronized (ACTIVE_PICKERS) {
                for (DatePicker picker : ACTIVE_PICKERS) {
                    refresh(picker);
                }
            }
        };
        try {
            if (Platform.isFxApplicationThread()) {
                task.run();
            } else {
                Platform.runLater(task);
            }
        } catch (IllegalStateException noToolkit) {
            task.run();
        } catch (Exception ignored) { }
    }

    public static void updatePromptText(DatePicker picker) {
        if (picker == null) return;
        try {
            picker.setPromptText(BusinessClock.datePattern().toLowerCase(Locale.ROOT));
        } catch (Exception ignored) { }
    }

    private static void commitEditorText(DatePicker picker) {
        if (picker == null || picker.getEditor() == null) return;
        String text = picker.getEditor().getText();
        if (text == null || text.trim().isEmpty()) {
            picker.setValue(null);
            return;
        }
        try {
            LocalDate parsed = BusinessClock.parseDate(text.trim());
            picker.setValue(parsed);
            picker.getEditor().setText(BusinessClock.formatDate(parsed));
        } catch (Exception ignored) {
            picker.getEditor().setText(picker.getValue() == null ? "" : BusinessClock.formatDate(picker.getValue()));
        }
    }
}
