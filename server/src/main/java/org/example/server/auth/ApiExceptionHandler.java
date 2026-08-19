package org.example.server.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.EmptyResultDataAccessException;

import java.time.DateTimeException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AuthDtos.OperationResponse> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new AuthDtos.OperationResponse(false, rootMessage(ex)));
    }


    @ExceptionHandler(DateTimeException.class)
    ResponseEntity<AuthDtos.OperationResponse> invalidDateTime(DateTimeException ex) {
        return ResponseEntity.badRequest().body(new AuthDtos.OperationResponse(false,
                "Invalid date/time value. DSE ERP APIs use ISO dates (yyyy-MM-dd) and UTC/offset timestamps."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<AuthDtos.OperationResponse> unreadableRequest(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new AuthDtos.OperationResponse(false,
                "Invalid request data. Check date/time values and try again."));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<AuthDtos.OperationResponse> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthDtos.OperationResponse(false, rootMessage(ex)));
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<AuthDtos.OperationResponse> forbidden(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new AuthDtos.OperationResponse(false, rootMessage(ex)));
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    ResponseEntity<AuthDtos.OperationResponse> notFound(EmptyResultDataAccessException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new AuthDtos.OperationResponse(false, "The requested record was not found. Refresh and try again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<AuthDtos.OperationResponse> dataConflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthDtos.OperationResponse(false, "The operation conflicts with existing ERP data. Refresh the record and try again."));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<AuthDtos.OperationResponse> concurrentChange(PessimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthDtos.OperationResponse(false, "This record is being changed by another operation. Refresh and try again."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<AuthDtos.OperationResponse> serverError(Exception ex) {
        ex.printStackTrace();
        return ResponseEntity.internalServerError().body(new AuthDtos.OperationResponse(false, "The request could not be completed"));
    }

    private String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
