package org.example.server.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SignedTokenCodecTest {
    @Test
    void roundTripsSignedIdentityState() {
        byte[] key = new byte[48];
        byte[] nonce = new byte[24];
        Arrays.fill(key, (byte) 0x41);
        Arrays.fill(nonce, (byte) 0x22);
        Instant expiry = Instant.parse("2030-01-02T03:04:05Z");

        String token = SignedTokenCodec.encode(42, 7L, expiry, nonce, key);
        SignedTokenCodec.DecodeResult decoded = SignedTokenCodec.decode(token, key);

        assertTrue(decoded.valid());
        assertEquals(42, decoded.token().userId());
        assertEquals(7L, decoded.token().authVersion());
        assertEquals(expiry, decoded.token().expiresAt());
        assertArrayEquals(nonce, decoded.token().nonce());
    }

    @Test
    void rejectsTamperedBearer() {
        byte[] key = new byte[48];
        byte[] nonce = new byte[24];
        Arrays.fill(key, (byte) 0x55);
        Arrays.fill(nonce, (byte) 0x33);
        String token = SignedTokenCodec.encode(5, 0L, Instant.parse("2030-01-01T00:00:00Z"), nonce, key);
        int dot = token.indexOf('.');
        char first = token.charAt(0);
        char replacement = first == 'A' ? 'B' : 'A';
        String tampered = replacement + token.substring(1, dot) + token.substring(dot);

        SignedTokenCodec.DecodeResult decoded = SignedTokenCodec.decode(tampered, key);

        assertFalse(decoded.valid());
        assertEquals(SignedTokenCodec.Failure.INVALID_SIGNATURE, decoded.failure());
    }
}
