package org.example.server.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Small dependency-free HMAC bearer codec used by the Spring server.
 *
 * <p>The token contains only server-verified identity state: user id, the user's
 * authentication version, expiry and a random nonce. Username/role are always
 * re-read from PostgreSQL on each request so role, lock and activation changes
 * remain authoritative.</p>
 */
final class SignedTokenCodec {
    private static final byte FORMAT_VERSION = 1;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private SignedTokenCodec() {
    }

    static String encode(int userId, long authVersion, Instant expiresAt, byte[] nonce, byte[] key) {
        if (userId <= 0) throw new IllegalArgumentException("User id must be positive");
        if (expiresAt == null) throw new IllegalArgumentException("Token expiry is required");
        if (nonce == null || nonce.length < 16) throw new IllegalArgumentException("Token nonce is too short");
        requireKey(key);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(FORMAT_VERSION);
                out.writeInt(userId);
                out.writeLong(authVersion);
                out.writeLong(expiresAt.getEpochSecond());
                out.writeInt(nonce.length);
                out.write(nonce);
            }
            String payload = ENCODER.encodeToString(bytes.toByteArray());
            String signature = ENCODER.encodeToString(hmac(payload.getBytes(StandardCharsets.US_ASCII), key));
            return payload + "." + signature;
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to create authentication token", failure);
        }
    }

    static DecodeResult decode(String token, byte[] key) {
        if (token == null || token.isBlank()) return DecodeResult.failure(Failure.MISSING);
        if (key == null || key.length < 32) return DecodeResult.failure(Failure.SERVER_KEY_MISSING);
        try {
            int dot = token.indexOf('.');
            if (dot <= 0 || dot != token.lastIndexOf('.') || dot == token.length() - 1) {
                return DecodeResult.failure(Failure.MALFORMED);
            }
            String payloadText = token.substring(0, dot);
            byte[] suppliedSignature = DECODER.decode(token.substring(dot + 1));
            byte[] expectedSignature = hmac(payloadText.getBytes(StandardCharsets.US_ASCII), key);
            if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
                return DecodeResult.failure(Failure.INVALID_SIGNATURE);
            }

            byte[] payload = DECODER.decode(payloadText);
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
                int version = in.readUnsignedByte();
                if (version != FORMAT_VERSION) return DecodeResult.failure(Failure.UNSUPPORTED_VERSION);
                int userId = in.readInt();
                long authVersion = in.readLong();
                long expiresEpochSecond = in.readLong();
                int nonceLength = in.readInt();
                if (userId <= 0 || nonceLength < 16 || nonceLength > 128 || in.available() != nonceLength) {
                    return DecodeResult.failure(Failure.MALFORMED);
                }
                byte[] nonce = in.readNBytes(nonceLength);
                if (nonce.length != nonceLength || in.available() != 0) return DecodeResult.failure(Failure.MALFORMED);
                return DecodeResult.success(new Decoded(userId, authVersion,
                        Instant.ofEpochSecond(expiresEpochSecond), nonce));
            }
        } catch (Exception ignored) {
            return DecodeResult.failure(Failure.MALFORMED);
        }
    }

    private static byte[] hmac(byte[] payload, byte[] key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        return mac.doFinal(payload);
    }

    private static void requireKey(byte[] key) {
        if (key == null || key.length < 32) throw new IllegalArgumentException("Authentication signing key is too short");
    }

    enum Failure {
        NONE,
        MISSING,
        MALFORMED,
        INVALID_SIGNATURE,
        UNSUPPORTED_VERSION,
        SERVER_KEY_MISSING
    }

    record Decoded(int userId, long authVersion, Instant expiresAt, byte[] nonce) {
        Decoded {
            nonce = nonce == null ? new byte[0] : nonce.clone();
        }

        @Override
        public byte[] nonce() {
            return nonce.clone();
        }
    }

    record DecodeResult(Decoded token, Failure failure) {
        static DecodeResult success(Decoded token) {
            return new DecodeResult(token, Failure.NONE);
        }

        static DecodeResult failure(Failure failure) {
            return new DecodeResult(null, failure);
        }

        boolean valid() {
            return token != null && failure == Failure.NONE;
        }
    }
}
