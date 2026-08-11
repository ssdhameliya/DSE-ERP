package org.example.util;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

/** Coalesces rapid search/filter events into one JavaFX-thread action. */
public final class FxDebouncer {
    private final PauseTransition pause;
    private Runnable action;

    public FxDebouncer(java.time.Duration delay) {
        long millis = Math.max(0, delay == null ? 250 : delay.toMillis());
        pause = new PauseTransition(Duration.millis(millis));
        pause.setOnFinished(event -> { if (action != null) action.run(); });
    }

    public void submit(Runnable nextAction) {
        action = nextAction;
        pause.playFromStart();
    }

    public void cancel() {
        pause.stop();
        action = null;
    }
}
