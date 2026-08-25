package org.example.server.auth;

/**
 * Compatibility shim for source trees that are updated by extracting a newer
 * DSE ERP ZIP over an existing checkout.  The pre-R3 class at this path was a
 * global Spring advice named "apiExceptionHandler", which collided with the
 * web-layer ApiExceptionHandler.  Keeping this non-component class ensures an
 * overlay replaces the old annotated source instead of leaving it behind.
 *
 * <p>Authentication exception handling is implemented by
 * {@link AuthApiExceptionHandler}.</p>
 */
@Deprecated(forRemoval = false)
public final class ApiExceptionHandler {
    private ApiExceptionHandler() {
    }
}
