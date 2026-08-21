package org.example.documentstudio.controller;

/** Shared navigation context for Document Studio landing modes and PDF template editing. */
public final class DocumentStudioContext {
    public enum Mode { PDF, EXCEL }

    private static volatile String templateId;
    private static volatile Mode landingMode = Mode.PDF;

    private DocumentStudioContext() {}

    /** Opens a PDF template in the PDF designer. */
    public static void open(String id) { templateId = id; }

    /** Consumes the selected PDF template id once. */
    public static String consume() { String id = templateId; templateId = null; return id; }

    /** Selects which Document Studio library should be shown when the landing page opens. */
    public static void selectMode(Mode mode) { landingMode = mode == null ? Mode.PDF : mode; }

    /** Returns the requested landing mode without clearing it, so child pages can return to it. */
    public static Mode currentMode() { return landingMode; }
}
