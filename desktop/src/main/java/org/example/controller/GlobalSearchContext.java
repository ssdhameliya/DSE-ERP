package org.example.controller;

import java.util.concurrent.atomic.AtomicReference;

/** One-shot query handoff from the persistent shell search box to the full search workspace. */
public final class GlobalSearchContext {
    private static final AtomicReference<String> QUERY=new AtomicReference<>("");
    private GlobalSearchContext(){}
    public static void open(String query){QUERY.set(query==null?"":query.trim());}
    public static String consume(){String q=QUERY.getAndSet("");return q==null?"":q;}
}
