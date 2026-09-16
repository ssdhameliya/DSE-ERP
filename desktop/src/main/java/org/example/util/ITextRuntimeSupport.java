package org.example.util;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central iText runtime compatibility settings for the Java 25 desktop runtime.
 * iText 7.2.6 otherwise tries to unmap direct buffers through the deprecated
 * sun.misc.Unsafe::invokeCleaner path, which Java 25 reports as terminally deprecated.
 * The relevant iText switch lives on a package-private class, so it is invoked
 * reflectively to avoid coupling application code to iText internals at compile time.
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
            // PDF generation must remain available even if a future iText version
            // removes this compatibility switch. In that case its own defaults apply.
        }
    }
}
