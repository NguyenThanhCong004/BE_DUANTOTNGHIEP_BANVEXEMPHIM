package com.fpoly.duan.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.dto.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private String localizeMessage(String rawMessage, String fallback) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return fallback;
        }
        String message = rawMessage.trim();
        String lower = message.toLowerCase();

        if (lower.contains("query did not return a unique result")) {
            return "Dữ liệu bị trùng, vui lòng kiểm tra lại thông tin.";
        }
        if (lower.contains("nonuniqueresultexception")) {
            return "Dữ liệu bị trùng, vui lòng kiểm tra lại thông tin.";
        }
        if (lower.contains("constraintviolationexception") || lower.contains("duplicate key")) {
            return "Dữ liệu đã tồn tại trong hệ thống.";
        }
        if (lower.contains("could not execute statement")) {
            return "Không thể xử lý yêu cầu với dữ liệu hiện tại.";
        }
        if (lower.contains("internal server error")) {
            return "Đã xảy ra lỗi hệ thống.";
        }

        return message;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(ApiResponse.builder()
                .status(status.value())
                .message(localizeMessage(ex.getReason(), "Yêu cầu không hợp lệ"))
                .build());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message(localizeMessage(ex.getMessage(), "Đã xảy ra lỗi hệ thống"))
                .build());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentialsException(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message("Tên đăng nhập hoặc mật khẩu không chính xác")
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.<Map<String, String>>builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Lỗi xác thực dữ liệu")
                .data(errors)
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message(localizeMessage(ex.getMessage(), "Đã xảy ra lỗi hệ thống"))
                .build());
    }
}
