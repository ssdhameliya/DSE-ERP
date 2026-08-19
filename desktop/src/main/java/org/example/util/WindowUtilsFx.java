package org.example.util;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.prefs.Preferences;

/** Cross-platform primary/secondary window sizing using the monitor usable area. */
public final class WindowUtilsFx {
    private static final double ABSOLUTE_MIN_WIDTH = 760;
    private static final double ABSOLUTE_MIN_HEIGHT = 520;
    private static final double PREFERRED_MIN_WIDTH = 960;
    private static final double PREFERRED_MIN_HEIGHT = 640;
    private static final Preferences PREFS = Preferences.userNodeForPackage(WindowUtilsFx.class);
    private static final String KEY_X = "fx.win.x", KEY_Y = "fx.win.y", KEY_W = "fx.win.w", KEY_H = "fx.win.h", KEY_MAX = "fx.win.max";

    private WindowUtilsFx() {}

    public static void apply(Stage stage, double defaultW, double defaultH) {
        if (stage == null) throw new IllegalArgumentException("Stage must not be null");
        Rectangle2D primary = Screen.getPrimary().getVisualBounds();
        applyAdaptiveMinimums(stage, primary);

        double minW = stage.getMinWidth(), minH = stage.getMinHeight();
        double w = validSize(PREFS.getDouble(KEY_W, defaultW), defaultW, minW);
        double h = validSize(PREFS.getDouble(KEY_H, defaultH), defaultH, minH);
        double x = PREFS.getDouble(KEY_X, Double.NaN);
        double y = PREFS.getDouble(KEY_Y, Double.NaN);
        Rectangle2D bounds = ensureVisibleOnScreens(x, y, w, h);
        stage.setX(bounds.getMinX()); stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth()); stage.setHeight(bounds.getHeight());

