package org.example.util;

import javafx.beans.value.ChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;

/**
 * Single owner for authentication/startup split geometry.
 *
 * <p>FXML and theme CSS do not own brand-panel width. SceneManager applies the
 * initial width before the scene is displayed, then this manager alone tracks
 * later scene resizes.</p>
 */
public final class AuthLayoutManager {
    private static final String LISTENER_KEY = "erp.auth.width-listener";
    private static final double MIN_BRAND_WIDTH = 360;
    private static final double MAX_BRAND_WIDTH = 690;
    private static final double BRAND_RATIO = .46;

    private AuthLayoutManager() {}

    public static void prepare(Parent root, double sceneWidth) {
        apply(root, sceneWidth);
    }

    public static void install(Parent root, Scene scene) {
        if (!isAuthRoot(root) || scene == null) return;
        apply(root, scene.getWidth());
        if (Boolean.TRUE.equals(root.getProperties().get(LISTENER_KEY))) return;
        ChangeListener<Number> listener = (observable, oldWidth, newWidth) -> apply(root, newWidth.doubleValue());
        scene.widthProperty().addListener(listener);
        root.getProperties().put(LISTENER_KEY, true);
        root.getProperties().put(LISTENER_KEY + ".ref", listener);
    }

    private static void apply(Parent root, double sceneWidth) {
        if (!isAuthRoot(root)) return;
        double width = Math.max(MIN_BRAND_WIDTH, Math.min(MAX_BRAND_WIDTH, sceneWidth * BRAND_RATIO));
        root.lookupAll(".auth-unified-brand-panel").forEach(node -> {
            if (node instanceof Region region) {
                region.setMinWidth(width);
                region.setPrefWidth(width);
                region.setMaxWidth(width);
            }
        });
    }

    private static boolean isAuthRoot(Parent root) {
        return root != null && root.getStyleClass().contains("auth-page");
    }
}
