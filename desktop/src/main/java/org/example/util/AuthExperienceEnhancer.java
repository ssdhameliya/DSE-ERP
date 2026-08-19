package org.example.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** Presentation-only owner for the startup/authentication family. */
public final class AuthExperienceEnhancer {
    private AuthExperienceEnhancer() {}
    public static void enhance(Parent root) {
        if (root == null || !root.getStyleClass().contains("auth-page")) return;
        if (!root.getStyleClass().contains("auth-experience-v2")) root.getStyleClass().add("auth-experience-v2");
        root.lookupAll(".auth-section-icon").forEach(n -> install(n, semantic(root), 28));
        root.lookupAll(".splash-stage-icon").forEach(n -> {
            String semantic = n.getStyleClass().stream().filter(c -> c.startsWith("splash-icon-")).findFirst()
                .map(c -> switch(c){case "splash-icon-workspace"->"folder";case "splash-icon-postgres"->"database";case "splash-icon-spring"->"settings";case "splash-icon-schema"->"check";default->"dashboard";}).orElse("dashboard");
            install(n, semantic, 18);
        });
    }
    private static String semantic(Parent root) {
        if (root.getStyleClass().contains("approved-screen-email-settings")) return "email";
        if (root.getStyleClass().contains("approved-screen-splash")) return "dashboard";
        if (root.getStyleClass().contains("approved-screen-registration")) return "user";
        return "security";
    }
    private static void install(Node node, String semantic, double size) {
        if (!(node instanceof StackPane pane) || Boolean.TRUE.equals(pane.getProperties().get("erp.auth.icon"))) return;
        pane.getProperties().put("erp.auth.icon", true);
        pane.getChildren().removeIf(child -> child instanceof Label);
        pane.getChildren().add(IconFactory.icon(semantic, size));
    }
}
