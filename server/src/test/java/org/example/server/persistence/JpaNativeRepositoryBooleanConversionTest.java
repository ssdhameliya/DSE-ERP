package org.example.server.persistence;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class JpaNativeRepositoryBooleanConversionTest {
    @Test
    void nativeBooleanConversionAcceptsLegacyIntegerBackedFlags() throws Exception {
        JpaNativeRepository repository = new JpaNativeRepository();
        Method convert = JpaNativeRepository.class.getDeclaredMethod("convert", Object.class, Class.class);
        convert.setAccessible(true);

        assertEquals(Boolean.TRUE, convert.invoke(repository, 1, Boolean.class));
        assertEquals(Boolean.FALSE, convert.invoke(repository, 0, Boolean.class));
        assertEquals(Boolean.TRUE, convert.invoke(repository, 9L, Boolean.class));
        assertEquals(Boolean.FALSE, convert.invoke(repository, "0", Boolean.class));
        assertEquals(Boolean.TRUE, convert.invoke(repository, "true", Boolean.class));
    }
}
