package org.example.server.auth;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthOtpServiceProxyContractTest {
    @Test
    void transactionalOtpServiceRemainsProxyableBySpring() {
        assertFalse(Modifier.isFinal(AuthOtpService.class.getModifiers()),
                "AuthOtpService is transactional and must remain proxyable by Spring");
    }
}
