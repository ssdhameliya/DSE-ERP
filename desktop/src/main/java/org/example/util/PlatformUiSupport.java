package org.example.util;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.OverrunStyle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.example.theme.ThemeManager;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.Locale;

/**
 * Cross-platform JavaFX sizing and window ownership helpers.
 *
 * <p>JavaFX uses different font metrics and window behaviour on macOS and
 * Windows. This class keeps those differences in one place instead of adding
 * platform checks throughout controllers.</p>
 */
public final class PlatformUiSupport {
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    private PlatformUiSupport() {
    }

    public static boolean isMac() {
        return OS.contains("mac");
    }

    public static boolean isWindows() {
        return OS.contains("win");
    }

    /** Adds stable platform and responsive-size classes to a scene root. */
    public static void installResponsiveClasses(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        Node root = scene.getRoot();
        addOnce(root, isMac() ? "platform-macos" : isWindows() ? "platform-windows" : "platform-other");

        if (!Boolean.TRUE.equals(scene.getProperties().putIfAbsent("dse.responsive.installed", Boolean.TRUE))) {
            ChangeListener<Number> listener = (observable, oldValue, newValue) -> updateSizeClasses(scene);
            scene.widthProperty().addListener(listener);
            scene.heightProperty().addListener(listener);
        }
        updateSizeClasses(scene);
    }

    private static void updateSizeClasses(Scene scene) {
        Node root = scene.getRoot();
        double width = scene.getWidth();
        double height = scene.getHeight();
        boolean compact = width < 1500 || height < 850;
        boolean narrow = width < 1250;
        boolean ultraCompact = width < 1220 || height < 720;
        boolean smallDisplay = width < 1050 || height < 650;
        toggle(root, "compact-shell", compact);
        toggle(root, "narrow-shell", narrow);
        toggle(root, "ultra-compact-shell", ultraCompact);
        toggle(root, "small-display", smallDisplay);
        setVisibleManaged(root.lookup("#shellClockCard"), !compact);
        setVisibleManaged(root.lookup("#shellSearch"), !ultraCompact);
        setVisibleManaged(root.lookup("#shellNewSale"), !ultraCompact);
        setVisibleManaged(root.lookup("#shellTheme"), !ultraCompact);
    }

    public static void configureTextOverflow(Labeled labeled) {
        if (labeled == null) return;
        labeled.setTextOverrun(OverrunStyle.ELLIPSIS);
        labeled.setEllipsisString("…");
    }

