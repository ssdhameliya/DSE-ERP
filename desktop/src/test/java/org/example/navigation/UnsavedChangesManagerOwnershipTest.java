package org.example.navigation;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnsavedChangesManagerOwnershipTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (IllegalStateException alreadyStarted) {
            // Toolkit is shared when this test runs with other JavaFX tests.
        }
        Platform.setImplicitExit(false);
    }

    @Test
    void childNodeInSameSceneCanClearActivePageTracker() throws Exception {
        fx(() -> {
            StackPane child = new StackPane();
            StackPane owner = new StackPane(child);
            new Scene(owner);
            UnsavedChangesManager.track(owner, "Create Sale");
            assertTrue(trackerInstalled());
            UnsavedChangesManager.clear(child);
            assertFalse(trackerInstalled());
            return null;
        });
    }

    @Test
    void nodeFromDifferentSceneCannotClearActivePageTracker() throws Exception {
        fx(() -> {
            StackPane owner = new StackPane();
            StackPane other = new StackPane();
            new Scene(owner);
            new Scene(other);
            UnsavedChangesManager.track(owner, "Create Sale");
            assertTrue(trackerInstalled());
            UnsavedChangesManager.clear(other);
            assertTrue(trackerInstalled());
            UnsavedChangesManager.clearAll();
            return null;
        });
    }

    @Test
    void exactOwnerAndNullNodeRemainSupported() {
        StackPane owner = new StackPane();
        assertTrue(UnsavedChangesManager.belongsToActiveWorkflow(owner, owner));
        assertTrue(UnsavedChangesManager.belongsToActiveWorkflow(null, owner));
    }

    @Test
    void detachedUnrelatedNodeCannotClearAnActiveOwner() {
        StackPane owner = new StackPane();
        StackPane unrelated = new StackPane();
        assertFalse(UnsavedChangesManager.belongsToActiveWorkflow(unrelated, owner));
    }

    private static boolean trackerInstalled() throws Exception {
        Field tracker = UnsavedChangesManager.class.getDeclaredField("tracker");
        tracker.setAccessible(true);
        return tracker.get(null) != null;
    }

    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                value.set(action.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            if (error.get() instanceof Exception e) throw e;
            throw new RuntimeException(error.get());
        }
        return value.get();
    }
}
