package org.example.navigation;

import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnsavedChangesManagerOwnershipTest {
    @AfterEach
    void cleanup() {
        UnsavedChangesManager.clearAll();
    }

    @Test
    void childNodeCanClearActivePageGuardWithoutJavaFxToolkit() throws Exception {
        StackPane child = new StackPane();
        StackPane owner = new StackPane(child);
        UnsavedChangesManager.installGuard(owner, destination -> false);

        assertNotNull(guard());
        UnsavedChangesManager.clear(child);
        assertNull(guard());
    }

    @Test
    void unrelatedNodeCannotClearActivePageGuard() throws Exception {
        StackPane owner = new StackPane();
        StackPane unrelated = new StackPane();
        UnsavedChangesManager.installGuard(owner, destination -> false);

        assertNotNull(guard());
        UnsavedChangesManager.clear(unrelated);
        assertNotNull(guard());
    }

    @Test
    void exactOwnerAndNullNodeRemainSupported() {
        StackPane owner = new StackPane();
        assertTrue(UnsavedChangesManager.belongsToActiveWorkflow(owner, owner));
        assertTrue(UnsavedChangesManager.belongsToActiveWorkflow(null, owner));
    }

    @Test
    void nestedDescendantBelongsToActiveWorkflow() {
        StackPane leaf = new StackPane();
        StackPane middle = new StackPane(leaf);
        StackPane owner = new StackPane(middle);
        assertTrue(UnsavedChangesManager.belongsToActiveWorkflow(leaf, owner));
    }

    @Test
    void detachedUnrelatedNodeCannotClearAnActiveOwner() {
        StackPane owner = new StackPane();
        StackPane unrelated = new StackPane();
        assertFalse(UnsavedChangesManager.belongsToActiveWorkflow(unrelated, owner));
    }

    private static Object guard() throws Exception {
        Field field = UnsavedChangesManager.class.getDeclaredField("guard");
        field.setAccessible(true);
        return field.get(null);
    }
}
