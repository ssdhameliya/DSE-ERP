package org.example.server.web;

import org.example.server.auth.EmailDeliveryException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import jakarta.persistence.OptimisticLockException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global REST exception handler — RFC 7807 {@code application/problem+json} responses.
 *
 * <p>Every handler returns a {@link ProblemDetail} body with Content-Type
 * {@code application/problem+json}. Custom ERP fields ({@code code}, {@code details})
 * are added as RFC 7807 extension properties so existing desktop clients that parse
 * {@code "code"} and {@code "message"} continue to work without change.</p>
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {

    // ── Concurrent / optimistic lock ────────────────────────────────────────

    @ExceptionHandler(ConcurrentEditException.class)
    public ResponseEntity<ProblemDetail> concurrentEdit(ConcurrentEditException ex) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_EDIT", ex.getMessage(), null);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ProblemDetail> optimisticConflict(Exception ex) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_EDIT",
                "This record was changed by another user. Reload the latest version before saving again.", null);
    }

    // ── Security / access ───────────────────────────────────────────────────

    /**
     * Handles Spring Security method-level {@code @PreAuthorize} / {@code @RolesAllowed} denials.
     * SecurityConfig already handles filter-level denials; this catches service/controller level.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "AUTH_PERMISSION_DENIED",
                "Insufficient permission to perform this operation.", null);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetail> securityException(SecurityException ex) {
        return problem(HttpStatus.FORBIDDEN, "AUTH_PERMISSION_DENIED",
                message(ex, "Insufficient permission"), null);
    }

    // ── Attachment ──────────────────────────────────────────────────────────

    @ExceptionHandler(AttachmentUnavailableException.class)
    public ResponseEntity<ProblemDetail> attachmentUnavailable(AttachmentUnavailableException ex) {
        return problem(HttpStatus.NOT_FOUND, "ATTACHMENT_UNAVAILABLE",
                message(ex, "The attachment file is not available in managed storage"), null);
    }

    // ── Validation / bad input ───────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> badRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                message(ex, "Invalid request"), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validationError(MethodArgumentNotValidException ex) {
        String summary = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (summary.isBlank()) summary = "Validation failed for one or more fields";

        // Structured field-level errors as RFC 7807 extension property
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        ProblemDetail pd = buildProblemDetail(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", summary, summary);
        pd.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> jsonNotReadable(HttpMessageNotReadableException ex) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        String rootMsg = root == null || root.getMessage() == null ? ex.getMessage() : root.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY",
                "Request payload could not be read or contains invalid data formats.", rootMsg);
    }

    // ── Business conflicts ───────────────────────────────────────────────────

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> businessConflict(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "BUSINESS_CONFLICT",
                message(ex, "The operation conflicts with current ERP data"), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> dataIntegrity(DataIntegrityViolationException ex) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
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
        return problem(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", userFriendly, rootMsg);
    }

    // ── Email delivery ───────────────────────────────────────────────────────

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ProblemDetail> emailUnavailable(EmailDeliveryException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_UNAVAILABLE",
                ex.getMessage(), null);
    }

    // ── Catch-all ────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> serverError(Exception ex) {
        ex.printStackTrace();
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        String rootMsg = root == null || root.getMessage() == null ? ex.getMessage() : root.getMessage();
        String displayMsg = (rootMsg != null && !rootMsg.isBlank())
                ? rootMsg
                : "The ERP server encountered an internal error during this operation.";
        String details = (root == null ? ex.getClass().getSimpleName() : root.getClass().getSimpleName())
                + ": " + displayMsg;
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR", displayMsg, details);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a {@link ProblemDetail} with RFC 7807 standard fields plus DSE ERP extension
     * properties ({@code code}, {@code details}) for backward compatibility with desktop client parsing.
     */
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail, String details) {
        ProblemDetail pd = buildProblemDetail(status, code, detail, details);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    private ProblemDetail buildProblemDetail(HttpStatus status, String code, String detail, String details) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("about:blank"));
        pd.setTitle(status.getReasonPhrase());
        // DSE ERP extension properties — keeps desktop JSON parsing intact
        pd.setProperty("code", code);
        pd.setProperty("message", detail); // backward-compat alias
        if (details != null && !details.isBlank() && !details.equals(detail)) {
            pd.setProperty("details", details);
        }
        return pd;
    }

    private static String message(Throwable ex, String fallback) {
        String value = ex == null ? null : ex.getMessage();
        return value == null || value.isBlank() ? fallback : value;
    }
}