    /**
     * Configures a secondary form as an owned window-modal dialog.
     * On macOS this prevents the form behaving like an unrelated application
     * window and keeps it centred over the ERP shell.
     */
    public static void configureDialogStage(Stage stage, Node ownerNode, String title, boolean resizable) {
        if (stage == null) return;
        if (isMac()) stage.initStyle(StageStyle.UTILITY);
        else stage.initStyle(StageStyle.TRANSPARENT);
        Window owner = ownerNode != null && ownerNode.getScene() != null
            ? ownerNode.getScene().getWindow() : DialogOwnerResolver.resolve(ownerNode);
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }
        stage.setTitle(SceneManager.brandedWindowTitle(title));
        stage.setResizable(resizable);
        stage.addEventHandler(WindowEvent.WINDOW_SHOWING, event -> prepareDialogStage(stage, owner, title));
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> {
            if (stage.getScene() != null && stage.getScene().getRoot() != null) {
                UiDiagnostics.audit(stage.getScene().getRoot(), "dialogStage:" + (title == null ? "" : title));
                animateEntrance(stage.getScene().getRoot());
            }
        });
    }

    public static void prepareDialogStage(Stage stage, Window owner, String title) {
        if (stage == null) return;
        Scene scene = stage.getScene();
        if (scene == null) return;
        Window resolvedOwner = owner != null ? owner : stage.getOwner();
        if (resolvedOwner == null) resolvedOwner = DialogOwnerResolver.resolve();

        if (!isMac()) scene.setFill(Color.TRANSPARENT);
        ThemeManager.applyTheme(scene, resolvedOwner);
        installResponsiveClasses(scene);
        Parent root = scene.getRoot();
        if (root != null) {
            decorateEntityDialogRoot(stage, root, title);
            if (!root.getStyleClass().contains("erp-modal-window-root")) {
                root.getStyleClass().add("erp-modal-window-root");
            }
            if (!root.getStyleClass().contains("erp-ui-standard")) {
                root.getStyleClass().add("erp-ui-standard");
            }
            if (!root.getStyleClass().contains("entity-dialog")) {
                root.getStyleClass().add("entity-dialog");
            }
            ProfessionalUiEnhancer.enhance(stage.getScene().getRoot());
            applyScreenTitleSemantic(root, title);
            root.applyCss();
            root.layout();
        }
        WindowUtilsFx.fitDialogToOwnerScreen(stage, resolvedOwner);
    }

    public static void animateEntrance(Node node) {
        if (node == null) return;
        node.setScaleX(0.94);
        node.setScaleY(0.94);
        node.setOpacity(0.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(220), node);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0));

        FadeTransition fade = new FadeTransition(Duration.millis(180), node);
        fade.setToValue(1.0);

        ParallelTransition parallel = new ParallelTransition(scale, fade);
        parallel.setOnFinished(e -> {
            node.setScaleX(1.0);
            node.setScaleY(1.0);
            node.setOpacity(1.0);
        });
        parallel.play();
    }

    public static void decorateEntityDialogRoot(Stage stage, Parent root, String title) {
        if (stage == null || root == null) return;
        if (Boolean.TRUE.equals(root.getProperties().get("dse-entity-dialog-decorated"))) return;
        root.getProperties().put("dse-entity-dialog-decorated", true);

        Node headerNode = root.lookup(".entity-dialog-header");
        if (headerNode == null && root instanceof BorderPane bp) {
            Node top = bp.getTop();
            if (top instanceof HBox) {
                headerNode = top;
            } else if (top instanceof VBox vb && !vb.getChildren().isEmpty()) {
                for (Node child : vb.getChildren()) {
                    if (child instanceof HBox) {
                        headerNode = child;
                        break;
                    }
                }
            }
        }
        if (headerNode instanceof HBox headerHBox) {
            final double[] dragDelta = new double[2];
            headerHBox.setOnMousePressed(mouseEvent -> {
                dragDelta[0] = stage.getX() - mouseEvent.getScreenX();
                dragDelta[1] = stage.getY() - mouseEvent.getScreenY();
            });
            headerHBox.setOnMouseDragged(mouseEvent -> {
                stage.setX(mouseEvent.getScreenX() + dragDelta[0]);
                stage.setY(mouseEvent.getScreenY() + dragDelta[1]);
            });

            if (headerHBox.lookup(".dse-dialog-close") == null) {
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button closeBtn = new Button();
                closeBtn.setGraphic(SemanticIconManager.compact("close", 14));
                closeBtn.getStyleClass().addAll("dse-dialog-close", "icon-button");
                closeBtn.setTooltip(new Tooltip("Close"));
                closeBtn.setCancelButton(true);
                closeBtn.setFocusTraversable(false);
                closeBtn.setOnAction(e -> stage.close());

                headerHBox.getChildren().addAll(spacer, closeBtn);
            }
        }

        if (root instanceof BorderPane borderPane) {
            Node currentTop = borderPane.getTop();
            if (currentTop != null && !(currentTop instanceof VBox vb && vb.getStyleClass().contains("dse-stage-header-wrap"))) {
                Region accent = new Region();
                accent.getStyleClass().add("dse-dialog-accent");
                accent.setMinHeight(4);
                accent.setPrefHeight(4);
                accent.setMaxHeight(4);

                VBox headerWrap = new VBox(accent, currentTop);
                headerWrap.getStyleClass().add("dse-stage-header-wrap");
                borderPane.setTop(headerWrap);
            }
        }
    }


    private static void applyScreenTitleSemantic(Node root, String title) {
        if (root == null) return;
        String semantic = ScreenIdentity.styleClass(title);
        for (Node node : root.lookupAll(".entity-dialog-title")) {
            node.getStyleClass().removeIf(style -> style != null && style.startsWith("screen-title-"));
            if (!node.getStyleClass().contains(semantic)) node.getStyleClass().add(semantic);
        }
    }

    private static void setVisibleManaged(Node node, boolean visible) {
        if (node == null) return;
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static void addOnce(Node node, String styleClass) {
        if (!node.getStyleClass().contains(styleClass)) node.getStyleClass().add(styleClass);
    }

    private static void toggle(Node node, String styleClass, boolean enabled) {
        if (enabled) addOnce(node, styleClass);
        else node.getStyleClass().remove(styleClass);
    }
}
