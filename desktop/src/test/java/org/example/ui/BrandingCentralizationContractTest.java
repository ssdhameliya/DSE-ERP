package org.example.ui;

import org.example.update.BuildInfo;
import org.example.update.ReleaseHighlights;
import org.example.util.ScreenIdentity;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BrandingCentralizationContractTest {
    @Test void screenIdentityIsCentralAndSemantic() {
        assertEquals(ScreenIdentity.Kind.CREATE, ScreenIdentity.kind("Add Item"));
        assertEquals(ScreenIdentity.Kind.CREATE, ScreenIdentity.kind("Create Sale"));
        assertEquals(ScreenIdentity.Kind.EDIT, ScreenIdentity.kind("Edit Customer"));
        assertEquals(ScreenIdentity.Kind.SETTINGS, ScreenIdentity.kind("Settings • Company & Billing"));
        assertEquals(ScreenIdentity.Kind.REPORT, ScreenIdentity.kind("Reports"));
        assertEquals(ScreenIdentity.Kind.LIST, ScreenIdentity.kind("Sales Register"));
        assertEquals("screen-title-create", ScreenIdentity.styleClass("Add Item"));
    }

    @Test void customerFacingApplicationIdentityUsesCompanyName() throws Exception {
        String branding = Files.readString(Path.of("src/main/java/org/example/service/BrandingService.java"));
        String scene = Files.readString(Path.of("src/main/java/org/example/util/SceneManager.java"));
        String dashboard = Files.readString(Path.of("src/main/java/org/example/controller/DashboardController.java"));
        assertTrue(branding.contains("public static String applicationName() { return companyName(); }"));
        assertTrue(scene.contains("brandedWindowTitle"));
        assertTrue(scene.contains("updateScreenTitle"));
        assertTrue(dashboard.contains("ScreenIdentity.styleClass(title)"));
        assertTrue(dashboard.contains("BrandingService.companyName()"));
        assertTrue(dashboard.contains("SceneManager.updateScreenTitle(title)"));
    }

    @Test void themesOwnCentralScreenTitleColours() throws Exception {
        for (String theme : new String[]{"light-theme.css", "dark-theme.css"}) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains("screen-title-create"));
            assertTrue(css.contains("screen-title-edit"));
            assertTrue(css.contains("screen-title-view"));
            assertTrue(css.contains("screen-title-list"));
            assertTrue(css.contains("screen-title-settings"));
            assertTrue(css.contains("screen-title-report"));
        }
    }

    @Test void visibleFxmlNoLongerHardcodesLegacyProductBrand() throws Exception {
        try (var files = Files.walk(Path.of("src/main/resources/fxml"))) {
            var offenders = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".fxml"))
                    .filter(path -> {
                        try { return Files.readString(path).contains("DSE ERP"); }
                        catch (Exception ignored) { return true; }
                    }).toList();
            assertTrue(offenders.isEmpty(), "Visible FXML still contains DSE ERP: " + offenders);
        }
    }

    @Test void runningBuildWhatsNewIsApplicationOwnedNotGithubBody() {
        String githubBody = "## What's Changed\n* Release by someone in https://github.com/example/repo/pull/1\n\n**Full Changelog**: https://github.com/example/repo/compare/a...b";
        String resolved = ReleaseHighlights.resolve(BuildInfo.version(), githubBody);
        assertFalse(resolved.contains("github.com"));
        assertTrue(resolved.contains("Customer-facing branding"));
        assertTrue(resolved.contains("Sales Register"));
    }
}
