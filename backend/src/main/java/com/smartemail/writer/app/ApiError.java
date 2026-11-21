package com.smartemail.writer.app;

import java.time.Instant;

public record ApiError(String message, String detail, Instant timestamp) {

    public static ApiError of(String message, String detail) {
        return new ApiError(message, detail, Instant.now());
    }
}
