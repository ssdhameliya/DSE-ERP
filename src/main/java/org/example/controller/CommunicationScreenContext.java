package org.example.controller;

/**
 * Carries the requested communication channel across an FXML navigation.
 * The value is consumed once so normal Communication menu navigation still
 * opens the combined Email and WhatsApp activity view.
 */
public final class CommunicationScreenContext {
    private static String initialChannel;

    private CommunicationScreenContext() {
    }

    public static synchronized void select(String channel) {
        initialChannel = channel;
    }

    public static synchronized String take() {
        String selected = initialChannel;
        initialChannel = null;
        return selected;
    }
}
