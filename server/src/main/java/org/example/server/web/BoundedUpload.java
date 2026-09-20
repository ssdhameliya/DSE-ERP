package org.example.server.web;

import jakarta.servlet.http.HttpServletRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Central bounded binary request reader. Never lets Spring materialize an unbounded byte[] first. */
public final class BoundedUpload {
    private BoundedUpload() {}

    public static byte[] read(HttpServletRequest request, long maxBytes, String label) throws IOException {
        if (maxBytes <= 0 || maxBytes > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid upload limit");
        long declared = request.getContentLengthLong();
        if (declared > maxBytes) throw new IllegalArgumentException(label + " exceeds the " + human(maxBytes) + " upload limit");
        int initial = (int)Math.min(Math.max(0L, declared), Math.min(maxBytes, 64 * 1024));
        try (InputStream in = request.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream(initial)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            for (int read; (read = in.read(buffer)) >= 0;) {
                if (read == 0) continue;
                total += read;
                if (total > maxBytes) throw new IllegalArgumentException(label + " exceeds the " + human(maxBytes) + " upload limit");
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String human(long bytes) {
        if (bytes % (1024L * 1024L) == 0) return (bytes / (1024L * 1024L)) + " MB";
        return bytes + " bytes";
    }
}
