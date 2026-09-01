package org.example.service;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.util.Duration;
import org.example.util.SceneManager;
import org.example.util.OwnedDialog;
import org.example.util.UiActionIcons;
import org.example.api.support.SupportApiClient;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashSet;
import java.util.Set;

/**
 * Central authenticated-desktop idle policy.
 * Only genuine user input resets the clock; background refresh, clocks and API polling do not.
 */
public final class SessionActivityManager {
    private static final int DEFAULT_IDLE_TIMEOUT_SECONDS = 10 * 60;
    private static final int DEFAULT_WARNING_SECONDS = 2 * 60;
    private volatile int idleTimeoutSeconds = DEFAULT_IDLE_TIMEOUT_SECONDS;
    private volatile int warningSeconds = DEFAULT_WARNING_SECONDS;

    private static final SessionActivityManager INSTANCE = new SessionActivityManager();
    private final Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
    private final AtomicBoolean loggingOut = new AtomicBoolean(false);
    private Scene authenticatedScene;
    private final Set<Scene> observedScenes = new HashSet<>();
    private long lastActivityNanos;
    private OwnedDialog<ButtonType> warning;
    private Label warningCountdown;

    private final EventHandler<Event> activityHandler = event -> {
        if (warning != null || loggingOut.get()) return;
        if (event instanceof MouseEvent mouse && mouse.getEventType() == MouseEvent.MOUSE_MOVED) return;
        markActivity();
    };

    private SessionActivityManager() {
        ticker.setCycleCount(Timeline.INDEFINITE);
    }

    public static void install(Scene authenticatedScene) { INSTANCE.attach(authenticatedScene); }
    public static void stop() { INSTANCE.detach(); }
    public static void activity() { INSTANCE.markActivity(); }
    public static void reloadPolicy() { INSTANCE.loadPolicy(); }

    private void attach(Scene target) {
        detach();
        if (target == null || SessionService.current() == null) return;
        authenticatedScene = target;
        observe(target);
        observeOpenWindows();
        loadPolicy();
        markActivity();
        ticker.playFromStart();
    }

    private void detach() {
        ticker.stop();
        for (Scene observed : new HashSet<>(observedScenes)) unobserve(observed);
        observedScenes.clear();
        authenticatedScene = null;
        closeWarning();
        loggingOut.set(false);
    }


    private void observeOpenWindows() {
        for (Window window : Window.getWindows()) {
            if (window != null && window.isShowing() && window.getScene() != null) observe(window.getScene());
        }
    }

    private void observe(Scene target) {
        if (target == null || !observedScenes.add(target)) return;
        target.addEventFilter(KeyEvent.ANY, activityHandler);
        target.addEventFilter(MouseEvent.MOUSE_PRESSED, activityHandler);
        target.addEventFilter(ScrollEvent.ANY, activityHandler);
        target.addEventFilter(TouchEvent.ANY, activityHandler);
    }

    private void unobserve(Scene target) {
        if (target == null) return;
        target.removeEventFilter(KeyEvent.ANY, activityHandler);
        target.removeEventFilter(MouseEvent.MOUSE_PRESSED, activityHandler);
        target.removeEventFilter(ScrollEvent.ANY, activityHandler);
        target.removeEventFilter(TouchEvent.ANY, activityHandler);
    }

    private void loadPolicy() {
        try {
            SupportApiClient support = new SupportApiClient();
            int timeoutMinutes = Integer.parseInt(support.setting("security.session.timeout.minutes", "10").trim());
            int warningMinutes = Integer.parseInt(support.setting("security.session.warning.minutes", "2").trim());
            timeoutMinutes = Math.max(5, Math.min(120, timeoutMinutes));
            warningMinutes = Math.max(1, Math.min(timeoutMinutes - 1, warningMinutes));
            idleTimeoutSeconds = timeoutMinutes * 60;
            warningSeconds = warningMinutes * 60;
        } catch (Exception ignored) {
            idleTimeoutSeconds = DEFAULT_IDLE_TIMEOUT_SECONDS; warningSeconds = DEFAULT_WARNING_SECONDS;
        }
    }

    private void markActivity() { lastActivityNanos = System.nanoTime(); }

    private int idleSeconds() {
        return (int) Math.max(0, (System.nanoTime() - lastActivityNanos) / 1_000_000_000L);
    }

    private void tick() {
        if (authenticatedScene == null || SessionService.current() == null || loggingOut.get()) return;
        observeOpenWindows();
        int idle = idleSeconds();
        if (idle >= idleTimeoutSeconds) {
            autoLogout();
            return;
        }
        if (idle >= idleTimeoutSeconds - warningSeconds) {
            if (warning == null) showWarning();
            updateCountdown(idleTimeoutSeconds - idle);
        }
    }

    private void showWarning() {
        OwnedDialog<ButtonType> dialog = new OwnedDialog<>();
        warning = dialog;
        dialog.setTitle("Session Expiring");
        dialog.setHeaderText("Your DSE ERP session is about to expire");
        dialog.getDialogPane().getStyleClass().addAll("modern-dialog", "session-timeout-dialog", "approved-dialog");
        warningCountdown = new Label();
        warningCountdown.getStyleClass().add("session-timeout-countdown");
        Label message = new Label("You have been inactive. Unsaved changes may be lost if the session expires.");
        message.setWrapText(true);
        ButtonType stay = new ButtonType("Stay Signed In", ButtonBar.ButtonData.OK_DONE);
        ButtonType logout = new ButtonType("Log Out Now", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(stay, logout);
        dialog.getDialogPane().setContent(new javafx.scene.layout.VBox(10, message, warningCountdown));
        dialog.setResultConverter(button -> button);
        dialog.setOnHidden(e -> {
            ButtonType result = dialog.getResult();
            if (warning != dialog) return;
            warning = null;
            warningCountdown = null;
            if (result == stay) extendSession();
            else if (result == logout) autoLogout();
        });
        Button stayButton = (Button) dialog.getDialogPane().lookupButton(stay);
        Button logoutButton = (Button) dialog.getDialogPane().lookupButton(logout);
        if (stayButton != null) { stayButton.getStyleClass().addAll("approved-button","approved-primary-button"); UiActionIcons.apply(stayButton,"refresh","Stay signed in"); }
        if (logoutButton != null) { logoutButton.getStyleClass().addAll("approved-button","approved-danger-button"); UiActionIcons.apply(logoutButton,"return","Log out now"); }
        updateCountdown(warningSeconds);
        dialog.show();
    }

    private void updateCountdown(int remaining) {
        if (warningCountdown == null) return;
        int safe = Math.max(0, remaining);
        warningCountdown.setText(String.format("Automatic logout in %d:%02d", safe / 60, safe % 60));
    }

    private void extendSession() {
        if (loggingOut.get()) return;
        try {
            new UserService().extendSession();
            markActivity();
        } catch (Exception failure) {
            autoLogout();
        }
    }

    private void autoLogout() {
        if (!loggingOut.compareAndSet(false, true)) return;
        closeWarning();
        ticker.stop();
        Thread worker = new Thread(() -> {
            try { new UserService().logoutIdle(); } catch (Exception ignored) { }
            finally { SessionService.clear(); }
            Platform.runLater(() -> {
                detach();
                SceneManager.showLogin();
            });
        }, "dse-idle-logout");
        worker.setDaemon(true);
        worker.start();
    }

    private void closeWarning() {
        OwnedDialog<ButtonType> current = warning;
        warning = null;
        warningCountdown = null;
        if (current != null && current.isShowing()) current.close();
    }
}
