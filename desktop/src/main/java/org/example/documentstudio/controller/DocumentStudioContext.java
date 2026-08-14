package org.example.documentstudio.controller;

/** One-shot navigation context for opening a template in the designer. */
public final class DocumentStudioContext {
    private static volatile String templateId;
    private DocumentStudioContext() {}
    public static void open(String id) { templateId = id; }
    public static String consume() { String id = templateId; templateId = null; return id; }
}
