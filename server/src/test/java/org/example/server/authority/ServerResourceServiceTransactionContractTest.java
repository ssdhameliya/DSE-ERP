package org.example.server.authority;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class ServerResourceServiceTransactionContractTest {
    @Test
    void apiWriteMethodsOwnAnActiveTransactionBoundary() throws Exception {
        Method put = ServerResourceService.class.getMethod(
                "apiPut", String.class, String.class, String.class, String.class, byte[].class, String.class);
        Method delete = ServerResourceService.class.getMethod("apiDelete", String.class, String.class);

        Transactional putTx = put.getAnnotation(Transactional.class);
        Transactional deleteTx = delete.getAnnotation(Transactional.class);
        assertNotNull(putTx, "apiPut must start the transaction before the internal self-call to put()");
        assertNotNull(deleteTx, "apiDelete must start the transaction before the internal self-call to delete()");
        assertFalse(putTx.readOnly());
        assertFalse(deleteTx.readOnly());
    }

    @Test
    void apiReadMethodsUseReadOnlyTransactions() throws Exception {
        Method list = ServerResourceService.class.getMethod("apiList", String.class);
        Method get = ServerResourceService.class.getMethod("apiGet", String.class, String.class);
        assertTrue(list.getAnnotation(Transactional.class).readOnly());
        assertTrue(get.getAnnotation(Transactional.class).readOnly());
    }
}
