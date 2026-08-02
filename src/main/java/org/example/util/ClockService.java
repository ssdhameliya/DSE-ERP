package org.example.util;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ClockService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy  |  hh:mm:ss a");

    private ClockService() {
    }

    public static void start(Label label) {

        update(label);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> update(label))
        );

        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        // Prevent garbage collection
        label.getProperties().put("clockTimeline", timeline);
    }

    private static void update(Label label) {
        label.setText(LocalDateTime.now().format(FORMATTER));
    }
}