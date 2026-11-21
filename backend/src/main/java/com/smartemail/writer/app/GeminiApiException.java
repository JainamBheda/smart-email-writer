package com.smartemail.writer.app;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Raised when the Gemini request cannot be completed or its response cannot be understood.
 */
public class GeminiApiException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public GeminiApiException(String message) {
        this(message, HttpStatus.BAD_GATEWAY);
    }

    public GeminiApiException(String message, HttpStatusCode statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
