package org.example.navigation;

/**
 * Optional lifecycle contract for cached JavaFX screens.
 * Controllers may implement this interface to refresh only what is necessary
 * when a page becomes visible and release transient work when it is hidden.
 */
public interface ScreenLifecycle {
    /** Called after the screen has been attached to the application scene. */
    default void onScreenShown(boolean reusedFromCache) { }

    /** Called immediately before the screen is replaced by another page. */
    default void onScreenHidden() { }
}
