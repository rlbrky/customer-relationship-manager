package com.berkay.crm.exception;

import com.berkay.crm.dto.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorEnvelope> handleAuth(AuthenticationException ex, HttpServletRequest request) {

        // dont reveal if username or password was wrong
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .findFirst().orElse("Validation failed");

        return build(HttpStatus.UNPROCESSABLE_ENTITY, message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorEnvelope> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {

        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorEnvelope> handleConflict(ConflictException ex, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {

        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);   // full detail server-side
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);  // generic to client
    }

    private ResponseEntity<ErrorEnvelope> build(HttpStatus status, String message, HttpServletRequest request) {

        ErrorEnvelope body = new ErrorEnvelope(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());

        return ResponseEntity.status(status).body(body);
    }
}
