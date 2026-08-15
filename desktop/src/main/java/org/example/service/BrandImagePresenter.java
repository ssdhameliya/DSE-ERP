package org.example.service;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 * Central image-geometry contract for user supplied branding assets.
 *
 * <p>Application branding is a responsive 5:1 banner. It is center-cropped
 * to that ratio and then rendered to the full available width, so the UI never
 * stretches the artwork and individual screens cannot invent their own image
 * dimensions. Logos, signatures and QR images use contain semantics and are
 * never cropped.</p>
 */
public final class BrandImagePresenter {
    public static final double APPLICATION_BANNER_RATIO = 5.0;
    private static final double DEFAULT_INSET = 14.0;

    private BrandImagePresenter() { }

    public static void applicationBanner(ImageView view, Region container) {
        configureApplicationBanner(view, container, false);
    }

    /** Settings preview variant: the preview owns a true 5:1 viewport and clips all artwork. */
    public static void applicationBannerPreview(ImageView view, Region container) {
        configureApplicationBanner(view, container, true);
    }

    private static void configureApplicationBanner(ImageView view, Region container, boolean sizeContainer) {
        if (view == null || container == null) return;
        view.setSmooth(true);
        view.setPreserveRatio(false);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(container.widthProperty());
        clip.heightProperty().bind(container.heightProperty());
        container.setClip(clip);
        Runnable resize = () -> {
            double width = Math.max(1.0, container.getWidth() - DEFAULT_INSET);
            double height = width / APPLICATION_BANNER_RATIO;
            if (sizeContainer && width > 2) {
                double target = height + DEFAULT_INSET;
                container.setMinHeight(target);
                container.setPrefHeight(target);
                container.setMaxHeight(target);
            }
            double availableHeight = Math.max(1.0, container.getHeight() - DEFAULT_INSET);
            if (!sizeContainer && height > availableHeight) {
                height = availableHeight;
                width = height * APPLICATION_BANNER_RATIO;
            }
            view.setFitWidth(width);
            view.setFitHeight(height);
        };
        container.widthProperty().addListener((obs, oldValue, newValue) -> resize.run());
        container.heightProperty().addListener((obs, oldValue, newValue) -> resize.run());
        view.imageProperty().addListener((obs, oldImage, newImage) -> applyApplicationViewport(view, newImage));
        applyApplicationViewport(view, view.getImage());
        resize.run();
    }

    public static void contain(ImageView view, Region container) {
        contain(view, container, 18.0);
    }

    public static void contain(ImageView view, Region container, double totalInset) {
        if (view == null || container == null) return;
        view.setViewport(null);
        view.setSmooth(true);
        view.setPreserveRatio(true);
        Runnable resize = () -> {
            view.setFitWidth(Math.max(1.0, container.getWidth() - totalInset));
            view.setFitHeight(Math.max(1.0, container.getHeight() - totalInset));
        };
        container.widthProperty().addListener((obs, oldValue, newValue) -> resize.run());
        container.heightProperty().addListener((obs, oldValue, newValue) -> resize.run());
        resize.run();
    }

    private static void applyApplicationViewport(ImageView view, Image image) {
        if (image == null || image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
            view.setViewport(null);
            return;
        }

        double sourceWidth = image.getWidth();
        double sourceHeight = image.getHeight();
        double sourceRatio = sourceWidth / sourceHeight;

        if (Math.abs(sourceRatio - APPLICATION_BANNER_RATIO) < 0.001) {
            view.setViewport(new Rectangle2D(0, 0, sourceWidth, sourceHeight));
            return;
        }

        if (sourceRatio > APPLICATION_BANNER_RATIO) {
            double cropWidth = sourceHeight * APPLICATION_BANNER_RATIO;
            double x = Math.max(0, (sourceWidth - cropWidth) / 2.0);
            view.setViewport(new Rectangle2D(x, 0, cropWidth, sourceHeight));
        } else {
            double cropHeight = sourceWidth / APPLICATION_BANNER_RATIO;
            double y = Math.max(0, (sourceHeight - cropHeight) / 2.0);
            view.setViewport(new Rectangle2D(0, y, sourceWidth, cropHeight));
        }
    }
}
