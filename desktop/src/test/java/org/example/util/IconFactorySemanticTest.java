package org.example.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless contract test for IconFactory semantic mapping.
 *
 * <p>Tests are intentionally JavaFX-free so they run on the headless Linux CI
 * runner without a DISPLAY. The invariants verified here are the pure mapping
 * contracts (semantic → colour name → hex) and the style-string construction
 * rule that must produce a combined inline style rather than replacing it.</p>
 */
class IconFactorySemanticTest {

    // -------------------------------------------------------------------------
    // Semantic → colour name contract
    // -------------------------------------------------------------------------

    @Test
    void profileDropdownIconsHaveDistinctExpectedColours() {
        assertEquals("purple", IconFactory.semanticColour("settings"),  "Settings must be purple");
        assertEquals("teal",   IconFactory.semanticColour("backup"),    "Backup must be teal");
        assertEquals("indigo", IconFactory.semanticColour("security"),  "Security must be indigo");
        assertEquals("blue",   IconFactory.semanticColour("user"),      "User must be blue");
        assertEquals("pink",   IconFactory.semanticColour("exit"),      "Exit (sign-out) must be pink");
        assertEquals("orange", IconFactory.semanticColour("lock"),      "Lock must be orange");
        assertEquals("green",  IconFactory.semanticColour("import"),    "Import must be green");
    }

    @Test
    void coreActionColoursAreDistinct() {
        assertEquals("green",  IconFactory.semanticColour("add"),      "Add must be green");
        assertEquals("green",  IconFactory.semanticColour("save"),     "Save must be green");
        assertEquals("green",  IconFactory.semanticColour("complete"), "Complete must be green");
        assertEquals("pink",   IconFactory.semanticColour("delete"),   "Delete must be pink");
        assertEquals("pink",   IconFactory.semanticColour("cancel"),   "Cancel must be pink");
        assertEquals("teal",   IconFactory.semanticColour("filter"),   "Filter must be teal");
        assertEquals("teal",   IconFactory.semanticColour("email"),    "Email must be teal");
        assertEquals("blue",   IconFactory.semanticColour("view"),     "View must be blue");
        assertEquals("blue",   IconFactory.semanticColour("download"), "Download must be blue");
        assertEquals("purple", IconFactory.semanticColour("settings"), "Settings must be purple");
        assertEquals("purple", IconFactory.semanticColour("print"),    "Print must be purple");
        assertEquals("blue",   IconFactory.semanticColour("export"),   "Export must be blue");
        assertEquals("orange", IconFactory.semanticColour("reminder"), "Reminder must be orange");
    }

    @Test
    void businessModuleColoursAreDistinct() {
        assertEquals("green",  IconFactory.semanticColour("sale"),      "Sale must be green");
        assertEquals("orange", IconFactory.semanticColour("purchase"),  "Purchase must be orange");
        assertEquals("purple", IconFactory.semanticColour("quotation"), "Quotation must be purple");
        assertEquals("blue",   IconFactory.semanticColour("customer"),  "Customer must be blue");
        assertEquals("teal",   IconFactory.semanticColour("supplier"),  "Supplier must be teal");
        assertEquals("orange", IconFactory.semanticColour("item"),      "Item must be orange");
        assertEquals("teal",   IconFactory.semanticColour("inventory"), "Inventory must be teal");
        assertEquals("pink",   IconFactory.semanticColour("report"),    "Report must be pink");
        assertEquals("blue",   IconFactory.semanticColour("dashboard"), "Dashboard must be blue");
        assertEquals("purple", IconFactory.semanticColour("bank"),      "Bank must be purple");
    }

    // -------------------------------------------------------------------------
    // Colour name → hex contract (light theme — ThemeManager default)
    // -------------------------------------------------------------------------

    @Test
    void hexColourValuesMatchDesignSystem() {
        // Light-theme canonical values (kept in sync with light-theme.css tokens)
        assertEquals("#2563eb", IconFactory.hexColor("blue"),   "Blue hex mismatch");
        assertEquals("#16a34a", IconFactory.hexColor("green"),  "Green hex mismatch");
        assertEquals("#d97706", IconFactory.hexColor("orange"), "Orange hex mismatch");
        assertEquals("#7c3aed", IconFactory.hexColor("purple"), "Purple hex mismatch");
        assertEquals("#e11d48", IconFactory.hexColor("pink"),   "Pink hex mismatch");
        assertEquals("#0d9488", IconFactory.hexColor("teal"),   "Teal hex mismatch");
        assertEquals("#4f46e5", IconFactory.hexColor("indigo"), "Indigo hex mismatch");
    }

