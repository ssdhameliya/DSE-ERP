package org.example.server.auth;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SmtpMailServiceProxyCompatibilityTest {
    @Test
    void transactionalSmtpServiceRemainsSubclassProxyable() throws Exception {
        assertFalse(Modifier.isFinal(SmtpMailService.class.getModifiers()),
                "SmtpMailService must not be final because Spring subclasses it for transactional AOP");
        assertNotNull(SmtpMailService.class
                .getDeclaredMethod("saveSettings", String.class, String.class, String.class, Integer.class)
                .getAnnotation(Transactional.class),
                "saveSettings must remain transactional");
    }
}
