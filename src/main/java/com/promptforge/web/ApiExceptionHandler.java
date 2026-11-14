package com.promptforge.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts exceptions into a consistent JSON error shape.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Handles validation failures on the request body.
     *
     * @param exception the validation exception
     * @return 400 with the field messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, Object> body = baseBody("Validation failed");
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles anything unexpected.
     *
     * @param exception the exception
     * @return 500 with a safe message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(baseBody("Unexpected error: " + exception.getMessage()));
    }

    private Map<String, Object> baseBody(String error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
