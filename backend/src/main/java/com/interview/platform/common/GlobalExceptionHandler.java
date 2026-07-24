package com.interview.platform.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleBusiness(BusinessException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(new ErrorResponse(
                exception.code(), exception.getMessage(), MDC.get("traceId"),
                request.getRequestURI(), Map.of(), Instant.now()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException exception,
                                                       HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "REQUIRED_HEADER_MISSING", "缺少请求头：" + exception.getHeaderName(), MDC.get("traceId"),
                request.getRequestURI(), Map.of("header", exception.getHeaderName()), Instant.now()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception,
                                                    HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "INVALID_JSON", "请求内容格式错误", MDC.get("traceId"),
                request.getRequestURI(), Map.of(), Instant.now()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> handleUploadSize(MaxUploadSizeExceededException exception,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ErrorResponse(
                "FILE_TOO_LARGE", "文件不能超过 5MB", MDC.get("traceId"),
                request.getRequestURI(), Map.of(), Instant.now()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException exception,
                                                  HttpServletRequest request) {
        log.warn("Data conflict traceId={} path={}", MDC.get("traceId"), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                "DATA_CONFLICT", "数据已发生变化，请刷新后重试", MDC.get("traceId"),
                request.getRequestURI(), Map.of(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                   HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_FAILED", "请求参数不符合要求", MDC.get("traceId"),
                request.getRequestURI(), details, Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected request failure traceId={} path={}", MDC.get("traceId"),
                request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                "INTERNAL_ERROR", "服务暂时不可用，请稍后重试", MDC.get("traceId"),
                request.getRequestURI(), Map.of(), Instant.now()));
    }

    record ErrorResponse(String code, String message, String traceId, String path,
                         Map<String, String> details, Instant timestamp) {}
}
