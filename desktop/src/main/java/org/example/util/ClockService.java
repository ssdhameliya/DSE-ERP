package org.example.util;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** One shared clock pulse for every visible application clock label. */
public final class ClockService {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy  |  hh:mm:ss a");
    private static final List<WeakReference<Label>> LABELS = new CopyOnWriteArrayList<>();
    private static Timeline timeline;

    private ClockService() { }

    public static synchronized void start(Label label) {
        if (label == null) return;
        boolean registered = LABELS.stream().map(WeakReference::get).anyMatch(existing -> existing == label);
        if (!registered) LABELS.add(new WeakReference<>(label));
        update(label);
        if (timeline == null) {
            timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateAll()));
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.play();
        }
    }

    private static void updateAll() {
        long started=System.nanoTime();
        String value = LocalDateTime.now().format(FORMATTER);
        for (WeakReference<Label> reference : LABELS) {
            Label label = reference.get();
            if (label != null && (label.getScene() != null || label.isVisible())) {
                label.setText(value);
            }
        }
        LABELS.removeIf(reference -> reference.get() == null);
        long millis=(System.nanoTime()-started)/1_000_000L;
        if(millis>=20)PerformanceMonitor.event("recurring-task","clock-update | "+millis+" ms | labels="+LABELS.size());
    }

    private static void update(Label label) {
        label.setText(LocalDateTime.now().format(FORMATTER));
    }
}
