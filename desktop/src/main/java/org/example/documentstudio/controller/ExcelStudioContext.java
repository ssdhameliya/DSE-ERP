package org.example.documentstudio.controller;

/** One-shot navigation context for opening an Excel template in Document Studio. */
public final class ExcelStudioContext {
    private static volatile String templateId;
    private ExcelStudioContext() {}
    public static void open(String id){templateId=id;}
    public static String consume(){String id=templateId;templateId=null;return id;}
}
