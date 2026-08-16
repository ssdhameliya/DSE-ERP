package org.example.util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.documentstudio.util.PdfPreviewSupport;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owned in-application PDF-only preview. It deliberately contains no ERP shell,
 * sidebar, application header or footer.
 */
public final class PdfPreviewDialog {
    private PdfPreviewDialog() {}

    public static void show(Node owner, Path pdf, String title) {
        try {
            if (pdf == null || !Files.isRegularFile(pdf)) throw new IllegalArgumentException("Invoice PDF was not generated.");
            var size = PdfPreviewSupport.pageSize(pdf, 0);
            int pageCount = size.pageCount();
            int[] page = {0};

            ImageView image = new ImageView();
            image.setPreserveRatio(true);
            image.setSmooth(true);
            image.setFitWidth(820);

            ScrollPane scroll = new ScrollPane(image);
            scroll.setFitToWidth(true);
            scroll.setPannable(true);
            scroll.setPrefViewportWidth(850);
            scroll.setPrefViewportHeight(690);
            scroll.getStyleClass().addAll("clean-scroll", "pdf-only-preview-scroll");

            Button previous = new Button("Previous", IconFactory.compactIcon("previous", 15));
            Button next = new Button("Next", IconFactory.compactIcon("next", 15));
            Label pageLabel = new Label();
            pageLabel.getStyleClass().add("pdf-only-preview-page-label");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox toolbar = new HBox(8, previous, next, spacer, pageLabel);
            toolbar.setAlignment(Pos.CENTER_LEFT);
            toolbar.getStyleClass().add("pdf-only-preview-toolbar");

            Runnable render = () -> {
                try {
                    image.setImage(PdfPreviewSupport.renderPage(pdf, page[0], 105));
                    pageLabel.setText("Page " + (page[0] + 1) + " / " + pageCount);
                    previous.setDisable(page[0] <= 0);
                    next.setDisable(page[0] >= pageCount - 1);
                } catch (Exception error) {
                    throw new IllegalStateException("Unable to render invoice PDF preview.", error);
                }
            };
            previous.setOnAction(e -> { if (page[0] > 0) { page[0]--; render.run(); } });
            next.setOnAction(e -> { if (page[0] < pageCount - 1) { page[0]++; render.run(); } });
            render.run();

            VBox content = new VBox(8, toolbar, scroll);
            content.getStyleClass().add("pdf-only-preview");
            VBox.setVgrow(scroll, Priority.ALWAYS);

            OwnedDialog<Void> dialog = new OwnedDialog<>(owner);
            dialog.setTitle(title == null || title.isBlank() ? "Sale Invoice" : title);
            dialog.setHeaderText(null);
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setPrefSize(900, 790);
            dialog.setResizable(true);
            dialog.showAndWait();
        } catch (Exception error) {
            new OwnedAlert(javafx.scene.control.Alert.AlertType.ERROR,
                    error.getMessage() == null ? "Unable to preview the invoice PDF." : error.getMessage()).showAndWait();
        }
    }
}
