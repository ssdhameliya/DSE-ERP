package org.example.documentstudio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for intentional deletion of built-in/starter PDF Studio templates. */
class PdfStudioBuiltInDeletionLifecycleTest {

    @TempDir
    Path temp;

    @Test
    void modernSalesStarterDoesNotSilentlyReinstallAfterIntentionalDelete() throws Exception {
        BuiltInModernSalesTemplateInstaller.ensureInstalled(temp);
        Path folder = temp.resolve(BuiltInModernSalesTemplateInstaller.TEMPLATE_ID);
        assertTrue(Files.isDirectory(folder), "Modern Sales starter should install in a fresh template root");

        BuiltInModernSalesTemplateInstaller.markIntentionallyDeleted(temp);
        BuiltInModernSalesTemplateInstaller.enforceIntentionalDeletion(temp);
        assertFalse(Files.exists(folder), "Intentional delete must remove the local starter");

        BuiltInModernSalesTemplateInstaller.ensureInstalled(temp);
        assertFalse(Files.exists(folder), "Refresh/ensure must not silently recreate an intentionally deleted starter");
    }
}
