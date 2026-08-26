package org.example.shared;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;

/** Encrypts application secrets at rest using an installation/user scoped AES-256 key. */
public final class SecretValueCodec {
    private static final String PREFIX = "ENCv1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private SecretValueCodec() {}

    public static boolean isEncrypted(String value) { return value != null && value.startsWith(PREFIX); }

    public static String encrypt(String plain) {
        if (plain == null || plain.isBlank()) return "";
        if (isEncrypted(plain)) return plain;
        try {
            byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) { throw new IllegalStateException("Unable to encrypt application secret", ex); }
    }

    public static String decrypt(String stored) {
        if (stored == null || stored.isBlank()) return "";
        if (!isEncrypted(stored)) return stored; // migration compatibility; caller should re-save encrypted.
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (payload.length < 29) throw new IllegalArgumentException("Encrypted secret payload is invalid");
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Unable to decrypt application secret", ex); }
    }

    private static SecretKey key() throws Exception {
        String external = System.getenv("DSE_SECRET_KEY");
        if (external != null && !external.isBlank()) {
            byte[] raw = Base64.getDecoder().decode(external.trim());
            if (raw.length != 32) throw new IllegalStateException("DSE_SECRET_KEY must be a Base64 encoded 32-byte key");
            return new SecretKeySpec(raw, "AES");
        }
        Path file = Path.of(System.getProperty("user.home", "."), ".dse-erp", "secret.key").toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        byte[] raw;
        if (Files.isRegularFile(file)) raw = Base64.getDecoder().decode(Files.readString(file).trim());
        else {
            KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(256); raw = generator.generateKey().getEncoded();
            Files.writeString(file, Base64.getEncoder().encodeToString(raw), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try { Files.setPosixFilePermissions(file, java.util.Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)); } catch (Exception ignored) {}
        }
        if (raw.length != 32) throw new IllegalStateException("DSE ERP secret key is invalid");
        return new SecretKeySpec(raw, "AES");
    }
}