        boolean restoreMaximized = PREFS.getBoolean(KEY_MAX, false);
        stage.setOnShown(ev -> Platform.runLater(() -> {
            PlatformUiSupport.installResponsiveClasses(stage.getScene());
            if (restoreMaximized) stage.setMaximized(true);
        }));
        stage.setOnCloseRequest(ev -> save(stage));
    }

    /**
     * Sizes ordinary message/confirmation dialogs to their actual content.
     * Compact dialogs must never inherit the 640x460 workspace minimum used by
     * larger editor/workspace windows.
     */
    public static void fitCompactDialogToOwnerScreen(Stage stage, Window owner) {
        if (stage == null) return;
        Rectangle2D screen = visualBoundsFor(owner);
        double margin = screen.getWidth() < 1100 || screen.getHeight() < 700 ? 16 : 36;
        double maxW = Math.max(360, Math.min(620, screen.getWidth() - margin * 2));
        double maxH = Math.max(220, Math.min(720, screen.getHeight() - margin * 2));

        // Let the DialogPane/CSS compute the natural message size first; then
        // cap it to the owner's usable monitor without imposing workspace mins.
        stage.setMinWidth(0);
        stage.setMinHeight(0);
        if (stage.getScene() != null && stage.getScene().getRoot() != null) {
            stage.getScene().getRoot().applyCss();
            stage.sizeToScene();
        }
        if (stage.getWidth() > maxW) stage.setWidth(maxW);
        if (stage.getHeight() > maxH) stage.setHeight(maxH);
        centreInside(stage, owner, screen);
    }

    /** Keeps workspace/secondary dialogs inside the owner's monitor with a useful editing minimum. */
    public static void fitDialogToOwnerScreen(Stage stage, Window owner) {
        if (stage == null) return;
        Rectangle2D screen = visualBoundsFor(owner);
        double margin = screen.getWidth() < 1100 || screen.getHeight() < 700 ? 16 : 36;
        double maxW = Math.max(320, screen.getWidth() - margin * 2);
        double maxH = Math.max(260, screen.getHeight() - margin * 2);
        stage.setMinWidth(Math.min(Math.max(640, stage.getMinWidth()), maxW));
        stage.setMinHeight(Math.min(Math.max(460, stage.getMinHeight()), maxH));
        if (stage.getWidth() > maxW) stage.setWidth(maxW);
        if (stage.getHeight() > maxH) stage.setHeight(maxH);
        if (stage.getWidth() < stage.getMinWidth()) stage.setWidth(stage.getMinWidth());
        if (stage.getHeight() < stage.getMinHeight()) stage.setHeight(stage.getMinHeight());
        centreInside(stage, owner, screen);
    }

    private static void centreInside(Stage stage, Window owner, Rectangle2D screen) {
        double x = owner != null && owner.isShowing()
            ? owner.getX() + (owner.getWidth() - stage.getWidth()) / 2
            : screen.getMinX() + (screen.getWidth() - stage.getWidth()) / 2;
        double y = owner != null && owner.isShowing()
            ? owner.getY() + (owner.getHeight() - stage.getHeight()) / 2
            : screen.getMinY() + (screen.getHeight() - stage.getHeight()) / 2;
        stage.setX(Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - stage.getWidth())));
        stage.setY(Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - stage.getHeight())));
    }

    public static Rectangle2D visualBoundsFor(Window window) {
        if (window != null) {
            double cx=window.getX()+Math.max(1,window.getWidth())/2, cy=window.getY()+Math.max(1,window.getHeight())/2;
            for (Screen s: Screen.getScreens()) if (s.getVisualBounds().contains(cx,cy)) return s.getVisualBounds();
        }
        return Screen.getPrimary().getVisualBounds();
    }

    public static void applyAdaptiveMinimums(Stage stage, Rectangle2D screen) {
        double minW = Math.min(PREFERRED_MIN_WIDTH, Math.max(ABSOLUTE_MIN_WIDTH, screen.getWidth()*0.78));
        double minH = Math.min(PREFERRED_MIN_HEIGHT, Math.max(ABSOLUTE_MIN_HEIGHT, screen.getHeight()*0.78));
        stage.setMinWidth(Math.min(minW, screen.getWidth()));
        stage.setMinHeight(Math.min(minH, screen.getHeight()));
    }

    private static void save(Stage stage) {
        PREFS.putBoolean(KEY_MAX, stage.isMaximized());
        if (stage.isMaximized() || stage.isIconified() || stage.isFullScreen()) return;
        if (Double.isFinite(stage.getX())) PREFS.putDouble(KEY_X, stage.getX());
        if (Double.isFinite(stage.getY())) PREFS.putDouble(KEY_Y, stage.getY());
        if (Double.isFinite(stage.getWidth()) && stage.getWidth() >= ABSOLUTE_MIN_WIDTH) PREFS.putDouble(KEY_W, stage.getWidth());
        if (Double.isFinite(stage.getHeight()) && stage.getHeight() >= ABSOLUTE_MIN_HEIGHT) PREFS.putDouble(KEY_H, stage.getHeight());
    }

    public static void resetSavedBounds() { for (String key : new String[]{KEY_X,KEY_Y,KEY_W,KEY_H,KEY_MAX}) PREFS.remove(key); }
    private static double validSize(double saved,double fallback,double minimum){ return !Double.isFinite(saved)||saved<minimum?Math.max(fallback,minimum):saved; }

    private static Rectangle2D ensureVisibleOnScreens(double x,double y,double w,double h){
        if(Double.isFinite(x)&&Double.isFinite(y)) for(Screen s:Screen.getScreens()){Rectangle2D b=s.getVisualBounds();if(b.intersects(x,y,Math.max(1,w),Math.max(1,h)))return clamp(b,x,y,w,h);}
        Rectangle2D b=Screen.getPrimary().getVisualBounds(); double nw=Math.min(w,b.getWidth()),nh=Math.min(h,b.getHeight());
        return new Rectangle2D(b.getMinX()+(b.getWidth()-nw)/2,b.getMinY()+(b.getHeight()-nh)/2,nw,nh);
    }
    private static Rectangle2D clamp(Rectangle2D b,double x,double y,double w,double h){double nw=Math.min(w,b.getWidth()),nh=Math.min(h,b.getHeight());double nx=Math.max(b.getMinX(),Math.min(x,b.getMaxX()-nw));double ny=Math.max(b.getMinY(),Math.min(y,b.getMaxY()-nh));return new Rectangle2D(nx,ny,nw,nh);}
}
