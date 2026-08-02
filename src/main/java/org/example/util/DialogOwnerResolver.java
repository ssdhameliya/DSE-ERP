package org.example.util;

import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Resolves the primary ERP window for owned dialogs on every platform. */
public final class DialogOwnerResolver {
    private DialogOwnerResolver() {}

    public static Window resolve(Node node) {
        if (node != null && node.getScene() != null && node.getScene().getWindow() != null) {
            return node.getScene().getWindow();
        }
        Window focused = Window.getWindows().stream()
                .filter(Window::isShowing)
                .filter(Window::isFocused)
                .filter(DialogOwnerResolver::isPrimaryCandidate)
                .findFirst().orElse(null);
        if (focused != null) return focused;
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .filter(DialogOwnerResolver::isPrimaryCandidate)
                .findFirst().orElse(null);
    }

    public static Window resolve() { return resolve(null); }

    private static boolean isPrimaryCandidate(Window window) {
        if (!(window instanceof Stage stage)) return false;
        if (stage.getOwner() != null) return false;
        String title = stage.getTitle() == null ? "" : stage.getTitle().toLowerCase();
        return !title.contains("toast") && !title.contains("notification");
    }
}
