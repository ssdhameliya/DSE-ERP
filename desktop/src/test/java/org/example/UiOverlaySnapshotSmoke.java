package org.example;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.example.theme.ThemeManager;
import org.example.util.ModernDialog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Captures the real application-wide modern dialog in both supported themes. */
public final class UiOverlaySnapshotSmoke extends Application {
    private final File output = new File("target/final-ui-review");

    @Override public void start(Stage owner) {
        output.mkdirs();
        Button anchor = new Button("Open review dialog");
        owner.setScene(new Scene(new StackPane(anchor), 720, 460));
        ThemeManager.applyTheme(owner.getScene());
        owner.show();

        captureDialog(anchor, "dialog-" + ThemeManager.getCurrentTheme().name().toLowerCase() + ".png");
        ThemeManager.toggle(owner.getScene());
        captureDialog(anchor, "dialog-" + ThemeManager.getCurrentTheme().name().toLowerCase() + ".png");
        owner.close();
        Platform.exit();
    }

    private void captureDialog(Button owner, String fileName) {
        PauseTransition capture = new PauseTransition(Duration.millis(350));
        capture.setOnFinished(event -> Window.getWindows().stream()
            .filter(window -> window.isShowing() && window != owner.getScene().getWindow())
            .findFirst()
            .ifPresent(window -> {
                try {
                    WritableImage image = window.getScene().getRoot().snapshot(null, null);
                    BufferedImage buffered = new BufferedImage(
                        (int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < buffered.getHeight(); y++) {
                        for (int x = 0; x < buffered.getWidth(); x++) {
                            buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                        }
                    }
                    ImageIO.write(buffered, "png", new File(output, fileName));
                } catch (Exception exception) {
                    throw new IllegalStateException("Unable to capture dialog", exception);
                } finally {
                    window.hide();
                }
            }));
        capture.play();
        ModernDialog.confirm(
            owner,
            "Confirm cancellation",
            "Are you sure?",
            "Cancel invoice SAL-00004 and restore its stock?"
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}
