package com.interview.platform.common;

import java.time.Instant;
import org.slf4j.MDC;

public record ApiResponse<T>(String code, String message, T data, String traceId, Instant timestamp) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data, MDC.get("traceId"), Instant.now());
    }
}
