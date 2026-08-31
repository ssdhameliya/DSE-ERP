package org.example.util;

import javafx.scene.Node;

/**
 * Shared semantic-presentation bootstrap.
 *
 * <p>Phase 6 keeps this entry point intentionally small: the global enhancer
 * owns table/KPI discovery, while IconFactory owns semantic labels/buttons.
 * This avoids the former second recursive TableView scan.</p>
 */
public final class SharedUiFramework {
    private SharedUiFramework() { }

    public static void install(Node root) {
        FxThreadWatchdog.install();
        if (root != null) IconFactory.decorate(root);
    }
}
