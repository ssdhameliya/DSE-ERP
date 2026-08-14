package org.example.controller;

/** One-shot selection used when the quotation register opens the full-page editor. */
public final class QuotationEditorContext {
    private static Integer quotationId;
    private QuotationEditorContext() {}
    public static synchronized void open(Integer id){quotationId=id;}
    public static synchronized Integer consume(){Integer id=quotationId;quotationId=null;return id;}
}
