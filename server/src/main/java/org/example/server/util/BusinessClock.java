package org.example.server.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.Properties;

/** Server-side business clock synchronized with the desktop workspace settings. */
public final class BusinessClock {
    private BusinessClock() { }

    public static ZoneId zone() {
        String configured = readConfigured("company.timeZone");
        if (configured == null || configured.isBlank()) configured = System.getenv("DSE_BUSINESS_TIME_ZONE");
        if (configured != null && !configured.isBlank()) {
            try { return ZoneId.of(configured.trim()); } catch (Exception ignored) { }
        }
        return ZoneId.systemDefault();
    }

    public static LocalDate today() { return LocalDate.now(zone()); }
    public static LocalDateTime now() { return LocalDateTime.now(zone()); }
    public static YearMonth currentMonth() { return YearMonth.from(today()); }
    public static Instant nowUtc() { return Instant.now(); }

    private static String readConfigured(String key) {
        String file = System.getenv("DSE_BUSINESS_CONFIG_FILE");
        if (file == null || file.isBlank()) return null;
        try {
            Path path = Path.of(file);
            if (!Files.isRegularFile(path)) return null;
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(path)) { properties.load(in); }
            return properties.getProperty(key);
        } catch (Exception ignored) {
            return null;
        }
    }
}
