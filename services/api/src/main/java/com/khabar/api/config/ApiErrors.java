package com.khabar.api.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends the sentences we write for people ("Every CRITICAL finding needs a written reason...") back
 * to the screen as JSON, so it can show them. Only our own ResponseStatusException reasons are sent;
 * any other error keeps Spring's default body, so internal details never leak.
 */
@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", e.getStatusCode().value());
        if (e.getReason() != null) {
            body.put("message", e.getReason());
        }
        return ResponseEntity.status(e.getStatusCode()).headers(e.getHeaders()).body(body);
    }
}
