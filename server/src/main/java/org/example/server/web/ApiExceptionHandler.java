package org.example.server.web;

import org.springframework.http.HttpStatus;
import org.example.server.auth.EmailDeliveryException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;

import java.util.Map;

@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ConcurrentEditException.class)
    public ResponseEntity<Map<String, Object>> concurrentEdit(ConcurrentEditException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "status", HttpStatus.CONFLICT.value(),
            "code", "CONCURRENT_EDIT",
            "message", error.getMessage()
        ));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<Map<String, Object>> optimisticConflict(Exception error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "status", HttpStatus.CONFLICT.value(),
            "code", "CONCURRENT_EDIT",
            "message", "This record was changed by another user. Reload the latest version before saving again."
        ));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> forbidden(SecurityException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
            "status", HttpStatus.FORBIDDEN.value(),
            "code", "AUTH_PERMISSION_DENIED",
            "message", message(error, "Insufficient permission")
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "status", HttpStatus.BAD_REQUEST.value(),
            "code", "BAD_REQUEST",
            "message", message(error, "Invalid request")
        ));
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<Map<String, Object>> emailUnavailable(EmailDeliveryException error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
            "code", "EMAIL_DELIVERY_UNAVAILABLE",
            "message", error.getMessage()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> businessConflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "status", HttpStatus.CONFLICT.value(),
            "code", "BUSINESS_CONFLICT",
            "message", message(error, "The operation conflicts with current ERP data")
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> serverError(Exception error) {
        error.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "code", "SERVER_ERROR",
            "message", "The ERP server could not complete this operation. Check the server log for the underlying error."
        ));
    }

    private static String message(Throwable error, String fallback) {
        String value = error == null ? null : error.getMessage();
        return value == null || value.isBlank() ? fallback : value;
    }

}
