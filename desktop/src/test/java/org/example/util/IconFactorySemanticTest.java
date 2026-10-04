package org.example.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class IconFactorySemanticTest {

    @BeforeAll
    static void initFx() throws Exception {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    void testProfileDropdownIconDistinctColors() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final AssertionError[] failure = new AssertionError[1];

        Platform.runLater(() -> {
            try {
                Node settings = IconFactory.compactIcon("settings", 15);
                Node backup = IconFactory.compactIcon("backup", 15);
                Node security = IconFactory.compactIcon("security", 15);
                Node user = IconFactory.compactIcon("user", 15);
                Node exit = IconFactory.compactIcon("exit", 15);
                Node lock = IconFactory.compactIcon("lock", 15);
                Node importIcon = IconFactory.compactIcon("import", 15);

                assertTrue(settings instanceof FontIcon);
                assertTrue(backup instanceof FontIcon);
                assertTrue(security instanceof FontIcon);
                assertTrue(user instanceof FontIcon);
                assertTrue(exit instanceof FontIcon);
                assertTrue(lock instanceof FontIcon);
                assertTrue(importIcon instanceof FontIcon);

                FontIcon fSettings = (FontIcon) settings;
                FontIcon fBackup = (FontIcon) backup;
                FontIcon fSecurity = (FontIcon) security;
                FontIcon fUser = (FontIcon) user;
                FontIcon fExit = (FontIcon) exit;
                FontIcon fLock = (FontIcon) lock;
                FontIcon fImport = (FontIcon) importIcon;

                System.out.println("DEBUG fSettings: literal=" + fSettings.getIconLiteral() + ", code=" + fSettings.getIconCode());
                System.out.println("DEBUG fBackup: literal=" + fBackup.getIconLiteral() + ", code=" + fBackup.getIconCode());
                System.out.println("DEBUG fInvoiceNo: " + ((FontIcon) IconFactory.compactIcon("invoice", 14)).getIconLiteral() + ", code=" + ((FontIcon) IconFactory.compactIcon("invoice", 14)).getIconCode());
                System.out.println("DEBUG fDate: " + ((FontIcon) IconFactory.compactIcon("date", 14)).getIconLiteral() + ", code=" + ((FontIcon) IconFactory.compactIcon("date", 14)).getIconCode());
                System.out.println("DEBUG fSupplier: " + ((FontIcon) IconFactory.compactIcon("supplier", 14)).getIconLiteral() + ", code=" + ((FontIcon) IconFactory.compactIcon("supplier", 14)).getIconCode());
                // None should be default black
                assertNotEquals(Color.BLACK, fSettings.getIconColor(), "Settings icon must not be black");
                assertNotEquals(Color.BLACK, fBackup.getIconColor(), "Backup icon must not be black");
                assertNotEquals(Color.BLACK, fSecurity.getIconColor(), "Security icon must not be black");
                assertNotEquals(Color.BLACK, fUser.getIconColor(), "User icon must not be black");
                assertNotEquals(Color.BLACK, fExit.getIconColor(), "Exit icon must not be black");
                assertNotEquals(Color.BLACK, fLock.getIconColor(), "Lock icon must not be black");
                assertNotEquals(Color.BLACK, fImport.getIconColor(), "Import icon must not be black");

                // Style attribute should enforce -fx-icon-color while preserving -fx-font-family and -fx-font-size
                assertTrue(fSettings.getStyle().contains("-fx-icon-color"), "Settings must have inline -fx-icon-color");
                assertTrue(fSettings.getStyle().contains("-fx-font-family"), "Settings must retain inline -fx-font-family");
                assertTrue(fSettings.getStyle().contains("-fx-font-size"), "Settings must retain inline -fx-font-size");
                assertTrue(fBackup.getStyle().contains("-fx-icon-color"), "Backup must have inline -fx-icon-color");
                assertTrue(fBackup.getStyle().contains("-fx-font-family"), "Backup must retain inline -fx-font-family");
                assertTrue(fSecurity.getStyle().contains("-fx-icon-color"), "Security must have inline -fx-icon-color");
                assertTrue(fSecurity.getStyle().contains("-fx-font-family"), "Security must retain inline -fx-font-family");

                // Verify specific semantic colors
                assertEquals(Color.web("#7c3aed"), fSettings.getIconColor(), "Settings should be purple");
                assertEquals(Color.web("#0d9488"), fBackup.getIconColor(), "Backup should be teal");
                assertEquals(Color.web("#4f46e5"), fSecurity.getIconColor(), "Security should be indigo");
                assertEquals(Color.web("#2563eb"), fUser.getIconColor(), "User should be blue");
                assertEquals(Color.web("#e11d48"), fExit.getIconColor(), "Exit should be pink");
                assertEquals(Color.web("#d97706"), fLock.getIconColor(), "Lock should be orange");
                assertEquals(Color.web("#16a34a"), fImport.getIconColor(), "Import should be green");

            } catch (AssertionError err) {
                failure[0] = err;
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) throw failure[0];
    }
}
