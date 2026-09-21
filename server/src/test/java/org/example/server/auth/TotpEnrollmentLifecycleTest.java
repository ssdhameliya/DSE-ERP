package org.example.server.auth;

import org.example.server.persistence.JpaNativeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TotpEnrollmentLifecycleTest {
    @TempDir Path temp;

    @Test
    void setupCanBeRenderedAgainAndVerifiedWithoutChangingTheSecret() {
        withTemporaryHome(() -> {
            TotpService service = new TotpService(new JpaNativeRepository());
            TotpService.Setup created = service.createSetup("admin", "admin@example.com");
            assertFalse(created.manualSecret().isBlank());
            assertTrue(created.provisioningUri().startsWith("otpauth://totp/"));
            assertTrue(created.provisioningUri().contains("secret=" + created.manualSecret()));
            assertTrue(created.provisioningUri().contains("issuer=DSE%20ERP"));

            TotpService.Setup reloaded = service.existingSetup("admin", "admin@example.com", created.encryptedSecret());
            assertEquals(created.manualSecret(), reloaded.manualSecret());
            assertEquals(created.encryptedSecret(), reloaded.encryptedSecret());
            assertEquals(created.provisioningUri(), reloaded.provisioningUri());

            String code = currentCode(created.manualSecret());
            assertTrue(service.verifyEncrypted(created.encryptedSecret(), code));
            assertFalse(service.verifyEncrypted(created.encryptedSecret(), "00000"));
            assertFalse(service.verifyEncrypted(created.encryptedSecret(), "ABCDEF"));
        });
    }

    private static String currentCode(String secret) {
        try {
            byte[] key = decode32(secret);
            long counter = Instant.now().getEpochSecond() / 30L;
            byte[] msg = new byte[8];
            for (int i = 7; i >= 0; i--) { msg[i] = (byte) (counter & 0xff); counter >>>= 8; }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(msg);
            int o = h[h.length - 1] & 15;
            int bin = ((h[o] & 127) << 24) | ((h[o + 1] & 255) << 16) | ((h[o + 2] & 255) << 8) | (h[o + 3] & 255);
            return String.format(Locale.ROOT, "%06d", bin % 1_000_000);
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private static byte[] decode32(String text) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String s = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : s.toCharArray()) {
            int v = alphabet.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Invalid authenticator secret");
            buffer = (buffer << 5) | v; bits += 5;
            if (bits >= 8) { out.write((buffer >> (bits - 8)) & 255); bits -= 8; }
        }
        return out.toByteArray();
    }

    private void withTemporaryHome(Runnable action) {
        String previous = System.getProperty("user.home");
        try { System.setProperty("user.home", temp.toString()); action.run(); }
        finally {
            if (previous == null) System.clearProperty("user.home"); else System.setProperty("user.home", previous);
        }
    }
}
