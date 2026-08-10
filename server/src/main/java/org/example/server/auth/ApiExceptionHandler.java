package org.example.server.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AuthDtos.OperationResponse> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new AuthDtos.OperationResponse(false, rootMessage(ex)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<AuthDtos.OperationResponse> serverError(Exception ex) {
        ex.printStackTrace();
        return ResponseEntity.internalServerError().body(new AuthDtos.OperationResponse(false, rootMessage(ex)));
    }

    private String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
