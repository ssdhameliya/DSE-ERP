package org.example.server.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AuthDtos.OperationResponse> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new AuthDtos.OperationResponse(false, rootMessage(ex)));
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<AuthDtos.OperationResponse> forbidden(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new AuthDtos.OperationResponse(false, rootMessage(ex)));
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
