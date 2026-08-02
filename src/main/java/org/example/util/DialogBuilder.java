package org.example.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/** Creates owned, themed, cross-platform alerts with semantic graphics. */
public final class DialogBuilder {
    private final Alert alert;

    private DialogBuilder(Alert.AlertType type, Window owner, String title, String header, String content) {
        alert = new OwnedAlert(type, content == null ? "" : content, ButtonType.OK);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle(title == null ? "DSE ERP" : title);
        alert.setHeaderText(header);
        alert.getDialogPane().getProperties().put("erp.dialog.builder", true);
        alert.getDialogPane().setGraphic(SemanticIconManager.tile(semantic(type), 32));
        alert.setOnShown(event -> {
            if (alert.getDialogPane().getScene() != null) {
                org.example.theme.ThemeManager.applyTheme(alert.getDialogPane().getScene());
                PlatformUiSupport.installResponsiveClasses(alert.getDialogPane().getScene());
            }
        });
    }

    public static DialogBuilder information(Window owner, String title, String header, String content) {
        return new DialogBuilder(Alert.AlertType.INFORMATION, owner, title, header, content);
    }

    public static DialogBuilder warning(Window owner, String title, String header, String content) {
        return new DialogBuilder(Alert.AlertType.WARNING, owner, title, header, content);
    }

    public static DialogBuilder error(Window owner, String title, String header, String content) {
        return new DialogBuilder(Alert.AlertType.ERROR, owner, title, header, content);
    }

    public DialogBuilder buttons(ButtonType... buttons) {
        if (buttons != null && buttons.length > 0) alert.getButtonTypes().setAll(buttons);
        return this;
    }

    public Alert build() {
        return alert;
    }

    public void showAndWait() {
        alert.showAndWait();
    }

    private static String semantic(Alert.AlertType type) {
        if (type == Alert.AlertType.ERROR) return "error";
        if (type == Alert.AlertType.WARNING) return "warning";
        if (type == Alert.AlertType.CONFIRMATION) return "question";
        return "notification";
    }
}
