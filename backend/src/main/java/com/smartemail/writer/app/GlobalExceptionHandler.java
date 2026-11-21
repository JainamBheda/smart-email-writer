package com.smartemail.writer.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiError.of("Invalid request", detail));
    }

    @ExceptionHandler(GeminiApiException.class)
    public ResponseEntity<ApiError> handleGemini(GeminiApiException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiError.of(ex.getMessage(), "Check GEMINI_API_KEY and the configured model URL."));
    }

    @ExceptionHandler(WebClientException.class)
    public ResponseEntity<ApiError> handleWebClient(WebClientException ex) {
        log.warn("HTTP client failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of("Could not reach the AI service", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error while generating a reply", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("Unexpected server error", "See server logs for details."));
    }

    private static String describe(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
