package org.example.util;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable, ultra-sleek 3D Animated Classy Loading Component for enterprise registers and dialogs.
 * Displays high-end elevated 3D skeleton cards with pulsing shimmer bars, status pills, and
 * realistic data table wireframes while asynchronous background requests stream in data.
 * Zero CPU burn when hidden; safely stops all animations upon detachment from the scene graph.
 */
public final class Classy3DLoadingView extends VBox {

    private final Timeline pulseTimeline;
    private final Timeline dotTimeline;
    private final List<Node> shimmerNodes = new ArrayList<>();

    public Classy3DLoadingView(String headerTitle, String subTitle) {
        this(headerTitle, subTitle, true);
    }

    public Classy3DLoadingView(String headerTitle, String subTitle, boolean includeHeader) {
        setAlignment(Pos.CENTER);
        setSpacing(16);
        setPadding(new Insets(24, 20, 24, 20));
        getStyleClass().addAll("skeleton-3d-root", "erp-ui-standard");
        VBox.setVgrow(this, Priority.ALWAYS);

        // 1. Status Pill Hero with 3D ambient shadow
        if (includeHeader) {
            HBox statusPill = buildStatusHero(headerTitle, subTitle);
            getChildren().add(statusPill);
        }

        // 2. 3D Elevated Table Skeleton Card
        VBox tableSkeleton = buildTableCard();
        VBox.setVgrow(tableSkeleton, Priority.ALWAYS);
        getChildren().add(tableSkeleton);

        // 3. Smooth Breathing & Shimmer Timeline
        pulseTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(opacityProperty(), 0.45, Interpolator.EASE_BOTH),
                        new KeyValue(scaleXProperty(), 0.996, Interpolator.EASE_BOTH),
                        new KeyValue(scaleYProperty(), 0.996, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(850),
                        new KeyValue(opacityProperty(), 0.92, Interpolator.EASE_BOTH),
                        new KeyValue(scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(scaleYProperty(), 1.0, Interpolator.EASE_BOTH)
                )
        );
        pulseTimeline.setAutoReverse(true);
        pulseTimeline.setCycleCount(Animation.INDEFINITE);

        dotTimeline = null; // initialized in buildStatusHero if present

        // Lifecycle hook: auto-stop when removed from Scene
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                start();
            } else {
                stop();
            }
        });

        start();
    }

    public static Classy3DLoadingView createTableSkeleton(String headerTitle, String subTitle) {
        return new Classy3DLoadingView(headerTitle, subTitle, true);
    }

    public static Classy3DLoadingView createCompactSkeleton(String headerTitle, String subTitle) {
        return new Classy3DLoadingView(headerTitle, subTitle, false);
    }

    public static void attachToTable(TableView<?> table, String headerTitle, String subTitle) {
        if (table == null) return;
        Classy3DLoadingView view = new Classy3DLoadingView(headerTitle, subTitle, true);
        table.setPlaceholder(view);
    }

    private HBox buildStatusHero(String headerTitle, String subTitle) {
        Circle pulseDot = new Circle(5.0);
        pulseDot.getStyleClass().add("skeleton-pulse-dot");
        pulseDot.setFill(Color.web("#38bdf8"));
        pulseDot.setEffect(new DropShadow(8, Color.web("#38bdf8", 0.6)));

        Timeline dotAnim = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(pulseDot.opacityProperty(), 0.35, Interpolator.EASE_BOTH), new KeyValue(pulseDot.radiusProperty(), 4.5)),
                new KeyFrame(Duration.millis(650), new KeyValue(pulseDot.opacityProperty(), 1.0, Interpolator.EASE_BOTH), new KeyValue(pulseDot.radiusProperty(), 5.5))
        );
        dotAnim.setAutoReverse(true);
        dotAnim.setCycleCount(Animation.INDEFINITE);
        dotAnim.play();

        Label lblTitle = new Label(headerTitle != null ? headerTitle : "Loading Enterprise Data…");
        lblTitle.getStyleClass().add("skeleton-hero-text");

        Label lblSub = new Label(subTitle != null ? subTitle : "Synchronizing authoritative records…");
        lblSub.getStyleClass().add("skeleton-sub-text");

        VBox textBox = new VBox(2, lblTitle, lblSub);
        textBox.setAlignment(Pos.CENTER_LEFT);

        HBox statusPill = new HBox(12, pulseDot, textBox);
        statusPill.setAlignment(Pos.CENTER_LEFT);
        statusPill.getStyleClass().add("skeleton-pill-hero");
        statusPill.setMaxWidth(680);
        return statusPill;
    }

    private VBox buildTableCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("skeleton-3d-card");
        card.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(card, Priority.ALWAYS);

        // Simulated Header Row
        HBox headerRow = new HBox(14);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(6, 12, 10, 12));
        headerRow.setStyle("-fx-border-color: rgba(255,255,255,0.08); -fx-border-width: 0 0 1px 0;");

        headerRow.getChildren().addAll(
                createSkeletonBar(36, 14, "skeleton-bar-highlight"),
                createSkeletonBar(140, 14, "skeleton-bar-highlight"),
                createSkeletonBar(110, 14, "skeleton-bar-highlight"),
                createSkeletonBar(90, 14, "skeleton-bar-highlight"),
                createSkeletonBar(130, 14, "skeleton-bar-highlight"),
                createSkeletonBar(120, 14, "skeleton-bar-highlight"),
                createSkeletonBar(120, 14, "skeleton-bar-highlight"),
                createSkeletonBar(95, 14, "skeleton-bar-highlight")
        );
        card.getChildren().add(headerRow);

        // 5 Simulated Content Rows
        double[] colWidths = {28, 130, 100, 85, 125, 115, 115, 85};
        for (int i = 0; i < 5; i++) {
            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 12, 8, 12));
            row.setStyle("-fx-background-color: " + (i % 2 == 0 ? "rgba(255,255,255,0.02)" : "transparent") + "; -fx-background-radius: 6px;");

            // Col 1: Index
            row.getChildren().add(createSkeletonBar(colWidths[0], 12, "skeleton-bar-base"));
            // Col 2: Date
            row.getChildren().add(createSkeletonBar(colWidths[1], 12, "skeleton-bar-base"));
            // Col 3: Action badge pill
            row.getChildren().add(createSkeletonBadge(colWidths[2], 20, (i % 2 == 0) ? "skeleton-badge-sky" : "skeleton-badge-emerald"));
            // Col 4: Category
            row.getChildren().add(createSkeletonBar(colWidths[3], 12, "skeleton-bar-base"));
            // Col 5: Field
            row.getChildren().add(createSkeletonBar(colWidths[4], 12, "skeleton-bar-highlight"));
            // Col 6: Old value
            row.getChildren().add(createSkeletonBadge(colWidths[5], 18, "skeleton-badge-amber"));
            // Col 7: New value
            row.getChildren().add(createSkeletonBadge(colWidths[6], 18, "skeleton-badge-emerald"));
            // Col 8: User
            row.getChildren().add(createSkeletonBar(colWidths[7], 12, "skeleton-bar-base"));

            card.getChildren().add(row);
        }

        return card;
    }

    private Region createSkeletonBar(double width, double height, String styleClass) {
        Region bar = new Region();
        bar.setPrefSize(width, height);
        bar.setMinSize(width, height);
        bar.setMaxSize(width, height);
        bar.getStyleClass().addAll("skeleton-bar", styleClass);
        shimmerNodes.add(bar);
        return bar;
    }

    private Region createSkeletonBadge(double width, double height, String styleClass) {
        Region badge = new Region();
        badge.setPrefSize(width, height);
        badge.setMinSize(width, height);
        badge.setMaxSize(width, height);
        badge.getStyleClass().addAll("skeleton-bar", styleClass);
        shimmerNodes.add(badge);
        return badge;
    }

    public void start() {
        if (pulseTimeline != null && pulseTimeline.getStatus() != Animation.Status.RUNNING) {
            pulseTimeline.play();
        }
    }

    public void stop() {
        if (pulseTimeline != null) {
            pulseTimeline.stop();
        }
        if (dotTimeline != null) {
            dotTimeline.stop();
        }
    }

    public void dispose() {
        stop();
        shimmerNodes.clear();
    }
}
