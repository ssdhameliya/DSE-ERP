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
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.HashMap;
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

    @ExceptionHandler(AttachmentUnavailableException.class)
    public ResponseEntity<Map<String, Object>> attachmentUnavailable(AttachmentUnavailableException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "status", HttpStatus.NOT_FOUND.value(),
            "code", "ATTACHMENT_UNAVAILABLE",
            "message", message(error, "The attachment file is not available in managed storage")
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> dataIntegrity(DataIntegrityViolationException error) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(error);
        String rootMsg = root == null || root.getMessage() == null ? "" : root.getMessage().trim();
        String userFriendly;
        if (rootMsg.contains("violates unique constraint") || rootMsg.contains("duplicate key")) {
            userFriendly = "A record with this identifier already exists in the ERP database (duplicate key violation).";
        } else if (rootMsg.contains("violates not-null constraint")) {
            userFriendly = "A mandatory required field was missing in database: " + rootMsg;
        } else if (rootMsg.contains("violates foreign key constraint")) {
            userFriendly = "A referenced record (customer, supplier, or item) could not be linked because it does not exist in master data.";
        } else if (rootMsg.contains("violates check constraint")) {
            userFriendly = "The submitted document violates a business integrity check constraint: " + rootMsg;
        } else {
            userFriendly = rootMsg.isBlank() ? "Database data integrity constraint violation." : rootMsg;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("code", "DATA_INTEGRITY_VIOLATION");
        body.put("message", userFriendly);
        body.put("details", rootMsg);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> jsonNotReadable(HttpMessageNotReadableException error) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(error);
        String rootMsg = root == null || root.getMessage() == null ? error.getMessage() : root.getMessage();
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("code", "INVALID_REQUEST_BODY");
        body.put("message", "Request payload could not be read or contains invalid data formats.");
        body.put("details", rootMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validationError(MethodArgumentNotValidException error) {
        String msg = error.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed for one or more fields");
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("code", "VALIDATION_FAILED");
        body.put("message", msg);
        body.put("details", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> serverError(Exception error) {
        error.printStackTrace();
        Throwable root = NestedExceptionUtils.getMostSpecificCause(error);
        String rootMsg = root == null || root.getMessage() == null ? error.getMessage() : root.getMessage();
        String displayMsg = (rootMsg != null && !rootMsg.isBlank())
            ? rootMsg
            : "The ERP server encountered an internal error during this operation.";
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("code", "SERVER_ERROR");
        body.put("message", displayMsg);
        body.put("details", (root == null ? error.getClass().getSimpleName() : root.getClass().getSimpleName()) + ": " + displayMsg);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static String message(Throwable error, String fallback) {
        String value = error == null ? null : error.getMessage();
        return value == null || value.isBlank() ? fallback : value;
    }

}
