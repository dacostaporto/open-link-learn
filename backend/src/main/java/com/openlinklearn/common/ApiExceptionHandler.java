package com.openlinklearn.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String INTERNAL_ERROR_DETAIL = "An unexpected error occurred.";

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatusCode status = exception.getStatusCode();
        String detail = status.is5xxServerError()
                ? INTERNAL_ERROR_DETAIL
                : nonBlank(exception.getReason(), defaultDetail(status));
        return problem(status, detail, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .limit(5)
                .map(this::formatFieldError)
                .toList();
        String detail = errors.isEmpty()
                ? "Request validation failed."
                : "Request validation failed: " + String.join("; ", errors);
        return problem(HttpStatus.BAD_REQUEST, detail, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.BAD_REQUEST, "Request body is missing or malformed.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            String detail = status.is5xxServerError()
                    ? INTERNAL_ERROR_DETAIL
                    : nonBlank(errorResponse.getBody().getDetail(), defaultDetail(status));
            return problem(status, detail, request);
        }
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_DETAIL, request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatusCode status,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        HttpStatus resolvedStatus = HttpStatus.resolve(status.value());
        body.setTitle(resolvedStatus == null ? "HTTP Error" : resolvedStatus.getReasonPhrase());
        body.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + nonBlank(error.getDefaultMessage(), "is invalid");
    }

    private String defaultDetail(HttpStatusCode status) {
        HttpStatus resolvedStatus = HttpStatus.resolve(status.value());
        return resolvedStatus == null ? "The request could not be processed." : resolvedStatus.getReasonPhrase() + ".";
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}