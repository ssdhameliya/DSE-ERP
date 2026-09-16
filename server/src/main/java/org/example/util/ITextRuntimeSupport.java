package org.example.util;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central iText runtime compatibility settings for the Java 25 server runtime.
 * iText 7.2.6 otherwise tries to unmap direct buffers through the terminally
 * deprecated sun.misc.Unsafe::invokeCleaner path. The switch is invoked by
 * reflection so the server stays compatible if a future iText release removes it.
 */
public final class ITextRuntimeSupport {
    private static final AtomicBoolean CONFIGURED = new AtomicBoolean();

    private ITextRuntimeSupport() { }

    public static void configure() {
        if (!CONFIGURED.compareAndSet(false, true)) return;
        try {
            Class<?> type = Class.forName("com.itextpdf.io.source.ByteBufferRandomAccessSource");
            Method method = type.getDeclaredMethod("disableByteBufferMemoryUnmapping");
            method.setAccessible(true);
            method.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep PDF generation available if iText changes this internal switch.
        }
    }
}