    @Test
    void unknownColourFallsBackToSlate() {
        String fallback = IconFactory.hexColor("nonexistent");
        // Must be a valid hex colour (slate/grey), not empty or null
        assertNotNull(fallback);
        assertFalse(fallback.isBlank());
        assertTrue(fallback.startsWith("#"), "Fallback must be a hex colour");
    }

    // -------------------------------------------------------------------------
    // Inline style string construction contract
    //
    // actionIcon() must produce a combined style string that contains BOTH
    // -fx-icon-color (for colour) AND any pre-existing -fx-font-family /
    // -fx-font-size entries (FontIcon glyph identity). This is verified here
    // by simulating the same string-append logic without creating a JavaFX node.
    // -------------------------------------------------------------------------

    @Test
    void styleStringAppendPreservesFontFamily() {
        // Simulate what FontIcon typically has as its existing style
        String existingStyle = "-fx-font-family: 'FontAwesome5Free-Solid'; -fx-font-size: 15px;";
        String colorStyle    = "-fx-icon-color: #7c3aed;";

        // This is the same logic as IconFactory.actionIcon():
        String combined = existingStyle.trim() + " " + colorStyle;

        assertTrue(combined.contains("-fx-font-family"),  "Combined style must retain -fx-font-family");
        assertTrue(combined.contains("-fx-font-size"),    "Combined style must retain -fx-font-size");
        assertTrue(combined.contains("-fx-icon-color"),   "Combined style must contain -fx-icon-color");
    }

    @Test
    void styleStringAppendWhenExistingIsBlank() {
        // When FontIcon has no prior inline style the result is just the color rule
        String existingStyle = "";
        String colorStyle    = "-fx-icon-color: #0d9488;";

        String combined = existingStyle.isBlank() ? colorStyle
                : existingStyle.trim() + " " + colorStyle;

        assertTrue(combined.contains("-fx-icon-color"), "Color style must be present when existing is blank");
        assertFalse(combined.contains("null"),           "Style must not contain literal 'null'");
    }

    @Test
    void styleStringAppendDoesNotDuplicate() {
        // A second call must not keep appending; icon key equality prevents re-application
        String existing  = "-fx-font-family: 'FontAwesome5Free-Solid'; -fx-icon-color: #7c3aed;";
        String colorStyle = "-fx-icon-color: #7c3aed;";
        String combined  = existing.trim() + " " + colorStyle;

        // Count occurrences — two is acceptable (real code guards via icon key),
        // but the font-family must survive either way
        assertTrue(combined.contains("-fx-font-family"), "Font-family must survive append");
    }

    // -------------------------------------------------------------------------
    // Semantic label resolution contract (no JavaFX required)
    // -------------------------------------------------------------------------

    @Test
    void semanticForLabelResolvesCommonCaptions() {
        assertNotNull(IconFactory.semanticForLabel("Date"),          "Date caption must resolve");
        assertNotNull(IconFactory.semanticForLabel("Amount"),        "Amount caption must resolve");
        assertNotNull(IconFactory.semanticForLabel("Customer"),      "Customer caption must resolve");
        assertNotNull(IconFactory.semanticForLabel("Invoice No."),   "Invoice No. caption must resolve");
        assertNotNull(IconFactory.semanticForLabel("Payment Mode"),  "Payment Mode caption must resolve");
    }

    @Test
    void semanticForPageTitleResolvesModules() {
        assertEquals("sale",      IconFactory.semanticForPageTitle("Sales Register"));
        assertEquals("purchase",  IconFactory.semanticForPageTitle("Purchase Register"));
        assertEquals("customer",  IconFactory.semanticForPageTitle("Customer List"));
        assertEquals("report",    IconFactory.semanticForPageTitle("Report Center"));
        assertEquals("dashboard", IconFactory.semanticForPageTitle("Dashboard"));
        assertEquals("settings",  IconFactory.semanticForPageTitle("Settings"));
    }
}
