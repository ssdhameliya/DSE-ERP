package org.example.server.auth;

import org.example.server.persistence.JpaNativeRepository;
import org.example.shared.SecretValueCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class SmtpMailServiceSecretReplacementTest {
    @TempDir Path temp;

    @Test
    void newlyEnteredPasswordReplacesUnreadableStoredSecretWithoutDecryptingItFirst() {
        withTemporaryHome(() -> {
            MemorySettingsRepository db = new MemorySettingsRepository();
            db.values.put("smtp.appPassword", "ENCv1:not-a-valid-payload");
            SmtpMailService service = new SmtpMailService("", 587, "", "", "", db);

            SmtpMailService.Settings saved = service.saveSettings(
                    "accounts@gmail.com", "abcd efgh ijkl mnop", "", 587);

            String stored = db.values.get("smtp.appPassword");
            assertNotNull(stored);
            assertTrue(SecretValueCodec.isEncrypted(stored));
            assertEquals("abcdefghijklmnop", SecretValueCodec.decrypt(stored));
            assertEquals("accounts@gmail.com", saved.email());
            assertEquals("smtp.gmail.com", saved.host());
        });
    }

    @Test
    void blankPasswordKeepsExistingValidServerOwnedSecret() {
        withTemporaryHome(() -> {
            MemorySettingsRepository db = new MemorySettingsRepository();
            SmtpMailService service = new SmtpMailService("", 587, "", "", "", db);
            service.saveSettings("accounts@gmail.com", "first password", "", 587);
            String firstStored = db.values.get("smtp.appPassword");

            SmtpMailService.Settings saved = service.saveSettings(
                    "accounts@gmail.com", "", "smtp.gmail.com", 587);

            assertEquals("firstpassword", saved.password());
            assertEquals("firstpassword", SecretValueCodec.decrypt(db.values.get("smtp.appPassword")));
            assertNotEquals(firstStored, db.values.get("smtp.appPassword"),
                    "AES-GCM should re-encrypt with a fresh IV while preserving the same secret");
        });
    }

    private void withTemporaryHome(Runnable action) {
        String previous = System.getProperty("user.home");
        try {
            System.setProperty("user.home", temp.toString());
            action.run();
        } finally {
            if (previous == null) System.clearProperty("user.home");
            else System.setProperty("user.home", previous);
        }
    }

    private static final class MemorySettingsRepository extends JpaNativeRepository {
        private final Map<String, String> values = new HashMap<>();

        @Override public int update(String sql, Object... args) {
            if (args != null && args.length >= 2) values.put(String.valueOf(args[0]), String.valueOf(args[1]));
            return 1;
        }

        @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            String key = args == null || args.length == 0 ? "" : String.valueOf(args[0]);
            if (!values.containsKey(key)) throw new NoSuchElementException("missing setting " + key);
            return type.cast(values.get(key));
        }
    }
}
