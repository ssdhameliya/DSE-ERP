package org.example.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.api.support.SupportApiClient;

import java.util.List;

/** Read-only audit timeline shared by transaction registers. */
public final class ActivityTimelineDialog {
    private ActivityTimelineDialog() { }

    public static void show(Node owner, String entityType, int entityId, String reference) {
        if (owner == null || entityId <= 0) return;
        SupportApiClient api = new SupportApiClient();
        String key = "activity-timeline-" + entityType + "-" + entityId;
        UiTaskExecutor.submitLatest(key,
                () -> api.activity(entityType, entityId),
                rows -> showRows(owner, entityType, reference, rows),
                failure -> new OwnedAlert(Alert.AlertType.ERROR,
                        "Activity timeline could not be loaded.\n\n" + rootMessage(failure)).showAndWait());
    }

    private static void showRows(Node owner, String entityType, String reference,
                                 List<SupportApiClient.ActivityRow> rows) {
        Dialog<Void> dialog = new OwnedDialog<>(owner);
        dialog.setTitle("Activity Timeline");
        dialog.setHeaderText((reference == null || reference.isBlank() ? entityType : reference) + " • Activity Timeline");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(8);
        content.setPadding(new Insets(4));
        content.setPrefWidth(720);
        content.setMinHeight(280);
        if (rows == null || rows.isEmpty()) {
            Label empty = new Label("No activity has been recorded for this document yet.");
            empty.getStyleClass().add("muted-label");
            content.getChildren().add(empty);
        } else {
            for (SupportApiClient.ActivityRow row : rows) content.getChildren().add(activityCard(row));
        }
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(460);
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    private static Node activityCard(SupportApiClient.ActivityRow row) {
        Label action = new Label(pretty(row.action()));
        action.setStyle("-fx-font-weight: 700;");
        Label time = new Label(safe(row.createdAt()));
        time.getStyleClass().add("muted-label");
        HBox top = new HBox(10, action, spacer(), time);
        top.setAlignment(Pos.CENTER_LEFT);
        Label detail = new Label(safe(row.detail()).isBlank() ? "No additional detail" : safe(row.detail()));
        detail.setWrapText(true);
        Label by = new Label("By " + (safe(row.createdBy()).isBlank() ? "System" : safe(row.createdBy())));
        by.getStyleClass().add("muted-label");
        VBox box = new VBox(4, top, detail, by);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: -fx-control-inner-background; -fx-border-color: derive(-fx-text-box-border,20%); -fx-border-radius: 8; -fx-background-radius: 8;");
        return box;
    }

    private static HBox spacer() { HBox box = new HBox(); HBox.setHgrow(box, Priority.ALWAYS); return box; }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String pretty(String value) { return safe(value).replace('_', ' '); }
    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current != null && current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current == null ? "Unknown error" : current.getMessage();
        return message == null || message.isBlank() ? String.valueOf(current) : message;
    }
}
