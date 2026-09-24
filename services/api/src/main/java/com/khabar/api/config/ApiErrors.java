package com.khabar.api.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Returns a consistent, safe error envelope. User-facing reasons come only from our own
 * ResponseStatusExceptions; unexpected failures expose no raw exception details to callers.
 */
@RestControllerAdvice
public class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    public record ApiError(String code, String message, int status, String requestId, boolean retryable) {
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handle(ResponseStatusException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        String message = e.getReason() == null ? "The request could not be completed." : e.getReason();
        return response(status, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handle(HttpMessageNotReadableException e, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "The request body is invalid or incomplete.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handle(MethodArgumentNotValidException e, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "One or more fields need attention.", request);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiError> handleFrameworkError(ErrorResponseException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        String message = switch (status) {
            case NOT_FOUND -> "The requested endpoint or record could not be found.";
            case METHOD_NOT_ALLOWED -> "This method is not supported for the requested endpoint.";
            default -> "The request could not be completed.";
        };
        return response(status, message, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleMissingResource(NoResourceFoundException e, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "The requested endpoint or record could not be found.", request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleMissingHandler(NoHandlerFoundException e, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "The requested endpoint or record could not be found.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "This method is not supported for the requested endpoint.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest request) {
        String requestId = RequestCorrelationFilter.requestId(request);
        // Avoid copying health details, user input, or provider responses into general logs.
        log.error("Unhandled API failure; requestId={}, exceptionType={}", requestId, e.getClass().getName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                "Khabar could not complete the request. Please try again or contact your clinic.", request);
    }

    private static ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request) {
        boolean retryable = status == HttpStatus.TOO_MANY_REQUESTS;
        ApiError body = new ApiError(codeFor(status), message, status.value(), RequestCorrelationFilter.requestId(request), retryable);
        return ResponseEntity.status(status).body(body);
    }

    private static String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "bad_request";
            case UNAUTHORIZED -> "unauthorized";
            case FORBIDDEN -> "forbidden";
            case NOT_FOUND -> "not_found";
            case CONFLICT -> "conflict";
            case PAYLOAD_TOO_LARGE -> "payload_too_large";
            case TOO_MANY_REQUESTS -> "rate_limited";
            default -> status.is5xxServerError() ? "server_error" : "request_failed";
        };
    }
}
